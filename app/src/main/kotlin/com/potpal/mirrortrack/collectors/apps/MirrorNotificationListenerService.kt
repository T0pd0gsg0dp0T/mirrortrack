package com.potpal.mirrortrack.collectors.apps

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.potpal.mirrortrack.util.Logger

/**
 * Notification listener service that captures notification metadata.
 *
 * Default mode: metadata only — no title or text body is stored.
 *
 * The [NotificationListenerCollector] reads from this service's static state during its
 * polling cadence.
 *
 * <!-- AndroidManifest.xml entry required:
 *
 * <service
 *     android:name=".collectors.apps.MirrorNotificationListenerService"
 *     android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
 *     android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.service.notification.NotificationListenerService" />
 *     </intent-filter>
 * </service>
 *
 * -->
 */
class MirrorNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val notification = sbn.notification ?: return
        val extras = notification.extras

        val entry = NotificationEntry(
            packageName = sbn.packageName.orEmpty(),
            postTimeMs = sbn.postTime,
            notificationCategory = notification.category.orEmpty(),
            priority = notification.priority,
            titlePresent = extras?.getCharSequence("android.title") != null,
            textPresent = extras?.getCharSequence("android.text") != null,
            actionCount = notification.actions?.size ?: 0,
            timestampMs = System.currentTimeMillis(),
        )

        synchronized(lock) {
            notifications.add(entry)
            // Trim to bounded size, dropping oldest entries
            while (notifications.size > MAX_ENTRIES) {
                notifications.removeAt(0)
            }
        }

        Logger.d(TAG, "Notification captured from ${entry.packageName}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Intentionally not tracked
    }

    /**
     * Metadata-only snapshot of a posted notification.
     */
    data class NotificationEntry(
        val packageName: String,
        val postTimeMs: Long,
        val notificationCategory: String,
        val priority: Int,
        val titlePresent: Boolean,
        val textPresent: Boolean,
        val actionCount: Int,
        val timestampMs: Long,
    )

    companion object {
        private const val TAG = "MirrorNLS"
        private const val MAX_ENTRIES = 500

        private val lock = Any()
        private val notifications = mutableListOf<NotificationEntry>()

        /**
         * Returns the number of notifications captured since the last drain and clears the list.
         */
        fun drainCount(): Int {
            synchronized(lock) {
                val count = notifications.size
                notifications.clear()
                return count
            }
        }

        /**
         * Returns a snapshot of all captured entries and clears the internal list.
         */
        fun drainEntries(): List<NotificationEntry> {
            synchronized(lock) {
                val snapshot = notifications.toList()
                notifications.clear()
                return snapshot
            }
        }

        /**
         * Returns the current count without draining.
         */
        fun peekCount(): Int {
            synchronized(lock) {
                return notifications.size
            }
        }
    }
}
