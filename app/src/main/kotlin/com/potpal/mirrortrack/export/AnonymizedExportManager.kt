package com.potpal.mirrortrack.export

import android.content.Context
import android.net.Uri
import com.potpal.mirrortrack.collectors.Ingestor
import com.potpal.mirrortrack.data.DataPointDao
import com.potpal.mirrortrack.data.entities.DataPointEntity
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports the full data_points table as anonymized JSONL (JSON Lines).
 *
 * One JSON object per line — the format most AI systems handle natively.
 * Each line is a self-contained data point with PII stripped or hashed.
 *
 * Anonymization rules:
 * - GPS coordinates rounded to ~1 km (2 decimal places)
 * - SSIDs, BSSIDs, MAC addresses → salted SHA-256 (first 12 hex chars)
 * - IP addresses → masked to /24 (IPv4) or hashed (IPv6)
 * - Emails → hashed
 * - Voice transcripts → replaced with "[redacted]"; only context tags kept
 * - Calendar event titles/locations/organizer → hashed
 * - Android ID, serial, device fingerprint → hashed
 * - Bluetooth device names/MACs → hashed
 * - Package names → kept (public identifiers, not PII)
 * - Build info (manufacturer, model) → kept (not PII)
 * - Sensor readings, battery, screen state → kept (not PII)
 *
 * The per-export random salt ensures hashes are not linkable across exports.
 */
@Singleton
class AnonymizedExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DataPointDao,
    private val ingestor: Ingestor
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun export(uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        try {
            ingestor.flush()

            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            var rowCount = 0L

            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext ExportResult.Error("Could not open output")

            BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8)).use { writer ->
                // Header line with metadata
                val header = buildJsonObject {
                    put("_type", "mirrortrack_anonymized_export")
                    put("_version", 1)
                    put("_format", "jsonl")
                    put("_exported_ms", System.currentTimeMillis())
                    put("_anonymization", "salted_sha256_per_export")
                    put("_note", "Each line is one data point. PII has been hashed or redacted. GPS rounded to ~1km. Timestamps are real epoch-ms for temporal analysis.")
                }
                writer.write(json.encodeToString(JsonObject.serializer(), header))
                writer.newLine()

                // Page through all rows
                var lastRowId = 0L
                while (true) {
                    val page = dao.pageAscending(afterRowId = lastRowId, limit = PAGE_SIZE)
                    if (page.isEmpty()) break

                    for (entity in page) {
                        val anonymized = anonymize(entity, salt)
                        writer.write(json.encodeToString(JsonObject.serializer(), anonymized))
                        writer.newLine()
                        rowCount++
                    }

                    lastRowId = page.last().rowId
                    if (page.size < PAGE_SIZE) break
                }
            }

            Logger.i(TAG, "Anonymized export: $rowCount rows")
            ExportResult.Success(rowCount)
        } catch (e: Exception) {
            Logger.e(TAG, "Anonymized export failed", e)
            ExportResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun anonymize(entity: DataPointEntity, salt: ByteArray): JsonObject = buildJsonObject {
        put("ts", entity.timestamp)
        put("collector", entity.collectorId)
        put("category", entity.category)
        put("key", entity.key)
        put("type", entity.valueType)
        put("value", anonymizeValue(entity.collectorId, entity.key, entity.value, entity.valueType, salt))
    }

    private fun anonymizeValue(
        collectorId: String,
        key: String,
        value: String,
        valueType: String,
        salt: ByteArray
    ): String {
        // Rule-based anonymization by collector and key
        return when (collectorId) {
            // ── Location: round GPS to ~1km ──
            "location" -> when (key) {
                "lat", "lon", "latitude", "longitude" -> roundCoordinate(value)
                else -> value
            }

            // ── WiFi: hash network identifiers ──
            "wifi" -> when (key) {
                "current_ssid", "ssid" -> hash(value, salt)
                "current_bssid", "bssid" -> hash(value, salt)
                "scan_results" -> anonymizeWifiScanJson(value, salt)
                else -> value
            }

            // ── Bluetooth: hash device info ──
            "bluetooth" -> when (key) {
                "device_name", "device_address", "mac", "name" -> hash(value, salt)
                else -> if (key.contains("mac", ignoreCase = true) ||
                    key.contains("address", ignoreCase = true) ||
                    key.contains("name", ignoreCase = true)) hash(value, salt) else value
            }

            // ── IP: mask addresses ──
            "ip" -> when (key) {
                "public_ipv4" -> maskIpv4(value)
                "public_ipv6" -> hash(value, salt)
                else -> if (key.contains("ip", ignoreCase = true)) hash(value, salt) else value
            }

            // ── Identifiers: hash everything ──
            "identifiers" -> when (key) {
                "android_id", "serial", "build_fingerprint",
                "widevine_id", "gsf_id" -> hash(value, salt)
                else -> if (key.contains("id", ignoreCase = true) ||
                    key.contains("mac", ignoreCase = true) ||
                    key.contains("serial", ignoreCase = true)) hash(value, salt) else value
            }

            // ── Voice transcription: redact text, keep tags ──
            "voice_transcription", "speech_gated_transcription" -> when (key) {
                "transcript_text" -> "[redacted]"
                "inferred_context", "context_tags", "conversation_present",
                "speech_density_wpm", "word_count", "window_duration_ms",
                "ambient_dbfs", "transcription_triggered" -> value
                else -> if (key.contains("transcript", ignoreCase = true) ||
                    key.contains("text", ignoreCase = true)) "[redacted]" else value
            }

            // ── Calendar: hash titles, locations, organizers ──
            "calendar" -> when (key) {
                "event_title", "title" -> hash(value, salt)
                "event_location", "location" -> hash(value, salt)
                "organizer_email", "organizer" -> hash(value, salt)
                else -> if (valueType == "JSON") anonymizeCalendarJson(value, salt) else value
            }

            // ── Contacts: already hashed by collector, pass through ──
            "contacts" -> value

            // ── Ambient sound: no raw audio, safe ──
            "ambient_sound" -> value

            // ── Notification listener: package names are public ──
            "notification_listener" -> value

            // ── Apps: package names are public identifiers ──
            "installed_apps", "usage_stats", "app_ops_audit" -> value

            // ── Build info: manufacturer/model not PII ──
            "build_info" -> when (key) {
                "serial", "fingerprint", "build_serial" -> hash(value, salt)
                else -> value
            }

            // ── Hardware: not PII ──
            "hardware" -> value

            // ── Carrier: network name is not PII ──
            "carrier" -> value

            // ── Connectivity: not PII ──
            "connectivity" -> value

            // ── Network usage: package names are public ──
            "network_usage" -> value

            // ── Screen state, battery, steps, sensors: not PII ──
            "screen_state", "battery", "step_counter", "motion_sensors",
            "environment_sensors", "body_sensors", "app_lifecycle",
            "integrity", "system_stats" -> value

            // ── Activity recognition: not PII ──
            "activity_recognition" -> value

            // ── Logcat: package names are public ──
            "logcat" -> value

            // ── Privacy dashboard: not PII ──
            "privacy_dashboard" -> value

            // ── Default: if it looks like JSON with PII-ish keys, hash them ──
            else -> if (valueType == "JSON") anonymizeGenericJson(value, salt) else value
        }
    }

    private fun roundCoordinate(value: String): String {
        val d = value.toDoubleOrNull() ?: return value
        return "%.2f".format(d) // ~1.1 km precision
    }

    private fun maskIpv4(value: String): String {
        val parts = value.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.0" else hash(value, ByteArray(0))
    }

    private fun hash(value: String, salt: ByteArray): String {
        if (value.isBlank()) return value
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(value.toByteArray(Charsets.UTF_8))
        return digest.digest().take(6).joinToString("") { "%02x".format(it) }
    }

    private fun anonymizeWifiScanJson(value: String, salt: ByteArray): String {
        return try {
            val array = json.parseToJsonElement(value).jsonArray
            val anonymized = array.map { element ->
                val obj = element.jsonObject
                buildJsonObject {
                    for ((k, v) in obj) {
                        when (k.lowercase()) {
                            "ssid" -> put(k, hash(v.jsonPrimitive.content, salt))
                            "bssid" -> put(k, hash(v.jsonPrimitive.content, salt))
                            else -> put(k, v)
                        }
                    }
                }
            }
            json.encodeToString(kotlinx.serialization.json.JsonArray.serializer(),
                kotlinx.serialization.json.JsonArray(anonymized))
        } catch (_: Exception) {
            hash(value, salt)
        }
    }

    private fun anonymizeCalendarJson(value: String, salt: ByteArray): String {
        return try {
            val obj = json.parseToJsonElement(value).jsonObject
            val anonymized = buildJsonObject {
                for ((k, v) in obj) {
                    when {
                        k.contains("title", ignoreCase = true) -> put(k, hash(v.jsonPrimitive.content, salt))
                        k.contains("location", ignoreCase = true) -> put(k, hash(v.jsonPrimitive.content, salt))
                        k.contains("email", ignoreCase = true) -> put(k, hash(v.jsonPrimitive.content, salt))
                        k.contains("organizer", ignoreCase = true) -> put(k, hash(v.jsonPrimitive.content, salt))
                        k.contains("description", ignoreCase = true) -> put(k, "[redacted]")
                        else -> put(k, v)
                    }
                }
            }
            json.encodeToString(JsonObject.serializer(), anonymized)
        } catch (_: Exception) {
            hash(value, salt)
        }
    }

    private fun anonymizeGenericJson(value: String, salt: ByteArray): String {
        return try {
            val obj = json.parseToJsonElement(value).jsonObject
            val anonymized = buildJsonObject {
                for ((k, v) in obj) {
                    when {
                        PII_KEY_PATTERNS.any { p -> k.contains(p, ignoreCase = true) } ->
                            put(k, hash(v.jsonPrimitive.content, salt))
                        else -> put(k, v)
                    }
                }
            }
            json.encodeToString(JsonObject.serializer(), anonymized)
        } catch (_: Exception) {
            // Not valid JSON object or has nested arrays — return as-is
            value
        }
    }

    sealed class ExportResult {
        data class Success(val rowCount: Long) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    companion object {
        private const val TAG = "AnonymizedExport"
        private const val PAGE_SIZE = 5000
        private val PII_KEY_PATTERNS = listOf(
            "email", "phone", "address", "name", "ssid", "bssid",
            "mac", "serial", "imei", "imsi", "android_id"
        )
    }
}
