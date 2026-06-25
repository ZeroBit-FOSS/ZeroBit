/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.capability

import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroValue
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

internal fun MacroBlock.text(key: String): String =
    (config.getValue(key) as MacroValue.Text).value
