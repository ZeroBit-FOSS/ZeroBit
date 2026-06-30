/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import com.vibhor1102.zerobit.openmacro.runtime.BatteryTemperatureComparison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryTemperatureStateTest {
    @Test
    fun acceptsOnlyBoundedRawTenths() {
        assertEquals(-1_000, validBatteryTemperatureTenthsOrNull(-1_000))
        assertEquals(1_000, validBatteryTemperatureTenthsOrNull(1_000))
        assertNull(validBatteryTemperatureTenthsOrNull(-1_001))
        assertNull(validBatteryTemperatureTenthsOrNull(1_001))
        assertNull(validBatteryTemperatureTenthsOrNull(Int.MIN_VALUE))
    }

    @Test
    fun comparesRawTenthsExactly() {
        assertTrue(batteryTemperatureMatches(404, 405, BatteryTemperatureComparison.BELOW))
        assertTrue(batteryTemperatureMatches(406, 405, BatteryTemperatureComparison.ABOVE))
        assertTrue(batteryTemperatureMatches(405, 405, BatteryTemperatureComparison.EQUALS))
        assertFalse(batteryTemperatureMatches(404, 405, BatteryTemperatureComparison.EQUALS))
    }

    @Test
    fun formatsTenthsWithoutFloatingPointArtifacts() {
        assertEquals("40.5", formatTenthsCelsius(405))
        assertEquals("40", formatTenthsCelsius(400))
        assertEquals("-0.5", formatTenthsCelsius(-5))
    }

    @Test
    fun suppressesInitialAndDuplicateSamplesThenEmitsAboveCrossings() {
        val tracker = BatteryTemperatureTransitionTracker(
            thresholdTenths = 400,
            comparison = BatteryTemperatureComparison.ABOVE,
        )

        assertNull(tracker.update(390))
        assertNull(tracker.update(400))
        assertEquals(401, tracker.update(401))
        assertNull(tracker.update(401))
        assertNull(tracker.update(399))
        assertEquals(410, tracker.update(410))
    }

    @Test
    fun emitsBelowAndEqualOnlyWhenEnteringTheirMatchingState() {
        val below = BatteryTemperatureTransitionTracker(400, BatteryTemperatureComparison.BELOW)
        assertNull(below.update(410))
        assertEquals(399, below.update(399))
        assertNull(below.update(390))

        val equal = BatteryTemperatureTransitionTracker(400, BatteryTemperatureComparison.EQUALS)
        assertNull(equal.update(390))
        assertEquals(400, equal.update(400))
        assertNull(equal.update(400))
    }
}
