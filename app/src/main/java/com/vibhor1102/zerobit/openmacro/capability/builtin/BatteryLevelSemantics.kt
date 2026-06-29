/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.BatteryDirection

internal data class BatteryThreshold(
    val level: Int,
    val direction: BatteryDirection,
) {
    fun matches(percentage: Int): Boolean = when (direction) {
        BatteryDirection.GOES_BELOW -> percentage < level
        BatteryDirection.GOES_ABOVE -> percentage > level
        BatteryDirection.EQUALS -> percentage == level
    }
}

internal fun batteryLevelFields(): List<CapabilityField> = listOf(
    CapabilityField(
        key = "level",
        label = "Battery Level (%)",
        kind = CapabilityFieldKind.NUMBER,
        required = true,
        help = "Whole percentage from 1 to 100.",
    ),
    CapabilityField(
        key = "direction",
        label = "Direction",
        kind = CapabilityFieldKind.TEXT,
        required = true,
        help = "Choose below, above, or equals.",
        allowedValues = listOf("goes_below", "goes_above", "equals"),
    ),
)

internal fun MacroBlock.batteryThresholdOrNull(): BatteryThreshold? {
    val level = (config["level"] as? MacroValue.Number)
        ?.value
        ?.toBigIntegerExactOrNull()
        ?.toIntExactOrNull()
        ?.takeIf { it in 1..100 }
        ?: return null
    val direction = when ((config["direction"] as? MacroValue.Text)?.value) {
        "goes_below" -> BatteryDirection.GOES_BELOW
        "goes_above" -> BatteryDirection.GOES_ABOVE
        "equals" -> BatteryDirection.EQUALS
        else -> return null
    }
    return BatteryThreshold(level, direction)
}

internal fun BatteryDirection.explanationWord(): String = when (this) {
    BatteryDirection.GOES_BELOW -> "below"
    BatteryDirection.GOES_ABOVE -> "above"
    BatteryDirection.EQUALS -> "equal to"
}

private fun java.math.BigDecimal.toBigIntegerExactOrNull() =
    runCatching { toBigIntegerExact() }.getOrNull()

private fun java.math.BigInteger.toIntExactOrNull() =
    runCatching { intValueExact() }.getOrNull()
