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
import com.vibhor1102.zerobit.openmacro.capability.CapabilitySetup
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import java.math.BigDecimal

object SetAlarmAction : CapabilityDefinition {
    override val type = "android.alarm.set"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Set alarm"
    override val description = "Asks Android's clock app to create one alarm."
    override val creation = CapabilityCreation(
        idBase = "set-alarm",
        setup = CapabilitySetup(
            fieldKeys = listOf("hour", "minute", "label", "skipUi"),
            initialConfig = mapOf(
                "hour" to MacroValue.Number(BigDecimal("7")),
                "minute" to MacroValue.Number(BigDecimal.ZERO),
                "skipUi" to MacroValue.Boolean(false),
            ),
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "hour",
            label = "Hour",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "Whole-number hour from 0 to 23.",
        ),
        CapabilityField(
            key = "minute",
            label = "Minute",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "Whole-number minute from 0 to 59.",
        ),
        CapabilityField(
            key = "label",
            label = "Label (optional)",
            kind = CapabilityFieldKind.TEXT,
            required = false,
            help = "Optional alarm label, up to 100 characters.",
        ),
        CapabilityField(
            key = "skipUi",
            label = "Set without confirmation",
            kind = CapabilityFieldKind.BOOLEAN,
            required = true,
            help = "Ask the clock app to create the alarm without showing its confirmation screen.",
            advanced = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("hour", "minute", "label", "skipUi"), path))
        if (block.wholeNumber("hour", 0..23) == null) {
            add(issue(path, "hour", "invalid_alarm_hour", "Alarm hour must be a whole number from 0 to 23."))
        }
        if (block.wholeNumber("minute", 0..59) == null) {
            add(issue(path, "minute", "invalid_alarm_minute", "Alarm minute must be a whole number from 0 to 59."))
        }
        val label = block.config["label"]
        if (label != null && (label !is MacroValue.Text || label.value.isBlank() || label.value.length > 100)) {
            add(issue(path, "label", "invalid_alarm_label", "Alarm label must be 1 to 100 characters."))
        }
        if (block.config["skipUi"] !is MacroValue.Boolean) {
            add(issue(path, "skipUi", "invalid_alarm_skip_ui", "Set without confirmation must be true or false."))
        }
    }

    override fun explain(block: MacroBlock): String {
        val hour = block.wholeNumber("hour", 0..23)
        val minute = block.wholeNumber("minute", 0..59)
        val label = (block.config["label"] as? MacroValue.Text)?.value
        val confirmation = if ((block.config["skipUi"] as? MacroValue.Boolean)?.value == true) {
            "without opening its confirmation screen"
        } else {
            "and show its confirmation screen"
        }
        return "Ask the clock app to set ${hour ?: "invalid"}:${minute?.toString()?.padStart(2, '0') ?: "invalid"}${
            label?.let { " labeled '$it'" }.orEmpty()
        } $confirmation."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep = RuntimeStep.SetAlarm(
        blockId = block.id,
        hour = requireNotNull(block.wholeNumber("hour", 0..23)),
        minute = requireNotNull(block.wholeNumber("minute", 0..59)),
        label = (block.config["label"] as? MacroValue.Text)?.value,
        skipUi = (block.config.getValue("skipUi") as MacroValue.Boolean).value,
    )

    private fun MacroBlock.wholeNumber(key: String, range: IntRange): Int? =
        (config[key] as? MacroValue.Number)
            ?.value
            ?.let { runCatching { it.intValueExact() }.getOrNull() }
            ?.takeIf(range::contains)

    private fun issue(path: String, key: String, code: String, message: String) =
        ValidationIssue("$path.config.$key", code, message)
}
