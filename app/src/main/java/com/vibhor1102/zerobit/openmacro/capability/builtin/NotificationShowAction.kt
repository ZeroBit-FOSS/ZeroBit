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

object NotificationShowAction : CapabilityDefinition {
    override val type = "android.notification.show"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Show notification"
    override val description = "Displays a local Android notification."
    override val fields = listOf(
        CapabilityField(
            key = "title",
            label = "Title",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "The short heading shown in the notification.",
        ),
        CapabilityField(
            key = "message",
            label = "Message",
            kind = CapabilityFieldKind.MULTILINE_TEXT,
            required = true,
            help = "The notification text.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("title", "message"), path))
            addAll(block.requireText("title", path, maxLength = 120))
            addAll(block.requireText("message", path, maxLength = 1_000))
        }

    override fun explain(block: MacroBlock): String =
        "Show a notification titled “${block.text("title")}” with the message “${block.text("message")}”."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> =
        setOf(AndroidPermission.POST_NOTIFICATIONS)

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ShowNotification(
            blockId = block.id,
            title = block.text("title"),
            message = block.text("message"),
        )
}
