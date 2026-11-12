@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DocumentViewModel
import com.example.ui.BatchDubItem
import com.example.ui.DubberV2Screen
import com.example.ui.VideoTranscribeScreen
import com.example.ui.MediaTaskHistoryDialog
import com.example.ui.theme.BalaSpeakTheme
import com.example.util.SubtitleTranslator
import com.example.util.LanguageOption
import com.example.util.SubtitleEntry
import com.example.ui.InAppFileExplorerDialog
import com.example.ui.OfflineTranslateCard
import com.example.ui.FilePickerMode
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.flow.collectLatest


/**
 * Video Dubber (batch) tab screen + item card (split from MainActivity.kt)
 */


/**
 * Screen 1: Batch Video Dubbing Studio
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BatchDubScreen(
    viewModel: DocumentViewModel
) {
    val context = LocalContext.current
    val batchItems by viewModel.batchItems.collectAsStateWithLifecycle()
    val isGlobalProcessing by viewModel.isGlobalProcessing.collectAsStateWithLifecycle()
    val friendlyVoicesList by viewModel.friendlyVoicesList.collectAsStateWithLifecycle()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsStateWithLifecycle()

    // Keep screen on during active dubbing
    val window = (context as? android.app.Activity)?.window
    DisposableEffect(window) {
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var activePickingItemId by remember { mutableStateOf<String?>(null) }
    var inAppVideoPickerTargetId by remember { mutableStateOf<String?>(null) }
    var inAppSrtPickerTargetId by remember { mutableStateOf<String?>(null) }
    var inAppAudioPickerTargetId by remember { mutableStateOf<String?>(null) }
    var audioTargetId by remember { mutableStateOf<String?>(null) }
    var voiceSelectorItemTarget by remember { mutableStateOf<BatchDubItem?>(null) }
    var translateItemTarget by remember { mutableStateOf<BatchDubItem?>(null) }
    var showDubbingHistoryDialog by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val itemId = activePickingItemId
        if (uri != null && itemId != null) {
            viewModel.loadVideoForBatchItem(itemId, uri)
        }
        activePickingItemId = null
    }

    val multiVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.loadMultipleVideosForDubbing(uris)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val itemId = audioTargetId
        if (uri != null && itemId != null) {
            viewModel.attachCreatedAudioForBatchItem(itemId, uri)
        }
        audioTargetId = null
    }

    val srtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val itemId = activePickingItemId
        if (uri != null && itemId != null) {
            viewModel.loadSrtForBatchItem(itemId, uri)
        }
        activePickingItemId = null
    }

    LaunchedEffect(key1 = true) {
        viewModel.toastMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Compact Dashboard Actions Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Dubbing Queue (${batchItems.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Auto-Sync Speed Fit Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // History button
                    IconButton(
                        onClick = { showDubbingHistoryDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = "Saved History",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Add multiple videos button
                    FilledTonalButton(
                        onClick = {
                            try {
                                multiVideoPickerLauncher.launch("video/*")
                            } catch (e: Exception) {
                                multiVideoPickerLauncher.launch("*/*")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp),
                        enabled = !isGlobalProcessing
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, contentDescription = "Add Videos", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Videos", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Add new task slot
                    OutlinedButton(
                        onClick = { viewModel.addBatchItem() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("add_item_button"),
                        enabled = !isGlobalProcessing
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Task", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Process all ready items
                    val readyCount = batchItems.count { it.videoFile != null && it.srtSubtitles.isNotEmpty() }
                    Button(
                        onClick = {
                            if (readyCount == 0) {
                                Toast.makeText(context, "Please select video and subtitle files first!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.startAllBatchDubbing()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (readyCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("start_all_button"),
                        enabled = !isGlobalProcessing
                    ) {
                        if (isGlobalProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Dub All", modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isGlobalProcessing) "Dubbing..." else "Dub All ($readyCount)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Batch Queue Items list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 20.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = batchItems,
                key = { _, item -> item.id }
            ) { index, item ->
                BatchDubItemCard(
                    index = index,
                    item = item,
                    totalItems = batchItems.size,
                    friendlyVoicesList = friendlyVoicesList,
                    isGlobalProcessing = isGlobalProcessing,
                    onPickVideo = {
                        inAppVideoPickerTargetId = item.id
                    },
                    onPickSrt = {
                        inAppSrtPickerTargetId = item.id
                    },
                    onOpenTranslator = {
                        translateItemTarget = item
                    },
                    onOpenVoiceSelector = {
                        voiceSelectorItemTarget = item
                    },
                    onUpdateVideoVolume = { viewModel.updateVideoVolume(item.id, it) },
                    onUpdateTtsVolume = { viewModel.updateTtsVolume(item.id, it) },
                    onAttachAudio = { inAppAudioPickerTargetId = item.id },
                    onStartDubbing = { viewModel.startVideoDubbingForBatchItem(item.id) },
                    onReset = { viewModel.resetBatchItem(item.id) },
                    onRemove = { viewModel.removeBatchItem(item.id) },
                    onShare = { viewModel.shareDubbedVideoForBatchItem(item.id) }
                )
            }
        }
    }

    // In-App File Explorer Dialog for Video
    val pickingVideoId = inAppVideoPickerTargetId
    if (pickingVideoId != null) {
        InAppFileExplorerDialog(
            mode = FilePickerMode.VIDEO,
            showHiddenFiles = showHiddenFiles,
            onFileSelected = { file ->
                viewModel.loadVideoFileDirect(pickingVideoId, file)
                inAppVideoPickerTargetId = null
            },
            onDismiss = { inAppVideoPickerTargetId = null },
            onOpenSystemPicker = {
                activePickingItemId = pickingVideoId
                inAppVideoPickerTargetId = null
                videoPickerLauncher.launch("video/*")
            }
        )
    }

    val pickingAudioId = inAppAudioPickerTargetId
    if (pickingAudioId != null) {
        InAppFileExplorerDialog(
            mode = FilePickerMode.AUDIO,
            showHiddenFiles = showHiddenFiles,
            onFileSelected = { file ->
                viewModel.attachCreatedAudioForBatchItem(pickingAudioId, Uri.fromFile(file))
                inAppAudioPickerTargetId = null
            },
            onDismiss = { inAppAudioPickerTargetId = null },
            onOpenSystemPicker = {
                audioTargetId = pickingAudioId
                inAppAudioPickerTargetId = null
                audioPickerLauncher.launch("audio/*")
            }
        )
    }

    // In-App File Explorer Dialog for Subtitle
    val pickingSrtId = inAppSrtPickerTargetId
    if (pickingSrtId != null) {
        InAppFileExplorerDialog(
            mode = FilePickerMode.SUBTITLE,
            showHiddenFiles = showHiddenFiles,
            onFileSelected = { file ->
                viewModel.loadSrtForBatchItem(pickingSrtId, Uri.fromFile(file))
                inAppSrtPickerTargetId = null
            },
            onDismiss = { inAppSrtPickerTargetId = null },
            onOpenSystemPicker = {
                activePickingItemId = pickingSrtId
                inAppSrtPickerTargetId = null
                srtPickerLauncher.launch("*/*")
            }
        )
    }

    // Modal Voice selector dialog with Voice Audition Preview
    val voiceTarget = voiceSelectorItemTarget
    if (voiceTarget != null) {
        VoiceSelectionDialog(
            viewModel = viewModel,
            currentTarget = voiceTarget,
            friendlyVoicesList = friendlyVoicesList,
            onSelectVoice = { voiceName ->
                viewModel.updateItemVoice(voiceTarget.id, voiceName)
                voiceSelectorItemTarget = null
            },
            onDismiss = {
                viewModel.stopVoicePreview()
                voiceSelectorItemTarget = null
            }
        )
    }

    // Modal In-Card Subtitle Translation Dialog
    val transTarget = translateItemTarget
    if (transTarget != null) {
        SubtitleTranslationDialog(
            viewModel = viewModel,
            item = transTarget,
            onTranslate = { targetLangCode, targetLangName ->
                viewModel.translateSubtitlesForBatchItem(transTarget.id, targetLangCode, targetLangName)
                translateItemTarget = null
            },
            onTranslateOffline = { targetLangCode, targetLangName ->
                viewModel.translateSubtitlesForBatchItemOffline(transTarget.id, targetLangCode, targetLangName)
                translateItemTarget = null
            },
            onDismiss = {
                viewModel.stopVoicePreview()
                translateItemTarget = null
            }
        )
    }

    if (showDubbingHistoryDialog) {
        MediaTaskHistoryDialog(
            viewModel = viewModel,
            onDismiss = { showDubbingHistoryDialog = false },
            onSendToDubbing = {
                showDubbingHistoryDialog = false
            }
        )
    }
}

@Composable
fun BatchDubItemCard(
    index: Int,
    item: BatchDubItem,
    totalItems: Int,
    friendlyVoicesList: List<com.example.tts.FriendlyVoice>,
    isGlobalProcessing: Boolean,
    onPickVideo: () -> Unit,
    onPickSrt: () -> Unit,
    onOpenTranslator: () -> Unit,
    onOpenVoiceSelector: () -> Unit,
    onUpdateVideoVolume: (Float) -> Unit,
    onUpdateTtsVolume: (Float) -> Unit,
    onAttachAudio: () -> Unit,
    onStartDubbing: () -> Unit,
    onReset: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("batch_item_card_$index"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isProcessing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Card Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Task #${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (totalItems > 1) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(28.dp),
                        enabled = !item.isProcessing && !isGlobalProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove task",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // File selection containers (Video & Subtitle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. VIDEO SELECTION CONTAINER
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(86.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (item.videoFile != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (item.videoFile != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = !item.isProcessing) { onPickVideo() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        if (item.videoFile != null) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                item.videoName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Video Loaded",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "1. Select Video",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "MP4 / MKV / MOV",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. SUBTITLE SELECTION CONTAINER
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(86.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (item.srtSubtitles.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (item.srtSubtitles.isNotEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = !item.isProcessing) { onPickSrt() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        if (item.srtSubtitles.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                item.srtName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "${item.srtSubtitles.size} Subtitle Lines",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "2. Select Subtitle",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                "SRT / VTT files",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Auto-Detected Language Badge
            if (item.detectedLanguage != null && item.srtSubtitles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Language: ${item.detectedLanguage.name} (${item.detectedLanguage.nativeName})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Auto-Voice Model",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Quick In-Card Translation Tool Bar
            if (item.srtSubtitles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Translate Subtitles Online",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    FilledTonalButton(
                        onClick = onOpenTranslator,
                        enabled = !item.isProcessing && !item.isTranslating,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        if (item.isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Text("Translate", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "One step, like the Windows mix. Set video sound and new audio, then attach the file.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onAttachAudio,
                enabled = item.videoFile != null && !item.isProcessing && !isGlobalProcessing,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Attach audio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))

            // DUAL AUDIO VOLUME CONTROLS (Video Sound & Dub Voice)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Video Sound (Background sound)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Video Sound", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (item.videoVolume <= 0.001f) "0% (Turbo)" else "${(item.videoVolume * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.videoVolume <= 0.001f) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = item.videoVolume,
                        onValueChange = onUpdateVideoVolume,
                        valueRange = 0f..1f,
                        enabled = !item.isProcessing,
                        modifier = Modifier.height(24.dp)
                    )
                }

                // 2. Dub Voice (TTS Sound)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("New audio", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${(item.ttsVolume * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = item.ttsVolume,
                        onValueChange = onUpdateTtsVolume,
                        valueRange = 0.1f..2.0f,
                        enabled = !item.isProcessing,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // VOICE SELECTION & AUTO-ADJUST SPEED ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice selector chip
                AssistChip(
                    onClick = onOpenVoiceSelector,
                    enabled = !item.isProcessing,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        val currentVoiceName = item.itemVoiceName ?: "System Voice"
                        val displayVoice = friendlyVoicesList.firstOrNull { it.id == currentVoiceName }?.displayName ?: currentVoiceName
                        Text(
                            displayVoice,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.weight(1.2f)
                )

                // Smart Auto-Adjust Speed Indicator
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Column {
                            Text(
                                "Auto-Adjust Speed",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                "Subtitle Sync Active",
                                fontSize = 8.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // STATUS & ACTION FOOTER
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            item.dubbedVideoFile != null -> Color(0xFF10B981)
                                            item.videoFile != null && item.srtSubtitles.isNotEmpty() -> MaterialTheme.colorScheme.primary
                                            else -> Color.Gray
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                item.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (item.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = onReset,
                                    modifier = Modifier.size(26.dp),
                                    enabled = !item.isProcessing && !isGlobalProcessing
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Reset",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Button(
                                    onClick = onStartDubbing,
                                    enabled = item.videoFile != null && item.srtSubtitles.isNotEmpty() && !isGlobalProcessing,
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Dub Video", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (item.progressText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            item.progressText,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Share Dubbed Output
            item.dubbedVideoFile?.let { file ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            file.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
