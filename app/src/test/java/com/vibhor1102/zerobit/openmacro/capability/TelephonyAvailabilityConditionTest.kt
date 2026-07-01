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

class TelephonyAvailabilityConditionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesAvailableAndUnavailableWithoutTelephonyAccess() {
        listOf("available" to true, "unavailable" to false).forEach { (state, expected) ->
            val result = compiler.compile(document(mapOf("state" to MacroValue.Text(state))), "sha256:telephony-$state")

            require(result is PlanCompilationResult.Success)
            assertEquals(
                RuntimeStep.CheckTelephonyAvailability("telephony-availability", expected),
                result.plan.conditions.single(),
            )
            assertEquals(emptySet<AndroidPermission>(), result.plan.requiredPermissions)
        }
    }

    @Test
    fun rejectsMissingUnknownNonTextAndSubscriptionConfiguration() {
        listOf(
            emptyMap(),
            mapOf("state" to MacroValue.Text("sim_ready")),
            mapOf("state" to MacroValue.Boolean(true)),
            mapOf(
                "state" to MacroValue.Text("available"),
                "subscription_id" to MacroValue.Number(1.0),
            ),
        ).forEachIndexed { index, config ->
            val result = compiler.compile(document(config), "sha256:invalid-telephony-$index")

            require(result is PlanCompilationResult.Invalid)
            assertEquals(
                if (index == 3) listOf("unknown_config_key") else listOf("invalid_telephony_availability"),
                result.issues.map { it.code },
            )
        }
    }

    private fun document(config: Map<String, MacroValue>) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("telephony-availability", "Telephony availability"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        conditions = listOf(
            MacroBlock("telephony-availability", "android.telephony.availability", config),
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
