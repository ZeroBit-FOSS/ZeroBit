/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

const val MAX_EMAIL_ADDRESS_LENGTH = 254
const val MAX_EMAIL_SUBJECT_LENGTH = 998
const val MAX_EMAIL_BODY_LENGTH = 20_000

fun isValidEmailAddress(value: String): Boolean {
    if (value.length !in 3..MAX_EMAIL_ADDRESS_LENGTH || value != value.trim()) return false
    if (value.any { it.isWhitespace() || it.isISOControl() }) return false

    val at = value.indexOf('@')
    if (at <= 0 || at != value.lastIndexOf('@')) return false
    val local = value.substring(0, at)
    val domain = value.substring(at + 1)
    if (local.length > 64 || !EMAIL_LOCAL_PATTERN.matches(local)) return false
    if (local.startsWith('.') || local.endsWith('.') || ".." in local) return false
    if (domain.length !in 1..253 || domain.startsWith('.') || domain.endsWith('.')) return false

    return domain.split('.').all { label ->
        label.length in 1..63 &&
            label.first().isLetterOrDigit() &&
            label.last().isLetterOrDigit() &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}

private val EMAIL_LOCAL_PATTERN = Regex("[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]+")
