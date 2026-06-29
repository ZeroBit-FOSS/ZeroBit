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
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialNumberActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesValidatedLiteralWithoutCallPermission() {
        val result = compiler.compile(document(MacroValue.Text("+91 (98765) 43210")), "sha256:dial")
        require(result is PlanCompilationResult.Success)

        assertEquals(
            RuntimeStep.DialNumber(
                "dial-number",
                RuntimeValueSource.Literal(MacroValue.Text("+91 (98765) 43210")),
            ),
            result.plan.actions.single(),
        )
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun rejectsLettersUrisAndNumbersWithoutDigits() {
        listOf("call-me", "tel:+123", "###").forEachIndexed { index, value ->
            val result = compiler.compile(
                document(MacroValue.Text(value)),
                "sha256:invalid-dial-$index",
            )
            require(result is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_dial_number"), result.issues.map { it.code })
        }
    }

    private fun document(phoneNumber: MacroValue) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("dial", "Dial"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        actions = listOf(
            MacroBlock(
                id = "dial-number",
                type = "android.phone.dial",
                config = mapOf("phoneNumber" to phoneNumber),
            ),
        ),
    )
}
