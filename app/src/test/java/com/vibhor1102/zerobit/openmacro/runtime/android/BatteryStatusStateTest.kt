/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.os.BatteryManager
import com.vibhor1102.zerobit.openmacro.runtime.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatusStateTest {
    @Test
    fun mapsEverySupportedAndroidBatteryStatus() {
        assertEquals(BatteryStatus.CHARGING, androidBatteryStatusOrNull(BatteryManager.BATTERY_STATUS_CHARGING))
        assertEquals(BatteryStatus.FULL, androidBatteryStatusOrNull(BatteryManager.BATTERY_STATUS_FULL))
        assertEquals(BatteryStatus.DISCHARGING, androidBatteryStatusOrNull(BatteryManager.BATTERY_STATUS_DISCHARGING))
        assertEquals(
            BatteryStatus.NOT_CHARGING,
            androidBatteryStatusOrNull(BatteryManager.BATTERY_STATUS_NOT_CHARGING),
        )
    }

    @Test
    fun rejectsUnknownAndUnrecognizedStatuses() {
        assertNull(androidBatteryStatusOrNull(BatteryManager.BATTERY_STATUS_UNKNOWN))
        assertNull(androidBatteryStatusOrNull(-1))
    }

    @Test
    fun preservesExistingChargingBooleanSemantics() {
        assertTrue(BatteryStatus.CHARGING.isCharging())
        assertTrue(BatteryStatus.FULL.isCharging())
        assertFalse(BatteryStatus.DISCHARGING.isCharging())
        assertFalse(BatteryStatus.NOT_CHARGING.isCharging())
    }
}
