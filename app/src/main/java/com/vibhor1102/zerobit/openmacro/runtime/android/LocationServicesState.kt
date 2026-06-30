/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.provider.Settings

internal fun locationServicesEnabledFromLegacyMode(mode: Int): Boolean? = when (mode) {
    Settings.Secure.LOCATION_MODE_OFF -> false
    Settings.Secure.LOCATION_MODE_SENSORS_ONLY,
    Settings.Secure.LOCATION_MODE_BATTERY_SAVING,
    Settings.Secure.LOCATION_MODE_HIGH_ACCURACY -> true
    else -> null
}
