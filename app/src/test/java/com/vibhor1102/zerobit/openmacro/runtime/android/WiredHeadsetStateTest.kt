/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.media.AudioDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WiredHeadsetStateTest {
    @Test
    fun recognizesOnlyWiredAndUsbHeadsetTypes() {
        assertTrue(hasWiredHeadset(listOf(AudioDeviceInfo.TYPE_WIRED_HEADSET)))
        assertTrue(hasWiredHeadset(listOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)))
        assertTrue(hasWiredHeadset(listOf(AudioDeviceInfo.TYPE_USB_HEADSET)))
    }

    @Test
    fun excludesOtherAudioOutputsAndEmptySnapshots() {
        assertFalse(hasWiredHeadset(emptyList()))
        assertFalse(
            hasWiredHeadset(
                listOf(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_HDMI,
                ),
            ),
        )
    }

    @Test
    fun findsAHeadsetInsideMixedOutputTypes() {
        assertTrue(
            hasWiredHeadset(
                listOf(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                ),
            ),
        )
    }
}
