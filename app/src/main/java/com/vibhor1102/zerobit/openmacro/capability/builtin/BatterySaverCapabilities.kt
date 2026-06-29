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
import com.vibhor1102.zerobit.openmacro.capability.TriggerOutput
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

private fun batterySaverFields(label: String): List<CapabilityField> = listOf(
    CapabilityField(
        key = "state",
        label = label,
        kind = CapabilityFieldKind.TEXT,
        required = true,
        help = "Choose enabled or disabled.",
        allowedValues = listOf("enabled", "disabled"),
    ),
)

internal fun MacroBlock.expectedBatterySaverEnabledOrNull(): Boolean? =
    when ((config["state"] as? MacroValue.Text)?.value) {
        "enabled" -> true
        "disabled" -> false
        else -> null
    }

object BatterySaverCondition : CapabilityDefinition {
    override val type = "android.battery-saver.state"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Battery saver"
    override val description = "Checks whether Android Battery Saver is enabled or disabled."
    override val creation = CapabilityCreation(
        idBase = "battery-saver",
        defaultConfig = mapOf("state" to MacroValue.Text("enabled")),
    )
    override val fields = batterySaverFields("State")

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        validateBatterySaverState(block, path, "invalid_battery_saver_state")

    override fun explain(block: MacroBlock): String =
        if (block.expectedBatterySaverEnabledOrNull() == false) {
            "Continue only while Battery Saver is disabled."
        } else {
            "Continue only while Battery Saver is enabled."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckBatterySaver(
            block.id,
            requireNotNull(block.expectedBatterySaverEnabledOrNull()),
        )
}

object BatterySaverTrigger : CapabilityDefinition {
    override val type = "android.battery-saver.changed"
    override val lane = CapabilityLane.TRIGGER
    override val displayName = "Battery saver changed"
    override val description = "Starts when Android Battery Saver changes to the selected state."
    override val creation = CapabilityCreation(
        idBase = "battery-saver-changed",
        defaultConfig = mapOf("state" to MacroValue.Text("enabled")),
    )
    override val fields = batterySaverFields("New State")
    override val triggerOutputs = listOf(
        TriggerOutput(
            key = "battery_saver.state",
            type = MacroVariableType.TEXT,
            description = "The Battery Saver state that caused this run.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        validateBatterySaverState(block, path, "invalid_battery_saver_trigger_state")

    override fun explain(block: MacroBlock): String =
        if (block.expectedBatterySaverEnabledOrNull() == false) {
            "Start when Battery Saver becomes disabled."
        } else {
            "Start when Battery Saver becomes enabled."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ObserveBatterySaver(
            block.id,
            requireNotNull(block.expectedBatterySaverEnabledOrNull()),
        )
}

private fun validateBatterySaverState(
    block: MacroBlock,
    path: String,
    issueCode: String,
): List<ValidationIssue> = buildList {
    addAll(block.rejectUnknownConfig(setOf("state"), path))
    if (block.expectedBatterySaverEnabledOrNull() == null) {
        add(
            ValidationIssue(
                path = "$path.config.state",
                code = issueCode,
                message = "Battery Saver state must be 'enabled' or 'disabled'.",
            ),
        )
    }
}
