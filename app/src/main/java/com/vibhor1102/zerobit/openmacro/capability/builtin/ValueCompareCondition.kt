/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability.builtin

import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.capability.CapabilityDefinition
import com.vibhor1102.zerobit.openmacro.capability.CapabilityField
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.describeValueSource
import com.vibhor1102.zerobit.openmacro.capability.rejectUnknownConfig
import com.vibhor1102.zerobit.openmacro.capability.requireText
import com.vibhor1102.zerobit.openmacro.capability.requireValueSource
import com.vibhor1102.zerobit.openmacro.capability.text
import com.vibhor1102.zerobit.openmacro.capability.toRuntimeValueSource
import com.vibhor1102.zerobit.openmacro.capability.valueReferenceIssues
import com.vibhor1102.zerobit.openmacro.capability.valueSourceType
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeStep
import com.vibhor1102.zerobit.openmacro.runtime.ValueComparisonOperator
import com.vibhor1102.zerobit.openmacro.validation.ValidationIssue

object ValueCompareCondition : CapabilityDefinition {
    override val type = "openmacro.value.compare"
    override val lane = CapabilityLane.CONDITION
    override val displayName = "Compare values"
    override val description =
        "Compares typed literal, variable, secret, or trigger-context values."
    override val fields = listOf(
        CapabilityField(
            key = "left",
            label = "Left value",
            kind = CapabilityFieldKind.VALUE,
            required = true,
            help = "A literal, local variable, secret, or trigger field.",
        ),
        CapabilityField(
            key = "operator",
            label = "Comparison",
            kind = CapabilityFieldKind.TEXT,
            required = true,
            help = "Equals, numeric comparison, text match, or presence check.",
        ),
        CapabilityField(
            key = "right",
            label = "Right value",
            kind = CapabilityFieldKind.VALUE,
            required = false,
            help = "Required except for presence checks.",
        ),
    )

    override fun validate(block: MacroBlock, path: String): List<ValidationIssue> =
        buildList {
            addAll(block.rejectUnknownConfig(setOf("left", "operator", "right"), path))
            addAll(block.requireValueSource("left", path))
            addAll(block.requireText("operator", path, maxLength = 32))
            val operator = block.operatorOrNull()
            if (operator == null && block.config["operator"] is MacroValue.Text) {
                add(
                    ValidationIssue(
                        "$path.config.operator",
                        "unknown_comparison_operator",
                        "Comparison operator is not supported.",
                    ),
                )
            }
            if (operator?.requiresRight == true) {
                addAll(block.requireValueSource("right", path))
            } else if (operator?.requiresRight == false && "right" in block.config) {
                add(
                    ValidationIssue(
                        "$path.config.right",
                        "unexpected_config",
                        "Presence comparisons do not use a right value.",
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
        val operator = block.operatorOrNull() ?: return emptyList()
        val left = block.config["left"] ?: return emptyList()
        val leftType = left.valueSourceType(document, registry)
            ?: return left.valueReferenceIssues("$path.config.left", document, registry)
        if (!operator.requiresRight) {
            return emptyList()
        }
        val right = block.config["right"] ?: return emptyList()
        val rightType = right.valueSourceType(document, registry)
            ?: return right.valueReferenceIssues("$path.config.right", document, registry)

        return when {
            operator in NUMERIC_OPERATORS &&
                (leftType != MacroVariableType.NUMBER || rightType != MacroVariableType.NUMBER) ->
                listOf(typeIssue(path, "Numeric comparisons require two number values."))

            operator in TEXT_OPERATORS &&
                (leftType != MacroVariableType.TEXT || rightType != MacroVariableType.TEXT) ->
                listOf(typeIssue(path, "Text comparisons require two text values."))

            operator in EQUALITY_OPERATORS && leftType != rightType ->
                listOf(typeIssue(path, "Equality comparisons require values of the same type."))

            else -> emptyList()
        }
    }

    override fun explain(block: MacroBlock): String {
        val operator = block.operatorOrNull()
        val left = block.describeValueSource("left")
        return if (operator != null && !operator.requiresRight) {
            "Continue if $left ${operator.description}."
        } else {
            "Continue if $left ${operator?.description ?: "uses an invalid comparison"} ${block.describeValueSource("right")}."
        }
    }

    override fun requiredPermissions(block: MacroBlock): Set<AndroidPermission> = emptySet()

    override fun compile(block: MacroBlock): RuntimeStep {
        val operator = checkNotNull(block.operatorOrNull())
        return RuntimeStep.CompareValues(
            blockId = block.id,
            left = block.config.getValue("left").toRuntimeValueSource(),
            operator = operator,
            right = block.config["right"]?.toRuntimeValueSource(),
        )
    }

    private fun typeIssue(path: String, message: String) = ValidationIssue(
        "$path.config",
        "comparison_type_mismatch",
        message,
    )

    private val EQUALITY_OPERATORS = setOf(
        ValueComparisonOperator.EQUALS,
        ValueComparisonOperator.NOT_EQUALS,
    )
    private val NUMERIC_OPERATORS = setOf(
        ValueComparisonOperator.GREATER_THAN,
        ValueComparisonOperator.GREATER_OR_EQUAL,
        ValueComparisonOperator.LESS_THAN,
        ValueComparisonOperator.LESS_OR_EQUAL,
    )
    private val TEXT_OPERATORS = setOf(
        ValueComparisonOperator.CONTAINS,
        ValueComparisonOperator.STARTS_WITH,
        ValueComparisonOperator.ENDS_WITH,
    )
}

private val ValueComparisonOperator.requiresRight: Boolean
    get() = this != ValueComparisonOperator.IS_PRESENT &&
        this != ValueComparisonOperator.IS_MISSING

private val ValueComparisonOperator.description: String
    get() = when (this) {
        ValueComparisonOperator.EQUALS -> "equals"
        ValueComparisonOperator.NOT_EQUALS -> "does not equal"
        ValueComparisonOperator.GREATER_THAN -> "is greater than"
        ValueComparisonOperator.GREATER_OR_EQUAL -> "is greater than or equal to"
        ValueComparisonOperator.LESS_THAN -> "is less than"
        ValueComparisonOperator.LESS_OR_EQUAL -> "is less than or equal to"
        ValueComparisonOperator.CONTAINS -> "contains"
        ValueComparisonOperator.STARTS_WITH -> "starts with"
        ValueComparisonOperator.ENDS_WITH -> "ends with"
        ValueComparisonOperator.IS_PRESENT -> "is present"
        ValueComparisonOperator.IS_MISSING -> "is missing"
    }

private fun MacroBlock.operatorOrNull(): ValueComparisonOperator? =
    when ((config["operator"] as? MacroValue.Text)?.value) {
        "equals" -> ValueComparisonOperator.EQUALS
        "not_equals" -> ValueComparisonOperator.NOT_EQUALS
        "greater_than" -> ValueComparisonOperator.GREATER_THAN
        "greater_or_equal" -> ValueComparisonOperator.GREATER_OR_EQUAL
        "less_than" -> ValueComparisonOperator.LESS_THAN
        "less_or_equal" -> ValueComparisonOperator.LESS_OR_EQUAL
        "contains" -> ValueComparisonOperator.CONTAINS
        "starts_with" -> ValueComparisonOperator.STARTS_WITH
        "ends_with" -> ValueComparisonOperator.ENDS_WITH
        "is_present" -> ValueComparisonOperator.IS_PRESENT
        "is_missing" -> ValueComparisonOperator.IS_MISSING
        else -> null
    }
