/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument

data class CapabilityFormModel(
    val blockId: String,
    val capabilityType: String,
    val title: String,
    val description: String,
    val fields: List<CapabilityFormField>,
)

data class CapabilityFormField(
    val key: String,
    val label: String,
    val kind: CapabilityFieldKind,
    val required: Boolean,
    val help: String,
    val advanced: Boolean,
    val acceptsValueSources: Boolean,
    val allowedValues: List<String>,
    val currentValue: MacroValue?,
    val referenceOptions: List<ValueReferenceOption>,
)

data class ValueReferenceOption(
    val label: String,
    val type: MacroVariableType,
    val value: MacroValue.ObjectValue,
)

fun filterValueReferenceOptions(
    options: List<ValueReferenceOption>,
    query: String,
): List<ValueReferenceOption> {
    val search = query.trim()
    if (search.isEmpty()) return options
    return options.filter { option ->
        option.label.contains(search, ignoreCase = true) ||
            option.type.name.contains(search, ignoreCase = true)
    }
}

class CapabilityFormModelFactory(
    private val registry: CapabilityRegistry,
) {
    fun create(
        document: OpenMacroDocument,
        block: MacroBlock,
    ): CapabilityFormModel? {
        val definition = registry.find(block.type) ?: return null
        val references = referenceOptions(document)
        return CapabilityFormModel(
            blockId = block.id,
            capabilityType = block.type,
            title = definition.displayName,
            description = definition.description,
            fields = definition.fields.map { field ->
                CapabilityFormField(
                    key = field.key,
                    label = field.label,
                    kind = field.kind,
                    required = field.required,
                    help = field.help,
                    advanced = field.advanced,
                    acceptsValueSources = field.acceptsValueSources ||
                        field.kind == CapabilityFieldKind.VALUE,
                    allowedValues = field.allowedValues,
                    currentValue = block.config[field.key],
                    referenceOptions = if (
                        field.kind == CapabilityFieldKind.VALUE ||
                        field.acceptsValueSources
                    ) {
                        if (field.kind == CapabilityFieldKind.TEXT ||
                            field.kind == CapabilityFieldKind.MULTILINE_TEXT
                        ) {
                            references.filter { it.type == MacroVariableType.TEXT }
                        } else {
                            references
                        }
                    } else {
                        emptyList()
                    },
                )
            },
        )
    }

    private fun referenceOptions(
        document: OpenMacroDocument,
    ): List<ValueReferenceOption> = buildList {
        document.variables.forEach { variable ->
            add(
                ValueReferenceOption(
                    label = if (variable.type == MacroVariableType.SECRET) {
                        "Secret: ${variable.name}"
                    } else {
                        "Variable: ${variable.name}"
                    },
                    type = if (variable.type == MacroVariableType.SECRET) {
                        MacroVariableType.TEXT
                    } else {
                        variable.type
                    },
                    value = reference("variable", variable.name),
                ),
            )
        }
        document.triggers.forEach { trigger ->
            registry.find(trigger.type)?.triggerOutputs(trigger)?.forEach { output ->
                add(
                    ValueReferenceOption(
                        label = "Trigger: ${output.key}",
                        type = output.type,
                        value = reference("trigger", output.key),
                    ),
                )
            }
        }
    }.distinctBy { it.value }
        .sortedBy(ValueReferenceOption::label)

    private fun reference(
        kind: String,
        name: String,
    ) = MacroValue.ObjectValue(
        mapOf(kind to MacroValue.Text(name)),
    )
}
