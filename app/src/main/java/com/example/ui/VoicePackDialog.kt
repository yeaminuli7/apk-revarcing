package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.VoiceCast
import com.example.tts.neural.NeuralVoiceManager
import kotlinx.coroutines.launch

/**
 * Voice pack status + diagnostics.
 *
 * The point of this dialog in the fully-offline build is to be honest: it shows the
 * real state of every language (bundled? copied? how many files, how many MB), gives
 * a Retry button when a copy failed, and can synthesise one sentence on the spot so
 * "voices are missing" turns into a concrete message instead of an empty list.
 */
@Composable
fun NeuralVoicePackDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { NeuralVoiceManager.get(context) }
    val states by manager.states.collectAsState()
    val busy by manager.busy.collectAsState()
    val lastError by manager.lastError.collectAsState()
    val scope = rememberCoroutineScope()
    var testResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showDetails by remember { mutableStateOf(false) }

    val readyCount = manager.packs.count { manager.isInstalled(it.langCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🎙️ Built-in Neural Voices", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "সব কণ্ঠ APK-এর ভেতরেই আছে — ডাউনলোড লাগে না। " +
                        "প্রথমবার মডেলগুলো অ্যাপের ফোল্ডারে কপি হয় (১–২ মিনিট), তারপর সব কণ্ঠ ব্যবহারযোগ্য।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "প্রস্তুত: $readyCount / ${manager.packs.size} ভাষা",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))

                for (pack in manager.packs) {
                    val state = states[pack.langCode] ?: NeuralVoiceManager.PackState.NotDownloaded
                    val installed = manager.isInstalled(pack.langCode)
                    val isBusy = pack.langCode in busy
                    val error = lastError[pack.langCode]
                        ?: ((state as? NeuralVoiceManager.PackState.Failed)?.message)
                    val (files, bytes) = remember(pack.langCode, state) {
                        manager.diskUsage(pack.langCode)
                    }

                    val statusText: String = when {
                        isBusy && installed -> "🎙️ ইঞ্জিন চলছে…"
                        state is NeuralVoiceManager.PackState.Preparing ->
                            "⏳ কপি হচ্ছে… ${state.copied} / ${state.total} ফাইল"
                        state is NeuralVoiceManager.PackState.Downloading && !installed &&
                            state.total > 0 -> "↓ ডাউনলোড ${state.received * 100 / state.total}%"
                        installed -> "✅ প্রস্তুত — ${files} ফাইল, ${mbOf(bytes)} MB"
                        else -> "⚠ এখনও প্রস্তুত নয় — নিচের বাটন চাপুন"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${pack.displayName}  ·  ${pack.qualityNote}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        statusText,
                                        fontSize = 11.sp,
                                        color = if (installed) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else if (installed) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (error != null && !isBusy) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "⚠ $error",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (showDetails) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "source: ${manager.assetSource(pack.langCode)}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "disk: ${manager.filesStatus(pack.langCode)}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "path: ${manager.packDir(pack.langCode).absolutePath}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    enabled = !isBusy,
                                    onClick = { manager.retryProvisioning(pack.langCode, force = true) }
                                ) {
                                    Text(if (installed) "নতুন করে কপি" else "এখনই কপি করুন", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    enabled = !isBusy && installed,
                                    onClick = {
                                        scope.launch {
                                            val msg = manager.selfTest(pack.langCode)
                                            testResults = testResults + (pack.langCode to msg)
                                        }
                                    }
                                ) {
                                    Text("টেস্ট", fontSize = 11.sp)
                                }
                            }
                            testResults[pack.langCode]?.let {
                                Text(it, fontSize = 10.sp)
                            }

                            if (installed) {
                                Spacer(Modifier.height(6.dp))
                                Text("Preview voices:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (role in VoiceCast.roles.filter {
                                        it.onlyLang == null || it.onlyLang == pack.langCode
                                    }) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "• ${role.bangla}${if (role.onlyLang != null) " · ${role.english}" else ""}",
                                                fontSize = 11.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                enabled = !isBusy,
                                                onClick = {
                                                    scope.launch {
                                                        val err = manager.preview(pack.langCode, role.id)
                                                        if (err != null) {
                                                            manager.reportPreviewError(pack.langCode, err)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.PlayArrow,
                                                    contentDescription = "Preview ${role.bangla}",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    "নোট: ইংরেজি — Kokoro v1.0 (৩১ ক্যারেক্টার), হিন্দি — Piper (৩ স্পিকার), বাংলা — MMS-TTS। " +
                        "সমস্যা হলে ফোনে অন্তত ১ GB ফাঁকা জায়গা রাখুন এবং Settings → Storage দেখুন।",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "বিস্তারিত লুকান" else "বিস্তারিত দেখুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

private fun mbOf(bytes: Long): Long = (bytes + 1_048_575L) / 1_048_576L
