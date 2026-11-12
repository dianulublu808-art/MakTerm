package com.rk.terminal.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rk.libcommons.child
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bumped whenever the user changes an appearance setting. All appearance composables read it, so
 * changes are visible immediately without recreating the activity.
 */
object AppearanceState {
    var revision by mutableIntStateOf(0)
        private set

    fun refresh() {
        revision++
    }
}

/**
 * Slow, throttled phase for the liquid backgrounds.
 *
 * The gradients move over tens of seconds, so redrawing them at the display
 * refresh rate only burns CPU/GPU and makes typing in the terminal feel laggy.
 * 20 fps is visually identical here, and the animation is frozen while the
 * keyboard is open (typing) or when the user enabled "reduce motion".
 */
@Composable
private fun rememberGlassPhase(periodMillis: Int, animated: Boolean, fps: Int = 20): Float {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMillis, animated, fps) {
        if (!animated) return@LaunchedEffect
        val frameNanos = 1_000_000_000L / fps
        val periodNanos = periodMillis * 1_000_000f
        val base = phase
        var startNanos = 0L
        var lastUpdate = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                if (now - lastUpdate >= frameNanos) {
                    lastUpdate = now
                    val elapsed = ((now - startNanos) / periodNanos) * (2f * PI).toFloat()
                    phase = (base + elapsed) % (2f * PI).toFloat()
                }
            }
        }
    }
    return phase
}

@Composable
private fun isImeVisible(): Boolean {
    val density = LocalDensity.current
    return WindowInsets.ime.getBottom(density) > 0
}

/** Full-screen background used by every screen of the app. */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val revision = AppearanceState.revision
    val style = Settings.app_background_style
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(modifier = modifier.fillMaxSize()) {
        when (style) {
            1 -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(Settings.app_background_color))
            )

            2 -> ImageAppBackground(revision)
            else -> LiquidBackground()
        }

        // Soft vignette keeps text readable on very bright wallpapers and gradients.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = if (dark) 0.34f else 0.08f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun ImageAppBackground(revision: Int) {
    val context = LocalContext.current
    val file = context.filesDir.child("app_background")
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadedModified by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(revision, Settings.app_background_style) {
        val modified = if (file.exists() && file.canRead()) file.lastModified() else 0L
        if (bitmap == null || modified != loadedModified) {
            bitmap = withContext(Dispatchers.IO) {
                if (modified != 0L) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else {
                    null
                }
            }
            loadedModified = modified
        }
    }

    val image = bitmap
    if (image != null) {
        Box(Modifier.fillMaxSize()) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .let {
                        if (Settings.app_background_blur > 0.5f) {
                            it.blur(Settings.app_background_blur.dp)
                        } else {
                            it
                        }
                    }
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = Settings.app_background_dim.coerceIn(0f, 0.9f)))
            )
        }
    } else {
        LiquidBackground()
    }
}

/** Animated, slowly moving colour field — the "liquid" part of the liquid glass look. */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val revision = AppearanceState.revision
    val scheme = MaterialTheme.colorScheme
    val speed = Settings.liquid_speed.coerceIn(0.15f, 3f)
    val intensity = Settings.liquid_intensity.coerceIn(0f, 1f)
    val dark = scheme.background.luminance() < 0.5f

    val animated = !Settings.reduce_motion && !isImeVisible()
    val phase = rememberGlassPhase(
        periodMillis = (22000f / speed).toInt().coerceAtLeast(5000),
        animated = animated
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.minDimension <= 0f) return@Canvas

        val base = if (dark) Color(0xFF070A12) else scheme.background
        val accent = if (dark) scheme.primary else scheme.primary.blend(Color.White, 0.30f)
        val second = if (dark) scheme.secondary else scheme.secondary.blend(Color.White, 0.35f)
        val third = if (dark) scheme.tertiary else scheme.tertiary.blend(Color.White, 0.35f)

        drawRect(
            Brush.linearGradient(
                colors = listOf(
                    base,
                    scheme.surfaceContainerLowest.blend(accent, 0.18f),
                    base
                )
            )
        )

        val min = size.minDimension
        drawBlob(
            center = Offset(
                x = size.width * (0.18f + 0.10f * sin(phase)),
                y = size.height * (0.22f + 0.08f * cos(phase * 1.3f))
            ),
            radius = min * 0.72f,
            color = accent,
            intensity = intensity * if (dark) 0.85f else 0.55f
        )
        drawBlob(
            center = Offset(
                x = size.width * (0.84f + 0.09f * cos(phase * 0.8f)),
                y = size.height * (0.34f + 0.12f * sin(phase * 1.1f))
            ),
            radius = min * 0.66f,
            color = second,
            intensity = intensity * if (dark) 0.70f else 0.45f
        )
        drawBlob(
            center = Offset(
                x = size.width * (0.58f + 0.14f * sin(phase * 0.7f)),
                y = size.height * (0.86f + 0.08f * cos(phase * 1.4f))
            ),
            radius = min * 0.78f,
            color = third,
            intensity = intensity * if (dark) 0.60f else 0.40f
        )

        // A moving specular band makes the gradient feel like glass rather than paint.
        drawRect(
            Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = if (dark) 0.035f else 0.10f),
                    Color.Transparent
                ),
                start = Offset(
                    x = -size.width + size.width * 2f * (phase / (2f * PI).toFloat()),
                    y = 0f
                ),
                end = Offset(
                    x = size.width * (phase / (2f * PI).toFloat()),
                    y = size.height
                )
            )
        )
    }
}

/** Dark accent gradient used when the terminal should not follow the app background. */
@Composable
fun TerminalGradientBackground(modifier: Modifier = Modifier) {
    val revision = AppearanceState.revision
    val scheme = MaterialTheme.colorScheme
    val speed = Settings.liquid_speed.coerceIn(0.15f, 3f)
    val animated = !Settings.reduce_motion && !isImeVisible()
    val phase = rememberGlassPhase(
        periodMillis = (26000f / speed).toInt().coerceAtLeast(6000),
        animated = animated
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.minDimension <= 0f) return@Canvas
        val base = Color(0xFF080A10)
        drawRect(
            Brush.linearGradient(
                colors = listOf(
                    base,
                    scheme.primary.blend(base, 0.72f),
                    scheme.tertiary.blend(base, 0.80f),
                    base
                )
            )
        )
        drawBlob(
            center = Offset(
                x = size.width * (0.25f + 0.15f * sin(phase)),
                y = size.height * (0.28f + 0.10f * cos(phase * 1.2f))
            ),
            radius = size.minDimension * 0.80f,
            color = scheme.primary,
            intensity = 0.55f
        )
        drawBlob(
            center = Offset(
                x = size.width * (0.82f + 0.12f * cos(phase * 0.9f)),
                y = size.height * (0.74f + 0.10f * sin(phase * 1.3f))
            ),
            radius = size.minDimension * 0.74f,
            color = scheme.tertiary,
            intensity = 0.42f
        )
    }
}

private fun DrawScope.drawBlob(
    center: Offset,
    radius: Float,
    color: Color,
    intensity: Float
) {
    if (radius <= 0f || intensity <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.60f * intensity),
                color.copy(alpha = 0.22f * intensity),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

