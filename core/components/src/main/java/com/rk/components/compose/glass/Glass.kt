package com.rk.components.compose.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Values shared by every glass surface in the app. They are provided once from the theme and can
 * be changed from the customization screen without touching individual cards.
 */
val LocalGlassAlpha = compositionLocalOf { 0.58f }
val LocalGlassRadius = compositionLocalOf<Dp> { 26.dp }
val LocalGlassBorderAlpha = compositionLocalOf { 0.45f }
val LocalGlassEnabled = compositionLocalOf { true }

/**
 * A translucent "liquid glass" surface.
 *
 * Compose does not expose a real backdrop blur for arbitrary views, so the effect is built from a
 * soft translucent gradient, a bright top edge and a very thin coloured rim. Combined with the
 * blurred / animated background behind the screen it reads as frosted glass while staying cheap.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalGlassRadius.current),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    alpha: Float = LocalGlassAlpha.current,
    borderAlpha: Float = LocalGlassBorderAlpha.current,
    content: @Composable BoxScope.() -> Unit
) {
    val enabled = LocalGlassEnabled.current
    val resolvedAlpha = if (enabled) alpha.coerceIn(0.05f, 0.98f) else 0.96f
    val resolvedBorder = if (enabled) borderAlpha.coerceIn(0f, 1f) else 0f
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val fill = Brush.linearGradient(
        colors = listOf(
            tint.copy(alpha = (resolvedAlpha + 0.10f).coerceAtMost(0.98f)),
            tint.copy(alpha = (resolvedAlpha * 0.84f).coerceAtLeast(0.04f)),
            tint.copy(alpha = (resolvedAlpha + 0.04f).coerceAtMost(0.98f))
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    val rim = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.24f * resolvedBorder else 0.66f * resolvedBorder),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f * resolvedBorder),
            Color.White.copy(alpha = 0.08f * resolvedBorder)
        )
    )

    val sheen = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.07f * resolvedBorder else 0.22f * resolvedBorder),
            Color.Transparent
        ),
        startY = 0f,
        endY = 260f
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, rim, shape)
            .drawWithContent {
                drawContent()
                drawRect(brush = sheen)
            },
        content = content
    )
}

/** A small circular swatch used by the colour pickers. */
@Composable
fun GlassSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val rim = if (selected) {
        Brush.linearGradient(listOf(Color.White, MaterialTheme.colorScheme.primary))
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.7f),
                Color.White.copy(alpha = 0.12f)
            )
        )
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(if (selected) 2.dp else 1.dp, rim, RoundedCornerShape(50))
    )
}
