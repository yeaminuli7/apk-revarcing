@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.VoicePreset
import com.example.tts.FriendlyVoice
import com.example.tts.VoiceCast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DubberV2Screen(viewModel: DocumentViewModel) {
    val context = LocalContext.current
    val videoFile by viewModel.v2VideoFile.collectAsStateWithLifecycle()
    val videoName by viewModel.v2VideoName.collectAsStateWithLifecycle()
    val subtitleName by viewModel.v2SubtitleName.collectAsStateWithLifecycle()
    val cues by viewModel.v2Cues.collectAsStateWithLifecycle()
    val voices by viewModel.friendlyVoicesList.collectAsStateWithLifecycle()
    val rendering by viewModel.v2Rendering.collectAsStateWithLifecycle()
    val progress by viewModel.v2Progress.collectAsStateWithLifecycle()
    val progressCount by viewModel.v2ProgressCount.collectAsStateWithLifecycle()
    val output by viewModel.v2OutputFile.collectAsStateWithLifecycle()
    val status by viewModel.v2Status.collectAsStateWithLifecycle()
    val videoVolume by viewModel.v2VideoVolume.collectAsStateWithLifecycle()
    val ttsVolume by viewModel.v2TtsVolume.collectAsStateWithLifecycle()
    val presets by viewModel.voicePresets.collectAsStateWithLifecycle()
    val activePresetId by viewModel.voicePresetActiveId.collectAsStateWithLifecycle()

    var previewOpen by remember { mutableStateOf(true) }
    var mixerOpen by remember { mutableStateOf(false) }
    var presetManagerOpen by remember { mutableStateOf(false) }
    var savePresetFor by remember { mutableStateOf<FriendlyVoice?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var voiceTargetId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var seekToMs by remember { mutableLongStateOf(-1L) }
    var loadNonce by remember { mutableIntStateOf(0) }

    val videoView = remember {
        VideoView(context).apply {
            setOnPreparedListener { player ->
                durationMs = player.duration.toLong().coerceAtLeast(0L)
                prepared = true
            }
            setOnCompletionListener {
                playing = false
                positionMs = durationMs
            }
            setOnErrorListener { _, _, _ ->
                prepared = false
                playing = false
                durationMs = 0L
                positionMs = 0L
                true
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            loadNonce++
            viewModel.loadDubberV2Video(uri)
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.attachDubberV2Audio(uri)
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.loadDubberV2Subtitle(uri)
    }

    LaunchedEffect(videoFile?.absolutePath, loadNonce) {
        prepared = false
        playing = false
        positionMs = 0L
        durationMs = 0L
        seekToMs = -1L
        val file = videoFile
        if (file != null && file.exists()) {
            videoView.setVideoURI(Uri.fromFile(file))
        } else {
            try { videoView.stopPlayback() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(playing, prepared) {
        if (!prepared) return@LaunchedEffect
        if (playing) {
            try {
                val dur = try { videoView.duration.toLong() } catch (_: Exception) { 0L }
                val pos = try { videoView.currentPosition.toLong() } catch (_: Exception) { 0L }
                when {
                    dur > 0L && pos >= dur - 120L -> {
                        videoView.seekTo(0)
                        positionMs = 0L
                    }
                    kotlin.math.abs(pos - positionMs) > 250L -> {
                        // Paused at a seek target the player never reached —
                        // re-seek once so the timeline resumes in sync.
                        videoView.seekTo(positionMs.toInt().coerceAtLeast(0))
                    }
                }
                videoView.start()
            } catch (_: Exception) {
                playing = false
            }
        } else {
            try { videoView.pause() } catch (_: Exception) {}
            try { positionMs = videoView.currentPosition.toLong().coerceAtLeast(0L) } catch (_: Exception) {}
        }
        while (playing && prepared) {
            try { positionMs = videoView.currentPosition.toLong().coerceAtLeast(0L) } catch (_: Exception) {}
            if (durationMs <= 0L) durationMs = try { videoView.duration.toLong().coerceAtLeast(0L) } catch (_: Exception) { 0L }
            if (durationMs > 0L && positionMs >= durationMs - 80L) playing = false
            delay(120)
        }
    }

    LaunchedEffect(seekToMs, prepared) {
        if (prepared && seekToMs >= 0L) {
            try {
                videoView.seekTo(seekToMs.toInt())
                positionMs = seekToMs
            } catch (_: Exception) {}
            seekToMs = -1L
        }
    }

    DisposableEffect(videoView) {
        onDispose {
            try { videoView.stopPlayback() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(cues) {
        if (selectedId == null || cues.none { it.id == selectedId }) {
            selectedId = cues.firstOrNull()?.id
        }
    }

    val activeIndex = activeCueIndex(cues, positionMs)
    val activeCue = cues.getOrNull(activeIndex)
    val selectedCue = cues.firstOrNull { it.id == selectedId }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val activePreset = presets.firstOrNull { it.id == activePresetId }
    // Rows that sit above the cue rows in the same list, so "jump to block N" needs an offset.
    val headerItems = 1 + if (selectedCue != null) 3 else 0

    fun gotoCue(index: Int) {
        val cue = cues.getOrNull(index) ?: return
        selectedId = cue.id
        playing = false
        positionMs = cue.startTimeMs.coerceAtLeast(0L)
        seekToMs = cue.startTimeMs.coerceAtLeast(0L)
        coroutineScope.launch { listState.animateScrollToItem(headerItems + index) }
    }

    LaunchedEffect(activeIndex, playing) {
        if (playing && activeIndex >= 0) listState.animateScrollToItem(headerItems + activeIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ───────────────────────── top bar ─────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dubber V2", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (activePreset != null) "🎙 ${activePreset.name} · saved voice"
                    else "🎙 No saved voice yet",
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (activePreset != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (activePreset != null) {
                TextButton(
                    onClick = {
                        playing = false
                        viewModel.applyActiveVoicePreset()
                    },
                    enabled = cues.isNotEmpty() && !rendering
                ) { Text("Lock all 🔒", fontSize = 11.sp) }
            }
            TextButton(onClick = { presetManagerOpen = true }) {
                Text(if (activePreset == null) "⭐ Voices" else "Change", fontSize = 11.sp)
            }
            IconButton(onClick = { previewOpen = !previewOpen }) {
                Icon(
                    if (previewOpen) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (previewOpen) "Hide preview" else "Show preview",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { videoPicker.launch("video/*") },
                enabled = !rendering,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Icon(Icons.Outlined.Movie, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (videoName.isBlank()) "🎬 Video" else videoName,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { subtitlePicker.launch("*/*") },
                enabled = !rendering,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Icon(Icons.Outlined.Subtitles, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (subtitleName.isBlank()) "📝 Subtitle" else subtitleName,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            status,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // ───────────────────────── preview ─────────────────────────
        if (previewOpen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                if (videoFile != null) {
                    AndroidView(factory = { videoView }, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Load a video to review", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
                if (activeCue != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth(),
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = highlightedCue(activeCue, positionMs, Color(0xFFFFE082)),
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // ─────────────── everything scrolls; the render bar is pinned ───────────────
        if (cues.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Load a subtitle. Each timed block will show up here.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
            ) {
                item(key = "player") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (prepared) playing = !playing },
                            enabled = prepared && !rendering
                        ) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play"
                            )
                        }
                        Text(formatReviewTime(positionMs), fontSize = 10.sp, modifier = Modifier.width(58.dp))
                        var dragging by remember { mutableStateOf(false) }
                        var dragMs by remember { mutableLongStateOf(0L) }
                        Slider(
                            value = if (durationMs <= 0L) 0f
                            else (if (dragging) dragMs else positionMs).toFloat() / durationMs.toFloat(),
                            onValueChange = { fraction ->
                                if (durationMs > 0L) {
                                    dragging = true
                                    dragMs = (fraction * durationMs).toLong().coerceIn(0L, durationMs)
                                }
                            },
                            onValueChangeFinished = {
                                if (dragging) {
                                    positionMs = dragMs
                                    seekToMs = dragMs
                                    dragging = false
                                }
                            },
                            enabled = prepared && durationMs > 0L,
                            modifier = Modifier.weight(1f)
                        )
                        Text(formatReviewTime(durationMs), fontSize = 10.sp)
                    }
                }

                if (selectedCue != null) {
                    item(key = "nav") {
                        val selIdx = cues.indexOfFirst { it.id == selectedCue.id }
                        val prevCue = cues.getOrNull(selIdx - 1)
                        val nextCue = cues.getOrNull(selIdx + 1)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { gotoCue(selIdx - 1) },
                                    enabled = selIdx > 0 && !rendering,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) { Text("◀ Prev", fontSize = 11.sp) }
                                Text(
                                    "Block ${selIdx + 1} of ${cues.size}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { gotoCue(selIdx + 1) },
                                    enabled = selIdx < cues.size - 1 && !rendering,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) { Text("Next ▶", fontSize = 11.sp) }
                            }
                            if (prevCue != null) {
                                Text(
                                    "◀ Previous: ${prevCue.text}",
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (nextCue != null) {
                                Text(
                                    "Next: ${nextCue.text} ▶",
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item(key = "editor") {
                        CueEditor(
                            cue = selectedCue,
                            voiceLabel = voiceLabel(selectedCue.voiceName, voices),
                            enabled = !rendering,
                            onText = { viewModel.updateDubberV2CueText(selectedCue.id, it) },
                            onPickVoice = { voiceTargetId = selectedCue.id },
                            onToggleLock = { viewModel.toggleDubberV2CueLock(selectedCue.id) },
                            onTogglePin = { viewModel.toggleDubberV2CueVoicePin(selectedCue.id) },
                            onPreview = {
                                playing = false
                                viewModel.previewDubberV2Cue(selectedCue.id)
                            }
                        )
                    }
                }

                item(key = "lockrow") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lockedCount = cues.count { it.locked }
                        val pinnedCount = cues.count { it.voicePinned }
                        Text(
                            "🔒 $lockedCount locked · 📌 $pinnedCount pinned",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.setAllDubberV2CuesLocked(true) },
                            enabled = !rendering && lockedCount < cues.size,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("Lock all", fontSize = 11.sp) }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { viewModel.setAllDubberV2CuesLocked(false) },
                            enabled = !rendering && lockedCount > 0,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("Unlock", fontSize = 11.sp) }
                    }
                }

                itemsIndexed(cues, key = { _, cue -> cue.id }) { index, cue ->
                    val active = index == activeIndex
                    val selected = cue.id == selectedId
                    Surface(
                        onClick = {
                            selectedId = cue.id
                            playing = false
                            positionMs = cue.startTimeMs
                            seekToMs = cue.startTimeMs
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            active -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "#${index + 1}  ${formatReviewTime(cue.startTimeMs)} – ${formatReviewTime(cue.endTimeMs)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (cue.locked) {
                                    Text(" 🔒", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                if (cue.voicePinned) {
                                    Text(" 📌", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.toggleDubberV2CueLock(cue.id) },
                                    enabled = !rendering,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        if (cue.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                        contentDescription = if (cue.locked) "Unlock block" else "Lock block",
                                        tint = if (cue.locked) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    voiceLabel(cue.voiceName, voices),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (active) highlightedCue(cue, positionMs, MaterialTheme.colorScheme.primary)
                                else AnnotatedString(cue.text),
                                fontSize = 13.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // ───────────────────────── pinned action bar ─────────────────────────
        Surface(tonalElevation = 3.dp) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (rendering) {
                    val done = progressCount.first
                    val total = progressCount.second
                    if (total > 0) {
                        LinearProgressIndicator(
                            progress = { done.toFloat() / total.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(progress, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }

                if (mixerOpen) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Original", fontSize = 10.sp, modifier = Modifier.width(54.dp))
                        Slider(
                            value = videoVolume,
                            onValueChange = { viewModel.setDubberV2VideoVolume(it) },
                            enabled = !rendering,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Voice", fontSize = 10.sp, modifier = Modifier.width(36.dp))
                        Slider(
                            value = (ttsVolume / 2f).coerceIn(0f, 1f),
                            onValueChange = { viewModel.setDubberV2TtsVolume(it * 2f) },
                            enabled = !rendering,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { audioPicker.launch("audio/*") },
                            enabled = videoFile != null && !rendering,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) { Text("🎵 Attach audio", fontSize = 11.sp) }
                        Text(
                            "Video ${(videoVolume * 100).toInt()}% · new audio ${(ttsVolume * 100).toInt()}%",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { mixerOpen = !mixerOpen },
                        enabled = !rendering,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = "Mix settings",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.saveDubberV2Srt() },
                        enabled = cues.isNotEmpty() && !rendering,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) { Text("Save SRT", fontSize = 11.sp, maxLines = 1) }
                    Button(
                        onClick = {
                            playing = false
                            viewModel.renderDubberV2()
                        },
                        enabled = videoFile != null && cues.isNotEmpty() && !rendering,
                        modifier = Modifier.weight(1.5f).height(40.dp)
                    ) {
                        if (rendering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Render dub", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    if (output != null) {
                        IconButton(onClick = { viewModel.shareDubberV2Output() }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share dubbed video")
                        }
                    }
                }
                if (!rendering && (videoFile == null || cues.isEmpty())) {
                    Text(
                        if (videoFile == null) "🎬 ভিডিও দিন, তারপর ▶ Render dub চাপুন"
                        else "📝 সাবটাইটেল দিন — ব্লকগুলোই ডাব হবে",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }

    val voiceCue = cues.firstOrNull { it.id == voiceTargetId }
    if (voiceCue != null) {
        BlockVoiceDialog(
            voices = voices,
            selectedVoiceId = voiceCue.voiceName,
            presets = presets,
            activePresetId = activePresetId,
            onUsePreset = { preset ->
                viewModel.setActiveVoicePreset(preset.id)
                voiceTargetId = null
            },
            onSavePreset = { voice ->
                voiceTargetId = null
                savePresetFor = voice
            },
            onPreview = { viewModel.previewVoice(it) },
            onThisBlock = {
                viewModel.updateDubberV2CueVoice(voiceCue.id, it, pin = true)
                voiceTargetId = null
            },
            onAllExceptPinned = {
                viewModel.applyDubberV2VoiceToAll(it, skipPinned = true)
                voiceTargetId = null
            },
            onAllBlocks = {
                viewModel.applyDubberV2VoiceToAll(it, skipPinned = false)
                voiceTargetId = null
            },
            onDismiss = { voiceTargetId = null }
        )
    }

    if (presetManagerOpen) {
        VoicePresetManagerDialog(
            presets = presets,
            activePresetId = activePresetId,
            voiceLabelOf = { id -> voiceLabel(id, voices) },
            onApply = { preset ->
                viewModel.setActiveVoicePreset(preset.id)
                presetManagerOpen = false
            },
            onRename = { preset, name -> viewModel.renameVoicePreset(preset.id, name) },
            onDelete = { preset -> viewModel.deleteVoicePreset(preset.id) },
            onClearActive = {
                viewModel.setActiveVoicePreset(null)
                presetManagerOpen = false
            },
            onDismiss = { presetManagerOpen = false }
        )
    }

    val presetVoice = savePresetFor
    if (presetVoice != null) {
        SaveVoicePresetDialog(
            voice = presetVoice,
            existing = presets.firstOrNull { it.voiceId == presetVoice.id },
            onSave = { name ->
                viewModel.saveVoicePreset(name, presetVoice.id)
                savePresetFor = null
            },
            onDismiss = { savePresetFor = null }
        )
    }
}

@Composable
private fun CueEditor(
    cue: DubCue,
    voiceLabel: String,
    enabled: Boolean,
    onText: (String) -> Unit,
    onPickVoice: () -> Unit,
    onToggleLock: () -> Unit,
    onTogglePin: () -> Unit,
    onPreview: () -> Unit
) {
    var draft by remember(cue.id, cue.text) { mutableStateOf(cue.text) }
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                onText(it)
            },
            enabled = enabled && !cue.locked,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            label = {
                Text(
                    if (cue.locked) "Block ${cue.index} text (locked)"
                    else "Block ${cue.index} text"
                )
            },
            supportingText = {
                if (cue.locked) Text("Text is frozen. Unlock to edit. Voice can still change.")
            },
            shape = RoundedCornerShape(10.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPickVoice, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(voiceLabel, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onToggleLock, enabled = enabled) {
                Icon(
                    if (cue.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = if (cue.locked) "Unlock this block" else "Lock this block",
                    tint = if (cue.locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onTogglePin, enabled = enabled) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = if (cue.voicePinned) "Unpin this voice" else "Pin this voice",
                    tint = if (cue.voicePinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onPreview, enabled = enabled && draft.isNotBlank()) {
                Icon(Icons.Rounded.VolumeUp, contentDescription = "Preview this block")
            }
        }
    }
}

@Composable
private fun BlockVoiceDialog(
    voices: List<FriendlyVoice>,
    selectedVoiceId: String?,
    presets: List<VoicePreset>,
    activePresetId: String?,
    onUsePreset: (VoicePreset) -> Unit,
    onSavePreset: (FriendlyVoice) -> Unit,
    onPreview: (FriendlyVoice) -> Unit,
    onThisBlock: (String) -> Unit,
    onAllExceptPinned: (String) -> Unit,
    onAllBlocks: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var pending by remember { mutableStateOf(selectedVoiceId) }
    val languages = listOf("All", "Bengali", "English", "Hindi", "Urdu", "Arabic")
    val filtered = voices.filter { voice ->
        val search = query.isBlank() ||
            voice.displayName.contains(query, ignoreCase = true) ||
            voice.languageGroup.contains(query, ignoreCase = true)
        val lang = when (filter) {
            "Bengali" -> voice.locale.language.equals("bn", true)
            "English" -> voice.locale.language.equals("en", true)
            "Hindi" -> voice.locale.language.equals("hi", true)
            "Urdu" -> voice.locale.language.equals("ur", true)
            "Arabic" -> voice.locale.language.equals("ar", true)
            else -> true
        }
        search && lang
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Who is speaking?", fontWeight = FontWeight.Bold)
                        Text("English: 31 characters · Bangla & Hindi: 6 each. Tap 🔊 to hear one.", fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                if (presets.isNotEmpty()) {
                    Text(
                        "⭐ Saved voices — one tap locks it on every block",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = preset.id == activePresetId,
                                onClick = { onUsePreset(preset) },
                                label = { Text("⭐ ${preset.name}", fontSize = 11.sp, maxLines = 1) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search a person or language") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languages.forEach { lang ->
                        FilterChip(
                            selected = filter == lang,
                            onClick = { filter = lang },
                            label = { Text(lang, fontSize = 11.sp) }
                        )
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 280.dp)) {
                    itemsIndexed(filtered, key = { _, voice -> voice.id }) { _, voice ->
                        val selected = pending == voice.id
                        Surface(
                            onClick = { pending = voice.id },
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(voice.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        voice.languageGroup + if (voice.ready) "" else " · ⏳ কপি হচ্ছে…",
                                        fontSize = 11.sp,
                                        color = if (voice.ready) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                                IconButton(onClick = { onPreview(voice) }) {
                                    Icon(Icons.Rounded.VolumeUp, contentDescription = "Audition", modifier = Modifier.size(18.dp))
                                }
                                if (selected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    Text(
                        "Preparing the voices… this fills in by itself in a moment.",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Text(
                    "\u201CThis block\u201D pins the voice here — it survives reloads and restarts.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(
                        onClick = { pending?.let(onAllBlocks) },
                        enabled = pending != null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("All blocks", fontSize = 11.sp, maxLines = 1) }
                    OutlinedButton(
                        onClick = { pending?.let(onAllExceptPinned) },
                        enabled = pending != null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1.3f)
                    ) { Text("All except pinned", fontSize = 11.sp, maxLines = 1) }
                    Button(
                        onClick = { pending?.let(onThisBlock) },
                        enabled = pending != null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1.3f)
                    ) { Text("This block 📌", fontSize = 11.sp, maxLines = 1) }
                }
                val pendingVoice = voices.firstOrNull { it.id == pending }
                OutlinedButton(
                    onClick = { pendingVoice?.let(onSavePreset) },
                    enabled = pendingVoice != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(
                        if (pendingVoice == null) "⭐ Save this voice…"
                        else "⭐ Save “${pendingVoice.displayName}” for one-tap reuse",
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun activeCueIndex(cues: List<DubCue>, positionMs: Long): Int {
    if (cues.isEmpty()) return -1
    val inside = cues.indexOfFirst { positionMs >= it.startTimeMs && positionMs < it.endTimeMs }
    if (inside >= 0) return inside
    return cues.indexOfLast { positionMs >= it.startTimeMs }
}

private fun highlightedCue(cue: DubCue, positionMs: Long, color: Color) = buildAnnotatedString {
    val text = cue.text
    val words = Regex("\\S+").findAll(text).toList()
    if (words.isEmpty() || positionMs < cue.startTimeMs || positionMs >= cue.endTimeMs) {
        append(text)
        return@buildAnnotatedString
    }
    val span = (cue.endTimeMs - cue.startTimeMs).coerceAtLeast(1L)
    val elapsed = (positionMs - cue.startTimeMs).coerceIn(0L, span)
    val index = ((elapsed.toDouble() / span.toDouble()) * words.size).toInt().coerceIn(0, words.lastIndex)
    val range = words[index].range
    append(text.substring(0, range.first))
    withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
        append(text.substring(range.first, range.last + 1))
    }
    if (range.last + 1 < text.length) append(text.substring(range.last + 1))
}

private fun voiceLabel(voiceId: String?, voices: List<FriendlyVoice>): String {
    if (voiceId.isNullOrBlank()) return "Young man"
    val match = voices.firstOrNull { it.id == voiceId }
    if (match != null) return match.displayName
    return VoiceCast.labelOf(voiceId) ?: "Custom voice"
}

private fun formatReviewTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val hours = safe / 3_600_000L
    val minutes = (safe / 60_000L) % 60L
    val seconds = (safe / 1_000L) % 60L
    val millis = safe % 1_000L
    return if (hours > 0L) {
        "%d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    } else {
        "%02d:%02d.%03d".format(minutes, seconds, millis)
    }
}

/**
 * Saved voices ("locked" voices). Pick a character in the picker, save it here once, and
 * from then on it is one tap — for every block of this video and for the next video too.
 */
@Composable
private fun VoicePresetManagerDialog(
    presets: List<VoicePreset>,
    activePresetId: String?,
    voiceLabelOf: (String) -> String,
    onApply: (VoicePreset) -> Unit,
    onRename: (VoicePreset, String) -> Unit,
    onDelete: (VoicePreset) -> Unit,
    onClearActive: () -> Unit,
    onDismiss: () -> Unit
) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⭐ Saved voices", fontWeight = FontWeight.Bold)
                        Text(
                            "Save a character once. After that one tap locks that voice on every block — and every new video starts with it.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (presets.isEmpty()) {
                    Text(
                        "Nothing saved yet.\n\nOpen any block → change voice → pick a character → “⭐ Save … for one-tap reuse”.",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(presets, key = { it.id }) { preset ->
                            val isActive = preset.id == activePresetId
                            Surface(
                                onClick = { onApply(preset) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (renamingId == preset.id) {
                                        OutlinedTextField(
                                            value = renameDraft,
                                            onValueChange = { renameDraft = it },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = {
                                            onRename(preset, renameDraft)
                                            renamingId = null
                                        }) { Text("OK", fontSize = 11.sp) }
                                    } else {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                (if (isActive) "🎙 " else "⭐ ") + preset.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                voiceLabelOf(preset.voiceId) +
                                                    if (isActive) " · active" else "",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                renamingId = preset.id
                                                renameDraft = preset.name
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Edit,
                                                contentDescription = "Rename",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDelete(preset) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = "Delete",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (activePresetId != null) {
                    TextButton(onClick = onClearActive) {
                        Text("Turn the lock off — go back to automatic detection", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** Asks for a name before a voice becomes a one-tap preset. */
@Composable
private fun SaveVoicePresetDialog(
    voice: FriendlyVoice,
    existing: VoicePreset?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(voice.id) { mutableStateOf(existing?.name ?: voice.displayName) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    if (existing == null) "⭐ Save this voice" else "⭐ Update this voice",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${voice.displayName} · ${voice.languageGroup}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name it") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Saved voices appear as ⭐ chips in the voice picker and in the Dubber V2 header — one tap applies the voice to every block, and the next video starts with it. You can rename or delete it any time under “Change”.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", fontSize = 12.sp) }
                    Button(
                        onClick = { onSave(name) },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1.3f)
                    ) { Text(if (existing == null) "Save & lock" else "Update & lock", fontSize = 12.sp) }
                }
            }
        }
    }
}
