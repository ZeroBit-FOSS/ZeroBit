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
import com.vibhor1102.zerobit.ui.editor.MacroEditorScreen
import com.vibhor1102.zerobit.ui.editor.MacroEditorSession
import com.vibhor1102.zerobit.ui.editor.FormSourceEditResult
import com.vibhor1102.zerobit.ui.editor.SampleMacro
import com.vibhor1102.zerobit.ui.theme.ZeroBitTheme
import com.vibhor1102.zerobit.openmacro.storage.ApprovalStoreResult
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
                val editor = remember {
                    val session = MacroEditorSession(
                        pipeline,
                        initialApproved = app.currentApprovedSnapshot(
                            SampleMacro.MACRO_ID,
                        ),
                    )
                    session to session.create(SampleMacro.source)
                }
                val session = editor.first
                var state by remember { mutableStateOf(editor.second) }
                var recoveryReport by remember {
                    mutableStateOf(app.runtimeRecoveryReport)
                }
                var runtimeEnabled by remember {
                    mutableStateOf(app.isMacroEnabled(SampleMacro.MACRO_ID))
                }
                var runtimeOverview by remember {
                    mutableStateOf(app.macroOverview(SampleMacro.MACRO_ID))
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {
                    recoveryReport = app.retryDesiredMacros()
                    runtimeEnabled = app.isMacroEnabled(SampleMacro.MACRO_ID)
                    runtimeOverview = app.macroOverview(SampleMacro.MACRO_ID)
                }

                LaunchedEffect(state.sourceText) {
                    val baseState = state
                    val sourceToParse = state.sourceText
                    delay(SOURCE_PARSE_DEBOUNCE_MILLIS)
                    val parsed = withContext(Dispatchers.Default) {
                        session.updateSource(baseState, sourceToParse)
                    }
                    if (state.sourceText == sourceToParse) {
                        state = parsed.copy(mode = state.mode)
                    }
                }

                MacroEditorScreen(
                    state = state,
                    onModeSelected = { state = session.selectMode(state, it) },
                    onSourceChanged = { state = state.copy(sourceText = it) },
                    onApprove = {
                        val proposal = (state.result as? ProposalResult.Ready)?.proposal
                        if (proposal != null) {
                            when (val result = app.approveMacro(proposal)) {
                                is ApprovalStoreResult.Success -> {
                                    state = session.approveCurrent(state)
                                    if (runtimeEnabled) {
                                        when (
                                            val enabled = app.enableMacro(
                                                SampleMacro.MACRO_ID,
                                            )
                                        ) {
                                            is RuntimeLifecycleResult.Enabled -> Unit
                                            is RuntimeLifecycleResult.EnableFailed -> {
                                                runtimeEnabled = app.isMacroEnabled(
                                                    SampleMacro.MACRO_ID,
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
                                            SampleMacro.MACRO_ID,
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
                    recoveryReport = recoveryReport,
                    onRetryRuntime = {
                        recoveryReport = app.retryDesiredMacros()
                        runtimeEnabled = app.isMacroEnabled(SampleMacro.MACRO_ID)
                        runtimeOverview = app.macroOverview(SampleMacro.MACRO_ID)
                    },
                    runtimeEnabled = runtimeEnabled,
                    onRuntimeEnabledChanged = { shouldEnable ->
                        val result = if (shouldEnable) {
                            app.enableMacro(SampleMacro.MACRO_ID)
                        } else {
                            app.disableMacro(SampleMacro.MACRO_ID)
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
                                    SampleMacro.MACRO_ID,
                                )
                                state = session.reportFormEditError(
                                    state,
                                    result.message,
                                )
                            }
                        }
                        runtimeOverview = app.macroOverview(SampleMacro.MACRO_ID)
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
