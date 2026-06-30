/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

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
}
