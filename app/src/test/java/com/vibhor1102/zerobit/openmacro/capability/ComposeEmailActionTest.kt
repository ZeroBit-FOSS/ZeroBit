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

class ComposeEmailActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesValidatedDraftWithoutAccountPermission() {
        val result = compiler.compile(document("person@example.com"), "sha256:email")
        require(result is PlanCompilationResult.Success)

        assertEquals(
            RuntimeStep.ComposeEmail(
                blockId = "compose-email",
                recipient = RuntimeValueSource.Literal(MacroValue.Text("person@example.com")),
                subject = RuntimeValueSource.Literal(MacroValue.Text("Automation report")),
                body = RuntimeValueSource.Literal(MacroValue.Text("The macro completed.")),
            ),
            result.plan.actions.single(),
        )
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun rejectsMalformedRecipientAndUnknownFields() {
        val malformed = compiler.compile(document("mailto:person@example.com"), "sha256:bad-email")
        require(malformed is PlanCompilationResult.Invalid)
        assertEquals(listOf("invalid_email_address"), malformed.issues.map { it.code })

        val unknown = compiler.compile(
            document("person@example.com", mapOf("sendImmediately" to MacroValue.Boolean(true))),
            "sha256:unsafe-email",
        )
        require(unknown is PlanCompilationResult.Invalid)
        assertEquals(listOf("unknown_config"), unknown.issues.map { it.code })
    }

    private fun document(
        recipient: String,
        extra: Map<String, MacroValue> = emptyMap(),
    ) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("email", "Email"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        actions = listOf(
            MacroBlock(
                id = "compose-email",
                type = "android.email.compose",
                config = mapOf(
                    "recipient" to MacroValue.Text(recipient),
                    "subject" to MacroValue.Text("Automation report"),
                    "body" to MacroValue.Text("The macro completed."),
                ) + extra,
            ),
        ),
    )
}
