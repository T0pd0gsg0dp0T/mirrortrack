package com.potpal.mirrortrack.collectors.apps

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
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
class UsageStatsCollector @Inject constructor() : Collector {
    override val id = "usage_stats"
    override val displayName = "App Usage Stats"
    override val rationale =
        "Tracks per-app foreground time, last-used timestamps, and launch counts. " +
        "Requires PACKAGE_USAGE_STATS special access permission."
    override val category = Category.APPS
    override val requiredPermissions: List<String> = listOf("android.permission.PACKAGE_USAGE_STATS")
    override val accessTier = AccessTier.SPECIAL_ACCESS
    override val defaultEnabled = false
    override val defaultPollInterval: Duration = 1.hours
    override val defaultRetention: Duration = 30.days

    override suspend fun isAvailable(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun collect(context: Context): List<DataPoint> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm == null) {
            Logger.w(TAG, "UsageStatsManager unavailable")
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val oneDayAgo = now - 86_400_000L

        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, oneDayAgo, now)
        } catch (e: SecurityException) {
            Logger.e(TAG, "Permission denied", e)
            return emptyList()
        }

        if (stats.isNullOrEmpty()) return emptyList()

        val launchCounts = mutableMapOf<String, Int>()
        try {
            val events = usm.queryEvents(oneDayAgo, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    launchCounts[event.packageName] = (launchCounts[event.packageName] ?: 0) + 1
                }
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "Usage event permission denied", e)
        }

        return stats
            .groupBy { it.packageName }
            .map { (pkg, entries) ->
                val totalFg = entries.sumOf { it.totalTimeInForeground }
                val lastUsed = entries.maxOf { it.lastTimeUsed }
                val launches = launchCounts[pkg] ?: 0
                val json = """{"package":"${escapeJson(pkg)}","total_foreground_ms":$totalFg,"last_used_ms":$lastUsed,"launch_count":$launches}"""
                DataPoint.json(id, category, "package_usage:$pkg", json)
            }
            .sortedByDescending {
                it.value.substringAfter("total_foreground_ms\":").substringBefore(",").toLongOrNull() ?: 0
            }
            .take(50)
    }

    private fun escapeJson(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val TAG = "UsageStats"
    }
}
