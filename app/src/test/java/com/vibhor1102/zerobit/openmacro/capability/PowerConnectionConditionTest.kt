/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.PowerSource
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerConnectionConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesPluggedInAndUnpluggedExpectations() {
        listOf("plugged_in" to true, "unplugged" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(state), "sha256:power-$state")
            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckPowerConnection("power-connection", expected),
                result.plan.conditions.single(),
            )
        }
    }

    @Test
    fun rejectsMissingOrUnknownPowerState() {
        listOf(null, "charging").forEachIndexed { index, state ->
            val result = compiler.compile(document(state), "sha256:invalid-power-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_power_connection_state"), result.issues.map { it.code })
        }
    }

    @Test
    fun compilesEveryExactPowerSource() {
        listOf(
            "ac" to PowerSource.AC,
            "usb" to PowerSource.USB,
            "wireless" to PowerSource.WIRELESS,
            "dock" to PowerSource.DOCK,
        ).forEach { (source, expected) ->
            val result = compiler.compile(
                document("plugged_in", source),
                "sha256:power-source-$source",
            )
            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckPowerConnection("power-connection", true, expected),
                result.plan.conditions.single(),
            )
        }
    }

    @Test
    fun rejectsUnknownSourceAndSpecificSourceWhileUnplugged() {
        listOf(
            document("plugged_in", "solar") to "invalid_power_source",
            document("unplugged", "usb") to "power_source_requires_plugged_in",
        ).forEachIndexed { index, (document, issueCode) ->
            val result = compiler.compile(document, "sha256:invalid-source-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf(issueCode), result.issues.map { it.code })
        }
    }

    private fun document(state: String?, source: String? = null) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("power-condition", "Power condition"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(
            MacroBlock(
                id = "power-connection",
                type = "android.power.connection-state",
                config = buildMap {
                    if (state != null) put("state", MacroValue.Text(state))
                    if (source != null) put("source", MacroValue.Text(source))
                },
            ),
        ),
        actions = listOf(
            MacroBlock(
                id = "log",
                type = "android.log.write",
                config = mapOf("message" to MacroValue.Text("matched")),
            ),
        ),
    )
}
