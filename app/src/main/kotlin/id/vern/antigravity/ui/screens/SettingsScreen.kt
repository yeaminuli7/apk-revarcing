package id.vern.antigravity.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import id.vern.antigravity.oauth.OAuthManager
import id.vern.antigravity.service.AgyModelsService
import id.vern.antigravity.service.RuleItem
import id.vern.antigravity.service.McpServerItem
import id.vern.antigravity.ui.components.InstallationProgressDialog
import id.vern.antigravity.ui.components.getModelQuotaPercentage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.SolidColor
import id.vern.antigravity.service.PermissionRule
import id.vern.antigravity.ui.components.DialogBlurEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.vern.antigravity.data.ThemeMode
import id.vern.antigravity.data.ThemePreset
import id.vern.antigravity.data.ModelAvailability
import id.vern.antigravity.data.AvailableModelInfo
import id.vern.antigravity.data.SubscriptionTierType
import id.vern.antigravity.oauth.StoredAccount
import id.vern.antigravity.ui.components.LogViewer
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed
import id.vern.antigravity.ui.theme.AntigravityAmber
import id.vern.antigravity.ui.theme.parseColorOrNull
import id.vern.antigravity.ui.theme.isColorDark
import id.vern.antigravity.viewmodel.AntigravityViewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import id.vern.antigravity.R
import kotlinx.coroutines.launch
import java.io.File

enum class SettingsPage {
    MAIN,
    GENERAL,
    APPEARANCE,
    MODELS_AND_USAGE,
    CUSTOMIZATIONS,
    ACCOUNT,
    DEVELOPER,
    ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    viewModel: AntigravityViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: SettingsPage = SettingsPage.MAIN
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageStack = remember(initialPage) {
        mutableStateListOf<SettingsPage>().apply {
            add(SettingsPage.MAIN)
            if (initialPage != SettingsPage.MAIN) {
                add(initialPage)
            }
        }
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refreshAuthState(context)
    }

    BackHandler(enabled = true) {
        if (pageStack.size > 1) {
            pageStack.removeAt(pageStack.lastIndex)
        } else {
            scope.launch {
                sheetState.hide()
                onDismissRequest()
            }
        }
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

    val appearance by viewModel.appearanceConfig.collectAsStateWithLifecycle()
    val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
    val customBg by viewModel.customThemeBg.collectAsStateWithLifecycle()
    val customFg by viewModel.customThemeFg.collectAsStateWithLifecycle()
    val customAccent by viewModel.customThemeAccent.collectAsStateWithLifecycle()
    val isDark = when (appearance.themeMode) {
        id.vern.antigravity.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        id.vern.antigravity.data.ThemeMode.LIGHT -> false
        id.vern.antigravity.data.ThemeMode.DARK -> true
    }

    id.vern.antigravity.ui.theme.AntigravityTheme(
        darkTheme = isDark,
        accentColor = appearance.accentColor,
        themePreset = themePreset,
        customBg = customBg,
        customFg = customFg,
        customAccent = customAccent
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
            containerColor = MaterialTheme.colorScheme.surface,
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
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        BackHandler(enabled = pageStack.size > 1) {
            pageStack.removeAt(pageStack.lastIndex)
        }

        val activeInstallProgress by viewModel.activeInstallProgress.collectAsStateWithLifecycle()
        if (activeInstallProgress != null) {
            InstallationProgressDialog(
                progress = activeInstallProgress!!,
                onDismissRequest = { viewModel.dismissInstallProgress() }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .nestedScroll(blockSheetScrollConnection)
        ) {
            AnimatedContent(
                targetState = pageStack.last(),
                transitionSpec = {
                    if (targetState == SettingsPage.MAIN) {
                        (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it }) + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { it }) + fadeOut(tween(250)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it }) + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { -it }) + fadeOut(tween(250)))
                    }
                },
                label = "settings_navigation"
            ) { currentPage ->
                when (currentPage) {
                    SettingsPage.MAIN -> {
                        MainSettingsPage(
                            viewModel = viewModel,
                            onNavigateTo = { pageStack.add(it) }
                        )
                    }
                    SettingsPage.GENERAL -> {
                        GeneralSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.APPEARANCE -> {
                        AppearanceSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.MODELS_AND_USAGE -> {
                        ModelsAndUsageSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.CUSTOMIZATIONS -> {
                        CustomizationsSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.ACCOUNT -> {
                        AccountSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) },
                            onNavigateTo = { pageStack.add(it) }
                        )
                    }
                    SettingsPage.DEVELOPER -> {
                        DeveloperSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.ABOUT -> {
                        AboutSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AntigravityViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: SettingsPage = SettingsPage.MAIN
) {
    SettingsBottomSheet(
        viewModel = viewModel,
        onDismissRequest = onNavigateBack,
        modifier = modifier,
        initialPage = initialPage
    )
}

@Composable
fun MainSettingsPage(
    viewModel: AntigravityViewModel,
    onNavigateTo: (SettingsPage) -> Unit
) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userPicture by viewModel.userPicture.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {

        Text(
            text = "Settings",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            SettingsMenuRow(
                title = "General",
                onClick = { onNavigateTo(SettingsPage.GENERAL) }
            )
            SettingsMenuRow(
                title = "Appearance",
                onClick = { onNavigateTo(SettingsPage.APPEARANCE) }
            )
            SettingsMenuRow(
                title = "Models",
                onClick = { onNavigateTo(SettingsPage.MODELS_AND_USAGE) }
            )
            SettingsMenuRow(
                title = "Customizations",
                onClick = { onNavigateTo(SettingsPage.CUSTOMIZATIONS) }
            )
            SettingsMenuRow(
                title = "Developer & System",
                onClick = { onNavigateTo(SettingsPage.DEVELOPER) }
            )
            SettingsMenuRow(
                title = stringResource(R.string.settings_about_title),
                onClick = { onNavigateTo(SettingsPage.ABOUT) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    if (userEmail != null) {
                        onNavigateTo(SettingsPage.ACCOUNT)
                    } else {
                        viewModel.startGoogleSignIn(context)
                    }
                }
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (userEmail != null) AntigravityBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AntigravityBlue
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (userEmail != null) userName.ifBlank { "Google User" } else stringResource(R.string.account_sign_in_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (userEmail != null) stringResource(R.string.status_authenticated) else stringResource(R.string.account_tap_to_sign_in),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (userEmail != null) stringResource(R.string.account_title) else stringResource(R.string.account_btn_sign_in),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun SettingsMenuRow(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SubSheetHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onBack() }
                .padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier.size(16.dp),
                tint = AntigravityBlue
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.common_back),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = AntigravityBlue
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (trailingAction != null) {
                trailingAction()
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}

enum class GeneralSubPage {
    MAIN,
    TOOL_PERMISSIONS,
    FILE_PERMISSIONS,
    TERMINAL_COMMANDS,
    MCP_TOOLS,
    NETWORK_PERMISSIONS
}

@Composable
fun PermissionRulesCard(
    title: String,
    subtitle: String,
    rules: List<PermissionRule>,
    placeholder: String,
    onAddRule: (pattern: String, mode: String) -> Unit,
    onUpdateRule: (oldPattern: String, newPattern: String, newMode: String) -> Unit,
    onRemoveRule: (pattern: String) -> Unit
) {
    var isAdding by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf("") }
    var addMode by remember { mutableStateOf("allow") }
    var showAddModeDropdown by remember { mutableStateOf(false) }

    SettingsCard {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { isAdding = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.common_add), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (rules.isNotEmpty() || isAdding) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    if (isAdding) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showAddModeDropdown = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = addMode.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showAddModeDropdown,
                                    onDismissRequest = { showAddModeDropdown = false },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                ) {
                                    listOf("allow", "ask", "deny").forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m.replaceFirstChar { it.uppercase() }) },
                                            onClick = {
                                                addMode = m
                                                showAddModeDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .border(1.dp, AntigravityBlue, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (addText.isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                BasicTextField(
                                    value = addText,
                                    onValueChange = { addText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(AntigravityBlue),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    if (addText.isNotBlank()) {
                                        onAddRule(addText.trim(), addMode)
                                        addText = ""
                                        isAdding = false
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.common_confirm),
                                    tint = AntigravityGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    isAdding = false
                                    addText = ""
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_cancel),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    rules.forEach { rule ->
                        var isEditing by remember(rule.pattern, rule.mode) { mutableStateOf(false) }
                        var editText by remember(rule.pattern) { mutableStateOf(rule.pattern) }
                        var showRuleModeDropdown by remember { mutableStateOf(false) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showRuleModeDropdown = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = rule.mode.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = when (rule.mode.lowercase()) {
                                                "allow" -> AntigravityGreen
                                                "deny" -> MaterialTheme.colorScheme.error
                                                else -> Color(0xFFEAB308)
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showRuleModeDropdown,
                                    onDismissRequest = { showRuleModeDropdown = false },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                ) {
                                    listOf("allow", "ask", "deny").forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m.replaceFirstChar { it.uppercase() }) },
                                            onClick = {
                                                onUpdateRule(rule.pattern, rule.pattern, m)
                                                showRuleModeDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isEditing) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .border(1.dp, AntigravityBlue, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = editText,
                                        onValueChange = { editText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        cursorBrush = SolidColor(AntigravityBlue),
                                        singleLine = true
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        if (editText.isNotBlank()) {
                                            onUpdateRule(rule.pattern, editText.trim(), rule.mode)
                                            isEditing = false
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.common_save),
                                        tint = AntigravityGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isEditing = false
                                        editText = rule.pattern
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.common_cancel),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = rule.pattern,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { isEditing = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.common_edit),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onRemoveRule(rule.pattern) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.common_remove),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@Composable
fun GeneralSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val pageStack = remember { mutableStateListOf(GeneralSubPage.MAIN) }
    val currentSubPage = pageStack.last()

    val queuedMode by viewModel.queuedMessagesMode.collectAsStateWithLifecycle()
    val securityPreset by viewModel.securityPreset.collectAsStateWithLifecycle()
    val planReviewPolicy by viewModel.planReviewPolicy.collectAsStateWithLifecycle()
    val permissionState by viewModel.permissionState.collectAsStateWithLifecycle()

    var showSecurityMenu by remember { mutableStateOf(false) }
    var showPlanReviewMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions(context)
    }

    BackHandler(enabled = pageStack.size > 1) {
        pageStack.removeAt(pageStack.lastIndex)
    }

    when (currentSubPage) {
        GeneralSubPage.MAIN -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "General",
                    subtitle = "Configure agent execution, queued message delivery, and permissions.",
                    onBack = onBack
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {

                    SettingsSectionTitle(title = "Execution")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Queued Messages",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Configure when follow-up messages are sent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(2.dp)
                            ) {
                                listOf("Queue", "Send Immediately").forEach { opt ->
                                    val selected = queuedMode == opt
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewModel.setQueuedMessagesMode(context, opt) }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = opt,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsSectionTitle(title = "Global Permissions")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = stringResource(R.string.chat_security_preset),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_security_preset_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val secPresetDisplay = when {
                                    securityPreset.startsWith("Turbo", ignoreCase = true) -> stringResource(R.string.security_preset_turbo_title)
                                    securityPreset.equals("Auto", ignoreCase = true) || securityPreset.equals("Permissive", ignoreCase = true) -> stringResource(R.string.security_preset_auto_title)
                                    securityPreset.equals("Default", ignoreCase = true) || securityPreset.equals("Standard", ignoreCase = true) -> stringResource(R.string.security_preset_default_title)
                                    securityPreset.equals("Request Review", ignoreCase = true) -> stringResource(R.string.security_preset_request_review_title)
                                    else -> securityPreset
                                }
                                Box {
                                    OutlinedButton(
                                        onClick = { showSecurityMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(secPresetDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = showSecurityMenu,
                                        onDismissRequest = { showSecurityMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        val presetsList = listOf(
                                            Pair("Request Review", R.string.security_preset_request_review_title),
                                            Pair("Default", R.string.security_preset_default_title),
                                            Pair("Auto", R.string.security_preset_auto_title),
                                            Pair("Turbo", R.string.security_preset_turbo_title)
                                        )
                                        presetsList.forEach { (key, titleRes) ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(titleRes), fontSize = 13.sp) },
                                                onClick = {
                                                    viewModel.setSecurityPreset(context, key)
                                                    showSecurityMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Tool Permissions",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (permissionState.toolPermissionsCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BadgePill(text = "${permissionState.toolPermissionsCount}")
                                        }
                                    }
                                    Text(
                                        text = "Modify permissions for file, terminal, and MCP tools.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pageStack.add(GeneralSubPage.TOOL_PERMISSIONS) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(R.string.common_btn_open), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsSectionTitle(title = "Agent Behavior")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Plan Review Policy",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Determine when the agent should pause and ask you to review its plan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box {
                                    OutlinedButton(
                                        onClick = { showPlanReviewMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(planReviewPolicy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = showPlanReviewMenu,
                                        onDismissRequest = { showPlanReviewMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        listOf("Always Ask", "Inherit Global", "Always Proceed").forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt, fontSize = 13.sp) },
                                                onClick = {
                                                    viewModel.setPlanReviewPolicy(context, opt)
                                                    showPlanReviewMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsSectionTitle(title = "Network Permissions")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Network Access Rules",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (permissionState.networkPermissionsCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BadgePill(text = "${permissionState.networkPermissionsCount}")
                                        }
                                    }
                                    Text(
                                        text = "Configure allowed and denied URLs for reading.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pageStack.add(GeneralSubPage.NETWORK_PERMISSIONS) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(R.string.common_btn_open), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        GeneralSubPage.TOOL_PERMISSIONS -> {
            val toolScrollState = rememberScrollState()
            val fileRulesCount = permissionState.getCountForAction("read_file") + permissionState.getCountForAction("write_file")
            val commandRulesCount = permissionState.getCountForAction("command")
            val mcpRulesCount = permissionState.getCountForAction("mcp")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "Tool Permissions",
                    subtitle = "Modify permissions for file, terminal, and MCP tools.",
                    onBack = { pageStack.removeAt(pageStack.lastIndex) }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(toolScrollState)
                ) {
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "File Access Rules",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (fileRulesCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BadgePill(text = "$fileRulesCount")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Configure allowed and denied paths for file reads and writes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pageStack.add(GeneralSubPage.FILE_PERMISSIONS) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.common_open), fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Terminal Commands",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (commandRulesCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BadgePill(text = "$commandRulesCount")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Configure allowed terminal commands.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pageStack.add(GeneralSubPage.TERMINAL_COMMANDS) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.common_open), fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "MCP Tools",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (mcpRulesCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BadgePill(text = "$mcpRulesCount")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Configure external tools via Model Context Protocol.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pageStack.add(GeneralSubPage.MCP_TOOLS) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.common_open), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        GeneralSubPage.FILE_PERMISSIONS -> {
            val fileScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "File Permissions",
                    subtitle = "Configure allowed and denied paths for file reads and writes.",
                    onBack = { pageStack.removeAt(pageStack.lastIndex) }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(fileScrollState)
                ) {
                    PermissionRulesCard(
                        title = "File Reads",
                        subtitle = "Allow/deny agent read access to specific files or directories.",
                        rules = permissionState.getRulesForAction("read_file"),
                        placeholder = "e.g., /path/to/file",
                        onAddRule = { pattern, mode ->
                            viewModel.addPermissionRule(context, "read_file", pattern, mode)
                        },
                        onUpdateRule = { oldPattern, newPattern, newMode ->
                            viewModel.updatePermissionRule(context, "read_file", oldPattern, newPattern, newMode)
                        },
                        onRemoveRule = { pattern ->
                            viewModel.removePermissionRule(context, "read_file", pattern)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PermissionRulesCard(
                        title = "File Writes",
                        subtitle = "Allow/deny agent write access to specific files or directories.",
                        rules = permissionState.getRulesForAction("write_file"),
                        placeholder = "e.g., /path/to/file",
                        onAddRule = { pattern, mode ->
                            viewModel.addPermissionRule(context, "write_file", pattern, mode)
                        },
                        onUpdateRule = { oldPattern, newPattern, newMode ->
                            viewModel.updatePermissionRule(context, "write_file", oldPattern, newPattern, newMode)
                        },
                        onRemoveRule = { pattern ->
                            viewModel.removePermissionRule(context, "write_file", pattern)
                        }
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        GeneralSubPage.TERMINAL_COMMANDS -> {
            val termScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "Terminal Commands",
                    subtitle = "Configure allowed terminal commands.",
                    onBack = { pageStack.removeAt(pageStack.lastIndex) }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(termScrollState)
                ) {
                    PermissionRulesCard(
                        title = "Terminal Commands",
                        subtitle = "Allow/deny specific terminal commands.",
                        rules = permissionState.getRulesForAction("command"),
                        placeholder = "e.g., npm test",
                        onAddRule = { pattern, mode ->
                            viewModel.addPermissionRule(context, "command", pattern, mode)
                        },
                        onUpdateRule = { oldPattern, newPattern, newMode ->
                            viewModel.updatePermissionRule(context, "command", oldPattern, newPattern, newMode)
                        },
                        onRemoveRule = { pattern ->
                            viewModel.removePermissionRule(context, "command", pattern)
                        }
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        GeneralSubPage.MCP_TOOLS -> {
            val mcpScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "MCP Tools",
                    subtitle = "External tools the agent can call via Model Context Protocol.",
                    onBack = { pageStack.removeAt(pageStack.lastIndex) }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(mcpScrollState)
                ) {
                    PermissionRulesCard(
                        title = "MCP Tools",
                        subtitle = "External tools the agent can call via Model Context Protocol.",
                        rules = permissionState.getRulesForAction("mcp"),
                        placeholder = "Enter tool name or server...",
                        onAddRule = { pattern, mode ->
                            viewModel.addPermissionRule(context, "mcp", pattern, mode)
                        },
                        onUpdateRule = { oldPattern, newPattern, newMode ->
                            viewModel.updatePermissionRule(context, "mcp", oldPattern, newPattern, newMode)
                        },
                        onRemoveRule = { pattern ->
                            viewModel.removePermissionRule(context, "mcp", pattern)
                        }
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        GeneralSubPage.NETWORK_PERMISSIONS -> {
            val netScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "Network Permissions",
                    subtitle = "Configure allowed and denied URLs for reading.",
                    onBack = { pageStack.removeAt(pageStack.lastIndex) }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(netScrollState)
                ) {
                    PermissionRulesCard(
                        title = "Read URLs",
                        subtitle = "Allow/deny agent read access to specific URLs or domains.",
                        rules = permissionState.getRulesForAction("read_url"),
                        placeholder = "e.g., https://example.com",
                        onAddRule = { pattern, mode ->
                            viewModel.addPermissionRule(context, "read_url", pattern, mode)
                        },
                        onUpdateRule = { oldPattern, newPattern, newMode ->
                            viewModel.updatePermissionRule(context, "read_url", oldPattern, newPattern, newMode)
                        },
                        onRemoveRule = { pattern ->
                            viewModel.removePermissionRule(context, "read_url", pattern)
                        }
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

    }
}

@Composable
fun CircularQuotaRow(
    label: String,
    refreshText: String,
    percentage: Int,
    disabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (refreshText.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = refreshText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp)
            ) {
                val sweepAngle = (percentage / 100f) * 360f
                val strokeColor = when {
                    disabled -> Color(0xFF3F3F46)
                    percentage == 0 -> Color(0xFFEF5350)
                    percentage < 50 -> Color(0xFFFFB300)
                    else -> AntigravityGreen
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                ) {
                    drawArc(
                        color = Color(0xFF27272A),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = strokeColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun ModelsAndUsageSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val enableCreditOverages by viewModel.enableCreditOverages.collectAsStateWithLifecycle()
    val userQuotaSummary by viewModel.userQuotaSummary.collectAsStateWithLifecycle()
    val codeAssistTier by viewModel.codeAssistTier.collectAsStateWithLifecycle()
    val isQuotaLoading by viewModel.isQuotaLoading.collectAsStateWithLifecycle()
    val quotaErrorMessage by viewModel.quotaErrorMessage.collectAsStateWithLifecycle()
    val cliModels by viewModel.cliModels.collectAsStateWithLifecycle()

    LaunchedEffect(Unit, userEmail) {
        if (userEmail != null) {
            viewModel.refreshUserQuota(context)
            viewModel.refreshCliModels(context)
        }
    }

    if (userEmail == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            SubSheetHeader(
                title = "Models & Usage",
                subtitle = "Manage your model quota and account.",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Sign In Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Connect your Google account with OAuth 2.0 to access Gemini models, synchronize live usage, and view model quotas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.startGoogleSignIn(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.account_sign_in_google), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        return
    }

    val userName = remember(userEmail) { id.vern.antigravity.oauth.OAuthManager.getUserName(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        SubSheetHeader(
            title = "Models & Usage",
            subtitle = "Manage your model quota and credits.",
            onBack = onBack,
            trailingAction = {
                IconButton(
                    onClick = {
                        viewModel.refreshUserQuota(context)
                        viewModel.refreshCliModels(context)
                        Toast.makeText(context, "Refreshing live quota...", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.file_manager_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {

            if (isQuotaLoading && userQuotaSummary == null && codeAssistTier == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = AntigravityBlue,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Fetching live model quota from Google Cloud Code...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (quotaErrorMessage != null && userQuotaSummary == null && !isQuotaLoading) {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Quota Unavailable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = quotaErrorMessage ?: "Check your connection or sign-in state.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.refreshUserQuota(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.btn_retry), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            val curTier = codeAssistTier?.currentTier
            if (curTier != null && curTier.name.isNotBlank()) {
                val planName = curTier.name
                val upgradeDesc = curTier.upgradeSubscriptionText.ifBlank { curTier.description }
                val upgradeUri = curTier.upgradeSubscriptionUri.ifBlank {
                    codeAssistTier?.paidTier?.upgradeSubscriptionUri ?: ""
                }

                SettingsSectionTitle(title = "Plan")
                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Your Plan: $planName",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (upgradeDesc.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = upgradeDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                        if (upgradeUri.isNotBlank()) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upgradeUri))
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.common_btn_upgrade),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            SettingsSectionTitle(title = "Model Credits")
            SettingsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Enable AI Credit Overages",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "When toggled on, Antigravity will use your AI credits to fulfill model requests once you're out of model quota. Antigravity will always use your model quota first before using AI credits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        Switch(
                            checked = enableCreditOverages,
                            onCheckedChange = { viewModel.setEnableCreditOverages(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AntigravityBlue
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            val groups = userQuotaSummary?.groups ?: emptyList()
            if (groups.isNotEmpty()) {
                groups.forEach { group ->
                    SettingsSectionTitleWithInfo(
                        title = group.displayName,
                        info = group.description ?: ""
                    )
                    SettingsCard {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                            group.buckets.forEachIndexed { idx, bucket ->
                                if (idx > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                                CircularQuotaRow(
                                    label = bucket.displayName,
                                    refreshText = bucket.description,
                                    percentage = bucket.percentage,
                                    disabled = bucket.disabled
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            } else if (!isQuotaLoading && quotaErrorMessage == null) {
                SettingsCard {
                    Text(
                        text = "No quota information available for this account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            val availableModels = userQuotaSummary?.availableModels ?: emptyList()
            val effectiveModels = remember(userQuotaSummary, availableModels, cliModels) {
                if (cliModels.isNotEmpty()) {
                    cliModels
                        .filter { AgyModelsService.isRealAiModel(it.cliId, it.displayName) }
                        .map { cli ->
                            val matchingAvail = availableModels.firstOrNull { avail ->
                                avail.modelId.equals(cli.cliId, ignoreCase = true) ||
                                avail.displayName.equals(cli.label, ignoreCase = true) ||
                                avail.displayName.equals(cli.displayName, ignoreCase = true) ||
                                avail.matches(cli.cliId) ||
                                avail.matches(cli.displayName)
                            }
                            val pct = matchingAvail?.percentage
                                ?: getModelQuotaPercentage(cli.cliId, userQuotaSummary).first
                                ?: getModelQuotaPercentage(cli.displayName, userQuotaSummary).first
                            val isArgon = cli.cliId.contains("argon", ignoreCase = true) || cli.displayName.contains("argon", ignoreCase = true)
                            val resetT = matchingAvail?.resetTime ?: (
                                if (isArgon) {
                                    userQuotaSummary?.groups?.firstOrNull { it.displayName.contains("argon", ignoreCase = true) }?.buckets?.firstOrNull()?.resetTime
                                        ?: userQuotaSummary?.groups?.firstOrNull { it.displayName.contains("gemini", ignoreCase = true) && !it.displayName.contains("argon", ignoreCase = true) }?.buckets?.firstOrNull { it.window.equals("weekly", ignoreCase = true) || it.bucketId.contains("weekly", ignoreCase = true) }?.resetTime
                                } else {
                                    userQuotaSummary?.groups?.firstOrNull { it.displayName.contains("gemini", ignoreCase = true) && !it.displayName.contains("argon", ignoreCase = true) }?.buckets?.firstOrNull()?.resetTime
                                }
                            )
                            val availStatus = matchingAvail?.availability ?: ModelAvailability.AVAILABLE
                            AvailableModelInfo(
                                modelId = cli.cliId,
                                displayName = cli.label.ifBlank { cli.displayName },
                                provider = cli.provider.ifBlank { AgyModelsService.providerForDisplayName(cli.displayName).ifBlank { "Google" } },
                                remainingFraction = pct?.let { it / 100.0 },
                                resetTime = resetT,
                                availability = availStatus
                            )
                        }
                } else {
                    availableModels
                        .filter { AgyModelsService.isRealAiModel(it.modelId, it.displayName) }
                }
            }

            if (effectiveModels.isNotEmpty()) {
                val modelsByProvider = effectiveModels
                    .distinctBy { it.displayName }
                    .groupBy { it.provider }

                if (modelsByProvider.isNotEmpty()) {
                    SettingsSectionTitle(title = "Models & Providers")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            modelsByProvider.entries.forEachIndexed { pIdx, (provider, modelsList) ->
                                if (pIdx > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }
                                val pColor = when (provider) {
                                    "Google" -> AntigravityBlue
                                    "OpenAI" -> AntigravityGreen
                                    else -> Color(0xFFD97706)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(pColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$provider Models",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                modelsList.forEach { m ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = m.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        if (m.availability == ModelAvailability.UNAVAILABLE) {
                                            Text(
                                                text = stringResource(R.string.model_status_unavailable_account),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        } else if (m.percentage != null) {
                                            Text(
                                                text = stringResource(R.string.status_remaining_pct, m.percentage ?: 0),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if ((m.percentage ?: 0) > 20) AntigravityGreen else MaterialTheme.colorScheme.error
                                            )
                                        } else if (m.resetTime != null) {
                                            val countdown = id.vern.antigravity.service.CloudCodeQuotaService.formatResetCountdown(m.resetTime)
                                            Text(
                                                text = "${stringResource(R.string.status_weekly_limit_reached)} · $countdown",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            Text(
                                                text = stringResource(R.string.status_model_available),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = AntigravityGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun CustomizationsSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showRulesBreakdown by remember { mutableStateOf(false) }
    var showOtherBreakdown by remember { mutableStateOf(false) }
    var showAddMcpDialog by remember { mutableStateOf(false) }
    var showRawMcpConfigDialog by remember { mutableStateOf(false) }
    var showSkillsDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showPluginsDialog by remember { mutableStateOf(false) }

    val installedSkills by viewModel.installedSkills.collectAsStateWithLifecycle()
    val installedPlugins by viewModel.installedPlugins.collectAsStateWithLifecycle()
    val installedRulesList by viewModel.installedRulesList.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val tokenStats by viewModel.tokenUsageStats.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshRules(context)
        viewModel.refreshSkills(context)
        viewModel.refreshPlugins(context)
        viewModel.refreshMcpServers(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        SubSheetHeader(
            title = "Customizations",
            subtitle = "Configure default behaviors, skills, and MCP servers. Learn more.",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {

            SettingsSectionTitle(title = "Token Usage")
            SettingsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Context token footprint of loaded customizations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val rulesPct = if (tokenStats.totalLimit > 0) (tokenStats.rulesTokens.toFloat() / tokenStats.totalLimit * 100f) else 0f
                    TokenUsageItem(
                        label = "Rules",
                        usedTokens = tokenStats.rulesTokens,
                        totalTokens = tokenStats.totalLimit,
                        percentage = rulesPct,
                        color = AntigravityBlue,
                        isExpanded = showRulesBreakdown,
                        breakdownLabel = "Show ${tokenStats.rulesBreakdowns.size} breakdowns",
                        onToggleBreakdown = { showRulesBreakdown = !showRulesBreakdown },
                        breakdowns = tokenStats.rulesBreakdowns
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val skillsPct = if (tokenStats.totalLimit > 0) (tokenStats.skillsTokens.toFloat() / tokenStats.totalLimit * 100f) else 0f
                    TokenUsageItem(
                        label = "Skills",
                        usedTokens = tokenStats.skillsTokens,
                        totalTokens = tokenStats.totalLimit,
                        percentage = skillsPct,
                        color = AntigravityGreen,
                        isExpanded = showOtherBreakdown,
                        breakdownLabel = "Show ${tokenStats.skillsBreakdowns.size} breakdowns",
                        onToggleBreakdown = { showOtherBreakdown = !showOtherBreakdown },
                        breakdowns = tokenStats.skillsBreakdowns
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard {
                Column {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSkillsDialog = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_customization_skills_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${installedSkills.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRulesDialog = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_customization_rules_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${installedRulesList.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPluginsDialog = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_customization_plugins_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${installedPlugins.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Installed MCP Servers",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mcpServers.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        BadgePill(text = "${mcpServers.size}")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            viewModel.refreshMcpServers(context)
                            Toast.makeText(context, "MCP servers refreshed (${mcpServers.size})", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.mcp_btn_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = { showRawMcpConfigDialog = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(stringResource(R.string.mcp_btn_config), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { showAddMcpDialog = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(stringResource(R.string.mcp_btn_add), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            SettingsCard {
                if (mcpServers.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No MCP servers installed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use Add MCP to configure a local tool or remote service, or edit mcp_config.json directly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        mcpServers.forEach { server ->
                            var showDeleteServerConfirm by remember { mutableStateOf(false) }

                            if (showDeleteServerConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteServerConfirm = false },
                                    shape = RoundedCornerShape(16.dp),
                                    title = {
                                        DialogBlurEffect()
                                        Text(stringResource(R.string.settings_mcp_delete_title), fontWeight = FontWeight.Bold)
                                    },
                                    text = { Text(stringResource(R.string.settings_mcp_delete_confirm, server.name)) },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                viewModel.deleteMcpServer(context, server.name) { ok ->
                                                    if (ok) Toast.makeText(context, context.getString(R.string.settings_mcp_deleted_toast, server.name), Toast.LENGTH_SHORT).show()
                                                }
                                                showDeleteServerConfirm = false
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                                        ) {
                                            Text(stringResource(R.string.settings_mcp_delete_btn))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteServerConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
                                    }
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (server.isRemote) Color(0xFF6366F1).copy(alpha = 0.2f) else AntigravityBlue.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (server.isRemote) "Remote HTTP" else "Stdio",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (server.isRemote) Color(0xFF818CF8) else AntigravityBlue,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AntigravityGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Active",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = AntigravityGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = server.displayTarget,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (server.source != "Global") {
                                        Text(
                                            text = "Source: ${server.source}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showDeleteServerConfirm = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.mcp_btn_delete_server),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showAddMcpDialog) {
        AddMcpServerDialog(
            onConfirm = { server ->
                viewModel.saveMcpServer(context, server) { ok ->
                    if (ok) {
                        Toast.makeText(context, "MCP server '${server.name}' configured!", Toast.LENGTH_SHORT).show()
                        showAddMcpDialog = false
                    } else {
                        Toast.makeText(context, "Failed to configure MCP server", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showAddMcpDialog = false }
        )
    }

    if (showRawMcpConfigDialog) {
        var rawJsonText by remember { mutableStateOf("") }
        var isRawLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            rawJsonText = viewModel.loadRawMcpConfig(context)
            isRawLoading = false
        }

        if (!isRawLoading) {
            RawMcpConfigDialog(
                initialJson = rawJsonText,
                onSave = { updatedJson ->
                    viewModel.saveRawMcpConfig(context, updatedJson) { ok ->
                        if (ok) {
                            Toast.makeText(context, "mcp_config.json saved!", Toast.LENGTH_SHORT).show()
                            showRawMcpConfigDialog = false
                        } else {
                            Toast.makeText(context, "Invalid JSON format", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { showRawMcpConfigDialog = false }
            )
        }
    }

    if (showSkillsDialog) {
        SkillsManagementDialog(
            viewModel = viewModel,
            onDismiss = { showSkillsDialog = false }
        )
    }

    if (showRulesDialog) {
        RulesManagementDialog(
            viewModel = viewModel,
            onDismiss = { showRulesDialog = false }
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
fun RulesManagementDialog(
    viewModel: AntigravityViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rulesList by viewModel.installedRulesList.collectAsStateWithLifecycle()
    var editingRule by remember { mutableStateOf<RuleItem?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var newRuleName by remember { mutableStateOf("") }
    var newRuleContent by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshRules(context)
    }

    if (editingRule != null) {
        RuleEditorDialog(
            rule = editingRule!!,
            onSave = { updatedContent ->
                viewModel.saveRuleContent(context, editingRule!!.filePath, updatedContent) { ok ->
                    if (ok) {
                        Toast.makeText(context, "Rule saved successfully", Toast.LENGTH_SHORT).show()
                        editingRule = null
                    } else {
                        Toast.makeText(context, "Failed to save rule", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = {
                viewModel.deleteRule(context, editingRule!!.filePath) { ok ->
                    if (ok) {
                        Toast.makeText(context, "Rule deleted", Toast.LENGTH_SHORT).show()
                        editingRule = null
                    }
                }
            },
            onDismiss = { editingRule = null }
        )
    }

    if (isCreatingNew) {
        AlertDialog(
            onDismissRequest = { isCreatingNew = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(stringResource(R.string.settings_rules_create_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_rules_create_filename_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newRuleName,
                        onValueChange = { newRuleName = it },
                        label = { Text(stringResource(R.string.settings_rules_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.settings_rules_content_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newRuleContent,
                        onValueChange = { newRuleContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRuleName.isNotBlank()) {
                            viewModel.createRule(context, newRuleName.trim(), newRuleContent) { ok ->
                                if (ok) {
                                    Toast.makeText(context, context.getString(R.string.settings_rules_created_toast), Toast.LENGTH_SHORT).show()
                                    isCreatingNew = false
                                    newRuleName = ""
                                    newRuleContent = ""
                                } else {
                                    Toast.makeText(context, context.getString(R.string.settings_rules_create_failed_toast), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                ) {
                    Text(stringResource(R.string.settings_rules_btn_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreatingNew = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_rules_mgmt_title, rulesList.size),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = { isCreatingNew = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(stringResource(R.string.settings_rules_btn_new), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (rulesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_rules_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    rulesList.forEach { rule ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                                .clickable { editingRule = rule }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AntigravityBlue.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = rule.source,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = AntigravityBlue,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingRule = rule },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rules_edit_rule), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                    if (rule.source != "Global") {
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteRule(context, rule.filePath) { ok ->
                                                    if (ok) Toast.makeText(context, context.getString(R.string.settings_rules_deleted_named_toast, rule.name), Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.rules_delete_rule), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${rule.lineCount} lines • ${rule.byteSize} bytes • ${rule.filePath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun RuleEditorDialog(
    rule: RuleItem,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var contentText by remember(rule.content) { mutableStateOf(rule.content) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(stringResource(R.string.settings_rules_delete_title), fontWeight = FontWeight.Bold)
            },
            text = { Text(stringResource(R.string.settings_rules_delete_confirm, rule.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                ) {
                    Text(stringResource(R.string.settings_rules_btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_rules_edit_title, rule.name),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (rule.source != "Global") {
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_rules_size_format, rule.filePath, contentText.toByteArray(Charsets.UTF_8).size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    BasicTextField(
                        value = contentText,
                        onValueChange = { contentText = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        cursorBrush = SolidColor(AntigravityBlue)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(contentText) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
            ) {
                Text(stringResource(R.string.settings_rules_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun AddMcpServerDialog(
    onConfirm: (McpServerItem) -> Unit,
    onDismiss: () -> Unit
) {
    var serverName by remember { mutableStateOf("") }
    var transportType by remember { mutableIntStateOf(0) }
    var command by remember { mutableStateOf("") }
    var argsText by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Text(stringResource(R.string.settings_mcp_add_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.settings_mcp_add_desc), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (transportType == 0) AntigravityBlue else Color.Transparent)
                                .clickable { transportType = 0 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.settings_mcp_tab_stdio), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (transportType == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (transportType == 1) AntigravityBlue else Color.Transparent)
                                .clickable { transportType = 1 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.settings_mcp_tab_http), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (transportType == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text(stringResource(R.string.settings_mcp_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (transportType == 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text(stringResource(R.string.settings_mcp_command_label)) },
                        placeholder = { Text(stringResource(R.string.settings_mcp_command_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = argsText,
                        onValueChange = { argsText = it },
                        label = { Text(stringResource(R.string.settings_mcp_args_label)) },
                        placeholder = { Text(stringResource(R.string.settings_mcp_args_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text(stringResource(R.string.settings_mcp_url_label)) },
                        placeholder = { Text(stringResource(R.string.settings_mcp_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (serverName.isNotBlank()) {
                        val isRemote = transportType == 1
                        val argsList = if (argsText.isNotBlank()) argsText.trim().split(Regex("\\s+")) else emptyList()
                        val item = McpServerItem(
                            name = serverName.trim(),
                            command = command.trim(),
                            args = argsList,
                            url = urlText.trim(),
                            isRemote = isRemote
                        )
                        onConfirm(item)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
            ) {
                Text(stringResource(R.string.settings_mcp_btn_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun RawMcpConfigDialog(
    initialJson: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var rawText by remember(initialJson) { mutableStateOf(initialJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Text(stringResource(R.string.settings_mcp_raw_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                ) {
                    BasicTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        cursorBrush = SolidColor(AntigravityBlue)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(rawText) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
            ) {
                Text(stringResource(R.string.settings_mcp_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun AppearanceSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val appearance by viewModel.appearanceConfig.collectAsStateWithLifecycle()
    val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
    val customBg by viewModel.customThemeBg.collectAsStateWithLifecycle()
    val customFg by viewModel.customThemeFg.collectAsStateWithLifecycle()
    val customAccent by viewModel.customThemeAccent.collectAsStateWithLifecycle()
    val verboseThinking by viewModel.verboseThinking.collectAsStateWithLifecycle()

    var presetDropdownExpanded by remember { mutableStateOf(false) }
    var colorPickerTarget by remember { mutableStateOf<String?>(null) }

    val isDark = when (appearance.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        SubSheetHeader(
            title = "Appearance",
            subtitle = "Customize theme mode, color presets, editor typography, and layout.",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {

            SettingsSectionTitle(title = "Theme Mode")
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(stringResource(R.string.settings_theme_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (appearance.themeMode == ThemeMode.SYSTEM) AntigravityBlue else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setThemeMode(context, ThemeMode.SYSTEM) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Computer,
                                    contentDescription = stringResource(R.string.theme_mode_system),
                                    tint = if (appearance.themeMode == ThemeMode.SYSTEM) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (appearance.themeMode == ThemeMode.LIGHT) AntigravityBlue else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setThemeMode(context, ThemeMode.LIGHT) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LightMode,
                                    contentDescription = stringResource(R.string.theme_mode_light),
                                    tint = if (appearance.themeMode == ThemeMode.LIGHT) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (appearance.themeMode == ThemeMode.DARK) AntigravityBlue else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setThemeMode(context, ThemeMode.DARK) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = stringResource(R.string.theme_mode_dark),
                                    tint = if (appearance.themeMode == ThemeMode.DARK) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val selectedPreset = ThemePreset.entries.find { it.displayName == themePreset }
                ?: if (isDark) ThemePreset.DEFAULT_DARK else ThemePreset.DEFAULT_LIGHT

            val displayBg = if (selectedPreset == ThemePreset.CUSTOM) customBg else selectedPreset.bgHex
            val displayFg = if (selectedPreset == ThemePreset.CUSTOM) customFg else selectedPreset.fgHex
            val displayAccent = if (selectedPreset == ThemePreset.CUSTOM) customAccent else selectedPreset.accentHex

            SettingsSectionTitle(
                title = stringResource(
                    if (isDark) R.string.settings_dark_theme_customization
                    else R.string.settings_light_theme_customization
                )
            )
            SettingsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(stringResource(R.string.settings_theme_preset_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.settings_theme_preset_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            OutlinedButton(
                                onClick = { presetDropdownExpanded = true },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                val buttonLabel = if (selectedPreset == ThemePreset.CUSTOM) stringResource(R.string.theme_preset_custom) else selectedPreset.displayName
                                Text(buttonLabel, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = presetDropdownExpanded,
                                onDismissRequest = { presetDropdownExpanded = false },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                ThemePreset.entries.forEach { p ->
                                    val itemName = if (p == ThemePreset.CUSTOM) stringResource(R.string.theme_preset_custom) else p.displayName
                                    DropdownMenuItem(
                                        text = { Text(itemName, fontSize = 13.sp) },
                                        onClick = {
                                            viewModel.setThemePreset(context, p.displayName)
                                            presetDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.theme_color_background), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { colorPickerTarget = "bg" }
                                    .padding(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(parseColorOrNull(displayBg) ?: Color(0xFF101010))
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(displayBg, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.theme_color_foreground), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { colorPickerTarget = "fg" }
                                    .padding(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(parseColorOrNull(displayFg) ?: Color(0xFFCCCCCC))
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(displayFg, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.theme_color_accent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            val accentColorParsed = parseColorOrNull(displayAccent) ?: AntigravityBlue
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accentColorParsed.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, accentColorParsed),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { colorPickerTarget = "accent" }
                                    .padding(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(accentColorParsed)
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(displayAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = accentColorParsed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsSectionTitle(title = stringResource(R.string.settings_chat_viewport_title))
            SettingsCard {
                Column(modifier = Modifier.padding(14.dp)) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_verbose_chat_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.settings_verbose_chat_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = verboseThinking,
                            onCheckedChange = { viewModel.setVerboseThinking(context, it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AntigravityBlue)
                        )
                    }

                }
            }

            if (colorPickerTarget != null) {
                val target = colorPickerTarget!!
                val dialogTitle = when (target) {
                    "bg" -> stringResource(R.string.color_picker_title_bg)
                    "fg" -> stringResource(R.string.color_picker_title_fg)
                    else -> stringResource(R.string.color_picker_title_accent)
                }
                val initialHex = when (target) {
                    "bg" -> displayBg
                    "fg" -> displayFg
                    else -> displayAccent
                }
                ColorPaletteDialog(
                    title = dialogTitle,
                    initialColorHex = initialHex,
                    onDismiss = { colorPickerTarget = null },
                    onColorSelected = { chosenHex ->
                        viewModel.updateCustomThemeColor(
                            context = context,
                            target = target,
                            newHex = chosenHex,
                            currentPreset = selectedPreset
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun ColorPaletteDialog(
    title: String,
    initialColorHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    var hexInput by remember { mutableStateOf(initialColorHex) }
    var selectedColorHex by remember { mutableStateOf(initialColorHex) }

    val paletteColors = remember {
        listOf(
            "#000000", "#090D14", "#101010", "#121212", "#18181B", "#1E293B", "#0F172A", "#2D2A2E", "#002B36", "#0B001A",
            "#FFFFFF", "#F8FAFC", "#F1F5F9", "#E2E8F0", "#CBD5E1", "#CCCCCC", "#94A3B8", "#64748B", "#FCFCFA", "#EEE8D5",
            "#1A73E8", "#007ACC", "#38BDF8", "#06B6D4", "#00FFFF", "#00FFCC", "#10B981", "#22C55E", "#84CC16", "#EAB308",
            "#FFD866", "#F59E0B", "#F97316", "#EF4444", "#FF0055", "#EC4899", "#D946EF", "#8B5CF6", "#6366F1", "#3B82F6"
        )
    }

    val currentPreviewColor = remember(hexInput, selectedColorHex) {
        parseColorOrNull(hexInput) ?: parseColorOrNull(selectedColorHex) ?: Color(0xFF101010)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Text(title, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(currentPreviewColor)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { newVal ->
                            val clean = newVal.trim()
                            hexInput = clean
                            val parsed = parseColorOrNull(clean)
                            if (parsed != null) {
                                selectedColorHex = clean
                            }
                        },
                        label = { Text(stringResource(R.string.color_picker_custom_hex)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.color_picker_palette),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    paletteColors.chunked(5).forEach { rowColors ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowColors.forEach { hex ->
                                val swatchColor = parseColorOrNull(hex) ?: Color.Gray
                                val isSelected = hex.equals(selectedColorHex, ignoreCase = true) || hex.equals(hexInput, ignoreCase = true)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(swatchColor)
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedColorHex = hex
                                            hexInput = hex
                                        }
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (isColorDark(swatchColor)) Color.White else Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalHex = if (parseColorOrNull(hexInput) != null) {
                        if (hexInput.startsWith("#")) hexInput.uppercase() else "#${hexInput.uppercase()}"
                    } else {
                        selectedColorHex
                    }
                    onColorSelected(finalHex)
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.color_picker_apply))
            }
        }
    )
}

@Composable
fun AccountSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit,
    onNavigateTo: (SettingsPage) -> Unit = {}
) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userPicture by viewModel.userPicture.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val codeAssistTier by viewModel.codeAssistTier.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val token: String? = remember(userEmail) { OAuthManager.getAccessToken(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        SubSheetHeader(
            title = stringResource(R.string.account_title),
            subtitle = stringResource(R.string.account_subtitle),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (userEmail != null) AntigravityBlue else MaterialTheme.colorScheme.surfaceVariant),
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
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (userEmail != null) userName.ifBlank { "Google User" } else stringResource(R.string.account_not_signed_in),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (userEmail != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val tierType = codeAssistTier?.tierType ?: SubscriptionTierType.FREE
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when (tierType) {
                                            SubscriptionTierType.ULTRA -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                            SubscriptionTierType.PRO -> AntigravityBlue.copy(alpha = 0.15f)
                                            SubscriptionTierType.ENTERPRISE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = when (tierType) {
                                                SubscriptionTierType.ULTRA -> stringResource(R.string.account_tier_ultra)
                                                SubscriptionTierType.PRO -> stringResource(R.string.account_tier_pro)
                                                SubscriptionTierType.ENTERPRISE -> stringResource(R.string.account_tier_enterprise)
                                                else -> stringResource(R.string.account_tier_free)
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = when (tierType) {
                                                SubscriptionTierType.ULTRA -> MaterialTheme.colorScheme.tertiary
                                                SubscriptionTierType.PRO -> AntigravityBlue
                                                SubscriptionTierType.ENTERPRISE -> MaterialTheme.colorScheme.secondary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (!userEmail.isNullOrBlank()) {
                                Text(
                                    text = userEmail ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (userEmail != null) stringResource(R.string.status_authenticated) else stringResource(R.string.account_not_authenticated),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (userEmail != null) AntigravityGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(R.string.account_details_header),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.account_token_path),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        if (userEmail != null) stringResource(R.string.account_status_active) else stringResource(R.string.account_status_disconnected),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (userEmail != null) AntigravityGreen else MaterialTheme.colorScheme.error
                    )

                    if (!token.isNullOrBlank()) {
                        val displayToken = token
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.account_token_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                            ) {
                                Text(
                                    text = displayToken,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("access_token", displayToken))
                                        Toast.makeText(context, context.getString(R.string.account_token_copied), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.chat_copy_code),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (userEmail != null) {
                        Button(
                            onClick = { onNavigateTo(SettingsPage.MODELS_AND_USAGE) },
                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_model_gemini),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.account_btn_models_usage),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.refreshAuthState(context)
                                    Toast.makeText(context, context.getString(R.string.account_toast_token_refreshed), Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.account_btn_refresh_token))
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.signOutGoogle(context)
                                    Toast.makeText(context, context.getString(R.string.account_toast_signed_out), Toast.LENGTH_SHORT).show()
                                    onBack()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.account_btn_sign_out))
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.startGoogleSignIn(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.account_btn_sign_in))
                        }
                    }
                }
            }

            if (userEmail != null || accounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.account_switcher_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.account_switcher_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                val otherAccounts = remember(accounts, userEmail) {
                    accounts.filter { !it.email.equals(userEmail, ignoreCase = true) }
                }

                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .animateContentSize()
                    ) {
                        if (otherAccounts.isNotEmpty()) {
                            otherAccounts.forEachIndexed { idx, acc ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.switchAccount(context, acc.id)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.account_switched_toast, acc.email),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!acc.picture.isNullOrBlank()) {
                                            AsyncImage(
                                                model = acc.picture,
                                                contentDescription = acc.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = acc.name.take(1).uppercase().ifBlank { "U" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = acc.name.ifBlank { acc.email },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = when (acc.tierType) {
                                                    SubscriptionTierType.ULTRA -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                                    SubscriptionTierType.PRO -> AntigravityBlue.copy(alpha = 0.15f)
                                                    SubscriptionTierType.ENTERPRISE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ) {
                                                Text(
                                                    text = when (acc.tierType) {
                                                        SubscriptionTierType.ULTRA -> stringResource(R.string.account_tier_ultra)
                                                        SubscriptionTierType.PRO -> stringResource(R.string.account_tier_pro)
                                                        SubscriptionTierType.ENTERPRISE -> stringResource(R.string.account_tier_enterprise)
                                                        else -> stringResource(R.string.account_tier_free)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (acc.tierType) {
                                                        SubscriptionTierType.ULTRA -> MaterialTheme.colorScheme.tertiary
                                                        SubscriptionTierType.PRO -> AntigravityBlue
                                                        SubscriptionTierType.ENTERPRISE -> MaterialTheme.colorScheme.secondary
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = acc.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.removeAccount(context, acc.id)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.account_remove_button),
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (idx < otherAccounts.size - 1) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { viewModel.startGoogleSignIn(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.account_add_button),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun DeveloperSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val engineStatus by viewModel.engineStatus.collectAsStateWithLifecycle()
    val logs by viewModel.executionLogs.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var coreVersion by remember { mutableStateOf("") }
    var coreState by remember { mutableStateOf(CoreEngineUiState()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        SubSheetHeader(
            title = "Developer & System",
            subtitle = "Runtime diagnostics, service status, and userspace management.",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {

            SettingsSectionTitle(title = stringResource(R.string.dev_userspace_engine_title))
            SettingsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.dev_userspace_prefix_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${context.filesDir}/usr",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.dev_core_service_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.dev_core_service_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (engineStatus.isRunning) AntigravityGreen else AntigravityRed)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (engineStatus.isRunning) {
                                    viewModel.stopCoreService(context)
                                } else {
                                    viewModel.startCoreService(context)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (engineStatus.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (engineStatus.isRunning) R.string.dev_btn_stop_core_service
                                    else R.string.dev_btn_start_core_service
                                ),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.restartCoreService(context)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.dev_btn_restart_core_service),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.bootEngine(context) {}
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.dev_btn_reinstall_bootstrap))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.core_update_title),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (coreVersion.isNullOrBlank())
                                    stringResource(R.string.core_update_checking)
                                else
                                    stringResource(R.string.core_update_current_version, coreVersion),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (coreState.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AntigravityBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.checkForCoreEngineUpdate(context, force = true)
                            },
                            enabled = !coreState.isChecking,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.core_update_btn_check),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        AnimatedVisibility(
                            visible = coreState.isUpdateAvailable,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.applyCoreEngineUpdate(context)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.core_update_btn_update),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsSectionTitle(title = stringResource(R.string.dev_terminal_logs_title))
            LogViewer(
                logs = logs,
                onCopyLogs = { viewModel.copyLogsToClipboard(context) },
                onExportLogs = { viewModel.exportLogsToFile(context) }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSectionTitleWithInfo(
    title: String,
    info: String = "",
    subtitle: String? = null
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (info.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.common_info),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable {
                            Toast.makeText(context, info, Toast.LENGTH_SHORT).show()
                        }
                )
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
fun BadgePill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun CircularQuotaIndicator(
    percentage: Int,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(44.dp)
    ) {
        val sweepAngle = (percentage / 100f) * 360f
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {

            drawArc(
                color = Color(0xFF27272A),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            drawArc(
                color = AntigravityGreen,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TokenUsageItem(
    label: String,
    usedTokens: Int,
    totalTokens: Int,
    percentage: Float,
    color: Color,
    isExpanded: Boolean,
    breakdownLabel: String,
    onToggleBreakdown: () -> Unit,
    breakdowns: List<Pair<String, Int>>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "%,d / %,d [%.1f%%]".format(usedTokens, totalTokens, percentage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isExpanded) "Hide breakdowns" else breakdownLabel,
            style = MaterialTheme.typography.labelSmall,
            color = AntigravityBlue,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onToggleBreakdown() }
                .padding(vertical = 2.dp)
        )

        if (isExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    breakdowns.forEach { (name, tokens) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Text(name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("%,d tokens".format(tokens), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

enum class CustomizationKind(
    val titleRes: Int,
    val emptyRes: Int,
    val addTitleRes: Int,
    val addDescRes: Int,
    val nameLabelRes: Int,
    val descLabelRes: Int,
    val isSkill: Boolean
) {
    SKILLS(
        titleRes = R.string.customization_skills_title,
        emptyRes = R.string.customization_skills_empty,
        addTitleRes = R.string.customization_add_skill_title,
        addDescRes = R.string.customization_add_skill_desc,
        nameLabelRes = R.string.customization_skill_name_label,
        descLabelRes = R.string.customization_desc_label,
        isSkill = true
    ),
    PLUGINS(
        titleRes = R.string.customization_plugins_title,
        emptyRes = R.string.customization_plugins_empty,
        addTitleRes = R.string.customization_add_plugin_title,
        addDescRes = R.string.customization_add_plugin_desc,
        nameLabelRes = R.string.customization_plugin_name_label,
        descLabelRes = R.string.customization_plugin_desc_label,
        isSkill = false
    )
}

@Composable
fun CustomizationManagementDialog(
    kind: CustomizationKind,
    viewModel: AntigravityViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val installedSkills by viewModel.installedSkills.collectAsStateWithLifecycle()
    val installedPlugins by viewModel.installedPlugins.collectAsStateWithLifecycle()
    val customizationTracks by viewModel.customizationTracks.collectAsStateWithLifecycle()
    val isCheckingUpdates by viewModel.isCheckingCustomizationUpdates.collectAsStateWithLifecycle()
    val itemsList = if (kind.isSkill) installedSkills else installedPlugins
    val skillsCatalogue by viewModel.skillsCatalogue.collectAsStateWithLifecycle()
    val pluginsCatalogue by viewModel.pluginsCatalogue.collectAsStateWithLifecycle()
    val catalogueItems = if (kind.isSkill) skillsCatalogue else pluginsCatalogue
    val installingItemId by viewModel.installingItemId.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogRepoUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadCachedCatalogue(context)
        viewModel.fetchRemoteCatalogue(context, force = false)
        viewModel.checkForCustomizationUpdates(context, force = false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(kind.titleRes),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = { showAddDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(stringResource(R.string.customization_btn_custom), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selectedTab == 0) AntigravityBlue else Color.Transparent)
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${stringResource(R.string.customization_tab_installed)} (${itemsList.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selectedTab == 1) AntigravityBlue else Color.Transparent)
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${stringResource(R.string.customization_tab_marketplace)} (${catalogueItems.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (selectedTab == 0) {
                    val pendingUpdatesCount = itemsList.count { item ->
                        val track = customizationTracks[item.name.lowercase()] ?: customizationTracks[item.id.lowercase()]
                        track?.hasUpdate == true
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp, start = 2.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCheckingUpdates) stringResource(R.string.customization_checking_updates)
                            else if (pendingUpdatesCount > 0) stringResource(R.string.customization_updates_found, pendingUpdatesCount)
                            else stringResource(R.string.customization_up_to_date),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { viewModel.checkForCustomizationUpdates(context, force = true) },
                            enabled = !isCheckingUpdates,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(stringResource(R.string.customization_btn_check_updates), fontSize = 11.sp)
                        }
                    }

                    if (itemsList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(kind.emptyRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        itemsList.forEach { item ->
                            val track = customizationTracks[item.name.lowercase()] ?: customizationTracks[item.id.lowercase()]
                            val hasUpdate = track?.hasUpdate == true

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val isFromPlugin = item.path?.contains("/plugins/") == true
                                        val badgeText = when {
                                            item.isBuiltIn -> stringResource(R.string.customization_badge_builtin)
                                            isFromPlugin -> stringResource(R.string.customization_badge_plugin)
                                            else -> stringResource(R.string.customization_badge_custom)
                                        }
                                        val badgeColor = when {
                                            item.isBuiltIn -> MaterialTheme.colorScheme.surfaceVariant
                                            isFromPlugin -> AntigravityGreen.copy(alpha = 0.2f)
                                            else -> AntigravityBlue.copy(alpha = 0.2f)
                                        }
                                        val badgeTextColor = when {
                                            item.isBuiltIn -> MaterialTheme.colorScheme.onSurfaceVariant
                                            isFromPlugin -> AntigravityGreen
                                            else -> AntigravityBlue
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = badgeColor
                                        ) {
                                            Text(
                                                text = badgeText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = badgeTextColor,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (hasUpdate) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AntigravityAmber.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.customization_badge_update_available),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AntigravityAmber,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (track != null && track.hasUpdate) {
                                            Button(
                                                onClick = {
                                                    viewModel.updateCustomizationViaTerminal(context, track)
                                                    onDismiss()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AntigravityAmber),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.customization_btn_update), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        if (!item.isBuiltIn) {
                                            IconButton(
                                                onClick = {
                                                    if (kind.isSkill) {
                                                        viewModel.uninstallSkill(context, item.name)
                                                    } else {
                                                        viewModel.uninstallPlugin(context, item.name)
                                                    }
                                                    val typeName = if (kind.isSkill) "Skill" else "Plugin"
                                                    Toast.makeText(context, context.getString(R.string.customization_removed_toast, typeName, item.name), Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.customization_uninstall_desc, item.name),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    catalogueItems.forEach { item ->
                        val isInstalled = itemsList.any { it.name.equals(item.name, ignoreCase = true) || it.id.equals(item.id, ignoreCase = true) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                        ) {

                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = item.category,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (kind.isSkill && item.repoUrl.isNotBlank() && (item.subPath.isNullOrBlank() || item.id == "antigravity-skills-manager")) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AntigravityBlue.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.customization_vault_badge),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AntigravityBlue,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isInstalled) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AntigravityGreen.copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = AntigravityGreen,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.customization_btn_installed),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AntigravityGreen
                                            )
                                        }
                                    }
                                } else if (installingItemId == item.id) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue.copy(alpha = 0.6f)),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.customization_btn_installing), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    val isMultiSkillVault = kind.isSkill && item.repoUrl.isNotBlank() && (item.subPath.isNullOrBlank() || item.id == "antigravity-skills-manager")
                                    if (isMultiSkillVault) {
                                        OutlinedButton(
                                            onClick = {
                                                addDialogRepoUrl = item.repoUrl
                                                showAddDialog = true
                                                viewModel.inspectRepoForSkills(context, item.repoUrl)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.customization_btn_browse_skills), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = {
                                                viewModel.installSkillViaTerminal(context, item)
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.customization_btn_install_all), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                if (kind.isSkill) {
                                                    viewModel.installSkillViaTerminal(context, item)
                                                } else {
                                                    viewModel.installPluginViaTerminal(context, item)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.customization_btn_install), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )

    if (showAddDialog) {
        var addTab by remember { mutableIntStateOf(0) }
        var repoUrl by remember { mutableStateOf(addDialogRepoUrl) }
        var repoSubPath by remember { mutableStateOf("") }
        var itemName by remember { mutableStateOf("") }
        var itemDesc by remember { mutableStateOf("") }
        var itemPrompt by remember { mutableStateOf("") }

        val isInspectingRepo by viewModel.isInspectingRepo.collectAsStateWithLifecycle()
        val discoveredSkills by viewModel.discoveredRepoSkills.collectAsStateWithLifecycle()
        val inspectionError by viewModel.repoInspectionError.collectAsStateWithLifecycle()

        AlertDialog(
            onDismissRequest = {
                viewModel.clearRepoInspection()
                addDialogRepoUrl = ""
                showAddDialog = false
            },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Column {
                    Text(stringResource(kind.addTitleRes), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (addTab == 0) AntigravityBlue else Color.Transparent)
                                    .clickable { addTab = 0 }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.customization_tab_git_repo),
                                    fontSize = 11.sp,
                                    fontWeight = if (addTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (addTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (addTab == 1) AntigravityBlue else Color.Transparent)
                                    .clickable { addTab = 1 }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.customization_tab_manual),
                                    fontSize = 11.sp,
                                    fontWeight = if (addTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (addTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (addTab == 0) {
                        Text(
                            stringResource(kind.addDescRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = repoUrl,
                                onValueChange = { input ->
                                    val (cleanRepoUrl, normSub) = viewModel.normalizeGitRepoUrl(input)
                                    if (normSub != null && repoSubPath.isBlank()) {
                                        repoSubPath = normSub
                                    }
                                    repoUrl = input
                                    if (discoveredSkills.isNotEmpty()) viewModel.clearRepoInspection()
                                },
                                label = { Text(stringResource(R.string.customization_repo_url_label)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (kind.isSkill) {
                                Button(
                                    onClick = {
                                        if (repoUrl.isNotBlank()) {
                                            viewModel.inspectRepoForSkills(context, repoUrl)
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.customization_repo_empty_url_error), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = repoUrl.isNotBlank() && !isInspectingRepo,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    if (isInspectingRepo) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.customization_btn_inspect_repo), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (isInspectingRepo) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AntigravityBlue)
                                Text(
                                    stringResource(R.string.customization_inspecting_repo),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AntigravityBlue
                                )
                            }
                        }

                        if (!inspectionError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.customization_repo_scan_error, inspectionError ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (discoveredSkills.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.customization_repo_skills_found, discoveredSkills.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val allSelected = discoveredSkills.all { it.isSelected }
                                TextButton(
                                    onClick = { viewModel.setAllRepoSkillsSelection(!allSelected) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (allSelected) stringResource(R.string.customization_btn_deselect_all)
                                        else stringResource(R.string.customization_btn_select_all),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                discoveredSkills.forEach { skill ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleRepoSkillSelection(skill.id) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = skill.isSelected,
                                                onCheckedChange = { viewModel.toggleRepoSkillSelection(skill.id) },
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = skill.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (skill.description.isNotBlank()) {
                                                    Text(
                                                        text = skill.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (skill.subPath.isNotBlank()) {
                                                    Text(
                                                        text = skill.subPath,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = AntigravityBlue
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (!kind.isSkill || discoveredSkills.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = { itemName = it },
                                label = { Text(stringResource(kind.nameLabelRes)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = repoSubPath,
                                onValueChange = { repoSubPath = it },
                                label = { Text(stringResource(R.string.customization_repo_subpath_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            stringResource(kind.addDescRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = { Text(stringResource(kind.nameLabelRes)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = itemDesc,
                            onValueChange = { itemDesc = it },
                            label = { Text(stringResource(kind.descLabelRes)) },
                            singleLine = !kind.isSkill,
                            minLines = if (kind.isSkill) 1 else 3,
                            maxLines = if (kind.isSkill) 1 else 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (kind.isSkill) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = itemPrompt,
                                onValueChange = { itemPrompt = it },
                                label = { Text(stringResource(R.string.customization_skill_prompt_label)) },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val selectedCount = discoveredSkills.count { it.isSelected }
                Button(
                    onClick = {
                        if (addTab == 0) {
                            if (repoUrl.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.customization_repo_empty_url_error), Toast.LENGTH_SHORT).show()
                            } else if (kind.isSkill && discoveredSkills.isNotEmpty()) {
                                val toInstall = discoveredSkills.filter { it.isSelected }
                                if (toInstall.isNotEmpty()) {
                                    viewModel.installSelectedRepoSkillsViaTerminal(context, repoUrl, toInstall)
                                    viewModel.clearRepoInspection()
                                    showAddDialog = false
                                }
                            } else if (kind.isSkill && repoSubPath.isBlank() && discoveredSkills.isEmpty()) {
                                viewModel.inspectRepoForSkills(context, repoUrl)
                            } else {
                                viewModel.installCustomFromRepoViaTerminal(
                                    context = context,
                                    rawRepoUrl = repoUrl,
                                    customName = itemName,
                                    subPath = repoSubPath.ifBlank { null },
                                    isPlugin = !kind.isSkill
                                )
                                viewModel.clearRepoInspection()
                                showAddDialog = false
                            }
                        } else {
                            if (itemName.isNotBlank()) {
                                val success = if (kind.isSkill) {
                                    viewModel.installSkill(context, itemName, itemDesc, itemPrompt)
                                } else {
                                    viewModel.installPlugin(context, itemName, itemDesc)
                                }
                                val typeName = if (kind.isSkill) "Skill" else "Plugin"
                                if (success) {
                                    Toast.makeText(context, context.getString(R.string.customization_installed_toast, typeName, itemName), Toast.LENGTH_SHORT).show()
                                    showAddDialog = false
                                } else {
                                    Toast.makeText(context, context.getString(R.string.customization_install_failed_toast, typeName), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    enabled = if (addTab == 0 && kind.isSkill && discoveredSkills.isNotEmpty()) selectedCount > 0 else true
                ) {
                    Text(
                        if (addTab == 0 && kind.isSkill && discoveredSkills.isNotEmpty()) {
                            stringResource(R.string.customization_btn_install_selected, selectedCount)
                        } else if (addTab == 0) {
                            stringResource(R.string.customization_btn_install_repo)
                        } else {
                            stringResource(R.string.customization_btn_install)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearRepoInspection()
                        addDialogRepoUrl = ""
                        showAddDialog = false
                    }
                ) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
fun SkillsManagementDialog(
    viewModel: AntigravityViewModel,
    onDismiss: () -> Unit
) {
    CustomizationManagementDialog(
        kind = CustomizationKind.SKILLS,
        viewModel = viewModel,
        onDismiss = onDismiss
    )
}

@Composable
fun PluginsManagementDialog(
    viewModel: AntigravityViewModel,
    onDismiss: () -> Unit
) {
    CustomizationManagementDialog(
        kind = CustomizationKind.PLUGINS,
        viewModel = viewModel,
        onDismiss = onDismiss
    )
}

@Composable
fun AboutSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        SubSheetHeader(
            title = stringResource(R.string.settings_about_title),
            subtitle = stringResource(R.string.settings_about_subtitle),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_logo),
                            contentDescription = stringResource(R.string.about_app_name),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val appVersion = remember {
                                try {
                                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                    packageInfo.versionName ?: id.vern.antigravity.BuildConfig.VERSION_NAME
                                } catch (e: Exception) {
                                    id.vern.antigravity.BuildConfig.VERSION_NAME
                                }
                            }
                            Text(
                                text = stringResource(R.string.about_app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.about_version, appVersion),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = stringResource(R.string.about_creator),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = AntigravityBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.about_repo_header),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.about_repo_name),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AntigravityBlue
                    )
                    Text(
                        text = stringResource(R.string.about_repo_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KuatoDev/Antigravity"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_logo),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.about_btn_repo_antigravity),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KuatoDev/antigravity-bootstrap"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.about_btn_repo_bootstrap),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KuatoDev/antigravity-catalogue"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.about_btn_repo_catalogue),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/vernkuato"))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_telegram),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.about_btn_telegram_channel))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/kuatodevprojects"))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_telegram),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.about_btn_telegram_community))
                    }
                }
            }

            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.about_contributors_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val contributors = listOf(
                        Pair(R.string.about_contrib_vern_title, R.string.about_contrib_vern_desc),
                        Pair(R.string.about_contrib_werdik_title, R.string.about_contrib_werdik_desc),
                        Pair(R.string.about_contrib_termux_title, R.string.about_contrib_termux_desc),
                        Pair(R.string.about_contrib_deepmind_title, R.string.about_contrib_deepmind_desc)
                    )

                    contributors.forEachIndexed { index, (titleRes, descRes) ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(titleRes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.about_modules_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val modules = listOf(
                        Pair(R.string.about_mod_core_title, R.string.about_mod_core_desc),
                        Pair(R.string.about_mod_env_title, R.string.about_mod_env_desc),
                        Pair(R.string.about_mod_io_title, R.string.about_mod_io_desc),
                        Pair(R.string.about_mod_patches_title, R.string.about_mod_patches_desc),
                        Pair(R.string.about_mod_runner_title, R.string.about_mod_runner_desc)
                    )

                    modules.forEachIndexed { index, (titleRes, descRes) ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(titleRes),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.SemiBold,
                                color = AntigravityBlue
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.about_libraries_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val libraries = listOf(
                        Pair(R.string.about_lib_compose_title, R.string.about_lib_compose_desc),
                        Pair(R.string.about_lib_coroutines_title, R.string.about_lib_coroutines_desc),
                        Pair(R.string.about_lib_bionic_title, R.string.about_lib_bionic_desc),
                        Pair(R.string.about_lib_coil_title, R.string.about_lib_coil_desc),
                        Pair(R.string.about_lib_datastore_title, R.string.about_lib_datastore_desc)
                    )

                    libraries.forEachIndexed { index, (titleRes, descRes) ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(titleRes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
