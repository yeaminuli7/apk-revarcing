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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Rolling in-app diagnostics log (native crashes / OOM are invisible otherwise).
        com.example.util.CrashLog.install(applicationContext)
        enableEdgeToEdge()
        setContent {
            val viewModel: DocumentViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            BalaSpeakTheme(darkTheme = isDark) {
                var showSettingsDialog by remember { mutableStateOf(false) }
                var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 Dubber, 1 Dubber V2, 2 Translator, 3 Transcriber

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CompactTopHeader(
                            viewModel = viewModel,
                            selectedTabIndex = selectedTabIndex,
                            onTabSelected = { selectedTabIndex = it },
                            onOpenSettings = { showSettingsDialog = true }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = selectedTabIndex,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "TabTransition"
                        ) { tab ->
                            when (tab) {
                                0 -> BatchDubScreen(viewModel = viewModel)
                                1 -> DubberV2Screen(viewModel = viewModel)
                                2 -> StandaloneSubtitleTranslatorScreen(
                                    viewModel = viewModel,
                                    onNavigateToDubbing = { selectedTabIndex = 0 }
                                )
                                3 -> VideoTranscribeScreen(
                                    viewModel = viewModel,
                                    onNavigateToDubbing = { selectedTabIndex = 0 },
                                    onNavigateToTranslator = { selectedTabIndex = 2 }
                                )
                                else -> BatchDubScreen(viewModel = viewModel)
                            }
                        }

                        if (showSettingsDialog) {
                            SettingsModalDialog(
                                viewModel = viewModel,
                                onDismiss = { showSettingsDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
