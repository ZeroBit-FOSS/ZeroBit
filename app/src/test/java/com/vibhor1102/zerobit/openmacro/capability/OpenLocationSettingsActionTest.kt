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

class OpenLocationSettingsActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesConfigFreeSettingsActionWithoutLocationPermission() {
        val result = compiler.compile(document(), "sha256:location-settings")
        require(result is PlanCompilationResult.Success)

        assertEquals(
            RuntimeStep.OpenLocationSettings("open-location-settings"),
            result.plan.actions.single(),
        )
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun rejectsProviderMutationAndGenericIntentConfig() {
        listOf("provider", "enabled", "action").forEachIndexed { index, key ->
            val result = compiler.compile(
                document(mapOf(key to MacroValue.Text("not allowed"))),
                "sha256:location-settings-$index",
            )
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("unknown_config"), result.issues.map { it.code })
        }
    }

    private fun document(config: Map<String, MacroValue> = emptyMap()) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("location-settings", "Location settings"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        actions = listOf(MacroBlock("open-location-settings", "android.settings.location", config)),
    )
}
