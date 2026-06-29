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
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.capability.requireText
import com.vibhor1102.zerobit.openmacro.capability.text
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue
import java.math.BigDecimal

object SetVariableAction : CapabilityDefinition {
    override val type = "openmacro.variable.set"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Set variable"
    override val description = "Stores a new value in a declared local variable."
    override val creation = CapabilityCreation(
        idBase = "set-variable",
        contextConfig = { context ->
            context.document.variables.firstNotNullOfOrNull { variable ->
                variable.defaultLiteral()?.let { value ->
                    mapOf(
                        "name" to MacroValue.Text(variable.name),
                        "value" to value,
                    )
                }
            }
        },
    )
    override val fields = listOf(
        variableNameField(),
        CapabilityField(
            key = "value",
            label = "Value",
            kind = CapabilityFieldKind.VALUE,
            required = true,
            help = "The text, number, or boolean value to store.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("name", "value"), path))
            addAll(block.requireText("name", path, maxLength = 64))
            val value = block.config["value"]
            if (value == null) {
                add(missingValue(path))
            } else if (!value.isValueSource()) {
                add(
                    ValidationIssue(
                        "$path.config.value",
                        "wrong_config_type",
                        "Use a text, number, boolean, variable reference, or trigger-field reference.",
                    ),
                )
            }
        }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> {
        val declaration = document.variable(block.config["name"]) ?: return unknownVariable(block, path)
        if (declaration.type == MacroVariableType.SECRET) {
            return listOf(readOnlySecret(path, declaration.name))
        }
        val value = block.config["value"] ?: return emptyList()
        val sourceType = value.sourceType(document, registry)
            ?: return value.referenceIssue(document, registry, path)
        return if (declaration.type != sourceType) {
            listOf(
                ValidationIssue(
                    "$path.config.value",
                    "variable_type_mismatch",
                    "Variable '${declaration.name}' requires ${declaration.type.name.lowercase()} values.",
                ),
            )
        } else {
            emptyList()
        }
    }

    override fun explain(block: MacroBlock): String =
        "Set local variable “${block.text("name")}” to ${block.config["value"].describe()}."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.SetVariable(
            blockId = block.id,
            name = block.text("name"),
            value = block.config.getValue("value").toRuntimeValueSource(),
        )
}

object IncrementVariableAction : CapabilityDefinition {
    override val type = "openmacro.variable.increment"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Increment variable"
    override val description = "Adds an amount to a declared number variable."
    override val creation = CapabilityCreation(
        idBase = "increment-variable",
        contextConfig = { context ->
            context.document.variables.firstOrNull { it.type == MacroVariableType.NUMBER }?.let {
                mapOf(
                    "name" to MacroValue.Text(it.name),
                    "amount" to MacroValue.Number(BigDecimal.ONE),
                )
            }
        },
    )
    override val fields = listOf(
        variableNameField(),
        CapabilityField(
            key = "amount",
            label = "Amount",
            kind = CapabilityFieldKind.NUMBER,
            required = true,
            help = "The number to add. Use a negative number to subtract.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("name", "amount"), path))
            addAll(block.requireText("name", path, maxLength = 64))
            when (block.config["amount"]) {
                null -> add(
                    ValidationIssue(
                        "$path.config.amount",
                        "missing_config",
                        "Configuration 'amount' is required.",
                    ),
                )
                !is MacroValue.Number -> add(
                    ValidationIssue(
                        "$path.config.amount",
                        "wrong_config_type",
                        "Configuration 'amount' must be a number.",
                    ),
                )
                else -> Unit
            }
        }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> =
        document.requireVariableType(block, path, MacroVariableType.NUMBER)

    override fun explain(block: MacroBlock): String =
        "Add ${(block.config["amount"] as? MacroValue.Number)?.value ?: BigDecimal.ZERO} to local variable “${block.text("name")}”."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.IncrementVariable(
            blockId = block.id,
            name = block.text("name"),
            amount = (block.config.getValue("amount") as MacroValue.Number).value,
        )
}

object ToggleVariableAction : CapabilityDefinition {
    override val type = "openmacro.variable.toggle"
    override val lane = CapabilityLane.ACTION
    override val displayName = "Toggle variable"
    override val description = "Flips a declared boolean variable."
    override val creation = CapabilityCreation(
        idBase = "toggle-variable",
        contextConfig = { context ->
            context.document.variables.firstOrNull { it.type == MacroVariableType.BOOLEAN }?.let {
                mapOf("name" to MacroValue.Text(it.name))
            }
        },
    )
    override val fields = listOf(variableNameField())

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("name"), path))
            addAll(block.requireText("name", path, maxLength = 64))
        }

    override fun validateDocument(
        block: MacroBlock,
        path: String,
        document: OpenMacroDocument,
        registry: CapabilityRegistry,
    ): List<ValidationIssue> =
        document.requireVariableType(block, path, MacroVariableType.BOOLEAN)

    override fun explain(block: MacroBlock): String =
        "Toggle local variable “${block.text("name")}”."

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep =
        RuntimeStep.ToggleVariable(
            blockId = block.id,
            name = block.text("name"),
        )
}

private fun variableNameField() = CapabilityField(
    key = "name",
    label = "Variable",
    kind = CapabilityFieldKind.TEXT,
    required = true,
    help = "The name of a variable declared by this macro.",
)

private fun MacroVariable.defaultLiteral(): MacroValue? =
    when (type) {
        MacroVariableType.TEXT -> MacroValue.Text("")
        MacroVariableType.NUMBER -> MacroValue.Number(BigDecimal.ZERO)
        MacroVariableType.BOOLEAN -> MacroValue.Boolean(false)
        MacroVariableType.SECRET -> null
    }

private fun missingValue(path: String) = ValidationIssue(
    "$path.config.value",
    "missing_config",
    "Configuration 'value' is required.",
)

private fun OpenMacroDocument.variable(value: MacroValue?) =
    (value as? MacroValue.Text)?.value?.let { name ->
        variables.find { it.name == name }
    }

private fun OpenMacroDocument.requireVariableType(
    block: MacroBlock,
    path: String,
    expected: MacroVariableType,
): List<ValidationIssue> {
    val declaration = variable(block.config["name"]) ?: return unknownVariable(block, path)
    return if (declaration.type == expected) {
        emptyList()
    } else {
        listOf(
            ValidationIssue(
                "$path.config.name",
                "variable_type_mismatch",
                "Variable '${declaration.name}' must be ${expected.name.lowercase()} for this action.",
            ),
        )
    }
}

private fun unknownVariable(block: MacroBlock, path: String): List<ValidationIssue> {
    val name = (block.config["name"] as? MacroValue.Text)?.value ?: return emptyList()
    if (name.isBlank()) {
        return emptyList()
    }
    return listOf(
        ValidationIssue(
            "$path.config.name",
            "unknown_variable",
            "Variable '$name' is not declared by this macro.",
        ),
    )
}

private fun readOnlySecret(path: String, name: String) = ValidationIssue(
    "$path.config.name",
    "secret_is_read_only",
    "Secret variable '$name' can be changed only through the local secret store.",
)

private fun MacroValue.isVariablePrimitive(): Boolean =
    this is MacroValue.Text || this is MacroValue.Number || this is MacroValue.Boolean

private fun MacroValue.isValueSource(): Boolean =
    isVariablePrimitive() || reference()?.let { it.first in setOf("variable", "trigger") } == true

private fun MacroValue.sourceType(
    document: OpenMacroDocument,
    registry: CapabilityRegistry,
): MacroVariableType? = when (this) {
    is MacroValue.Text -> MacroVariableType.TEXT
    is MacroValue.Number -> MacroVariableType.NUMBER
    is MacroValue.Boolean -> MacroVariableType.BOOLEAN
    else -> reference()?.let { (kind, name) ->
        when (kind) {
            "variable" -> document.variables.find { it.name == name }?.type?.let {
                if (it == MacroVariableType.SECRET) MacroVariableType.TEXT else it
            }
            "trigger" -> document.triggerOutputTypes(registry)[name]
            else -> null
        }
    }
}

private fun MacroValue.referenceIssue(
    document: OpenMacroDocument,
    registry: CapabilityRegistry,
    path: String,
): List<ValidationIssue> {
    val reference = reference()
        ?: return listOf(
            ValidationIssue(
                "$path.config.value",
                "invalid_value_reference",
                "Use a literal value or an object containing exactly one 'variable' or 'trigger' reference.",
            ),
        )
    val (kind, name) = reference
    return when (kind) {
        "variable" -> if (document.variables.none { it.name == name }) {
            listOf(
                ValidationIssue(
                    "$path.config.value.variable",
                    "unknown_variable",
                    "Variable '$name' is not declared by this macro.",
                ),
            )
        } else {
            emptyList()
        }
        "trigger" -> if (name !in document.triggerOutputTypes(registry)) {
            listOf(
                ValidationIssue(
                    "$path.config.value.trigger",
                    "unknown_trigger_field",
                    "Trigger field '$name' is not produced by this macro's triggers.",
                ),
            )
        } else {
            emptyList()
        }
        else -> listOf(
            ValidationIssue(
                "$path.config.value",
                "invalid_value_reference",
                "Value references must use 'variable' or 'trigger'.",
            ),
        )
    }
}

private fun MacroValue.reference(): Pair<String, String>? {
    val values = (this as? MacroValue.ObjectValue)?.values ?: return null
    if (values.size != 1) {
        return null
    }
    val (kind, value) = values.entries.single()
    val name = (value as? MacroValue.Text)?.value ?: return null
    return kind to name
}

private fun MacroValue.toRuntimeValueSource(): RuntimeValueSource =
    reference()?.let { (kind, name) ->
        when (kind) {
            "variable" -> RuntimeValueSource.Variable(name)
            "trigger" -> RuntimeValueSource.Trigger(name)
            else -> error("Unsupported value source '$kind'.")
        }
    } ?: RuntimeValueSource.Literal(this)

private fun OpenMacroDocument.triggerOutputTypes(
    registry: CapabilityRegistry,
): Map<String, MacroVariableType> = buildMap {
    triggers.forEach { trigger ->
        registry.find(trigger.type)?.triggerOutputs(trigger)?.forEach { output ->
            put(output.key, output.type)
        }
    }
}

private fun MacroValue?.describe(): String = when (this) {
    is MacroValue.Text -> "“$value”"
    is MacroValue.Number -> value.toPlainString()
    is MacroValue.Boolean -> value.toString()
    is MacroValue.ObjectValue -> reference()?.let { (kind, name) ->
        "$kind value “$name”"
    } ?: "an invalid value reference"
    else -> "an invalid value"
}
