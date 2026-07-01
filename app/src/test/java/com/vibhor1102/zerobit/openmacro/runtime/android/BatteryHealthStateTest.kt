/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.os.BatteryManager
import com.vibhor1102.zerobit.openmacro.runtime.BatteryHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryHealthStateTest {
    @Test
    fun mapsEverySupportedAndroidHealthValue() {
        assertEquals(BatteryHealth.HEALTHY, androidBatteryHealthOrNull(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals(BatteryHealth.OVERHEATING, androidBatteryHealthOrNull(BatteryManager.BATTERY_HEALTH_OVERHEAT))
        assertEquals(BatteryHealth.COLD, androidBatteryHealthOrNull(BatteryManager.BATTERY_HEALTH_COLD))
        assertEquals(BatteryHealth.DEAD, androidBatteryHealthOrNull(BatteryManager.BATTERY_HEALTH_DEAD))
        assertEquals(BatteryHealth.OVER_VOLTAGE, androidBatteryHealthOrNull(BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE))
        assertEquals(
            BatteryHealth.FAILURE,
            androidBatteryHealthOrNull(BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE),
        )
    }

    @Test
    fun rejectsUnknownAndUnrecognizedHealthValues() {
        assertNull(androidBatteryHealthOrNull(BatteryManager.BATTERY_HEALTH_UNKNOWN))
        assertNull(androidBatteryHealthOrNull(-1))
        assertNull(androidBatteryHealthOrNull(Int.MAX_VALUE))
    }

    @Test
    fun providesBoundedDiagnosticNames() {
        assertEquals("healthy", BatteryHealth.HEALTHY.diagnosticName())
        assertEquals("over-voltage", BatteryHealth.OVER_VOLTAGE.diagnosticName())
        assertEquals("failure", BatteryHealth.FAILURE.diagnosticName())
    }

    @Test
    fun emitsOnlyRealTransitionsIntoTheRequestedHealth() {
        val tracker = BatteryHealthTransitionTracker(BatteryHealth.OVER_VOLTAGE)

        assertNull(tracker.update(BatteryHealth.HEALTHY))
        assertNull(tracker.update(BatteryHealth.HEALTHY))
        assertEquals("over_voltage", tracker.update(BatteryHealth.OVER_VOLTAGE))
        assertNull(tracker.update(BatteryHealth.OVER_VOLTAGE))
        assertNull(tracker.update(BatteryHealth.COLD))
        assertEquals("over_voltage", tracker.update(BatteryHealth.OVER_VOLTAGE))
    }
}
