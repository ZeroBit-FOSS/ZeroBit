/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

import com.vibhor1102.zerobit.openmacro.runtime.BatteryTemperatureComparison
import java.math.BigDecimal

internal fun validBatteryTemperatureTenthsOrNull(rawTenths: Int): Int? =
    rawTenths.takeIf { it in -1_000..1_000 }

internal fun batteryTemperatureMatches(
    currentTenths: Int,
    thresholdTenths: Int,
    comparison: BatteryTemperatureComparison,
): Boolean = when (comparison) {
    BatteryTemperatureComparison.BELOW -> currentTenths < thresholdTenths
    BatteryTemperatureComparison.ABOVE -> currentTenths > thresholdTenths
    BatteryTemperatureComparison.EQUALS -> currentTenths == thresholdTenths
}

internal fun formatTenthsCelsius(tenths: Int): String =
    BigDecimal(tenths).movePointLeft(1).stripTrailingZeros().toPlainString()

internal class BatteryTemperatureTransitionTracker(
    private val thresholdTenths: Int,
    private val comparison: BatteryTemperatureComparison,
) {
    private var lastTenths: Int? = null

    fun update(currentTenths: Int): Int? {
        val previousTenths = lastTenths
        lastTenths = currentTenths
        val matched = when (comparison) {
            BatteryTemperatureComparison.BELOW ->
                previousTenths?.let { it >= thresholdTenths && currentTenths < thresholdTenths }
            BatteryTemperatureComparison.ABOVE ->
                previousTenths?.let { it <= thresholdTenths && currentTenths > thresholdTenths }
            BatteryTemperatureComparison.EQUALS ->
                previousTenths?.let { it != thresholdTenths && currentTenths == thresholdTenths }
        } ?: false
        return currentTenths.takeIf { matched }
    }
}
