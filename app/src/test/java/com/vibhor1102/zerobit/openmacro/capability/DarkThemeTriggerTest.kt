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

class DarkThemeTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesExplicitTransitionsWithBoundedContext() {
        listOf("dark" to true, "light" to false).forEach { (theme, expected) ->
            val result = compiler.compile(document(theme), "sha256:theme-trigger-$theme")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveDarkTheme("theme-changed", expected),
                result.plan.triggers.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(
            listOf("theme.state"),
            registry.find("android.ui.theme-state-changed")?.triggerOutputs?.map { it.key },
        )
    }

    @Test
    fun rejectsMissingUnknownAndScheduleConfiguration() {
        listOf(
            emptyMap(),
            mapOf("theme" to MacroValue.Text("automatic")),
            mapOf(
                "theme" to MacroValue.Text("dark"),
                "schedule" to MacroValue.Text("sunset"),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-theme-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 2) listOf("unknown_config_key")
                else listOf("invalid_theme_trigger_state"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(theme: String) = document(mapOf("theme" to MacroValue.Text(theme)))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("theme-trigger", "Theme trigger"),
        triggers = listOf(
            MacroBlock("theme-changed", "android.ui.theme-state-changed", config),
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
