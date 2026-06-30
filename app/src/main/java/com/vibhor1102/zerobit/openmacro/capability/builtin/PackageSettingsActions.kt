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
import com.vibhor1102.zerobit.openmacro.capability.CapabilitySetup
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.capability.requireAndroidPackageName
import com.vibhor1102.zerobit.openmacro.capability.text
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object OpenAppLanguageSettingsAction : ExactPackageSettingsAction(
    type = "android.app.language-settings",
    displayName = "Open app language settings",
    description = "Opens language settings for one exact Android package.",
    idBase = "open-app-language-settings",
    explanationPrefix = "Open app language settings for",
    compileStep = { blockId, packageName -> RuntimeStep.OpenAppLanguageSettings(blockId, packageName) },
)

object OpenAppPictureInPictureSettingsAction : ExactPackageSettingsAction(
    type = "android.app.picture-in-picture-settings",
    displayName = "Open app picture-in-picture settings",
    description = "Opens picture-in-picture settings for one exact Android package.",
    idBase = "open-app-picture-in-picture-settings",
    explanationPrefix = "Open app picture-in-picture settings for",
    compileStep = { blockId, packageName ->
        RuntimeStep.OpenAppPictureInPictureSettings(blockId, packageName)
    },
)

object OpenAppOverlaySettingsAction : ExactPackageSettingsAction(
    type = "android.app.overlay-settings",
    displayName = "Open app overlay settings",
    description = "Opens draw-over-other-apps settings for one exact Android package.",
    idBase = "open-app-overlay-settings",
    explanationPrefix = "Open draw-over-other-apps settings for",
    compileStep = { blockId, packageName -> RuntimeStep.OpenAppOverlaySettings(blockId, packageName) },
)

object OpenAppAllFilesAccessSettingsAction : ExactPackageSettingsAction(
    type = "android.app.all-files-access-settings",
    displayName = "Open app all files access settings",
    description = "Opens all-files access settings for one exact Android package.",
    idBase = "open-app-all-files-access-settings",
    explanationPrefix = "Open all-files access settings for",
    compileStep = { blockId, packageName ->
        RuntimeStep.OpenAppAllFilesAccessSettings(blockId, packageName)
    },
)

object OpenAppUnknownSourcesSettingsAction : ExactPackageSettingsAction(
    type = "android.app.unknown-sources-settings",
    displayName = "Open app unknown sources settings",
    description = "Opens install-unknown-apps settings for one exact Android package.",
    idBase = "open-app-unknown-sources-settings",
    explanationPrefix = "Open install-unknown-apps settings for",
    compileStep = { blockId, packageName ->
        RuntimeStep.OpenAppUnknownSourcesSettings(blockId, packageName)
    },
)

object OpenAppNotificationBubbleSettingsAction : ExactPackageSettingsAction(
    type = "android.app.notification-bubble-settings",
    displayName = "Open app notification bubble settings",
    description = "Opens notification-bubble settings for one exact Android package.",
    idBase = "open-app-notification-bubble-settings",
    explanationPrefix = "Open notification-bubble settings for",
    compileStep = { blockId, packageName ->
        RuntimeStep.OpenAppNotificationBubbleSettings(blockId, packageName)
    },
)

abstract class ExactPackageSettingsAction(
    final override val type: String,
    final override val displayName: String,
    final override val description: String,
    idBase: String,
    private val explanationPrefix: String,
    private val compileStep: (String, String) -> RuntimeStep,
) : CapabilityDefinition {
    final override val lane = CapabilityLane.ACTION
    final override val creation = CapabilityCreation(
        idBase = idBase,
        setup = CapabilitySetup(fieldKeys = listOf("package")),
    )
    final override val fields = listOf(
        CapabilityField(
            key = "package",
            label = "App package",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Exact package name, for example com.example.app.",
        ),
    )

    final override fun validate(block: MacroBlock, path: String): List<ValidationIssue> = buildList {
        addAll(block.rejectUnknownConfig(setOf("package"), path))
        addAll(block.requireAndroidPackageName("package", path))
    }

    final override fun explain(block: MacroBlock): String =
        "$explanationPrefix ${runCatching { block.text("package") }.getOrNull() ?: "an invalid package"}."

    final override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    final override fun compile(block: MacroBlock): RuntimeStep =
        compileStep(block.id, block.text("package"))
}
