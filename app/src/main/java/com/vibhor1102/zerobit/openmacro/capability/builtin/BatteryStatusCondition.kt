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
import com.vibhor1102.zerobit.openmacro.runtime.BatteryStatus
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object BatteryStatusCondition : CapabilityDefinition {
    override val type = "android.battery.status"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Battery status"
    override val description = "Checks Android's exact current battery charging status."
    override val creation = CapabilityCreation(
        idBase = "battery-status",
        defaultConfig = mapOf("status" to MacroValue.Text("charging")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "status",
            label = "Status",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose charging, full, discharging, or not charging.",
            allowedValues = listOf("charging", "full", "discharging", "not_charging"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("status"), path))
            if (block.batteryStatusOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.status",
                        code = "invalid_battery_status",
                        message = "Battery status must be charging, full, discharging, or not_charging.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val status = block.batteryStatusOrNull()
            ?: return "Check an invalid battery status."
        return "Continue only while battery status is ${status.explanation}."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckBatteryStatus(
            blockId = block.id,
            expectedStatus = requireNotNull(block.batteryStatusOrNull()),
        )
}

internal fun MacroBlock.batteryStatusOrNull(): BatteryStatus? =
    when ((config["status"] as? MacroValue.Text)?.value) {
        "charging" -> BatteryStatus.CHARGING
        "full" -> BatteryStatus.FULL
        "discharging" -> BatteryStatus.DISCHARGING
        "not_charging" -> BatteryStatus.NOT_CHARGING
        else -> null
    }

internal val BatteryStatus.sourceName: String
    get() = when (this) {
        BatteryStatus.CHARGING -> "charging"
        BatteryStatus.FULL -> "full"
        BatteryStatus.DISCHARGING -> "discharging"
        BatteryStatus.NOT_CHARGING -> "not_charging"
    }

private val BatteryStatus.explanation: String
    get() = if (this == BatteryStatus.NOT_CHARGING) "not charging" else sourceName
