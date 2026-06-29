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

class ContactDraftActionTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compilesValidatedDraftWithoutContactsPermission() {
        val result = compiler.compile(validDocument(), "sha256:contact")
        require(result is PlanCompilationResult.Success)

        assertEquals(
            RuntimeStep.CreateContactDraft(
                blockId = "contact-draft",
                name = RuntimeValueSource.Literal(MacroValue.Text("Ada Lovelace")),
                phoneNumber = RuntimeValueSource.Literal(MacroValue.Text("+44 20 7946 0958")),
                email = RuntimeValueSource.Literal(MacroValue.Text("ada@example.com")),
            ),
            result.plan.actions.single(),
        )
        assertTrue(result.plan.requiredPermissions.isEmpty())
    }

    @Test
    fun acceptsNameOnlyDraft() {
        val result = compiler.compile(
            validDocument(mapOf("phoneNumber" to null, "email" to null)),
            "sha256:contact-name-only",
        )
        require(result is PlanCompilationResult.Success)
        val step = result.plan.actions.single() as RuntimeStep.CreateContactDraft
        assertEquals(null, step.phoneNumber)
        assertEquals(null, step.email)
    }

    @Test
    fun rejectsMalformedContactValuesAndRawContactFields() {
        listOf(
            mapOf("phoneNumber" to MacroValue.Text("call-me")) to "invalid_contact_phone",
            mapOf("email" to MacroValue.Text("mailto:ada@example.com")) to "invalid_contact_email",
            mapOf("rawContactId" to MacroValue.Text("42")) to "unknown_config",
        ).forEachIndexed { index, (override, code) ->
            val result = compiler.compile(validDocument(override), "sha256:bad-contact-$index")
            require(result is PlanCompilationResult.Invalid)
            assertEquals(code, result.issues.single().code)
        }
    }

    private fun validDocument(override: Map<String, MacroValue?> = emptyMap()): OpenMacroDocument {
        val config = mutableMapOf<String, MacroValue>(
            "name" to MacroValue.Text("Ada Lovelace"),
            "phoneNumber" to MacroValue.Text("+44 20 7946 0958"),
            "email" to MacroValue.Text("ada@example.com"),
        )
        override.forEach { (key, value) ->
            if (value == null) config.remove(key) else config[key] = value
        }
        return OpenMacroDocument(
            format = "openmacro/v0.1",
            metadata = MacroMetadata("contact", "Contact"),
            triggers = listOf(MacroBlock("power", "android.power.connected")),
            actions = listOf(MacroBlock("contact-draft", "android.contact.draft", config)),
        )
    }
}
