/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.content.res.Configuration
import com.vibhor1102.zerobit.openmacro.runtime.ScreenOrientation

internal fun screenOrientationOrNull(rawOrientation: Int): ScreenOrientation? =
    when (rawOrientation) {
        Configuration.ORIENTATION_PORTRAIT -> ScreenOrientation.PORTRAIT
        Configuration.ORIENTATION_LANDSCAPE -> ScreenOrientation.LANDSCAPE
        else -> null
    }
