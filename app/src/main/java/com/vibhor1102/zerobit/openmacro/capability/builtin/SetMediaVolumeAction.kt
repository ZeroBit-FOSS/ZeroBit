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
import java.math.BigDecimal

object SetMediaVolumeAction : CapabilityDefinition {
    override val type = "android.audio.media_volume.set"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Set media volume"
    override val description = "Sets media playback volume to a bounded percentage."
    override val creation = CapabilityCreation(
        idBase = "set-media-volume",
        defaultConfig = mapOf("percentage" to MacroValue.Number(BigDecimal("50"))),
    )
    override val fields = listOf(
        CapabilityField(
            key = "percentage",
            label = "Media volume percent",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "Whole percentage from 0 to 100.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("percentage"), path))
            if (block.percentageOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.percentage",
                        code = "invalid_media_volume_percentage",
                        message = "Media volume must be a whole percentage from 0 to 100.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        "Set media volume to ${block.percentageOrNull() ?: "invalid"}%."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.SetMediaVolume(
            blockId = block.id,
            percentage = requireNotNull(block.percentageOrNull()),
        )

    private fun MacroBlock.percentageOrNull(): Int? =
        (config["percentage"] as? MacroValue.Number)
            ?.value
            ?.takeIf { it.stripTrailingZeros().scale() <= 0 }
            ?.toIntExactOrNull()
            ?.takeIf { it in 0..100 }

    private fun BigDecimal.toIntExactOrNull(): Int? =
        runCatching { intValueExact() }.getOrNull()
}
