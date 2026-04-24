package com.potpal.mirrortrack.collectors

import com.potpal.mirrortrack.data.DataPointDao
import com.potpal.mirrortrack.data.entities.DataPointEntity
import com.potpal.mirrortrack.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central write gateway for all DataPoints.
 *
 * Purpose: decouple collection rate from DB write rate. Sensors at 50 Hz
 * would otherwise hammer SQLCipher; we batch instead.
 *
 * Strategy:
 *  - Channel-backed in-memory buffer (bounded, dropOldest on overflow).
 *  - Flush trigger: buffer >= FLUSH_THRESHOLD rows OR periodic flush tick.
 *  - Flush is a single batched insert transaction.
 *
 * Backpressure note: the channel is intentionally bounded with DROP_OLDEST.
 * If the DB falls behind (device under heavy load, user locked it and
 * Keystore refused the DB key), we prefer losing the oldest sensor points
 * over unbounded memory growth. Gaps are recoverable; OOM crash is not.
 */
@Singleton
class Ingestor @Inject constructor(
    private val dao: DataPointDao
) {
    companion object {
        private const val TAG = "Ingestor"
        private const val FLUSH_THRESHOLD = 25
        private const val BUFFER_CAPACITY = 2_000
        private const val FLUSH_INTERVAL_MS = 15_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channel = Channel<DataPoint>(
        capacity = BUFFER_CAPACITY,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    private val flushMutex = Mutex()
    private val pending = mutableListOf<DataPoint>()

    @Volatile var droppedCount: Long = 0L
        private set
    @Volatile var totalIngested: Long = 0L
        private set

    init {
        scope.launch { consumeLoop() }
        scope.launch { periodicFlushLoop() }
    }

    suspend fun submit(point: DataPoint) {
        channel.send(point)
        totalIngested++
    }

    suspend fun submitAll(points: List<DataPoint>) {
        for (point in points) {
            channel.send(point)
            totalIngested++
        }
    }

    private suspend fun consumeLoop() {
        channel.consumeAsFlow().collect { point ->
            flushMutex.withLock {
                pending.add(point)
                if (pending.size >= FLUSH_THRESHOLD) {
                    flushLocked()
                }
            }
        }
    }

    private suspend fun periodicFlushLoop() {
        while (true) {
            kotlinx.coroutines.delay(FLUSH_INTERVAL_MS)
            flushMutex.withLock { flushLocked() }
        }
    }

    @Volatile private var totalFlushed: Long = 0L

    /** Caller must hold [flushMutex]. */
    private suspend fun flushLocked() {
        if (pending.isEmpty()) return
        val batch = pending.map { it.toEntity() }
        pending.clear()
        withContext(Dispatchers.IO) {
            dao.insertAll(batch)
        }
        totalFlushed += batch.size
        val gap = totalIngested - totalFlushed
        if (gap > BUFFER_CAPACITY / 2) {
            droppedCount = gap
            Logger.w(TAG, "Buffer pressure: ~$gap points may have been dropped (submitted=$totalIngested, flushed=$totalFlushed)")
        }
    }

    /** Force-flush. Call before app shutdown / export. */
    suspend fun flush() {
        flushMutex.withLock { flushLocked() }
    }

    private fun DataPoint.toEntity() = DataPointEntity(
        timestamp = timestamp,
        collectorId = collectorId,
        category = category.name,
        key = key,
        value = value,
        valueType = valueType.name
    )
}
