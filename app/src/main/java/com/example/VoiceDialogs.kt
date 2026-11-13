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
 * Voice selection dialog (split from MainActivity.kt)
 */


@Composable
fun VoiceSelectionDialog(
    viewModel: DocumentViewModel? = null,
    currentTarget: BatchDubItem,
    friendlyVoicesList: List<com.example.tts.FriendlyVoice>,
    onSelectVoice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLanguageFilter by remember { mutableStateOf("All") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Choose a speaker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "English: 31 characters (all ages + monster, robot, ghost…) · Bangla & Hindi: 6 personas each.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search voice by name or language...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick language filter chips
                val languages = listOf("All", "Bengali", "English", "Hindi", "Urdu", "Arabic", "Spanish", "French")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languages.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguageFilter == lang,
                            onClick = { selectedLanguageFilter = lang },
                            label = { Text(lang, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                val filteredList = friendlyVoicesList.filter { voice ->
                    val matchesSearch = searchQuery.isBlank() ||
                            voice.displayName.contains(searchQuery, ignoreCase = true) ||
                            voice.languageGroup.contains(searchQuery, ignoreCase = true) ||
                            voice.locale.displayName.contains(searchQuery, ignoreCase = true)

                    val matchesFilter = when (selectedLanguageFilter) {
                        "All" -> true
                        "Bengali" -> voice.locale.language.equals("bn", ignoreCase = true)
                        "English" -> voice.locale.language.equals("en", ignoreCase = true)
                        "Hindi" -> voice.locale.language.equals("hi", ignoreCase = true)
                        "Urdu" -> voice.locale.language.equals("ur", ignoreCase = true)
                        "Arabic" -> voice.locale.language.equals("ar", ignoreCase = true)
                        "Spanish" -> voice.locale.language.equals("es", ignoreCase = true)
                        "French" -> voice.locale.language.equals("fr", ignoreCase = true)
                        else -> true
                    }
                    matchesSearch && matchesFilter
                }

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Preparing the voices…\nThe bundled voice models are copied into the app on first launch (a minute or two). This list fills in by itself — you do not need to restart the app.\n\nIf it stays empty: Settings → 🎙️ AI Voice Packs shows the progress or the error.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredList) { voice ->
                            val isSelected = currentTarget.itemVoiceName == voice.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectVoice(voice.id) },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            voice.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            voice.languageGroup,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (viewModel != null) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.previewVoice(voice.id, voice.locale)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.VolumeUp,
                                                    contentDescription = "Audition voice",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
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
