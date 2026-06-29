/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.media.AudioManager
import com.vibhor1102.zerobit.openmacro.runtime.RingerMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingerModeStateTest {
    @Test
    fun decodesOnlyKnownAndroidRingerModes() {
        assertEquals(RingerMode.NORMAL, androidRingerModeOrNull(AudioManager.RINGER_MODE_NORMAL))
        assertEquals(RingerMode.VIBRATE, androidRingerModeOrNull(AudioManager.RINGER_MODE_VIBRATE))
        assertEquals(RingerMode.SILENT, androidRingerModeOrNull(AudioManager.RINGER_MODE_SILENT))
        assertNull(androidRingerModeOrNull(-1))
    }
}
