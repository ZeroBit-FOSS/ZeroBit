/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

/**
 * One source of truth for a block's code shape, generated form, explanation,
 * permission discovery, and runtime instruction.
 */
interface CapabilityDefinition {
    val type: String
    val lane: CapabilityLane
    val displayName: String
    val description: String
    val fields: List<CapabilityField>

    fun validate(block: MacroBlock, path: String): List<ValidationIssue>

    fun explain(block: MacroBlock): String

    fun requiredPermissions(block: MacroBlock): Set<AndroidPermission>

    fun compile(block: MacroBlock): RuntimeStep
}

enum class CapabilityLane {
    TRIGGER,
    CONDITION,
    ACTION,
}

data class CapabilityField(
    val key: String,
    val label: String,
    val kind: CapabilityFieldKind,
    val required: Boolean,
    val help: String,
    val advanced: Boolean = false,
)

enum class CapabilityFieldKind {
    TEXT,
    MULTILINE_TEXT,
    NUMBER,
    BOOLEAN,
}

enum class AndroidPermission(
    val manifestName: String,
) {
    POST_NOTIFICATIONS("android.permission.POST_NOTIFICATIONS"),
    SEND_SMS("android.permission.SEND_SMS"),
    ACCESS_NETWORK_STATE("android.permission.ACCESS_NETWORK_STATE"),
}
