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

object LaunchAppAction : CapabilityDefinition {
    override val type = "android.app.launch"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Launch app"
    override val description =
        "Opens the normal launcher activity for one exact Android package."
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
            addAll(block.requireText("package", path, MAX_PACKAGE_LENGTH))
            val packageName = runCatching { block.text("package") }.getOrNull()
            if (packageName != null && !PACKAGE_PATTERN.matches(packageName)) {
                add(
                    ValidationIssue(
                        "$path.config.package",
                        "invalid_package_name",
                        "Use an exact Android package name such as com.example.app.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        "Open the app package ${(runCatching { block.text("package") }.getOrNull() ?: "not set")}."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.LaunchApp(block.id, block.text("package"))

    private const val MAX_PACKAGE_LENGTH = 255
    private val PACKAGE_PATTERN =
        Regex("""[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+""")
}
