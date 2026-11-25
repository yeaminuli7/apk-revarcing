package id.vern.antigravity.ui.components

import android.content.Context
import android.os.Environment
import id.vern.antigravity.util.FileResolver
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.vern.antigravity.R
import id.vern.antigravity.data.ChangedFileItem
import id.vern.antigravity.data.OpenTab
import id.vern.antigravity.data.ArtifactFileItem
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.service.TerminalSessionManager
import id.vern.antigravity.service.TerminalLine
import id.vern.antigravity.viewmodel.AntigravityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

data class UploadedMediaItem(
    val name: String,
    val path: String
)

data class InstalledSkillItem(
    val name: String,
    val path: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailsBottomSheet(
    viewModel: AntigravityViewModel,
    initialTab: Int = 0,
    initialFileToOpen: String? = null,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val openEditorTabs by viewModel.openEditorTabs.collectAsStateWithLifecycle()
    val activeEditorTabId by viewModel.activeEditorTabId.collectAsStateWithLifecycle()
    val activeEditorTab = openEditorTabs.find { it.id == activeEditorTabId }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val workspacePath by viewModel.workspacePath.collectAsStateWithLifecycle()
    val projectList by viewModel.projectList.collectAsStateWithLifecycle()
    val runningTasks by viewModel.runningTasks.collectAsStateWithLifecycle()
    val runningSubagents by viewModel.runningSubagents.collectAsStateWithLifecycle()
    val daemonTasks by viewModel.daemonTasks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadDaemonTasks(context)
        viewModel.refreshConversationArtifacts(context)
    }

    LaunchedEffect(initialFileToOpen) {
        if (!initialFileToOpen.isNullOrBlank()) {
            val fileToLoad = FileResolver.resolveTargetFile(
                pathOrName = initialFileToOpen,
                workspacePath = workspacePath,
                projectList = projectList,
                context = context
            )
            if (fileToLoad != null) {
                viewModel.openFileInEditor(fileToLoad)
                selectedTab = 3
            }
        }
    }

    val filesChanged by viewModel.gitChangedFiles.collectAsStateWithLifecycle()
    var uploadsList by remember { mutableStateOf<List<UploadedMediaItem>>(emptyList()) }
    var skillsList by remember { mutableStateOf<List<InstalledSkillItem>>(emptyList()) }
    val artifactsList by viewModel.conversationArtifacts.collectAsStateWithLifecycle()

    var expandedFiles by remember { mutableStateOf(true) }
    var expandedUploads by remember { mutableStateOf(true) }
    var expandedSkills by remember { mutableStateOf(true) }
    var expandedArtifacts by remember { mutableStateOf(false) }
    var expandedSubagents by remember { mutableStateOf(false) }
    var expandedTasks by remember { mutableStateOf(false) }

    var showAllUploadsDialog by remember { mutableStateOf(false) }

    var isReviewSearching by remember { mutableStateOf(false) }
    var reviewSearchQuery by remember { mutableStateOf("") }
    var showReviewFilterMenu by remember { mutableStateOf(false) }
    var reviewFilterType by remember { mutableStateOf("All") }
    var showReviewMoreMenu by remember { mutableStateOf(false) }
    var expandAllDiffs by remember { mutableStateOf(false) }
    val fileDiffCache by viewModel.gitDiffCache.collectAsStateWithLifecycle()
    val loadingDiffs by viewModel.gitLoadingDiffs.collectAsStateWithLifecycle()

    var showTerminalSessionsMenu by remember { mutableStateOf(false) }
    var showTerminalMoreMenu by remember { mutableStateOf(false) }
    var showWorkspacePickerMenu by remember { mutableStateOf(false) }

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()
    val installedSkills by viewModel.installedSkills.collectAsStateWithLifecycle()

    val reloadWorkspaceData: () -> Unit = {
        scope.launch(Dispatchers.IO) {

            viewModel.fetchGitChangedFiles(context, workspacePath)

            val currentUploads = mutableListOf<UploadedMediaItem>()
            val msgAttachments = chatMessages.flatMap { it.attachments }
            val combined = (msgAttachments + attachedFiles).distinctBy { it.uri.toString() }
            for (att in combined) {
                currentUploads.add(UploadedMediaItem(att.name, att.uri.toString()))
            }
            uploadsList = currentUploads

            val currentSkills = viewModel.getInstalledSkills(context).map {
                InstalledSkillItem(it.name, it.path ?: "builtin")
            }
            skillsList = currentSkills

            viewModel.refreshConversationArtifacts(context)
        }
    }

    LaunchedEffect(workspacePath, chatMessages, attachedFiles, installedSkills) {
        val isProjectOpen = !workspacePath.isNullOrBlank() && File(workspacePath ?: "").isDirectory
        if (!isProjectOpen && selectedTab == 3 && activeEditorTab == null) {
            selectedTab = 0
        }
        reloadWorkspaceData()
    }

    val blockSheetScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {

                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {

                return available
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        BackHandler(enabled = activeEditorTab != null) {
            if (activeEditorTab != null) {
                viewModel.closeEditorTab(activeEditorTab.id)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .nestedScroll(blockSheetScrollConnection)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(3.dp)
                    ) {
                        TabPillButton(
                            selected = selectedTab == 0,
                            icon = Icons.AutoMirrored.Filled.Article,
                            contentDescription = stringResource(R.string.agent_tab_overview),
                            onClick = { selectedTab = 0 }
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        TabPillButton(
                            selected = selectedTab == 1,
                            icon = Icons.Default.Difference,
                            contentDescription = stringResource(R.string.agent_tab_review),
                            onClick = { selectedTab = 1 }
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        TabPillButton(
                            selected = selectedTab == 2,
                            icon = Icons.Default.Terminal,
                            contentDescription = stringResource(R.string.agent_tab_terminals),
                            onClick = {
                                selectedTab = 2
                                if (viewModel.isExecuting.value || EngineExecutor.isAgyRunning()) {
                                    TerminalSessionManager.ensureAgyLiveSession(autoSwitch = true)
                                } else {
                                    TerminalSessionManager.ensureBionicSession(autoSwitch = true)
                                }
                            }
                        )
                        if (!workspacePath.isNullOrBlank() && File(workspacePath ?: "").isDirectory) {
                            Spacer(modifier = Modifier.width(3.dp))
                            TabPillButton(
                                selected = selectedTab == 3,
                                icon = Icons.Default.Folder,
                                contentDescription = stringResource(R.string.agent_tab_files),
                                onClick = { selectedTab = 3 }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "AgentDetailsTabTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetTab ->
                when (targetTab) {
                0 -> {

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp)
                    ) {

                        item {
                            AccordionHeader(
                                title = stringResource(R.string.agent_details_subagents),
                                count = runningSubagents.size,
                                isExpanded = expandedSubagents,
                                onToggle = { expandedSubagents = !expandedSubagents }
                            )
                        }
                        if (expandedSubagents) {
                            if (runningSubagents.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.agent_details_no_subagents),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                items(runningSubagents) { sub ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.openSubagentAsActiveConversation(context, sub)
                                            }
                                            .padding(horizontal = 20.dp, vertical = 8.dp)
                                    ) {
                                        if (sub.isRunning) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                strokeWidth = 2.dp,
                                                color = AntigravityBlue
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.SmartToy,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = AntigravityBlue
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sub.role.ifEmpty { sub.typeName.ifEmpty { stringResource(R.string.subagent_default_title) } },
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (sub.isRunning) stringResource(R.string.subagent_chat_running) else stringResource(R.string.subagent_chat_completed),
                                                fontSize = 10.5.sp,
                                                color = if (sub.isRunning) AntigravityBlue else AntigravityGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (runningTasks.isNotEmpty()) {
                            item {
                                AccordionHeader(
                                    title = stringResource(R.string.chat_tasks_running, runningTasks.size),
                                    count = runningTasks.size,
                                    isExpanded = expandedTasks,
                                    onToggle = { expandedTasks = !expandedTasks }
                                )
                            }
                            if (expandedTasks) {
                                items(runningTasks) { task ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedTab = 2
                                                if (viewModel.isExecuting.value) {
                                                    TerminalSessionManager.ensureAgyLiveSession(autoSwitch = true)
                                                }
                                            }
                                            .padding(horizontal = 20.dp, vertical = 8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 2.dp,
                                            color = AntigravityBlue
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Icon(
                                            imageVector = Icons.Default.Terminal,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = AntigravityBlue
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.command,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = stringResource(R.string.agent_subagent_task_subtext, task.id),
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            AccordionHeader(
                                title = stringResource(R.string.agent_details_files_changed),
                                count = filesChanged.size,
                                isExpanded = expandedFiles,
                                onToggle = { expandedFiles = !expandedFiles }
                            )
                        }
                        if (expandedFiles) {
                            if (filesChanged.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.agent_details_no_files_changed),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                items(filesChanged.take(8)) { file ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                val ws = workspacePath
                                                val target = if (!ws.isNullOrBlank()) File(ws, file.relativePath) else File(file.relativePath)
                                                if (target.exists() && target.isFile) {
                                                    viewModel.openFileInEditor(target)
                                                    selectedTab = 3
                                                } else {
                                                    selectedTab = 1
                                                }
                                            }
                                            .padding(horizontal = 20.dp, vertical = 6.dp)
                                    ) {
                                        FileBadgeIcon(fileName = file.name)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = file.relativePath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (filesChanged.size > 8) {
                                    item {
                                        TextButton(
                                            onClick = { selectedTab = 1 },
                                            modifier = Modifier.padding(start = 16.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.agent_details_see_all, filesChanged.size),
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            AccordionHeader(
                                title = stringResource(R.string.agent_details_artifacts),
                                count = artifactsList.size,
                                isExpanded = expandedArtifacts,
                                onToggle = { expandedArtifacts = !expandedArtifacts }
                            )
                        }
                        if (expandedArtifacts) {
                            if (artifactsList.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.agent_details_no_artifacts),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                items(artifactsList) { art ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                val target = File(art.filePath)
                                                if (target.exists() && target.isFile) {
                                                    viewModel.openFileInEditor(target)
                                                    selectedTab = 3
                                                }
                                            }
                                            .padding(horizontal = 20.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = AntigravityBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = art.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${art.byteSize / 1024} KB • ${art.filePath}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            AccordionHeader(
                                title = stringResource(R.string.agent_details_uploads),
                                count = uploadsList.size,
                                isExpanded = expandedUploads,
                                onToggle = { expandedUploads = !expandedUploads }
                            )
                        }
                        if (expandedUploads) {
                            if (uploadsList.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.agent_details_no_uploads),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                items(uploadsList.take(6)) { upload ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = upload.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (uploadsList.size > 6) {
                                    item {
                                        TextButton(
                                            onClick = { showAllUploadsDialog = true },
                                            modifier = Modifier.padding(start = 16.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.agent_details_see_all, uploadsList.size),
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val isTerminalRunning = TerminalSessionManager.isRunning.value
                        val totalTasksCount = runningTasks.size + daemonTasks.size + (if (isTerminalRunning) 1 else 0)
                        item {
                            AccordionHeader(
                                title = stringResource(R.string.agent_details_background_tasks),
                                count = totalTasksCount,
                                isExpanded = expandedTasks,
                                onToggle = { expandedTasks = !expandedTasks }
                            )
                        }
                        if (expandedTasks) {
                            if (totalTasksCount == 0) {
                                item {
                                    Text(
                                        text = stringResource(R.string.agent_details_no_tasks),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                if (isTerminalRunning) {
                                    item {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = AntigravityGreen
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = stringResource(R.string.agent_details_active_task),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                items(runningTasks) { task ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 6.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 1.8.dp,
                                            color = AntigravityBlue
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (task.command.isNotBlank()) {
                                                Text(
                                                    text = task.command,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                items(daemonTasks) { dtask ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (dtask.enabled) AntigravityGreen else MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(8.dp)
                                        ) {}
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = dtask.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val schedText = if (dtask.triggerType == "cron" && dtask.cron.isNotBlank()) {
                                                    stringResource(R.string.daemon_task_cron, dtask.cron)
                                                } else if (dtask.intervalSec > 0) {
                                                    stringResource(R.string.daemon_task_interval, dtask.intervalSec)
                                                } else {
                                                    dtask.triggerType
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                                ) {
                                                    Text(
                                                        text = schedText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            if (dtask.command.isNotBlank()) {
                                                Text(
                                                    text = dtask.command,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (dtask.enabled) stringResource(R.string.daemon_task_status_active) else stringResource(R.string.daemon_task_status_disabled),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (dtask.enabled) AntigravityGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            AccordionHeader(
                                title = stringResource(R.string.agent_details_terminals),
                                count = TerminalSessionManager.sessionNames.size,
                                isExpanded = false,
                                onToggle = {
                                    selectedTab = 2
                                    if (viewModel.isExecuting.value || EngineExecutor.isAgyRunning()) {
                                        TerminalSessionManager.ensureAgyLiveSession(autoSwitch = true)
                                    } else {
                                        TerminalSessionManager.ensureBionicSession(autoSwitch = true)
                                    }
                                }
                            )
                        }

                        item {
                            AccordionHeader(
                                title = stringResource(R.string.agent_details_skills_used),
                                count = skillsList.size,
                                isExpanded = expandedSkills,
                                onToggle = { expandedSkills = !expandedSkills }
                            )
                        }
                        if (expandedSkills) {
                            if (skillsList.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.agent_details_no_skills),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                items(skillsList) { skill ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = skill.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = skill.path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {

                    val filteredFiles = remember(filesChanged, reviewSearchQuery, reviewFilterType) {
                        filesChanged.filter { file ->
                            val matchSearch = reviewSearchQuery.isBlank() ||
                                file.name.contains(reviewSearchQuery, ignoreCase = true) ||
                                file.relativePath.contains(reviewSearchQuery, ignoreCase = true)
                            val matchFilter = when (reviewFilterType) {
                                "Kotlin" -> file.name.endsWith(".kt") || file.name.endsWith(".kts")
                                "Config" -> file.name.endsWith(".json") || file.name.endsWith(".xml") || file.name.endsWith(".gradle")
                                "Markdown" -> file.name.endsWith(".md")
                                else -> true
                            }
                            matchSearch && matchFilter
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Review (${filteredFiles.size}/${filesChanged.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {

                                IconButton(
                                    onClick = { isReviewSearching = !isReviewSearching },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(R.string.chat_search),
                                        tint = if (isReviewSearching) AntigravityBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showReviewFilterMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.List,
                                            contentDescription = stringResource(R.string.common_filter),
                                            tint = if (reviewFilterType != "All") AntigravityBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showReviewFilterMenu,
                                        onDismissRequest = { showReviewFilterMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        listOf("All", "Kotlin", "Config", "Markdown").forEach { f ->
                                            DropdownMenuItem(
                                                text = { Text(f, fontSize = 13.sp) },
                                                onClick = {
                                                    reviewFilterType = f
                                                    showReviewFilterMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Box {
                                    IconButton(
                                        onClick = { showReviewMoreMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.common_more),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showReviewMoreMenu,
                                        onDismissRequest = { showReviewMoreMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.git_refresh_status), fontSize = 13.sp) },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                viewModel.clearGitDiffCache()
                                                reloadWorkspaceData()
                                                showReviewMoreMenu = false
                                                Toast.makeText(context, "Workspace diff refreshed", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (expandAllDiffs) "Collapse All" else "Expand All", fontSize = 13.sp) },
                                            onClick = {
                                                expandAllDiffs = !expandAllDiffs
                                                showReviewMoreMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (isReviewSearching) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = reviewSearchQuery,
                                        onValueChange = { reviewSearchQuery = it },
                                        modifier = Modifier.weight(1f),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                        singleLine = true
                                    )
                                    if (reviewSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { reviewSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.chat_clear), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp)
                        ) {
                            if (filteredFiles.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 40.dp, start = 20.dp, end = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (reviewSearchQuery.isNotEmpty()) "No matching modified files found." else "No modified files in workspace to review.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(filteredFiles) { file ->
                                    val fullRelPath = if (file.relativePath.isNotBlank()) "${file.relativePath}/${file.name}" else file.name
                                    var itemExpanded by remember(expandAllDiffs) { mutableStateOf(expandAllDiffs) }

                                    LaunchedEffect(itemExpanded) {
                                        if (itemExpanded && !fileDiffCache.containsKey(fullRelPath)) {
                                            viewModel.fetchGitDiff(context, workspacePath, fullRelPath)
                                        }
                                    }

                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 2.dp)
                                    ) {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { itemExpanded = !itemExpanded }
                                                    .padding(vertical = 8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    FileBadgeIcon(fileName = file.name)
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = file.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = file.relativePath,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Icon(
                                                    imageVector = if (itemExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            AnimatedVisibility(visible = itemExpanded) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 28.dp, bottom = 8.dp, top = 2.dp)
                                                ) {
                                                    if (loadingDiffs.contains(fullRelPath)) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(14.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                        }
                                                    } else {
                                                        val content = fileDiffCache[fullRelPath] ?: ""
                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                            content.lines().take(60).forEach { line ->
                                                                val color = when {
                                                                    line.startsWith("+") && !line.startsWith("+++") -> AntigravityGreen
                                                                    line.startsWith("-") && !line.startsWith("---") -> AntigravityRed
                                                                    line.startsWith("@@") -> AntigravityBlue
                                                                    else -> Color(0xFF8B949E)
                                                                }
                                                                Text(
                                                                    text = line,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontSize = 11.sp,
                                                                    color = color,
                                                                    lineHeight = 15.sp
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

                2 -> {

                    val projName = workspacePath?.let { File(it).name }.orEmpty()
                    val currentSession = TerminalSessionManager.currentSessionName.value
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.agent_tab_terminals),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(10.dp))

                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable { showTerminalSessionsMenu = true }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            val isAgy = currentSession == TerminalSessionManager.AGY_SESSION_NAME
                                            if (isAgy) {
                                                androidx.compose.foundation.Image(
                                                    painter = painterResource(id = R.drawable.ic_launcher_logo),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Terminal,
                                                    contentDescription = null,
                                                    tint = AntigravityGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = currentSession,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showTerminalSessionsMenu,
                                        onDismissRequest = { showTerminalSessionsMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        TerminalSessionManager.sessionNames.forEach { sName ->
                                            val isAgy = sName == TerminalSessionManager.AGY_SESSION_NAME
                                            val isSelected = sName == TerminalSessionManager.currentSessionName.value
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        sName,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isAgy) Color(0xFF38BDF8) else Color.Unspecified
                                                    )
                                                },
                                                leadingIcon = {
                                                    if (isAgy) {
                                                        androidx.compose.foundation.Image(
                                                            painter = painterResource(id = R.drawable.ic_launcher_logo),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.Terminal,
                                                            contentDescription = null,
                                                            tint = if (isSelected) AntigravityGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    TerminalSessionManager.switchSession(sName)
                                                    showTerminalSessionsMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { TerminalSessionManager.newSession() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = stringResource(R.string.terminal_btn_new),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showTerminalMoreMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = stringResource(R.string.terminal_sessions_title),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showTerminalMoreMenu,
                                        onDismissRequest = { showTerminalMoreMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.terminal_clear_buffer), fontSize = 13.sp) },
                                            leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                TerminalSessionManager.clear()
                                                showTerminalMoreMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.terminal_kill_process), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                TerminalSessionManager.killCurrentProcess()
                                                showTerminalMoreMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.terminal_restart_shell), fontSize = 13.sp) },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            onClick = {
                                                TerminalSessionManager.restartShell()
                                                showTerminalMoreMenu = false
                                            }
                                        )
                                        if (projName.isNotBlank()) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.workspace_label, projName), fontSize = 13.sp) },
                                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    showWorkspacePickerMenu = true
                                                    showTerminalMoreMenu = false
                                                }
                                            )
                                        }
                                    }

                                    if (showWorkspacePickerMenu) {
                                        DropdownMenu(
                                            expanded = showWorkspacePickerMenu,
                                            onDismissRequest = { showWorkspacePickerMenu = false },
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        ) {
                                            if (projectList.isEmpty()) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.workspaces_empty_configured), fontSize = 12.sp) },
                                                    onClick = { showWorkspacePickerMenu = false }
                                                )
                                            } else {
                                                projectList.forEach { p ->
                                                    DropdownMenuItem(
                                                        text = { Text(p.name, fontSize = 13.sp, fontWeight = if (p.absolutePath == workspacePath) FontWeight.Bold else FontWeight.Normal) },
                                                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                                        onClick = {
                                                            viewModel.setWorkspacePath(context, p.absolutePath)
                                                            showWorkspacePickerMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        TerminalView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            showHeader = false
                        )
                    }
                }
                3 -> {

                    val rootDir = remember(workspacePath) { File(workspacePath ?: "") }
                    val currentTab = activeEditorTab
                    if (currentTab == null) {
                        FileTree(
                            rootDirectory = rootDir,
                            onFileSelected = { file ->
                                if (file.isFile) {
                                    viewModel.openFileInEditor(file)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CodeEditor(
                            tab = currentTab,
                            onSaveFile = { file, newContent ->
                                viewModel.updateEditorContent(currentTab.id, newContent)
                                viewModel.saveEditorTab(context, currentTab.id)
                            },
                            onCloseTab = {
                                viewModel.closeEditorTab(currentTab.id)
                            },
                            onToggleDiff = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
    }

    if (showAllUploadsDialog) {
        AlertDialog(
            onDismissRequest = { showAllUploadsDialog = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(stringResource(R.string.agent_uploaded_attachments, uploadsList.size), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    uploadsList.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(item.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun TabPillButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun AccordionHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 200),
        label = "accordionArrow"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = count.toString(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(16.dp)
                .rotate(arrowRotation)
        )
    }
}

@Composable
fun FileBadgeIcon(
    fileName: String,
    modifier: Modifier = Modifier.size(18.dp)
) {
    val isKotlin = fileName.endsWith(".kt") || fileName.endsWith(".kts")
    if (isKotlin) {
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = Color(0xFF7F52FF).copy(alpha = 0.18f),
            border = BorderStroke(1.dp, Color(0xFF7F52FF)),
            modifier = modifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "K",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC084FC)
                )
            }
        }
    } else {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
    }
}

