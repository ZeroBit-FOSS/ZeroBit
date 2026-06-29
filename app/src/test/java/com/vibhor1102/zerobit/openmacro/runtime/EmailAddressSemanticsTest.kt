/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailAddressSemanticsTest {
    @Test
    fun acceptsPracticalSingleAddresses() {
        listOf(
            "person@example.com",
            "first.last+automation@sub.example.co.uk",
            "alerts@example",
        ).forEach { assertTrue(it, isValidEmailAddress(it)) }
    }

    @Test
    fun rejectsNamesUrisWhitespaceAndMalformedAddresses() {
        listOf(
            "Person <person@example.com>",
            "mailto:person@example.com",
            " person@example.com",
            "person@@example.com",
            ".person@example.com",
            "person..name@example.com",
            "person@-example.com",
            "person@example..com",
        ).forEach { assertFalse(it, isValidEmailAddress(it)) }
    }
}
