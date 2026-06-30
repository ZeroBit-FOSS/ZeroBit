/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.nfc.NfcAdapter

internal enum class AndroidNfcState {
    ENABLED,
    DISABLED,
    CHANGING,
    UNKNOWN,
}

internal fun androidNfcState(rawState: Int): AndroidNfcState = when (rawState) {
    NfcAdapter.STATE_ON -> AndroidNfcState.ENABLED
    NfcAdapter.STATE_OFF -> AndroidNfcState.DISABLED
    NfcAdapter.STATE_TURNING_ON,
    NfcAdapter.STATE_TURNING_OFF -> AndroidNfcState.CHANGING
    else -> AndroidNfcState.UNKNOWN
}

internal fun matchingNfcTriggerState(
    rawState: Int,
    expectedEnabled: Boolean,
): String? = when (androidNfcState(rawState)) {
    AndroidNfcState.ENABLED -> "enabled".takeIf { expectedEnabled }
    AndroidNfcState.DISABLED -> "disabled".takeIf { !expectedEnabled }
    AndroidNfcState.CHANGING,
    AndroidNfcState.UNKNOWN -> null
}
