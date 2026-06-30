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
import com.vibhor1102.zerobit.openmacro.runtime.MediaVolumeComparison
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import java.math.BigDecimal

object MediaVolumeCondition : CapabilityDefinition {
    override val type = "android.audio.media_volume"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Media volume"
    override val description = "Compares current media volume with a percentage."
    override val creation = CapabilityCreation(
        idBase = "media-volume",
        defaultConfig = mapOf(
            "percentage" to MacroValue.Number(BigDecimal("50")),
            "comparison" to MacroValue.Text("below"),
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "percentage",
            label = "Media volume percent",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "Whole percentage from 0 to 100.",
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
            addAll(block.rejectUnknownConfig(setOf("percentage", "comparison"), path))
            if (block.thresholdOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config",
                        code = "invalid_media_volume_threshold",
                        message = "Media volume requires a whole percentage from 0 to 100 and a valid comparison.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String {
        val threshold = block.thresholdOrNull()
            ?: return "Check an invalid media volume threshold."
        return "Continue while media volume is ${threshold.comparison.explanation} ${threshold.percentage}%."
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val threshold = requireNotNull(block.thresholdOrNull())
        return RuntimeStep.CheckMediaVolume(
            blockId = block.id,
            percentage = threshold.percentage,
            comparison = threshold.comparison,
        )
    }

    private fun MacroBlock.thresholdOrNull(): MediaVolumeThreshold? {
        val percentage = (config["percentage"] as? MacroValue.Number)
            ?.value
            ?.takeIf { it.stripTrailingZeros().scale() <= 0 }
            ?.toIntExactOrNull()
            ?.takeIf { it in 0..100 }
            ?: return null
        val comparison = when ((config["comparison"] as? MacroValue.Text)?.value) {
            "below" -> MediaVolumeComparison.BELOW
            "above" -> MediaVolumeComparison.ABOVE
            "equals" -> MediaVolumeComparison.EQUALS
            else -> return null
        }
        return MediaVolumeThreshold(percentage, comparison)
    }

    private fun BigDecimal.toIntExactOrNull(): Int? =
        runCatching { intValueExact() }.getOrNull()

    private val MediaVolumeComparison.explanation: String
        get() = when (this) {
            MediaVolumeComparison.BELOW -> "below"
            MediaVolumeComparison.ABOVE -> "above"
            MediaVolumeComparison.EQUALS -> "equal to"
        }

    private data class MediaVolumeThreshold(
        val percentage: Int,
        val comparison: MediaVolumeComparison,
    )
}
