/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

const val MAX_CALENDAR_TITLE_LENGTH = 200
const val MAX_CALENDAR_LOCATION_LENGTH = 500
const val MAX_CALENDAR_DESCRIPTION_LENGTH = 5_000

data class CalendarEventTimeSpec(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val zoneId: ZoneId,
) {
    val startInstant: Instant = start.atZone(zoneId).withEarlierOffsetAtOverlap().toInstant()
    val endInstant: Instant = end.atZone(zoneId).withEarlierOffsetAtOverlap().toInstant()

    init {
        val duration = Duration.between(startInstant, endInstant)
        require(!duration.isZero && !duration.isNegative) { "Calendar event end must follow its start." }
        require(duration <= MAX_DURATION) { "Calendar event duration must not exceed seven days." }
    }

    private companion object {
        val MAX_DURATION: Duration = Duration.ofDays(7)
    }
}
