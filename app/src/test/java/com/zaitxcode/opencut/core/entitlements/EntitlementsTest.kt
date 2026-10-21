package com.zaitxcode.opencut.core.entitlements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the promise that the editor is free.
 *
 * If someone ever adds a paid tier, these tests fail before the change ships.
 */
class EntitlementsTest {

    @Test
    fun `every feature is unlocked`() {
        assertTrue(Feature.entries.isNotEmpty())
        Feature.entries.forEach { feature ->
            assertTrue("$feature must be free", Entitlements.isUnlocked(feature))
        }
    }

    @Test
    fun `the plan copy advertises no price`() {
        val copy = Entitlements.PLAN_NAME + " " + Entitlements.PLAN_DETAIL
        listOf("$", "€", "£", "¥").forEach { symbol ->
            assertTrue("plan copy contains '$symbol'", !copy.contains(symbol))
        }
        assertTrue("plan copy should say the features are free", copy.lowercase().contains("free"))
    }

    @Test
    fun `grouping covers the whole catalogue`() {
        val grouped = Entitlements.grouped().values.flatten()
        assertEquals(Feature.entries.size, grouped.size)
        assertEquals(Feature.entries.toSet(), grouped.toSet())
    }

    @Test
    fun `premium style capabilities are present`() {
        val ids = Feature.entries.toSet()
        listOf(
            Feature.EXPORT_4K,
            Feature.NO_WATERMARK,
            Feature.KEYFRAMES,
            Feature.MULTI_TRACK,
            Feature.OFFLINE,
            Feature.NO_ACCOUNT_REQUIRED,
        ).forEach { assertTrue("missing $it", it in ids) }
    }
}
