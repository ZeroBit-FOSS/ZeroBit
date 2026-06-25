/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.model

import java.math.BigDecimal

/**
 * The format-neutral meaning of one OpenMacro file.
 *
 * The visual editor and source editor must both read and write this model.
 * YAML is an adapter around it, not the runtime's source of truth.
 */
data class OpenMacroDocument(
    val format: String,
    val metadata: MacroMetadata,
    val variables: List<MacroVariable> = emptyList(),
    val triggers: List<MacroBlock>,
    val conditions: List<MacroBlock>,
    val actions: List<MacroBlock>,
)

data class MacroVariable(
    val name: String,
    val type: MacroVariableType,
    val initialValue: MacroValue? = null,
    val secretKey: String? = null,
)

enum class MacroVariableType {
    TEXT,
    NUMBER,
    BOOLEAN,
    SECRET,
}


data class MacroMetadata(
    val id: String,
    val name: String,
    val description: String? = null,
)

/**
 * A block remains generic at the file boundary so new capabilities can be
 * preserved and explained even when this app version cannot execute them.
 */
data class MacroBlock(
    val id: String,
    val type: String,
    val config: Map<String, MacroValue> = emptyMap(),
)

sealed interface MacroValue {
    data class Text(val value: String) : MacroValue

    data class Number(val value: BigDecimal) : MacroValue

    data class Boolean(val value: kotlin.Boolean) : MacroValue

    data class ListValue(val values: List<MacroValue>) : MacroValue

    data class ObjectValue(val values: Map<String, MacroValue>) : MacroValue

    data object Null : MacroValue
}
