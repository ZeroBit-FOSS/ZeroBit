/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.nfc.NfcAdapter
import org.junit.Assert.assertEquals
import org.junit.Test

class NfcStateTest {
    @Test
    fun translatesStableNfcStates() {
        assertEquals(AndroidNfcState.ENABLED, androidNfcState(NfcAdapter.STATE_ON))
        assertEquals(AndroidNfcState.DISABLED, androidNfcState(NfcAdapter.STATE_OFF))
    }

    @Test
    fun keepsTransitionalAndUnknownStatesFailClosed() {
        assertEquals(AndroidNfcState.CHANGING, androidNfcState(NfcAdapter.STATE_TURNING_ON))
        assertEquals(AndroidNfcState.CHANGING, androidNfcState(NfcAdapter.STATE_TURNING_OFF))
        assertEquals(AndroidNfcState.UNKNOWN, androidNfcState(-1))
    }
}
