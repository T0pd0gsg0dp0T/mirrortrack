package com.potpal.mirrortrack.collectors

import kotlinx.serialization.Serializable

/**
 * Unified event shape. One DataPoint = one field value at one moment.
 *
 * A single polled BuildInfoCollector invocation produces ~15 DataPoints
 * (one per field: manufacturer, model, brand, etc.) all with the same
 * timestamp. This is deliberate — it lets the desktop side pivot on
 * (timestamp, collectorId) to reconstruct the original record while
 * keeping the storage schema completely static.
 *
 * `value` is always a String. Numeric types round-trip via toString()/toLong()
 * etc. Complex values (lists, nested objects) are JSON-encoded with
 * valueType = JSON. The valueType field is advisory for the UI and desktop
 * analysis tools; SQLCipher itself stores everything as TEXT.
 */
@Serializable
data class DataPoint(
    val timestamp: Long,
    val collectorId: String,
    val category: Category,
    val key: String,
    val value: String,
    val valueType: ValueType
) {
    companion object {
        fun string(collectorId: String, category: Category, key: String, value: String) =
            DataPoint(System.currentTimeMillis(), collectorId, category, key, value, ValueType.STRING)

        fun long(collectorId: String, category: Category, key: String, value: Long) =
            DataPoint(System.currentTimeMillis(), collectorId, category, key, value.toString(), ValueType.LONG)

        fun double(collectorId: String, category: Category, key: String, value: Double) =
            DataPoint(System.currentTimeMillis(), collectorId, category, key, value.toString(), ValueType.DOUBLE)

        fun bool(collectorId: String, category: Category, key: String, value: Boolean) =
            DataPoint(System.currentTimeMillis(), collectorId, category, key, value.toString(), ValueType.BOOLEAN)

        fun json(collectorId: String, category: Category, key: String, jsonValue: String) =
            DataPoint(System.currentTimeMillis(), collectorId, category, key, jsonValue, ValueType.JSON)
    }
}

enum class ValueType {
    STRING, LONG, DOUBLE, BOOLEAN, JSON
}
