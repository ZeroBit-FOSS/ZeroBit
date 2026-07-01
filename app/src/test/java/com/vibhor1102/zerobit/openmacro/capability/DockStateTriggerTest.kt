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

class DockStateTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesEveryBoundedTransitionWithCanonicalContext() {
        mapOf(
            "undocked" to DockState.UNDOCKED,
            "desk" to DockState.DESK,
            "car" to DockState.CAR,
            "low_end_desk" to DockState.LOW_END_DESK,
            "high_end_desk" to DockState.HIGH_END_DESK,
        ).forEach { (state, expected) ->
            val result = compiler.compile(document(mapOf("state" to MacroValue.Text(state))), "sha256:dock-trigger-$state")
            require(result is PlanCompilationResult.Success)
            assertEquals(RuntimeStep.ObserveDockState("dock-changed", expected), result.plan.triggers.single())
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(listOf("dock.state"), registry.find("android.device.dock-state-changed")?.triggerOutputs?.map { it.key })
    }

    @Test
    fun rejectsMissingUnknownNonTextAndIdentityConfiguration() {
        listOf(
            emptyMap(),
            mapOf("state" to MacroValue.Text("vendor_dock")),
            mapOf("state" to MacroValue.Boolean(true)),
            mapOf("state" to MacroValue.Text("desk"), "dock_id" to MacroValue.Text("bedside")),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-dock-trigger-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key") else listOf("invalid_dock_state_trigger"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("dock-trigger", "Dock trigger"),
        triggers = listOf(MacroBlock("dock-changed", "android.device.dock-state-changed", config)),
        actions = listOf(MacroBlock("log", "android.log.write", mapOf("message" to MacroValue.Text("changed")))),
    )
}
