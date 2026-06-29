/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberSemanticsTest {
    @Test
    fun acceptsCommonInternationalAndServiceCodeShapes() {
        assertTrue(isDialablePhoneNumber("+91 (98765) 43210"))
        assertTrue(isDialablePhoneNumber("*123#"))
        assertTrue(isDialablePhoneNumber("555-0100,123"))
    }

    @Test
    fun rejectsUriInjectionControlTextAndOverlongValues() {
        assertFalse(isDialablePhoneNumber("tel:+123"))
        assertFalse(isDialablePhoneNumber("123\n456"))
        assertFalse(isDialablePhoneNumber("+" + "1".repeat(MAX_DIAL_NUMBER_LENGTH)))
        assertFalse(isDialablePhoneNumber("###"))
    }
}
