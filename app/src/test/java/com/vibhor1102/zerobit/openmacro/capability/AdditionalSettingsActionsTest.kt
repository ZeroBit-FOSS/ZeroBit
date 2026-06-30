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

class AdditionalSettingsActionsTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compileAsDistinctConfigFreePermissionlessSteps() {
        val cases = listOf(
            "android.settings.default-apps" to RuntimeStep.OpenDefaultAppsSettings("settings"),
            "android.settings.developer-options" to RuntimeStep.OpenDeveloperOptions("settings"),
            "android.settings.wireless" to RuntimeStep.OpenWirelessSettings("settings"),
        )

        cases.forEachIndexed { index, (type, expected) ->
            val result = compiler.compile(document(type), "sha256:settings-$index")
            require(result is PlanCompilationResult.Success)
            assertEquals(expected, result.plan.actions.single())
            assertTrue(result.plan.requiredPermissions.isEmpty())
        }
    }

    @Test
    fun rejectRolePackageMutationAndGenericIntentFields() {
        val cases = listOf(
            "android.settings.default-apps" to "role",
            "android.settings.default-apps" to "package",
            "android.settings.developer-options" to "enabled",
            "android.settings.developer-options" to "setting",
            "android.settings.wireless" to "radio",
            "android.settings.wireless" to "connected",
            "android.settings.wireless" to "action",
        )

        cases.forEachIndexed { index, (type, key) ->
            val result = compiler.compile(
                document(type, mapOf(key to MacroValue.Text("not allowed"))),
                "sha256:invalid-settings-$index",
            )
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("unknown_config"), result.issues.map { it.code })
        }
    }

    private fun document(
        type: String,
        config: Map<String, MacroValue> = emptyMap(),
    ) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("settings", "Settings"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        actions = listOf(MacroBlock("settings", type, config)),
    )
}
