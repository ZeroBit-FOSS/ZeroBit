/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapQuerySemanticsTest {
    @Test
    fun acceptsHumanLocationQueries() {
        assertTrue(isValidMapQuery("India Gate, New Delhi"))
        assertTrue(isValidMapQuery("1600 Amphitheatre Parkway"))
    }

    @Test
    fun rejectsBlankControlAndOversizedQueries() {
        assertFalse(isValidMapQuery("   "))
        assertFalse(isValidMapQuery("Delhi\ngeo:1,2"))
        assertFalse(isValidMapQuery("x".repeat(MAX_MAP_QUERY_LENGTH + 1)))
    }
}
