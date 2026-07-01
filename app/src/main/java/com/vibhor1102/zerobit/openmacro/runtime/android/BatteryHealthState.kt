/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.os.BatteryManager
import com.vibhor1102.zerobit.openmacro.runtime.BatteryHealth

internal fun androidBatteryHealthOrNull(rawHealth: Int): BatteryHealth? = when (rawHealth) {
    BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.HEALTHY
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEATING
    BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
    BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
    else -> null
}

internal fun BatteryHealth.diagnosticName(): String = when (this) {
    BatteryHealth.HEALTHY -> "healthy"
    BatteryHealth.OVERHEATING -> "overheating"
    BatteryHealth.COLD -> "cold"
    BatteryHealth.DEAD -> "dead"
    BatteryHealth.OVER_VOLTAGE -> "over-voltage"
    BatteryHealth.FAILURE -> "failure"
}
