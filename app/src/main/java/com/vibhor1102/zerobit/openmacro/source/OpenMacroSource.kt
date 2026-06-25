/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.source

import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument

/**
 * Keeps the exact user-owned source beside its decoded meaning.
 *
 * Reading a file never reformats it. The canonical writer is used only for a
 * new document or when the user explicitly chooses to format the source.
 */
data class OpenMacroSource(
    val document: OpenMacroDocument,
    val originalText: String,
    val fingerprint: String,
)

sealed interface OpenMacroSourceResult {
    data class Success(
        val source: OpenMacroSource,
    ) : OpenMacroSourceResult

    data class Failure(
        val issues: List<SourceIssue>,
    ) : OpenMacroSourceResult
}

data class SourceIssue(
    val code: String,
    val message: String,
    val path: String = "$",
    val line: Int? = null,
    val column: Int? = null,
)
