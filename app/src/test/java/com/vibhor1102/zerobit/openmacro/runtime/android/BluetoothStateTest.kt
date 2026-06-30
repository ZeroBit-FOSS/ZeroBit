/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.bluetooth.BluetoothAdapter
import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothStateTest {
    @Test
    fun translatesStableBluetoothStates() {
        assertEquals(
            AndroidBluetoothState.ENABLED,
            androidBluetoothState(BluetoothAdapter.STATE_ON),
        )
        assertEquals(
            AndroidBluetoothState.DISABLED,
            androidBluetoothState(BluetoothAdapter.STATE_OFF),
        )
    }

    @Test
    fun keepsTransitionalAndUnknownStatesFailClosed() {
        assertEquals(
            AndroidBluetoothState.CHANGING,
            androidBluetoothState(BluetoothAdapter.STATE_TURNING_ON),
        )
        assertEquals(
            AndroidBluetoothState.CHANGING,
            androidBluetoothState(BluetoothAdapter.STATE_TURNING_OFF),
        )
        assertEquals(AndroidBluetoothState.UNKNOWN, androidBluetoothState(-1))
    }
}
