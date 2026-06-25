/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ScheduleSpec(
    val localTime: LocalTime,
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val zoneId: ZoneId,
    val delivery: ScheduleDelivery = ScheduleDelivery.WINDOWED,
    val windowMinutes: Int = DEFAULT_WINDOW_MINUTES,
) {
    init {
        require(daysOfWeek.isNotEmpty()) { "A schedule needs at least one day." }
        require(windowMinutes in 1..MAX_WINDOW_MINUTES) {
            "Schedule window must be 1-$MAX_WINDOW_MINUTES minutes."
        }
    }

    /**
     * Returns the first selected wall-clock occurrence strictly after [after].
     *
     * Java's zone rules move nonexistent local times forward across DST gaps.
     * During an overlap, the earlier offset is chosen so one wall-clock time
     * produces one deterministic occurrence rather than firing twice.
     */
    fun nextOccurrence(after: Instant): ZonedDateTime {
        val current = after.atZone(zoneId)
        for (offsetDays in 0..MAX_LOOKAHEAD_DAYS) {
            val date = current.toLocalDate().plusDays(offsetDays.toLong())
            if (date.dayOfWeek !in daysOfWeek) {
                continue
            }
            val candidate = resolve(date)
            if (candidate.toInstant() > after) {
                return candidate
            }
        }
        error("No schedule occurrence found within $MAX_LOOKAHEAD_DAYS days.")
    }

    fun deliveryWindow(): Duration = when (delivery) {
        ScheduleDelivery.EXACT -> Duration.ZERO
        ScheduleDelivery.WINDOWED -> Duration.ofMinutes(windowMinutes.toLong())
    }

    private fun resolve(date: LocalDate): ZonedDateTime {
        val local = LocalDateTime.of(date, localTime)
        return local.atZone(zoneId).withEarlierOffsetAtOverlap()
    }

    companion object {
        const val DEFAULT_WINDOW_MINUTES = 15
        const val MAX_WINDOW_MINUTES = 60
        private const val MAX_LOOKAHEAD_DAYS = 14
    }
}

enum class ScheduleDelivery {
    WINDOWED,
    EXACT,
}
