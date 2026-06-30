/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.MediaVolumeComparison
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaVolumeConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesEachBoundedMediaVolumeComparison() {
        listOf(
            "below" to MediaVolumeComparison.BELOW,
            "above" to MediaVolumeComparison.ABOVE,
            "equals" to MediaVolumeComparison.EQUALS,
        ).forEach { (source, expected) ->
            val result = compiler.compile(
                document(BigDecimal("50"), source),
                "sha256:media-volume-$source",
            )

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckMediaVolume("media-volume", 50, expected),
                result.plan.conditions.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
    }

    @Test
    fun rejectsMissingFractionalOutOfRangeUnknownAndExtraConfiguration() {
        listOf(
            emptyMap(),
            config(BigDecimal("1.5"), "below"),
            config(BigDecimal("-1"), "below"),
            config(BigDecimal("101"), "below"),
            config(BigDecimal("50"), "near"),
            config(BigDecimal("50"), "equals") + ("stream" to MacroValue.Text("alarm")),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-media-condition-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 5) listOf("unknown_config_key")
                else listOf("invalid_media_volume_threshold"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(percentage: BigDecimal, comparison: String) =
        document(config(percentage, comparison))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("media-volume-condition", "Media volume condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(
            MacroBlock("media-volume", "android.audio.media_volume", config),
        ),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )

    private fun config(percentage: BigDecimal, comparison: String) = mapOf(
        "percentage" to MacroValue.Number(percentage),
        "comparison" to MacroValue.Text(comparison),
    )
}
