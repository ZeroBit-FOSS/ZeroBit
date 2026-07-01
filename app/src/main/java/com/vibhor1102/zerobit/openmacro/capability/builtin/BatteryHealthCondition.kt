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
import com.vibhor1102.zerobit.openmacro.runtime.BatteryHealth
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object BatteryHealthCondition : CapabilityDefinition {
    override val type = "android.battery.health"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Battery health"
    override val description = "Checks Android's current bounded battery health state."
    override val creation = CapabilityCreation(
        idBase = "battery-health",
        defaultConfig = mapOf("health" to MacroValue.Text("healthy")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "health",
            label = "Health",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose healthy, overheating, cold, dead, over-voltage, or failure.",
            allowedValues = listOf(
                "healthy",
                "overheating",
                "cold",
                "dead",
                "over_voltage",
                "failure",
            ),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("health"), path))
            if (block.batteryHealthOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.health",
                        code = "invalid_battery_health",
                        message = "Battery health must be healthy, overheating, cold, dead, over_voltage, or failure.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val health = block.batteryHealthOrNull()
            ?: return "Check an invalid battery health state."
        return "Continue only while battery health is ${health.explanation}."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckBatteryHealth(
            blockId = block.id,
            expectedHealth = requireNotNull(block.batteryHealthOrNull()),
        )
}

internal fun MacroBlock.batteryHealthOrNull(): BatteryHealth? =
    when ((config["health"] as? MacroValue.Text)?.value) {
        "healthy" -> BatteryHealth.HEALTHY
        "overheating" -> BatteryHealth.OVERHEATING
        "cold" -> BatteryHealth.COLD
        "dead" -> BatteryHealth.DEAD
        "over_voltage" -> BatteryHealth.OVER_VOLTAGE
        "failure" -> BatteryHealth.FAILURE
        else -> null
    }

internal val BatteryHealth.sourceName: String
    get() = when (this) {
        BatteryHealth.HEALTHY -> "healthy"
        BatteryHealth.OVERHEATING -> "overheating"
        BatteryHealth.COLD -> "cold"
        BatteryHealth.DEAD -> "dead"
        BatteryHealth.OVER_VOLTAGE -> "over_voltage"
        BatteryHealth.FAILURE -> "failure"
    }

private val BatteryHealth.explanation: String
    get() = when (this) {
        BatteryHealth.OVER_VOLTAGE -> "over-voltage"
        else -> sourceName
    }
