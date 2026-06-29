/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.openmacro.source.OpenMacroSource
import com.vibhor1102.zerobit.ui.editor.MacroEditorScreen
import com.vibhor1102.zerobit.ui.editor.AndroidLauncherAppCatalog
import com.vibhor1102.zerobit.ui.editor.LauncherAppOption
import com.vibhor1102.zerobit.ui.editor.MacroEditorSession
import com.vibhor1102.zerobit.ui.editor.FormSourceEditResult
import com.vibhor1102.zerobit.ui.editor.SampleMacro
import com.vibhor1102.zerobit.ui.editor.WorkspacePanelState
import com.vibhor1102.zerobit.ui.editor.WorkspaceExternalChange
import com.vibhor1102.zerobit.ui.editor.detectWorkspaceExternalChange
import com.vibhor1102.zerobit.ui.editor.hasUnsavedWorkspaceChanges
import com.vibhor1102.zerobit.ui.theme.ZeroBitTheme
import com.vibhor1102.zerobit.openmacro.storage.ApprovalStoreResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceMacroListResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceMacroResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceCreateResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceDeleteResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceGuardedWriteResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceIdentityMoveResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceRenameResult
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceWriteConflict
import com.vibhor1102.zerobit.openmacro.storage.WorkspaceWriteResult
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeLifecycleResult
import com.vibhor1102.zerobit.openmacro.runtime.android.AndroidPermissionRecovery
import com.vibhor1102.zerobit.openmacro.runtime.android.AndroidPermissionRecoveryFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZeroBitTheme {
                val pipeline = remember {
                    OpenMacroProposalPipeline(CapabilityRegistry.builtIn())
                }
                val app = application as ZeroBitApplication
                var session by remember {
                    mutableStateOf(
                        MacroEditorSession(
                            pipeline,
                            initialApproved = app.currentApprovedSnapshot(
                                SampleMacro.MACRO_ID,
                            ),
                        ),
                    )
                }
                var state by remember {
                    mutableStateOf(
                        session.create(SampleMacro.source),
                    )
                }
                var activeMacroId by remember {
                    mutableStateOf(SampleMacro.MACRO_ID)
                }
                var workspaceFolderLabel by remember {
                    mutableStateOf(app.selectedWorkspace()?.label)
                }
                var workspaceMacroIds by remember {
                    mutableStateOf(app.workspaceMacroIdsOrEmpty())
                }
                var workspaceStatus by remember {
                    mutableStateOf<String?>(null)
                }
                var workspaceBaselineMacroId by remember {
                    mutableStateOf<String?>(null)
                }
                var workspaceBaselineSourceText by remember {
                    mutableStateOf<String?>(null)
                }
                var workspaceExternalChange by remember {
                    mutableStateOf(WorkspaceExternalChange.NONE)
                }
                val workspaceLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree(),
                ) { treeUri ->
                    if (treeUri != null) {
                        try {
                            val selection = app.selectWorkspace(treeUri)
                            workspaceFolderLabel = selection.label
                            workspaceBaselineMacroId = null
                            workspaceBaselineSourceText = null
                            workspaceExternalChange = WorkspaceExternalChange.NONE
                            val listed = app.listWorkspaceMacroIds()
                            workspaceMacroIds = listed.macroIdsOrEmpty()
                            workspaceStatus = listed.messageOrNull()
                                ?: "Workspace selected."
                        } catch (problem: SecurityException) {
                            workspaceStatus = problem.message
                                ?: "Android did not grant workspace access."
                        }
                    }
                }
                var recoveryReport by remember {
                    mutableStateOf(app.runtimeRecoveryReport)
                }
                var runtimeEnabled by remember {
                    mutableStateOf(app.isMacroEnabled(activeMacroId))
                }
                var runtimeOverview by remember {
                    mutableStateOf(app.macroOverview(activeMacroId))
                }
                var launcherApps by remember {
                    mutableStateOf<List<LauncherAppOption>>(emptyList())
                }
                fun openEditorForSource(macroId: String, sourceText: String) {
                    session = MacroEditorSession(
                        pipeline,
                        initialApproved = app.currentApprovedSnapshot(
                            macroId,
                        ),
                    )
                    state = session.create(sourceText)
                    activeMacroId = macroId
                    runtimeEnabled = app.isMacroEnabled(macroId)
                    runtimeOverview = app.macroOverview(macroId)
                }
                fun recordWorkspaceWrite(source: OpenMacroSource) {
                    activeMacroId = source.document.metadata.id
                    workspaceBaselineMacroId = source.document.metadata.id
                    workspaceBaselineSourceText = source.originalText
                    workspaceExternalChange = WorkspaceExternalChange.NONE
                    val listed = app.listWorkspaceMacroIds()
                    workspaceMacroIds = listed.macroIdsOrEmpty()
                    workspaceStatus = listed.messageOrNull()
                        ?: "Saved ${source.document.metadata.id}."
                }
                fun reportWorkspaceConflict(reason: WorkspaceWriteConflict) {
                    workspaceExternalChange = when (reason) {
                        WorkspaceWriteConflict.MODIFIED -> WorkspaceExternalChange.MODIFIED
                        WorkspaceWriteConflict.MISSING -> WorkspaceExternalChange.MISSING
                        WorkspaceWriteConflict.INVALID -> WorkspaceExternalChange.INVALID
                        WorkspaceWriteConflict.EXISTS -> WorkspaceExternalChange.EXISTS
                    }
                    workspaceStatus = when (reason) {
                        WorkspaceWriteConflict.MODIFIED ->
                            "The workspace file changed before it could be saved."
                        WorkspaceWriteConflict.MISSING ->
                            "The workspace file was removed before it could be saved."
                        WorkspaceWriteConflict.INVALID ->
                            "The workspace file changed and is now invalid."
                        WorkspaceWriteConflict.EXISTS ->
                            "A workspace macro with this id already exists."
                    }
                }
                fun saveWorkspaceMacro(
                    overwrite: Boolean,
                    saveAs: Boolean = false,
                ) {
                    val source = state.visibleProposal?.source
                    if (source == null || source.originalText != state.sourceText) {
                        workspaceStatus =
                            "Wait for the current source to validate before saving it."
                        return
                    }
                    if (overwrite) {
                        when (val saved = app.writeWorkspaceMacro(source)) {
                            WorkspaceWriteResult.Success -> recordWorkspaceWrite(source)
                            is WorkspaceWriteResult.Failure -> {
                                workspaceStatus = saved.message
                            }
                        }
                        return
                    }
                    when (
                        val saved = app.writeWorkspaceMacroIfUnchanged(
                            source = source,
                            expectedMacroId = workspaceBaselineMacroId.takeUnless { saveAs },
                            expectedSourceText = workspaceBaselineSourceText.takeUnless { saveAs },
                        )
                    ) {
                        WorkspaceGuardedWriteResult.Written -> recordWorkspaceWrite(source)
                        is WorkspaceGuardedWriteResult.Failure -> {
                            workspaceStatus = saved.message
                        }
                        is WorkspaceGuardedWriteResult.Conflict -> {
                            reportWorkspaceConflict(saved.reason)
                        }
                    }
                }
                fun renameWorkspaceIdentity() {
                    val source = state.visibleProposal?.source
                    val oldMacroId = workspaceBaselineMacroId
                    val oldSourceText = workspaceBaselineSourceText
                    if (
                        source == null ||
                        source.originalText != state.sourceText ||
                        oldMacroId == null ||
                        oldSourceText == null
                    ) {
                        workspaceStatus =
                            "Wait for the current source to validate before renaming it."
                        return
                    }
                    when (
                        val moved = app.moveEditedWorkspaceMacro(
                            source = source,
                            oldMacroId = oldMacroId,
                            expectedOldSourceText = oldSourceText,
                        )
                    ) {
                        WorkspaceIdentityMoveResult.Moved -> {
                            recordWorkspaceWrite(source)
                            workspaceStatus =
                                "Renamed $oldMacroId to ${source.document.metadata.id}. " +
                                "Approvals were not changed."
                        }
                        is WorkspaceIdentityMoveResult.Conflict -> {
                            reportWorkspaceConflict(moved.reason)
                        }
                        is WorkspaceIdentityMoveResult.Failure -> {
                            workspaceStatus = moved.message
                        }
                    }
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {
                    recoveryReport = app.retryDesiredMacros()
                    runtimeEnabled = app.isMacroEnabled(activeMacroId)
                    runtimeOverview = app.macroOverview(activeMacroId)
                }

                LaunchedEffect(state.sourceText, session) {
                    val sessionToParse = session
                    val baseState = state
                    val sourceToParse = state.sourceText
                    delay(SOURCE_PARSE_DEBOUNCE_MILLIS)
                    val parsed = withContext(Dispatchers.Default) {
                        sessionToParse.updateSource(baseState, sourceToParse)
                    }
                    if (state.sourceText == sourceToParse) {
                        state = parsed.copy(mode = state.mode)
                    }
                }

                LaunchedEffect(app) {
                    launcherApps = withContext(Dispatchers.IO) {
                        AndroidLauncherAppCatalog(app).listApps()
                    }
                }

                MacroEditorScreen(
                    state = state,
                    launcherApps = launcherApps,
                    onModeSelected = { state = session.selectMode(state, it) },
                    onSourceChanged = { state = state.copy(sourceText = it) },
                    onApprove = {
                        val proposal = (state.result as? ProposalResult.Ready)?.proposal
                        if (proposal != null) {
                            val macroId = proposal.source.document.metadata.id
                            when (val result = app.approveMacro(proposal)) {
                                is ApprovalStoreResult.Success -> {
                                    state = session.approveCurrent(state)
                                    activeMacroId = macroId
                                    if (runtimeEnabled) {
                                        when (
                                            val enabled = app.enableMacro(
                                                macroId,
                                            )
                                        ) {
                                            is RuntimeLifecycleResult.Enabled -> Unit
                                            is RuntimeLifecycleResult.EnableFailed -> {
                                                runtimeEnabled = app.isMacroEnabled(
                                                    macroId,
                                                )
                                                state = session.reportFormEditError(
                                                    state,
                                                    if (runtimeEnabled) {
                                                        "${enabled.message} The previous approved revision is still active."
                                                    } else {
                                                        enabled.message
                                                    },
                                                )
                                            }
                                            else -> Unit
                                        }
                                        runtimeOverview = app.macroOverview(
                                            macroId,
                                        )
                                    }
                                }
                                is ApprovalStoreResult.Failure -> {
                                    state = session.reportFormEditError(
                                        state,
                                        result.message,
                                    )
                                }
                            }
                        }
                    },
                    onConfigChanged = { blockId, key, value ->
                        when (
                            val result = session.updateConfig(
                                current = state,
                                blockId = blockId,
                                key = key,
                                value = value,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(state, result.message)
                            }
                        }
                    },
                    onVariableChanged = { variableName, key, value ->
                        when (
                            val result = session.updateVariableField(
                                current = state,
                                variableName = variableName,
                                key = key,
                                value = value,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onAddVariable = { template ->
                        when (
                            val result = session.addVariable(
                                current = state,
                                template = template,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onRenameVariable = { oldName, newName ->
                        when (
                            val result = session.renameVariable(
                                current = state,
                                oldName = oldName,
                                newName = newName,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onRemoveVariable = { variableName ->
                        when (
                            val result = session.removeVariable(
                                current = state,
                                variableName = variableName,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onAddGroupedAction = { groupBlockId, template ->
                        when (
                            val result = session.addGroupedAction(
                                current = state,
                                groupBlockId = groupBlockId,
                                template = template,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onRemoveGroupedAction = { childBlockId ->
                        when (
                            val result = session.removeGroupedAction(
                                current = state,
                                childBlockId = childBlockId,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onMoveGroupedAction = { childBlockId, direction ->
                        when (
                            val result = session.moveGroupedAction(
                                current = state,
                                childBlockId = childBlockId,
                                direction = direction,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onAddTopLevelBlock = { template ->
                        when (
                            val result = session.addTopLevelBlock(
                                current = state,
                                template = template,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onRemoveTopLevelBlock = { blockId ->
                        when (
                            val result = session.removeTopLevelBlock(
                                current = state,
                                blockId = blockId,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onMoveTopLevelBlock = { blockId, direction ->
                        when (
                            val result = session.moveTopLevelBlock(
                                current = state,
                                blockId = blockId,
                                direction = direction,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onConditionGroupLogicChanged = { groupPath, logic ->
                        when (
                            val result = session.switchConditionGroup(
                                current = state,
                                groupPath = groupPath,
                                logic = logic,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onAddConditionTreeChild = { groupPath, template ->
                        when (
                            val result = session.addConditionTreeChild(
                                current = state,
                                groupPath = groupPath,
                                template = template,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onRemoveConditionTreeChild = { childPath ->
                        when (
                            val result = session.removeConditionTreeChild(
                                current = state,
                                childPath = childPath,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onWrapConditionTreeChildInNot = { childPath ->
                        when (
                            val result = session.wrapConditionTreeChildInNot(
                                current = state,
                                childPath = childPath,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    onUnwrapConditionTreeNot = { childPath ->
                        when (
                            val result = session.unwrapConditionTreeNot(
                                current = state,
                                childPath = childPath,
                            )
                        ) {
                            is FormSourceEditResult.Updated -> state = result.state
                            is FormSourceEditResult.Rejected -> {
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                    },
                    workspace = WorkspacePanelState(
                        folderLabel = workspaceFolderLabel,
                        macroIds = workspaceMacroIds,
                        activeMacroId = activeMacroId,
                        trackedMacroId = workspaceBaselineMacroId,
                        editorMacroId = state.visibleProposal?.source
                            ?.takeIf { it.originalText == state.sourceText }
                            ?.document
                            ?.metadata
                            ?.id
                            ?: activeMacroId,
                        status = workspaceStatus,
                        hasUnsavedChanges = hasUnsavedWorkspaceChanges(
                            workspaceSelected = workspaceFolderLabel != null,
                            activeMacroId = activeMacroId,
                            sourceText = state.sourceText,
                            savedMacroId = workspaceBaselineMacroId,
                            savedSourceText = workspaceBaselineSourceText,
                        ),
                        externalChange = workspaceExternalChange,
                    ),
                    onChooseWorkspace = {
                        workspaceLauncher.launch(null)
                    },
                    onSaveToWorkspace = {
                        saveWorkspaceMacro(overwrite = false)
                    },
                    onOverwriteWorkspaceMacro = {
                        saveWorkspaceMacro(overwrite = true)
                    },
                    onSaveAsWorkspaceMacro = {
                        saveWorkspaceMacro(overwrite = false, saveAs = true)
                    },
                    onRenameWorkspaceIdentity = {
                        renameWorkspaceIdentity()
                    },
                    onRefreshWorkspace = {
                        when (val listed = app.listWorkspaceMacroIds()) {
                            is WorkspaceMacroListResult.Failure -> {
                                workspaceStatus = listed.message
                            }
                            is WorkspaceMacroListResult.Success -> {
                                workspaceMacroIds = listed.macroIds
                                if (
                                    workspaceBaselineMacroId != activeMacroId ||
                                    workspaceBaselineSourceText == null
                                ) {
                                    workspaceExternalChange = WorkspaceExternalChange.NONE
                                    workspaceStatus = "Workspace list refreshed."
                                } else {
                                    when (val loaded = app.readWorkspaceMacro(activeMacroId)) {
                                        is WorkspaceMacroResult.Success -> {
                                            workspaceExternalChange =
                                                detectWorkspaceExternalChange(
                                                    activeMacroId = activeMacroId,
                                                    savedMacroId = workspaceBaselineMacroId,
                                                    savedSourceText =
                                                        workspaceBaselineSourceText,
                                                    workspaceSourceText =
                                                        loaded.source.originalText,
                                                )
                                            workspaceStatus =
                                                if (
                                                    workspaceExternalChange ==
                                                    WorkspaceExternalChange.MODIFIED
                                                ) {
                                                    "The active macro changed outside the editor."
                                                } else {
                                                    "Workspace is up to date."
                                                }
                                        }
                                        WorkspaceMacroResult.Missing -> {
                                            workspaceExternalChange =
                                                WorkspaceExternalChange.MISSING
                                            workspaceStatus =
                                                "The active macro was removed from the workspace."
                                        }
                                        is WorkspaceMacroResult.InvalidSource -> {
                                            workspaceExternalChange =
                                                WorkspaceExternalChange.INVALID
                                            workspaceStatus =
                                                loaded.issues.firstOrNull()?.message
                                                    ?: "The active workspace file is invalid."
                                        }
                                        is WorkspaceMacroResult.InvalidId -> {
                                            workspaceStatus = loaded.message
                                        }
                                        is WorkspaceMacroResult.IoFailure -> {
                                            workspaceStatus = loaded.message
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onReloadWorkspaceMacro = {
                        when (val loaded = app.readWorkspaceMacro(activeMacroId)) {
                            is WorkspaceMacroResult.Success -> {
                                openEditorForSource(
                                    macroId = activeMacroId,
                                    sourceText = loaded.source.originalText,
                                )
                                workspaceBaselineMacroId = activeMacroId
                                workspaceBaselineSourceText = loaded.source.originalText
                                workspaceExternalChange = WorkspaceExternalChange.NONE
                                workspaceStatus = "Reloaded $activeMacroId."
                            }
                            WorkspaceMacroResult.Missing -> {
                                workspaceExternalChange = WorkspaceExternalChange.MISSING
                                workspaceStatus =
                                    "The active macro was removed before it could be reloaded."
                            }
                            is WorkspaceMacroResult.InvalidSource -> {
                                workspaceExternalChange = WorkspaceExternalChange.INVALID
                                workspaceStatus = loaded.issues.firstOrNull()?.message
                                    ?: "The active workspace file is invalid."
                            }
                            is WorkspaceMacroResult.InvalidId -> {
                                workspaceStatus = loaded.message
                            }
                            is WorkspaceMacroResult.IoFailure -> {
                                workspaceStatus = loaded.message
                            }
                        }
                    },
                    onOpenWorkspaceMacro = { macroId ->
                        when (val loaded = app.readWorkspaceMacro(macroId)) {
                            is WorkspaceMacroResult.Success -> {
                                openEditorForSource(
                                    macroId = macroId,
                                    sourceText = loaded.source.originalText,
                                )
                                workspaceBaselineMacroId = macroId
                                workspaceBaselineSourceText = loaded.source.originalText
                                workspaceExternalChange = WorkspaceExternalChange.NONE
                                workspaceStatus = "Opened $macroId."
                            }
                            WorkspaceMacroResult.Missing -> {
                                workspaceStatus = "Workspace macro '$macroId' was not found."
                            }
                            is WorkspaceMacroResult.InvalidId -> {
                                workspaceStatus = loaded.message
                            }
                            is WorkspaceMacroResult.InvalidSource -> {
                                workspaceStatus = loaded.issues.firstOrNull()?.message
                                    ?: "Workspace macro '$macroId' is invalid."
                            }
                            is WorkspaceMacroResult.IoFailure -> {
                                workspaceStatus = loaded.message
                            }
                        }
                    },
                    onCreateWorkspaceMacro = { macroId ->
                        when (val created = app.createWorkspaceMacro(macroId.trim())) {
                            is WorkspaceCreateResult.Created -> {
                                val createdId = created.source.document.metadata.id
                                openEditorForSource(createdId, created.source.originalText)
                                workspaceBaselineMacroId = createdId
                                workspaceBaselineSourceText = created.source.originalText
                                workspaceExternalChange = WorkspaceExternalChange.NONE
                                val listed = app.listWorkspaceMacroIds()
                                workspaceMacroIds = listed.macroIdsOrEmpty()
                                workspaceStatus = listed.messageOrNull()
                                    ?: "Created $createdId. Approve it before enabling it."
                            }
                            WorkspaceCreateResult.Conflict -> {
                                workspaceStatus = "A workspace macro with that id already exists."
                            }
                            is WorkspaceCreateResult.Failure -> {
                                workspaceStatus = created.message
                            }
                        }
                    },
                    onRenameWorkspaceMacro = { oldId, newIdInput ->
                        val newId = newIdInput.trim()
                        when (val renamed = app.renameWorkspaceMacro(oldId, newId)) {
                            WorkspaceRenameResult.Renamed -> {
                                val listed = app.listWorkspaceMacroIds()
                                workspaceMacroIds = listed.macroIdsOrEmpty()
                                if (oldId == activeMacroId && oldId != newId) {
                                    when (val loaded = app.readWorkspaceMacro(newId)) {
                                        is WorkspaceMacroResult.Success -> {
                                            openEditorForSource(
                                                macroId = newId,
                                                sourceText = loaded.source.originalText,
                                            )
                                            workspaceBaselineMacroId = newId
                                            workspaceBaselineSourceText =
                                                loaded.source.originalText
                                            workspaceExternalChange =
                                                WorkspaceExternalChange.NONE
                                            workspaceStatus =
                                                "Renamed to $newId. Existing approvals were not changed."
                                        }
                                        else -> {
                                            workspaceStatus =
                                                "Renamed to $newId, but could not reopen it."
                                        }
                                    }
                                } else {
                                    workspaceStatus = listed.messageOrNull()
                                        ?: "Renamed $oldId to $newId."
                                }
                            }
                            WorkspaceRenameResult.Missing -> {
                                workspaceStatus = "Workspace macro '$oldId' was not found."
                            }
                            WorkspaceRenameResult.Conflict -> {
                                workspaceStatus = "A workspace macro named '$newId' already exists."
                            }
                            is WorkspaceRenameResult.Failure -> {
                                workspaceStatus = renamed.message
                            }
                        }
                    },
                    onDeleteWorkspaceMacro = { macroId ->
                        when (val deleted = app.deleteWorkspaceMacro(macroId)) {
                            WorkspaceDeleteResult.Deleted -> {
                                val listed = app.listWorkspaceMacroIds()
                                workspaceMacroIds = listed.macroIdsOrEmpty()
                                if (macroId == activeMacroId) {
                                    workspaceBaselineMacroId = null
                                    workspaceBaselineSourceText = null
                                    workspaceExternalChange = WorkspaceExternalChange.NONE
                                }
                                workspaceStatus = if (macroId == activeMacroId) {
                                    "Deleted $macroId from the workspace. Its editor copy and " +
                                        "approved runtime revision were not changed."
                                } else {
                                    listed.messageOrNull() ?: "Deleted $macroId."
                                }
                            }
                            WorkspaceDeleteResult.Missing -> {
                                workspaceStatus = "Workspace macro '$macroId' was already missing."
                            }
                            is WorkspaceDeleteResult.Failure -> {
                                workspaceStatus = deleted.message
                            }
                        }
                    },
                    recoveryReport = recoveryReport,
                    onRetryRuntime = {
                        recoveryReport = app.retryDesiredMacros()
                        runtimeEnabled = app.isMacroEnabled(activeMacroId)
                        runtimeOverview = app.macroOverview(activeMacroId)
                    },
                    runtimeEnabled = runtimeEnabled,
                    onRuntimeEnabledChanged = { shouldEnable ->
                        val result = if (shouldEnable) {
                            app.enableMacro(activeMacroId)
                        } else {
                            app.disableMacro(activeMacroId)
                        }
                        when (result) {
                            is RuntimeLifecycleResult.Enabled -> {
                                runtimeEnabled = true
                            }
                            RuntimeLifecycleResult.Disabled,
                            RuntimeLifecycleResult.AlreadyDisabled -> {
                                runtimeEnabled = false
                            }
                            is RuntimeLifecycleResult.EnableFailed -> {
                                runtimeEnabled = app.isMacroEnabled(
                                    activeMacroId,
                                )
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                        runtimeOverview = app.macroOverview(activeMacroId)
                    },
                    onRepairPermission = { permission ->
                        when (
                            val recovery = AndroidPermissionRecoveryFactory.create(
                                this@MainActivity,
                                permission,
                            )
                        ) {
                            is AndroidPermissionRecovery.RequestRuntimePermission ->
                                permissionLauncher.launch(recovery.manifestPermission)
                            is AndroidPermissionRecovery.OpenSettings ->
                                startActivity(recovery.intent)
                        }
                    },
                    runtimeOverview = runtimeOverview,
                )
            }
        }
    }

    private companion object {
        const val SOURCE_PARSE_DEBOUNCE_MILLIS = 250L
    }
}

private fun ZeroBitApplication.workspaceMacroIdsOrEmpty(): List<String> =
    listWorkspaceMacroIds().macroIdsOrEmpty()

private fun WorkspaceMacroListResult.macroIdsOrEmpty(): List<String> = when (this) {
    is WorkspaceMacroListResult.Success -> macroIds
    is WorkspaceMacroListResult.Failure -> emptyList()
}

private fun WorkspaceMacroListResult.messageOrNull(): String? = when (this) {
    is WorkspaceMacroListResult.Success -> null
    is WorkspaceMacroListResult.Failure -> message
}
