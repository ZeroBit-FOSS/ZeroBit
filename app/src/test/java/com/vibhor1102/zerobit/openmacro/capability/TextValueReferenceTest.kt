/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroMetadata
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.PlanCompilationResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimePlanCompiler
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class TextValueReferenceTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesTextFieldsFromVariablesSecretsAndTriggerContext() {
        val document = OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("references", "References"),
            variables = listOf(
                MacroVariable("phone", MacroVariableType.TEXT, MacroValue.Text("+12345")),
                MacroVariable("token", MacroVariableType.SECRET, secretKey = "account.token"),
            ),
            triggers = listOf(
                MacroBlock("screen-on", "android.screen.on"),
            ),
            conditions = emptyList(),
            actions = listOf(
                MacroBlock(
                    id = "log-state",
                    type = "android.log.write",
                    config = mapOf(
                        "message" to reference("trigger", "screen.state"),
                    ),
                ),
                MacroBlock(
                    id = "notify-token",
                    type = "android.notification.show",
                    config = mapOf(
                        "title" to MacroValue.Text("Token"),
                        "message" to reference("variable", "token"),
                    ),
                ),
                MacroBlock(
                    id = "send-state",
                    type = "android.sms.send",
                    config = mapOf(
                        "phoneNumber" to reference("variable", "phone"),
                        "message" to reference("trigger", "screen.state"),
                    ),
                ),
            ),
        )

        val result = compiler.compile(document, "sha256:text-references")

        require(result is PlanCompilationResult.Success)
        assertEquals(
            RuntimeValueSource.Trigger("screen.state"),
            (result.plan.actions[0] as RuntimeStep.WriteLog).message,
        )
        assertEquals(
            RuntimeValueSource.Variable("token"),
            (result.plan.actions[1] as RuntimeStep.ShowNotification).message,
        )
        assertEquals(
            RuntimeValueSource.Variable("phone"),
            (result.plan.actions[2] as RuntimeStep.SendSms).phoneNumber,
        )
    }

    @Test
    fun rejectsNumberReferenceWhereTextIsRequired() {
        val document = OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("references", "References"),
            variables = listOf(
                MacroVariable(
                    "count",
                    MacroVariableType.NUMBER,
                    MacroValue.Number(BigDecimal.ONE),
                ),
            ),
            triggers = listOf(MacroBlock("power", "android.power.connected")),
            conditions = emptyList(),
            actions = listOf(
                MacroBlock(
                    id = "log-count",
                    type = "android.log.write",
                    config = mapOf(
                        "message" to reference("variable", "count"),
                    ),
                ),
            ),
        )

        val result = compiler.compile(document, "sha256:wrong-text-reference")

        require(result is PlanCompilationResult.Invalid)
        assertEquals(
            listOf("value_reference_type_mismatch"),
            result.issues.map { it.code },
        )
    }

    private fun reference(kind: String, name: String) =
        MacroValue.ObjectValue(mapOf(kind to MacroValue.Text(name)))
}
