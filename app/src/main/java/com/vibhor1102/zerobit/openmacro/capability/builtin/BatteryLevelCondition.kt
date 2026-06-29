/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object BatteryLevelCondition : CapabilityDefinition {
    override val type = "android.battery.level-condition"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Battery level"
    override val description = "Compares the current battery percentage with a threshold."
    override val creation = CapabilityCreation(
        idBase = "battery-level-condition",
        defaultConfig = mapOf(
            "level" to MacroValue.Number(java.math.BigDecimal("50")),
            "direction" to MacroValue.Text("goes_below"),
        ),
    )
    override val fields = batteryLevelFields()

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("level", "direction"), path))
            if (block.batteryThresholdOrNull() == null) {
                add(
                    ValidationIssue(
                        "$path.config",
                        "invalid_battery_threshold",
                        "Battery threshold requires a whole level from 1 to 100 and a valid direction.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val threshold = block.batteryThresholdOrNull()
            ?: return "Check an invalid battery threshold."
        return "Continue while battery level is ${threshold.direction.explanationWord()} ${threshold.level}%."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val threshold = requireNotNull(block.batteryThresholdOrNull())
        return RuntimeStep.CheckBatteryLevel(
            blockId = block.id,
            level = threshold.level,
            direction = threshold.direction,
        )
    }
}
