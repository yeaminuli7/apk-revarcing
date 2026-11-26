package id.vern.antigravity.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import id.vern.antigravity.api.GeminiClient
import id.vern.antigravity.ui.components.DialogBlurState
import id.vern.antigravity.ui.screens.ApiKeyScreen
import id.vern.antigravity.ui.screens.SplashScreen
import id.vern.antigravity.ui.screens.WorkspaceScreen
import id.vern.antigravity.ui.theme.AntigravityTheme
import id.vern.antigravity.viewmodel.AntigravityViewModel

enum class AppDestination {
    API_KEY,
    SPLASH,
    WORKSPACE
}

class MainActivity : ComponentActivity() {

    private val viewModel: AntigravityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadPreferences(this)
        enableEdgeToEdge()

        setContent {
            val appearance by viewModel.appearanceConfig.collectAsStateWithLifecycle()
            val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
            val customBg by viewModel.customThemeBg.collectAsStateWithLifecycle()
            val customFg by viewModel.customThemeFg.collectAsStateWithLifecycle()
            val customAccent by viewModel.customThemeAccent.collectAsStateWithLifecycle()
            val isDark = when (appearance.themeMode) {
                id.vern.antigravity.data.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                id.vern.antigravity.data.ThemeMode.LIGHT -> false
                id.vern.antigravity.data.ThemeMode.DARK -> true
            }

            AntigravityTheme(
                darkTheme = isDark,
                accentColor = appearance.accentColor,
                themePreset = themePreset,
                customBg = customBg,
                customFg = customFg,
                customAccent = customAccent
            ) {
                // API-key only build: there is no Google sign-in, so the key screen
                // is shown whenever no Gemini key is stored yet.
                val startDestination = if (GeminiClient.hasApiKey(this@MainActivity)) {
                    AppDestination.SPLASH
                } else {
                    AppDestination.API_KEY
                }
                var currentScreen by remember { mutableStateOf(startDestination) }
                val isBlurActive = DialogBlurState.isBlurActive
                val blurRadius by animateDpAsState(
                    targetValue = if (isBlurActive) 22.dp else 0.dp,
                    animationSpec = tween(durationMillis = 200),
                    label = "DialogBackgroundBlur"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400)))
                                .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.05f, animationSpec = tween(300)))
                        },
                        label = "ScreenTransition"
                    ) { destination ->
                        when (destination) {
                            AppDestination.API_KEY -> {
                                ApiKeyScreen(
                                    onContinue = {
                                        currentScreen = AppDestination.SPLASH
                                    }
                                )
                            }
                            AppDestination.SPLASH -> {
                                SplashScreen(
                                    viewModel = viewModel,
                                    onNavigateToChat = {
                                        currentScreen = AppDestination.WORKSPACE
                                    }
                                )
                            }
                            AppDestination.WORKSPACE -> {
                                WorkspaceScreen(viewModel = viewModel)
                            }
                        }
                    }
                }

            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onActivityResume(this)
    }
}

