/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.runtime

data class RuntimeMacroStatus(
    val macroId: String,
    val revisionId: String,
    val sourceFingerprint: String,
    val triggerCount: Int,
    val executing: Boolean,
)

data class RuntimeMacroOverview(
    val active: RuntimeMacroStatus?,
    val lastEvent: RuntimeDiagnosticEvent?,
    val recentEvents: List<RuntimeDiagnosticEvent> = emptyList(),
)
