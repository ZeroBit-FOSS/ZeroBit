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
                onConfigChanged = onConfigChanged,
                recoveryReport = recoveryReport,
                onRetryRuntime = onRetryRuntime,
                runtimeEnabled = runtimeEnabled,
                onRuntimeEnabledChanged = onRuntimeEnabledChanged,
                onRepairPermission = onRepairPermission,
                runtimeOverview = runtimeOverview,
                onVariableChanged = onVariableChanged,
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
    onConfigChanged: (String, String, MacroValue?) -> Unit,
    recoveryReport: RuntimeRecoveryReport,
    onRetryRuntime: () -> Unit,
    runtimeEnabled: Boolean,
    onRuntimeEnabledChanged: (Boolean) -> Unit,
    onRepairPermission: (AndroidPermission) -> Unit,
    runtimeOverview: RuntimeMacroOverview?,
    onVariableChanged: (String, String, MacroValue?) -> Unit,
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
            if (proposal.source.document.variables.isNotEmpty()) {
                VariableLaneCard(
                    proposal.source.document.variables,
                    onVariableChanged,
                )
            }
            LaneCard(
                title = "Triggers",
                subtitle = "Any trigger can start this macro",
                blocks = proposal.explanation.blocksIn(CapabilityLane.TRIGGER),
                document = proposal.source.document,
                onConfigChanged = onConfigChanged,
            )
            LaneCard(
                title = "Conditions",
                subtitle = "Every condition must pass",
                blocks = proposal.explanation.blocksIn(CapabilityLane.CONDITION),
                document = proposal.source.document,
                onConfigChanged = onConfigChanged,
            )
            LaneCard(
                title = "Actions",
                subtitle = "Actions run from top to bottom",
                blocks = proposal.explanation.blocksIn(CapabilityLane.ACTION),
                document = proposal.source.document,
                onConfigChanged = onConfigChanged,
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
    blocks: List<BlockExplanation>,
    document: OpenMacroDocument,
    onConfigChanged: (String, String, MacroValue?) -> Unit,
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
                document.findBlock(block.blockId)?.let { modelBlock ->
                    CapabilityForm(
                        document = document,
                        block = modelBlock,
                        onConfigChanged = onConfigChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun VariableLaneCard(
    variables: List<MacroVariable>,
    onVariableChanged: (String, String, MacroValue?) -> Unit,
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
            variables.forEach { variable ->
                VariableEditor(variable, onVariableChanged)
            }
        }
    }
}

@Composable
private fun VariableEditor(
    variable: MacroVariable,
    onVariableChanged: (String, String, MacroValue?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${variable.name} · ${variable.type.name.lowercase()}",
            fontWeight = FontWeight.SemiBold,
        )
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
    onConfigChanged: (String, String, MacroValue?) -> Unit,
) {
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
            is MacroValue.ObjectValue -> Text(
                text = "${field.label}: ${current.referenceDescription()}",
                style = MaterialTheme.typography.bodyMedium,
            )
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
            Text(
                "Use a value from:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                field.referenceOptions.take(MAX_VISIBLE_REFERENCE_BUTTONS).forEach { option ->
                    TextButton(
                        onClick = {
                            onConfigChanged(blockId, field.key, option.value)
                        },
                    ) {
                        Text(option.label)
                    }
                }
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

private fun OpenMacroDocument.findBlock(blockId: String): MacroBlock? =
    (triggers + conditions + actions).find { it.id == blockId }
        ?: conditionTree?.findBlock(blockId)

private fun MacroConditionNode.findBlock(blockId: String): MacroBlock? = when (this) {
    is MacroConditionNode.Condition -> block.takeIf { it.id == blockId }
    is MacroConditionNode.All -> children.firstNotNullOfOrNull { it.findBlock(blockId) }
    is MacroConditionNode.Any -> children.firstNotNullOfOrNull { it.findBlock(blockId) }
    is MacroConditionNode.Not -> child.findBlock(blockId)
}

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

private const val MAX_VISIBLE_REFERENCE_BUTTONS = 3

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
