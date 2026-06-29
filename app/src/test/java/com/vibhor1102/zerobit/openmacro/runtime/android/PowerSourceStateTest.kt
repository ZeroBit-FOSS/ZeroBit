/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.os.BatteryManager
import com.vibhor1102.zerobit.openmacro.runtime.PowerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerSourceStateTest {
    @Test
    fun decodesOnlyKnownAndroidPowerSources() {
        assertEquals(PowerSource.AC, androidPowerSourceOrNull(BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals(PowerSource.USB, androidPowerSourceOrNull(BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals(
            PowerSource.WIRELESS,
            androidPowerSourceOrNull(BatteryManager.BATTERY_PLUGGED_WIRELESS),
        )
        assertEquals(PowerSource.DOCK, androidPowerSourceOrNull(BatteryManager.BATTERY_PLUGGED_DOCK))
        assertNull(androidPowerSourceOrNull(0))
        assertNull(androidPowerSourceOrNull(-1))
    }
}
