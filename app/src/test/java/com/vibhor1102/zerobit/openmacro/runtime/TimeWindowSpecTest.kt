/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWindowSpecTest {
    @Test
    fun daytimeWindowIncludesStartAndExcludesEnd() {
        val window = TimeWindowSpec(
            LocalTime.of(9, 0),
            LocalTime.of(17, 0),
            setOf(DayOfWeek.MONDAY),
            ZoneId.of("UTC"),
        )

        assertFalse(window.contains(Instant.parse("2026-06-29T08:59:59Z")))
        assertTrue(window.contains(Instant.parse("2026-06-29T09:00:00Z")))
        assertTrue(window.contains(Instant.parse("2026-06-29T16:59:59Z")))
        assertFalse(window.contains(Instant.parse("2026-06-29T17:00:00Z")))
        assertFalse(window.contains(Instant.parse("2026-06-30T10:00:00Z")))
    }

    @Test
    fun overnightWindowBelongsToItsStartingWeekday() {
        val window = TimeWindowSpec(
            LocalTime.of(22, 0),
            LocalTime.of(2, 0),
            setOf(DayOfWeek.FRIDAY),
            ZoneId.of("UTC"),
        )

        assertTrue(window.contains(Instant.parse("2026-07-03T22:00:00Z")))
        assertTrue(window.contains(Instant.parse("2026-07-04T01:59:59Z")))
        assertFalse(window.contains(Instant.parse("2026-07-04T02:00:00Z")))
        assertFalse(window.contains(Instant.parse("2026-07-04T23:00:00Z")))
    }

    @Test
    fun springForwardGapUsesRealInstantsWithoutInventingLocalTimes() {
        val window = TimeWindowSpec(
            LocalTime.of(1, 30),
            LocalTime.of(3, 30),
            setOf(DayOfWeek.SUNDAY),
            ZoneId.of("America/New_York"),
        )

        assertTrue(window.contains(Instant.parse("2026-03-08T06:45:00Z")))
        assertTrue(window.contains(Instant.parse("2026-03-08T07:15:00Z")))
        assertFalse(window.contains(Instant.parse("2026-03-08T07:30:00Z")))
    }

    @Test
    fun bothFallBackHourInstancesRemainInsideTheSameWindow() {
        val window = TimeWindowSpec(
            LocalTime.of(1, 0),
            LocalTime.of(2, 0),
            setOf(DayOfWeek.SUNDAY),
            ZoneId.of("America/New_York"),
        )

        assertTrue(window.contains(Instant.parse("2026-11-01T05:30:00Z")))
        assertTrue(window.contains(Instant.parse("2026-11-01T06:30:00Z")))
        assertFalse(window.contains(Instant.parse("2026-11-01T07:00:00Z")))
    }
}
