/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.media.AudioDeviceInfo

internal fun hasWiredHeadset(deviceTypes: Iterable<Int>): Boolean =
    deviceTypes.any { it in WIRED_HEADSET_DEVICE_TYPES }

private val WIRED_HEADSET_DEVICE_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
)
