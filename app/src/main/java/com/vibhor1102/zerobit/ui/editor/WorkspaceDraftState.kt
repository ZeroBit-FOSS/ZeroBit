/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

internal fun hasUnsavedWorkspaceChanges(
    workspaceSelected: Boolean,
    activeMacroId: String,
    sourceText: String,
    savedMacroId: String?,
    savedSourceText: String?,
): Boolean = workspaceSelected &&
    (savedMacroId != activeMacroId || savedSourceText != sourceText)

enum class WorkspaceExternalChange {
    NONE,
    MODIFIED,
    MISSING,
    INVALID,
    EXISTS,
}

internal fun detectWorkspaceExternalChange(
    activeMacroId: String,
    savedMacroId: String?,
    savedSourceText: String?,
    workspaceSourceText: String?,
): WorkspaceExternalChange {
    if (savedMacroId != activeMacroId || savedSourceText == null) {
        return WorkspaceExternalChange.NONE
    }
    if (workspaceSourceText == null) {
        return WorkspaceExternalChange.MISSING
    }
    return if (workspaceSourceText == savedSourceText) {
        WorkspaceExternalChange.NONE
    } else {
        WorkspaceExternalChange.MODIFIED
    }
}
