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

class DeviceLockStateConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun configFreeExistingConditionStillMeansUnlocked() {
        val result = compiler.compile(document(null), "sha256:legacy-unlocked")
        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.CheckDeviceUnlocked("device-lock", expectedUnlocked = true),
            result.plan.conditions.single(),
        )
    }

    @Test
    fun compilesExplicitLockedAndUnlockedChoices() {
        listOf("unlocked" to true, "locked" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(state), "sha256:device-$state")
            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckDeviceUnlocked("device-lock", expected),
                result.plan.conditions.single(),
            )
        }
    }

    @Test
    fun rejectsUnknownOrNonTextState() {
        listOf(
            mapOf("state" to MacroValue.Text("secured")),
            mapOf("state" to MacroValue.Boolean(true)),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-lock-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_device_lock_state"), result.issues.map { it.code })
        }
    }

    private fun document(state: String?) = document(
        if (state == null) emptyMap() else mapOf("state" to MacroValue.Text(state)),
    )

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("device-lock", "Device lock"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(MacroBlock("device-lock", "android.device.unlocked", config)),
        actions = listOf(
            MacroBlock(
                id = "log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )
}
