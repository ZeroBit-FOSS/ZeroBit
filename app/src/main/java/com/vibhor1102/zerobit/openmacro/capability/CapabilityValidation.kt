/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeValueSource
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

internal fun MacroBlock.rejectUnknownConfig(
    allowedKeys: Set<String>,
    path: String,
): List<ValidationIssue> = config.keys
    .filterNot(allowedKeys::contains)
    .sorted()
    .map { key ->
        ValidationIssue(
            path = "$path.config.$key",
            code = "unknown_config",
            message = "Configuration '$key' is not supported by '$type'.",
        )
    }

internal fun MacroBlock.requireText(
    key: String,
    path: String,
    maxLength: Int,
): List<ValidationIssue> {
    val value = config[key]
    return when {
        value == null -> listOf(
            ValidationIssue(
                path = "$path.config.$key",
                code = "missing_config",
                message = "Configuration '$key' is required.",
            ),
        )

        value !is MacroValue.Text -> listOf(
            ValidationIssue(
                path = "$path.config.$key",
                code = "wrong_config_type",
                message = "Configuration '$key' must be text.",
            ),
        )

        value.value.isBlank() -> listOf(
            ValidationIssue(
                path = "$path.config.$key",
                code = "blank_config",
                message = "Configuration '$key' must not be blank.",
            ),
        )

        value.value.length > maxLength -> listOf(
            ValidationIssue(
                path = "$path.config.$key",
                code = "config_too_long",
                message = "Configuration '$key' must be $maxLength characters or fewer.",
            ),
        )

        else -> emptyList()
    }
}

internal fun MacroBlock.requireAndroidPackageName(
    key: String,
    path: String,
): List<ValidationIssue> =
    buildList {
        addAll(requireText(key, path, MAX_ANDROID_PACKAGE_LENGTH))
        val packageName = runCatching { text(key) }.getOrNull()
        if (packageName != null && !ANDROID_PACKAGE_PATTERN.matches(packageName)) {
            add(
                ValidationIssue(
                    "$path.config.$key",
                    "invalid_package_name",
                    "Use an exact Android package name such as com.example.app.",
                ),
            )
        }
    }

internal fun MacroBlock.optionalAndroidPackageName(
    key: String,
    path: String,
): List<ValidationIssue> =
    if (config[key] == null) emptyList() else requireAndroidPackageName(key, path)

internal fun MacroBlock.text(key: String): String =
    (config.getValue(key) as MacroValue.Text).value

internal fun MacroBlock.requireTextSource(
    key: String,
    path: String,
    maxLiteralLength: Int,
): List<ValidationIssue> {
    val value = config[key]
    return when {
        value == null -> listOf(
            ValidationIssue(
                path = "$path.config.$key",
                code = "missing_config",
                message = "Configuration '$key' is required.",
            ),
        )
        value is MacroValue.Text && value.value.isBlank() -> listOf(
            ValidationIssue(
                path = "$path.config.$key",
                code = "blank_config",
                message = "Configuration '$key' must not be blank.",
            ),
        )
        value is MacroValue.Text && value.value.length > maxLiteralLength -> listOf(
            ValidationIssue(
                path = "$path.config.$key",
                code = "config_too_long",
                message = "Configuration '$key' must be $maxLiteralLength characters or fewer.",
            ),
        )
        value is MacroValue.Text -> emptyList()
        value.valueReference() == null -> listOf(
            ValidationIssue(
                path = "$path.config.$key",
                code = "invalid_value_reference",
                message = "Use text or an object containing exactly one 'variable' or 'trigger' reference.",
            ),
        )
        else -> emptyList()
    }
}

internal fun MacroBlock.requireValueSource(
    key: String,
    path: String,
): List<ValidationIssue> {
    val value = config[key] ?: return listOf(
        ValidationIssue(
            path = "$path.config.$key",
            code = "missing_config",
            message = "Configuration '$key' is required.",
        ),
    )
    return if (
        value is MacroValue.Text ||
        value is MacroValue.Number ||
        value is MacroValue.Boolean ||
        value.valueReference() != null
    ) {
        emptyList()
    } else {
        listOf(
            ValidationIssue(
                path = "$path.config.$key",
                code = "invalid_value_reference",
                message = "Use a text, number, boolean, variable reference, or trigger-field reference.",
            ),
        )
    }
}

internal fun MacroBlock.validateTextSource(
    key: String,
    path: String,
    document: OpenMacroDocument,
    registry: CapabilityRegistry,
): List<ValidationIssue> {
    val value = config[key] ?: return emptyList()
    if (value is MacroValue.Text) {
        return emptyList()
    }
    val sourceType = value.valueSourceType(document, registry)
        ?: return value.valueReferenceIssues("$path.config.$key", document, registry)
    return if (sourceType == MacroVariableType.TEXT) {
        emptyList()
    } else {
        listOf(
            ValidationIssue(
                path = "$path.config.$key",
                code = "value_reference_type_mismatch",
                message = "Configuration '$key' requires a text value.",
            ),
        )
    }
}

internal fun MacroBlock.valueSource(key: String): RuntimeValueSource =
    config.getValue(key).toRuntimeValueSource()

internal fun MacroBlock.describeValueSource(key: String): String =
    config[key]?.describeValueSource() ?: "a missing value"

internal fun MacroValue.valueReference(): Pair<String, String>? {
    val values = (this as? MacroValue.ObjectValue)?.values ?: return null
    if (values.size != 1) {
        return null
    }
    val (kind, value) = values.entries.single()
    val name = (value as? MacroValue.Text)?.value ?: return null
    if (name.isBlank()) {
        return null
    }
    return kind to name
}

internal fun MacroValue.valueSourceType(
    document: OpenMacroDocument,
    registry: CapabilityRegistry,
): MacroVariableType? = when (this) {
    is MacroValue.Text -> MacroVariableType.TEXT
    is MacroValue.Number -> MacroVariableType.NUMBER
    is MacroValue.Boolean -> MacroVariableType.BOOLEAN
    else -> valueReference()?.let { (kind, name) ->
        when (kind) {
            "variable" -> document.variables.find { it.name == name }?.type?.let {
                if (it == MacroVariableType.SECRET) MacroVariableType.TEXT else it
            }
            "trigger" -> document.triggerOutputTypes(registry)[name]
            else -> null
        }
    }
}

internal fun MacroValue.valueReferenceIssues(
    path: String,
    document: OpenMacroDocument,
    registry: CapabilityRegistry,
): List<ValidationIssue> {
    val reference = valueReference()
        ?: return listOf(
            ValidationIssue(
                path = path,
                code = "invalid_value_reference",
                message = "Use a literal value or an object containing exactly one 'variable' or 'trigger' reference.",
            ),
        )
    val (kind, name) = reference
    return when (kind) {
        "variable" -> if (document.variables.none { it.name == name }) {
            listOf(
                ValidationIssue(
                    path = "$path.variable",
                    code = "unknown_variable",
                    message = "Variable '$name' is not declared by this macro.",
                ),
            )
        } else {
            emptyList()
        }
        "trigger" -> if (name !in document.triggerOutputTypes(registry)) {
            listOf(
                ValidationIssue(
                    path = "$path.trigger",
                    code = "unknown_trigger_field",
                    message = "Trigger field '$name' is not produced by this macro's triggers.",
                ),
            )
        } else {
            emptyList()
        }
        else -> listOf(
            ValidationIssue(
                path = path,
                code = "invalid_value_reference",
                message = "Value references must use 'variable' or 'trigger'.",
            ),
        )
    }
}

internal fun MacroValue.toRuntimeValueSource(): RuntimeValueSource =
    valueReference()?.let { (kind, name) ->
        when (kind) {
            "variable" -> RuntimeValueSource.Variable(name)
            "trigger" -> RuntimeValueSource.Trigger(name)
            else -> error("Unsupported value source '$kind'.")
        }
    } ?: RuntimeValueSource.Literal(this)

internal fun MacroValue.describeValueSource(): String = when (this) {
    is MacroValue.Text -> "“$value”"
    is MacroValue.Number -> value.toPlainString()
    is MacroValue.Boolean -> value.toString()
    else -> valueReference()?.let { (kind, name) ->
        when (kind) {
            "variable" -> "local variable “$name”"
            "trigger" -> "trigger field “$name”"
            else -> "an invalid value reference"
        }
    } ?: "an invalid value"
}

internal fun OpenMacroDocument.triggerOutputTypes(
    registry: CapabilityRegistry,
): Map<String, MacroVariableType> = buildMap {
    triggers.forEach { trigger ->
        registry.find(trigger.type)?.triggerOutputs(trigger)?.forEach { output ->
            put(output.key, output.type)
        }
    }
}

private const val MAX_ANDROID_PACKAGE_LENGTH = 255
private val ANDROID_PACKAGE_PATTERN =
    Regex("""[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+""")
