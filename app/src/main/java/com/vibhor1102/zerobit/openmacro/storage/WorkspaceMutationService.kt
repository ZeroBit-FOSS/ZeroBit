/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import com.vibhor1102.zerobit.openmacro.source.OpenMacroSourceResult
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSource
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSourcePatcher
import com.vibhor1102.zerobit.openmacro.source.OpenMacroYamlReader
import com.vibhor1102.zerobit.openmacro.source.SourcePatchResult

class WorkspaceMutationService(
    private val workspace: MacroWorkspaceStore,
) {
    fun writeIfUnchanged(
        source: OpenMacroSource,
        expectedMacroId: String?,
        expectedSourceText: String?,
    ): WorkspaceGuardedWriteResult {
        val targetId = source.document.metadata.id
        val current = workspace.read(targetId)
        val conflict = if (expectedMacroId == targetId && expectedSourceText != null) {
            when (current) {
                is WorkspaceMacroResult.Success ->
                    if (current.source.originalText == expectedSourceText) null
                    else WorkspaceWriteConflict.MODIFIED
                WorkspaceMacroResult.Missing -> WorkspaceWriteConflict.MISSING
                is WorkspaceMacroResult.InvalidSource -> WorkspaceWriteConflict.INVALID
                is WorkspaceMacroResult.InvalidId ->
                    return WorkspaceGuardedWriteResult.Failure(
                        "invalid_macro_id",
                        current.message,
                    )
                is WorkspaceMacroResult.IoFailure ->
                    return WorkspaceGuardedWriteResult.Failure(
                        "workspace_read_failed",
                        current.message,
                    )
            }
        } else {
            when (current) {
                WorkspaceMacroResult.Missing -> null
                is WorkspaceMacroResult.Success,
                is WorkspaceMacroResult.InvalidSource -> WorkspaceWriteConflict.EXISTS
                is WorkspaceMacroResult.InvalidId ->
                    return WorkspaceGuardedWriteResult.Failure(
                        "invalid_macro_id",
                        current.message,
                    )
                is WorkspaceMacroResult.IoFailure ->
                    return WorkspaceGuardedWriteResult.Failure(
                        "workspace_read_failed",
                        current.message,
                    )
            }
        }
        if (conflict != null) {
            return WorkspaceGuardedWriteResult.Conflict(conflict)
        }
        return when (val written = workspace.write(source)) {
            WorkspaceWriteResult.Success -> WorkspaceGuardedWriteResult.Written
            is WorkspaceWriteResult.Failure ->
                WorkspaceGuardedWriteResult.Failure(written.code, written.message)
        }
    }

    fun moveEditedSource(
        source: OpenMacroSource,
        oldMacroId: String,
        expectedOldSourceText: String,
    ): WorkspaceIdentityMoveResult {
        val newMacroId = source.document.metadata.id
        if (oldMacroId == newMacroId) {
            return when (
                val result = writeIfUnchanged(
                    source = source,
                    expectedMacroId = oldMacroId,
                    expectedSourceText = expectedOldSourceText,
                )
            ) {
                WorkspaceGuardedWriteResult.Written -> WorkspaceIdentityMoveResult.Moved
                is WorkspaceGuardedWriteResult.Conflict ->
                    WorkspaceIdentityMoveResult.Conflict(result.reason)
                is WorkspaceGuardedWriteResult.Failure ->
                    WorkspaceIdentityMoveResult.Failure(result.code, result.message)
            }
        }
        when (val oldSource = workspace.read(oldMacroId)) {
            is WorkspaceMacroResult.Success -> {
                if (oldSource.source.originalText != expectedOldSourceText) {
                    return WorkspaceIdentityMoveResult.Conflict(
                        WorkspaceWriteConflict.MODIFIED,
                    )
                }
            }
            WorkspaceMacroResult.Missing ->
                return WorkspaceIdentityMoveResult.Conflict(WorkspaceWriteConflict.MISSING)
            is WorkspaceMacroResult.InvalidSource ->
                return WorkspaceIdentityMoveResult.Conflict(WorkspaceWriteConflict.INVALID)
            is WorkspaceMacroResult.InvalidId ->
                return WorkspaceIdentityMoveResult.Failure(
                    "invalid_macro_id",
                    oldSource.message,
                )
            is WorkspaceMacroResult.IoFailure ->
                return WorkspaceIdentityMoveResult.Failure(
                    "workspace_read_failed",
                    oldSource.message,
                )
        }
        when (val target = workspace.read(newMacroId)) {
            WorkspaceMacroResult.Missing -> Unit
            is WorkspaceMacroResult.Success,
            is WorkspaceMacroResult.InvalidSource ->
                return WorkspaceIdentityMoveResult.Conflict(WorkspaceWriteConflict.EXISTS)
            is WorkspaceMacroResult.InvalidId ->
                return WorkspaceIdentityMoveResult.Failure(
                    "invalid_macro_id",
                    target.message,
                )
            is WorkspaceMacroResult.IoFailure ->
                return WorkspaceIdentityMoveResult.Failure(
                    "workspace_read_failed",
                    target.message,
                )
        }
        when (val written = workspace.write(source)) {
            WorkspaceWriteResult.Success -> Unit
            is WorkspaceWriteResult.Failure ->
                return WorkspaceIdentityMoveResult.Failure(written.code, written.message)
        }
        return when (val deleted = workspace.delete(oldMacroId)) {
            WorkspaceDeleteResult.Deleted -> WorkspaceIdentityMoveResult.Moved
            WorkspaceDeleteResult.Missing -> {
                when (val rollback = workspace.delete(newMacroId)) {
                    is WorkspaceDeleteResult.Failure ->
                        WorkspaceIdentityMoveResult.Failure(
                            rollback.code,
                            "The old file disappeared during rename, and the new file " +
                                "could not be rolled back: ${rollback.message}",
                        )
                    WorkspaceDeleteResult.Deleted,
                    WorkspaceDeleteResult.Missing ->
                        WorkspaceIdentityMoveResult.Conflict(WorkspaceWriteConflict.MISSING)
                }
            }
            is WorkspaceDeleteResult.Failure -> {
                val rollback = workspace.delete(newMacroId)
                WorkspaceIdentityMoveResult.Failure(
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

    fun create(macroId: String): WorkspaceCreateResult {
        val safeId = try {
            MacroStorageNames.requireMacroId(macroId)
        } catch (problem: IllegalArgumentException) {
            return WorkspaceCreateResult.Failure(
                "invalid_macro_id",
                problem.message.orEmpty(),
            )
        }
        when (val existing = workspace.read(safeId)) {
            WorkspaceMacroResult.Missing -> Unit
            is WorkspaceMacroResult.InvalidId ->
                return WorkspaceCreateResult.Failure("invalid_macro_id", existing.message)
            is WorkspaceMacroResult.IoFailure ->
                return WorkspaceCreateResult.Failure("workspace_read_failed", existing.message)
            is WorkspaceMacroResult.InvalidSource,
            is WorkspaceMacroResult.Success -> return WorkspaceCreateResult.Conflict
        }
        val parsed = when (val result = OpenMacroYamlReader.read(newMacroSource(safeId))) {
            is OpenMacroSourceResult.Success -> result.source
            is OpenMacroSourceResult.Failure ->
                return WorkspaceCreateResult.Failure(
                    "starter_source_invalid",
                    result.issues.firstOrNull()?.message
                        ?: "Could not create a valid starter macro.",
                )
        }
        return when (val written = workspace.write(parsed)) {
            WorkspaceWriteResult.Success -> WorkspaceCreateResult.Created(parsed)
            is WorkspaceWriteResult.Failure ->
                WorkspaceCreateResult.Failure(written.code, written.message)
        }
    }

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

    fun delete(macroId: String): WorkspaceDeleteResult = workspace.delete(macroId)

    private fun newMacroSource(macroId: String): String {
        val displayName = macroId
            .split('-')
            .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercaseChar) }
        return """
            format: openmacro/v0.1

            metadata:
              id: $macroId
              name: $displayName
              description: A new local automation.

            triggers:
              - id: charger-connected
                type: android.power.connected

            conditions: []

            actions:
              - id: stop
                type: openmacro.flow.stop
        """.trimIndent() + "\n"
    }
}

sealed interface WorkspaceCreateResult {
    data class Created(
        val source: OpenMacroSource,
    ) : WorkspaceCreateResult

    data object Conflict : WorkspaceCreateResult

    data class Failure(
        val code: String,
        val message: String,
    ) : WorkspaceCreateResult
}

enum class WorkspaceWriteConflict {
    MODIFIED,
    MISSING,
    INVALID,
    EXISTS,
}

sealed interface WorkspaceGuardedWriteResult {
    data object Written : WorkspaceGuardedWriteResult

    data class Conflict(
        val reason: WorkspaceWriteConflict,
    ) : WorkspaceGuardedWriteResult

    data class Failure(
        val code: String,
        val message: String,
    ) : WorkspaceGuardedWriteResult
}

sealed interface WorkspaceIdentityMoveResult {
    data object Moved : WorkspaceIdentityMoveResult

    data class Conflict(
        val reason: WorkspaceWriteConflict,
    ) : WorkspaceIdentityMoveResult

    data class Failure(
        val code: String,
        val message: String,
    ) : WorkspaceIdentityMoveResult
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
