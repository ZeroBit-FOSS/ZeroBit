/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.validation

import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument

object OpenMacroValidator {
    const val SUPPORTED_FORMAT = "openmacro/v0.1"

    private val stableIdPattern = Regex("^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$")
    private val capabilityTypePattern =
        Regex("^[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+$")

    fun validate(
        document: OpenMacroDocument,
        registry: CapabilityRegistry? = null,
    ): List<ValidationIssue> = buildList {
        if (document.format != SUPPORTED_FORMAT) {
            add(
                ValidationIssue(
                    path = "$.format",
                    code = "unsupported_format",
                    message = "Expected format '$SUPPORTED_FORMAT'.",
                ),
            )
        }

        validateStableId(document.metadata.id, "$.metadata.id", "Macro", this)

        if (document.metadata.name.isBlank()) {
            add(
                ValidationIssue(
                    path = "$.metadata.name",
                    code = "blank_name",
                    message = "Macro name must not be blank.",
                ),
            )
        } else if (document.metadata.name.length > 120) {
            add(
                ValidationIssue(
                    path = "$.metadata.name",
                    code = "name_too_long",
                    message = "Macro name must be 120 characters or fewer.",
                ),
            )
        }

        if (document.triggers.isEmpty()) {
            add(
                ValidationIssue(
                    path = "$.triggers",
                    code = "missing_trigger",
                    message = "A macro needs at least one trigger.",
                ),
            )
        }

        if (document.actions.isEmpty()) {
            add(
                ValidationIssue(
                    path = "$.actions",
                    code = "missing_action",
                    message = "A macro needs at least one action.",
                ),
            )
        }

        val locationsById = mutableMapOf<String, String>()
        validateBlocks(
            section = "triggers",
            expectedLane = CapabilityLane.TRIGGER,
            blocks = document.triggers,
            locationsById = locationsById,
            registry = registry,
            issues = this,
        )
        validateBlocks(
            section = "conditions",
            expectedLane = CapabilityLane.CONDITION,
            blocks = document.conditions,
            locationsById = locationsById,
            registry = registry,
            issues = this,
        )
        validateBlocks(
            section = "actions",
            expectedLane = CapabilityLane.ACTION,
            blocks = document.actions,
            locationsById = locationsById,
            registry = registry,
            issues = this,
        )
    }

    private fun validateBlocks(
        section: String,
        expectedLane: CapabilityLane,
        blocks: List<MacroBlock>,
        locationsById: MutableMap<String, String>,
        registry: CapabilityRegistry?,
        issues: MutableList<ValidationIssue>,
    ) {
        blocks.forEachIndexed { index, block ->
            val path = "$.$section[$index]"
            validateStableId(block.id, "$path.id", "Block", issues)

            val earlierPath = locationsById.putIfAbsent(block.id, path)
            if (earlierPath != null) {
                issues += ValidationIssue(
                    path = "$path.id",
                    code = "duplicate_block_id",
                    message = "Block id '${block.id}' is already used at $earlierPath.",
                )
            }

            if (!capabilityTypePattern.matches(block.type)) {
                issues += ValidationIssue(
                    path = "$path.type",
                    code = "invalid_capability_type",
                    message = "Capability type must use a dotted name such as 'android.notification.show'.",
                )
                return@forEachIndexed
            }

            if (registry != null) {
                val definition = registry.find(block.type)
                when {
                    definition == null -> issues += ValidationIssue(
                        path = "$path.type",
                        code = "unsupported_capability",
                        message = "This app version does not support '${block.type}'.",
                    )

                    definition.lane != expectedLane -> issues += ValidationIssue(
                        path = "$path.type",
                        code = "wrong_lane",
                        message = "'${block.type}' belongs in ${definition.lane.name.lowercase()}, not $section.",
                    )

                    else -> issues += definition.validate(block, path)
                }
            }
        }
    }

    private fun validateStableId(
        value: String,
        path: String,
        label: String,
        issues: MutableList<ValidationIssue>,
    ) {
        if (!stableIdPattern.matches(value)) {
            issues += ValidationIssue(
                path = path,
                code = "invalid_id",
                message = "$label id must be 1-64 lowercase letters, numbers, or hyphens.",
            )
        }
    }
}

data class ValidationIssue(
    val path: String,
    val code: String,
    val message: String,
)
