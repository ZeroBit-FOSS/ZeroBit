/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class SetMediaVolumeActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesBoundedMediaPercentageWithoutAdditionalAccess() {
        listOf(0, 50, 100).forEach { percentage ->
            val result = compiler.compile(
                document(mapOf("percentage" to MacroValue.Number(percentage.toBigDecimal()))),
                "sha256:media-volume-$percentage",
            )

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.SetMediaVolume("media-volume", percentage),
                result.plan.actions.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
    }

    @Test
    fun rejectsMissingFractionalOutOfRangeAndUnknownConfiguration() {
        val invalid = listOf(
            emptyMap(),
            mapOf("percentage" to MacroValue.Number(BigDecimal("1.5"))),
            mapOf("percentage" to MacroValue.Number(BigDecimal("-1"))),
            mapOf("percentage" to MacroValue.Number(BigDecimal("101"))),
            mapOf(
                "percentage" to MacroValue.Number(BigDecimal("50")),
                "stream" to MacroValue.Text("alarm"),
            ),
        )

        invalid.forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-media-volume-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 4) listOf("unknown_config_key")
                else listOf("invalid_media_volume_percentage"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("media-volume-test", "Media volume test"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = listOf(
            MacroBlock("media-volume", "android.audio.media_volume.set", config),
        ),
    )
}
