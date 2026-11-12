package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.VoiceCast
import com.example.tts.neural.NeuralVoiceManager
import kotlinx.coroutines.launch

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎙️ Built-in Neural Voices", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "সব কণ্ঠ বিল্ট-ইন — ইনস্টল করলেই তৎক্ষণালে ব্যবহার করা যাবে। আলাদা করে ডাউনলোডের প্রয়োজন নেই।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                for (pack in manager.packs) {
                    val state = states[pack.langCode] ?: NeuralVoiceManager.PackState.NotDownloaded
                    val installed = manager.isInstalled(pack.langCode)
                    val isBusy = pack.langCode in busy
                    val error = lastError[pack.langCode]
                        ?: ((state as? NeuralVoiceManager.PackState.Failed)?.message)

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
                                        when {
                                            isBusy -> "Engine loading / speaking… (একটু অপেক্ষা করুন)"
                                            state is NeuralVoiceManager.PackState.Ready || installed ->
                                                "🎙️ বিল্ট-ইন সক্রিয় — এই ভাষায় ৬ কণ্ঠ ব্যবহার করা যাবে"
                                            else ->
                                                "⏳ প্যাক প্রস্তুত করা হচ্ছে…"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else if (state is NeuralVoiceManager.PackState.Ready || installed) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Text(
                                        "⚠ Preparing…",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
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
                    "নোট: সব কণ্ঠ বিল্ট-ইন। ইংরেজি — Kokoro v1.0 (af_heart সহ ৫৩ স্পিকার), হিন্দি — Piper (pratham/rohan/priyamvada), বাংলা — MMS-TTS। সমস্যা হলে ফোনে অন্তত ১ GB ফাঁকা জায়গা রাখুন।",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

private fun mbOf(bytes: Long): Long = (bytes + 1_048_575L) / 1_048_576L
