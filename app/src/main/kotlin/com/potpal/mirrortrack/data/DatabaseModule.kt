package com.potpal.mirrortrack.data

import android.content.Context
import com.potpal.mirrortrack.collectors.Ingestor
import com.potpal.mirrortrack.scheduling.RetentionWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle:
 *  - DatabaseHolder starts CLOSED. Hilt graph still builds — Ingestor and
 *    DAOs are injected lazily via DatabaseHolder.dao().
 *  - User enters passphrase → UnlockViewModel calls CryptoManager.deriveKey →
 *    passes the key to DatabaseHolder.open(key). Key is zeroed after
 *    SupportOpenHelperFactory consumes it.
 *  - All DAO access goes through DatabaseHolder, which throws if the DB is
 *    still locked. This is intentional: no collector should ever run before
 *    the user unlocks.
 *  - On app close (or panic-lock), DatabaseHolder.close() drops the reference
 *    and SQLCipher wipes its in-memory key material.
 */
@Singleton
class DatabaseHolder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    @Volatile private var db: MirrorDatabase? = null

    suspend fun open(rawKey: ByteArray) = mutex.withLock {
        if (db != null) return@withLock
        db = MirrorDatabase.build(context, rawKey)
        // SupportOpenHelperFactory has copied the bytes internally; wipe ours.
        rawKey.fill(0)
        // Schedule daily retention purge now that DB is open
        RetentionWorker.schedule(context)
    }

    fun isOpen(): Boolean = db != null

    fun requireDb(): MirrorDatabase =
        db ?: throw IllegalStateException("Database not unlocked")

    suspend fun close() = mutex.withLock {
        db?.close()
        db = null
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDataPointDao(holder: DatabaseHolder): DataPointDao =
        LazyDataPointDao(holder)

    @Provides
    @Singleton
    fun provideIngestor(dao: DataPointDao): Ingestor = Ingestor(dao)
}

/**
 * Thin proxy that defers to DatabaseHolder.requireDb().dataPointDao() on each
 * call. The indirection lets us inject DataPointDao into Ingestor before the
 * DB is opened, so the DI graph is static.
 *
 * If a collector fires before unlock, `requireDb()` throws — which bubbles up
 * through Ingestor.submit and gets caught by the collector's own error handler.
 * We log-and-drop rather than crash.
 */
private class LazyDataPointDao(private val holder: DatabaseHolder) : DataPointDao {
    private fun real(): DataPointDao = holder.requireDb().dataPointDao()

    override suspend fun insertAll(points: List<com.potpal.mirrortrack.data.entities.DataPointEntity>) =
        real().insertAll(points)

    override fun observeRecent(limit: Int) = real().observeRecent(limit)

    override suspend fun byCollector(collectorId: String, limit: Int) =
        real().byCollector(collectorId, limit)

    override suspend fun byCategory(category: String, limit: Int) =
        real().byCategory(category, limit)

    override suspend fun count(): Long = real().count()

    override suspend fun countByCollector(collectorId: String) =
        real().countByCollector(collectorId)

    override suspend fun purgeOlderThan(collectorId: String, cutoffMs: Long) =
        real().purgeOlderThan(collectorId, cutoffMs)

    override suspend fun byRowId(rowId: Long) = real().byRowId(rowId)

    override suspend fun countByCollectorSince(sinceMs: Long) =
        real().countByCollectorSince(sinceMs)

    override suspend fun latestByCollectorAndKey(collectorId: String, key: String) =
        real().latestByCollectorAndKey(collectorId, key)

    override suspend fun latestPerCollectorKey() = real().latestPerCollectorKey()

    override suspend fun byCollectorSince(collectorId: String, sinceMs: Long, limit: Int) =
        real().byCollectorSince(collectorId, sinceMs, limit)

    override suspend fun byCollectorKeySince(collectorId: String, key: String, sinceMs: Long, limit: Int) =
        real().byCollectorKeySince(collectorId, key, sinceMs, limit)

    override suspend fun countSince(sinceMs: Long) = real().countSince(sinceMs)

    override suspend fun purgeAll() = real().purgeAll()
}
