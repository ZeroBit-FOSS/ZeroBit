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

class ScreenOrientationConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesExplicitPortraitAndLandscapeWithoutAccess() {
        listOf(
            "portrait" to ScreenOrientation.PORTRAIT,
            "landscape" to ScreenOrientation.LANDSCAPE,
        ).forEach { (source, expected) ->
            val result = compiler.compile(document(mapOf("orientation" to MacroValue.Text(source))), "sha256:$source")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckScreenOrientation("screen-orientation", expected),
                result.plan.conditions.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
    }

    @Test
    fun rejectsMissingUnknownNonTextAndSensorConfiguration() {
        listOf(
            emptyMap(),
            mapOf("orientation" to MacroValue.Text("face_up")),
            mapOf("orientation" to MacroValue.Boolean(true)),
            mapOf(
                "orientation" to MacroValue.Text("portrait"),
                "sensor" to MacroValue.Text("accelerometer"),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-orientation-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key")
                else listOf("invalid_screen_orientation"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("orientation-condition", "Orientation condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(
            MacroBlock("screen-orientation", "android.ui.screen-orientation", config),
        ),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )
}
