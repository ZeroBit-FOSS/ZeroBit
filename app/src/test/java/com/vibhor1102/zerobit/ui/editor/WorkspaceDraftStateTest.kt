/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDraftStateTest {
    @Test
    fun matchingWorkspaceBaselineIsSaved() {
        assertFalse(
            hasUnsavedWorkspaceChanges(
                workspaceSelected = true,
                activeMacroId = "morning",
                sourceText = "source",
                savedMacroId = "morning",
                savedSourceText = "source",
            ),
        )
    }

    @Test
    fun sourceOrIdentityChangesAreUnsaved() {
        assertTrue(
            hasUnsavedWorkspaceChanges(
                workspaceSelected = true,
                activeMacroId = "morning",
                sourceText = "edited",
                savedMacroId = "morning",
                savedSourceText = "source",
            ),
        )
        assertTrue(
            hasUnsavedWorkspaceChanges(
                workspaceSelected = true,
                activeMacroId = "evening",
                sourceText = "source",
                savedMacroId = "morning",
                savedSourceText = "source",
            ),
        )
    }

    @Test
    fun noSelectedWorkspaceDoesNotClaimUnsavedWorkspaceChanges() {
        assertFalse(
            hasUnsavedWorkspaceChanges(
                workspaceSelected = false,
                activeMacroId = "morning",
                sourceText = "source",
                savedMacroId = null,
                savedSourceText = null,
            ),
        )
    }

    @Test
    fun refreshDetectsModifiedAndMissingTrackedFiles() {
        assertEquals(
            WorkspaceExternalChange.MODIFIED,
            detectWorkspaceExternalChange(
                activeMacroId = "morning",
                savedMacroId = "morning",
                savedSourceText = "saved",
                workspaceSourceText = "external edit",
            ),
        )
        assertEquals(
            WorkspaceExternalChange.MISSING,
            detectWorkspaceExternalChange(
                activeMacroId = "morning",
                savedMacroId = "morning",
                savedSourceText = "saved",
                workspaceSourceText = null,
            ),
        )
    }

    @Test
    fun refreshIgnoresUntrackedEditorAndMatchingFile() {
        assertEquals(
            WorkspaceExternalChange.NONE,
            detectWorkspaceExternalChange(
                activeMacroId = "morning",
                savedMacroId = null,
                savedSourceText = null,
                workspaceSourceText = "workspace",
            ),
        )
        assertEquals(
            WorkspaceExternalChange.NONE,
            detectWorkspaceExternalChange(
                activeMacroId = "morning",
                savedMacroId = "morning",
                savedSourceText = "same",
                workspaceSourceText = "same",
            ),
        )
    }
}
