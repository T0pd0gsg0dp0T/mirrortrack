package com.potpal.mirrortrack.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.potpal.mirrortrack.collectors.CollectorRegistry
import com.potpal.mirrortrack.collectors.Ingestor
import com.potpal.mirrortrack.data.DatabaseHolder
import com.potpal.mirrortrack.settings.CollectorPreferences
import com.potpal.mirrortrack.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class PolledCollectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val registry: CollectorRegistry,
    private val ingestor: Ingestor,
    private val databaseHolder: DatabaseHolder,
    private val prefs: CollectorPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!databaseHolder.isOpen()) {
            Logger.d(TAG, "DB not open, skipping polled collection")
            return Result.retry()
        }

        // Master kill-switch: if the user has toggled Collection Service off,
        // never open mics/sensors from a polled worker either, even if
        // WorkManager hasn't finished cancelling the schedule yet.
        if (!prefs.isServiceEnabled().first()) {
            Logger.d(TAG, "Service disabled, skipping polled collection")
            return Result.success()
        }

        val collectorId = inputData.getString(KEY_COLLECTOR_ID) ?: return Result.failure()
        val collector = registry.byId(collectorId) ?: return Result.failure()

        if (!collector.isAvailable(applicationContext)) {
            Logger.d(TAG, "Collector $collectorId not available, skipping")
            return Result.success()
        }

        val enabled = prefs.isEnabledSync(collectorId) || collector.defaultEnabled
        if (!enabled) {
            Logger.d(TAG, "Collector $collectorId not enabled, skipping")
            return Result.success()
        }

        return try {
            val points = collector.collect(applicationContext)
            if (points.isNotEmpty()) {
                ingestor.submitAll(points)
                Logger.d(TAG, "Collected ${points.size} points from $collectorId")
            }
            CollectorHealthTracker.recordSuccess(collectorId)
            Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, "Collector $collectorId failed", e)
            CollectorHealthTracker.recordFailure(collectorId, e.message ?: "unknown")
            Result.success() // Don't retry individual collector failures
        }
    }

    companion object {
        private const val TAG = "PolledWorker"
        const val KEY_COLLECTOR_ID = "collector_id"
    }
}
