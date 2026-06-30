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

class NfcStateTriggerTest {
    private val registry = CapabilityRegistry.builtIn()
    private val compiler = RuntimePlanCompiler(registry)

    @Test
    fun compilesExplicitTransitionsWithBoundedContext() {
        listOf("enabled" to true, "disabled" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(state), "sha256:nfc-trigger-$state")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.ObserveNfcState("nfc-changed", expected),
                result.plan.triggers.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
        assertEquals(
            listOf("nfc.state"),
            registry.find("android.nfc.state-changed")?.triggerOutputs?.map { it.key },
        )
    }

    @Test
    fun rejectsMissingUnknownAndExtraConfiguration() {
        listOf(
            emptyMap(),
            mapOf("state" to MacroValue.Text("turning_off")),
            mapOf(
                "state" to MacroValue.Text("enabled"),
                "tag" to MacroValue.Text("any"),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-nfc-trigger-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 2) listOf("unknown_config_key")
                else listOf("invalid_nfc_trigger_state"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(state: String) = document(mapOf("state" to MacroValue.Text(state)))

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("nfc-trigger", "NFC trigger"),
        triggers = listOf(MacroBlock("nfc-changed", "android.nfc.state-changed", config)),
        actions = listOf(
            MacroBlock(
                "log",
                "android.log.write",
                mapOf("message" to MacroValue.Text("changed")),
            ),
        ),
    )
}
