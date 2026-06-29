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
import com.vibhor1102.zerobit.openmacro.capability.CapabilitySetup
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.capability.requireAndroidPackageName
import com.vibhor1102.zerobit.openmacro.capability.text
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object OpenAppNotificationSettingsAction : CapabilityDefinition {
    override val type = "android.app.notification-settings"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Open notification settings"
    override val description =
        "Opens Android's notification settings page for one exact package."
    override val creation = CapabilityCreation(
        idBase = "open-notification-settings",
        setup = CapabilitySetup(fieldKeys = listOf("package")),
    )
    override val fields = listOf(
        CapabilityField(
            key = "package",
            label = "App package",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Exact package name, for example com.example.app.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("package"), path))
            addAll(block.requireAndroidPackageName("package", path))
        }

    override fun explain(block: MacroBlock): String =
        "Open Android notification settings for package ${
            runCatching { block.text("package") }.getOrNull() ?: "not set"
        }."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.OpenAppNotificationSettings(
            blockId = block.id,
            packageName = block.text("package"),
        )
}
