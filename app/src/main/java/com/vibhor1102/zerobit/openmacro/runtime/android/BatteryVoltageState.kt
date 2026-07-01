/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import com.vibhor1102.zerobit.openmacro.runtime.BatteryVoltageComparison

internal fun validBatteryMillivoltsOrNull(rawMillivolts: Int): Int? =
    rawMillivolts.takeIf { it in 0..20_000 }

internal fun batteryVoltageMatches(
    currentMillivolts: Int,
    thresholdMillivolts: Int,
    comparison: BatteryVoltageComparison,
): Boolean = when (comparison) {
    BatteryVoltageComparison.BELOW -> currentMillivolts < thresholdMillivolts
    BatteryVoltageComparison.ABOVE -> currentMillivolts > thresholdMillivolts
    BatteryVoltageComparison.EQUALS -> currentMillivolts == thresholdMillivolts
}
