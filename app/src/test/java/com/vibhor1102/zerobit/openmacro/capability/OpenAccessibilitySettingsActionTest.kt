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
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAccessibilitySettingsActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesConfigFreeSettingsActionWithoutAccessibilityAccess() {
        val result = compiler.compile(document(), "sha256:accessibility-settings")
        require(result is PlanCompilationResult.Success)

        assertEquals(
            RuntimeStep.OpenAccessibilitySettings("open-accessibility-settings"),
            result.plan.actions.single(),
        )
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun rejectsServiceTargetAndGenericIntentConfig() {
        listOf("service", "component", "enabled", "action").forEachIndexed { index, key ->
            val result = compiler.compile(
                document(mapOf(key to MacroValue.Text("not allowed"))),
                "sha256:accessibility-settings-$index",
            )
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("unknown_config"), result.issues.map { it.code })
        }
    }

    private fun document(config: Map<String, MacroValue> = emptyMap()) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("accessibility-settings", "Accessibility settings"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        actions = listOf(
            MacroBlock("open-accessibility-settings", "android.settings.accessibility", config),
        ),
    )
}
