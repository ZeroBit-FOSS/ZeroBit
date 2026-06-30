/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

internal fun mediaVolumeIndex(percentage: Int, maximum: Int): Int? {
    if (percentage !in 0..100 || maximum <= 0) return null
    if (percentage == 0) return 0

    return ((percentage.toLong() * maximum.toLong() + 50L) / 100L)
        .toInt()
        .coerceIn(1, maximum)
}
