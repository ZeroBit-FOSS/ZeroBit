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
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object ScreenInteractiveCondition : CapabilityDefinition {
    override val type = "android.screen.interactive-state"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Screen state"
    override val description = "Checks whether Android currently reports the screen as on or off."
    override val creation = CapabilityCreation(
        idBase = "screen-state",
        defaultConfig = mapOf("state" to MacroValue.Text("screen_on")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "state",
            label = "State",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Choose screen on or screen off.",
            allowedValues = listOf("screen_on", "screen_off"),
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("state"), path))
            if (block.expectedInteractiveOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.state",
                        code = "invalid_screen_state",
                        message = "Screen state must be 'screen_on' or 'screen_off'.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if (block.expectedInteractiveOrNull() == false) {
            "Continue only while the screen is off."
        } else {
            "Continue only while the screen is on."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.CheckScreenInteractive(
            blockId = block.id,
            expectedInteractive = requireNotNull(block.expectedInteractiveOrNull()),
        )

    private fun MacroBlock.expectedInteractiveOrNull(): Boolean? =
        when ((config["state"] as? MacroValue.Text)?.value) {
            "screen_on" -> true
            "screen_off" -> false
            else -> null
        }
}
