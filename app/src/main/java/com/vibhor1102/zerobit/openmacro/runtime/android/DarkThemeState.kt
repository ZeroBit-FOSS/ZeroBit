/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.content.res.Configuration

internal fun darkThemeEnabledOrNull(uiMode: Int): Boolean? =
    when (uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> true
        Configuration.UI_MODE_NIGHT_NO -> false
        else -> null
    }

internal class DarkThemeTransitionTracker(initialDark: Boolean?) {
    private var lastDark = initialDark

    fun matchingState(currentDark: Boolean?, expectedDark: Boolean): String? {
        if (currentDark == null) return null
        val previousDark = lastDark
        lastDark = currentDark
        if (previousDark == null || previousDark == currentDark || currentDark != expectedDark) {
            return null
        }
        return if (currentDark) "dark" else "light"
    }
}
