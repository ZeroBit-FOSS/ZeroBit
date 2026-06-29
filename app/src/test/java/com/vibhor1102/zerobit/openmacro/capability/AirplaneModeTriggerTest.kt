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
import org.junit.Assert.assertEquals
import org.junit.Test

class AirplaneModeTriggerTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesEnabledAndDisabledTransitions() {
        listOf("enabled" to true, "disabled" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(state), "sha256:airplane-trigger-$state")
            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveAirplaneMode("airplane-mode", expected),
                result.plan.triggers.single(),
            )
        }
    }

    @Test
    fun rejectsMissingOrUnknownTargetState() {
        listOf(null, "changed").forEachIndexed { index, state ->
            val result = compiler.compile(document(state), "sha256:invalid-airplane-trigger-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                listOf("invalid_airplane_mode_trigger_state"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(state: String?) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("airplane-trigger", "Airplane trigger"),
        triggers = listOf(
            MacroBlock(
                id = "airplane-mode",
                type = "android.airplane-mode.changed",
                config = if (state == null) emptyMap() else {
                    mapOf("state" to MacroValue.Text(state))
                },
            ),
        ),
        actions = listOf(
            MacroBlock(
                id = "log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("changed")),
            ),
        ),
    )
}
