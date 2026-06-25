/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.storage.SecretStore
import com.vibhor1102.zerobit.openmacro.storage.VariableStore

/**
 * A macro-scoped view of runtime values.
 *
 * Capabilities resolve declared names through this boundary. They never receive
 * the raw secret store or choose secret keys themselves.
 */
class RuntimeValues(
    private val macroId: String,
    declarations: List<MacroVariable>,
    private val variables: VariableStore,
    private val secrets: SecretStore,
) {
    private val declarationsByName = declarations.associateBy(MacroVariable::name)

    fun read(name: String): RuntimeValueResult {
        val declaration = declarationsByName[name]
            ?: return RuntimeValueResult.Unknown(name)
        val value = if (declaration.type == MacroVariableType.SECRET) {
            declaration.secretKey
                ?.let(secrets::getSecret)
                ?.let { MacroValue.Text(it) }
        } else {
            variables.getValue(macroId, name)
        }
        return value?.let { RuntimeValueResult.Available(it) }
            ?: RuntimeValueResult.Missing(name)
    }

    fun write(name: String, value: MacroValue): RuntimeValueWriteResult {
        val declaration = declarationsByName[name]
            ?: return RuntimeValueWriteResult.Unknown(name)
        if (declaration.type == MacroVariableType.SECRET) {
            return RuntimeValueWriteResult.ReadOnlySecret(name)
        }
        if (!declaration.accepts(value)) {
            return RuntimeValueWriteResult.TypeMismatch(
                name = name,
                expected = declaration.type,
                actual = value.typeName(),
            )
        }

        variables.setValue(macroId, name, value)
        return RuntimeValueWriteResult.Written
    }
}

sealed interface RuntimeValueResult {
    data class Available(val value: MacroValue) : RuntimeValueResult

    data class Missing(val name: String) : RuntimeValueResult

    data class Unknown(val name: String) : RuntimeValueResult
}

sealed interface RuntimeValueWriteResult {
    data object Written : RuntimeValueWriteResult

    data class Unknown(val name: String) : RuntimeValueWriteResult

    data class ReadOnlySecret(val name: String) : RuntimeValueWriteResult

    data class TypeMismatch(
        val name: String,
        val expected: MacroVariableType,
        val actual: String,
    ) : RuntimeValueWriteResult
}

private fun MacroVariable.accepts(value: MacroValue): Boolean = when (type) {
    MacroVariableType.TEXT -> value is MacroValue.Text
    MacroVariableType.NUMBER -> value is MacroValue.Number
    MacroVariableType.BOOLEAN -> value is MacroValue.Boolean
    MacroVariableType.SECRET -> false
}

private fun MacroValue.typeName(): String = when (this) {
    is MacroValue.Text -> "text"
    is MacroValue.Number -> "number"
    is MacroValue.Boolean -> "boolean"
    is MacroValue.ListValue -> "list"
    is MacroValue.ObjectValue -> "object"
    MacroValue.Null -> "null"
}

fun executeVariableAction(
    action: RuntimeStep,
    context: RuntimeContext,
): ActionResult? = when (action) {
    is RuntimeStep.SetVariable -> when (val resolved = action.value.resolve(context)) {
        is RuntimeValueSourceResult.Available ->
            context.values.write(action.name, resolved.value).toActionResult()
        is RuntimeValueSourceResult.Missing ->
            ActionResult.Failed(resolved.message)
    }

    is RuntimeStep.IncrementVariable -> when (val current = context.values.read(action.name)) {
        is RuntimeValueResult.Available -> {
            val number = current.value as? MacroValue.Number
            if (number == null) {
                ActionResult.Failed(
                    "Variable '${action.name}' no longer contains a number.",
                )
            } else {
                context.values.write(
                    action.name,
                    MacroValue.Number(number.value + action.amount),
                ).toActionResult()
            }
        }
        is RuntimeValueResult.Missing ->
            ActionResult.Failed("Variable '${action.name}' has no value to increment.")
        is RuntimeValueResult.Unknown ->
            ActionResult.Failed("Variable '${action.name}' is not declared.")
    }

    is RuntimeStep.ToggleVariable -> when (val current = context.values.read(action.name)) {
        is RuntimeValueResult.Available -> {
            val boolean = current.value as? MacroValue.Boolean
            if (boolean == null) {
                ActionResult.Failed(
                    "Variable '${action.name}' no longer contains a boolean.",
                )
            } else {
                context.values.write(
                    action.name,
                    MacroValue.Boolean(!boolean.value),
                ).toActionResult()
            }
        }
        is RuntimeValueResult.Missing ->
            ActionResult.Failed("Variable '${action.name}' has no value to toggle.")
        is RuntimeValueResult.Unknown ->
            ActionResult.Failed("Variable '${action.name}' is not declared.")
    }

    else -> null
}

sealed interface RuntimeValueSourceResult {
    data class Available(val value: MacroValue) : RuntimeValueSourceResult

    data class Missing(val message: String) : RuntimeValueSourceResult
}

fun RuntimeValueSource.resolve(context: RuntimeContext): RuntimeValueSourceResult = when (this) {
    is RuntimeValueSource.Literal -> RuntimeValueSourceResult.Available(value)
    is RuntimeValueSource.Variable -> when (val result = context.values.read(name)) {
        is RuntimeValueResult.Available -> RuntimeValueSourceResult.Available(result.value)
        is RuntimeValueResult.Missing ->
            RuntimeValueSourceResult.Missing("Variable '$name' has no value.")
        is RuntimeValueResult.Unknown ->
            RuntimeValueSourceResult.Missing("Variable '$name' is not declared.")
    }
    is RuntimeValueSource.Trigger -> context.trigger.values[key]?.let {
        RuntimeValueSourceResult.Available(it)
    } ?: RuntimeValueSourceResult.Missing(
        "Trigger '${context.triggerBlockId}' did not provide field '$key'.",
    )
}

fun evaluateValueCondition(
    condition: RuntimeStep,
    context: RuntimeContext,
): ConditionResult? {
    if (condition !is RuntimeStep.CompareValues) {
        return null
    }
    val left = condition.left.resolve(context)
    if (condition.operator == ValueComparisonOperator.IS_PRESENT) {
        return if (left is RuntimeValueSourceResult.Available) {
            ConditionResult.Passed
        } else {
            ConditionResult.Blocked("The compared value is missing.")
        }
    }
    if (condition.operator == ValueComparisonOperator.IS_MISSING) {
        return if (left is RuntimeValueSourceResult.Missing) {
            ConditionResult.Passed
        } else {
            ConditionResult.Blocked("The compared value is present.")
        }
    }
    if (left is RuntimeValueSourceResult.Missing) {
        return ConditionResult.Blocked(left.message)
    }
    val rightSource = condition.right
        ?: return ConditionResult.Failed("The comparison has no right value.")
    val right = rightSource.resolve(context)
    if (right is RuntimeValueSourceResult.Missing) {
        return ConditionResult.Blocked(right.message)
    }

    val leftValue = (left as RuntimeValueSourceResult.Available).value
    val rightValue = (right as RuntimeValueSourceResult.Available).value
    val matched = when (condition.operator) {
        ValueComparisonOperator.EQUALS -> valuesEqual(leftValue, rightValue)
        ValueComparisonOperator.NOT_EQUALS -> valuesEqual(leftValue, rightValue)?.not()
        ValueComparisonOperator.GREATER_THAN ->
            compareNumbers(leftValue, rightValue) { comparison -> comparison > 0 }
        ValueComparisonOperator.GREATER_OR_EQUAL ->
            compareNumbers(leftValue, rightValue) { comparison -> comparison >= 0 }
        ValueComparisonOperator.LESS_THAN ->
            compareNumbers(leftValue, rightValue) { comparison -> comparison < 0 }
        ValueComparisonOperator.LESS_OR_EQUAL ->
            compareNumbers(leftValue, rightValue) { comparison -> comparison <= 0 }
        ValueComparisonOperator.CONTAINS ->
            compareText(leftValue, rightValue) { other -> contains(other) }
        ValueComparisonOperator.STARTS_WITH ->
            compareText(leftValue, rightValue) { other -> startsWith(other) }
        ValueComparisonOperator.ENDS_WITH ->
            compareText(leftValue, rightValue) { other -> endsWith(other) }
        ValueComparisonOperator.IS_PRESENT,
        ValueComparisonOperator.IS_MISSING ->
            error("Presence operators are handled before binary comparison.")
    }
    return if (matched == null) {
        ConditionResult.Failed("Compared runtime values do not match the approved types.")
    } else if (matched) {
        ConditionResult.Passed
    } else {
        ConditionResult.Blocked("The value comparison did not match.")
    }
}

private fun valuesEqual(left: MacroValue, right: MacroValue): Boolean? = when {
    left is MacroValue.Number && right is MacroValue.Number ->
        left.value.compareTo(right.value) == 0
    left is MacroValue.Text && right is MacroValue.Text -> left.value == right.value
    left is MacroValue.Boolean && right is MacroValue.Boolean -> left.value == right.value
    else -> null
}

private fun compareNumbers(
    left: MacroValue,
    right: MacroValue,
    predicate: (Int) -> Boolean,
): Boolean? {
    val leftNumber = (left as? MacroValue.Number)?.value ?: return null
    val rightNumber = (right as? MacroValue.Number)?.value ?: return null
    return predicate(leftNumber.compareTo(rightNumber))
}

private fun compareText(
    left: MacroValue,
    right: MacroValue,
    predicate: String.(String) -> Boolean,
): Boolean? {
    val leftText = (left as? MacroValue.Text)?.value ?: return null
    val rightText = (right as? MacroValue.Text)?.value ?: return null
    return leftText.predicate(rightText)
}

private fun RuntimeValueWriteResult.toActionResult(): ActionResult = when (this) {
    RuntimeValueWriteResult.Written -> ActionResult.Succeeded
    is RuntimeValueWriteResult.Unknown ->
        ActionResult.Failed("Variable '$name' is not declared.")
    is RuntimeValueWriteResult.ReadOnlySecret ->
        ActionResult.Failed("Secret variable '$name' is read-only at runtime.")
    is RuntimeValueWriteResult.TypeMismatch ->
        ActionResult.Failed(
            "Variable '$name' requires ${expected.name.lowercase()}, but received $actual.",
        )
}
