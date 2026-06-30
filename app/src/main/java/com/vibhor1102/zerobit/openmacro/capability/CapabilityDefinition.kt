/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
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
    val creation: CapabilityCreation?
        get() = null
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

    fun explain(
        block: MacroBlock,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): String = explain(block)

    fun requiredPermissions(block: MacroBlock): Set<AndroidPermission>

    fun requiredPermissions(
        block: MacroBlock,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): Set<AndroidPermission> = requiredPermissions(block)

    fun compile(block: MacroBlock): RuntimeStep

    fun compile(
        block: MacroBlock,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): RuntimeStep = compile(block)
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

data class CapabilityCreation(
    val idBase: String,
    val defaultConfig: Map<String, MacroValue> = emptyMap(),
    val contextConfig: ((CapabilityCreationContext) -> Map<String, MacroValue>?)? = null,
    val setup: CapabilitySetup? = null,
) {
    fun configFor(context: CapabilityCreationContext): Map<String, MacroValue>? =
        if (contextConfig == null) defaultConfig else contextConfig.invoke(context)
}

data class CapabilitySetup(
    val fieldKeys: List<String>,
    val initialConfig: Map<String, MacroValue> = emptyMap(),
)

data class CapabilityCreationContext(
    val document: OpenMacroDocument,
    val insertion: CapabilityInsertion,
)

sealed interface CapabilityInsertion {
    object TopLevel : CapabilityInsertion

    data class ActionGroup(
        val parentBlockId: String,
        val parentDepth: Int,
    ) : CapabilityInsertion

    data class ConditionGroup(val path: String) : CapabilityInsertion
}

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
    BLUETOOTH_CONNECT("android.permission.BLUETOOTH_CONNECT"),
    CAMERA("android.permission.CAMERA"),
    POST_NOTIFICATIONS("android.permission.POST_NOTIFICATIONS"),
    SEND_SMS("android.permission.SEND_SMS"),
    ACCESS_NETWORK_STATE("android.permission.ACCESS_NETWORK_STATE"),
    NOTIFICATION_LISTENER_ACCESS("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"),
    SCHEDULE_EXACT_ALARM_ACCESS("android.permission.SCHEDULE_EXACT_ALARM"),
}
