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

class OpenAppNotificationSettingsActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesExactPackageNotificationSettingsIntent() {
        val result = compiler.compile(
            document(
                mapOf("package" to MacroValue.Text("com.example.chat")),
            ),
            "sha256:notification-settings",
        )

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.OpenAppNotificationSettings(
                blockId = "notifications",
                packageName = "com.example.chat",
            ),
            result.plan.actions.single(),
        )
    }

    @Test
    fun rejectsUnsafePackageNames() {
        val result = compiler.compile(
            document(
                mapOf("package" to MacroValue.Text("com.example/chat")),
            ),
            "sha256:bad-package",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("invalid_package_name"), result.issues.map { it.code })
    }

    @Test
    fun rejectsUnknownConfigKeys() {
        val result = compiler.compile(
            document(
                mapOf(
                    "package" to MacroValue.Text("com.example.chat"),
                    "channel" to MacroValue.Text("unsupported"),
                ),
            ),
            "sha256:unknown-key",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("unknown_config"), result.issues.map { it.code })
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("settings", "Settings"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = listOf(
            MacroBlock(
                id = "notifications",
                type = "android.app.notification-settings",
                config = config,
            ),
        ),
    )
}
