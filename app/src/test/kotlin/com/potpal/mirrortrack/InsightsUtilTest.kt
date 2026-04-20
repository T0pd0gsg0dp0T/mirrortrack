package com.potpal.mirrortrack

import com.potpal.mirrortrack.ui.insights.relativeTime
import org.junit.Test
import org.junit.Assert.*

class InsightsUtilTest {

    @Test
    fun `relativeTime just now for recent timestamp`() {
        val now = System.currentTimeMillis()
        assertEquals("just now", relativeTime(now - 30_000))
    }

    @Test
    fun `relativeTime minutes ago`() {
        val now = System.currentTimeMillis()
        val result = relativeTime(now - 5 * 60_000)
        assertEquals("5m ago", result)
    }

    @Test
    fun `relativeTime hours ago`() {
        val now = System.currentTimeMillis()
        val result = relativeTime(now - 3 * 3_600_000)
        assertEquals("3h ago", result)
    }

    @Test
    fun `relativeTime days ago`() {
        val now = System.currentTimeMillis()
        val result = relativeTime(now - 2 * 86_400_000)
        assertEquals("2d ago", result)
    }
}
