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

class LaunchAppActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesExactPackageWithoutPermission() {
        val result = compiler.compile(
            document(MacroValue.Text("com.example.music")),
            "sha256:launch",
        )

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeStep.LaunchApp("launch", "com.example.music"),
            result.plan.actions.single(),
        )
        assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
    }

    @Test
    fun rejectsDisplayNamesAndMalformedPackages() {
        val result = compiler.compile(
            document(MacroValue.Text("My Music App")),
            "sha256:invalid",
        )

        require(result is PlanCompilationResult.Invalid)
        assertEquals(listOf("invalid_package_name"), result.issues.map { it.code })
    }

    private fun document(packageName: MacroValue) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("launch", "Launch"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = emptyList(),
        actions = listOf(
            MacroBlock(
                id = "launch",
                type = "android.app.launch",
                config = mapOf("package" to packageName),
            ),
        ),
    )
}
