package id.vern.antigravity.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import id.vern.antigravity.data.AccentColor
import id.vern.antigravity.data.ThemePreset

fun parseColorOrNull(hex: String): Color? {
    return try {
        val clean = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
        Color(android.graphics.Color.parseColor(clean))
    } catch (_: Exception) {
        null
    }
}

fun isColorDark(color: Color): Boolean {
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance < 0.5
}

fun blendColors(from: Color, to: Color, ratio: Float): Color {
    val clampedRatio = ratio.coerceIn(0f, 1f)
    val inverse = 1f - clampedRatio
    return Color(
        red = from.red * inverse + to.red * clampedRatio,
        green = from.green * inverse + to.green * clampedRatio,
        blue = from.blue * inverse + to.blue * clampedRatio,
        alpha = 1f
    )
}

fun buildColorScheme(
    darkTheme: Boolean,
    accent: AccentColor,
    themePresetName: String = "Default Dark",
    customBg: String = "#101010",
    customFg: String = "#CCCCCC",
    customAccent: String = "#1A73E8"
): androidx.compose.material3.ColorScheme {
    val preset = if (themePresetName.equals(ThemePreset.CUSTOM.displayName, ignoreCase = true) || themePresetName.equals(ThemePreset.CUSTOM.id, ignoreCase = true)) {
        ThemePreset.CUSTOM
    } else if (darkTheme) {
        ThemePreset.entries.find {
            (it.displayName.equals(themePresetName, ignoreCase = true) || it.id.equals(themePresetName, ignoreCase = true)) && it != ThemePreset.DEFAULT_LIGHT
        } ?: ThemePreset.DEFAULT_DARK
    } else {
        ThemePreset.DEFAULT_LIGHT
    }

    return when (preset) {
        ThemePreset.CUSTOM -> {
            val bg = parseColorOrNull(customBg) ?: Color(0xFF101010)
            val fg = parseColorOrNull(customFg) ?: Color(0xFFCCCCCC)
            val acc = parseColorOrNull(customAccent) ?: Color(0xFF1A73E8)
            val isBgDark = isColorDark(bg)
            val isAccDark = isColorDark(acc)

            if (isBgDark) {
                darkColorScheme(
                    primary = acc,
                    onPrimary = if (isAccDark) Color.White else Color.Black,
                    primaryContainer = blendColors(bg, acc, 0.25f),
                    onPrimaryContainer = if (isAccDark) Color.White else acc,
                    secondary = acc,
                    onSecondary = if (isAccDark) Color.White else Color.Black,
                    secondaryContainer = blendColors(bg, acc, 0.18f),
                    onSecondaryContainer = fg,
                    background = bg,
                    onBackground = fg,
                    surface = blendColors(bg, fg, 0.04f),
                    onSurface = fg,
                    surfaceVariant = blendColors(bg, fg, 0.08f),
                    onSurfaceVariant = fg.copy(alpha = 0.75f),
                    surfaceContainerLowest = bg,
                    surfaceContainerLow = blendColors(bg, fg, 0.02f),
                    surfaceContainer = blendColors(bg, fg, 0.04f),
                    surfaceContainerHigh = blendColors(bg, fg, 0.08f),
                    surfaceContainerHighest = blendColors(bg, fg, 0.12f),
                    outline = fg.copy(alpha = 0.25f),
                    outlineVariant = fg.copy(alpha = 0.12f),
                    error = AntigravityRed
                )
            } else {
                lightColorScheme(
                    primary = acc,
                    onPrimary = if (isAccDark) Color.White else Color.Black,
                    primaryContainer = blendColors(bg, acc, 0.15f),
                    onPrimaryContainer = if (isAccDark) acc else Color(0xFF062E6F),
                    secondary = acc,
                    onSecondary = if (isAccDark) Color.White else Color.Black,
                    secondaryContainer = blendColors(bg, acc, 0.10f),
                    onSecondaryContainer = fg,
                    background = bg,
                    onBackground = fg,
                    surface = blendColors(bg, fg, 0.03f),
                    onSurface = fg,
                    surfaceVariant = blendColors(bg, fg, 0.06f),
                    onSurfaceVariant = fg.copy(alpha = 0.75f),
                    surfaceContainerLowest = bg,
                    surfaceContainerLow = blendColors(bg, fg, 0.02f),
                    surfaceContainer = blendColors(bg, fg, 0.04f),
                    surfaceContainerHigh = blendColors(bg, fg, 0.07f),
                    surfaceContainerHighest = blendColors(bg, fg, 0.10f),
                    outline = fg.copy(alpha = 0.25f),
                    outlineVariant = fg.copy(alpha = 0.12f),
                    error = AntigravityRed
                )
            }
        }
        ThemePreset.CYBERPUNK -> darkColorScheme(
            primary = Color(0xFF00FFCC),
            onPrimary = Color(0xFF0B001A),
            primaryContainer = Color(0xFF163832),
            onPrimaryContainer = Color(0xFF00FFCC),
            secondary = Color(0xFFFF0055),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF4A0022),
            onSecondaryContainer = Color(0xFFFF80AA),
            background = Color(0xFF0B001A),
            onBackground = Color(0xFF00FFCC),
            surface = Color(0xFF140529),
            onSurface = Color(0xFFE8DCFF),
            surfaceVariant = Color(0xFF220A40),
            onSurfaceVariant = Color(0xFF00FFCC),
            surfaceContainerLowest = Color(0xFF070012),
            surfaceContainerLow = Color(0xFF100320),
            surfaceContainer = Color(0xFF140529),
            surfaceContainerHigh = Color(0xFF220A40),
            surfaceContainerHighest = Color(0xFF32105C),
            outline = Color(0xFFFF0055),
            outlineVariant = Color(0xFF6E1848),
            error = Color(0xFFFF3366)
        )
        ThemePreset.HIGH_CONTRAST -> darkColorScheme(
            primary = Color(0xFF00FFFF),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF003838),
            onPrimaryContainer = Color(0xFF00FFFF),
            secondary = Color(0xFFFFFF00),
            onSecondary = Color.Black,
            secondaryContainer = Color(0xFF383800),
            onSecondaryContainer = Color(0xFFFFFF00),
            background = Color(0xFF000000),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF1E1E1E),
            onSurfaceVariant = Color(0xFF00FFFF),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF1E1E1E),
            surfaceContainerHighest = Color(0xFF2A2A2A),
            outline = Color(0xFF00FFFF),
            outlineVariant = Color(0xFF008888),
            error = Color(0xFFFF0000)
        )
        ThemePreset.SOLARIZED -> darkColorScheme(
            primary = Color(0xFF268BD2),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF0F4D75),
            onPrimaryContainer = Color(0xFFBFE3FF),
            secondary = Color(0xFF2AA198),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF12544F),
            onSecondaryContainer = Color(0xFFB5EBE6),
            background = Color(0xFF002B36),
            onBackground = Color(0xFF93A1A1),
            surface = Color(0xFF073642),
            onSurface = Color(0xFFEEE8D5),
            surfaceVariant = Color(0xFF0D4857),
            onSurfaceVariant = Color(0xFF93A1A1),
            surfaceContainerLowest = Color(0xFF001F27),
            surfaceContainerLow = Color(0xFF042B34),
            surfaceContainer = Color(0xFF073642),
            surfaceContainerHigh = Color(0xFF0D4857),
            surfaceContainerHighest = Color(0xFF14596B),
            outline = Color(0xFF586E75),
            outlineVariant = Color(0xFF2E4B54),
            error = Color(0xFFDC322F)
        )
        ThemePreset.MONOKAI -> darkColorScheme(
            primary = Color(0xFFFFD866),
            onPrimary = Color(0xFF2D2A2E),
            primaryContainer = Color(0xFF574619),
            onPrimaryContainer = Color(0xFFFFEFA8),
            secondary = Color(0xFFFF6188),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF541B2A),
            onSecondaryContainer = Color(0xFFFFB3C4),
            background = Color(0xFF2D2A2E),
            onBackground = Color(0xFFFCFCFA),
            surface = Color(0xFF363337),
            onSurface = Color(0xFFFCFCFA),
            surfaceVariant = Color(0xFF403E41),
            onSurfaceVariant = Color(0xFFC1C0C0),
            surfaceContainerLowest = Color(0xFF222023),
            surfaceContainerLow = Color(0xFF2D2A2E),
            surfaceContainer = Color(0xFF363337),
            surfaceContainerHigh = Color(0xFF403E41),
            surfaceContainerHighest = Color(0xFF4D4A4E),
            outline = Color(0xFF787878),
            outlineVariant = Color(0xFF4A484B),
            error = Color(0xFFFF6188)
        )
        ThemePreset.DEFAULT_LIGHT -> {
            val (primary, onPrimary, primaryContainer, onPrimaryContainer) = when (accent) {
                AccentColor.DEFAULT, AccentColor.BLUE -> listOf(AccentBlueLight, Color.White, Color(0xFFE3EDFE), Color(0xFF062E6F))
                AccentColor.RED -> listOf(AccentRedLight, Color.White, Color(0xFFFFDAD6), Color(0xFF410002))
                AccentColor.YELLOW -> listOf(AccentYellowLight, Color.White, Color(0xFFFFEFD2), Color(0xFF452B00))
                AccentColor.GREEN -> listOf(AccentGreenLight, Color.White, Color(0xFFD1FAE5), Color(0xFF003923))
                AccentColor.PURPLE -> listOf(AccentPurpleLight, Color.White, Color(0xFFF3E8FF), Color(0xFF381E72))
            }
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = LightSecondary,
                onSecondary = LightOnSecondary,
                secondaryContainer = LightSecondaryContainer,
                onSecondaryContainer = LightOnSecondaryContainer,
                background = LightBackground,
                onBackground = LightTextPrimary,
                surface = LightSurface,
                onSurface = LightTextPrimary,
                surfaceVariant = LightSurfaceContainer,
                onSurfaceVariant = LightTextSecondary,
                surfaceContainerLowest = LightSurfaceContainerLowest,
                surfaceContainerLow = LightSurfaceContainerLow,
                surfaceContainer = LightSurfaceContainer,
                surfaceContainerHigh = LightSurfaceContainerHigh,
                surfaceContainerHighest = LightSurfaceContainerHighest,
                outline = LightOutline,
                outlineVariant = LightOutlineVariant,
                error = AntigravityRed
            )
        }
        ThemePreset.DEFAULT_DARK -> {
            val (primary, onPrimary, primaryContainer, onPrimaryContainer) = when (accent) {
                AccentColor.DEFAULT, AccentColor.BLUE -> {
                    if (darkTheme) listOf(AccentBlueDark, Color(0xFF002B75), Color(0xFF1B4D9E), Color(0xFFD3E3FD))
                    else listOf(AccentBlueLight, Color.White, Color(0xFFE3EDFE), Color(0xFF062E6F))
                }
                AccentColor.RED -> {
                    if (darkTheme) listOf(AccentRedDark, Color(0xFF560000), Color(0xFF930006), Color(0xFFFFDAD4))
                    else listOf(AccentRedLight, Color.White, Color(0xFFFFDAD6), Color(0xFF410002))
                }
                AccentColor.YELLOW -> {
                    if (darkTheme) listOf(AccentYellowDark, Color(0xFF432C00), Color(0xFF604100), Color(0xFFFFDF9E))
                    else listOf(AccentYellowLight, Color.White, Color(0xFFFFEFD2), Color(0xFF452B00))
                }
                AccentColor.GREEN -> {
                    if (darkTheme) listOf(AccentGreenDark, Color(0xFF003822), Color(0xFF005234), Color(0xFF86F8C4))
                    else listOf(AccentGreenLight, Color.White, Color(0xFFD1FAE5), Color(0xFF003923))
                }
                AccentColor.PURPLE -> {
                    if (darkTheme) listOf(AccentPurpleDark, Color(0xFF381E72), Color(0xFF4F378A), Color(0xFFE9DDFF))
                    else listOf(AccentPurpleLight, Color.White, Color(0xFFF3E8FF), Color(0xFF381E72))
                }
            }

            if (darkTheme) {
                darkColorScheme(
                    primary = primary,
                    onPrimary = onPrimary,
                    primaryContainer = primaryContainer,
                    onPrimaryContainer = onPrimaryContainer,
                    secondary = DarkSecondary,
                    onSecondary = DarkOnSecondary,
                    secondaryContainer = DarkSecondaryContainer,
                    onSecondaryContainer = DarkOnSecondaryContainer,
                    background = DarkBackground,
                    onBackground = DarkTextPrimary,
                    surface = DarkSurface,
                    onSurface = DarkTextPrimary,
                    surfaceVariant = DarkSurfaceContainer,
                    onSurfaceVariant = DarkTextSecondary,
                    surfaceContainerLowest = DarkSurfaceContainerLowest,
                    surfaceContainerLow = DarkSurfaceContainerLow,
                    surfaceContainer = DarkSurfaceContainer,
                    surfaceContainerHigh = DarkSurfaceContainerHigh,
                    surfaceContainerHighest = DarkSurfaceContainerHighest,
                    outline = DarkOutline,
                    outlineVariant = DarkOutlineVariant,
                    error = AntigravityRed
                )
            } else {
                lightColorScheme(
                    primary = primary,
                    onPrimary = onPrimary,
                    primaryContainer = primaryContainer,
                    onPrimaryContainer = onPrimaryContainer,
                    secondary = LightSecondary,
                    onSecondary = LightOnSecondary,
                    secondaryContainer = LightSecondaryContainer,
                    onSecondaryContainer = LightOnSecondaryContainer,
                    background = LightBackground,
                    onBackground = LightTextPrimary,
                    surface = LightSurface,
                    onSurface = LightTextPrimary,
                    surfaceVariant = LightSurfaceContainer,
                    onSurfaceVariant = LightTextSecondary,
                    surfaceContainerLowest = LightSurfaceContainerLowest,
                    surfaceContainerLow = LightSurfaceContainerLow,
                    surfaceContainer = LightSurfaceContainer,
                    surfaceContainerHigh = LightSurfaceContainerHigh,
                    surfaceContainerHighest = LightSurfaceContainerHighest,
                    outline = LightOutline,
                    outlineVariant = LightOutlineVariant,
                    error = AntigravityRed
                )
            }
        }
    }
}

val AntigravityMenuShape = RoundedCornerShape(16.dp)

val AntigravityShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AntigravityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: AccentColor = AccentColor.BLUE,
    themePreset: String = "Default Dark",
    customBg: String = "#101010",
    customFg: String = "#CCCCCC",
    customAccent: String = "#1A73E8",
    content: @Composable () -> Unit
) {
    val colorScheme = buildColorScheme(
        darkTheme = darkTheme,
        accent = accentColor,
        themePresetName = themePreset,
        customBg = customBg,
        customFg = customFg,
        customAccent = customAccent
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = colorScheme.background.toArgb()
                val isLightPreset = if (themePreset.equals(ThemePreset.CUSTOM.displayName, ignoreCase = true) || themePreset.equals(ThemePreset.CUSTOM.id, ignoreCase = true)) {
                    val bg = parseColorOrNull(customBg) ?: Color(0xFF101010)
                    !isColorDark(bg)
                } else {
                    !darkTheme
                }
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = isLightPreset
                    isAppearanceLightNavigationBars = isLightPreset
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AntigravityShapes,
        content = content
    )
}

