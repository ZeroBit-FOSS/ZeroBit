/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorchCameraSelectionTest {
    @Test
    fun prefersRearFlashCameraThenStableIdOrder() {
        val candidates = listOf(
            TorchCameraCandidate("front", true, CameraCharacteristics.LENS_FACING_FRONT),
            TorchCameraCandidate("rear-z", true, CameraCharacteristics.LENS_FACING_BACK),
            TorchCameraCandidate("rear-a", true, CameraCharacteristics.LENS_FACING_BACK),
        )

        assertEquals("rear-a", selectTorchCameraId(candidates))
    }

    @Test
    fun fallsBackToAnyFlashCameraAndIgnoresCamerasWithoutFlash() {
        assertEquals(
            "external",
            selectTorchCameraId(
                listOf(
                    TorchCameraCandidate("rear", false, CameraCharacteristics.LENS_FACING_BACK),
                    TorchCameraCandidate("external", true, CameraCharacteristics.LENS_FACING_EXTERNAL),
                ),
            ),
        )
        assertNull(selectTorchCameraId(listOf(TorchCameraCandidate("rear", false, null))))
    }

    @Test
    fun translatesCameraFailuresWithoutExposingPlatformDetails() {
        assertEquals(
            "The torch camera is currently in use.",
            torchCameraFailureMessage(CameraAccessException.CAMERA_IN_USE),
        )
        assertEquals(
            "Camera access is disabled by Android policy.",
            torchCameraFailureMessage(CameraAccessException.CAMERA_DISABLED),
        )
        assertEquals("Android could not change the torch.", torchCameraFailureMessage(-1))
    }
}
