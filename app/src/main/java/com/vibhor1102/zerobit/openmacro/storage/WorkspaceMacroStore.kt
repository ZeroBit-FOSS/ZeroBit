/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import com.vibhor1102.zerobit.openmacro.source.OpenMacroSource
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSourceResult
import com.vibhor1102.zerobit.openmacro.source.OpenMacroYamlReader
import com.vibhor1102.zerobit.openmacro.source.SourceIssue
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * User-owned, versionable source files. This store contains no approval state,
 * secrets, runtime state, or logs.
 */
class WorkspaceMacroStore(
    workspaceRoot: Path,
) {
    private val macrosDirectory = workspaceRoot.toAbsolutePath().normalize().resolve("macros")

    fun write(source: OpenMacroSource): WorkspaceWriteResult {
        val macroId = try {
            MacroStorageNames.requireMacroId(source.document.metadata.id)
        } catch (problem: IllegalArgumentException) {
            return WorkspaceWriteResult.Failure(
                code = "invalid_macro_id",
                message = problem.message.orEmpty(),
            )
        }
        return try {
            val path = pathFor(macroId)
            if (Files.isSymbolicLink(macrosDirectory) || Files.isSymbolicLink(path)) {
                return WorkspaceWriteResult.Failure(
                    code = "workspace_symlink_not_allowed",
                    message = "OpenMacro workspace paths may not be symbolic links.",
                )
            }
            AtomicFiles.writeText(path, source.originalText)
            WorkspaceWriteResult.Success
        } catch (problem: IOException) {
            WorkspaceWriteResult.Failure(
                code = "workspace_write_failed",
                message = problem.message ?: "Could not write the macro file.",
            )
        }
    }

    fun read(macroId: String): WorkspaceMacroResult {
        val safeId = try {
            MacroStorageNames.requireMacroId(macroId)
        } catch (problem: IllegalArgumentException) {
            return WorkspaceMacroResult.InvalidId(problem.message.orEmpty())
        }
        val path = pathFor(safeId)
        if (!Files.exists(path)) {
            return WorkspaceMacroResult.Missing
        }
        if (Files.isSymbolicLink(macrosDirectory) || Files.isSymbolicLink(path)) {
            return WorkspaceMacroResult.IoFailure(
                "OpenMacro workspace paths may not be symbolic links.",
            )
        }

        return try {
            when (
                val parsed = OpenMacroYamlReader.read(
                    String(Files.readAllBytes(path), StandardCharsets.UTF_8),
                )
            ) {
                is OpenMacroSourceResult.Failure ->
                    WorkspaceMacroResult.InvalidSource(parsed.issues)

                is OpenMacroSourceResult.Success -> {
                    if (parsed.source.document.metadata.id != safeId) {
                        WorkspaceMacroResult.InvalidSource(
                            listOf(
                                SourceIssue(
                                    code = "workspace_id_mismatch",
                                    message = "File '$safeId.openmacro.yaml' declares macro id " +
                                        "'${parsed.source.document.metadata.id}'.",
                                    path = "$.metadata.id",
                                ),
                            ),
                        )
                    } else {
                        WorkspaceMacroResult.Success(parsed.source)
                    }
                }
            }
        } catch (problem: IOException) {
            WorkspaceMacroResult.IoFailure(problem.message ?: "Could not read the macro file.")
        }
    }

    fun listMacroIds(): WorkspaceMacroListResult {
        if (!Files.isDirectory(macrosDirectory)) {
            return WorkspaceMacroListResult.Success(emptyList())
        }
        return try {
            WorkspaceMacroListResult.Success(
                Files.newDirectoryStream(macrosDirectory, "*.openmacro.yaml").use { paths ->
                    paths.mapNotNull { path ->
                        MacroStorageNames.idFromFileName(path.fileName.toString())
                    }.sorted()
                },
            )
        } catch (problem: IOException) {
            WorkspaceMacroListResult.Failure(
                problem.message ?: "Could not list workspace macros.",
            )
        }
    }

    private fun pathFor(macroId: String): Path =
        macrosDirectory.resolve("$macroId.openmacro.yaml")
}

sealed interface WorkspaceMacroResult {
    data class Success(val source: OpenMacroSource) : WorkspaceMacroResult

    data object Missing : WorkspaceMacroResult

    data class InvalidId(val message: String) : WorkspaceMacroResult

    data class InvalidSource(val issues: List<SourceIssue>) : WorkspaceMacroResult

    data class IoFailure(val message: String) : WorkspaceMacroResult
}

sealed interface WorkspaceWriteResult {
    data object Success : WorkspaceWriteResult

    data class Failure(
        val code: String,
        val message: String,
    ) : WorkspaceWriteResult
}

sealed interface WorkspaceMacroListResult {
    data class Success(val macroIds: List<String>) : WorkspaceMacroListResult

    data class Failure(val message: String) : WorkspaceMacroListResult
}

internal object MacroStorageNames {
    private val macroIdPattern = Regex("^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$")
    private const val SUFFIX = ".openmacro.yaml"

    fun requireMacroId(macroId: String): String {
        require(macroIdPattern.matches(macroId)) {
            "Macro id must be 1-64 lowercase letters, numbers, or hyphens."
        }
        return macroId
    }

    fun idFromFileName(fileName: String): String? {
        if (!fileName.endsWith(SUFFIX)) return null
        val candidate = fileName.removeSuffix(SUFFIX)
        return candidate.takeIf(macroIdPattern::matches)
    }
}
