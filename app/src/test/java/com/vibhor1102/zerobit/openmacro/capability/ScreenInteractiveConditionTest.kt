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

class ScreenInteractiveConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesScreenOnAndOffExpectations() {
        listOf("screen_on" to true, "screen_off" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(state), "sha256:screen-$state")
            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckScreenInteractive("screen-state", expected),
                result.plan.conditions.single(),
            )
        }
    }

    @Test
    fun rejectsMissingOrUnknownScreenState() {
        listOf(null, "locked").forEachIndexed { index, state ->
            val result = compiler.compile(document(state), "sha256:invalid-screen-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_screen_state"), result.issues.map { it.code })
        }
    }

    private fun document(state: String?) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("screen-condition", "Screen condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(
            MacroBlock(
                id = "screen-state",
                type = "android.screen.interactive-state",
                config = if (state == null) emptyMap() else {
                    mapOf("state" to MacroValue.Text(state))
                },
            ),
        ),
        actions = listOf(
            MacroBlock(
                id = "log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )
}
