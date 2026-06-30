/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DarkThemeStateTest {
    @Test
    fun readsNightModeWithoutBeingConfusedByOtherUiModeBits() {
        assertEquals(
            true,
            darkThemeEnabledOrNull(
                Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_YES,
            ),
        )
        assertEquals(
            false,
            darkThemeEnabledOrNull(
                Configuration.UI_MODE_TYPE_DESK or Configuration.UI_MODE_NIGHT_NO,
            ),
        )
    }

    @Test
    fun rejectsUndefinedNightMode() {
        assertNull(
            darkThemeEnabledOrNull(
                Configuration.UI_MODE_TYPE_NORMAL or Configuration.UI_MODE_NIGHT_UNDEFINED,
            ),
        )
    }
}
