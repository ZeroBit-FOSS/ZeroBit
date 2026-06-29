/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

const val MAX_DIAL_NUMBER_LENGTH = 40

fun isDialablePhoneNumber(value: String): Boolean =
    value.length in 1..MAX_DIAL_NUMBER_LENGTH &&
        value.any(Char::isDigit) &&
        DIAL_NUMBER_PATTERN.matches(value)

private val DIAL_NUMBER_PATTERN = Regex("""\+?[0-9*#().,; -]+""")
