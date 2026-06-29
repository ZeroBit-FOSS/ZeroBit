/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimezoneCatalogTest {
    @Test
    fun providesSortedBoundedIanaTimezones() {
        val timezones = availableTimezones()

        assertTrue("UTC" in timezones)
        assertEquals(timezones.sorted(), timezones)
        assertTrue(timezones.size <= 1_000)
        assertEquals(3, availableTimezones(limit = 3).size)
    }

    @Test
    fun filtersTimezonesCaseInsensitively() {
        val timezones = listOf("America/New_York", "Asia/Kolkata", "UTC")

        assertEquals(timezones, filterTimezones(timezones, "  "))
        assertEquals(listOf("Asia/Kolkata"), filterTimezones(timezones, "KOLKATA"))
        assertEquals(listOf("America/New_York"), filterTimezones(timezones, "america"))
        assertTrue(filterTimezones(timezones, "missing").isEmpty())
    }
}
