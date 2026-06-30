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

object SetTorchAction : CapabilityDefinition {
    override val type = "android.torch.set"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Set torch"
    override val description = "Turns the device torch on or off explicitly."
    override val creation = CapabilityCreation(
        idBase = "set-torch",
        defaultConfig = mapOf("enabled" to MacroValue.Boolean(false)),
    )
    override val fields = listOf(
        CapabilityField(
            key = "enabled",
            label = "Torch on",
            kind = CapabilityFieldKind.BOOLEAN,
            required = true,
            help = "Turn the torch on or off.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("enabled"), path))
            if (block.config["enabled"] !is MacroValue.Boolean) {
                add(
                    ValidationIssue(
                        path = "$path.config.enabled",
                        code = "invalid_torch_state",
                        message = "Torch state must be true or false.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        if ((block.config["enabled"] as? MacroValue.Boolean)?.value == true) {
            "Turn the torch on."
        } else {
            "Turn the torch off."
        }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> =
        setOf(AndroidPermission.CAMERA)

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.SetTorch(
            blockId = block.id,
            enabled = (block.config["enabled"] as MacroValue.Boolean).value,
        )
}
