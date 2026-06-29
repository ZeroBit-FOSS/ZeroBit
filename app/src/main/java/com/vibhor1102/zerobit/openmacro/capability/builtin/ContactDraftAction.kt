/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.CapabilitySetup
import com.vibhor1102.zerobit.openmacro.capability.describeValueSource
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.capability.requireTextSource
import com.vibhor1102.zerobit.openmacro.capability.validateTextSource
import com.vibhor1102.zerobit.openmacro.capability.valueSource
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.MAX_DIAL_NUMBER_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MAX_EMAIL_ADDRESS_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MAX_CONTACT_NAME_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.isDialablePhoneNumber
import com.vibhor1102.zerobit.openmacro.runtime.isValidEmailAddress
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object ContactDraftAction : CapabilityDefinition {
    override val type = "android.contact.draft"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Create contact draft"
    override val description = "Opens a validated contact draft in Android's contacts app."
    override val creation = CapabilityCreation(
        idBase = "contact-draft",
        setup = CapabilitySetup(fieldKeys = listOf("name", "phoneNumber", "email")),
    )
    override val fields = listOf(
        CapabilityField("name", "Name", CapabilityFieldKind.TEXT, true, "Contact name, up to 200 characters.", acceptsValueSources = true),
        CapabilityField("phoneNumber", "Phone number (optional)", CapabilityFieldKind.TEXT, false, "Optional validated phone number.", acceptsValueSources = true),
        CapabilityField("email", "Email (optional)", CapabilityFieldKind.TEXT, false, "Optional single email address.", acceptsValueSources = true),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(ALLOWED_KEYS, path))
        addAll(block.requireTextSource("name", path, MAX_CONTACT_NAME_LENGTH))
        addAll(block.optionalTextSourceIssues("phoneNumber", path, MAX_DIAL_NUMBER_LENGTH))
        addAll(block.optionalTextSourceIssues("email", path, MAX_EMAIL_ADDRESS_LENGTH))
        val phone = block.config["phoneNumber"] as? MacroValue.Text
        if (phone != null && phone.value.length <= MAX_DIAL_NUMBER_LENGTH && !isDialablePhoneNumber(phone.value)) {
            add(issue(path, "phoneNumber", "invalid_contact_phone", "Use a phone number with digits and standard dialer symbols."))
        }
        val email = block.config["email"] as? MacroValue.Text
        if (email != null && email.value.length <= MAX_EMAIL_ADDRESS_LENGTH && !isValidEmailAddress(email.value)) {
            add(issue(path, "email", "invalid_contact_email", "Use one valid email address without a name or mailto prefix."))
        }
    }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> = listOf("name", "phoneNumber", "email").flatMap { key ->
        block.validateTextSource(key, path, document, registry)
    }

    override fun explain(block: MacroBlock): String =
        "Open a contact draft named ${block.describeValueSource("name")} with optional validated phone and email fields."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep = RuntimeStep.CreateContactDraft(
        blockId = block.id,
        name = block.valueSource("name"),
        phoneNumber = block.config["phoneNumber"]?.let { block.valueSource("phoneNumber") },
        email = block.config["email"]?.let { block.valueSource("email") },
    )

    private fun MacroBlock.optionalTextSourceIssues(
        key: String,
        path: String,
        maxLength: Int,
    ): List<ValidationIssue> = if (config[key] == null) emptyList() else requireTextSource(key, path, maxLength)

    private fun issue(path: String, key: String, code: String, message: String) =
        ValidationIssue("$path.config.$key", code, message)

    private val ALLOWED_KEYS = setOf("name", "phoneNumber", "email")
}
