/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiTransitionTrackerTest {
    @Test
    fun emitsOnlyMatchingRealTransitions() {
        var connectedMatches = 0
        val connected = WifiTransitionTracker(false, expectedConnected = true) {
            connectedMatches += 1
        }

        connected.update(false)
        connected.update(true)
        connected.update(true)
        connected.update(false)
        connected.update(true)

        assertEquals(2, connectedMatches)
    }

    @Test
    fun disconnectedSubscriptionDoesNotFireForInitialStateOrDuplicates() {
        var disconnectedMatches = 0
        val disconnected = WifiTransitionTracker(true, expectedConnected = false) {
            disconnectedMatches += 1
        }

        disconnected.update(true)
        disconnected.update(false)
        disconnected.update(false)

        assertEquals(1, disconnectedMatches)
    }
}
