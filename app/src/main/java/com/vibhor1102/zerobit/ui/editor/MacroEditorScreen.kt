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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vibhor1102.zerobit.openmacro.capability.CapabilityLane
import com.vibhor1102.zerobit.openmacro.proposal.BlockExplanation
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposal
import com.vibhor1102.zerobit.openmacro.proposal.ProposalResult
import com.vibhor1102.zerobit.ui.theme.ZeroBitTheme

@Composable
fun MacroEditorScreen(
    state: MacroEditorState,
    onModeSelected: (EditorMode) -> Unit,
    onSourceChanged: (String) -> Unit,
    onApprove: () -> Unit,
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
                onApprove = onApprove,
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
    onApprove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EditorHeader(state.visibleProposal)
        ProposalStatus(state)

        val proposal = state.visibleProposal
        if (proposal == null) {
            EmptyVisualState()
        } else {
            if (proposal.comparison.approvalRequired) {
                BehaviorChangesCard(proposal = proposal, onApprove = onApprove)
            }
            LaneCard(
                title = "Triggers",
                subtitle = "Any trigger can start this macro",
                blocks = proposal.explanation.blocksIn(CapabilityLane.TRIGGER),
            )
            LaneCard(
                title = "Conditions",
                subtitle = "Every condition must pass",
                blocks = proposal.explanation.blocksIn(CapabilityLane.CONDITION),
            )
            LaneCard(
                title = "Actions",
                subtitle = "Actions run from top to bottom",
                blocks = proposal.explanation.blocksIn(CapabilityLane.ACTION),
            )
            PermissionCard(proposal)
        }
    }
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
        state.problems.isNotEmpty() -> MaterialTheme.colorScheme.errorContainer
        ready?.proposal?.comparison?.approvalRequired == true ->
            MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val title = when {
        state.problems.isNotEmpty() -> "Code needs attention"
        ready?.proposal?.comparison?.approvalRequired == true -> "Review required"
        else -> "Matches approved behavior"
    }
    val detail = when {
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
    blocks: List<BlockExplanation>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
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

            blocks.forEachIndexed { index, block ->
                BlockCard(position = index + 1, block = block)
            }
        }
    }
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
