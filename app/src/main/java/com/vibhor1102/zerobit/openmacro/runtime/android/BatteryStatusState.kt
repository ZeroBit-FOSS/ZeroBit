/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.os.BatteryManager
import com.vibhor1102.zerobit.openmacro.runtime.BatteryStatus

internal fun androidBatteryStatusOrNull(rawStatus: Int): BatteryStatus? = when (rawStatus) {
    BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
    BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
    BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
    else -> null
}

internal fun BatteryStatus.isCharging(): Boolean =
    this == BatteryStatus.CHARGING || this == BatteryStatus.FULL

internal fun BatteryStatus.diagnosticName(): String = when (this) {
    BatteryStatus.CHARGING -> "charging"
    BatteryStatus.FULL -> "full"
    BatteryStatus.DISCHARGING -> "discharging"
    BatteryStatus.NOT_CHARGING -> "not charging"
}
