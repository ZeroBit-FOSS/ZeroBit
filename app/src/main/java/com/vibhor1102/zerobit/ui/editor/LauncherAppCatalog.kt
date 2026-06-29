/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

data class LauncherAppOption(
    val label: String,
    val packageName: String,
)

fun normalizeLauncherApps(
    apps: List<LauncherAppOption>,
    limit: Int = MAX_LAUNCHER_APPS,
): List<LauncherAppOption> =
    apps.asSequence()
        .filter { it.packageName.isNotBlank() }
        .groupBy(LauncherAppOption::packageName)
        .values
        .asSequence()
        .map { matches -> matches.minBy { it.label.lowercase() } }
        .sortedWith(
            compareBy<LauncherAppOption> { it.label.lowercase() }
                .thenBy(LauncherAppOption::packageName),
        )
        .take(limit.coerceAtLeast(0))
        .toList()

fun filterLauncherApps(
    apps: List<LauncherAppOption>,
    query: String,
): List<LauncherAppOption> {
    val search = query.trim()
    if (search.isEmpty()) return apps
    return apps.filter { app ->
        app.label.contains(search, ignoreCase = true) ||
            app.packageName.contains(search, ignoreCase = true)
    }
}

private const val MAX_LAUNCHER_APPS = 250
