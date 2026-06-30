/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.bluetooth.BluetoothAdapter

internal enum class AndroidBluetoothState {
    ENABLED,
    DISABLED,
    CHANGING,
    UNKNOWN,
}

internal fun androidBluetoothState(rawState: Int): AndroidBluetoothState = when (rawState) {
    BluetoothAdapter.STATE_ON -> AndroidBluetoothState.ENABLED
    BluetoothAdapter.STATE_OFF -> AndroidBluetoothState.DISABLED
    BluetoothAdapter.STATE_TURNING_ON,
    BluetoothAdapter.STATE_TURNING_OFF -> AndroidBluetoothState.CHANGING
    else -> AndroidBluetoothState.UNKNOWN
}

internal fun matchingBluetoothTriggerState(
    rawState: Int,
    expectedEnabled: Boolean,
): String? = when (androidBluetoothState(rawState)) {
    AndroidBluetoothState.ENABLED -> "enabled".takeIf { expectedEnabled }
    AndroidBluetoothState.DISABLED -> "disabled".takeIf { !expectedEnabled }
    AndroidBluetoothState.CHANGING,
    AndroidBluetoothState.UNKNOWN -> null
}
