/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import com.vibhor1102.zerobit.openmacro.source.OpenMacroSourceResult
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSourcePatcher
import com.vibhor1102.zerobit.openmacro.source.OpenMacroYamlReader
import com.vibhor1102.zerobit.openmacro.source.SourcePatchResult

class WorkspaceMutationService(
    private val workspace: MacroWorkspaceStore,
) {
    fun rename(oldId: String, newId: String): WorkspaceRenameResult {
        val safeNewId = try {
            MacroStorageNames.requireMacroId(newId)
        } catch (problem: IllegalArgumentException) {
            return WorkspaceRenameResult.Failure(
                "invalid_macro_id",
                problem.message.orEmpty(),
            )
        }
        if (oldId == safeNewId) {
            return WorkspaceRenameResult.Renamed
        }
        when (val existing = workspace.read(safeNewId)) {
            WorkspaceMacroResult.Missing -> Unit
            is WorkspaceMacroResult.InvalidId ->
                return WorkspaceRenameResult.Failure("invalid_macro_id", existing.message)
            is WorkspaceMacroResult.IoFailure ->
                return WorkspaceRenameResult.Failure("workspace_read_failed", existing.message)
            is WorkspaceMacroResult.InvalidSource,
            is WorkspaceMacroResult.Success -> return WorkspaceRenameResult.Conflict
        }
        val source = when (val current = workspace.read(oldId)) {
            is WorkspaceMacroResult.Success -> current.source
            WorkspaceMacroResult.Missing -> return WorkspaceRenameResult.Missing
            is WorkspaceMacroResult.InvalidId ->
                return WorkspaceRenameResult.Failure("invalid_macro_id", current.message)
            is WorkspaceMacroResult.InvalidSource ->
                return WorkspaceRenameResult.Failure(
                    "source_invalid",
                    current.issues.firstOrNull()?.message ?: "Macro source is invalid.",
                )
            is WorkspaceMacroResult.IoFailure ->
                return WorkspaceRenameResult.Failure("workspace_read_failed", current.message)
        }
        val patched = when (
            val result = OpenMacroSourcePatcher.replaceMetadataText(
                source.originalText,
                "id",
                safeNewId,
            )
        ) {
            is SourcePatchResult.Success -> result.sourceText
            is SourcePatchResult.InvalidSource ->
                return WorkspaceRenameResult.Failure(
                    "source_invalid",
                    result.issues.firstOrNull()?.message ?: "Macro source is invalid.",
                )
            is SourcePatchResult.NotFound,
            is SourcePatchResult.Unsupported ->
                return WorkspaceRenameResult.Failure(
                    "rename_patch_failed",
                    (result as? SourcePatchResult.Unsupported)?.message
                        ?: "Could not update the macro id.",
                )
        }
        val parsed = when (val result = OpenMacroYamlReader.read(patched)) {
            is OpenMacroSourceResult.Success -> result.source
            is OpenMacroSourceResult.Failure ->
                return WorkspaceRenameResult.Failure(
                    "renamed_source_invalid",
                    result.issues.firstOrNull()?.message
                        ?: "Renamed macro source is invalid.",
                )
        }
        when (val written = workspace.write(parsed)) {
            WorkspaceWriteResult.Success -> Unit
            is WorkspaceWriteResult.Failure ->
                return WorkspaceRenameResult.Failure(written.code, written.message)
        }
        return when (val deleted = workspace.delete(oldId)) {
            WorkspaceDeleteResult.Deleted,
            WorkspaceDeleteResult.Missing -> WorkspaceRenameResult.Renamed
            is WorkspaceDeleteResult.Failure -> {
                val rollback = workspace.delete(safeNewId)
                WorkspaceRenameResult.Failure(
                    deleted.code,
                    if (rollback is WorkspaceDeleteResult.Failure) {
                        "${deleted.message} The new file also remains because rollback failed: " +
                            rollback.message
                    } else {
                        deleted.message
                    },
                )
            }
        }
    }
}

sealed interface WorkspaceRenameResult {
    data object Renamed : WorkspaceRenameResult

    data object Missing : WorkspaceRenameResult

    data object Conflict : WorkspaceRenameResult

    data class Failure(
        val code: String,
        val message: String,
    ) : WorkspaceRenameResult
}
