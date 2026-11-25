package id.vern.antigravity.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import id.vern.antigravity.R
import id.vern.antigravity.data.AgentExecutionStep
import id.vern.antigravity.data.AgentStepKind
import id.vern.antigravity.data.ChatMessage
import id.vern.antigravity.data.MessageRole
import id.vern.antigravity.data.MessageStatus
import id.vern.antigravity.data.TurnFileChanges
import id.vern.antigravity.ui.theme.AntigravityAmber
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    verboseThinking: Boolean = false,
    onOpenFile: (String) -> Unit = {},
    onOpenTerminal: (String) -> Unit = {},
    onUndoPrompt: (ChatMessage) -> Unit = {},
    isPinnedExecuting: Boolean = false,
    isExecuting: Boolean = false
) {
    val context = LocalContext.current
    val isUser = message.role == MessageRole.USER

    if (isUser) {
        val isLongPrompt = isPinnedExecuting && (message.content.length > 80 || message.content.count { it == '\n' } >= 2)
        var isExpanded by remember(message.id) { mutableStateOf(false) }
        val chevronRotation by animateFloatAsState(
            targetValue = if (isExpanded) 180f else 0f,
            animationSpec = tween(250, easing = FastOutSlowInEasing),
            label = "bubbleChevronRotation"
        )

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .animateContentSize(),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (message.attachments.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            message.attachments.forEach { file ->
                                val isImage = file.mimeType.startsWith("image/") ||
                                    file.name.endsWith(".png", ignoreCase = true) ||
                                    file.name.endsWith(".jpg", ignoreCase = true) ||
                                    file.name.endsWith(".jpeg", ignoreCase = true) ||
                                    file.name.endsWith(".webp", ignoreCase = true) ||
                                    file.name.endsWith(".gif", ignoreCase = true)

                                if (isImage) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val imageModel = file.localPath?.let { File(it) } ?: file.uri
                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = file.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(72.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = AntigravityBlue
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Column {
                                                Text(
                                                    text = file.name,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 160.dp)
                                                )
                                                Text(
                                                    text = file.formattedSize,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (message.content.isNotBlank()) {
                        if (isPinnedExecuting && isLongPrompt && !isExpanded) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            MarkdownText(
                                markdown = message.content,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                selectable = message.status == MessageStatus.SENT
                            )
                        }
                    }

                    val toastCopiedMsg = stringResource(R.string.chat_toast_copied)
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        if (isLongPrompt) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { isExpanded = !isExpanded }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.chat_expand_collapse),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(chevronRotation)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        if (!isExecuting && !isPinnedExecuting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onUndoPrompt(message) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = stringResource(R.string.chat_undo_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.chat_btn_undo),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_btn_copy), message.content))
                                    Toast.makeText(context, toastCopiedMsg, Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.chat_btn_copy),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.chat_btn_copy),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    } else {

        val isSending = message.status == MessageStatus.SENDING
        var workedExpanded by remember(message.id, verboseThinking) { mutableStateOf(verboseThinking || isSending) }
        var stepsExpanded by remember(message.id, verboseThinking) { mutableStateOf(verboseThinking || isSending) }
        var thoughtExpanded by remember(message.id, verboseThinking) { mutableStateOf(verboseThinking || isSending) }
        var showReviewDialog by remember { mutableStateOf(false) }

        androidx.compose.runtime.LaunchedEffect(message.id, verboseThinking, isSending) {
            if (verboseThinking || isSending) {
                workedExpanded = true
                stepsExpanded = true
                thoughtExpanded = true
            }
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start
        ) {

            val hasExecutionSteps = message.steps.isNotEmpty() || !message.thinking.isNullOrBlank() || message.durationText != null
            if (isSending || hasExecutionSteps) {
                val durationLabel = if (isSending) {
                    stringResource(R.string.chat_working_ellipsis)
                } else {
                    stringResource(R.string.chat_worked_for, message.durationText ?: "1m")
                }

                Column(modifier = Modifier.padding(bottom = 6.dp)) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { workedExpanded = !workedExpanded }
                            .padding(vertical = 3.dp, horizontal = 2.dp)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.8.dp,
                                color = AntigravityBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = durationLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val workedRot by animateFloatAsState(
                            targetValue = if (workedExpanded) 180f else 0f,
                            animationSpec = tween(200),
                            label = "workedRot"
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.chat_expand_collapse),
                            modifier = Modifier
                                .size(15.dp)
                                .rotate(workedRot),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    AnimatedVisibility(
                        visible = workedExpanded,
                        enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                    ) {
                        Column(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                            val hasThoughtInSteps = message.steps.any { it.kind == AgentStepKind.THOUGHT }
                            val thoughtDetail = message.thinking

                            if (!hasThoughtInSteps && (isSending || !thoughtDetail.isNullOrBlank())) {
                                var liveElapsedSeconds by remember(message.id) {
                                    mutableIntStateOf(((System.currentTimeMillis() - message.timestamp) / 1000L).coerceAtLeast(1L).toInt())
                                }
                                LaunchedEffect(message.id, isSending) {
                                    if (isSending) {
                                        while (true) {
                                            liveElapsedSeconds = ((System.currentTimeMillis() - message.timestamp) / 1000L).coerceAtLeast(1L).toInt()
                                            delay(1000L)
                                        }
                                    }
                                }
                                val thoughtTitleText = if (isSending) {
                                    stringResource(R.string.chat_thought_title, liveElapsedSeconds)
                                } else {
                                    stringResource(R.string.chat_thought_process)
                                }
                                val rot by animateFloatAsState(
                                    targetValue = if (thoughtExpanded) 180f else 0f,
                                    animationSpec = tween(durationMillis = 200),
                                    label = "singleThoughtRot"
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { thoughtExpanded = !thoughtExpanded }
                                        .padding(vertical = 3.dp)
                                ) {
                                    if (isSending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.5.dp,
                                            color = AntigravityBlue
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = thoughtTitleText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                        )
                                    )
                                    if (!thoughtDetail.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.chat_expand_collapse),
                                            modifier = Modifier
                                                .size(13.dp)
                                                .rotate(rot),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                if (!thoughtDetail.isNullOrBlank()) {
                                    AnimatedVisibility(
                                        visible = thoughtExpanded,
                                        enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                                        exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 280.dp)
                                                .padding(vertical = 4.dp)
                                        ) {
                                            val singleThoughtScroll = rememberScrollState()
                                            LaunchedEffect(singleThoughtScroll.maxValue) {
                                                if (isSending && !singleThoughtScroll.isScrollInProgress) {
                                                    singleThoughtScroll.scrollTo(singleThoughtScroll.maxValue)
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(singleThoughtScroll)
                                                    .padding(10.dp)
                                            ) {
                                                val displayThought = thoughtDetail
                                                Text(
                                                    text = displayThought,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 11.5.sp,
                                                        lineHeight = 16.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (message.steps.isNotEmpty()) {
                                AgentExecutionStepsView(
                                    steps = message.steps,
                                    isSending = isSending,
                                    verboseThinking = verboseThinking,
                                    onOpenFile = onOpenFile,
                                    onOpenTerminal = onOpenTerminal,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!message.commandRun.isNullOrBlank()) {
                val hasOutput = !message.commandOutput.isNullOrBlank()
                var isDirectCmdOpen by remember(message.id, verboseThinking) { mutableStateOf(verboseThinking) }
                val cmdRot by animateFloatAsState(
                    targetValue = if (isDirectCmdOpen) 180f else 0f,
                    animationSpec = tween(200),
                    label = "directCmdRot"
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = hasOutput) { isDirectCmdOpen = !isDirectCmdOpen }
                        ) {
                            Text(
                                text = "~/ $ ${message.commandRun}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = AntigravityAmber,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (hasOutput) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.chat_expand_collapse),
                                    modifier = Modifier
                                        .size(15.dp)
                                        .rotate(cmdRot),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (hasOutput) {
                            AnimatedVisibility(
                                visible = isDirectCmdOpen,
                                enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                            ) {
                                Column(modifier = Modifier.padding(top = 6.dp)) {
                                    val isErrorOutput = message.status == MessageStatus.ERROR ||
                                        (message.commandOutput?.contains("error", ignoreCase = true) == true) ||
                                        (message.commandOutput?.contains("failed", ignoreCase = true) == true) ||
                                        (message.commandOutput?.contains("fatal", ignoreCase = true) == true)
                                    val cmdOutScroll = rememberScrollState()
                                    val cmdOutHScroll = rememberScrollState()
                                    LaunchedEffect(cmdOutScroll.maxValue) {
                                        if (isSending && !cmdOutScroll.isScrollInProgress) {
                                            cmdOutScroll.scrollTo(cmdOutScroll.maxValue)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 320.dp)
                                            .verticalScroll(cmdOutScroll)
                                            .horizontalScroll(cmdOutHScroll)
                                    ) {
                                        Text(
                                            text = message.commandOutput ?: "",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = if (isErrorOutput) AntigravityRed else Color(0xFFD4D4D4),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (isSending && message.content.isBlank()) {
                val liveThinking = message.thinking
                var isLiveThinkingOpen by remember(message.id, verboseThinking) { mutableStateOf(verboseThinking) }
                androidx.compose.runtime.LaunchedEffect(message.id, verboseThinking) {
                    if (verboseThinking) {
                        isLiveThinkingOpen = true
                    }
                }
                val rot by animateFloatAsState(
                    targetValue = if (isLiveThinkingOpen) 180f else 0f,
                    animationSpec = tween(durationMillis = 200),
                    label = "liveThinkingRot"
                )

                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.widthIn(max = 600.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (!liveThinking.isNullOrBlank()) {
                                        isLiveThinkingOpen = !isLiveThinkingOpen
                                    }
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = AntigravityBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.chat_agent_thinking),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!liveThinking.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.chat_expand_collapse),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .rotate(rot),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        if (!liveThinking.isNullOrBlank()) {
                            AnimatedVisibility(
                                visible = isLiveThinkingOpen,
                                enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp)
                                        .padding(top = 8.dp)
                                ) {
                                    val thoughtScroll = rememberScrollState()
                                    LaunchedEffect(thoughtScroll.maxValue) {
                                        if (!thoughtScroll.isScrollInProgress) {
                                            thoughtScroll.scrollTo(thoughtScroll.maxValue)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(thoughtScroll)
                                            .padding(10.dp)
                                    ) {
                                        val displayThought = liveThinking
                                        Text(
                                            text = displayThought,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (message.content.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.widthIn(max = 600.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        MarkdownText(
                            markdown = message.content,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            selectable = message.status == MessageStatus.SENT
                        )

                        val toastCopiedMsg = stringResource(R.string.chat_toast_copied)
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_assistant_response), message.content))
                                        Toast.makeText(context, toastCopiedMsg, Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.chat_btn_copy),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.chat_btn_copy),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            val fileChanges = message.fileChanges
            if (fileChanges != null && fileChanges.totalFiles > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showReviewDialog = true }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.chat_files_changed, fileChanges.totalFiles),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+${fileChanges.totalAdditions}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "-${fileChanges.totalDeletions}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFFEF5350)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { showReviewDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(R.string.chat_btn_review),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (showReviewDialog && fileChanges != null) {
                ReviewChangesDialog(
                    fileChanges = fileChanges,
                    onDismissRequest = { showReviewDialog = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewChangesDialog(
    fileChanges: TurnFileChanges,
    onDismissRequest: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogBlurEffect()
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_review_changes_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                R.string.chat_review_changes_subtitle,
                                fileChanges.totalFiles,
                                fileChanges.totalAdditions,
                                fileChanges.totalDeletions
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(fileChanges.files, key = { it.relativePath }) { file ->
                        var isDiffOpen by remember { mutableStateOf(false) }
                        val diffRotation by animateFloatAsState(
                            targetValue = if (isDiffOpen) 0f else -90f,
                            animationSpec = tween(durationMillis = 200),
                            label = "diffArrow"
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().animateContentSize()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isDiffOpen = !isDiffOpen }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    FileBadge(fileName = file.name)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = file.relativePath,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    when (file.status) {
                                        "D" -> {
                                            Text(
                                                text = stringResource(R.string.chat_status_deleted),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFFEF5350)
                                            )
                                        }
                                        "A" -> {
                                            Text(
                                                text = stringResource(R.string.chat_status_added),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                        else -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (file.additions > 0) {
                                                    Text(
                                                        text = "+${file.additions}",
                                                        color = Color(0xFF4CAF50),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                if (file.deletions > 0) {
                                                    Text(
                                                        text = "-${file.deletions}",
                                                        color = Color(0xFFEF5350),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(diffRotation)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isDiffOpen,
                                    enter = expandVertically(animationSpec = tween(250)) + fadeIn(tween(200)),
                                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(150))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                        Surface(
                                            color = Color(0xFF0F1115),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp)
                                        ) {
                                            val scrollState = rememberScrollState()
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(scrollState)
                                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                if (file.diff.isNotBlank()) {
                                                    file.diff.lines().forEach { diffLine ->
                                                        val (bgColor, textColor) = when {
                                                            diffLine.startsWith("+") && !diffLine.startsWith("+++") -> {
                                                                Color(0xFF14301B) to Color(0xFF81C784)
                                                            }
                                                            diffLine.startsWith("-") && !diffLine.startsWith("---") -> {
                                                                Color(0xFF331618) to Color(0xFFE57373)
                                                            }
                                                            diffLine.startsWith("@@") -> {
                                                                Color(0xFF1E2838) to Color(0xFF64B5F6)
                                                            }
                                                            else -> Color.Transparent to Color(0xFFD4D4D4)
                                                        }

                                                        Text(
                                                            text = diffLine,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 11.sp,
                                                            color = textColor,
                                                            lineHeight = 15.sp,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(bgColor)
                                                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = stringResource(R.string.chat_no_changes),
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentExecutionStepsView(
    steps: List<AgentExecutionStep>,
    isSending: Boolean,
    modifier: Modifier = Modifier,
    verboseThinking: Boolean = false,
    onOpenFile: (String) -> Unit = {},
    onOpenTerminal: (String) -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        steps.forEach { step ->
            androidx.compose.runtime.key(step.id) {
                when (step.kind) {
                AgentStepKind.THOUGHT -> {
                    var isThoughtOpen by remember(step.id, verboseThinking) { mutableStateOf(verboseThinking || !step.isCompleted) }
                    androidx.compose.runtime.LaunchedEffect(step.id, verboseThinking, step.isCompleted) {
                        if (verboseThinking || !step.isCompleted) {
                            isThoughtOpen = true
                        }
                    }
                    val rot by animateFloatAsState(
                        targetValue = if (isThoughtOpen) 180f else 0f,
                        animationSpec = tween(durationMillis = 200),
                        label = "thoughtRot"
                    )
                    var liveStepSeconds by remember(step.id) {
                        mutableIntStateOf((step.durationSeconds ?: 1).coerceAtLeast(1))
                    }
                    LaunchedEffect(step.id, step.isCompleted) {
                        if (!step.isCompleted) {
                            val startMs = System.currentTimeMillis() - (liveStepSeconds * 1000L)
                            while (true) {
                                liveStepSeconds = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(1L).toInt()
                                delay(1000L)
                            }
                        } else if (step.durationSeconds != null) {
                            liveStepSeconds = step.durationSeconds
                        }
                    }
                    val stepThoughtTitle = when {
                        !step.isCompleted -> stringResource(R.string.chat_thought_title, liveStepSeconds)
                        step.durationSeconds != null -> stringResource(R.string.chat_thought_title, step.durationSeconds)
                        else -> stringResource(R.string.chat_thought_process)
                    }
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { isThoughtOpen = !isThoughtOpen }
                                .padding(vertical = 2.dp)
                        ) {
                            if (!step.isCompleted) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = AntigravityBlue
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = stepThoughtTitle,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(13.dp)
                                    .rotate(rot),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        AnimatedVisibility(
                            visible = isThoughtOpen && !step.detail.isNullOrBlank(),
                            enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                            exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                            ) {
                                val thoughtScroll = rememberScrollState()
                                LaunchedEffect(thoughtScroll.maxValue) {
                                    if (!step.isCompleted && !thoughtScroll.isScrollInProgress) {
                                        thoughtScroll.scrollTo(thoughtScroll.maxValue)
                                    }
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(thoughtScroll)
                                        .padding(10.dp)
                                ) {
                                    val displayThought = step.detail ?: ""
                                    Text(
                                        text = displayThought,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                AgentStepKind.ANALYZED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val target = step.filePath ?: step.title
                                onOpenFile(target)
                            }
                            .padding(vertical = 1.5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.chat_analyzed),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FileBadge(fileName = step.title)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = step.title,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        )
                        if (!step.lines.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = step.lines,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                AgentStepKind.EDITED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val target = step.filePath ?: step.title
                                onOpenFile(target)
                            }
                            .padding(vertical = 1.5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.chat_edited),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FileBadge(fileName = step.title)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = step.title,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        )
                        if (!step.diff.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val parts = step.diff.trim().split(" ")
                            if (parts.size >= 2 && parts[0].startsWith("+") && parts[1].startsWith("-")) {
                                Text(
                                    text = parts[0],
                                    color = Color(0xFF4CAF50),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = parts[1],
                                    color = Color(0xFFEF5350),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = step.diff,
                                    color = Color(0xFF4CAF50),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                AgentStepKind.SEARCH -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 1.5.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.chat_search)} ${step.title}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                AgentStepKind.COMMAND -> {
                    var isCommandOpen by remember(step.id) { mutableStateOf(!step.isCompleted) }
                    androidx.compose.runtime.LaunchedEffect(step.id, step.isCompleted) {
                        if (!step.isCompleted) {
                            isCommandOpen = true
                        }
                    }
                    val rot by animateFloatAsState(
                        targetValue = if (isCommandOpen) 180f else 0f,
                        animationSpec = tween(durationMillis = 200),
                        label = "cmdRot"
                    )
                    val hasDetail = !step.detail.isNullOrBlank()
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (hasDetail) {
                                        isCommandOpen = !isCommandOpen
                                    } else {
                                        onOpenTerminal(step.title)
                                    }
                                }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        ) {
                            if (!step.isCompleted && isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = AntigravityBlue
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = stringResource(R.string.chat_command_open_terminal),
                                modifier = Modifier.size(13.dp),
                                tint = AntigravityBlue
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = step.title,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (hasDetail) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.chat_expand_collapse),
                                    modifier = Modifier
                                        .size(13.dp)
                                        .rotate(rot),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onOpenTerminal(step.title) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = stringResource(R.string.chat_command_open_terminal),
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                        if (hasDetail) {
                            AnimatedVisibility(
                                visible = isCommandOpen,
                                enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0F1115),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                        .padding(start = 6.dp, top = 2.dp, bottom = 4.dp)
                                ) {
                                    val cmdScrollState = rememberScrollState()
                                    val cmdHScrollState = rememberScrollState()
                                    LaunchedEffect(cmdScrollState.maxValue) {
                                        if (!step.isCompleted && !cmdScrollState.isScrollInProgress) {
                                            cmdScrollState.scrollTo(cmdScrollState.maxValue)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(cmdScrollState)
                                            .horizontalScroll(cmdHScrollState)
                                            .padding(8.dp)
                                    ) {
                                        val displayCmd = step.detail ?: ""
                                        Text(
                                            text = displayCmd,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            color = Color(0xFFD4D4D4),
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                AgentStepKind.WORKING -> {
                    if (!step.isCompleted && isSending) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 1.8.dp,
                                color = AntigravityBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.chat_status_working),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun FileBadge(
    fileName: String,
    modifier: Modifier = Modifier
) {
    val cleanName = fileName.trim().removeSurrounding("\"").removeSurrounding("'")
    val ext = cleanName.substringAfterLast('.', "").lowercase()
    val (label, bg) = when {
        cleanName.equals("Dockerfile", ignoreCase = true) -> "DK" to Color(0xFF0288D1)
        cleanName.equals("Makefile", ignoreCase = true) -> "MK" to Color(0xFF78909C)
        ext == "py" -> "🐍" to Color(0xFF388E3C)
        ext in listOf("sh", "bash", "zsh") -> ">_" to Color(0xFF00897B)
        ext in listOf("kt", "kts", "gradle") -> "Kt" to Color(0xFF7F52FF)
        ext == "java" -> "J" to Color(0xFFE53935)
        ext in listOf("yml", "yaml") -> "Yml" to Color(0xFFE65100)
        ext == "md" -> "MD" to Color(0xFF0288D1)
        ext == "json" -> "{}" to Color(0xFF43A047)
        ext == "xml" -> "<>" to Color(0xFFFB8C00)
        ext in listOf("html", "htm") -> "web" to Color(0xFFE64A19)
        ext == "css" -> "CSS" to Color(0xFF1976D2)
        ext in listOf("js", "ts", "jsx", "tsx") -> "JS" to Color(0xFFFBC02D)
        ext in listOf("c", "cpp", "h", "hpp") -> "C++" to Color(0xFF00838F)
        else -> "F" to Color(0xFF78909C)
    }

    if (label == "🐍") {
        Text(text = "🐍", fontSize = 11.sp, modifier = modifier)
    } else if (label == ">_") {
        Text(
            text = ">_",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF26A69A),
            modifier = modifier
        )
    } else {
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = bg.copy(alpha = 0.18f),
            border = BorderStroke(0.8.dp, bg.copy(alpha = 0.65f)),
            modifier = modifier.height(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 3.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = bg
                )
            }
        }
    }
}

