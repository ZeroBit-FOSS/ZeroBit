/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.model.MacroValue
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

internal fun parseLocalTime(value: MacroValue?): LocalTime? {
    val text = (value as? MacroValue.Text)?.value ?: return null
    if (!LOCAL_TIME_PATTERN.matches(text)) return null
    return try {
        LocalTime.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }
}

internal fun parseWeekdays(value: MacroValue?): Set<DayOfWeek>? {
    val raw = (value as? MacroValue.ListValue)?.values ?: return null
    val days = raw.map { child ->
        val text = (child as? MacroValue.Text)?.value ?: return null
        WEEKDAY_NAMES[text.lowercase()] ?: return null
    }
    return days.toSet().takeIf { days.isNotEmpty() && it.size == days.size }
}

internal fun parseTimezone(value: MacroValue?): ZoneId? {
    val text = (value as? MacroValue.Text)?.value ?: return null
    return runCatching { ZoneId.of(text) }.getOrNull()
}

internal fun Set<DayOfWeek>.shortNames(): String =
    sortedBy(DayOfWeek::getValue).joinToString { it.name.lowercase().take(3) }

private val WEEKDAY_NAMES = mapOf(
    "mon" to DayOfWeek.MONDAY,
    "tue" to DayOfWeek.TUESDAY,
    "wed" to DayOfWeek.WEDNESDAY,
    "thu" to DayOfWeek.THURSDAY,
    "fri" to DayOfWeek.FRIDAY,
    "sat" to DayOfWeek.SATURDAY,
    "sun" to DayOfWeek.SUNDAY,
)

private val LOCAL_TIME_PATTERN = Regex("""(?:[01]\d|2[0-3]):[0-5]\d""")
