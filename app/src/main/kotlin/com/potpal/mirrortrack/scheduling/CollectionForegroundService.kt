package com.potpal.mirrortrack.scheduling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.potpal.mirrortrack.R
import com.potpal.mirrortrack.collectors.CollectorRegistry
import com.potpal.mirrortrack.collectors.Ingestor
import com.potpal.mirrortrack.data.DataPointDao
import com.potpal.mirrortrack.data.DatabaseHolder
import com.potpal.mirrortrack.settings.CollectorPreferences
import com.potpal.mirrortrack.ui.MainActivity
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CollectionForegroundService : Service() {

    @Inject lateinit var registry: CollectorRegistry
    @Inject lateinit var ingestor: Ingestor
    @Inject lateinit var prefs: CollectorPreferences
    @Inject lateinit var databaseHolder: DatabaseHolder
    @Inject lateinit var dao: DataPointDao

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val streamJobs = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0, 0))
        scope.launch { notificationUpdateLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH_STREAMS -> scope.launch { refreshStreamedCollectors() }
            ACTION_REFRESH_NOTIFICATION -> scope.launch { updateNotification() }
            else -> scope.launch { refreshStreamedCollectors() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        streamJobs.clear()
        super.onDestroy()
    }

    private suspend fun refreshStreamedCollectors() {
        if (!databaseHolder.isOpen()) return

        val allCollectors = registry.all()
        val streamedCollectors = allCollectors.filter { it.defaultPollInterval == null }

        for (collector in streamedCollectors) {
            val effectiveEnabled = prefs.isEnabledSync(collector.id) || collector.defaultEnabled

            if (effectiveEnabled && collector.isAvailable(applicationContext)) {
                if (streamJobs[collector.id]?.isActive != true) {
                    streamJobs[collector.id] = scope.launch {
                        try {
                            collector.stream(applicationContext).collect { point ->
                                ingestor.submit(point)
                                CollectorHealthTracker.recordSuccess(collector.id)
                            }
                        } catch (_: CancellationException) {
                            Logger.d(TAG, "Stream ${collector.id} stopped")
                        } catch (e: Exception) {
                            Logger.w(TAG, "Stream ${collector.id} failed", e)
                            CollectorHealthTracker.recordFailure(collector.id, e.message ?: "unknown")
                        }
                    }
                    Logger.d(TAG, "Started stream: ${collector.id}")
                }
            } else {
                streamJobs[collector.id]?.cancel()
                streamJobs.remove(collector.id)
                CollectorHealthTracker.clear(collector.id)
            }
        }
        updateNotification()
    }

    private suspend fun notificationUpdateLoop() {
        while (true) {
            delay(60_000)
            updateNotification()
        }
    }

    private suspend fun updateNotification() {
        if (!databaseHolder.isOpen()) return
        try {
            val streamCount = streamJobs.count { it.value.isActive }
            val todayMs = System.currentTimeMillis() - 86_400_000
            val pointsToday = dao.countSince(todayMs)
            val totalPoints = dao.count()
            val failedCount = CollectorHealthTracker.failedCollectors().size
            val showDetails = prefs.isCollectionNotificationDetailsEnabledSync()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(
                NOTIFICATION_ID,
                buildNotification(
                    streams = streamCount,
                    pointsToday = pointsToday,
                    totalPoints = totalPoints,
                    failedCollectors = failedCount,
                    showDetails = showDetails
                )
            )
        } catch (_: Exception) { }
    }

    private fun buildNotification(
        streams: Int,
        pointsToday: Long,
        totalPoints: Long = 0L,
        failedCollectors: Int = 0,
        showDetails: Boolean = true
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = if (showDetails) {
            getString(
                R.string.fgs_notification_text,
                formatNotificationCount(totalPoints),
                formatNotificationCount(pointsToday),
                streams
            )
        } else {
            getString(R.string.fgs_notification_text_private)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setContentIntent(pendingIntent)

        if (failedCollectors > 0) {
            builder.setSubText("$failedCollectors collector(s) failing")
        }

        return builder.build()
    }

    private fun formatNotificationCount(count: Long): String =
        when {
            count >= 1_000_000 -> "${count / 1_000_000}M"
            count >= 1_000 -> "${count / 1_000}K"
            else -> count.toString()
        }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fgs_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.fgs_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "CollectionFGS"
        private const val CHANNEL_ID = "mirrortrack_collection"
        private const val NOTIFICATION_ID = 1
        const val ACTION_REFRESH_STREAMS = "com.potpal.mirrortrack.REFRESH_STREAMS"
        const val ACTION_REFRESH_NOTIFICATION = "com.potpal.mirrortrack.REFRESH_NOTIFICATION"

        fun startIfEnabled(context: Context) {
            val intent = Intent(context, CollectionForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun refreshStreams(context: Context) {
            val intent = Intent(context, CollectionForegroundService::class.java).apply {
                action = ACTION_REFRESH_STREAMS
            }
            context.startForegroundService(intent)
        }

        fun refreshNotification(context: Context) {
            val intent = Intent(context, CollectionForegroundService::class.java).apply {
                action = ACTION_REFRESH_NOTIFICATION
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CollectionForegroundService::class.java))
        }
    }
}
