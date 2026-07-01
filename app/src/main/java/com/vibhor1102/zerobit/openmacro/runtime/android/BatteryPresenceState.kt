/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime.android

internal fun batteryPresentOrNull(hasPresentExtra: Boolean, present: Boolean): Boolean? =
    present.takeIf { hasPresentExtra }
