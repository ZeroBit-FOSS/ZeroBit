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

class WiredHeadsetTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesConnectedAndDisconnectedWithBoundedContext() {
        listOf("connected" to true, "disconnected" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(state), "sha256:headset-trigger-$state")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveWiredHeadset("headset-changed", expected),
                result.plan.triggers.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(
            listOf("wired_headset.state"),
            registry.find("android.audio.wired-headset-state-changed")
                ?.triggerOutputs
                ?.map { it.key },
        )
    }

    @Test
    fun rejectsMissingUnknownAndDeviceMetadata() {
        listOf(
            emptyMap(),
            mapOf("state" to MacroValue.Text("bluetooth")),
            mapOf(
                "state" to MacroValue.Text("connected"),
                "name" to MacroValue.Text("headphones"),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-headset-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 2) listOf("unknown_config_key")
                else listOf("invalid_wired_headset_trigger_state"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(state: String) = document(mapOf("state" to MacroValue.Text(state)))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("headset-trigger", "Headset trigger"),
        triggers = listOf(
            MacroBlock(
                "headset-changed",
                "android.audio.wired-headset-state-changed",
                config,
            ),
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
