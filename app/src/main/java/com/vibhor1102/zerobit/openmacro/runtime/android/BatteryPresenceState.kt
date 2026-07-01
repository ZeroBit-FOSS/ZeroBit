/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

internal fun batteryPresentOrNull(hasPresentExtra: Boolean, present: Boolean): Boolean? =
    present.takeIf { hasPresentExtra }

internal class BatteryPresenceTransitionTracker(
    private val expectedPresent: Boolean,
) {
    private var lastPresent: Boolean? = null

    fun update(present: Boolean): String? {
        val previousPresent = lastPresent
        lastPresent = present
        if (previousPresent == null || previousPresent == present || present != expectedPresent) {
            return null
        }
        return if (present) "present" else "not_present"
    }
}
