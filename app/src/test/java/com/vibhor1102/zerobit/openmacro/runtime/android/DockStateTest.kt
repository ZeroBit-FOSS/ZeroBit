/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.content.Intent
import com.vibhor1102.zerobit.openmacro.runtime.DockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockStateTest {
    @Test
    fun mapsOnlyAndroidsCanonicalDockStates() {
        mapOf(
            Intent.EXTRA_DOCK_STATE_UNDOCKED to DockState.UNDOCKED,
            Intent.EXTRA_DOCK_STATE_DESK to DockState.DESK,
            Intent.EXTRA_DOCK_STATE_CAR to DockState.CAR,
            Intent.EXTRA_DOCK_STATE_LE_DESK to DockState.LOW_END_DESK,
            Intent.EXTRA_DOCK_STATE_HE_DESK to DockState.HIGH_END_DESK,
        ).forEach { (raw, expected) ->
            assertEquals(expected, androidDockStateOrNull(raw))
        }

        assertNull(androidDockStateOrNull(-1))
        assertNull(androidDockStateOrNull(99))
    }

    @Test
    fun usesBoundedDiagnosticNames() {
        assertEquals("undocked", DockState.UNDOCKED.diagnosticName())
        assertEquals("desk", DockState.DESK.diagnosticName())
        assertEquals("car", DockState.CAR.diagnosticName())
        assertEquals("low-end desk", DockState.LOW_END_DESK.diagnosticName())
        assertEquals("high-end desk", DockState.HIGH_END_DESK.diagnosticName())
    }
}
