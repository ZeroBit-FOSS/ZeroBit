/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.BatteryTemperatureComparison
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import java.math.BigDecimal

object BatteryTemperatureCondition : CapabilityDefinition {
    override val type = "android.battery.temperature"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Battery temperature"
    override val description = "Compares current battery temperature in degrees Celsius."
    override val creation = CapabilityCreation(
        idBase = "battery-temperature",
        defaultConfig = mapOf(
            "celsius" to MacroValue.Number(BigDecimal("40")),
            "comparison" to MacroValue.Text("above"),
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "celsius",
            label = "Temperature (C)",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "Temperature from -100.0 to 100.0 with at most one decimal place.",
        ),
        CapabilityField(
            key = "comparison",
            label = "Comparison",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose below, above, or equals.",
            allowedValues = listOf("below", "above", "equals"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("celsius", "comparison"), path))
            if (block.batteryTemperatureThresholdOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config",
                        code = "invalid_battery_temperature_threshold",
                        message = "Battery temperature requires -100.0 to 100.0 Celsius with at most one decimal place and a valid comparison.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val threshold = block.batteryTemperatureThresholdOrNull()
            ?: return "Check an invalid battery temperature threshold."
        val comparison = when (threshold.comparison) {
            BatteryTemperatureComparison.BELOW -> "below"
            BatteryTemperatureComparison.ABOVE -> "above"
            BatteryTemperatureComparison.EQUALS -> "equal to"
        }
        return "Continue while battery temperature is $comparison ${threshold.displayCelsius} C."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val threshold = requireNotNull(block.batteryTemperatureThresholdOrNull())
        return RuntimeStep.CheckBatteryTemperature(
            blockId = block.id,
            thresholdTenthsCelsius = threshold.tenthsCelsius,
            comparison = threshold.comparison,
        )
    }
}

internal data class BatteryTemperatureThreshold(
    val tenthsCelsius: Int,
    val comparison: BatteryTemperatureComparison,
) {
    val displayCelsius: String = BigDecimal(tenthsCelsius)
        .movePointLeft(1)
        .stripTrailingZeros()
        .toPlainString()
}

internal fun MacroBlock.batteryTemperatureThresholdOrNull(): BatteryTemperatureThreshold? {
    val tenths = (config["celsius"] as? MacroValue.Number)
        ?.value
        ?.movePointRight(1)
        ?.toIntExactOrNull()
        ?.takeIf { it in -1_000..1_000 }
        ?: return null
    val comparison = when ((config["comparison"] as? MacroValue.Text)?.value) {
        "below" -> BatteryTemperatureComparison.BELOW
        "above" -> BatteryTemperatureComparison.ABOVE
        "equals" -> BatteryTemperatureComparison.EQUALS
        else -> return null
    }
    return BatteryTemperatureThreshold(tenths, comparison)
}

private fun BigDecimal.toIntExactOrNull(): Int? =
    runCatching { intValueExact() }.getOrNull()
