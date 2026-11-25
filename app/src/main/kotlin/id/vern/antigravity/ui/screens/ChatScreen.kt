package id.vern.antigravity.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import android.widget.Toast
import id.vern.antigravity.data.RunningSubagent
import id.vern.antigravity.ui.components.AgentDetailsBottomSheet
import id.vern.antigravity.ui.components.DialogBlurEffect
import id.vern.antigravity.ui.components.InstallationProgressDialog
import id.vern.antigravity.ui.components.SubagentChatBottomSheet
import id.vern.antigravity.viewmodel.ConversationSummary
import java.io.File
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import id.vern.antigravity.data.MessageRole
import id.vern.antigravity.data.ProjectSortOrder
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.vern.antigravity.R
import id.vern.antigravity.ui.components.ChatBubble
import id.vern.antigravity.ui.components.ChatInputBar
import id.vern.antigravity.api.GeminiClient
import id.vern.antigravity.accessibility.AccessibilityHelper
import id.vern.antigravity.ui.components.AccessibilityDialog
import id.vern.antigravity.ui.components.ApiKeyDialog
import id.vern.antigravity.ui.components.GoogleSignInDialog
import id.vern.antigravity.ui.components.ModelSelectorDialog
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed
import id.vern.antigravity.viewmodel.AntigravityViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: AntigravityViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToFiles: () -> Unit = {},
    onNavigateToEditor: () -> Unit = {},
    onNavigateToTerminal: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val conversationTitle by viewModel.conversationTitle.collectAsStateWithLifecycle()
    val conversationHistory by viewModel.conversationHistory.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()
    val isExecuting by viewModel.isExecuting.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val thinkingBudget by viewModel.thinkingBudget.collectAsStateWithLifecycle()
    val verboseThinking by viewModel.verboseThinking.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userPicture by viewModel.userPicture.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val showAuthDialog by viewModel.showGoogleSignInDialog.collectAsStateWithLifecycle()
    val showApiKeyDialogState by viewModel.showApiKeyDialog.collectAsStateWithLifecycle()
    val showAccessibilityDialogState by viewModel.showAccessibilityDialog.collectAsStateWithLifecycle()
    val showModelSelector by viewModel.showModelSelector.collectAsStateWithLifecycle()
    val isPinned by viewModel.isConversationPinned.collectAsStateWithLifecycle()
    val userQuotaSummary by viewModel.userQuotaSummary.collectAsStateWithLifecycle()
    val codeAssistTier by viewModel.codeAssistTier.collectAsStateWithLifecycle()
    val cliModels by viewModel.cliModels.collectAsStateWithLifecycle()
    val quotaErrorMessage by viewModel.quotaErrorMessage.collectAsStateWithLifecycle()
    val queuedMessages by viewModel.queuedMessages.collectAsStateWithLifecycle()
    val runningTasks by viewModel.runningTasks.collectAsStateWithLifecycle()
    val runningSubagents by viewModel.runningSubagents.collectAsStateWithLifecycle()
    val slashActions by viewModel.slashActions.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceConfig.collectAsStateWithLifecycle()
    val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
    val pendingInputText by viewModel.pendingInputText.collectAsStateWithLifecycle()
    val securityPreset by viewModel.securityPreset.collectAsStateWithLifecycle()

    var showMenuDropdown by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDetailsBottomSheet by remember { mutableStateOf(false) }
    var detailsBottomSheetTab by remember { mutableIntStateOf(0) }
    var fileToOpenInEditor by remember { mutableStateOf<String?>(null) }
    var selectedSubagent by remember { mutableStateOf<RunningSubagent?>(null) }
    var showSettingsBottomSheet by remember { mutableStateOf(false) }
    var showSkillsDialog by remember { mutableStateOf(false) }
    var showPluginsDialog by remember { mutableStateOf(false) }
    var settingsInitialPage by remember { mutableStateOf(SettingsPage.MAIN) }
    val listState = rememberLazyListState()
    val terminalReq by viewModel.openTerminalRequest.collectAsStateWithLifecycle()

    LaunchedEffect(terminalReq) {
        if (terminalReq != null) {
            showSettingsBottomSheet = false
            showSkillsDialog = false
            showPluginsDialog = false
            detailsBottomSheetTab = 2
            showDetailsBottomSheet = true
            viewModel.consumeOpenTerminalRequest()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCliSlashCommands(context)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val currentCount = attachedFiles.size
        val remaining = (5 - currentCount).coerceAtLeast(0)
        if (remaining <= 0) {
            Toast.makeText(context, context.getString(R.string.chat_attachment_limit_reached), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        uris.take(remaining).forEach { uri ->
            viewModel.addAttachment(context, uri)
        }
        if (uris.size > remaining) {
            Toast.makeText(context, context.getString(R.string.chat_attachment_limit_reached), Toast.LENGTH_SHORT).show()
        }
    }

    val projectList by viewModel.projectList.collectAsStateWithLifecycle()
    val projectSortOrder by viewModel.projectSortOrder.collectAsStateWithLifecycle()
    val currentWorkspace by viewModel.workspacePath.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var chosenDestinationFolder by remember { mutableStateOf<File?>(null) }

    var isPinnedExpanded by remember { mutableStateOf(true) }
    val pinnedArrowRotation by animateFloatAsState(
        targetValue = if (isPinnedExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "pinnedChevronRotation"
    )

    var isProjectsExpanded by remember { mutableStateOf(true) }
    val projectsArrowRotation by animateFloatAsState(
        targetValue = if (isProjectsExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "projectsChevronRotation"
    )

    var isConversationsExpanded by remember { mutableStateOf(true) }
    val convosArrowRotation by animateFloatAsState(
        targetValue = if (isConversationsExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "convosChevronRotation"
    )
    var showPinnedMenu by remember { mutableStateOf(false) }

    var showDeleteConversationDialog by remember { mutableStateOf(false) }
    var showDeleteSpecificConversationDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    var showDeleteProjectDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<File?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val folder = viewModel.addProjectFromTreeUri(context, uri)
            if (folder != null) {
                Toast.makeText(context, context.getString(R.string.toast_project_added, folder.name), Toast.LENGTH_SHORT).show()
                showNewProjectDialog = false
            } else {
                Toast.makeText(context, context.getString(R.string.toast_project_add_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val createProjectFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val parentDir = viewModel.resolveTreeUriToFolder(context, uri)
            if (parentDir != null && parentDir.exists()) {
                val name = newProjectName.trim()
                if (name.isNotEmpty()) {
                    val created = viewModel.createProjectInFolder(context, name, parentDir)
                    if (created != null) {
                        Toast.makeText(context, context.getString(R.string.toast_project_created, created.name), Toast.LENGTH_SHORT).show()
                        showNewProjectDialog = false
                        newProjectName = ""
                        chosenDestinationFolder = null
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_project_create_failed), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    chosenDestinationFolder = parentDir
                }
            } else {
                Toast.makeText(context, context.getString(R.string.toast_project_create_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewProjectDialog = false
                newProjectName = ""
                chosenDestinationFolder = null
            },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(
                    text = stringResource(R.string.dialog_add_project_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.wrapContentHeight().animateContentSize()) {
                    Text(
                        text = stringResource(R.string.dialog_add_project_choose_existing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            folderPickerLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.dialog_add_project_choose_btn), fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.dialog_add_project_or_create),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text(stringResource(R.string.dialog_add_project_name_label)) },
                        placeholder = { Text(stringResource(R.string.dialog_add_project_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            createProjectFolderPickerLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (chosenDestinationFolder != null) {
                                stringResource(R.string.dialog_create_project_dest_label, chosenDestinationFolder!!.name)
                            } else {
                                stringResource(R.string.dialog_create_project_pick_folder_btn)
                            },
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        val name = newProjectName.trim()
                        if (name.isNotEmpty()) {
                            val dest = chosenDestinationFolder
                            if (dest != null) {
                                val created = viewModel.createProjectInFolder(context, name, dest)
                                if (created != null) {
                                    Toast.makeText(context, context.getString(R.string.toast_project_created, created.name), Toast.LENGTH_SHORT).show()
                                    showNewProjectDialog = false
                                    newProjectName = ""
                                    chosenDestinationFolder = null
                                } else {
                                    Toast.makeText(context, context.getString(R.string.toast_project_create_failed), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                createProjectFolderPickerLauncher.launch(null)
                            }
                        }
                    },
                    enabled = newProjectName.isNotBlank()
                ) {
                    Text(
                        if (chosenDestinationFolder != null) stringResource(R.string.dialog_add_project_create_btn)
                        else stringResource(R.string.dialog_create_project_pick_and_create_btn)
                    )
                }
            }
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            runCatching {
                listState.scrollToItem(messages.size)
            }
        }
    }

    val lastMessage = messages.lastOrNull()
    val lastMessageContentLength = lastMessage?.content?.length ?: 0
    val lastMessageThinkingLength = lastMessage?.thinking?.length ?: 0
    val lastMessageStepsCount = lastMessage?.steps?.size ?: 0

    LaunchedEffect(lastMessageContentLength, lastMessageThinkingLength, lastMessageStepsCount, isExecuting) {
        if (isExecuting && messages.isNotEmpty() && !listState.isScrollInProgress) {
            kotlinx.coroutines.delay(200)
            runCatching {
                listState.scrollToItem(messages.size)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_logo),
                            contentDescription = stringResource(id = R.string.app_name),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.startNewConversation(context)
                                scope.launch { drawerState.close() }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.chat_new_conversation),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.chat_new_conversation),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val pinnedConversations = conversationHistory.filter { it.isPinned }
                    val unpinnedConversations = conversationHistory.filter { !it.isPinned }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {

                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { isPinnedExpanded = !isPinnedExpanded }
                                        .padding(vertical = 2.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.drawer_pinned_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(pinnedArrowRotation),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showPinnedMenu = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.chat_pinned_options),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showPinnedMenu,
                                        onDismissRequest = { showPinnedMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(if (isPinnedExpanded) R.string.drawer_collapse_section else R.string.drawer_expand_section), fontSize = 13.sp) },
                                            onClick = {
                                                isPinnedExpanded = !isPinnedExpanded
                                                showPinnedMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            AnimatedVisibility(
                                visible = isPinnedExpanded,
                                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
                                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(150))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                                    if (pinnedConversations.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.drawer_pinned_empty),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                                        )
                                    } else {
                                        pinnedConversations.forEach { convo ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.Transparent,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        viewModel.loadConversation(context, convo)
                                                        scope.launch { drawerState.close() }
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = convo.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = formatRelativeTime(convo.timestamp),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontSize = 12.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    var showConvoMenu by remember { mutableStateOf(false) }
                                                    Box {
                                                        IconButton(
                                                            onClick = { showConvoMenu = true },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.MoreVert,
                                                                contentDescription = stringResource(R.string.common_options),
                                                                modifier = Modifier.size(14.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                            )
                                                        }
                                                        DropdownMenu(
                                                            expanded = showConvoMenu,
                                                            onDismissRequest = { showConvoMenu = false },
                                                            shape = RoundedCornerShape(16.dp),
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.menu_unpin_conversation)) },
                                                                leadingIcon = {
                                                                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                                                                },
                                                                onClick = {
                                                                    showConvoMenu = false
                                                                    viewModel.togglePinConversation(convo.id, context = context)
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.menu_delete_conversation), color = AntigravityRed) },
                                                                leadingIcon = {
                                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = AntigravityRed, modifier = Modifier.size(18.dp))
                                                                },
                                                                onClick = {
                                                                    showConvoMenu = false
                                                                    conversationToDelete = convo
                                                                    showDeleteSpecificConversationDialog = true
                                                                }
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

                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { isProjectsExpanded = !isProjectsExpanded }
                                        .padding(vertical = 2.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.drawer_projects_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(projectsArrowRotation),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        IconButton(
                                            onClick = { showSortMenu = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FilterList,
                                                contentDescription = stringResource(R.string.drawer_sort_projects),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false },
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.sort_name_asc), fontSize = 13.sp) },
                                                onClick = {
                                                    viewModel.setProjectSortOrder(context, ProjectSortOrder.NAME_ASC)
                                                    showSortMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.sort_name_desc), fontSize = 13.sp) },
                                                onClick = {
                                                    viewModel.setProjectSortOrder(context, ProjectSortOrder.NAME_DESC)
                                                    showSortMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.sort_date_desc), fontSize = 13.sp) },
                                                onClick = {
                                                    viewModel.setProjectSortOrder(context, ProjectSortOrder.DATE_DESC)
                                                    showSortMenu = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.sort_date_asc), fontSize = 13.sp) },
                                                onClick = {
                                                    viewModel.setProjectSortOrder(context, ProjectSortOrder.DATE_ASC)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(2.dp))

                                    IconButton(
                                        onClick = {
                                            newProjectName = ""
                                            showNewProjectDialog = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CreateNewFolder,
                                            contentDescription = stringResource(R.string.drawer_add_project_folder),
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            AnimatedVisibility(
                                visible = isProjectsExpanded,
                                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
                                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(150))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                                    if (projectList.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.drawer_projects_empty),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                                        )
                                    } else {
                                        projectList.forEach { projectDir ->
                                    val isCurrent = projectDir.absolutePath == currentWorkspace
                                    val convoListForProject = conversationHistory.filter { it.projectPath == projectDir.absolutePath }

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    viewModel.setWorkspacePath(context, projectDir.absolutePath)
                                                    scope.launch { drawerState.close() }
                                                }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = projectDir.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {

                                                    IconButton(
                                                        onClick = {
                                                            viewModel.startNewConversation(context, projectDir.absolutePath)
                                                            scope.launch { drawerState.close() }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = stringResource(R.string.menu_new_chat_in_project),
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    var showProjectMenu by remember { mutableStateOf(false) }
                                                    Box {
                                                        IconButton(
                                                            onClick = { showProjectMenu = true },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.MoreVert,
                                                                contentDescription = stringResource(R.string.menu_project_options),
                                                                modifier = Modifier.size(16.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        DropdownMenu(
                                                            expanded = showProjectMenu,
                                                            onDismissRequest = { showProjectMenu = false },
                                                            shape = RoundedCornerShape(16.dp),
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.menu_new_chat_in_project)) },
                                                                leadingIcon = {
                                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                                },
                                                                onClick = {
                                                                    showProjectMenu = false
                                                                    viewModel.startNewConversation(context, projectDir.absolutePath)
                                                                    scope.launch { drawerState.close() }
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.menu_delete_project_plain), color = AntigravityRed) },
                                                                leadingIcon = {
                                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = AntigravityRed, modifier = Modifier.size(18.dp))
                                                                },
                                                                onClick = {
                                                                    showProjectMenu = false
                                                                    projectToDelete = projectDir
                                                                    showDeleteProjectDialog = true
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (convoListForProject.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.drawer_project_convos_empty),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(start = 32.dp, top = 2.dp, bottom = 6.dp)
                                            )
                                        } else {
                                            convoListForProject.forEach { c ->
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color.Transparent,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            viewModel.loadConversation(context, c)
                                                            scope.launch { drawerState.close() }
                                                        }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.padding(start = 32.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = c.title,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = formatRelativeTime(c.timestamp),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        var showProjectConvoMenu by remember { mutableStateOf(false) }
                                                        Box {
                                                            IconButton(
                                                                onClick = { showProjectConvoMenu = true },
                                                                modifier = Modifier.size(20.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.MoreVert,
                                                                    contentDescription = stringResource(R.string.common_options),
                                                                    modifier = Modifier.size(14.dp),
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                                )
                                                            }
                                                            DropdownMenu(
                                                                expanded = showProjectConvoMenu,
                                                                onDismissRequest = { showProjectConvoMenu = false },
                                                                shape = RoundedCornerShape(16.dp),
                                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                                            ) {
                                                                DropdownMenuItem(
                                                                    text = { Text(stringResource(R.string.menu_delete_conversation), color = AntigravityRed) },
                                                                    leadingIcon = {
                                                                        Icon(Icons.Default.Delete, contentDescription = null, tint = AntigravityRed, modifier = Modifier.size(18.dp))
                                                                    },
                                                                    onClick = {
                                                                        showProjectConvoMenu = false
                                                                        conversationToDelete = c
                                                                        showDeleteSpecificConversationDialog = true
                                                                    }
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

                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { isConversationsExpanded = !isConversationsExpanded }
                                        .padding(vertical = 2.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.drawer_conversations_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .rotate(convosArrowRotation),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        item {
                            AnimatedVisibility(
                                visible = isConversationsExpanded,
                                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
                                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(150))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                                    if (unpinnedConversations.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.drawer_recent_empty),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                                        )
                                    } else {
                                        unpinnedConversations.forEach { convo ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.Transparent,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        viewModel.loadConversation(context, convo)
                                                        scope.launch { drawerState.close() }
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = convo.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = formatRelativeTime(convo.timestamp),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        fontSize = 12.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    var showUnpinnedMenu by remember { mutableStateOf(false) }
                                                    Box {
                                                        IconButton(
                                                            onClick = { showUnpinnedMenu = true },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.MoreVert,
                                                                contentDescription = stringResource(R.string.common_options),
                                                                modifier = Modifier.size(14.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                            )
                                                        }
                                                        DropdownMenu(
                                                            expanded = showUnpinnedMenu,
                                                            onDismissRequest = { showUnpinnedMenu = false },
                                                            shape = RoundedCornerShape(16.dp),
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.chat_pin_conversation)) },
                                                                leadingIcon = {
                                                                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                                                                },
                                                                onClick = {
                                                                    showUnpinnedMenu = false
                                                                    viewModel.togglePinConversation(convo.id)
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.chat_delete_conversation), color = AntigravityRed) },
                                                                leadingIcon = {
                                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = AntigravityRed, modifier = Modifier.size(18.dp))
                                                                },
                                                                onClick = {
                                                                    showUnpinnedMenu = false
                                                                    conversationToDelete = convo
                                                                    showDeleteSpecificConversationDialog = true
                                                                }
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                scope.launch { drawerState.close() }
                                if (userEmail == null) {
                                    viewModel.startGoogleSignIn(context)
                                } else {
                                    settingsInitialPage = SettingsPage.ACCOUNT
                                    showSettingsBottomSheet = true
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (userEmail != null) AntigravityGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!userPicture.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userPicture,
                                        contentDescription = userName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (userEmail != null) {
                                    Text(
                                        text = (userName.takeIf { it.isNotBlank() } ?: "U").take(1).uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AntigravityGreen
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (userEmail != null) userName.ifBlank { "Google User" } else stringResource(R.string.account_sign_in_title),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (userEmail != null) stringResource(R.string.status_authenticated) else stringResource(R.string.account_tap_to_sign_in),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (userEmail != null) stringResource(R.string.status_authenticated) else stringResource(R.string.account_btn_sign_in),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                scope.launch { drawerState.close() }
                                settingsInitialPage = SettingsPage.MAIN
                                showSettingsBottomSheet = true
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.common_settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.common_settings),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (conversationTitle.isNotBlank() && conversationTitle != "New Conversation" && conversationTitle != "Antigravity -Android") conversationTitle else stringResource(R.string.chat_new_conversation),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            if (onNavigateBack != null) {
                                onNavigateBack()
                            } else {
                                scope.launch { drawerState.open() }
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_logo),
                                contentDescription = stringResource(R.string.app_name),
                                tint = Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMenuDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.common_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showMenuDropdown,
                                onDismissRequest = { showMenuDropdown = false },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_rename_conversation)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenuDropdown = false
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (isPinned) R.string.menu_unpin_conversation else R.string.menu_pin_conversation)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenuDropdown = false
                                        val pinned = viewModel.togglePinConversation(context = context)
                                        Toast.makeText(
                                            context,
                                            context.getString(if (pinned) R.string.toast_conversation_pinned else R.string.toast_conversation_unpinned),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_archive_conversation)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Archive,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenuDropdown = false
                                        viewModel.archiveConversation(context = context)
                                        Toast.makeText(context, context.getString(R.string.toast_conversation_archived), Toast.LENGTH_SHORT).show()
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_delete_conversation), color = AntigravityRed) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = AntigravityRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenuDropdown = false
                                        showDeleteConversationDialog = true
                                    }
                                )

                                val currentProject = projectList.firstOrNull { it.absolutePath == currentWorkspace }
                                if (currentProject != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_delete_project, currentProject.name), color = AntigravityRed) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DeleteForever,
                                                contentDescription = null,
                                                tint = AntigravityRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            showMenuDropdown = false
                                            projectToDelete = currentProject
                                            showDeleteProjectDialog = true
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            detailsBottomSheetTab = 0
                            showDetailsBottomSheet = true
                        }) {
                            SidePanelIcon(
                                tint = if (showDetailsBottomSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = messages.isEmpty(),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                        },
                        label = "ChatMainContentTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { isListEmpty ->
                        if (isListEmpty) {

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_launcher_logo),
                                        contentDescription = stringResource(id = R.string.app_name),
                                        modifier = Modifier.size(64.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = stringResource(R.string.chat_empty_headline),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = stringResource(R.string.chat_empty_subheadline),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { showSkillsDialog = true }
                                        ) {
                                            Text(
                                                text = stringResource(R.string.chat_empty_install_skills),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { showPluginsDialog = true }
                                        ) {
                                            Text(
                                                text = stringResource(R.string.chat_empty_install_plugins),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp)
                            ) {
                                val executingUserMsgIndex = if (isExecuting) messages.indexOfLast { it.role == MessageRole.USER } else -1

                                if (executingUserMsgIndex != -1 && isExecuting) {
                                    items(messages.subList(0, executingUserMsgIndex), key = { it.id }) { message ->
                                        ChatBubble(
                                            message = message,
                                            verboseThinking = verboseThinking,
                                            onOpenFile = { path ->
                                                fileToOpenInEditor = path
                                                detailsBottomSheetTab = 3
                                                showDetailsBottomSheet = true
                                            },
                                            onOpenTerminal = { cmd ->
                                                viewModel.requestOpenTerminal(cmd)
                                            },
                                            onUndoPrompt = { userMsg ->
                                                viewModel.undoPrompt(context, userMsg)
                                            },
                                            isExecuting = isExecuting
                                        )
                                    }

                                    stickyHeader(key = messages[executingUserMsgIndex].id) {
                                        val userMsg = messages[executingUserMsgIndex]
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.background)
                                        ) {
                                            ChatBubble(
                                                message = userMsg,
                                                verboseThinking = verboseThinking,
                                                onOpenFile = { path ->
                                                    fileToOpenInEditor = path
                                                    detailsBottomSheetTab = 3
                                                    showDetailsBottomSheet = true
                                                },
                                                onOpenTerminal = { cmd ->
                                                    viewModel.requestOpenTerminal(cmd)
                                                },
                                                onUndoPrompt = { userMsgToUndo ->
                                                    viewModel.undoPrompt(context, userMsgToUndo)
                                                },
                                                isPinnedExecuting = true,
                                                isExecuting = isExecuting
                                            )
                                        }
                                    }

                                    items(messages.subList(executingUserMsgIndex + 1, messages.size), key = { it.id }) { message ->
                                        ChatBubble(
                                            message = message,
                                            verboseThinking = verboseThinking,
                                            onOpenFile = { path ->
                                                fileToOpenInEditor = path
                                                detailsBottomSheetTab = 3
                                                showDetailsBottomSheet = true
                                            },
                                            onOpenTerminal = { cmd ->
                                                viewModel.requestOpenTerminal(cmd)
                                            },
                                            onUndoPrompt = { userMsg ->
                                                viewModel.undoPrompt(context, userMsg)
                                            },
                                            isExecuting = isExecuting
                                        )
                                    }
                                } else {
                                    items(messages, key = { it.id }) { message ->
                                        ChatBubble(
                                            message = message,
                                            verboseThinking = verboseThinking,
                                            onOpenFile = { path ->
                                                fileToOpenInEditor = path
                                                detailsBottomSheetTab = 3
                                                showDetailsBottomSheet = true
                                            },
                                            onOpenTerminal = { cmd ->
                                                viewModel.requestOpenTerminal(cmd)
                                            },
                                            onUndoPrompt = { userMsg ->
                                                viewModel.undoPrompt(context, userMsg)
                                            },
                                            isExecuting = isExecuting
                                        )
                                    }
                                }
                                item(key = "chat_bottom_anchor") {
                                    Spacer(modifier = Modifier.height(1.dp))
                                }
                            }
                        }
                    }
                }

                ChatInputBar(
                    isExecuting = isExecuting,
                    selectedModel = selectedModel,
                    thinkingBudget = thinkingBudget,
                    attachedFiles = attachedFiles,
                    queuedMessages = queuedMessages,
                    runningTasks = runningTasks,
                    runningSubagents = runningSubagents,
                    externalInputText = pendingInputText,
                    onExternalInputTextConsumed = { viewModel.clearPendingInputText() },
                    onSendMessage = { text ->
                        viewModel.sendMessage(context, text)
                        scope.launch {
                            kotlinx.coroutines.delay(60)
                            runCatching {
                                listState.scrollToItem(messages.size)
                            }
                        }
                    },
                    onModelSelectorClick = { viewModel.showModelSelector() },
                    onAttachFileClick = {
                        if (attachedFiles.size >= 5) {
                            Toast.makeText(context, context.getString(R.string.chat_attachment_limit_reached), Toast.LENGTH_SHORT).show()
                        } else {
                            filePicker.launch("*/*")
                        }
                    },
                    onRemoveAttachment = { file -> viewModel.removeAttachment(file) },
                    onStopExecution = { viewModel.stopAiExecution() },
                    onProcessQueuedMessageNow = { id -> viewModel.processQueuedMessageNow(context, id) },
                    onEditQueuedMessage = { id -> viewModel.editQueuedMessage(id) },
                    onRemoveQueuedMessage = { id -> viewModel.removeQueuedMessage(id) },
                    onStopTask = { taskId -> viewModel.stopRunningTask(taskId) },
                    onTaskClick = { task -> viewModel.requestOpenTerminal(task.command) },
                    onSubagentClick = { subagent -> selectedSubagent = subagent },
                    onStopSubagent = { subagentId -> viewModel.stopSubagent(subagentId) },
                    onGetWorkspaceFiles = { query -> viewModel.getWorkspaceFiles(query) },
                    slashActions = slashActions,
                    onSlashMenuOpened = { viewModel.refreshCliSlashCommands(context) },
                    securityPreset = securityPreset,
                    onSecurityPresetChange = { preset -> viewModel.setSecurityPreset(context, preset) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
            }
        }
    }

    if (showModelSelector) {
        LaunchedEffect(Unit) {
            viewModel.refreshUserQuota(context)
            viewModel.refreshCliModels(context)
        }
        ModelSelectorDialog(
            selectedModel = selectedModel,
            selectedThinking = thinkingBudget,
            userQuotaSummary = userQuotaSummary,
            codeAssistTier = codeAssistTier,
            cliModels = cliModels,
            modelsErrorMessage = quotaErrorMessage,
            onModelSelected = { model, thinking, cliId ->
                viewModel.setModel(context, model, thinking, cliId)
            },
            onDismissRequest = { viewModel.dismissModelSelector() }
        )
    }

    if (showAuthDialog) {
        GoogleSignInDialog(
            userEmail = userEmail,
            userName = userName,
            userPicture = userPicture,
            isAuthenticating = isAuthenticating,
            authError = authError,
            onDismissRequest = { viewModel.dismissGoogleSignInDialog() },
            onLaunchSignIn = { viewModel.startGoogleSignIn(context) }
        )
    }

    if (showApiKeyDialogState) {
        ApiKeyDialog(
            onDismissRequest = { viewModel.dismissApiKeyDialog() },
            onSave = { key ->
                GeminiClient.saveApiKey(context, key)
                viewModel.dismissApiKeyDialog()
            }
        )
    }

    if (showAccessibilityDialogState) {
        AccessibilityDialog(
            onDismissRequest = { viewModel.dismissAccessibilityDialog() },
            onOpenSettings = {
                AccessibilityHelper.openSettings(context)
                viewModel.dismissAccessibilityDialog()
            },
            onSkip = {
                viewModel.dismissAccessibilityDialog()
            }
        )
    }

    if (showRenameDialog) {
        var newTitleText by remember(conversationTitle) { mutableStateOf(conversationTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(stringResource(R.string.dialog_rename_convo_title), fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = newTitleText,
                    onValueChange = { newTitleText = it },
                    label = { Text(stringResource(R.string.dialog_rename_convo_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                )
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        if (newTitleText.isNotBlank()) {
                            viewModel.renameConversation(newTitleText, context = context)
                            Toast.makeText(context, context.getString(R.string.toast_conversation_renamed), Toast.LENGTH_SHORT).show()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_rename_convo_save))
                }
            }
        )
    }

    if (showDeleteConversationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConversationDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(stringResource(R.string.dialog_delete_convo_title), fontWeight = FontWeight.Bold)
            },
            text = { Text(stringResource(R.string.dialog_delete_convo_msg)) },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        viewModel.deleteCurrentConversation(context)
                        showDeleteConversationDialog = false
                        Toast.makeText(context, context.getString(R.string.toast_conversation_deleted), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                ) {
                    Text(stringResource(R.string.dialog_delete_btn))
                }
            }
        )
    }

    if (showDeleteSpecificConversationDialog && conversationToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteSpecificConversationDialog = false
                conversationToDelete = null
            },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(stringResource(R.string.dialog_delete_convo_title), fontWeight = FontWeight.Bold)
            },
            text = { Text(stringResource(R.string.dialog_delete_specific_convo_msg, conversationToDelete?.title ?: "")) },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        conversationToDelete?.let {
                            viewModel.deleteConversation(context, it.id)
                        }
                        showDeleteSpecificConversationDialog = false
                        conversationToDelete = null
                        Toast.makeText(context, context.getString(R.string.toast_conversation_deleted), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                ) {
                    Text(stringResource(R.string.dialog_delete_btn))
                }
            }
        )
    }

    if (showDeleteProjectDialog && projectToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteProjectDialog = false
                projectToDelete = null
            },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(stringResource(R.string.dialog_delete_project_title), fontWeight = FontWeight.Bold)
            },
            text = { Text(stringResource(R.string.dialog_delete_project_msg, projectToDelete?.name ?: "")) },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        projectToDelete?.let {
                            viewModel.removeProject(context, it.absolutePath)
                        }
                        showDeleteProjectDialog = false
                        projectToDelete = null
                        Toast.makeText(context, context.getString(R.string.toast_project_removed), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                ) {
                    Text(stringResource(R.string.dialog_delete_btn))
                }
            }
        )
    }

    if (showDetailsBottomSheet) {
        AgentDetailsBottomSheet(
            viewModel = viewModel,
            initialTab = detailsBottomSheetTab,
            initialFileToOpen = fileToOpenInEditor,
            onDismissRequest = {
                showDetailsBottomSheet = false
                fileToOpenInEditor = null
            }
        )
    }

    if (selectedSubagent != null) {
        SubagentChatBottomSheet(
            subagent = selectedSubagent!!,
            viewModel = viewModel,
            onDismissRequest = { selectedSubagent = null },
            onOpenFullScreen = {
                val target = selectedSubagent
                selectedSubagent = null
                if (target != null) {
                    viewModel.openSubagentAsActiveConversation(context, target)
                }
            }
        )
    }

    if (showSettingsBottomSheet) {
        SettingsBottomSheet(
            viewModel = viewModel,
            initialPage = settingsInitialPage,
            onDismissRequest = {
                showSettingsBottomSheet = false
                settingsInitialPage = SettingsPage.MAIN
            }
        )
    }

    val activeInstallProgress by viewModel.activeInstallProgress.collectAsStateWithLifecycle()
    if (activeInstallProgress != null && !showSettingsBottomSheet) {
        InstallationProgressDialog(
            progress = activeInstallProgress!!,
            onDismissRequest = { viewModel.dismissInstallProgress() }
        )
    }

    if (showSkillsDialog) {
        SkillsManagementDialog(
            viewModel = viewModel,
            onDismiss = { showSkillsDialog = false }
        )
    }

    if (showPluginsDialog) {
        PluginsManagementDialog(
            viewModel = viewModel,
            onDismiss = { showPluginsDialog = false }
        )
    }
}

@Composable
fun SidePanelIcon(
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier.size(20.dp)
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
        val padding = 2.dp.toPx()
        val w = size.width - (padding * 2)
        val h = size.height - (padding * 2)

        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(padding, padding),
            size = androidx.compose.ui.geometry.Size(w, h),
            cornerRadius = cornerRadius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )

        val splitX = padding + (w * 0.65f)
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(splitX, padding),
            end = androidx.compose.ui.geometry.Offset(splitX, padding + h),
            strokeWidth = strokeWidth
        )
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)
    val days = diff / (24 * 60 * 60 * 1000)

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        else -> "${days}d"
    }
}

