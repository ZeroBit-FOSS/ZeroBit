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

class BatterySaverCapabilitiesTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesConditionAndTriggerForBothStates() {
        listOf("enabled" to true, "disabled" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(state), "sha256:battery-saver-$state")
            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveBatterySaver("battery-saver-trigger", expected),
                result.plan.triggers.single(),
            )
            assertEquals(
                RuntimeStep.CheckBatterySaver("battery-saver-condition", expected),
                result.plan.conditions.single(),
            )
        }
    }

    @Test
    fun rejectsMissingOrUnknownStatesInBothLanes() {
        listOf(null, "automatic").forEachIndexed { index, state ->
            val result = compiler.compile(document(state), "sha256:invalid-saver-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                listOf(
                    "invalid_battery_saver_trigger_state",
                    "invalid_battery_saver_state",
                ),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(state: String?) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("battery-saver", "Battery Saver"),
        triggers = listOf(
            MacroBlock(
                id = "battery-saver-trigger",
                type = "android.battery-saver.changed",
                config = stateConfig(state),
            ),
        ),
        conditions = listOf(
            MacroBlock(
                id = "battery-saver-condition",
                type = "android.battery-saver.state",
                config = stateConfig(state),
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

    private fun stateConfig(state: String?) = if (state == null) {
        emptyMap()
    } else {
        mapOf("state" to MacroValue.Text(state))
    }
}
