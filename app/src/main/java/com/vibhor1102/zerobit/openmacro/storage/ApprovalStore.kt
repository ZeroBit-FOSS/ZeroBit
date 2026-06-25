/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.openmacro.storage

import com.vibhor1102.zerobit.openmacro.proposal.ApprovedMacroSnapshot
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposal
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * App-private approval history. Revisions are immutable; an atomic pointer
 * selects the exact snapshot the runtime may use.
 */
class ApprovalStore(
    privateRoot: Path,
    private val pipeline: OpenMacroProposalPipeline,
    private val clock: MillisecondClock = MillisecondClock.System,
) {
    private val approvalsDirectory =
        privateRoot.toAbsolutePath().normalize().resolve("approvals")

    @Synchronized
    fun approve(proposal: OpenMacroProposal): ApprovalStoreResult<ApprovedRevision> =
        persist(
            sourceText = proposal.source.originalText,
            macroId = proposal.source.document.metadata.id,
            fingerprint = proposal.source.fingerprint,
            kind = ApprovalKind.APPROVAL,
            restoredFromRevisionId = null,
        )

    fun loadCurrent(macroId: String): ApprovalStoreResult<ApprovedRevision?> {
        val safeId = safeMacroId(macroId)
            ?: return ApprovalStoreResult.Failure("invalid_macro_id", "Invalid macro id.")
        val pointer = macroDirectory(safeId).resolve(CURRENT_FILE)
        if (!Files.exists(pointer)) {
            return ApprovalStoreResult.Success(null)
        }
        return try {
            val revisionId =
                String(Files.readAllBytes(pointer), StandardCharsets.UTF_8).trim()
            if (!RevisionId.isValid(revisionId)) {
                failure("corrupt_approval_pointer", "The approval pointer is invalid.")
            } else {
                loadRevisionInternal(safeId, revisionId)
            }
        } catch (problem: IOException) {
            failure("approval_read_failed", problem.message ?: "Could not read approval state.")
        }
    }

    fun listRevisions(macroId: String): ApprovalStoreResult<List<ApprovalRevisionSummary>> {
        val safeId = safeMacroId(macroId)
            ?: return failure("invalid_macro_id", "Invalid macro id.")
        val revisions = revisionsDirectory(safeId)
        if (!Files.isDirectory(revisions)) {
            return ApprovalStoreResult.Success(emptyList())
        }
        return try {
            val summaries = Files.newDirectoryStream(revisions).use { paths ->
                paths.mapNotNull { path ->
                    val id = path.fileName.toString()
                    RevisionId.parse(id)?.let { parsed ->
                        ApprovalRevisionSummary(
                            revisionId = id,
                            approvedAtEpochMillis = parsed.timestamp,
                            fingerprint = parsed.fingerprint,
                        )
                    }
                }.sortedByDescending { it.approvedAtEpochMillis }
            }
            ApprovalStoreResult.Success(summaries)
        } catch (problem: IOException) {
            failure("approval_read_failed", problem.message ?: "Could not list approvals.")
        }
    }

    fun loadRevision(
        macroId: String,
        revisionId: String,
    ): ApprovalStoreResult<ApprovedRevision> {
        val safeId = safeMacroId(macroId)
            ?: return failure("invalid_macro_id", "Invalid macro id.")
        return loadRevisionInternal(safeId, revisionId)
    }

    @Synchronized
    fun rollback(
        macroId: String,
        targetRevisionId: String,
    ): ApprovalStoreResult<ApprovedRevision> {
        val safeId = safeMacroId(macroId)
            ?: return failure("invalid_macro_id", "Invalid macro id.")
        val target = when (val result = loadRevisionInternal(safeId, targetRevisionId)) {
            is ApprovalStoreResult.Failure -> return result
            is ApprovalStoreResult.Success -> result.value
        }
        return persist(
            sourceText = target.snapshot.source.originalText,
            macroId = safeId,
            fingerprint = target.snapshot.source.fingerprint,
            kind = ApprovalKind.ROLLBACK,
            restoredFromRevisionId = targetRevisionId,
        )
    }

    private fun persist(
        sourceText: String,
        macroId: String,
        fingerprint: String,
        kind: ApprovalKind,
        restoredFromRevisionId: String?,
    ): ApprovalStoreResult<ApprovedRevision> {
        val safeId = safeMacroId(macroId)
            ?: return failure("invalid_macro_id", "Invalid macro id.")
        val fingerprintHex = fingerprint.removePrefix(FINGERPRINT_PREFIX)
        if (!FINGERPRINT_HEX.matches(fingerprintHex)) {
            return failure("invalid_fingerprint", "The proposed source fingerprint is invalid.")
        }

        return try {
            val previousRevisionId = currentRevisionIdOrNull(safeId)
            val revisionId = nextRevisionId(safeId, fingerprintHex)
            val revisionDirectory = revisionsDirectory(safeId).resolve(revisionId)
            val revisionsDirectory = revisionsDirectory(safeId)
            Files.createDirectories(revisionsDirectory)
            val temporaryDirectory = Files.createTempDirectory(
                revisionsDirectory,
                ".revision-",
            )
            try {
                Files.write(
                    temporaryDirectory.resolve(SOURCE_FILE),
                    sourceText.toByteArray(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                )
                Files.write(
                    temporaryDirectory.resolve(METADATA_FILE),
                    metadataText(
                        macroId = safeId,
                        fingerprint = fingerprint,
                        kind = kind,
                        previousRevisionId = previousRevisionId,
                        restoredFromRevisionId = restoredFromRevisionId,
                    ).toByteArray(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                )
                AtomicFiles.moveDirectory(temporaryDirectory, revisionDirectory)
            } finally {
                deleteEmptyDirectoryIfPresent(temporaryDirectory)
            }
            AtomicFiles.writeText(
                macroDirectory(safeId).resolve(CURRENT_FILE),
                "$revisionId\n",
            )
            loadRevisionInternal(safeId, revisionId)
        } catch (problem: IOException) {
            failure("approval_write_failed", problem.message ?: "Could not save approval.")
        } catch (problem: IllegalArgumentException) {
            failure(
                "corrupt_approval_pointer",
                problem.message ?: "The current approval pointer is invalid.",
            )
        }
    }

    private fun loadRevisionInternal(
        macroId: String,
        revisionId: String,
    ): ApprovalStoreResult<ApprovedRevision> {
        val parsedId = RevisionId.parse(revisionId)
            ?: return failure("invalid_revision_id", "The approval revision id is invalid.")
        val directory = revisionsDirectory(macroId).resolve(revisionId).normalize()
        if (!directory.startsWith(revisionsDirectory(macroId)) || !Files.isDirectory(directory)) {
            return failure("approval_missing", "The requested approval revision does not exist.")
        }

        return try {
            val metadata = parseMetadata(
                String(
                    Files.readAllBytes(directory.resolve(METADATA_FILE)),
                    StandardCharsets.UTF_8,
                ),
            )
            val sourceText = String(
                Files.readAllBytes(directory.resolve(SOURCE_FILE)),
                StandardCharsets.UTF_8,
            )
            val proposal = pipeline.propose(sourceText)
            if (proposal !is ProposalResult.Ready) {
                return failure(
                    "corrupt_approval_source",
                    "The approved source no longer parses and validates.",
                )
            }
            val ready = proposal.proposal
            if (
                ready.source.document.metadata.id != macroId ||
                ready.source.fingerprint != parsedId.fingerprint ||
                metadata["macroId"] != macroId ||
                metadata["fingerprint"] != parsedId.fingerprint
            ) {
                return failure(
                    "corrupt_approval_integrity",
                    "The approved snapshot failed its integrity check.",
                )
            }
            val kind = ApprovalKind.fromStorage(metadata["kind"])
                ?: return failure("corrupt_approval_metadata", "Unknown approval kind.")
            val previousRevisionId = metadata["previousRevisionId"].emptyToNull()
            val restoredFromRevisionId = metadata["restoredFromRevisionId"].emptyToNull()
            if (
                previousRevisionId?.let(RevisionId::isValid) == false ||
                restoredFromRevisionId?.let(RevisionId::isValid) == false ||
                (kind == ApprovalKind.APPROVAL && restoredFromRevisionId != null) ||
                (kind == ApprovalKind.ROLLBACK && restoredFromRevisionId == null) ||
                previousRevisionId?.let {
                    !Files.isDirectory(revisionsDirectory(macroId).resolve(it))
                } == true ||
                restoredFromRevisionId?.let {
                    !Files.isDirectory(revisionsDirectory(macroId).resolve(it))
                } == true
            ) {
                return failure(
                    "corrupt_approval_metadata",
                    "The approval revision links are invalid.",
                )
            }
            ApprovalStoreResult.Success(
                ApprovedRevision(
                    revisionId = revisionId,
                    approvedAtEpochMillis = parsedId.timestamp,
                    kind = kind,
                    previousRevisionId = previousRevisionId,
                    restoredFromRevisionId = restoredFromRevisionId,
                    snapshot = ApprovedMacroSnapshot.from(ready),
                ),
            )
        } catch (problem: IOException) {
            failure("approval_read_failed", problem.message ?: "Could not read approval.")
        } catch (problem: IllegalArgumentException) {
            failure(
                "corrupt_approval_metadata",
                problem.message ?: "The approval metadata is invalid.",
            )
        }
    }

    private fun nextRevisionId(macroId: String, fingerprintHex: String): String {
        var timestamp = clock.nowEpochMillis()
        var candidate = RevisionId.format(timestamp, fingerprintHex)
        while (Files.exists(revisionsDirectory(macroId).resolve(candidate))) {
            timestamp += 1
            candidate = RevisionId.format(timestamp, fingerprintHex)
        }
        return candidate
    }

    private fun currentRevisionIdOrNull(macroId: String): String? {
        val path = macroDirectory(macroId).resolve(CURRENT_FILE)
        if (!Files.exists(path)) return null
        val revisionId = String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim()
        require(RevisionId.isValid(revisionId)) { "The current approval pointer is invalid." }
        return revisionId
    }

    private fun metadataText(
        macroId: String,
        fingerprint: String,
        kind: ApprovalKind,
        previousRevisionId: String?,
        restoredFromRevisionId: String?,
    ): String = buildString {
        appendLine("version=1")
        appendLine("macroId=$macroId")
        appendLine("fingerprint=$fingerprint")
        appendLine("kind=${kind.storageValue}")
        appendLine("previousRevisionId=${previousRevisionId.orEmpty()}")
        appendLine("restoredFromRevisionId=${restoredFromRevisionId.orEmpty()}")
    }

    private fun parseMetadata(text: String): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        text.lineSequence().filter(String::isNotEmpty).forEach { line ->
            val separator = line.indexOf('=')
            require(separator > 0) { "Malformed approval metadata." }
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            require(entries.put(key, value) == null) { "Duplicate approval metadata key." }
        }
        require(entries.keys == METADATA_KEYS) { "Unexpected approval metadata keys." }
        require(entries["version"] == "1") { "Unsupported approval metadata version." }
        return entries
    }

    private fun macroDirectory(macroId: String): Path = approvalsDirectory.resolve(macroId)

    private fun revisionsDirectory(macroId: String): Path =
        macroDirectory(macroId).resolve(REVISIONS_DIRECTORY)

    private fun safeMacroId(value: String): String? =
        runCatching { MacroStorageNames.requireMacroId(value) }.getOrNull()

    private fun deleteEmptyDirectoryIfPresent(path: Path) {
        if (!Files.exists(path)) return
        runCatching {
            Files.walk(path).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun String?.emptyToNull(): String? = this?.takeIf(String::isNotEmpty)

    private fun <T> failure(code: String, message: String): ApprovalStoreResult<T> =
        ApprovalStoreResult.Failure(code, message)

    private companion object {
        const val CURRENT_FILE = "current"
        const val REVISIONS_DIRECTORY = "revisions"
        const val SOURCE_FILE = "source.openmacro.yaml"
        const val METADATA_FILE = "metadata"
        const val FINGERPRINT_PREFIX = "sha256:"
        val FINGERPRINT_HEX = Regex("^[a-f0-9]{64}$")
        val METADATA_KEYS = setOf(
            "version",
            "macroId",
            "fingerprint",
            "kind",
            "previousRevisionId",
            "restoredFromRevisionId",
        )
    }
}

data class ApprovedRevision(
    val revisionId: String,
    val approvedAtEpochMillis: Long,
    val kind: ApprovalKind,
    val previousRevisionId: String?,
    val restoredFromRevisionId: String?,
    val snapshot: ApprovedMacroSnapshot,
)

data class ApprovalRevisionSummary(
    val revisionId: String,
    val approvedAtEpochMillis: Long,
    val fingerprint: String,
)

enum class ApprovalKind(
    val storageValue: String,
) {
    APPROVAL("approval"),
    ROLLBACK("rollback");

    companion object {
        fun fromStorage(value: String?): ApprovalKind? =
            entries.firstOrNull { it.storageValue == value }
    }
}

sealed interface ApprovalStoreResult<out T> {
    data class Success<T>(val value: T) : ApprovalStoreResult<T>

    data class Failure(
        val code: String,
        val message: String,
    ) : ApprovalStoreResult<Nothing>
}

fun interface MillisecondClock {
    fun nowEpochMillis(): Long

    data object System : MillisecondClock {
        override fun nowEpochMillis(): Long = java.lang.System.currentTimeMillis()
    }
}

private data class RevisionId(
    val timestamp: Long,
    val fingerprint: String,
) {
    companion object {
        private val pattern = Regex("^([0-9]{13})-([a-f0-9]{64})$")

        fun format(timestamp: Long, fingerprintHex: String): String =
            "${timestamp.toString().padStart(13, '0')}-$fingerprintHex"

        fun parse(value: String): RevisionId? {
            val match = pattern.matchEntire(value) ?: return null
            val timestamp = match.groupValues[1].toLongOrNull() ?: return null
            return RevisionId(
                timestamp = timestamp,
                fingerprint = "sha256:${match.groupValues[2]}",
            )
        }

        fun isValid(value: String): Boolean = parse(value) != null
    }
}
