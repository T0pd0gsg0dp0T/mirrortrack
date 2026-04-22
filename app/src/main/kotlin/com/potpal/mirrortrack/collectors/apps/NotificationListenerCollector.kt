package com.potpal.mirrortrack.collectors.apps

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.Collector
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@Singleton
class NotificationListenerCollector @Inject constructor() : Collector {
    override val id = "notification_listener"
    override val displayName = "Notification Listener"
    override val rationale =
        "Reports whether the notification listener is enabled and notification metadata counts."
    override val category = Category.APPS
    override val requiredPermissions: List<String> = emptyList()
    override val accessTier = AccessTier.SPECIAL_ACCESS
    override val defaultEnabled = false
    override val defaultPollInterval: Duration? = null
    override val defaultRetention: Duration = 30.days

    override suspend fun isAvailable(context: Context): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabled.contains(context.packageName)
    }

    override suspend fun collect(context: Context): List<DataPoint> {
        val enabled = isAvailable(context)
        val points = mutableListOf<DataPoint>()

        points.add(DataPoint.bool(id, category, "listener_enabled", enabled))

        if (enabled) {
            val entries = MirrorNotificationListenerService.drainEntries()
            points.add(DataPoint.long(id, category, "notification_count_since_last_poll", entries.size.toLong()))

            // Store per-notification metadata for unlock-latency analysis
            for (entry in entries) {
                points.add(DataPoint(
                    timestamp = entry.postTimeMs,
                    collectorId = id,
                    category = category,
                    key = "notif_posted",
                    value = entry.packageName,
                    valueType = com.potpal.mirrortrack.collectors.ValueType.STRING
                ))
            }

            Logger.d(TAG, "Listener enabled, ${entries.size} notifications")
        }

        return points
    }

    override fun stream(context: Context): Flow<DataPoint> = flow {
        while (true) {
            collect(context).forEach { emit(it) }
            delay(DRAIN_INTERVAL_MS)
        }
    }

    companion object {
        private const val TAG = "NLSCollector"
        private const val DRAIN_INTERVAL_MS = 60_000L
    }
}
