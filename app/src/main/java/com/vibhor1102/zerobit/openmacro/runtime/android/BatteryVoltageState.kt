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

internal class BatteryVoltageTransitionTracker(
    private val thresholdMillivolts: Int,
    private val comparison: BatteryVoltageComparison,
) {
    private var lastMillivolts: Int? = null

    fun update(currentMillivolts: Int): Int? {
        val previousMillivolts = lastMillivolts
        lastMillivolts = currentMillivolts
        val matched = when (comparison) {
            BatteryVoltageComparison.BELOW ->
                previousMillivolts?.let { it >= thresholdMillivolts && currentMillivolts < thresholdMillivolts }
            BatteryVoltageComparison.ABOVE ->
                previousMillivolts?.let { it <= thresholdMillivolts && currentMillivolts > thresholdMillivolts }
            BatteryVoltageComparison.EQUALS ->
                previousMillivolts?.let { it != thresholdMillivolts && currentMillivolts == thresholdMillivolts }
        } ?: false
        return currentMillivolts.takeIf { matched }
    }
}
