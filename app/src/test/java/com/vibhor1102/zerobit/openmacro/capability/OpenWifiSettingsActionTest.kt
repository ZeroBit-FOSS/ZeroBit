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

class OpenWifiSettingsActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesConfigFreeSettingsActionWithoutPermission() {
        val result = compiler.compile(document(), "sha256:wifi-settings")
        require(result is PlanCompilationResult.Success)

        assertEquals(RuntimeStep.OpenWifiSettings("open-wifi-settings"), result.plan.actions.single())
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun rejectsGenericSettingsIntentConfig() {
        val result = compiler.compile(
            document(mapOf("action" to MacroValue.Text("android.settings.SETTINGS"))),
            "sha256:generic-settings",
        )
        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("unknown_config"), result.issues.map { it.code })
    }

    private fun document(config: Map<String, MacroValue> = emptyMap()) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("wifi-settings", "Wi-Fi settings"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        actions = listOf(MacroBlock("open-wifi-settings", "android.settings.wifi", config)),
    )
}
