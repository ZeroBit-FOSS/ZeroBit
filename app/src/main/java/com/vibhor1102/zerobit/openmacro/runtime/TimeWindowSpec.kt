/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class TimeWindowSpec(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val daysOfWeek: Set<DayOfWeek>,
    val zoneId: ZoneId,
) {
    init {
        require(startTime != endTime) { "Time window start and end must differ." }
        require(daysOfWeek.isNotEmpty()) { "Time window requires at least one weekday." }
    }

    fun contains(instant: Instant): Boolean {
        val local = instant.atZone(zoneId)
        val time = local.toLocalTime()
        return if (startTime < endTime) {
            local.dayOfWeek in daysOfWeek &&
                !time.isBefore(startTime) &&
                time.isBefore(endTime)
        } else if (!time.isBefore(startTime)) {
            local.dayOfWeek in daysOfWeek
        } else {
            time.isBefore(endTime) && local.dayOfWeek.minus(1) in daysOfWeek
        }
    }
}
