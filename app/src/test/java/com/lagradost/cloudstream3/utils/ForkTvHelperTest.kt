package com.lagradost.cloudstream3.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic tests for the TV helpers added by the Tik TV fork.
 *
 * [ForkTvHelper.isWithin] is covered by the instrumented test because it walks real [android.view.View] parents.
 */
class ForkTvHelperTest {
    @Test
    fun `television gets the fork overscan default`() {
        assertEquals(24, ForkTvHelper.defaultOverscanDp(isTv = true, tvDefaultDp = 24))
    }

    @Test
    fun `phones keep cloudstreams zero overscan default`() {
        assertEquals(0, ForkTvHelper.defaultOverscanDp(isTv = false, tvDefaultDp = 24))
    }

    @Test
    fun `overscan default is clamped to a sane range`() {
        assertEquals(0, ForkTvHelper.defaultOverscanDp(isTv = true, tvDefaultDp = -40))
        assertEquals(100, ForkTvHelper.defaultOverscanDp(isTv = true, tvDefaultDp = 900))
    }
}
