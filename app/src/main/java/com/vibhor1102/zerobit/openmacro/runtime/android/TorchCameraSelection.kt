/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics

internal data class TorchCameraCandidate(
    val id: String,
    val hasFlash: Boolean,
    val lensFacing: Int?,
)

internal fun selectTorchCameraId(candidates: List<TorchCameraCandidate>): String? =
    candidates
        .asSequence()
        .filter { it.hasFlash }
        .sortedWith(
            compareBy<TorchCameraCandidate> {
                if (it.lensFacing == CameraCharacteristics.LENS_FACING_BACK) 0 else 1
            }.thenBy { it.id },
        )
        .firstOrNull()
        ?.id

internal fun torchCameraFailureMessage(reason: Int): String = when (reason) {
    CameraAccessException.CAMERA_IN_USE -> "The torch camera is currently in use."
    CameraAccessException.MAX_CAMERAS_IN_USE -> "Camera resources are currently in use."
    CameraAccessException.CAMERA_DISCONNECTED -> "The torch camera disconnected."
    CameraAccessException.CAMERA_DISABLED -> "Camera access is disabled by Android policy."
    else -> "Android could not change the torch."
}
