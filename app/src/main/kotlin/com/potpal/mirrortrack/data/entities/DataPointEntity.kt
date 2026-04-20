package com.potpal.mirrortrack.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The one and only table.
 *
 * Indices reflect the three dominant query patterns:
 *   1. "Show me everything for collector X in time range" → (collectorId, timestamp)
 *   2. "Show me everything in category Y in time range"   → (category, timestamp)
 *   3. "Time-scrub the live feed"                         → (timestamp)
 *
 * Rows are append-only. No updates, no in-place deletes except by the
 * retention worker, which deletes by (collectorId, timestamp < cutoff).
 */
@Entity(
    tableName = "data_points",
    indices = [
        Index(value = ["collectorId", "timestamp"]),
        Index(value = ["category", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class DataPointEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val timestamp: Long,
    val collectorId: String,
    val category: String,
    val key: String,
    val value: String,
    val valueType: String
)
