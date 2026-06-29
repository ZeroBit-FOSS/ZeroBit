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

class VibrateActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesBoundedOneShotVibration() {
        val result = compiler.compile(document(MacroValue.Number(BigDecimal("750"))), "sha256:vibrate")

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.Vibrate(blockId = "vibrate", durationMillis = 750L),
            result.plan.actions.single(),
        )
        assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
    }

    @Test
    fun rejectsMissingFractionalZeroAndOverLimitDurations() {
        val invalid = listOf(
            null,
            MacroValue.Number(BigDecimal("1.5")),
            MacroValue.Number(BigDecimal.ZERO),
            MacroValue.Number(BigDecimal("5001")),
        )

        invalid.forEachIndexed { index, value ->
            val result = compiler.compile(document(value), "sha256:invalid-vibrate-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_vibration_duration"), result.issues.map { it.code })
        }
    }

    private fun document(duration: MacroValue?) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("vibrate-test", "Vibrate test"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = listOf(
            MacroBlock(
                id = "vibrate",
                type = "android.device.vibrate",
                config = if (duration == null) emptyMap() else mapOf("milliseconds" to duration),
            ),
        ),
    )
}
