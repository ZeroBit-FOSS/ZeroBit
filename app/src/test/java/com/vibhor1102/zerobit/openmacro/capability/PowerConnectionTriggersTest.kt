/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerConnectionTriggersTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesConnectedAndDisconnectedBroadcastTriggers() {
        val result = compiler.compile(document(), "sha256:power-events")
        require(result is PlanCompilationResult.Success)
        assertEquals(
            listOf(
                RuntimeStep.ObservePowerConnected("power-connected"),
                RuntimeStep.ObservePowerDisconnected("power-disconnected"),
            ),
            result.plan.triggers,
        )
    }

    @Test
    fun publishesOnlyBoundedPowerContext() {
        val registry = CapabilityRegistry.builtIn()

        assertEquals(
            listOf("power.state", "power.source"),
            registry.find("android.power.connected")?.triggerOutputs?.map { it.key },
        )
        assertEquals(
            listOf(MacroVariableType.TEXT, MacroVariableType.TEXT),
            registry.find("android.power.connected")?.triggerOutputs?.map { it.type },
        )
        assertEquals(
            listOf("power.state"),
            registry.find("android.power.disconnected")?.triggerOutputs?.map { it.key },
        )
    }

    private fun document() = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("power-events", "Power events"),
        triggers = listOf(
            MacroBlock("power-connected", "android.power.connected"),
            MacroBlock("power-disconnected", "android.power.disconnected"),
        ),
        actions = listOf(
            MacroBlock(
                id = "log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("power changed")),
            ),
        ),
    )
}
