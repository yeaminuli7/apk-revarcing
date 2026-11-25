package id.vern.antigravity.ui.components

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import id.vern.antigravity.R
import id.vern.antigravity.ui.theme.AntigravityGreen
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Handler
import android.os.Looper
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.ui.theme.AntigravityRed
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import id.vern.antigravity.service.TerminalLine
import id.vern.antigravity.service.TerminalSessionManager

@Composable
fun TerminalView(
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val lines = TerminalSessionManager.lines
    val history = TerminalSessionManager.history
    var historyIndex by TerminalSessionManager.historyIndex
    var commandInput by TerminalSessionManager.commandInput
    var isRunning by TerminalSessionManager.isRunning

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    fun safeRequestFocus(showKeyboard: Boolean = true) {
        try {
            focusRequester.requestFocus()
            if (showKeyboard) {
                keyboardController?.show()
            }
        } catch (e: Exception) {}
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        TerminalSessionManager.initIfNeeded()
        val isAgyActive = EngineExecutor.isAgyRunning()
        if (isAgyActive) {
            TerminalSessionManager.ensureAgyLiveSession(autoSwitch = true)
        } else if (TerminalSessionManager.currentSessionName.value == TerminalSessionManager.AGY_SESSION_NAME) {
            TerminalSessionManager.ensureBionicSession(autoSwitch = true)
        }
        safeRequestFocus(showKeyboard = false)
    }

    fun executeTerminalCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty() || isRunning) return

        if (TerminalSessionManager.currentSessionName.value == TerminalSessionManager.AGY_SESSION_NAME) {
            val isAgyRunning = EngineExecutor.isAgyRunning()
            if (isAgyRunning) {
                TerminalSessionManager.appendAgyLine("> $trimmed", isCommand = true)
                EngineExecutor.writeToAgyStdin(trimmed)
            } else {
                TerminalSessionManager.appendAgyLine("> $trimmed", isCommand = true)
                TerminalSessionManager.appendAgyLine(context.getString(R.string.terminal_agy_idle_hint), isError = true)
            }
            commandInput = ""
            safeRequestFocus()
            return
        }

        lines.add(TerminalLine(text = "$ $trimmed", isCommand = true))
        TerminalSessionManager.sessionBuffers[TerminalSessionManager.currentSessionName.value]?.add(TerminalLine(text = "$ $trimmed", isCommand = true))
        history.add(trimmed)
        historyIndex = history.size
        commandInput = ""
        isRunning = true
        safeRequestFocus()

        coroutineScope.launch {
            val exitCode = EngineExecutor.executeCommandWithUpdates(context, trimmed, TerminalSessionManager.currentWorkingDir) { line, isErr, isUpdate ->
                TerminalSessionManager.appendLine(line, isErr, isUpdate)
            }
            withContext(Dispatchers.Main) {
                isRunning = false
                safeRequestFocus()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D14))
    ) {
        if (showHeader) {
            Surface(
                color = Color(0xFF131924),
                modifier = Modifier.fillMaxWidth()
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAgyActive = TerminalSessionManager.currentSessionName.value == TerminalSessionManager.AGY_SESSION_NAME && EngineExecutor.isAgyRunning()
                val isSessionBusy = isRunning || isAgyActive
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isSessionBusy) AntigravityGreen else Color(0xFF64748B))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (isSessionBusy) R.string.terminal_status_busy else R.string.terminal_status_ready),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { TerminalSessionManager.clear() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.terminal_action_clear),
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    safeRequestFocus()
                }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            items(lines, key = { it.id }) { line ->
                val isErr = line.isError || line.text.contains("error:", ignoreCase = true) || line.text.contains("failed", ignoreCase = true) || line.text.contains("fatal:", ignoreCase = true)
                val color = when {
                    line.isCommand -> Color(0xFF38BDF8)
                    isErr -> AntigravityRed
                    else -> Color(0xFFE2E8F0)
                }
                Text(
                    text = line.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = color,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                ) {
                    val isAgySession = TerminalSessionManager.currentSessionName.value == TerminalSessionManager.AGY_SESSION_NAME
                    Text(
                        text = if (isAgySession) "> " else "$ ",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isAgySession) Color(0xFF38BDF8) else AntigravityGreen
                    )
                    BasicTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFF8FAFC)
                        ),
                        cursorBrush = SolidColor(if (isAgySession) Color(0xFF38BDF8) else AntigravityGreen),
                        visualTransformation = VisualTransformation.None,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onSend = {
                            executeTerminalCommand(commandInput)
                            safeRequestFocus()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            }
        }

        Surface(
            color = Color(0xFF131924),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.clickable {
                        TerminalSessionManager.killCurrentProcess()
                        safeRequestFocus()
                    }
                ) {
                    Text(
                        text = "Ctrl+C",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.clickable {
                        if (history.isNotEmpty() && historyIndex > 0) {
                            historyIndex--
                            commandInput = history[historyIndex]
                        }
                        safeRequestFocus()
                    }
                ) {
                    Text(
                        text = "▲",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.clickable {
                        if (history.isNotEmpty() && historyIndex < history.size - 1) {
                            historyIndex++
                            commandInput = history[historyIndex]
                        } else if (historyIndex >= history.size - 1) {
                            historyIndex = history.size
                            commandInput = ""
                        }
                        safeRequestFocus()
                    }
                ) {
                    Text(
                        text = "▼",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.clickable {
                        safeRequestFocus()
                    }
                ) {
                    Text(
                        text = "◀",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.clickable {
                        safeRequestFocus()
                    }
                ) {
                    Text(
                        text = "▶",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                val quickShortcuts = listOf(
                    "agy --version",
                    "ls -la",
                    "git status",
                    "pwd",
                    "pkg status",
                    "agy --help"
                )

                quickShortcuts.forEach { shortcut ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.clickable {
                            executeTerminalCommand(shortcut)
                            safeRequestFocus()
                        }
                    ) {
                        Text(
                            text = shortcut,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

