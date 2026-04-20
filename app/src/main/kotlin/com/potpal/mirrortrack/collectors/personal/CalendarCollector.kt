package com.potpal.mirrortrack.collectors.personal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Singleton
class CalendarCollector @Inject constructor() : Collector {
    override val id = "calendar"
    override val displayName = "Calendar"
    override val rationale =
        "Records calendar events in the next +/-30 days. Requires READ_CALENDAR."
    override val category = Category.PERSONAL
    override val requiredPermissions = listOf(Manifest.permission.READ_CALENDAR)
    override val accessTier = AccessTier.RUNTIME
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 6.hours
    override val defaultRetention: Duration = 90.days

    override suspend fun isAvailable(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override suspend fun collect(context: Context): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        try {
            val now = System.currentTimeMillis()
            val thirtyDays = 30L * 86_400_000L

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.CALENDAR_ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.ORGANIZER
                ),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf((now - thirtyDays).toString(), (now + thirtyDays).toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                val calIdx = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val orgIdx = cursor.getColumnIndex(CalendarContract.Events.ORGANIZER)

                while (cursor.moveToNext()) {
                    val eventId = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx) ?: "" else ""
                    val location = if (locIdx >= 0) cursor.getString(locIdx) ?: "" else ""
                    val start = if (startIdx >= 0) cursor.getLong(startIdx) else 0L
                    val end = if (endIdx >= 0) cursor.getLong(endIdx) else 0L
                    val allDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false
                    val organizer = if (orgIdx >= 0) cursor.getString(orgIdx) ?: "" else ""

                    val json = buildString {
                        append("{\"title\":\"").append(escapeJson(title)).append("\",")
                        append("\"location\":\"").append(escapeJson(location)).append("\",")
                        append("\"start_ms\":").append(start).append(",")
                        append("\"end_ms\":").append(end).append(",")
                        append("\"all_day\":").append(allDay).append(",")
                        append("\"organizer_email\":\"").append(escapeJson(organizer)).append("\"}")
                    }
                    points.add(DataPoint.json(id, category, "event_$eventId", json))
                }
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "Permission denied", e)
        }
        return points
    }

    private fun escapeJson(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    companion object {
        private const val TAG = "CalendarCollector"
    }
}
