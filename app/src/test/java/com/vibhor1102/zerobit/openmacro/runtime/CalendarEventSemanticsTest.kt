/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarEventSemanticsTest {
    @Test
    fun movesNonexistentStartForwardAcrossDstGap() {
        val spec = CalendarEventTimeSpec(
            start = LocalDateTime.parse("2026-03-08T02:30"),
            end = LocalDateTime.parse("2026-03-08T04:00"),
            zoneId = ZoneId.of("America/New_York"),
        )

        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), spec.startInstant)
        assertEquals(Instant.parse("2026-03-08T08:00:00Z"), spec.endInstant)
    }

    @Test
    fun choosesEarlierOffsetAtAutumnOverlap() {
        val spec = CalendarEventTimeSpec(
            start = LocalDateTime.parse("2026-11-01T01:30"),
            end = LocalDateTime.parse("2026-11-01T02:30"),
            zoneId = ZoneId.of("America/New_York"),
        )

        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), spec.startInstant)
        assertEquals(Instant.parse("2026-11-01T07:30:00Z"), spec.endInstant)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEventsLongerThanSevenDays() {
        CalendarEventTimeSpec(
            start = LocalDateTime.parse("2026-07-01T09:00"),
            end = LocalDateTime.parse("2026-07-08T09:01"),
            zoneId = ZoneId.of("UTC"),
        )
    }
}
