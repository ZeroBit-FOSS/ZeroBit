/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.provider.Settings

internal fun locationServicesEnabledFromLegacyMode(mode: Int): Boolean? = when (mode) {
    Settings.Secure.LOCATION_MODE_OFF -> false
    Settings.Secure.LOCATION_MODE_SENSORS_ONLY,
    Settings.Secure.LOCATION_MODE_BATTERY_SAVING,
    Settings.Secure.LOCATION_MODE_HIGH_ACCURACY -> true
    else -> null
}

internal fun locationServicesEnabledOrNull(context: Context): Boolean? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        context.getSystemService(LocationManager::class.java)?.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        val mode = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.LOCATION_MODE,
        )
        locationServicesEnabledFromLegacyMode(mode)
    }
} catch (_: Settings.SettingNotFoundException) {
    null
} catch (_: SecurityException) {
    null
} catch (_: RuntimeException) {
    null
}

internal fun matchingLocationServicesTriggerState(
    enabled: Boolean?,
    expectedEnabled: Boolean,
): String? = when {
    enabled == null || enabled != expectedEnabled -> null
    enabled -> "enabled"
    else -> "disabled"
}
