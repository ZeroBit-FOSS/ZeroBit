/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AirplaneModeStateTest {
    @Test
    fun decodesOnlyAndroidsDocumentedBinaryStates() {
        assertEquals(false, airplaneModeEnabledOrNull(0))
        assertEquals(true, airplaneModeEnabledOrNull(1))
        assertNull(airplaneModeEnabledOrNull(-1))
        assertNull(airplaneModeEnabledOrNull(2))
    }
}
