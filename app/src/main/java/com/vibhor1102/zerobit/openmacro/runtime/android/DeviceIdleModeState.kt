/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

internal class DeviceIdleModeTransitionTracker(
    initialIdle: Boolean,
    private val expectedIdle: Boolean,
) {
    private var lastIdle = initialIdle

    fun update(idle: Boolean): String? {
        if (idle == lastIdle) return null
        lastIdle = idle
        if (idle != expectedIdle) return null
        return if (idle) "idle" else "not_idle"
    }
}
