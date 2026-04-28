package com.potpal.mirrortrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.potpal.mirrortrack.data.entities.DataPointEntity
import kotlinx.coroutines.flow.Flow

data class CollectorCount(val collectorId: String, val cnt: Long)

@Dao
interface DataPointDao {

    @Insert
    suspend fun insertAll(points: List<DataPointEntity>)

    @Query("SELECT * FROM data_points ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<DataPointEntity>>

    @Query(
        "SELECT * FROM data_points WHERE collectorId = :collectorId " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun byCollector(collectorId: String, limit: Int = 1000): List<DataPointEntity>

    @Query(
        "SELECT * FROM data_points WHERE category = :category " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun byCategory(category: String, limit: Int = 1000): List<DataPointEntity>

    @Query("SELECT COUNT(*) FROM data_points")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM data_points WHERE collectorId = :collectorId")
    suspend fun countByCollector(collectorId: String): Long

    /**
     * Retention cutoff. Deletes rows for a specific collector older than
     * `cutoffMs`. Called by the retention worker per-collector with that
     * collector's configured TTL.
     */
    @Query("DELETE FROM data_points WHERE collectorId = :collectorId AND timestamp < :cutoffMs")
    suspend fun purgeOlderThan(collectorId: String, cutoffMs: Long): Int

    @Query("SELECT * FROM data_points WHERE rowId = :rowId LIMIT 1")
    suspend fun byRowId(rowId: Long): DataPointEntity?

    @Query("SELECT collectorId, COUNT(*) as cnt FROM data_points WHERE timestamp > :sinceMs GROUP BY collectorId")
    suspend fun countByCollectorSince(sinceMs: Long): List<CollectorCount>

    @Query("SELECT * FROM data_points WHERE collectorId = :collectorId AND key = :key ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestByCollectorAndKey(collectorId: String, key: String): DataPointEntity?

    @Query("SELECT DISTINCT collectorId, key, value, valueType, timestamp, category, rowId FROM data_points dp1 WHERE timestamp = (SELECT MAX(timestamp) FROM data_points dp2 WHERE dp2.collectorId = dp1.collectorId AND dp2.key = dp1.key)")
    suspend fun latestPerCollectorKey(): List<DataPointEntity>

    @Query("SELECT * FROM data_points WHERE collectorId = :collectorId AND timestamp >= :sinceMs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun byCollectorSince(collectorId: String, sinceMs: Long, limit: Int = 10_000): List<DataPointEntity>

    @Query("SELECT * FROM data_points WHERE collectorId = :collectorId AND key = :key AND timestamp >= :sinceMs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun byCollectorKeySince(collectorId: String, key: String, sinceMs: Long, limit: Int = 10_000): List<DataPointEntity>

    @Query("SELECT COUNT(*) FROM data_points WHERE timestamp >= :sinceMs")
    suspend fun countSince(sinceMs: Long): Long

    @Query("DELETE FROM data_points")
    suspend fun purgeAll()

    @Query("SELECT * FROM data_points WHERE rowId > :afterRowId ORDER BY rowId ASC LIMIT :limit")
    suspend fun pageAscending(afterRowId: Long = 0, limit: Int = 5000): List<DataPointEntity>
}
