package com.potpal.mirrortrack.export

import android.content.Context
import android.net.Uri
import com.potpal.mirrortrack.data.DataPointDao
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerPayloadGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DataPointDao
) {
    private val prettyJson = Json { prettyPrint = true }

    suspend fun generate(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val latestPoints = dao.latestPerCollectorKey()

            val grouped = latestPoints.groupBy { it.category }

            val payload = buildJsonObject {
                put("disclaimer",
                    "This file was generated locally from your own DB. " +
                    "It was never transmitted.")
                put("generated_ts_ms", System.currentTimeMillis())

                for (section in listOf(
                    "DEVICE_IDENTITY" to "device",
                    "NETWORK" to "network",
                    "LOCATION" to "location",
                    "SENSORS" to "sensors",
                    "BEHAVIORAL" to "behavioral",
                    "PERSONAL" to "personal",
                    "APPS" to "apps"
                )) {
                    val points = grouped[section.first].orEmpty()
                    put(section.second, buildJsonObject {
                        points.forEach { point ->
                            put("${point.collectorId}.${point.key}", point.value)
                        }
                    })
                }
            }

            val jsonString = prettyJson.encodeToString(JsonObject.serializer(), payload)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(jsonString.toByteArray(Charsets.UTF_8))
            }

            Logger.d(TAG, "Tracker payload: ${jsonString.length} bytes")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to generate tracker payload", e)
        }
    }

    companion object {
        private const val TAG = "TrackerPayload"
    }
}
