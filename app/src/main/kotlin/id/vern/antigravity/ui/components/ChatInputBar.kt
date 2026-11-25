package id.vern.antigravity.ui.components

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.vern.antigravity.R
import id.vern.antigravity.data.AttachedFile
import id.vern.antigravity.data.QueuedMessage
import id.vern.antigravity.data.RunningTask
import id.vern.antigravity.data.RunningSubagent
import id.vern.antigravity.data.SlashAction
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityRed
import kotlin.math.cos
import kotlin.math.sin

private val AuroraColorInts = intArrayOf(
    0xFF4285F4.toInt(),
    0xFF8B5CF6.toInt(),
    0xFFEA4335.toInt(),
    0xFFFBBC05.toInt(),
    0xFF34A853.toInt(),
    0xFF24C6DC.toInt(),
    0xFF4285F4.toInt()
)

private data class MentionItem(
    val token: String,
    val descRes: Int,
    val icon: ImageVector,
    val isFile: Boolean = false
)

private data class SecurityPresetItem(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val isDefault: Boolean = false
)

private val SecurityPresets = listOf(
    SecurityPresetItem(
        id = "Request Review",
        titleRes = R.string.security_preset_request_review_title,
        descRes = R.string.security_preset_request_review_desc,
        icon = Icons.Outlined.Visibility
    ),
    SecurityPresetItem(
        id = "Default",
        titleRes = R.string.security_preset_default_title,
        descRes = R.string.security_preset_default_desc,
        icon = Icons.Outlined.Lock
    ),
    SecurityPresetItem(
        id = "Auto",
        titleRes = R.string.security_preset_auto_title,
        descRes = R.string.security_preset_auto_desc,
        icon = Icons.Outlined.Shield
    ),
    SecurityPresetItem(
        id = "Turbo",
        titleRes = R.string.security_preset_turbo_title,
        descRes = R.string.security_preset_turbo_desc,
        icon = Icons.Outlined.GppMaybe,
        isDefault = true
    )
)

private fun isPresetSelected(itemId: String, currentPreset: String): Boolean {
    val norm = currentPreset.lowercase()
    return when (itemId) {
        "Turbo" -> norm == "turbo" || norm == "turbo mode"
        "Auto" -> norm == "auto" || norm == "permissive"
        "Default" -> norm == "default" || norm == "standard"
        "Request Review" -> norm == "request review" || norm == "request_review"
        else -> itemId.equals(currentPreset, ignoreCase = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatInputBar(
    isExecuting: Boolean,
    selectedModel: String,
    thinkingBudget: String,
    attachedFiles: List<AttachedFile>,
    queuedMessages: List<QueuedMessage> = emptyList(),
    runningTasks: List<RunningTask> = emptyList(),
    runningSubagents: List<RunningSubagent> = emptyList(),
    onSendMessage: (String) -> Unit,
    onModelSelectorClick: () -> Unit,
    onAttachFileClick: () -> Unit,
    onRemoveAttachment: (AttachedFile) -> Unit,
    onVoiceInputClick: (() -> Unit)? = null,
    onStopExecution: () -> Unit = {},
    onProcessQueuedMessageNow: (String) -> Unit = {},
    onEditQueuedMessage: (String) -> QueuedMessage? = { null },
    onRemoveQueuedMessage: (String) -> Unit = {},
    onStopTask: (String) -> Unit = {},
    onTaskClick: (RunningTask) -> Unit = {},
    onSubagentClick: (RunningSubagent) -> Unit = {},
    onStopSubagent: (String) -> Unit = {},
    onGetWorkspaceFiles: (String) -> List<String> = { emptyList() },
    slashActions: List<SlashAction> = emptyList(),
    onSlashMenuOpened: () -> Unit = {},
    externalInputText: String? = null,
    onExternalInputTextConsumed: () -> Unit = {},
    securityPreset: String = "Turbo",
    onSecurityPresetChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var isQueuedExpanded by remember { mutableStateOf(true) }
    var isTaskRunningExpanded by remember { mutableStateOf(true) }
    var isSubagentRunningExpanded by remember { mutableStateOf(true) }
    var showSecurityMenu by remember { mutableStateOf(false) }

    val currentPresetDisplayLabel = when {
        securityPreset.startsWith("Turbo", ignoreCase = true) -> stringResource(R.string.security_preset_turbo_title)
        securityPreset.equals("Auto", ignoreCase = true) || securityPreset.equals("Permissive", ignoreCase = true) -> stringResource(R.string.security_preset_auto_title)
        securityPreset.equals("Default", ignoreCase = true) || securityPreset.equals("Standard", ignoreCase = true) -> stringResource(R.string.security_preset_default_title)
        securityPreset.equals("Request Review", ignoreCase = true) -> stringResource(R.string.security_preset_request_review_title)
        else -> securityPreset
    }

    val currentPresetIcon = when {
        securityPreset.startsWith("Turbo", ignoreCase = true) -> Icons.Outlined.GppMaybe
        securityPreset.equals("Auto", ignoreCase = true) || securityPreset.equals("Permissive", ignoreCase = true) -> Icons.Outlined.Shield
        securityPreset.equals("Default", ignoreCase = true) || securityPreset.equals("Standard", ignoreCase = true) -> Icons.Outlined.Lock
        securityPreset.equals("Request Review", ignoreCase = true) -> Icons.Outlined.Visibility
        else -> Icons.Outlined.GppMaybe
    }

    LaunchedEffect(externalInputText) {
        if (!externalInputText.isNullOrBlank()) {
            inputText = externalInputText
            onExternalInputTextConsumed()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "AgentCardAurora")
    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AuroraRotation"
    )
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuroraPulse"
    )

    val rotationMatrix = remember { android.graphics.Matrix() }
    val borderStrokePaint = remember {
        Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
            }
        }
    }

    val voicePrompt = stringResource(R.string.chat_voice_prompt)
    val voiceUnavailableMsg = stringResource(R.string.chat_voice_unavailable, "")

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) spokenText else "$inputText $spokenText"
            }
        }
    }

    val atIndex = inputText.lastIndexOf('@')
    val showMentions = atIndex >= 0 &&
        (atIndex == 0 || inputText[atIndex - 1].isWhitespace()) &&
        !inputText.substring(atIndex + 1).contains(' ') &&
        !inputText.substring(atIndex + 1).contains('\n')
    val atQuery = if (showMentions) inputText.substring(atIndex + 1).lowercase() else ""

    val slashIndex = inputText.lastIndexOf('/')
    val showActions = !showMentions && slashIndex >= 0 &&
        (slashIndex == 0 || inputText[slashIndex - 1].isWhitespace()) &&
        !inputText.substring(slashIndex + 1).contains(' ') &&
        !inputText.substring(slashIndex + 1).contains('\n')
    val slashQuery = if (showActions) inputText.substring(slashIndex + 1).lowercase() else ""

    LaunchedEffect(showActions) {
        if (showActions) onSlashMenuOpened()
    }

    val defaultMentions = listOf(
        MentionItem("workspace", R.string.chat_mention_workspace, Icons.Default.Folder),
        MentionItem("terminal", R.string.chat_mention_terminal, Icons.Default.Terminal),
        MentionItem("git", R.string.chat_mention_git, Icons.Default.History),
        MentionItem("problems", R.string.chat_mention_problems, Icons.Default.Warning),
        MentionItem("docs", R.string.chat_mention_docs, Icons.Default.Description),
        MentionItem("web", R.string.chat_mention_web, Icons.Default.Language)
    )

    Column(modifier = modifier) {

        AnimatedVisibility(visible = showMentions) {
            val matchingBuiltins = defaultMentions.filter { it.token.contains(atQuery) }
            val matchingFiles = remember(atQuery) {
                onGetWorkspaceFiles(atQuery).take(8).map { path ->
                    MentionItem(path, R.string.chat_mention_file, Icons.Default.Description, isFile = true)
                }
            }
            val allMentions = matchingBuiltins + matchingFiles

            if (allMentions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    tonalElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = stringResource(R.string.chat_mentions_header),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(allMentions, key = { it.token }) { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val prefix = inputText.substring(0, atIndex)
                                            inputText = "$prefix@${item.token} "
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    if (item.isFile) {
                                        FileBadge(fileName = item.token)
                                    } else {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "@${item.token}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = if (item.isFile) FontFamily.Monospace else FontFamily.Default
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(item.descRes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showActions) {
            val matchingActions = slashActions.filter { it.cmd.contains(slashQuery, ignoreCase = true) }
            if (slashActions.isEmpty() || matchingActions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    tonalElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        if (slashActions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_actions_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(matchingActions, key = { "cli-${it.cmd}" }) { item ->
                                    SlashActionRow(
                                        item = item,
                                        onPick = {
                                            val prefix = inputText.substring(0, slashIndex)
                                            inputText = "$prefix/${item.cmd} "
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = queuedMessages.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .clickable { isQueuedExpanded = !isQueuedExpanded }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.chat_queued_messages_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${queuedMessages.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.chat_queued_messages_subtitle),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isQueuedExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.chat_expand_collapse),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isQueuedExpanded) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            queuedMessages.forEach { qm ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = qm.content,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))

                                    IconButton(
                                        onClick = { onProcessQueuedMessageNow(qm.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = stringResource(R.string.chat_queued_btn_process_now),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val item = onEditQueuedMessage(qm.id)
                                            if (item != null) {
                                                inputText = item.content
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.chat_queued_btn_edit),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveQueuedMessage(qm.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.chat_queued_btn_delete),
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = runningSubagents.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val subagentCount = runningSubagents.size
                    val headerText = if (subagentCount == 1) {
                        stringResource(R.string.chat_subagent_running, 1)
                    } else {
                        stringResource(R.string.chat_subagents_running, subagentCount)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { isSubagentRunningExpanded = !isSubagentRunningExpanded }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (isSubagentRunningExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.chat_expand_collapse),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isSubagentRunningExpanded) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            runningSubagents.forEach { subagent ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSubagentClick(subagent) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = AntigravityBlue
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = subagent.role.ifEmpty { subagent.typeName.ifEmpty { stringResource(R.string.subagent_default_title) } },
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { onStopSubagent(subagent.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = stringResource(R.string.chat_subagent_stop),
                                            tint = AntigravityRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = runningTasks.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val taskCount = runningTasks.size
                    val headerText = if (taskCount == 1) {
                        stringResource(R.string.chat_task_running, 1)
                    } else {
                        stringResource(R.string.chat_tasks_running, taskCount)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { isTaskRunningExpanded = !isTaskRunningExpanded }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (isTaskRunningExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.chat_expand_collapse),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isTaskRunningExpanded) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            runningTasks.forEach { task ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onTaskClick(task) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = AntigravityBlue
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = task.command,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { onStopTask(task.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = stringResource(R.string.chat_task_stop),
                                            tint = AntigravityRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
            val isDark = surfaceColor.luminance() < 0.5f
            val baseAlpha = (if (isDark) 0.52f else 0.40f) * (if (isExecuting) 1.25f else 1.0f)

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(radius = 24.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        } else {
                            Modifier
                        }
                    )
            ) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                val rx = (w / 2f) * 0.94f
                val ry = (h / 2f) * 0.88f
                val spotRadius = maxOf(h * 0.95f, 65.dp.toPx()) * pulseGlow

                val glowNodes = listOf(
                    Pair(Color(0xFF4285F4), 0f),
                    Pair(Color(0xFF8B5CF6), 60f),
                    Pair(Color(0xFFEA4335), 120f),
                    Pair(Color(0xFFFBBC05), 180f),
                    Pair(Color(0xFF34A853), 240f),
                    Pair(Color(0xFF24C6DC), 300f)
                )

                for ((color, angleOffset) in glowNodes) {
                    val rad = (rotationDegrees + angleOffset) * (Math.PI.toFloat() / 180f)
                    val px = cx + rx * cos(rad)
                    val py = cy + ry * sin(rad)

                    val radialBrush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = (baseAlpha * pulseGlow).coerceIn(0f, 1f)),
                            color.copy(alpha = (baseAlpha * 0.45f * pulseGlow).coerceIn(0f, 1f)),
                            Color.Transparent
                        ),
                        center = Offset(px, py),
                        radius = spotRadius
                    )

                    drawCircle(
                        brush = radialBrush,
                        radius = spotRadius,
                        center = Offset(px, py)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()

                        val strokeWidth = 1.35.dp.toPx()
                        val halfStroke = strokeWidth / 2f
                        val cornerRadius = 20.dp.toPx()

                        val sweepShader = android.graphics.SweepGradient(
                            size.width / 2f,
                            size.height / 2f,
                            AuroraColorInts,
                            null
                        )
                        rotationMatrix.reset()
                        rotationMatrix.postRotate(rotationDegrees, size.width / 2f, size.height / 2f)
                        sweepShader.setLocalMatrix(rotationMatrix)

                        borderStrokePaint.asFrameworkPaint().strokeWidth = strokeWidth
                        borderStrokePaint.asFrameworkPaint().shader = sweepShader

                        drawIntoCanvas { canvas ->
                            canvas.drawRoundRect(
                                left = halfStroke,
                                top = halfStroke,
                                right = size.width - halfStroke,
                                bottom = size.height - halfStroke,
                                radiusX = cornerRadius - halfStroke,
                                radiusY = cornerRadius - halfStroke,
                                paint = borderStrokePaint
                            )
                        }
                    },
                tonalElevation = 2.dp
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {

                AnimatedVisibility(visible = attachedFiles.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        attachedFiles.forEach { file ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        modifier = Modifier.widthIn(max = 140.dp)
                                    )
                                    IconButton(
                                        onClick = { onRemoveAttachment(file) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.chat_attachment_remove),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp, max = 120.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_input_placeholder),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                fontSize = 12.5.sp
                            ),
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }

                    BasicTextField(
                        value = inputText,
                        onValueChange = { newText ->
                            inputText = newText
                        },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(AntigravityBlue),
                        singleLine = false,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() || attachedFiles.isNotEmpty()) {
                                    val text = inputText
                                    inputText = ""
                                    onSendMessage(text)
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    val canAddMoreAttachments = attachedFiles.size < 5
                    Box(
                        modifier = Modifier
                            .requiredSize(32.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canAddMoreAttachments) { onAttachFileClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.chat_attachment_add),
                            tint = if (canAddMoreAttachments) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    val pillText = when {
                        selectedModel.isBlank() -> stringResource(R.string.chat_model_select)
                        thinkingBudget.isNotBlank() -> "$selectedModel $thinkingBudget"
                        else -> selectedModel
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .widthIn(max = 135.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onModelSelectorClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = pillText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.chat_model_select),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .widthIn(max = 105.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showSecurityMenu = !showSecurityMenu }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = currentPresetIcon,
                                    contentDescription = stringResource(R.string.chat_security_preset),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = currentPresetDisplayLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        MaterialTheme(
                            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(14.dp))
                        ) {
                            DropdownMenu(
                                expanded = showSecurityMenu,
                                onDismissRequest = { showSecurityMenu = false },
                                modifier = Modifier
                                    .width(295.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Text(
                                    text = stringResource(R.string.security_preset_local_permissions),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
                                )
                                SecurityPresets.forEach { item ->
                                    val isSelected = isPresetSelected(item.id, securityPreset)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSecurityPresetChange(item.id)
                                                showSecurityMenu = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = stringResource(item.titleRes),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 13.sp
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (item.isDefault) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.security_preset_badge_default),
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontSize = 9.5.sp,
                                                                    fontWeight = FontWeight.Normal
                                                                ),
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.Info,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(10.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = stringResource(item.descRes),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                lineHeight = 14.sp
                                            )
                                        }
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .requiredSize(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (onVoiceInputClick != null) {
                                    onVoiceInputClick()
                                } else {
                                    try {
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
                                        }
                                        speechLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "$voiceUnavailableMsg: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = stringResource(R.string.chat_voice_input),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    val hasInput = inputText.isNotBlank() || attachedFiles.isNotEmpty()
                    if (isExecuting && !hasInput) {

                        Box(
                            modifier = Modifier
                                .requiredSize(32.dp)
                                .clip(CircleShape)
                                .background(AntigravityRed.copy(alpha = 0.2f))
                                .clickable { onStopExecution() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = stringResource(R.string.chat_btn_stop),
                                tint = AntigravityRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {

                        val canSend = hasInput
                        Box(
                            modifier = Modifier
                                .requiredSize(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) AntigravityBlue
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = canSend) {
                                    val text = inputText
                                    inputText = ""
                                    onSendMessage(text)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.chat_btn_send),
                                tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
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
private fun SlashActionRow(item: SlashAction, onPick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "/${item.cmd}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            val desc = item.descText?.takeIf { it.isNotBlank() }.orEmpty()
            if (desc.isNotBlank()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

