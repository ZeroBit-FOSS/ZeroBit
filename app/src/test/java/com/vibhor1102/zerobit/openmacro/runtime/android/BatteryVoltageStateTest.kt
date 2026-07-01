/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import com.vibhor1102.zerobit.openmacro.runtime.BatteryVoltageComparison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryVoltageStateTest {
    @Test
    fun acceptsOnlyBoundedMillivolts() {
        assertEquals(0, validBatteryMillivoltsOrNull(0))
        assertEquals(20_000, validBatteryMillivoltsOrNull(20_000))
        assertNull(validBatteryMillivoltsOrNull(-1))
        assertNull(validBatteryMillivoltsOrNull(20_001))
        assertNull(validBatteryMillivoltsOrNull(Int.MIN_VALUE))
    }

    @Test
    fun comparesExactMillivolts() {
        assertTrue(batteryVoltageMatches(3999, 4000, BatteryVoltageComparison.BELOW))
        assertTrue(batteryVoltageMatches(4001, 4000, BatteryVoltageComparison.ABOVE))
        assertTrue(batteryVoltageMatches(4000, 4000, BatteryVoltageComparison.EQUALS))
        assertFalse(batteryVoltageMatches(3999, 4000, BatteryVoltageComparison.EQUALS))
    }

    @Test
    fun suppressesInitialAndDuplicateSamplesThenEmitsAboveCrossings() {
        val tracker = BatteryVoltageTransitionTracker(4000, BatteryVoltageComparison.ABOVE)

        assertNull(tracker.update(3900))
        assertNull(tracker.update(4000))
        assertEquals(4001, tracker.update(4001))
        assertNull(tracker.update(4001))
        assertNull(tracker.update(3999))
        assertEquals(4100, tracker.update(4100))
    }

    @Test
    fun emitsBelowAndEqualOnlyWhenEnteringTheirMatchingState() {
        val below = BatteryVoltageTransitionTracker(4000, BatteryVoltageComparison.BELOW)
        assertNull(below.update(4100))
        assertEquals(3999, below.update(3999))
        assertNull(below.update(3900))

        val equal = BatteryVoltageTransitionTracker(4000, BatteryVoltageComparison.EQUALS)
        assertNull(equal.update(3900))
        assertEquals(4000, equal.update(4000))
        assertNull(equal.update(4000))
    }
}
