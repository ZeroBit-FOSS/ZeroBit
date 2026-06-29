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

class BatteryChargingConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesChargingAndNotChargingExpectations() {
        listOf("charging" to true, "not_charging" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(state), "sha256:battery-$state")
            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckBatteryCharging(
                    blockId = "battery-charging",
                    expectedCharging = expected,
                ),
                result.plan.conditions.single(),
            )
        }
    }

    @Test
    fun rejectsMissingOrUnknownChargingState() {
        listOf(null, "plugged_in").forEachIndexed { index, state ->
            val result = compiler.compile(document(state), "sha256:invalid-state-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_charging_state"), result.issues.map { it.code })
        }
    }

    private fun document(state: String?) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("battery-condition", "Battery condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(
            MacroBlock(
                id = "battery-charging",
                type = "android.battery.charging",
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
