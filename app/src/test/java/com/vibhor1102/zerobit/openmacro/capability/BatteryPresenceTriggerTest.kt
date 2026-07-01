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

class BatteryPresenceTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesPresentAndNotPresentTransitionsWithBoundedContext() {
        listOf("present" to true, "not_present" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(mapOf("state" to MacroValue.Text(state))), "sha256:presence-trigger-$state")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveBatteryPresence("presence-changed", expected),
                result.plan.triggers.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(
            listOf("battery.presence"),
            registry.find("android.battery.presence-changed")?.triggerOutputs?.map { it.key },
        )
    }

    @Test
    fun rejectsUnknownNonTextAndBatteryIdentityConfiguration() {
        listOf(
            mapOf("state" to MacroValue.Text("unknown")),
            mapOf("state" to MacroValue.Boolean(true)),
            mapOf(
                "state" to MacroValue.Text("present"),
                "battery_id" to MacroValue.Text("internal"),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-presence-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 2) listOf("unknown_config_key")
                else listOf("invalid_battery_presence_trigger"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("presence-trigger", "Presence trigger"),
        triggers = listOf(
            MacroBlock("presence-changed", "android.battery.presence-changed", config),
        ),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("changed")),
            ),
        ),
    )
}
