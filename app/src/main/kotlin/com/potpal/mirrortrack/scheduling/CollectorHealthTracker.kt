package com.potpal.mirrortrack.scheduling

/**
 * In-memory tracker of collector success/failure state.
 * Accessible from the foreground service notification and settings UI
 * to surface which collectors are broken.
 */
object CollectorHealthTracker {

    data class HealthRecord(
        val collectorId: String,
        val lastSuccessMs: Long = 0L,
        val lastFailureMs: Long = 0L,
        val lastError: String? = null,
        val consecutiveFailures: Int = 0
    )

    private val records = mutableMapOf<String, HealthRecord>()

    fun recordSuccess(collectorId: String) {
        synchronized(records) {
            records[collectorId] = HealthRecord(
                collectorId = collectorId,
                lastSuccessMs = System.currentTimeMillis(),
                consecutiveFailures = 0
            )
        }
    }

    fun recordFailure(collectorId: String, error: String) {
        synchronized(records) {
            val existing = records[collectorId]
            records[collectorId] = HealthRecord(
                collectorId = collectorId,
                lastSuccessMs = existing?.lastSuccessMs ?: 0L,
                lastFailureMs = System.currentTimeMillis(),
                lastError = error,
                consecutiveFailures = (existing?.consecutiveFailures ?: 0) + 1
            )
        }
    }

    fun failedCollectors(): List<HealthRecord> {
        synchronized(records) {
            return records.values.filter { it.consecutiveFailures > 0 }.toList()
        }
    }

    fun allRecords(): Map<String, HealthRecord> {
        synchronized(records) {
            return records.toMap()
        }
    }

    fun clear() {
        synchronized(records) {
            records.clear()
        }
    }
}
