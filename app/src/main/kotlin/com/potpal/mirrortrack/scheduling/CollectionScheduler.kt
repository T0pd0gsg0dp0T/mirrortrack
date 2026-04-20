package com.potpal.mirrortrack.scheduling

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.potpal.mirrortrack.collectors.CollectorRegistry
import com.potpal.mirrortrack.settings.CollectorPreferences
import com.potpal.mirrortrack.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

@Singleton
class CollectionScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: CollectorRegistry,
    private val prefs: CollectorPreferences
) {
    suspend fun refreshAll() {
        val wm = WorkManager.getInstance(context)

        for (collector in registry.all()) {
            val pollInterval = collector.defaultPollInterval ?: continue // streamed, skip

            val enabled = prefs.isEnabledSync(collector.id) || collector.defaultEnabled
            if (!enabled || !collector.isAvailable(context)) {
                wm.cancelUniqueWork("poll_${collector.id}")
                continue
            }

            val overrideMinutes = prefs.getPollIntervalMinutesSync(collector.id)
            val intervalMinutes = overrideMinutes?.toLong()
                ?: pollInterval.inWholeMinutes.coerceAtLeast(15)

            val request = PeriodicWorkRequestBuilder<PolledCollectionWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setInputData(workDataOf(PolledCollectionWorker.KEY_COLLECTOR_ID to collector.id))
                .setConstraints(Constraints.Builder().build())
                .build()

            wm.enqueueUniquePeriodicWork(
                "poll_${collector.id}",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Logger.d(TAG, "Scheduled ${collector.id} every ${intervalMinutes}m")
        }

        // Refresh streamed collectors in FGS
        CollectionForegroundService.refreshStreams(context)
    }

    fun cancelAll() {
        val wm = WorkManager.getInstance(context)
        for (collector in registry.all()) {
            wm.cancelUniqueWork("poll_${collector.id}")
        }
    }

    companion object {
        private const val TAG = "Scheduler"
    }
}
