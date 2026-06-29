/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSource
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSourceResult
import com.vibhor1102.zerobit.openmacro.source.OpenMacroYamlReader
import com.vibhor1102.zerobit.openmacro.source.SourceIssue
import java.io.IOException
import java.nio.charset.StandardCharsets

class AndroidSafWorkspaceStore(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : MacroWorkspaceStore {
    override fun write(source: OpenMacroSource): WorkspaceWriteResult {
        val macroId = try {
            MacroStorageNames.requireMacroId(source.document.metadata.id)
        } catch (problem: IllegalArgumentException) {
            return WorkspaceWriteResult.Failure(
                code = "invalid_macro_id",
                message = problem.message.orEmpty(),
            )
        }
        val macros = when (val directory = ensureMacrosDirectory()) {
            is SafDocumentResult.Success -> directory.entry
            is SafDocumentResult.Failure -> return WorkspaceWriteResult.Failure(
                code = directory.code,
                message = directory.message,
            )
        }
        val fileName = "$macroId.openmacro.yaml"
        val existing = findChild(macros.documentId, fileName)
        if (existing != null && existing.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            return WorkspaceWriteResult.Failure(
                code = "workspace_write_failed",
                message = "Workspace entry '$fileName' is a folder.",
            )
        }
        val fileUri = existing?.uri ?: try {
            DocumentsContract.createDocument(
                resolver,
                macros.uri,
                OPENMACRO_MIME_TYPE,
                fileName,
            ) ?: return WorkspaceWriteResult.Failure(
                code = "workspace_write_failed",
                message = "Could not create '$fileName'.",
            )
        } catch (problem: Exception) {
            return WorkspaceWriteResult.Failure(
                code = "workspace_write_failed",
                message = problem.message ?: "Could not create '$fileName'.",
            )
        }
        return try {
            resolver.openOutputStream(fileUri, "wt")?.use { stream ->
                stream.write(source.originalText.toByteArray(StandardCharsets.UTF_8))
            } ?: return WorkspaceWriteResult.Failure(
                code = "workspace_write_failed",
                message = "Could not open '$fileName' for writing.",
            )
            WorkspaceWriteResult.Success
        } catch (problem: IOException) {
            WorkspaceWriteResult.Failure(
                code = "workspace_write_failed",
                message = problem.message ?: "Could not write '$fileName'.",
            )
        }
    }

    override fun read(macroId: String): WorkspaceMacroResult {
        val safeId = try {
            MacroStorageNames.requireMacroId(macroId)
        } catch (problem: IllegalArgumentException) {
            return WorkspaceMacroResult.InvalidId(problem.message.orEmpty())
        }
        val macros = try {
            findMacrosDirectory()
        } catch (problem: Exception) {
            return WorkspaceMacroResult.IoFailure(
                problem.message ?: "Could not access the workspace folder.",
            )
        } ?: return WorkspaceMacroResult.Missing
        val fileName = "$safeId.openmacro.yaml"
        val file = findChild(macros.documentId, fileName)
            ?: return WorkspaceMacroResult.Missing
        if (file.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            return WorkspaceMacroResult.IoFailure("Workspace entry '$fileName' is a folder.")
        }
        return try {
            val text = resolver.openInputStream(file.uri)?.use { stream ->
                String(stream.readBytes(), StandardCharsets.UTF_8)
            } ?: return WorkspaceMacroResult.IoFailure("Could not open '$fileName'.")
            when (val parsed = OpenMacroYamlReader.read(text)) {
                is OpenMacroSourceResult.Failure ->
                    WorkspaceMacroResult.InvalidSource(parsed.issues)

                is OpenMacroSourceResult.Success -> {
                    if (parsed.source.document.metadata.id != safeId) {
                        WorkspaceMacroResult.InvalidSource(
                            listOf(
                                SourceIssue(
                                    code = "workspace_id_mismatch",
                                    message = "File '$fileName' declares macro id " +
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
            WorkspaceMacroResult.IoFailure(problem.message ?: "Could not read '$fileName'.")
        }
    }

    override fun listMacroIds(): WorkspaceMacroListResult {
        return try {
            val macros = findMacrosDirectory()
                ?: return WorkspaceMacroListResult.Success(emptyList())
            WorkspaceMacroListResult.Success(
                listChildren(macros.documentId)
                    .mapNotNull { entry -> MacroStorageNames.idFromFileName(entry.name) }
                    .sorted(),
            )
        } catch (problem: Exception) {
            WorkspaceMacroListResult.Failure(
                problem.message ?: "Could not list workspace macros.",
            )
        }
    }

    override fun delete(macroId: String): WorkspaceDeleteResult {
        val safeId = try {
            MacroStorageNames.requireMacroId(macroId)
        } catch (problem: IllegalArgumentException) {
            return WorkspaceDeleteResult.Failure(
                "invalid_macro_id",
                problem.message.orEmpty(),
            )
        }
        val macros = try {
            findMacrosDirectory()
        } catch (problem: Exception) {
            return WorkspaceDeleteResult.Failure(
                "workspace_delete_failed",
                problem.message ?: "Could not access the workspace folder.",
            )
        } ?: return WorkspaceDeleteResult.Missing
        val file = findChild(macros.documentId, "$safeId.openmacro.yaml")
            ?: return WorkspaceDeleteResult.Missing
        return try {
            if (DocumentsContract.deleteDocument(resolver, file.uri)) {
                WorkspaceDeleteResult.Deleted
            } else {
                WorkspaceDeleteResult.Failure(
                    "workspace_delete_failed",
                    "Could not delete '$safeId.openmacro.yaml'.",
                )
            }
        } catch (problem: Exception) {
            WorkspaceDeleteResult.Failure(
                "workspace_delete_failed",
                problem.message ?: "Could not delete '$safeId.openmacro.yaml'.",
            )
        }
    }

    private fun ensureMacrosDirectory(): SafDocumentResult {
        val root = rootDocument()
        val existing = try {
            findChild(root.documentId, MACROS_DIRECTORY)
        } catch (problem: Exception) {
            return SafDocumentResult.Failure(
                "workspace_directory_failed",
                problem.message ?: "Could not inspect the workspace macros folder.",
            )
        }
        if (existing != null) {
            return if (existing.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                SafDocumentResult.Success(existing)
            } else {
                SafDocumentResult.Failure(
                    "workspace_directory_failed",
                    "Workspace entry '$MACROS_DIRECTORY' is not a folder.",
                )
            }
        }
        return try {
            val uri = DocumentsContract.createDocument(
                resolver,
                root.uri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                MACROS_DIRECTORY,
            ) ?: return SafDocumentResult.Failure(
                "workspace_directory_failed",
                "Could not create the workspace macros folder.",
            )
            SafDocumentResult.Success(
                SafDocumentEntry(
                    uri = uri,
                    documentId = DocumentsContract.getDocumentId(uri),
                    name = MACROS_DIRECTORY,
                    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                ),
            )
        } catch (problem: Exception) {
            SafDocumentResult.Failure(
                "workspace_directory_failed",
                problem.message ?: "Could not create the workspace macros folder.",
            )
        }
    }

    private fun findMacrosDirectory(): SafDocumentEntry? =
        findChild(rootDocument().documentId, MACROS_DIRECTORY)
            ?.takeIf { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }

    private fun rootDocument(): SafDocumentEntry {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return SafDocumentEntry(
            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
            documentId = documentId,
            name = treeUri.lastPathSegment ?: documentId,
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
        )
    }

    private fun findChild(parentDocumentId: String, name: String): SafDocumentEntry? =
        listChildren(parentDocumentId).firstOrNull { it.name == name }

    private fun listChildren(parentDocumentId: String): List<SafDocumentEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parentDocumentId,
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        return resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            buildList {
                val idColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                )
                val nameColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                )
                val mimeColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idColumn)
                    val name = cursor.getString(nameColumn)
                    val mimeType = cursor.getString(mimeColumn).orEmpty()
                    if (documentId != null && name != null) {
                        add(
                            SafDocumentEntry(
                                uri = DocumentsContract.buildDocumentUriUsingTree(
                                    treeUri,
                                    documentId,
                                ),
                                documentId = documentId,
                                name = name,
                                mimeType = mimeType,
                            ),
                        )
                    }
                }
            }
        }.orEmpty()
    }

    private companion object {
        const val MACROS_DIRECTORY = "macros"
        const val OPENMACRO_MIME_TYPE = "application/x-yaml"
    }
}

private data class SafDocumentEntry(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val mimeType: String,
)

private sealed interface SafDocumentResult {
    data class Success(val entry: SafDocumentEntry) : SafDocumentResult

    data class Failure(
        val code: String,
        val message: String,
    ) : SafDocumentResult
}
