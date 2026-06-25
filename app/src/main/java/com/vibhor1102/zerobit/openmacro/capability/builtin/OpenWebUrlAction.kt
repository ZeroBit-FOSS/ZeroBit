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
import java.net.URI

object OpenWebUrlAction : CapabilityDefinition {
    override val type = "android.web.open"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Open web page"
    override val description =
        "Opens one explicit HTTP or HTTPS address in a browser."
    override val fields = listOf(
        CapabilityField(
            key = "url",
            label = "Web address",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "A complete https:// or http:// address.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("url"), path))
            addAll(block.requireText("url", path, MAX_URL_LENGTH))
            val url = runCatching { block.text("url") }.getOrNull()
            if (url != null && !isAllowedWebUrl(url)) {
                add(
                    ValidationIssue(
                        "$path.config.url",
                        "invalid_web_url",
                        "Use a complete HTTP or HTTPS address with a host.",
                    ),
                )
            }
        }

    override fun explain(block: MacroBlock): String =
        "Open ${(runCatching { block.text("url") }.getOrNull() ?: "an invalid web address")}."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.OpenWebUrl(block.id, block.text("url"))

    private fun isAllowedWebUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }

    private const val MAX_URL_LENGTH = 2_048
}
