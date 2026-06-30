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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageSettingsActionsTest {
    private val compiler = RuntimePlanCompiler(CapabilityRegistry.builtIn())

    @Test
    fun compileAsDistinctExactPackagePermissionlessSteps() {
        val packageName = "com.example.app"
        val cases = listOf(
            "android.app.language-settings" to
                RuntimeStep.OpenAppLanguageSettings("settings", packageName),
            "android.app.picture-in-picture-settings" to
                RuntimeStep.OpenAppPictureInPictureSettings("settings", packageName),
            "android.app.overlay-settings" to
                RuntimeStep.OpenAppOverlaySettings("settings", packageName),
            "android.app.all-files-access-settings" to
                RuntimeStep.OpenAppAllFilesAccessSettings("settings", packageName),
            "android.app.unknown-sources-settings" to
                RuntimeStep.OpenAppUnknownSourcesSettings("settings", packageName),
            "android.app.notification-bubble-settings" to
                RuntimeStep.OpenAppNotificationBubbleSettings("settings", packageName),
        )

        cases.forEachIndexed { index, (type, expected) ->
            val result = compiler.compile(document(type, packageName), "sha256:package-settings-$index")
            require(result is PlanCompilationResult.Success)
            assertEquals(expected, result.plan.actions.single())
            assertTrue(result.plan.requiredPermissions.isEmpty())
        }
    }

    @Test
    fun rejectUnsafePackagesAndArbitraryIntentFields() {
        val types = listOf(
            "android.app.language-settings",
            "android.app.picture-in-picture-settings",
            "android.app.overlay-settings",
            "android.app.all-files-access-settings",
            "android.app.unknown-sources-settings",
            "android.app.notification-bubble-settings",
        )
        types.forEachIndexed { index, type ->
            val invalidPackage = compiler.compile(
                document(type, "package:com.example.app"),
                "sha256:invalid-package-$index",
            )
            require(invalidPackage is PlanCompilationResult.Invalid)
            assertEquals(listOf("invalid_package_name"), invalidPackage.issues.map { it.code })

            val extra = compiler.compile(
                document(type, "com.example.app", mapOf("action" to MacroValue.Text("unsafe"))),
                "sha256:extra-package-settings-$index",
            )
            require(extra is PlanCompilationResult.Invalid)
            assertEquals(listOf("unknown_config"), extra.issues.map { it.code })
        }
    }

    private fun document(
        type: String,
        packageName: String,
        extra: Map<String, MacroValue> = emptyMap(),
    ) = OpenMacroDocument(
        format = "openmacro/v0.1",
        metadata = MacroMetadata("settings", "Settings"),
        triggers = listOf(MacroBlock("power", "android.power.connected")),
        actions = listOf(
            MacroBlock(
                "settings",
                type,
                mapOf("package" to MacroValue.Text(packageName)) + extra,
            ),
        ),
    )
}
