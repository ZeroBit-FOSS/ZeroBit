/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.capability.requireText
import com.vibhor1102.zerobit.openmacro.capability.text
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object SendSmsAction : CapabilityDefinition {
    override val type = "android.sms.send"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Send SMS"
    override val description = "Sends an SMS message to a phone number."
    override val fields = listOf(
        CapabilityField(
            key = "phoneNumber",
            label = "Phone Number",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "The recipient's phone number.",
        ),
        CapabilityField(
            key = "message",
            label = "Message",
            kind = CapabilityFieldKind.MULTILINE_TEXT,
            required = true,
            help = "The text message content (up to 160 characters).",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("phoneNumber", "message"), path))
            addAll(block.requireText("phoneNumber", path, maxLength = 30))
            addAll(block.requireText("message", path, maxLength = 160))
        }

    override fun explain(block: MacroBlock): String =
        "Send SMS to ${block.text("phoneNumber")}: “${block.text("message")}”."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> =
        setOf(AndroidPermission.SEND_SMS)

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.SendSms(
            blockId = block.id,
            phoneNumber = block.text("phoneNumber"),
            message = block.text("message"),
        )
}
