package com.vibhor1102.zerobit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vibhor1102.zerobit.openmacro.capability.CapabilityRegistry
import com.vibhor1102.zerobit.openmacro.proposal.OpenMacroProposalPipeline
import com.vibhor1102.zerobit.ui.editor.MacroEditorScreen
import com.vibhor1102.zerobit.ui.editor.MacroEditorSession
import com.vibhor1102.zerobit.ui.editor.SampleMacro
import com.vibhor1102.zerobit.ui.theme.ZeroBitTheme
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
                val editor = remember {
                    MacroEditorSession.withInitialSourceApproved(
                        pipeline = pipeline,
                        initialSource = SampleMacro.source,
                    )
                }
                val session = editor.first
                var state by remember { mutableStateOf(editor.second) }

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
                )
            }
        }
    }

    private companion object {
        const val SOURCE_PARSE_DEBOUNCE_MILLIS = 250L
    }
}
