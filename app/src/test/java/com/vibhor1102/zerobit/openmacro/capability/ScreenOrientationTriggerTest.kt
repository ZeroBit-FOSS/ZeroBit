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
import com.vibhor1102.zerobit.openmacro.runtime.ScreenOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOrientationTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesExplicitTransitionsWithBoundedContext() {
        listOf(
            "portrait" to ScreenOrientation.PORTRAIT,
            "landscape" to ScreenOrientation.LANDSCAPE,
        ).forEach { (source, expected) ->
            val result = compiler.compile(document(source), "sha256:orientation-trigger-$source")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveScreenOrientation("orientation-changed", expected),
                result.plan.triggers.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(
            listOf("screen.orientation"),
            registry.find("android.ui.screen-orientation-changed")
                ?.triggerOutputs
                ?.map { it.key },
        )
    }

    @Test
    fun rejectsMissingUnknownAndSensorConfiguration() {
        listOf(
            emptyMap(),
            mapOf("orientation" to MacroValue.Text("face_up")),
            mapOf(
                "orientation" to MacroValue.Text("portrait"),
                "sensor" to MacroValue.Text("accelerometer"),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-orientation-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 2) listOf("unknown_config_key")
                else listOf("invalid_screen_orientation_trigger"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(orientation: String) =
        document(mapOf("orientation" to MacroValue.Text(orientation)))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("orientation-trigger", "Orientation trigger"),
        triggers = listOf(
            MacroBlock(
                "orientation-changed",
                "android.ui.screen-orientation-changed",
                config,
            ),
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
