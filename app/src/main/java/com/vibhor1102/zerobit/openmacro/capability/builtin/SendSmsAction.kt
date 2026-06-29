/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityCreation
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
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object SendSmsAction : CapabilityDefinition {
    override val type = "android.sms.send"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Send SMS"
    override val description = "Sends an SMS message to a phone number."
    override val creation = CapabilityCreation(
        idBase = "send-sms",
        setup = CapabilitySetup(fieldKeys = listOf("phoneNumber", "message")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "phoneNumber",
            label = "Phone Number",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "The recipient's phone number.",
            acceptsValueSources = true,
        ),
        CapabilityField(
            key = "message",
            label = "Message",
            kind = CapabilityFieldKind.MULTILINE_TEXT,
            required = true,
            help = "The text message content (up to 160 characters).",
            acceptsValueSources = true,
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("phoneNumber", "message"), path))
            addAll(block.requireTextSource("phoneNumber", path, maxLiteralLength = 30))
            addAll(block.requireTextSource("message", path, maxLiteralLength = 160))
        }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> = buildList {
        addAll(block.validateTextSource("phoneNumber", path, document, registry))
        addAll(block.validateTextSource("message", path, document, registry))
    }

    override fun explain(block: MacroBlock): String =
        "Send ${block.describeValueSource("message")} by SMS to ${block.describeValueSource("phoneNumber")}."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> =
        setOf(AndroidPermission.SEND_SMS)

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.SendSms(
            blockId = block.id,
            phoneNumber = block.valueSource("phoneNumber"),
            message = block.valueSource("message"),
        )
}
