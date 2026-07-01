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

class DeviceIdleModeTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesBothTransitionsWithBoundedContext() {
        listOf("idle" to true, "not_idle" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(mapOf("state" to MacroValue.Text(state))), "sha256:idle-trigger-$state")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveDeviceIdleMode("idle-changed", expected),
                result.plan.triggers.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(
            listOf("device_idle.state"),
            registry.find("android.device.idle-mode-changed")?.triggerOutputs?.map { it.key },
        )
    }

    @Test
    fun rejectsMissingUnknownNonTextAndTimingConfiguration() {
        listOf(
            emptyMap(),
            mapOf("state" to MacroValue.Text("light_idle")),
            mapOf("state" to MacroValue.Boolean(true)),
            mapOf("state" to MacroValue.Text("idle"), "debounce_seconds" to MacroValue.Number(5.0)),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-idle-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key") else listOf("invalid_device_idle_trigger_state"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("idle-trigger", "Idle trigger"),
        triggers = listOf(MacroBlock("idle-changed", "android.device.idle-mode-changed", config)),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("changed")),
            ),
        ),
    )
}
