/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.content.res.Configuration
import com.vibhor1102.zerobit.openmacro.runtime.ScreenOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenOrientationStateTest {
    @Test
    fun translatesPortraitAndLandscape() {
        assertEquals(
            ScreenOrientation.PORTRAIT,
            screenOrientationOrNull(Configuration.ORIENTATION_PORTRAIT),
        )
        assertEquals(
            ScreenOrientation.LANDSCAPE,
            screenOrientationOrNull(Configuration.ORIENTATION_LANDSCAPE),
        )
    }

    @Test
    fun rejectsUndefinedAndSquareOrientation() {
        assertNull(screenOrientationOrNull(Configuration.ORIENTATION_UNDEFINED))
        @Suppress("DEPRECATION")
        assertNull(screenOrientationOrNull(Configuration.ORIENTATION_SQUARE))
    }

    @Test
    fun emitsOnlyRequestedRealTransitions() {
        val tracker = ScreenOrientationTransitionTracker(ScreenOrientation.PORTRAIT)

        assertNull(tracker.matchingState(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE))
        assertEquals(
            "landscape",
            tracker.matchingState(ScreenOrientation.LANDSCAPE, ScreenOrientation.LANDSCAPE),
        )
        assertNull(tracker.matchingState(ScreenOrientation.LANDSCAPE, ScreenOrientation.LANDSCAPE))
        assertNull(tracker.matchingState(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE))
    }

    @Test
    fun undefinedStateNeitherEmitsNorErasesStableBaseline() {
        val tracker = ScreenOrientationTransitionTracker(ScreenOrientation.PORTRAIT)

        assertNull(tracker.matchingState(null, ScreenOrientation.LANDSCAPE))
        assertEquals(
            "landscape",
            tracker.matchingState(ScreenOrientation.LANDSCAPE, ScreenOrientation.LANDSCAPE),
        )
    }
}
