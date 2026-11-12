package com.rk.terminal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.rk.components.compose.glass.LocalGlassAlpha
import com.rk.components.compose.glass.LocalGlassBorderAlpha
import com.rk.components.compose.glass.LocalGlassEnabled
import com.rk.components.compose.glass.LocalGlassRadius
import com.rk.libcommons.isDarkMode
import com.rk.settings.Settings

/*
 * More Themes
 */

private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
    onBackground = Color(0xFFF2F2F6),
    onSurface = Color(0xFFF2F2F6),
    onSurfaceVariant = Color(0xFFC8C8CE),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF48484E),
)

/**
 * Replaces the main accent of an existing scheme while keeping the carefully tuned surfaces of the
 * selected palette. The result is still a fully valid Material 3 colour scheme.
 */
private fun ColorScheme.withAccent(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = if (dark) accent.blend(Color.Black, 0.58f) else accent.blend(Color.White, 0.74f),
        onPrimaryContainer = if (dark) Color.White else accent.blend(Color.Black, 0.78f),
        inversePrimary = if (dark) accent.blend(Color.White, 0.35f) else accent.blend(Color.Black, 0.35f),
        surfaceTint = accent,
    )
}

private val MakShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun KarbonTheme(
    darkTheme: Boolean = when (Settings.default_night_mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> isDarkMode(LocalContext.current)
    },
    highContrastDarkTheme: Boolean = Settings.amoled,
    dynamicColor: Boolean = Settings.monet,
    themePalette: ThemePalette = Settings.theme_palette,
    content: @Composable () -> Unit,
) {
    // Reading this state makes every appearance-related setting live-update immediately.
    val revision = AppearanceState.revision

    val baseScheme =
        when {
            dynamicColor && supportsDynamicTheming() -> {
                val context = LocalContext.current
                when {
                    darkTheme && highContrastDarkTheme ->
                        dynamicDarkColorScheme(context).toAmoled()
                    darkTheme -> dynamicDarkColorScheme(context)
                    else -> dynamicLightColorScheme(context)
                }
            }

            darkTheme && highContrastDarkTheme ->
                darkColorSchemeFor(themePalette).toAmoled()
            darkTheme -> darkColorSchemeFor(themePalette)
            else -> lightColorSchemeFor(themePalette)
        }

    val colorScheme = if (Settings.use_custom_accent) {
        baseScheme.withAccent(Color(Settings.custom_accent_color), darkTheme)
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as Activity).apply {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalGlassEnabled provides Settings.glass_enabled,
        LocalGlassAlpha provides Settings.glass_alpha,
        LocalGlassRadius provides Settings.glass_corner_radius.dp,
        LocalGlassBorderAlpha provides Settings.glass_border_alpha,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = MakShapes,
            content = content
        )
    }
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
