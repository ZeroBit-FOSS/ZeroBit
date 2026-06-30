/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import com.vibhor1102.zerobit.openmacro.runtime.MediaVolumeComparison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaVolumeMappingTest {
    @Test
    fun mapsPercentageAcrossTheRuntimeMaximum() {
        assertEquals(0, mediaVolumeIndex(0, 15))
        assertEquals(1, mediaVolumeIndex(1, 15))
        assertEquals(8, mediaVolumeIndex(50, 15))
        assertEquals(15, mediaVolumeIndex(100, 15))
    }

    @Test
    fun handlesDifferentDeviceRangesWithoutExceedingThem() {
        assertEquals(13, mediaVolumeIndex(33, 40))
        assertEquals(1, mediaVolumeIndex(1, 1))
        assertEquals(1, mediaVolumeIndex(100, 1))
    }

    @Test
    fun rejectsInvalidPercentagesAndUnavailableRanges() {
        assertNull(mediaVolumeIndex(-1, 15))
        assertNull(mediaVolumeIndex(101, 15))
        assertNull(mediaVolumeIndex(50, 0))
        assertNull(mediaVolumeIndex(50, -1))
    }

    @Test
    fun comparesUsingTheSameDiscreteStepsAsTheSetAction() {
        assertEquals(true, mediaVolumeMatches(8, 15, 50, MediaVolumeComparison.EQUALS))
        assertEquals(false, mediaVolumeMatches(8, 15, 50, MediaVolumeComparison.ABOVE))
        assertEquals(true, mediaVolumeMatches(7, 15, 50, MediaVolumeComparison.BELOW))
        assertEquals(false, mediaVolumeMatches(9, 15, 50, MediaVolumeComparison.BELOW))
    }

    @Test
    fun reportsApproximatePercentageForDiagnostics() {
        assertEquals(0, mediaVolumeApproximatePercentage(0, 15))
        assertEquals(53, mediaVolumeApproximatePercentage(8, 15))
        assertEquals(100, mediaVolumeApproximatePercentage(15, 15))
        assertNull(mediaVolumeApproximatePercentage(16, 15))
    }
}
