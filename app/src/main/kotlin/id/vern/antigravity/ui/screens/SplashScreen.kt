package id.vern.antigravity.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import id.vern.antigravity.R
import id.vern.antigravity.ui.theme.AccentYellowDark
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed
import id.vern.antigravity.util.PermissionHelper
import id.vern.antigravity.viewmodel.AntigravityViewModel

@Composable
fun SplashScreen(
    viewModel: AntigravityViewModel,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val bootStatusText by viewModel.bootStatusText.collectAsStateWithLifecycle()
    val bootError by viewModel.bootError.collectAsStateWithLifecycle()
    val bootErrorDetail by viewModel.bootErrorDetail.collectAsStateWithLifecycle()
    val setupProgress by viewModel.setupProgress.collectAsStateWithLifecycle()

    var hasStoragePermission by remember {
        mutableStateOf(PermissionHelper.hasStoragePermission(context))
    }
    var hasTriggeredBoot by remember { mutableStateOf(false) }

    var pendingStorageLaunch by remember { mutableStateOf(false) }

    fun triggerEngineBoot() {
        if (!hasTriggeredBoot && PermissionHelper.hasStoragePermission(context)) {
            hasTriggeredBoot = true
            hasStoragePermission = true
            viewModel.bootEngine(context) { onNavigateToChat() }
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = PermissionHelper.hasStoragePermission(context)
        hasStoragePermission = granted
        if (granted) {
            triggerEngineBoot()
        }
    }

    fun proceedToStoragePermission() {
        if (!PermissionHelper.hasStoragePermission(context)) {
            try {
                manageStorageLauncher.launch(PermissionHelper.createManageStorageIntent(context))
            } catch (e: Exception) {
                try {
                    manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e: Exception) {
                    val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    manageStorageLauncher.launch(appSettings)
                }
            }
        } else {
            hasStoragePermission = true
            triggerEngineBoot()
        }
    }

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { unused ->
        if (PermissionHelper.hasStoragePermission(context)) {
            hasStoragePermission = true
            triggerEngineBoot()
        } else {

            hasStoragePermission = false
            pendingStorageLaunch = true
        }
    }

    LaunchedEffect(pendingStorageLaunch) {
        if (pendingStorageLaunch) {
            pendingStorageLaunch = false
            kotlinx.coroutines.delay(350)
            proceedToStoragePermission()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { lifecycleOwner, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = PermissionHelper.hasStoragePermission(context)
                hasStoragePermission = granted
                if (granted) {
                    triggerEngineBoot()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val runtimePerms = PermissionHelper.getRequiredRuntimePermissions()
        val needsRuntime = runtimePerms.isNotEmpty() && !PermissionHelper.hasAllRuntimePermissions(context)

        if (needsRuntime) {
            runtimePermissionsLauncher.launch(runtimePerms)
        } else if (!PermissionHelper.hasStoragePermission(context)) {
            hasStoragePermission = false
            proceedToStoragePermission()
        } else {
            hasStoragePermission = true
            triggerEngineBoot()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "SplashDotAnim")
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, delayMillis = 0, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot1"
    )
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot2"
    )
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot3"
    )
    val dot4Scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot4"
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_logo),
                    contentDescription = stringResource(id = R.string.app_name),
                    modifier = Modifier.size(96.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.splash_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))
                if (bootError != null) {
                    Text(
                        text = bootError ?: stringResource(R.string.splash_error_bootstrap_extract),
                        color = AntigravityRed,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!bootErrorDetail.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF15181E))
                                .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = bootErrorDetail ?: "",
                                color = Color(0xFFFF8585),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val logText = bootErrorDetail ?: bootError ?: ""
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(context.getString(R.string.splash_error_log_title), logText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.splash_logs_copied_toast), Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.splash_btn_copy_logs))
                        }

                        Button(
                            onClick = {
                                if (!PermissionHelper.hasStoragePermission(context)) {
                                    hasStoragePermission = false
                                    proceedToStoragePermission()
                                } else {
                                    hasStoragePermission = true
                                    triggerEngineBoot()
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_retry))
                        }
                    }
                } else if (!hasStoragePermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(AntigravityBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = AntigravityBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = stringResource(R.string.splash_permission_storage_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.splash_permission_storage_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    proceedToStoragePermission()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                            ) {
                                Text(
                                    text = stringResource(R.string.splash_permission_grant_btn),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(dot1Scale)
                                .clip(CircleShape)
                                .background(AntigravityBlue)
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(dot2Scale)
                                .clip(CircleShape)
                                .background(AntigravityRed)
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(dot3Scale)
                                .clip(CircleShape)
                                .background(AccentYellowDark)
                        )
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .scale(dot4Scale)
                                .clip(CircleShape)
                                .background(AntigravityGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (setupProgress > 0f && setupProgress < 1.0f) {
                        LinearProgressIndicator(
                            progress = { setupProgress },
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AntigravityBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = bootStatusText.ifBlank { stringResource(R.string.splash_status_initializing) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

