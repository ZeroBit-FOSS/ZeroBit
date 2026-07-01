/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceIdleModeStateTest {
    @Test
    fun suppressesDuplicatesAndEmitsOnlyRequestedTransitions() {
        val tracker = DeviceIdleModeTransitionTracker(
            initialIdle = false,
            expectedIdle = true,
        )

        assertNull(tracker.update(false))
        assertEquals("idle", tracker.update(true))
        assertNull(tracker.update(true))
        assertNull(tracker.update(false))
        assertNull(tracker.update(false))
        assertEquals("idle", tracker.update(true))
    }

    @Test
    fun emitsCanonicalNotIdleState() {
        val tracker = DeviceIdleModeTransitionTracker(
            initialIdle = true,
            expectedIdle = false,
        )

        assertEquals("not_idle", tracker.update(false))
    }
}
