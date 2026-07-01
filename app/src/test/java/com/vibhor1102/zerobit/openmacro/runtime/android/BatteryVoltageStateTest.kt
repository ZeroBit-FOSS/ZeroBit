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
}
