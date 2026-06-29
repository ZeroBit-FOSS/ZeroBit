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

object SetTimerAction : CapabilityDefinition {
    override val type = "android.timer.set"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Set timer"
    override val description = "Asks Android's clock app to create one bounded timer."
    override val creation = CapabilityCreation(
        idBase = "set-timer",
        setup = CapabilitySetup(
            fieldKeys = listOf("seconds", "label", "skipUi"),
            initialConfig = mapOf(
                "seconds" to MacroValue.Number(BigDecimal("300")),
                "skipUi" to MacroValue.Boolean(false),
            ),
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "seconds",
            label = "Duration (seconds)",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "Whole-number duration from 1 second to 24 hours.",
        ),
        CapabilityField(
            key = "label",
            label = "Label (optional)",
            kind = CapabilityFieldKind.TEXT,
            required = false,
            help = "Optional timer label, up to 100 characters.",
        ),
        CapabilityField(
            key = "skipUi",
            label = "Set without confirmation",
            kind = CapabilityFieldKind.BOOLEAN,
            required = true,
            help = "Ask the clock app to create the timer without showing its confirmation screen.",
            advanced = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("seconds", "label", "skipUi"), path))
        if (block.durationSecondsOrNull() == null) {
            add(issue(path, "seconds", "invalid_timer_duration", "Timer duration must be a whole number from 1 to 86400 seconds."))
        }
        val label = block.config["label"]
        if (label != null && (label !is MacroValue.Text || label.value.isBlank() || label.value.length > 100)) {
            add(issue(path, "label", "invalid_timer_label", "Timer label must be 1 to 100 characters."))
        }
        if (block.config["skipUi"] !is MacroValue.Boolean) {
            add(issue(path, "skipUi", "invalid_timer_skip_ui", "Set without confirmation must be true or false."))
        }
    }

    override fun explain(block: MacroBlock): String {
        val seconds = block.durationSecondsOrNull()
        val label = (block.config["label"] as? MacroValue.Text)?.value
        val confirmation = if ((block.config["skipUi"] as? MacroValue.Boolean)?.value == true) {
            "without opening its confirmation screen"
        } else {
            "and show its confirmation screen"
        }
        return "Ask the clock app to set a ${seconds ?: "invalid"}-second timer${
            label?.let { " labeled '$it'" }.orEmpty()
        } $confirmation."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep = RuntimeStep.SetTimer(
        blockId = block.id,
        durationSeconds = requireNotNull(block.durationSecondsOrNull()),
        label = (block.config["label"] as? MacroValue.Text)?.value,
        skipUi = (block.config.getValue("skipUi") as MacroValue.Boolean).value,
    )

    private fun MacroBlock.durationSecondsOrNull(): Int? =
        (config["seconds"] as? MacroValue.Number)
            ?.value
            ?.let { runCatching { it.intValueExact() }.getOrNull() }
            ?.takeIf { it in 1..MAX_TIMER_SECONDS }

    private fun issue(path: String, key: String, code: String, message: String) =
        ValidationIssue("$path.config.$key", code, message)

    private const val MAX_TIMER_SECONDS = 86_400
}
