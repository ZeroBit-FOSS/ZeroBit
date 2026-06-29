/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

const val MAX_MAP_QUERY_LENGTH = 500

fun isValidMapQuery(value: String): Boolean =
    value.isNotBlank() &&
        value.length <= MAX_MAP_QUERY_LENGTH &&
        value.none { it.isISOControl() }
