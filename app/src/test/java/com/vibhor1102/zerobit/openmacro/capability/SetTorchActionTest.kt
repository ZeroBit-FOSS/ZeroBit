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

class SetTorchActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesExplicitTorchStateAndCameraAccess() {
        listOf(false, true).forEach { enabled ->
            val result = compiler.compile(document(MacroValue.Boolean(enabled)), "sha256:torch-$enabled")

            require(result is PlanCompilationResult.Success)
            assertEquals(RuntimeStep.SetTorch("torch", enabled), result.plan.actions.single())
            assertEquals(setOf(AndroidPermission.CAMERA), result.plan.requiredPermissions)
        }
    }

    @Test
    fun rejectsMissingNonBooleanAndUnknownConfiguration() {
        listOf(
            emptyMap(),
            mapOf("enabled" to MacroValue.Text("true")),
            mapOf("enabled" to MacroValue.Boolean(true), "mode" to MacroValue.Text("toggle")),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-torch-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 2) listOf("unknown_config_key") else listOf("invalid_torch_state"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(enabled: MacroValue) = document(mapOf("enabled" to enabled))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("torch-test", "Torch test"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = listOf(MacroBlock("torch", "android.torch.set", config)),
    )
}
