/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleSpecTest {
    @Test
    fun choosesNextSelectedLocalDayStrictlyAfterCurrentTime() {
        val schedule = ScheduleSpec(
            localTime = LocalTime.of(9, 30),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            zoneId = ZoneId.of("Asia/Kolkata"),
        )

        val next = schedule.nextOccurrence(
            Instant.parse("2026-06-22T05:00:00Z"),
        )

        assertEquals("2026-06-24T09:30+05:30[Asia/Kolkata]", next.toString())
    }

    @Test
    fun movesNonexistentSpringTimeForwardAcrossDstGap() {
        val schedule = ScheduleSpec(
            localTime = LocalTime.of(2, 30),
            daysOfWeek = setOf(DayOfWeek.SUNDAY),
            zoneId = ZoneId.of("America/New_York"),
        )

        val next = schedule.nextOccurrence(
            Instant.parse("2026-03-07T12:00:00Z"),
        )

        assertEquals(
            "2026-03-08T03:30-04:00[America/New_York]",
            next.toString(),
        )
    }

    @Test
    fun choosesEarlierOffsetDuringAutumnOverlap() {
        val schedule = ScheduleSpec(
            localTime = LocalTime.of(1, 30),
            daysOfWeek = setOf(DayOfWeek.SUNDAY),
            zoneId = ZoneId.of("America/New_York"),
        )

        val next = schedule.nextOccurrence(
            Instant.parse("2026-10-31T12:00:00Z"),
        )

        assertEquals(
            "2026-11-01T01:30-04:00[America/New_York]",
            next.toString(),
        )
    }
}
