/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryPresenceStateTest {
    @Test
    fun preservesBothExplicitBooleanValues() {
        assertEquals(true, batteryPresentOrNull(hasPresentExtra = true, present = true))
        assertEquals(false, batteryPresentOrNull(hasPresentExtra = true, present = false))
    }

    @Test
    fun rejectsDefaultValueWhenExtraIsMissing() {
        assertNull(batteryPresentOrNull(hasPresentExtra = false, present = true))
        assertNull(batteryPresentOrNull(hasPresentExtra = false, present = false))
    }
}
