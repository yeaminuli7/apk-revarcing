package id.vern.antigravity.ui.screens

import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.vern.antigravity.R
import id.vern.antigravity.data.OpenTab
import id.vern.antigravity.ui.components.CodeEditor
import id.vern.antigravity.ui.components.DiffViewer
import id.vern.antigravity.ui.components.FileTree
import id.vern.antigravity.ui.components.ModelSelectorDialog
import id.vern.antigravity.ui.components.TerminalView
import id.vern.antigravity.viewmodel.AntigravityViewModel
import java.io.File

enum class ActiveToolView {
    CHAT,
    FILES,
    EDITOR,
    TERMINAL,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: AntigravityViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeView by remember { mutableStateOf(ActiveToolView.CHAT) }
    val appearance by viewModel.appearanceConfig.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val thinkingBudget by viewModel.thinkingBudget.collectAsStateWithLifecycle()
    val userQuotaSummary by viewModel.userQuotaSummary.collectAsStateWithLifecycle()
    val codeAssistTier by viewModel.codeAssistTier.collectAsStateWithLifecycle()
    val cliModels by viewModel.cliModels.collectAsStateWithLifecycle()
    val quotaErrorMessage by viewModel.quotaErrorMessage.collectAsStateWithLifecycle()
    var showModelDialog by remember { mutableStateOf(false) }

    val openTabs by viewModel.openEditorTabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeEditorTabId.collectAsStateWithLifecycle()

    val rootDir = remember {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (docs != null && docs.exists()) docs else (Environment.getExternalStorageDirectory() ?: File("/"))
    }

    fun openFile(file: File) {
        viewModel.openFileInEditor(file)
        activeView = ActiveToolView.EDITOR
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 760.dp

        if (isWideScreen) {

            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_logo),
                        contentDescription = stringResource(R.string.common_logo),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    NavigationRailItem(
                        selected = activeView == ActiveToolView.CHAT,
                        onClick = { activeView = ActiveToolView.CHAT },
                        icon = { Icon(imageVector = Icons.Default.SmartToy, contentDescription = stringResource(R.string.nav_item_chat)) },
                        label = { Text(stringResource(R.string.nav_item_chat), fontSize = 10.sp) }
                    )
                    NavigationRailItem(
                        selected = activeView == ActiveToolView.FILES,
                        onClick = { activeView = ActiveToolView.FILES },
                        icon = { Icon(imageVector = Icons.Default.Folder, contentDescription = stringResource(R.string.nav_item_files)) },
                        label = { Text(stringResource(R.string.nav_item_files), fontSize = 10.sp) }
                    )
                    NavigationRailItem(
                        selected = activeView == ActiveToolView.EDITOR,
                        onClick = { activeView = ActiveToolView.EDITOR },
                        icon = { Icon(imageVector = Icons.Default.Code, contentDescription = stringResource(R.string.nav_item_editor)) },
                        label = { Text(stringResource(R.string.nav_item_editor), fontSize = 10.sp) }
                    )
                    NavigationRailItem(
                        selected = activeView == ActiveToolView.TERMINAL,
                        onClick = { activeView = ActiveToolView.TERMINAL },
                        icon = { Icon(imageVector = Icons.Default.Terminal, contentDescription = stringResource(R.string.nav_item_terminal)) },
                        label = { Text(stringResource(R.string.nav_item_terminal), fontSize = 10.sp) }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    NavigationRailItem(
                        selected = activeView == ActiveToolView.SETTINGS,
                        onClick = { activeView = ActiveToolView.SETTINGS },
                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.nav_item_settings)) },
                        label = { Text(stringResource(R.string.nav_item_settings), fontSize = 10.sp) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {

                    Surface(
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        FileTree(
                            rootDirectory = rootDir,
                            onFileSelected = { openFile(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    ) {
                        val activeTab = openTabs.find { it.id == activeTabId }
                        if (activeTab != null) {
                            if (activeTab.isDiffView) {
                                DiffViewer(
                                    fileName = activeTab.title,
                                    originalText = activeTab.originalContent,
                                    modifiedText = activeTab.content,
                                    onApplyChanges = {
                                        activeTab.file.writeText(activeTab.content)
                                        viewModel.updateEditorContent(activeTab.id, activeTab.content)
                                        viewModel.saveEditorTab(context, activeTab.id)
                                    },
                                    onDiscardChanges = {
                                        viewModel.updateEditorContent(activeTab.id, activeTab.originalContent)
                                    },
                                    onBack = {
                                        viewModel.setEditorTabDiffView(activeTab.id, false)
                                    }
                                )
                            } else {
                                CodeEditor(
                                    tab = activeTab,
                                    onSaveFile = { _, content ->
                                        viewModel.updateEditorContent(activeTab.id, content)
                                        viewModel.saveEditorTab(context, activeTab.id)
                                    },
                                    onCloseTab = {
                                        viewModel.closeEditorTab(activeTab.id)
                                    },
                                    onToggleDiff = {
                                        viewModel.setEditorTabDiffView(activeTab.id, true)
                                    }
                                )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Select a file from the explorer to open in the editor",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        tonalElevation = 2.dp
                    ) {
                        AnimatedContent(
                            targetState = activeView,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                            },
                            label = "TabletToolViewTransition"
                        ) { targetView ->
                            when (targetView) {
                                ActiveToolView.TERMINAL -> TerminalView(modifier = Modifier.fillMaxSize())
                                ActiveToolView.SETTINGS -> SettingsScreen(viewModel = viewModel, onNavigateBack = { activeView = ActiveToolView.CHAT })
                                else -> ChatScreen(
                                    viewModel = viewModel,
                                    onNavigateToSettings = { activeView = ActiveToolView.SETTINGS },
                                    onNavigateToFiles = { activeView = ActiveToolView.FILES },
                                    onNavigateToEditor = { activeView = ActiveToolView.EDITOR },
                                    onNavigateToTerminal = { activeView = ActiveToolView.TERMINAL }
                                )
                            }
                        }
                    }
                }
            }
        } else {

            AnimatedContent(
                targetState = activeView,
                transitionSpec = {
                    if (targetState == ActiveToolView.CHAT) {
                        (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it }) + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { it }) + fadeOut(tween(250)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it }) + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { -it }) + fadeOut(tween(250)))
                    }
                },
                label = "MobileToolViewTransition",
                modifier = Modifier.fillMaxSize()
            ) { view ->
                when (view) {
                    ActiveToolView.CHAT -> {
                        ChatScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { activeView = ActiveToolView.SETTINGS },
                            onNavigateToFiles = { activeView = ActiveToolView.FILES },
                            onNavigateToEditor = { activeView = ActiveToolView.EDITOR },
                            onNavigateToTerminal = { activeView = ActiveToolView.TERMINAL }
                        )
                    }

                    ActiveToolView.FILES -> {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    navigationIcon = {
                                        IconButton(onClick = { activeView = ActiveToolView.CHAT }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(R.string.nav_back_to_chat),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    title = {
                                        Text(
                                            text = stringResource(R.string.nav_item_files),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    actions = {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            IconButton(onClick = { activeView = ActiveToolView.CHAT }) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = stringResource(R.string.nav_item_files_active),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        IconButton(onClick = { activeView = ActiveToolView.EDITOR }) {
                                            Icon(
                                                imageVector = Icons.Default.Code,
                                                contentDescription = stringResource(R.string.nav_item_editor),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(onClick = { activeView = ActiveToolView.TERMINAL }) {
                                            Icon(
                                                imageVector = Icons.Default.Terminal,
                                                contentDescription = stringResource(R.string.nav_item_terminal),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            },
                            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                FileTree(
                                    rootDirectory = rootDir,
                                    onFileSelected = { openFile(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    ActiveToolView.EDITOR -> {
                        val activeTab = openTabs.find { it.id == activeTabId }
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    navigationIcon = {
                                        IconButton(onClick = { activeView = ActiveToolView.CHAT }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(R.string.nav_back_to_chat),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    title = {
                                        Text(
                                            text = activeTab?.title ?: stringResource(R.string.nav_item_editor),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    actions = {
                                        IconButton(onClick = { activeView = ActiveToolView.FILES }) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = stringResource(R.string.nav_item_files),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            IconButton(onClick = { activeView = ActiveToolView.CHAT }) {
                                                Icon(
                                                    imageVector = Icons.Default.Code,
                                                    contentDescription = stringResource(R.string.nav_item_editor_active),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        IconButton(onClick = { activeView = ActiveToolView.TERMINAL }) {
                                            Icon(
                                                imageVector = Icons.Default.Terminal,
                                                contentDescription = stringResource(R.string.nav_item_terminal),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            },
                            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                 if (activeTab != null) {
                                    if (activeTab.isDiffView) {
                                        DiffViewer(
                                            fileName = activeTab.title,
                                            originalText = activeTab.originalContent,
                                            modifiedText = activeTab.content,
                                            onApplyChanges = {
                                                activeTab.file.writeText(activeTab.content)
                                                viewModel.updateEditorContent(activeTab.id, activeTab.content)
                                                viewModel.saveEditorTab(context, activeTab.id)
                                            },
                                            onDiscardChanges = {
                                                viewModel.updateEditorContent(activeTab.id, activeTab.originalContent)
                                            },
                                            onBack = {
                                                viewModel.setEditorTabDiffView(activeTab.id, false)
                                            }
                                        )
                                    } else {
                                        CodeEditor(
                                            tab = activeTab,
                                            onSaveFile = { _, content ->
                                                viewModel.updateEditorContent(activeTab.id, content)
                                                viewModel.saveEditorTab(context, activeTab.id)
                                            },
                                            onCloseTab = {
                                                viewModel.closeEditorTab(activeTab.id)
                                            },
                                            onToggleDiff = {
                                                viewModel.setEditorTabDiffView(activeTab.id, true)
                                            }
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No open files in editor",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Open the Files tab from the top toolbar to select a file.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ActiveToolView.TERMINAL -> {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    navigationIcon = {
                                        IconButton(onClick = { activeView = ActiveToolView.CHAT }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(R.string.nav_back_to_chat),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    title = {
                                        Text(
                                            text = stringResource(R.string.nav_item_terminal),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    actions = {
                                        IconButton(onClick = { activeView = ActiveToolView.FILES }) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = stringResource(R.string.nav_item_files),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(onClick = { activeView = ActiveToolView.EDITOR }) {
                                            Icon(
                                                imageVector = Icons.Default.Code,
                                                contentDescription = stringResource(R.string.nav_item_editor),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            IconButton(onClick = { activeView = ActiveToolView.CHAT }) {
                                                Icon(
                                                    imageVector = Icons.Default.Terminal,
                                                    contentDescription = stringResource(R.string.nav_item_terminal_active),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                )
                            },
                            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                TerminalView(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }

                    ActiveToolView.SETTINGS -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { activeView = ActiveToolView.CHAT }
                        )
                    }
                }
            }
        }
    }

    if (showModelDialog) {
        ModelSelectorDialog(
            selectedModel = selectedModel,
            selectedThinking = thinkingBudget,
            userQuotaSummary = userQuotaSummary,
            codeAssistTier = codeAssistTier,
            cliModels = cliModels,
            modelsErrorMessage = quotaErrorMessage,
            onModelSelected = { model, thinking, cliId ->
                viewModel.setModel(context, model, thinking, cliId)
                showModelDialog = false
            },
            onDismissRequest = { showModelDialog = false }
        )
    }
}

