/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.content.Intent
import com.vibhor1102.zerobit.openmacro.runtime.DockState

internal fun androidDockStateOrNull(rawState: Int): DockState? = when (rawState) {
    Intent.EXTRA_DOCK_STATE_UNDOCKED -> DockState.UNDOCKED
    Intent.EXTRA_DOCK_STATE_DESK -> DockState.DESK
    Intent.EXTRA_DOCK_STATE_CAR -> DockState.CAR
    Intent.EXTRA_DOCK_STATE_LE_DESK -> DockState.LOW_END_DESK
    Intent.EXTRA_DOCK_STATE_HE_DESK -> DockState.HIGH_END_DESK
    else -> null
}

internal fun DockState.diagnosticName(): String = when (this) {
    DockState.UNDOCKED -> "undocked"
    DockState.DESK -> "desk"
    DockState.CAR -> "car"
    DockState.LOW_END_DESK -> "low-end desk"
    DockState.HIGH_END_DESK -> "high-end desk"
}

internal class DockStateTransitionTracker(private val expectedState: DockState) {
    private var lastState: DockState? = null

    fun update(state: DockState): String? {
        val previousState = lastState
        lastState = state
        if (previousState == null || previousState == state || state != expectedState) return null
        return when (state) {
            DockState.UNDOCKED -> "undocked"
            DockState.DESK -> "desk"
            DockState.CAR -> "car"
            DockState.LOW_END_DESK -> "low_end_desk"
            DockState.HIGH_END_DESK -> "high_end_desk"
        }
    }
}
