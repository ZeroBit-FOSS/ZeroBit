/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationServicesStateTest {
    @Test
    fun translatesEveryKnownLegacyLocationMode() {
        assertEquals(
            false,
            locationServicesEnabledFromLegacyMode(Settings.Secure.LOCATION_MODE_OFF),
        )
        listOf(
            Settings.Secure.LOCATION_MODE_SENSORS_ONLY,
            Settings.Secure.LOCATION_MODE_BATTERY_SAVING,
            Settings.Secure.LOCATION_MODE_HIGH_ACCURACY,
        ).forEach { mode ->
            assertEquals(true, locationServicesEnabledFromLegacyMode(mode))
        }
    }

    @Test
    fun rejectsUnknownLegacyLocationModes() {
        assertNull(locationServicesEnabledFromLegacyMode(-1))
        assertNull(locationServicesEnabledFromLegacyMode(4))
    }
}
