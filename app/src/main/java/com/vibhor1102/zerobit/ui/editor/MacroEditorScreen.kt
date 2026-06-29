/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.vibhor1102.zerobit.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.capability.CapabilityFieldKind
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.capability.AndroidPermission
import com.vibhor1102.zerobit.openmacro.model.ConditionGroupLogic
import com.vibhor1102.zerobit.openmacro.model.MacroBlock
import com.vibhor1102.zerobit.openmacro.model.MacroConditionNode
import com.vibhor1102.zerobit.openmacro.model.MacroValue
import com.vibhor1102.zerobit.openmacro.model.MacroVariable
import com.vibhor1102.zerobit.openmacro.model.MacroVariableType
import com.vibhor1102.zerobit.openmacro.model.OpenMacroDocument
import com.vibhor1102.zerobit.openmacro.proposal.BlockExplanation
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposal
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeRecoveryReport
import com.vibhor1102.zerobit.openmacro.runtime.MacroRecoveryStatus
import com.vibhor1102.zerobit.openmacro.runtime.userFacingName
import com.vibhor1102.zerobit.openmacro.runtime.RuntimeMacroOverview
import com.vibhor1102.zerobit.ui.theme.ZeroBitTheme

@Composable
fun MacroEditorScreen(
    state: MacroEditorState,
    launcherApps: List<LauncherAppOption> = emptyList(),
    onModeSelected: (EditorMode) -> Unit,
    onSourceChanged: (String) -> Unit,
    onApprove: () -> Unit,
    onConfigChanged: (String, String, MacroValue?) -> Unit,
    recoveryReport: RuntimeRecoveryReport = RuntimeRecoveryReport(emptyList()),
    onRetryRuntime: () -> Unit = {},
    runtimeEnabled: Boolean = false,
    onRuntimeEnabledChanged: (Boolean) -> Unit = {},
    onRepairPermission: (AndroidPermission) -> Unit = {},
    runtimeOverview: RuntimeMacroOverview? = null,
    onVariableChanged: (String, String, MacroValue?) -> Unit = { _, _, _ -> },
    onAddVariable: (VariableDeclarationTemplate) -> Unit = {},
    onRenameVariable: (String, String) -> Unit = { _, _ -> },
    onRemoveVariable: (String) -> Unit = {},
    onAddGroupedAction: (String, TopLevelBlockTemplate) -> Unit = { _, _ -> },
    onRemoveGroupedAction: (String) -> Unit = {},
    onMoveGroupedAction: (String, NestedActionMoveDirection) -> Unit = { _, _ -> },
    onAddTopLevelBlock: (TopLevelBlockTemplate) -> Unit = {},
    onRemoveTopLevelBlock: (String) -> Unit = {},
    onMoveTopLevelBlock: (String, NestedActionMoveDirection) -> Unit = { _, _ -> },
    onConditionGroupLogicChanged: (String, ConditionGroupLogic) -> Unit = { _, _ -> },
    onAddConditionTreeChild: (String, TopLevelBlockTemplate) -> Unit = { _, _ -> },
    onRemoveConditionTreeChild: (String) -> Unit = {},
    onWrapConditionTreeChildInNot: (String) -> Unit = {},
    onUnwrapConditionTreeNot: (String) -> Unit = {},
    workspace: WorkspacePanelState? = null,
    onChooseWorkspace: () -> Unit = {},
    onSaveToWorkspace: () -> Unit = {},
    onOverwriteWorkspaceMacro: () -> Unit = {},
    onSaveAsWorkspaceMacro: () -> Unit = {},
    onRenameWorkspaceIdentity: () -> Unit = {},
    onRefreshWorkspace: () -> Unit = {},
    onReloadWorkspaceMacro: () -> Unit = {},
    onOpenWorkspaceMacro: (String) -> Unit = {},
    onCreateWorkspaceMacro: (String) -> Unit = {},
    onRenameWorkspaceMacro: (String, String) -> Unit = { _, _ -> },
    onDeleteWorkspaceMacro: (String) -> Unit = {},
) {
    Scaffold(
        bottomBar = {
            EditorBottomBar(
                selected = state.mode,
                onSelected = onModeSelected,
            )
        },
    ) { contentPadding ->
        when (state.mode) {
            EditorMode.VISUAL -> VisualEditor(
                state = state,
                launcherApps = launcherApps,
                onApprove = onApprove,
                onConfigChanged = onConfigChanged,
                recoveryReport = recoveryReport,
                onRetryRuntime = onRetryRuntime,
                runtimeEnabled = runtimeEnabled,
                onRuntimeEnabledChanged = onRuntimeEnabledChanged,
                onRepairPermission = onRepairPermission,
                runtimeOverview = runtimeOverview,
                onVariableChanged = onVariableChanged,
                onAddVariable = onAddVariable,
                onRenameVariable = onRenameVariable,
                onRemoveVariable = onRemoveVariable,
                onAddGroupedAction = onAddGroupedAction,
                onRemoveGroupedAction = onRemoveGroupedAction,
                onMoveGroupedAction = onMoveGroupedAction,
                onAddTopLevelBlock = onAddTopLevelBlock,
                onRemoveTopLevelBlock = onRemoveTopLevelBlock,
                onMoveTopLevelBlock = onMoveTopLevelBlock,
                onConditionGroupLogicChanged = onConditionGroupLogicChanged,
                onAddConditionTreeChild = onAddConditionTreeChild,
                onRemoveConditionTreeChild = onRemoveConditionTreeChild,
                onWrapConditionTreeChildInNot = onWrapConditionTreeChildInNot,
                onUnwrapConditionTreeNot = onUnwrapConditionTreeNot,
                workspace = workspace,
                onChooseWorkspace = onChooseWorkspace,
                onSaveToWorkspace = onSaveToWorkspace,
                onOverwriteWorkspaceMacro = onOverwriteWorkspaceMacro,
                onSaveAsWorkspaceMacro = onSaveAsWorkspaceMacro,
                onRenameWorkspaceIdentity = onRenameWorkspaceIdentity,
                onRefreshWorkspace = onRefreshWorkspace,
                onReloadWorkspaceMacro = onReloadWorkspaceMacro,
                onOpenWorkspaceMacro = onOpenWorkspaceMacro,
                onCreateWorkspaceMacro = onCreateWorkspaceMacro,
                onRenameWorkspaceMacro = onRenameWorkspaceMacro,
                onDeleteWorkspaceMacro = onDeleteWorkspaceMacro,
                modifier = Modifier.padding(contentPadding),
            )
            EditorMode.CODE -> CodeEditor(
                state = state,
                onSourceChanged = onSourceChanged,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

@Composable
private fun VisualEditor(
    state: MacroEditorState,
    launcherApps: List<LauncherAppOption>,
    onApprove: () -> Unit,
    onConfigChanged: (String, String, MacroValue?) -> Unit,
    recoveryReport: RuntimeRecoveryReport,
    onRetryRuntime: () -> Unit,
    runtimeEnabled: Boolean,
    onRuntimeEnabledChanged: (Boolean) -> Unit,
    onRepairPermission: (AndroidPermission) -> Unit,
    runtimeOverview: RuntimeMacroOverview?,
    onVariableChanged: (String, String, MacroValue?) -> Unit,
    onAddVariable: (VariableDeclarationTemplate) -> Unit,
    onRenameVariable: (String, String) -> Unit,
    onRemoveVariable: (String) -> Unit,
    onAddGroupedAction: (String, TopLevelBlockTemplate) -> Unit,
    onRemoveGroupedAction: (String) -> Unit,
    onMoveGroupedAction: (String, NestedActionMoveDirection) -> Unit,
    onAddTopLevelBlock: (TopLevelBlockTemplate) -> Unit,
    onRemoveTopLevelBlock: (String) -> Unit,
    onMoveTopLevelBlock: (String, NestedActionMoveDirection) -> Unit,
    onConditionGroupLogicChanged: (String, ConditionGroupLogic) -> Unit,
    onAddConditionTreeChild: (String, TopLevelBlockTemplate) -> Unit,
    onRemoveConditionTreeChild: (String) -> Unit,
    onWrapConditionTreeChildInNot: (String) -> Unit,
    onUnwrapConditionTreeNot: (String) -> Unit,
    workspace: WorkspacePanelState?,
    onChooseWorkspace: () -> Unit,
    onSaveToWorkspace: () -> Unit,
    onOverwriteWorkspaceMacro: () -> Unit,
    onSaveAsWorkspaceMacro: () -> Unit,
    onRenameWorkspaceIdentity: () -> Unit,
    onRefreshWorkspace: () -> Unit,
    onReloadWorkspaceMacro: () -> Unit,
    onOpenWorkspaceMacro: (String) -> Unit,
    onCreateWorkspaceMacro: (String) -> Unit,
    onRenameWorkspaceMacro: (String, String) -> Unit,
    onDeleteWorkspaceMacro: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        workspace?.let {
            WorkspaceCard(
                state = it,
                onChooseWorkspace = onChooseWorkspace,
                onSaveToWorkspace = onSaveToWorkspace,
                onOverwriteWorkspaceMacro = onOverwriteWorkspaceMacro,
                onSaveAsWorkspaceMacro = onSaveAsWorkspaceMacro,
                onRenameWorkspaceIdentity = onRenameWorkspaceIdentity,
                onRefreshWorkspace = onRefreshWorkspace,
                onReloadWorkspaceMacro = onReloadWorkspaceMacro,
                onOpenWorkspaceMacro = onOpenWorkspaceMacro,
                onCreateWorkspaceMacro = onCreateWorkspaceMacro,
                onRenameWorkspaceMacro = onRenameWorkspaceMacro,
                onDeleteWorkspaceMacro = onDeleteWorkspaceMacro,
            )
        }
        EditorHeader(state.visibleProposal)
        ProposalStatus(state)
        RuntimeRecoveryCard(
            recoveryReport,
            onRetryRuntime,
            onRepairPermission,
        )
        RuntimeControlCard(
            runtimeEnabled,
            runtimeOverview,
            onRuntimeEnabledChanged,
        )

        val proposal = state.visibleProposal
        if (proposal == null) {
            EmptyVisualState()
        } else {
            if (
                proposal.comparison.approvalRequired &&
                state.result is ProposalResult.Ready
            ) {
                BehaviorChangesCard(proposal = proposal, onApprove = onApprove)
            }
            VariableLaneCard(
                variables = proposal.source.document.variables,
                onVariableChanged = onVariableChanged,
                onAddVariable = onAddVariable,
                onRenameVariable = onRenameVariable,
                onRemoveVariable = onRemoveVariable,
            )
            LaneCard(
                title = "Triggers",
                subtitle = "Any trigger can start this macro",
                lane = CapabilityLane.TRIGGER,
                blocks = proposal.explanation.blocksIn(CapabilityLane.TRIGGER).filter {
                    block -> proposal.source.document.triggers.any { it.id == block.blockId }
                },
                document = proposal.source.document,
                launcherApps = launcherApps,
                onConfigChanged = onConfigChanged,
                onAddGroupedAction = onAddGroupedAction,
                onRemoveGroupedAction = onRemoveGroupedAction,
                onMoveGroupedAction = onMoveGroupedAction,
                onAddTopLevelBlock = onAddTopLevelBlock,
                onRemoveTopLevelBlock = onRemoveTopLevelBlock,
                onMoveTopLevelBlock = onMoveTopLevelBlock,
            )
            proposal.source.document.conditionTree?.let { tree ->
                ConditionTreeCard(
                    document = proposal.source.document,
                    tree = tree,
                    onConditionGroupLogicChanged = onConditionGroupLogicChanged,
                    onAddConditionTreeChild = onAddConditionTreeChild,
                    onRemoveConditionTreeChild = onRemoveConditionTreeChild,
                    onWrapConditionTreeChildInNot = onWrapConditionTreeChildInNot,
                    onUnwrapConditionTreeNot = onUnwrapConditionTreeNot,
                )
            }
            LaneCard(
                title = "Conditions",
                subtitle = "Every condition must pass",
                lane = CapabilityLane.CONDITION,
                blocks = proposal.explanation.blocksIn(CapabilityLane.CONDITION).filter {
                    block -> proposal.source.document.conditions.any { it.id == block.blockId }
                },
                document = proposal.source.document,
                launcherApps = launcherApps,
                onConfigChanged = onConfigChanged,
                onAddGroupedAction = onAddGroupedAction,
                onRemoveGroupedAction = onRemoveGroupedAction,
                onMoveGroupedAction = onMoveGroupedAction,
                onAddTopLevelBlock = onAddTopLevelBlock,
                onRemoveTopLevelBlock = onRemoveTopLevelBlock,
                onMoveTopLevelBlock = onMoveTopLevelBlock,
            )
            LaneCard(
                title = "Actions",
                subtitle = "Actions run from top to bottom",
                lane = CapabilityLane.ACTION,
                blocks = proposal.explanation.blocksIn(CapabilityLane.ACTION).filter {
                    block -> proposal.source.document.actions.any { it.id == block.blockId }
                },
                document = proposal.source.document,
                launcherApps = launcherApps,
                onConfigChanged = onConfigChanged,
                onAddGroupedAction = onAddGroupedAction,
                onRemoveGroupedAction = onRemoveGroupedAction,
                onMoveGroupedAction = onMoveGroupedAction,
                onAddTopLevelBlock = onAddTopLevelBlock,
                onRemoveTopLevelBlock = onRemoveTopLevelBlock,
                onMoveTopLevelBlock = onMoveTopLevelBlock,
            )
            PermissionCard(proposal)
        }
    }
}

data class WorkspacePanelState(
    val folderLabel: String?,
    val macroIds: List<String>,
    val activeMacroId: String,
    val trackedMacroId: String?,
    val editorMacroId: String,
    val status: String?,
    val hasUnsavedChanges: Boolean,
    val externalChange: WorkspaceExternalChange,
)

@Composable
private fun WorkspaceCard(
    state: WorkspacePanelState,
    onChooseWorkspace: () -> Unit,
    onSaveToWorkspace: () -> Unit,
    onOverwriteWorkspaceMacro: () -> Unit,
    onSaveAsWorkspaceMacro: () -> Unit,
    onRenameWorkspaceIdentity: () -> Unit,
    onRefreshWorkspace: () -> Unit,
    onReloadWorkspaceMacro: () -> Unit,
    onOpenWorkspaceMacro: (String) -> Unit,
    onCreateWorkspaceMacro: (String) -> Unit,
    onRenameWorkspaceMacro: (String, String) -> Unit,
    onDeleteWorkspaceMacro: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var draftMacroId by remember { mutableStateOf("") }
    var pendingEditorReplacement by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingWorkspaceOverwrite by remember { mutableStateOf(false) }
    var pendingIdentityChoice by remember { mutableStateOf(false) }
    fun replaceEditor(action: () -> Unit) {
        if (state.hasUnsavedChanges) {
            pendingEditorReplacement = action
        } else {
            action()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Workspace", fontWeight = FontWeight.Bold)
            Text(
                state.folderLabel ?: "No folder selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.folderLabel != null) {
                Text(
                    if (state.hasUnsavedChanges) {
                        "Unsaved workspace changes"
                    } else {
                        "Saved to workspace"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.hasUnsavedChanges) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onChooseWorkspace) {
                    Text("Choose folder")
                }
                Button(
                    onClick = {
                        if (
                            state.trackedMacroId != null &&
                            state.trackedMacroId != state.editorMacroId
                        ) {
                            pendingIdentityChoice = true
                        } else if (
                            state.externalChange == WorkspaceExternalChange.MODIFIED ||
                            state.externalChange == WorkspaceExternalChange.MISSING ||
                            state.externalChange == WorkspaceExternalChange.INVALID ||
                            state.externalChange == WorkspaceExternalChange.EXISTS
                        ) {
                            pendingWorkspaceOverwrite = true
                        } else {
                            onSaveToWorkspace()
                        }
                    },
                    enabled = state.folderLabel != null,
                ) {
                    Text("Save macro")
                }
            }
            TextButton(
                onClick = onRefreshWorkspace,
                enabled = state.folderLabel != null,
            ) {
                Text("Refresh workspace")
            }
            when (state.externalChange) {
                WorkspaceExternalChange.NONE -> Unit
                WorkspaceExternalChange.MODIFIED -> {
                    Text(
                        "The active macro changed outside this editor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = {
                            replaceEditor(onReloadWorkspaceMacro)
                        },
                    ) {
                        Text("Reload workspace file")
                    }
                }
                WorkspaceExternalChange.MISSING -> {
                    Text(
                        "The active macro was removed from the workspace. Saving will recreate it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                WorkspaceExternalChange.INVALID -> {
                    Text(
                        "The active workspace file changed and is not valid OpenMacro source.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                WorkspaceExternalChange.EXISTS -> {
                    Text(
                        "A different workspace macro already uses the editor's current id.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Button(
                onClick = {
                    draftMacroId = ""
                    creating = true
                },
                enabled = state.folderLabel != null,
            ) {
                Text("New macro")
            }
            state.status?.let { status ->
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.macroIds.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.macroIds.forEach { macroId ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = {
                                    replaceEditor { onOpenWorkspaceMacro(macroId) }
                                },
                                enabled = macroId != state.activeMacroId,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(macroId)
                            }
                            TextButton(
                                onClick = {
                                    draftMacroId = macroId
                                    renameTarget = macroId
                                },
                            ) {
                                Text("Rename")
                            }
                            TextButton(onClick = { deleteTarget = macroId }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        MacroIdDialog(
            title = "New workspace macro",
            confirmation = "Create",
            macroId = draftMacroId,
            onMacroIdChanged = { draftMacroId = it },
            onDismiss = { creating = false },
            onConfirm = {
                creating = false
                replaceEditor { onCreateWorkspaceMacro(draftMacroId) }
            },
        )
    }
    renameTarget?.let { oldId ->
        MacroIdDialog(
            title = "Rename $oldId",
            confirmation = "Rename",
            macroId = draftMacroId,
            onMacroIdChanged = { draftMacroId = it },
            onDismiss = { renameTarget = null },
            onConfirm = {
                renameTarget = null
                val rename = { onRenameWorkspaceMacro(oldId, draftMacroId) }
                if (oldId == state.activeMacroId) {
                    replaceEditor(rename)
                } else {
                    rename()
                }
            },
        )
    }
    deleteTarget?.let { macroId ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete $macroId?") },
            text = {
                Text(
                    "This removes only the workspace source file. Any approved or " +
                        "running revision stays in the app until you disable or replace it.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDeleteWorkspaceMacro(macroId)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
    pendingEditorReplacement?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingEditorReplacement = null },
            title = { Text("Discard unsaved changes?") },
            text = {
                Text("The current editor differs from its last workspace save.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingEditorReplacement = null
                        action()
                    },
                ) {
                    Text("Discard and continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEditorReplacement = null }) {
                    Text("Keep editing")
                }
            },
        )
    }
    if (pendingWorkspaceOverwrite) {
        AlertDialog(
            onDismissRequest = { pendingWorkspaceOverwrite = false },
            title = {
                Text(
                    if (state.externalChange == WorkspaceExternalChange.MISSING) {
                        "Recreate workspace file?"
                    } else {
                        "Overwrite workspace conflict?"
                    },
                )
            },
            text = {
                Text(
                    if (state.externalChange == WorkspaceExternalChange.MISSING) {
                        "Saving now recreates the removed file from this editor copy."
                    } else {
                        "Saving now replaces the conflicting workspace file with this editor copy."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingWorkspaceOverwrite = false
                        onOverwriteWorkspaceMacro()
                    },
                ) {
                    Text(
                        if (state.externalChange == WorkspaceExternalChange.MISSING) {
                            "Recreate and save"
                        } else {
                            "Overwrite and save"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingWorkspaceOverwrite = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    if (pendingIdentityChoice) {
        AlertDialog(
            onDismissRequest = { pendingIdentityChoice = false },
            title = { Text("Save changed macro id") },
            text = {
                Text(
                    "The workspace file is '${state.trackedMacroId}', while this editor " +
                        "declares '${state.editorMacroId}'. Save as new keeps both files. " +
                        "Rename file removes the old workspace source after saving this one. " +
                        "Approvals and runtime state are not renamed.",
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            pendingIdentityChoice = false
                            onRenameWorkspaceIdentity()
                        },
                    ) {
                        Text("Rename file")
                    }
                    TextButton(
                        onClick = {
                            pendingIdentityChoice = false
                            onSaveAsWorkspaceMacro()
                        },
                    ) {
                        Text("Save as new")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingIdentityChoice = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MacroIdDialog(
    title: String,
    confirmation: String,
    macroId: String,
    onMacroIdChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = macroId,
                onValueChange = onMacroIdChanged,
                label = { Text("Macro id") },
                supportingText = {
                    Text("Use lowercase letters, numbers, and hyphens.")
                },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = macroId.isNotBlank(),
            ) {
                Text(confirmation)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EditorHeader(proposal: OpenMacroProposal?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = proposal?.explanation?.name ?: "OpenMacro editor",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Simple on the surface. Exact underneath.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProposalStatus(state: MacroEditorState) {
    val ready = state.result as? ProposalResult.Ready
    val color = when {
        state.formEditError != null -> MaterialTheme.colorScheme.errorContainer
        state.problems.isNotEmpty() -> MaterialTheme.colorScheme.errorContainer
        ready?.proposal?.comparison?.approvalRequired == true ->
            MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val title = when {
        state.formEditError != null -> "Visual edit could not be applied"
        state.problems.isNotEmpty() -> "Code needs attention"
        ready?.proposal?.comparison?.approvalRequired == true -> "Review required"
        else -> "Matches approved behavior"
    }
    val detail = when {
        state.formEditError != null -> state.formEditError
        state.visualIsStale ->
            "The visual view shows the last valid version while the code is corrected."
        state.problems.isNotEmpty() ->
            state.problems.first().message
        ready?.proposal?.comparison?.approvalRequired == true ->
            "Runnable behavior changed. Explain and approve it before enabling."
        else ->
            "Source and approved runtime behavior are aligned."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LaneCard(
    title: String,
    subtitle: String,
    lane: CapabilityLane,
    blocks: List<BlockExplanation>,
    document: OpenMacroDocument,
    launcherApps: List<LauncherAppOption>,
    onConfigChanged: (String, String, MacroValue?) -> Unit,
    onAddGroupedAction: (String, TopLevelBlockTemplate) -> Unit,
    onRemoveGroupedAction: (String) -> Unit,
    onMoveGroupedAction: (String, NestedActionMoveDirection) -> Unit,
    onAddTopLevelBlock: (TopLevelBlockTemplate) -> Unit,
    onRemoveTopLevelBlock: (String) -> Unit,
    onMoveTopLevelBlock: (String, NestedActionMoveDirection) -> Unit,
) {
    val registry = remember { CapabilityRegistry.builtIn() }
    val templates = MacroBlockEditor.topLevelTemplates(registry, lane, document)
    var addPickerOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = blocks.size.toString(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (lane != CapabilityLane.CONDITION || document.conditionTree == null) {
                Button(
                    onClick = { addPickerOpen = true },
                    enabled = templates.isNotEmpty(),
                ) {
                    Text("Add ${lane.name.lowercase()}")
                }
            }

            blocks.forEachIndexed { index, block ->
                BlockCard(position = index + 1, block = block)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            onMoveTopLevelBlock(block.blockId, NestedActionMoveDirection.UP)
                        },
                        enabled = index > 0,
                    ) {
                        Text("Move up")
                    }
                    TextButton(
                        onClick = {
                            onMoveTopLevelBlock(block.blockId, NestedActionMoveDirection.DOWN)
                        },
                        enabled = index < blocks.lastIndex,
                    ) {
                        Text("Move down")
                    }
                    TextButton(
                        onClick = { onRemoveTopLevelBlock(block.blockId) },
                        enabled = lane == CapabilityLane.CONDITION || blocks.size > 1,
                    ) {
                        Text("Remove")
                    }
                }
                MacroBlockEditor.findBlock(document, block.blockId)?.let { modelBlock ->
                    CapabilityForm(
                        document = document,
                        block = modelBlock,
                        launcherApps = launcherApps,
                        onConfigChanged = onConfigChanged,
                    )
                    if (block.lane == CapabilityLane.ACTION) {
                        NestedActionForms(
                            document = document,
                            launcherApps = launcherApps,
                            parent = modelBlock,
                            onConfigChanged = onConfigChanged,
                            onAddGroupedAction = onAddGroupedAction,
                            onRemoveGroupedAction = onRemoveGroupedAction,
                            onMoveGroupedAction = onMoveGroupedAction,
                        )
                    }
                }
            }
    }

    if (addPickerOpen) {
        CapabilityAddPicker(
            lane = lane,
            templates = templates,
            registry = registry,
            document = document,
            launcherApps = launcherApps,
            onDismiss = { addPickerOpen = false },
            onSelected = { template ->
                addPickerOpen = false
                onAddTopLevelBlock(template)
            },
        )
    }
}

@Composable
private fun CapabilityAddPicker(
    lane: CapabilityLane,
    templates: List<TopLevelBlockTemplate>,
    registry: CapabilityRegistry,
    document: OpenMacroDocument,
    launcherApps: List<LauncherAppOption> = emptyList(),
    onDismiss: () -> Unit,
    onSelected: (TopLevelBlockTemplate) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var setupTemplate by remember { mutableStateOf<TopLevelBlockTemplate?>(null) }
    val filtered = MacroBlockEditor.filterTopLevelTemplates(templates, query)
    val selectedSetup = setupTemplate
    if (selectedSetup != null) {
        CapabilitySetupDialog(
            template = selectedSetup,
            registry = registry,
            document = document,
            launcherApps = launcherApps,
            onDismiss = { setupTemplate = null },
            onConfigured = onSelected,
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add ${lane.name.lowercase()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (filtered.isEmpty()) {
                            Text(
                                text = "No matching capabilities.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        filtered.forEach { template ->
                            TextButton(
                                onClick = {
                                    if (template.setupRequired) {
                                        setupTemplate = template
                                    } else {
                                        onSelected(template)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = template.label,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = template.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun CapabilitySetupDialog(
    template: TopLevelBlockTemplate,
    registry: CapabilityRegistry,
    document: OpenMacroDocument,
    launcherApps: List<LauncherAppOption>,
    onDismiss: () -> Unit,
    onConfigured: (TopLevelBlockTemplate) -> Unit,
) {
    var config by remember(template.type) {
        mutableStateOf(
            template.setupFields.fold(template.defaultConfig) { current, field ->
                if (field.key in current || !field.required) {
                    current
                } else {
                    current + (field.key to field.initialSetupValue())
                }
            },
        )
    }
    var error by remember(template.type) { mutableStateOf<String?>(null) }
    val block = template.copy(defaultConfig = config, setupRequired = false).block(template.idBase)
    val form = CapabilityFormModelFactory(registry).create(document, block)
    val requiredAccess = MacroBlockEditor.setupRequiredPermissions(
        registry,
        document,
        template,
        config,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(template.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                form?.fields
                    ?.filter { field -> template.setupFields.any { it.key == field.key } }
                    ?.forEach { field ->
                        CapabilityFieldEditor(
                            blockId = block.id,
                            field = field,
                            launcherApps = launcherApps,
                            onConfigChanged = { _, key, value ->
                                config = if (value == null) config - key else config + (key to value)
                                error = null
                            },
                        )
                    }
                if (form == null) {
                    Text(
                        "This capability cannot be configured.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (requiredAccess.isNotEmpty()) {
                    Text("Required access", fontWeight = FontWeight.SemiBold)
                    requiredAccess.sortedBy(AndroidPermission::name).forEach { permission ->
                        Text(
                            permission.userFacingName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (
                        val result = MacroBlockEditor.configureTemplate(
                            registry,
                            document,
                            template,
                            config,
                        )
                    ) {
                        is TemplateConfigurationResult.Configured -> onConfigured(result.template)
                        is TemplateConfigurationResult.Rejected -> error = result.message
                    }
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Back")
            }
        },
    )
}

@Composable
private fun LauncherAppPickerDialog(
    apps: List<LauncherAppOption>,
    onDismiss: () -> Unit,
    onSelected: (LauncherAppOption) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = filterLauncherApps(apps, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "No matching apps.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    items(filtered, key = LauncherAppOption::packageName) { app ->
                        TextButton(
                            onClick = { onSelected(app) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

private fun com.vibhor1102.zerobit.openmacro.capability.CapabilityField.initialSetupValue(): MacroValue =
    when (kind) {
        CapabilityFieldKind.NUMBER -> MacroValue.Number(java.math.BigDecimal.ZERO)
        CapabilityFieldKind.BOOLEAN -> MacroValue.Boolean(false)
        CapabilityFieldKind.TEXT_LIST -> MacroValue.ListValue(emptyList())
        else -> MacroValue.Text("")
    }

@Composable
private fun ConditionTreeCard(
    document: OpenMacroDocument,
    tree: MacroConditionNode,
    onConditionGroupLogicChanged: (String, ConditionGroupLogic) -> Unit,
    onAddConditionTreeChild: (String, TopLevelBlockTemplate) -> Unit,
    onRemoveConditionTreeChild: (String) -> Unit,
    onWrapConditionTreeChildInNot: (String) -> Unit,
    onUnwrapConditionTreeNot: (String) -> Unit,
) {
    val registry = remember { CapabilityRegistry.builtIn() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Condition logic", fontWeight = FontWeight.Bold)
            ConditionTreeNode(
                node = tree,
                path = "root",
                depth = 0,
                registry = registry,
                document = document,
                canRemove = false,
                canWrapOrUnwrap = false,
                onConditionGroupLogicChanged = onConditionGroupLogicChanged,
                onAddConditionTreeChild = onAddConditionTreeChild,
                onRemoveConditionTreeChild = onRemoveConditionTreeChild,
                onWrapConditionTreeChildInNot = onWrapConditionTreeChildInNot,
                onUnwrapConditionTreeNot = onUnwrapConditionTreeNot,
            )
        }
    }
}

@Composable
private fun ConditionTreeNode(
    node: MacroConditionNode,
    path: String,
    depth: Int,
    registry: CapabilityRegistry,
    document: OpenMacroDocument,
    canRemove: Boolean,
    canWrapOrUnwrap: Boolean,
    onConditionGroupLogicChanged: (String, ConditionGroupLogic) -> Unit,
    onAddConditionTreeChild: (String, TopLevelBlockTemplate) -> Unit,
    onRemoveConditionTreeChild: (String) -> Unit,
    onWrapConditionTreeChildInNot: (String) -> Unit,
    onUnwrapConditionTreeNot: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = (depth * 12).dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (node) {
            is MacroConditionNode.All -> {
                ConditionGroupControls(
                    label = "AND group",
                    path = path,
                    selected = ConditionGroupLogic.AND,
                    canRemove = canRemove,
                    canWrap = canWrapOrUnwrap,
                    registry = registry,
                    document = document,
                    onConditionGroupLogicChanged = onConditionGroupLogicChanged,
                    onAddConditionTreeChild = onAddConditionTreeChild,
                    onRemoveConditionTreeChild = onRemoveConditionTreeChild,
                    onWrapConditionTreeChildInNot = onWrapConditionTreeChildInNot,
                )
                node.children.forEachIndexed { index, child ->
                    ConditionTreeNode(
                        node = child,
                        path = "$path.$index",
                        depth = depth + 1,
                        registry = registry,
                        document = document,
                        canRemove = node.children.size > 1,
                        canWrapOrUnwrap = true,
                        onConditionGroupLogicChanged = onConditionGroupLogicChanged,
                        onAddConditionTreeChild = onAddConditionTreeChild,
                        onRemoveConditionTreeChild = onRemoveConditionTreeChild,
                        onWrapConditionTreeChildInNot = onWrapConditionTreeChildInNot,
                        onUnwrapConditionTreeNot = onUnwrapConditionTreeNot,
                    )
                }
            }
            is MacroConditionNode.Any -> {
                ConditionGroupControls(
                    label = "OR group",
                    path = path,
                    selected = ConditionGroupLogic.OR,
                    canRemove = canRemove,
                    canWrap = canWrapOrUnwrap,
                    registry = registry,
                    document = document,
                    onConditionGroupLogicChanged = onConditionGroupLogicChanged,
                    onAddConditionTreeChild = onAddConditionTreeChild,
                    onRemoveConditionTreeChild = onRemoveConditionTreeChild,
                    onWrapConditionTreeChildInNot = onWrapConditionTreeChildInNot,
                )
                node.children.forEachIndexed { index, child ->
                    ConditionTreeNode(
                        node = child,
                        path = "$path.$index",
                        depth = depth + 1,
                        registry = registry,
                        document = document,
                        canRemove = node.children.size > 1,
                        canWrapOrUnwrap = true,
                        onConditionGroupLogicChanged = onConditionGroupLogicChanged,
                        onAddConditionTreeChild = onAddConditionTreeChild,
                        onRemoveConditionTreeChild = onRemoveConditionTreeChild,
                        onWrapConditionTreeChildInNot = onWrapConditionTreeChildInNot,
                        onUnwrapConditionTreeNot = onUnwrapConditionTreeNot,
                    )
                }
            }
            is MacroConditionNode.Not -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("NOT", fontWeight = FontWeight.SemiBold)
                    if (canRemove) {
                        TextButton(onClick = { onRemoveConditionTreeChild(path) }) {
                            Text("Remove")
                        }
                    }
                    if (canWrapOrUnwrap) {
                        TextButton(onClick = { onUnwrapConditionTreeNot(path) }) {
                            Text("Unwrap")
                        }
                    }
                }
                ConditionTreeNode(
                    node = node.child,
                    path = "$path.not",
                    depth = depth + 1,
                    registry = registry,
                    document = document,
                    canRemove = false,
                    canWrapOrUnwrap = false,
                    onConditionGroupLogicChanged = onConditionGroupLogicChanged,
                    onAddConditionTreeChild = onAddConditionTreeChild,
                    onRemoveConditionTreeChild = onRemoveConditionTreeChild,
                    onWrapConditionTreeChildInNot = onWrapConditionTreeChildInNot,
                    onUnwrapConditionTreeNot = onUnwrapConditionTreeNot,
                )
            }
            is MacroConditionNode.Condition -> {
                val definition = registry.find(node.block.type)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        definition?.displayName ?: node.block.type,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (canRemove) {
                        TextButton(onClick = { onRemoveConditionTreeChild(path) }) {
                            Text("Remove")
                        }
                    }
                    if (canWrapOrUnwrap) {
                        TextButton(onClick = { onWrapConditionTreeChildInNot(path) }) {
                            Text("Wrap in NOT")
                        }
                    }
                }
                Text(
                    definition?.explain(node.block) ?: node.block.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConditionGroupControls(
    label: String,
    path: String,
    selected: ConditionGroupLogic,
    canRemove: Boolean,
    canWrap: Boolean,
    registry: CapabilityRegistry,
    document: OpenMacroDocument,
    onConditionGroupLogicChanged: (String, ConditionGroupLogic) -> Unit,
    onAddConditionTreeChild: (String, TopLevelBlockTemplate) -> Unit,
    onRemoveConditionTreeChild: (String) -> Unit,
    onWrapConditionTreeChildInNot: (String) -> Unit,
) {
    val conditionTemplates = MacroBlockEditor.conditionTreeChildTemplates(
        registry,
        document,
        path,
    )
    var addPickerOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, fontWeight = FontWeight.SemiBold)
            if (canRemove) {
                TextButton(onClick = { onRemoveConditionTreeChild(path) }) {
                    Text("Remove")
                }
            }
            if (canWrap) {
                TextButton(onClick = { onWrapConditionTreeChildInNot(path) }) {
                    Text("Wrap in NOT")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ConditionGroupLogic.values().forEach { logic ->
                TextButton(
                    onClick = { onConditionGroupLogicChanged(path, logic) },
                    enabled = logic != selected,
                ) {
                    Text(logic.label)
                }
            }
        }
        Button(
            onClick = { addPickerOpen = true },
            enabled = conditionTemplates.isNotEmpty(),
        ) {
            Text("Add condition")
        }
    }
    if (addPickerOpen) {
        CapabilityAddPicker(
            lane = CapabilityLane.CONDITION,
            templates = conditionTemplates,
            registry = registry,
            document = document,
            onDismiss = { addPickerOpen = false },
            onSelected = { template ->
                addPickerOpen = false
                onAddConditionTreeChild(path, template)
            },
        )
    }
}

@Composable
private fun NestedActionForms(
    document: OpenMacroDocument,
    launcherApps: List<LauncherAppOption>,
    parent: MacroBlock,
    onConfigChanged: (String, String, MacroValue?) -> Unit,
    onAddGroupedAction: (String, TopLevelBlockTemplate) -> Unit,
    onRemoveGroupedAction: (String) -> Unit,
    onMoveGroupedAction: (String, NestedActionMoveDirection) -> Unit,
) {
    val children = MacroBlockEditor.nestedActions(parent)
    if (children.isEmpty() && parent.type != "openmacro.action.group") {
        return
    }
    val registry = remember { CapabilityRegistry.builtIn() }
    val templates = MacroBlockEditor.groupedActionTemplates(registry, document, parent.id)
    var addPickerOpen by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 18.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (parent.type == "openmacro.action.group") {
                Button(
                    onClick = { addPickerOpen = true },
                    enabled = templates.isNotEmpty(),
                ) {
                    Text("Add action")
                }
            }
            children.forEachIndexed { index, child ->
                val definition = registry.find(child.type)
                Text(
                    "${index + 1}. ${definition?.displayName ?: child.type}",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    definition?.explain(child) ?: child.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            onMoveGroupedAction(child.id, NestedActionMoveDirection.UP)
                        },
                        enabled = index > 0,
                    ) {
                        Text("Move up")
                    }
                    TextButton(
                        onClick = {
                            onMoveGroupedAction(child.id, NestedActionMoveDirection.DOWN)
                        },
                        enabled = index < children.lastIndex,
                    ) {
                        Text("Move down")
                    }
                    TextButton(
                        onClick = { onRemoveGroupedAction(child.id) },
                        enabled = children.size > 1,
                    ) {
                        Text("Remove")
                    }
                }
                CapabilityForm(
                    document = document,
                    block = child,
                    launcherApps = launcherApps,
                    onConfigChanged = onConfigChanged,
                )
                NestedActionForms(
                    document = document,
                    launcherApps = launcherApps,
                    parent = child,
                    onConfigChanged = onConfigChanged,
                    onAddGroupedAction = onAddGroupedAction,
                    onRemoveGroupedAction = onRemoveGroupedAction,
                    onMoveGroupedAction = onMoveGroupedAction,
                )
            }
        }
    }
    if (addPickerOpen) {
        CapabilityAddPicker(
            lane = CapabilityLane.ACTION,
            templates = templates,
            registry = registry,
            document = document,
            launcherApps = launcherApps,
            onDismiss = { addPickerOpen = false },
            onSelected = { template ->
                addPickerOpen = false
                onAddGroupedAction(parent.id, template)
            },
        )
    }
}

@Composable
private fun VariableLaneCard(
    variables: List<MacroVariable>,
    onVariableChanged: (String, String, MacroValue?) -> Unit,
    onAddVariable: (VariableDeclarationTemplate) -> Unit,
    onRenameVariable: (String, String) -> Unit,
    onRemoveVariable: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Local values", fontWeight = FontWeight.Bold)
            Text(
                "Values stay on this device; secret declarations store only a local key name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MacroBlockEditor.variableTemplates().forEach { template ->
                    TextButton(onClick = { onAddVariable(template) }) {
                        Text("Add ${template.label.lowercase()}")
                    }
                }
            }
            variables.forEach { variable ->
                VariableEditor(
                    variable = variable,
                    onVariableChanged = onVariableChanged,
                    onRenameVariable = onRenameVariable,
                    onRemoveVariable = onRemoveVariable,
                )
            }
        }
    }
}

@Composable
private fun VariableEditor(
    variable: MacroVariable,
    onVariableChanged: (String, String, MacroValue?) -> Unit,
    onRenameVariable: (String, String) -> Unit,
    onRemoveVariable: (String) -> Unit,
) {
    var proposedName by remember(variable.name) { mutableStateOf(variable.name) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${variable.name} · ${variable.type.name.lowercase()}",
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = proposedName,
            onValueChange = { proposedName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
        )
        TextButton(
            onClick = { onRenameVariable(variable.name, proposedName) },
            enabled = proposedName.trim() != variable.name,
        ) {
            Text("Rename variable")
        }
        when (variable.type) {
            MacroVariableType.TEXT -> {
                val value = (variable.initialValue as? MacroValue.Text)?.value
                if (value == null) {
                    TextButton(
                        onClick = {
                            onVariableChanged(
                                variable.name,
                                "initial",
                                MacroValue.Text(""),
                            )
                        },
                    ) {
                        Text("Add initial text")
                    }
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            onVariableChanged(
                                variable.name,
                                "initial",
                                MacroValue.Text(it),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Initial value") },
                    )
                    TextButton(
                        onClick = {
                            onVariableChanged(variable.name, "initial", null)
                        },
                    ) {
                        Text("Remove initial value")
                    }
                }
            }
            MacroVariableType.NUMBER -> {
                val value = (variable.initialValue as? MacroValue.Number)
                    ?.value
                    ?.toPlainString()
                if (value == null) {
                    TextButton(
                        onClick = {
                            onVariableChanged(
                                variable.name,
                                "initial",
                                MacroValue.Number(java.math.BigDecimal.ZERO),
                            )
                        },
                    ) {
                        Text("Add initial number")
                    }
                } else {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { text ->
                            text.toBigDecimalOrNull()?.let {
                                onVariableChanged(
                                    variable.name,
                                    "initial",
                                    MacroValue.Number(it),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Initial value") },
                    )
                    TextButton(
                        onClick = {
                            onVariableChanged(variable.name, "initial", null)
                        },
                    ) {
                        Text("Remove initial value")
                    }
                }
            }
            MacroVariableType.BOOLEAN -> {
                val value = (variable.initialValue as? MacroValue.Boolean)?.value
                FilledTonalButton(
                    onClick = {
                        onVariableChanged(
                            variable.name,
                            "initial",
                            MacroValue.Boolean(!(value ?: false)),
                        )
                    },
                ) {
                    Text(
                        when (value) {
                            true -> "Initial value: On"
                            false -> "Initial value: Off"
                            null -> "Add initial value: Off"
                        },
                    )
                }
                if (value != null) {
                    TextButton(
                        onClick = {
                            onVariableChanged(variable.name, "initial", null)
                        },
                    ) {
                        Text("Remove initial value")
                    }
                }
            }
            MacroVariableType.SECRET -> {
                OutlinedTextField(
                    value = variable.secretKey.orEmpty(),
                    onValueChange = {
                        if (it.isNotBlank()) {
                            onVariableChanged(
                                variable.name,
                                "secret_key",
                                MacroValue.Text(it),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Local secret key") },
                    supportingText = {
                        Text("This identifies a secret; it is never the secret value.")
                    },
                )
            }
        }
        TextButton(onClick = { onRemoveVariable(variable.name) }) {
            Text("Delete variable")
        }
    }
}

@Composable
private fun RuntimeControlCard(
    enabled: Boolean,
    overview: RuntimeMacroOverview?,
    onEnabledChanged: (Boolean) -> Unit,
) {
    var showDiagnostics by remember { mutableStateOf(false) }
    Surface(
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (enabled) "Automation enabled" else "Automation disabled",
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (enabled) {
                        "The approved revision is listening for triggers."
                    } else {
                        "Approve the macro, then enable it when you are ready."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                overview?.active?.let { active ->
                    Text(
                        "Revision ${active.revisionId}; ${active.triggerCount} trigger subscription(s).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                overview?.lastEvent?.let { event ->
                    Text(
                        "Last runtime event: ${event.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (overview?.recentEvents?.isNotEmpty() == true) {
                    TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                        Text(
                            if (showDiagnostics) {
                                "Hide recent runtime events"
                            } else {
                                "Show recent runtime events"
                            },
                        )
                    }
                    if (showDiagnostics) {
                        overview.recentEvents.takeLast(8).forEach { event ->
                            Text(
                                buildString {
                                    append(event.kind.name.lowercase().replace('_', ' '))
                                    event.blockId?.let {
                                        append(" · ")
                                        append(it)
                                    }
                                    append(": ")
                                    append(event.message)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            FilledTonalButton(onClick = { onEnabledChanged(!enabled) }) {
                Text(if (enabled) "Disable" else "Enable")
            }
        }
    }
}

@Composable
private fun RuntimeRecoveryCard(
    report: RuntimeRecoveryReport,
    onRetry: () -> Unit,
    onRepairPermission: (AndroidPermission) -> Unit,
) {
    if (!report.needsAttention) {
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Some automations need attention", fontWeight = FontWeight.Bold)
            report.macros
                .filterNot { it is MacroRecoveryStatus.Running }
                .forEach { status ->
                    Text(
                        "${status.macroId}: ${status.explanation}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (status is MacroRecoveryStatus.AccessRequired) {
                        status.permissions
                            .sortedBy(AndroidPermission::name)
                            .forEach { permission ->
                                TextButton(
                                    onClick = { onRepairPermission(permission) },
                                ) {
                                    Text("Fix ${permission.userFacingName()}")
                                }
                            }
                    }
                }
            FilledTonalButton(onClick = onRetry) {
                Text("Retry after fixing access")
            }
        }
    }
}

@Composable
private fun CapabilityForm(
    document: OpenMacroDocument,
    block: MacroBlock,
    launcherApps: List<LauncherAppOption>,
    onConfigChanged: (String, String, MacroValue?) -> Unit,
) {
    val factory = remember {
        CapabilityFormModelFactory(CapabilityRegistry.builtIn())
    }
    val form = factory.create(document, block) ?: return
    if (form.fields.isEmpty()) {
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Configure", fontWeight = FontWeight.SemiBold)
            form.fields.forEach { field ->
                CapabilityFieldEditor(
                    blockId = block.id,
                    field = field,
                    launcherApps = launcherApps,
                    onConfigChanged = onConfigChanged,
                )
            }
        }
    }
}

@Composable
private fun CapabilityFieldEditor(
    blockId: String,
    field: CapabilityFormField,
    launcherApps: List<LauncherAppOption> = emptyList(),
    onConfigChanged: (String, String, MacroValue?) -> Unit,
) {
    var referencePickerOpen by remember(blockId, field.key) { mutableStateOf(false) }
    var appPickerOpen by remember(blockId, field.key) { mutableStateOf(false) }
    var timezonePickerOpen by remember(blockId, field.key) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (val current = field.currentValue) {
            is MacroValue.Text -> OutlinedTextField(
                value = current.value,
                onValueChange = {
                    onConfigChanged(blockId, field.key, MacroValue.Text(it))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(field.label) },
                supportingText = { Text(field.help) },
            )
            is MacroValue.Number -> OutlinedTextField(
                value = current.value.toPlainString(),
                onValueChange = { text ->
                    text.toBigDecimalOrNull()?.let {
                        onConfigChanged(blockId, field.key, MacroValue.Number(it))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(field.label) },
                supportingText = { Text(field.help) },
            )
            is MacroValue.Boolean -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(field.label, fontWeight = FontWeight.Medium)
                    Text(
                        field.help,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = {
                        onConfigChanged(
                            blockId,
                            field.key,
                            MacroValue.Boolean(!current.value),
                        )
                    },
                ) {
                    Text(if (current.value) "On" else "Off")
                }
            }
            is MacroValue.ObjectValue -> {
                Text(
                    text = "${field.label}: ${current.referenceDescription()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (
                    field.kind == CapabilityFieldKind.TEXT ||
                    field.kind == CapabilityFieldKind.MULTILINE_TEXT
                ) {
                    TextButton(
                        onClick = {
                            onConfigChanged(
                                blockId,
                                field.key,
                                MacroValue.Text(""),
                            )
                        },
                    ) {
                        Text("Use literal text")
                    }
                }
            }
            is MacroValue.ListValue -> Text(
                text = "${field.label}: ${current.values.joinToString { it.displayValue() }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            MacroValue.Null,
            null -> {
                Text(field.label, fontWeight = FontWeight.Medium)
                Text(
                    field.help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (
                    field.kind == CapabilityFieldKind.TEXT ||
                    field.kind == CapabilityFieldKind.MULTILINE_TEXT ||
                    field.kind == CapabilityFieldKind.VALUE ||
                    field.kind == CapabilityFieldKind.NUMBER ||
                    field.kind == CapabilityFieldKind.BOOLEAN
                ) {
                    TextButton(
                        onClick = {
                            val initial = when (field.kind) {
                                CapabilityFieldKind.NUMBER ->
                                    MacroValue.Number(java.math.BigDecimal.ZERO)
                                CapabilityFieldKind.BOOLEAN ->
                                    MacroValue.Boolean(false)
                                else -> MacroValue.Text("")
                            }
                            onConfigChanged(
                                blockId,
                                field.key,
                                initial,
                            )
                        },
                    ) {
                        Text("Add value")
                    }
                }
            }
        }
        if (field.allowedValues.isNotEmpty()) {
            Text(
                if (field.kind == CapabilityFieldKind.TEXT_LIST) {
                    "Choose one or more:"
                } else {
                    "Choose:"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                field.allowedValues.forEach { option ->
                    val selected = when (val current = field.currentValue) {
                        is MacroValue.Text -> current.value == option
                        is MacroValue.ListValue -> current.values.any {
                            (it as? MacroValue.Text)?.value == option
                        }
                        else -> false
                    }
                    TextButton(
                        onClick = {
                            val next = if (field.kind == CapabilityFieldKind.TEXT_LIST) {
                                field.toggleListOption(option)
                            } else {
                                MacroValue.Text(option)
                            }
                            onConfigChanged(blockId, field.key, next)
                        },
                    ) {
                        Text("${if (selected) "✓ " else ""}${option.displayOption()}")
                    }
                }
            }
        }
        if (field.referenceOptions.isNotEmpty()) {
            Button(onClick = { referencePickerOpen = true }) {
                Text("Choose value source")
            }
        }
        if (field.key == "package" && launcherApps.isNotEmpty()) {
            Button(onClick = { appPickerOpen = true }) {
                Text("Choose app")
            }
        }
        if (field.key == "timezone") {
            Button(onClick = { timezonePickerOpen = true }) {
                Text("Choose timezone")
            }
        }
        if (!field.required && field.currentValue != null) {
            TextButton(
                onClick = { onConfigChanged(blockId, field.key, null) },
            ) {
                Text("Remove optional value")
            }
        }
    }
    if (referencePickerOpen) {
        ValueReferencePickerDialog(
            fieldLabel = field.label,
            options = field.referenceOptions,
            onDismiss = { referencePickerOpen = false },
            onSelected = { option ->
                referencePickerOpen = false
                onConfigChanged(blockId, field.key, option.value)
            },
        )
    }
    if (appPickerOpen) {
        LauncherAppPickerDialog(
            apps = launcherApps,
            onDismiss = { appPickerOpen = false },
            onSelected = { app ->
                appPickerOpen = false
                onConfigChanged(
                    blockId,
                    field.key,
                    MacroValue.Text(app.packageName),
                )
            },
        )
    }
    if (timezonePickerOpen) {
        TimezonePickerDialog(
            timezones = remember { availableTimezones() },
            onDismiss = { timezonePickerOpen = false },
            onSelected = { timezone ->
                timezonePickerOpen = false
                onConfigChanged(
                    blockId,
                    field.key,
                    MacroValue.Text(timezone),
                )
            },
        )
    }
}

@Composable
private fun TimezonePickerDialog(
    timezones: List<String>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = filterTimezones(timezones, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose timezone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "No matching timezones.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    items(filtered, key = { it }) { timezone ->
                        TextButton(
                            onClick = { onSelected(timezone) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(timezone, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ValueReferencePickerDialog(
    fieldLabel: String,
    options: List<ValueReferenceOption>,
    onDismiss: () -> Unit,
    onSelected: (ValueReferenceOption) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = filterValueReferenceOptions(options, query)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fieldLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "No matching values.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    items(filtered, key = { it.value.toString() }) { option ->
                        TextButton(
                            onClick = { onSelected(option) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(option.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    option.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

private fun CapabilityFormField.toggleListOption(option: String): MacroValue.ListValue {
    val current = (currentValue as? MacroValue.ListValue)
        ?.values
        ?.mapNotNull { (it as? MacroValue.Text)?.value }
        .orEmpty()
        .toMutableSet()
    if (!current.add(option)) {
        current.remove(option)
    }
    val ordered = allowedValues.filter(current::contains)
    return MacroValue.ListValue(ordered.map { MacroValue.Text(it) })
}

private fun String.displayOption(): String =
    replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun MacroValue.ObjectValue.referenceDescription(): String {
    val (kind, value) = values.entries.singleOrNull() ?: return "structured value"
    return "$kind ${(value as? MacroValue.Text)?.value.orEmpty()}"
}

private fun MacroValue.displayValue(): String = when (this) {
    is MacroValue.Text -> value
    is MacroValue.Number -> value.toPlainString()
    is MacroValue.Boolean -> value.toString()
    is MacroValue.ListValue -> "[${values.size} values]"
    is MacroValue.ObjectValue -> referenceDescription()
    MacroValue.Null -> "null"
}

@Composable
private fun BlockCard(
    position: Int,
    block: BlockExplanation,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = position.toString(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(block.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    text = block.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = block.capabilityType,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(proposal: OpenMacroProposal) {
    val permissions = proposal.explanation.requiredPermissions
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Permissions", fontWeight = FontWeight.SemiBold)
            Text(
                text = if (permissions.isEmpty()) {
                    "No Android permissions are required."
                } else {
                    permissions.joinToString { it.manifestName }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyVisualState() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Fix the code to restore the visual macro.",
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun CodeEditor(
    state: MacroEditorState,
    onSourceChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "OpenMacro code",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Edits are parsed and explained locally. Nothing runs from this text directly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.sourceText,
            onValueChange = onSourceChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            isError = state.problems.isNotEmpty(),
            label = { Text("charger-greeting.openmacro.yaml") },
        )
        state.problems.firstOrNull()?.let { problem ->
            Text(
                text = buildString {
                    append(problem.path)
                    if (problem.line != null) {
                        append(" · line ${problem.line}")
                        if (problem.column != null) append(":${problem.column}")
                    }
                    append("\n${problem.message}")
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EditorBottomBar(
    selected: EditorMode,
    onSelected: (EditorMode) -> Unit,
) {
    BottomAppBar {
        Spacer(Modifier.weight(1f))
        ModeButton(
            label = "Visual",
            selected = selected == EditorMode.VISUAL,
            onClick = { onSelected(EditorMode.VISUAL) },
        )
        ModeButton(
            label = "Code",
            selected = selected == EditorMode.CODE,
            onClick = { onSelected(EditorMode.CODE) },
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        FilledTonalButton(onClick = onClick) {
            Text(label)
        }
    } else {
        TextButton(onClick = onClick) {
            Text(label)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MacroEditorScreenPreview() {
    ZeroBitTheme {
        val editor = rememberPreviewEditor()
        MacroEditorScreen(
            state = editor.second,
            onModeSelected = {},
            onSourceChanged = {},
            onApprove = {},
            onConfigChanged = { _, _, _ -> },
        )
    }
}


@Composable
private fun rememberPreviewEditor(): Pair<MacroEditorSession, MacroEditorState> =
    androidx.compose.runtime.remember {
        val pipeline = com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline(
            com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry.builtIn(),
        )
        MacroEditorSession.withInitialSourceApproved(pipeline, SampleMacro.source)
    }

@Composable
private fun BehaviorChangesCard(
    proposal: OpenMacroProposal,
    onApprove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val comparison = proposal.comparison
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Behavioral Changes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            comparison.changes.forEach { change ->
                val changeText = when (change.kind) {
                    com.vibhor1102.zerobit.openmacro.proposal.BehaviorChangeKind.NEW_MACRO ->
                        "New Macro: ${change.after}"
                    com.vibhor1102.zerobit.openmacro.proposal.BehaviorChangeKind.MACRO_ID_CHANGED ->
                        "Macro ID changed: ${change.before} -> ${change.after}"
                    com.vibhor1102.zerobit.openmacro.proposal.BehaviorChangeKind.BLOCK_ADDED ->
                        "Added block in ${change.lane.toString().lowercase()}: ${change.after}"
                    com.vibhor1102.zerobit.openmacro.proposal.BehaviorChangeKind.BLOCK_REMOVED ->
                        "Removed block from ${change.lane.toString().lowercase()}: ${change.before}"
                    com.vibhor1102.zerobit.openmacro.proposal.BehaviorChangeKind.BLOCK_CHANGED ->
                        "Modified block config: ${change.before} -> ${change.after}"
                    com.vibhor1102.zerobit.openmacro.proposal.BehaviorChangeKind.BLOCK_REORDERED ->
                        "Reordered block: ${change.before} -> ${change.after}"
                    com.vibhor1102.zerobit.openmacro.proposal.BehaviorChangeKind.CONDITION_TREE_CHANGED ->
                        "Changed AND/OR/NOT condition logic."
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = changeText,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (comparison.permissionsAdded.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Required permissions added:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        comparison.permissionsAdded.forEach { permission ->
                            Text(
                                text = "• ${permission.manifestName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            if (comparison.permissionsRemoved.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Permissions removed:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        comparison.permissionsRemoved.forEach { permission ->
                            Text(
                                text = "• ${permission.manifestName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onApprove,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = "Approve behavior",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
