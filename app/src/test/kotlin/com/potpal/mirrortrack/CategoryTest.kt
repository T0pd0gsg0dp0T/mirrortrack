package com.potpal.mirrortrack

import com.potpal.mirrortrack.collectors.AccessTier
import com.potpal.mirrortrack.collectors.Category
import org.junit.Test
import org.junit.Assert.*

class CategoryTest {

    @Test
    fun `all categories have unique names`() {
        val names = Category.entries.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `all categories have display names`() {
        for (cat in Category.entries) {
            assertTrue("Category ${cat.name} missing displayName", cat.displayName.isNotBlank())
        }
    }

    @Test
    fun `access tiers are ordered by privilege`() {
        val tiers = AccessTier.entries
        assertTrue(tiers.indexOf(AccessTier.NONE) < tiers.indexOf(AccessTier.RUNTIME))
        assertTrue(tiers.indexOf(AccessTier.RUNTIME) < tiers.indexOf(AccessTier.SPECIAL_ACCESS))
        assertTrue(tiers.indexOf(AccessTier.SPECIAL_ACCESS) < tiers.indexOf(AccessTier.RESTRICTED))
        assertTrue(tiers.indexOf(AccessTier.RESTRICTED) < tiers.indexOf(AccessTier.ADB))
    }
}
