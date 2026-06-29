/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.model

enum class ConditionGroupLogic(
    val label: String,
    val sourceKey: String,
) {
    AND("AND", "all"),
    OR("OR", "any"),
}
