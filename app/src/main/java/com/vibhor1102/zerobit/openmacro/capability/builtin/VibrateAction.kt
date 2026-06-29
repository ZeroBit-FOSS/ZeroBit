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

object VibrateAction : CapabilityDefinition {
    override val type = "android.device.vibrate"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Vibrate"
    override val description = "Vibrates the device once for a bounded duration."
    override val creation = CapabilityCreation(
        idBase = "vibrate",
        defaultConfig = mapOf(
            "milliseconds" to MacroValue.Number(BigDecimal("250")),
        ),
    )
    override val fields = listOf(
        CapabilityField(
            key = "milliseconds",
            label = "Milliseconds",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "Whole number from 1 to 5000.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("milliseconds"), path))
            if (block.durationMillisOrNull() == null) {
                add(
                    ValidationIssue(
                        path = "$path.config.milliseconds",
                        code = "invalid_vibration_duration",
                        message = "Vibration duration must be a whole number from 1 to 5000 milliseconds.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        "Vibrate once for ${block.durationMillisOrNull() ?: "invalid"} milliseconds."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.Vibrate(
            blockId = block.id,
            durationMillis = requireNotNull(block.durationMillisOrNull()),
        )

    private fun MacroBlock.durationMillisOrNull(): Long? =
        (config["milliseconds"] as? MacroValue.Number)
            ?.value
            ?.takeIf { it.stripTrailingZeros().scale() <= 0 }
            ?.toLongExactOrNull()
            ?.takeIf { it in MIN_DURATION_MILLIS..MAX_DURATION_MILLIS }

    private fun BigDecimal.toLongExactOrNull(): Long? =
        runCatching { longValueExact() }.getOrNull()

    private const val MIN_DURATION_MILLIS = 1L
    private const val MAX_DURATION_MILLIS = 5_000L
}
