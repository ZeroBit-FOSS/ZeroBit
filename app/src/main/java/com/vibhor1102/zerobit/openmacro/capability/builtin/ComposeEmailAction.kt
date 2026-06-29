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
import com.vibhor1102.zerobit.openmacro.runtime.MAX_EMAIL_ADDRESS_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MAX_EMAIL_BODY_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.MAX_EMAIL_SUBJECT_LENGTH
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.isValidEmailAddress
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object ComposeEmailAction : CapabilityDefinition {
    override val type = "android.email.compose"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Compose email"
    override val description = "Opens an email app with validated draft fields."
    override val creation = CapabilityCreation(
        idBase = "compose-email",
        setup = CapabilitySetup(fieldKeys = listOf("recipient", "subject", "body")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "recipient",
            label = "Recipient",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "One email address to place in the To field.",
            acceptsValueSources = true,
        ),
        CapabilityField(
            key = "subject",
            label = "Subject",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Subject for the draft email.",
            acceptsValueSources = true,
        ),
        CapabilityField(
            key = "body",
            label = "Body",
            kind = CapabilityFieldKind.MULTILINE_TEXT,
            required = true,
            help = "Body for the draft email.",
            acceptsValueSources = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("recipient", "subject", "body"), path))
        addAll(block.requireTextSource("recipient", path, MAX_EMAIL_ADDRESS_LENGTH))
        addAll(block.requireTextSource("subject", path, MAX_EMAIL_SUBJECT_LENGTH))
        addAll(block.requireTextSource("body", path, MAX_EMAIL_BODY_LENGTH))
        val recipient = block.config["recipient"] as? MacroValue.Text
        if (
            recipient != null &&
            recipient.value.length <= MAX_EMAIL_ADDRESS_LENGTH &&
            !isValidEmailAddress(recipient.value)
        ) {
            add(
                ValidationIssue(
                    path = "$path.config.recipient",
                    code = "invalid_email_address",
                    message = "Use one valid email address without a name or mailto prefix.",
                ),
            )
        }
    }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> = listOf("recipient", "subject", "body").flatMap { key ->
        block.validateTextSource(key, path, document, registry)
    }

    override fun explain(block: MacroBlock): String =
        "Open an email draft to ${block.describeValueSource("recipient")} with ${
            block.describeValueSource("subject")
        } and ${block.describeValueSource("body")}; do not send it."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep = RuntimeStep.ComposeEmail(
        blockId = block.id,
        recipient = block.valueSource("recipient"),
        subject = block.valueSource("subject"),
        body = block.valueSource("body"),
    )
}
