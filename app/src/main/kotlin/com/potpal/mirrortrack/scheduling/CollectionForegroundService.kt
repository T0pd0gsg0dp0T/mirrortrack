package com.potpal.mirrortrack.scheduling

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.potpal.mirrortrack.R
import com.potpal.mirrortrack.collectors.Collector
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
        startForegroundWithType(buildLockedNotification(), includeMicrophone = false)
        scope.launch { notificationUpdateLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REFRESH_STREAMS -> scope.launch { refreshStreamedCollectors() }
            ACTION_REFRESH_NOTIFICATION -> scope.launch { updateNotification() }
            ACTION_STOP -> {
                // Cancel-and-join stream jobs *without* blocking the main
                // thread. The cooperative cancellation in the mic loops
                // (ensureActive in tight inner loops) lets join finish
                // within milliseconds; we cap with a short withTimeout so
                // a misbehaving stream cannot keep the service alive.
                scope.launch {
                    try {
                        val jobs = streamJobs.values.toList()
                        withTimeout(STOP_TEARDOWN_TIMEOUT_MS) {
                            jobs.forEach { it.cancelAndJoin() }
                        }
                    } catch (_: TimeoutCancellationException) {
                        Logger.w(TAG, "Stream teardown timeout; forcing stop")
                    } catch (e: Exception) {
                        Logger.w(TAG, "Stream teardown failed", e)
                    } finally {
                        streamJobs.clear()
                        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
                        stopSelf()
                    }
                }
            }
            else -> scope.launch { refreshStreamedCollectors() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // scope.cancel() propagates cancellation to every stream job; the
        // cooperative ensureActive() checks in the mic sample loops let them
        // unwind within milliseconds. No blocking on the main thread here.
        scope.cancel()
        streamJobs.clear()
        super.onDestroy()
    }

    private suspend fun refreshStreamedCollectors() {
        if (!databaseHolder.isOpen()) {
            // FGS is already attached via onCreate; just refresh the visible
            // notification so the user sees the locked state instead of stale "0/0/0".
            postLockedNotification()
            return
        }

        val allCollectors = registry.all()
        val streamedCollectors = allCollectors.filter { it.defaultPollInterval == null }
        val availableCollectors = mutableSetOf<String>()
        var needsMicrophoneType = false

        for (collector in streamedCollectors) {
            val effectiveEnabled = prefs.isEnabledSync(collector.id) || collector.defaultEnabled
            if (effectiveEnabled && collector.isAvailable(applicationContext)) {
                availableCollectors += collector.id
                if (collector.requiresMicrophone()) {
                    needsMicrophoneType = true
                }
            }
        }

        val canRunMicrophoneStreams = startForegroundWithType(
            buildNotification(streamJobs.count { it.value.isActive }, 0),
            includeMicrophone = needsMicrophoneType
        )

        for (collector in streamedCollectors) {
            val canRunCollector = collector.id in availableCollectors &&
                (!collector.requiresMicrophone() || canRunMicrophoneStreams)

            if (canRunCollector) {
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

    private fun Collector.requiresMicrophone(): Boolean =
        Manifest.permission.RECORD_AUDIO in requiredPermissions

    private suspend fun notificationUpdateLoop() {
        while (true) {
            delay(60_000)
            updateNotification()
        }
    }

    private suspend fun updateNotification() {
        if (!databaseHolder.isOpen()) {
            postLockedNotification()
            return
        }
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
        } catch (e: Exception) {
            Logger.w(TAG, "Notification update failed", e)
        }
    }

    private fun postLockedNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildLockedNotification())
        } catch (e: Exception) {
            Logger.w(TAG, "Locked notification post failed", e)
        }
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
            .setShowWhen(false)
            .setContentIntent(pendingIntent)

        if (failedCollectors > 0) {
            builder.setSubText("$failedCollectors collector(s) failing")
        }

        return builder.build()
    }

    private fun buildLockedNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fgs_notification_title_locked))
            .setContentText(getString(R.string.fgs_notification_text_locked))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
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

    private fun startForegroundWithType(
        notification: Notification,
        includeMicrophone: Boolean
    ): Boolean {
        val requestedType = foregroundServiceType(includeMicrophone)
        if (includeMicrophone && !hasRecordAudioPermission()) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType(includeMicrophone = false)
            )
            return false
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, requestedType)
            true
        } catch (e: SecurityException) {
            if (!includeMicrophone) throw e

            Logger.w(TAG, "Microphone foreground service type unavailable; skipping microphone streams", e)
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType(includeMicrophone = false)
            )
            false
        }
    }

    private fun foregroundServiceType(includeMicrophone: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (includeMicrophone && hasRecordAudioPermission()) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return type
    }

    private fun hasRecordAudioPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CollectionFGS"
        private const val CHANNEL_ID = "mirrortrack_collection"
        private const val NOTIFICATION_ID = 1
        const val ACTION_REFRESH_STREAMS = "com.potpal.mirrortrack.REFRESH_STREAMS"
        const val ACTION_REFRESH_NOTIFICATION = "com.potpal.mirrortrack.REFRESH_NOTIFICATION"
        const val ACTION_STOP = "com.potpal.mirrortrack.STOP_COLLECTION"
        // Hard cap so a misbehaving mic stream cannot wedge the main thread
        // when the user toggles collection off.
        private const val STOP_TEARDOWN_TIMEOUT_MS = 1500L

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
            // Send ACTION_STOP first so the service can drain stream jobs
            // synchronously (including releasing the microphone) before
            // shutting itself down. Falls back to stopService below in case
            // the service has already stopped by the time the intent arrives.
            val intent = Intent(context, CollectionForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                context.stopService(Intent(context, CollectionForegroundService::class.java))
            }
        }
    }
}
