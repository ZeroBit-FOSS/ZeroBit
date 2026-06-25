/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
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
    val triggerOutputs: List<TriggerOutput>
        get() = emptyList()

    fun triggerOutputs(block: MacroBlock): List<TriggerOutput> = triggerOutputs

    fun validate(block: MacroBlock, path: String): List<ValidationIssue>

    fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> = emptyList()

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
    val acceptsValueSources: Boolean = false,
    val allowedValues: List<String> = emptyList(),
)

data class TriggerOutput(
    val key: String,
    val type: MacroVariableType,
    val description: String,
)

enum class CapabilityFieldKind {
    TEXT,
    MULTILINE_TEXT,
    NUMBER,
    BOOLEAN,
    VALUE,
    TEXT_LIST,
}

enum class AndroidPermission(
    val manifestName: String,
) {
    POST_NOTIFICATIONS("android.permission.POST_NOTIFICATIONS"),
    SEND_SMS("android.permission.SEND_SMS"),
    ACCESS_NETWORK_STATE("android.permission.ACCESS_NETWORK_STATE"),
    NOTIFICATION_LISTENER_ACCESS("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"),
    SCHEDULE_EXACT_ALARM_ACCESS("android.permission.SCHEDULE_EXACT_ALARM"),
}
