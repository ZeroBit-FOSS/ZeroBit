/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import com.vibhor1102.zerobit.openmacro.runtime.MediaVolumeComparison

internal fun mediaVolumeIndex(percentage: Int, maximum: Int): Int? {
    if (percentage !in 0..100 || maximum <= 0) return null
    if (percentage == 0) return 0

    return ((percentage.toLong() * maximum.toLong() + 50L) / 100L)
        .toInt()
        .coerceIn(1, maximum)
}

internal fun mediaVolumeMatches(
    currentIndex: Int,
    maximum: Int,
    percentage: Int,
    comparison: MediaVolumeComparison,
): Boolean? {
    if (currentIndex !in 0..maximum) return null
    val thresholdIndex = mediaVolumeIndex(percentage, maximum) ?: return null
    return when (comparison) {
        MediaVolumeComparison.BELOW -> currentIndex < thresholdIndex
        MediaVolumeComparison.ABOVE -> currentIndex > thresholdIndex
        MediaVolumeComparison.EQUALS -> currentIndex == thresholdIndex
    }
}

internal fun mediaVolumeApproximatePercentage(currentIndex: Int, maximum: Int): Int? {
    if (maximum <= 0 || currentIndex !in 0..maximum) return null
    return ((currentIndex.toLong() * 100L + maximum / 2L) / maximum.toLong()).toInt()
}
