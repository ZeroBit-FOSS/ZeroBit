/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import java.time.ZoneId

fun availableTimezones(limit: Int = MAX_TIMEZONE_OPTIONS): List<String> =
    ZoneId.getAvailableZoneIds()
        .asSequence()
        .sorted()
        .take(limit.coerceAtLeast(0))
        .toList()

fun filterTimezones(
    timezones: List<String>,
    query: String,
): List<String> {
    val search = query.trim()
    if (search.isEmpty()) return timezones
    return timezones.filter { it.contains(search, ignoreCase = true) }
}

private const val MAX_TIMEZONE_OPTIONS = 1_000
