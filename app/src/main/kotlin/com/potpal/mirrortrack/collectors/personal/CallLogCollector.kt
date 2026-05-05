package com.potpal.mirrortrack.collectors.personal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Reads call log metadata. **No raw caller numbers, contact names, or post-dial
 * digits are persisted.** Numbers are hashed; everything else is aggregate.
 *
 * What is stored, per poll window:
 *  - inbound/outbound/missed/rejected counts (last 24h, 7d, 30d)
 *  - total duration buckets (short / medium / long)
 *  - unique hashed counterparts
 *  - top counterpart frequency (for "close-tie ratio" inference)
 *  - hour-of-day call histogram
 */
@Singleton
class CallLogCollector @Inject constructor() : Collector {
    override val id = "call_log"
    override val displayName = "Call Patterns"
    override val rationale =
        "Counts call frequency, durations, and unique hashed counterparts. " +
        "Raw numbers and contact names are never stored. " +
        "Requires READ_CALL_LOG."
    override val category = Category.PERSONAL
    override val requiredPermissions = listOf(Manifest.permission.READ_CALL_LOG)
    override val accessTier = AccessTier.RUNTIME
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 12.hours
    override val defaultRetention: Duration = 90.days

    override suspend fun isAvailable(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun collect(context: Context): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L
        val cutoff30d = now - 30L * day
        val zone = ZoneId.systemDefault()

        try {
            var inbound24 = 0L; var inbound7 = 0L; var inbound30 = 0L
            var outbound24 = 0L; var outbound7 = 0L; var outbound30 = 0L
            var missed24 = 0L; var missed7 = 0L; var missed30 = 0L
            var rejected30 = 0L
            var voicemail30 = 0L
            var totalDuration30 = 0L
            var shortCalls = 0L; var mediumCalls = 0L; var longCalls = 0L
            val counterpartFreq = mutableMapOf<String, Long>()
            val hourHistogram = LongArray(24)

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(cutoff30d.toString()),
                null
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                while (cursor.moveToNext()) {
                    val ts = if (dateIdx >= 0) cursor.getLong(dateIdx) else continue
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0
                    val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                    val number = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""

                    val ageDays = (now - ts) / day
                    when (type) {
                        CallLog.Calls.INCOMING_TYPE -> {
                            if (ageDays <= 1) inbound24++
                            if (ageDays <= 7) inbound7++
                            inbound30++
                        }
                        CallLog.Calls.OUTGOING_TYPE -> {
                            if (ageDays <= 1) outbound24++
                            if (ageDays <= 7) outbound7++
                            outbound30++
                        }
                        CallLog.Calls.MISSED_TYPE -> {
                            if (ageDays <= 1) missed24++
                            if (ageDays <= 7) missed7++
                            missed30++
                        }
                        CallLog.Calls.REJECTED_TYPE -> rejected30++
                        CallLog.Calls.VOICEMAIL_TYPE -> voicemail30++
                    }

                    totalDuration30 += duration
                    when {
                        duration in 1..59 -> shortCalls++
                        duration in 60..299 -> mediumCalls++
                        duration >= 300 -> longCalls++
                    }

                    if (number.isNotBlank() && duration > 0) {
                        val hashed = sha256(number.filter { it.isDigit() || it == '+' })
                        counterpartFreq[hashed] = (counterpartFreq[hashed] ?: 0L) + 1L
                    }

                    val hour = Instant.ofEpochMilli(ts).atZone(zone).hour.coerceIn(0, 23)
                    hourHistogram[hour]++
                }
            }

            val topFreq = counterpartFreq.values.sortedDescending().take(5)
            val topShare = if (counterpartFreq.values.sum() > 0L) {
                topFreq.sum().toDouble() / counterpartFreq.values.sum().toDouble()
            } else 0.0

            points += DataPoint.long(id, category, "inbound_24h", inbound24)
            points += DataPoint.long(id, category, "inbound_7d", inbound7)
            points += DataPoint.long(id, category, "inbound_30d", inbound30)
            points += DataPoint.long(id, category, "outbound_24h", outbound24)
            points += DataPoint.long(id, category, "outbound_7d", outbound7)
            points += DataPoint.long(id, category, "outbound_30d", outbound30)
            points += DataPoint.long(id, category, "missed_24h", missed24)
            points += DataPoint.long(id, category, "missed_7d", missed7)
            points += DataPoint.long(id, category, "missed_30d", missed30)
            points += DataPoint.long(id, category, "rejected_30d", rejected30)
            points += DataPoint.long(id, category, "voicemail_30d", voicemail30)
            points += DataPoint.long(id, category, "total_duration_seconds_30d", totalDuration30)
            points += DataPoint.long(id, category, "short_calls_30d", shortCalls)
            points += DataPoint.long(id, category, "medium_calls_30d", mediumCalls)
            points += DataPoint.long(id, category, "long_calls_30d", longCalls)
            points += DataPoint.long(id, category, "unique_counterparts_30d", counterpartFreq.size.toLong())
            points += DataPoint.double(id, category, "top5_counterpart_share_30d", topShare)
            points += DataPoint.json(
                id, category, "hour_histogram_30d",
                hourHistogram.joinToString(prefix = "[", postfix = "]")
            )
        } catch (e: SecurityException) {
            Logger.e(TAG, "Permission denied", e)
        } catch (e: Exception) {
            Logger.e(TAG, "Call log read failed", e)
        }
        return points
    }

    private fun sha256(input: String): String {
        if (input.isBlank()) return ""
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "CallLogCollector"
    }
}
