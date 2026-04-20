package com.potpal.mirrortrack.scheduling

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.potpal.mirrortrack.collectors.CollectorRegistry
import com.potpal.mirrortrack.data.DataPointDao
import com.potpal.mirrortrack.data.DatabaseHolder
import com.potpal.mirrortrack.settings.CollectorPreferences
import com.potpal.mirrortrack.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val registry: CollectorRegistry,
    private val dao: DataPointDao,
    private val databaseHolder: DatabaseHolder,
    private val prefs: CollectorPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!databaseHolder.isOpen()) return Result.retry()

        var totalPurged = 0
        val now = System.currentTimeMillis()

        for (collector in registry.all()) {
            val retention = collector.defaultRetention ?: continue

            // Check for user override
            val overrideDays = prefs.getRetentionDays(collector.id).first()
            val retentionMs = if (overrideDays != null) {
                overrideDays * 86_400_000L
            } else {
                retention.inWholeMilliseconds
            }

            val cutoff = now - retentionMs
            val purged = dao.purgeOlderThan(collector.id, cutoff)
            totalPurged += purged
        }

        Logger.d(TAG, "Retention purge complete: $totalPurged rows deleted")
        return Result.success()
    }

    companion object {
        private const val TAG = "RetentionWorker"
        private const val WORK_NAME = "retention_purge"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(
                24, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
