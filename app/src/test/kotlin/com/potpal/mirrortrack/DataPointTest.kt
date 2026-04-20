package com.potpal.mirrortrack

import com.potpal.mirrortrack.collectors.Category
import com.potpal.mirrortrack.collectors.DataPoint
import com.potpal.mirrortrack.collectors.ValueType
import org.junit.Test
import org.junit.Assert.*

class DataPointTest {

    @Test
    fun `string factory creates STRING type`() {
        val dp = DataPoint.string("test", Category.DEVICE_IDENTITY, "key1", "value1")
        assertEquals("test", dp.collectorId)
        assertEquals(Category.DEVICE_IDENTITY, dp.category)
        assertEquals("key1", dp.key)
        assertEquals("value1", dp.value)
        assertEquals(ValueType.STRING, dp.valueType)
        assertTrue(dp.timestamp > 0)
    }

    @Test
    fun `long factory creates LONG type with string value`() {
        val dp = DataPoint.long("test", Category.BEHAVIORAL, "count", 42L)
        assertEquals("42", dp.value)
        assertEquals(ValueType.LONG, dp.valueType)
    }

    @Test
    fun `double factory creates DOUBLE type`() {
        val dp = DataPoint.double("test", Category.SENSORS, "temp", 36.5)
        assertEquals("36.5", dp.value)
        assertEquals(ValueType.DOUBLE, dp.valueType)
    }

    @Test
    fun `bool factory creates BOOLEAN type`() {
        val dp = DataPoint.bool("test", Category.BEHAVIORAL, "active", true)
        assertEquals("true", dp.value)
        assertEquals(ValueType.BOOLEAN, dp.valueType)
    }

    @Test
    fun `json factory creates JSON type`() {
        val json = """{"key":"value"}"""
        val dp = DataPoint.json("test", Category.APPS, "data", json)
        assertEquals(json, dp.value)
        assertEquals(ValueType.JSON, dp.valueType)
    }

    @Test
    fun `timestamp is set to current time`() {
        val before = System.currentTimeMillis()
        val dp = DataPoint.string("test", Category.NETWORK, "k", "v")
        val after = System.currentTimeMillis()
        assertTrue(dp.timestamp in before..after)
    }
}
