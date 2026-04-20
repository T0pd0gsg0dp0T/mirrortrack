package com.potpal.mirrortrack

import com.potpal.mirrortrack.scheduling.CollectorHealthTracker
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class CollectorHealthTrackerTest {

    @Before
    fun setup() {
        CollectorHealthTracker.clear()
    }

    @Test
    fun `recordSuccess clears consecutive failures`() {
        CollectorHealthTracker.recordFailure("test_collector", "error1")
        CollectorHealthTracker.recordFailure("test_collector", "error2")
        assertEquals(2, CollectorHealthTracker.allRecords()["test_collector"]?.consecutiveFailures)

        CollectorHealthTracker.recordSuccess("test_collector")
        assertEquals(0, CollectorHealthTracker.allRecords()["test_collector"]?.consecutiveFailures)
    }

    @Test
    fun `recordFailure increments consecutive failures`() {
        CollectorHealthTracker.recordFailure("c1", "err1")
        assertEquals(1, CollectorHealthTracker.allRecords()["c1"]?.consecutiveFailures)

        CollectorHealthTracker.recordFailure("c1", "err2")
        assertEquals(2, CollectorHealthTracker.allRecords()["c1"]?.consecutiveFailures)
        assertEquals("err2", CollectorHealthTracker.allRecords()["c1"]?.lastError)
    }

    @Test
    fun `failedCollectors returns only collectors with failures`() {
        CollectorHealthTracker.recordSuccess("good_collector")
        CollectorHealthTracker.recordFailure("bad_collector", "broken")

        val failed = CollectorHealthTracker.failedCollectors()
        assertEquals(1, failed.size)
        assertEquals("bad_collector", failed[0].collectorId)
    }

    @Test
    fun `recordFailure preserves last success time`() {
        CollectorHealthTracker.recordSuccess("c1")
        val successTime = CollectorHealthTracker.allRecords()["c1"]?.lastSuccessMs ?: 0L
        assertTrue(successTime > 0)

        CollectorHealthTracker.recordFailure("c1", "err")
        assertEquals(successTime, CollectorHealthTracker.allRecords()["c1"]?.lastSuccessMs)
    }
}
