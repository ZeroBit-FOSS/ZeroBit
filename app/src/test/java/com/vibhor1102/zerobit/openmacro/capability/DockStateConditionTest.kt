/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.DockState
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import org.junit.Assert.assertEquals
import org.junit.Test

class DockStateConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesEveryBoundedDockStateWithoutAccess() {
        mapOf(
            "undocked" to DockState.UNDOCKED,
            "desk" to DockState.DESK,
            "car" to DockState.CAR,
            "low_end_desk" to DockState.LOW_END_DESK,
            "high_end_desk" to DockState.HIGH_END_DESK,
        ).forEach { (state, expected) ->
            val result = compiler.compile(document(mapOf("state" to MacroValue.Text(state))), "sha256:dock-$state")

            require(result is PlanCompilationResult.Success)
            assertEquals(RuntimeStep.CheckDockState("dock-state", expected), result.plan.conditions.single())
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
    }

    @Test
    fun rejectsMissingUnknownNonTextAndDockIdentityConfiguration() {
        listOf(
            emptyMap(),
            mapOf("state" to MacroValue.Text("vendor_dock")),
            mapOf("state" to MacroValue.Number(1.0)),
            mapOf(
                "state" to MacroValue.Text("desk"),
                "dock_id" to MacroValue.Text("bedside"),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-dock-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key") else listOf("invalid_dock_state"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("dock-condition", "Dock condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(MacroBlock("dock-state", "android.device.dock-state", config)),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )
}
