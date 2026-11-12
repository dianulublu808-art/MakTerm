package com.rk.terminal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import com.rk.i18n.t

/**
 * Small, dependency-free HSV colour picker. It intentionally uses plain Material 3 widgets so it
 * keeps working on every Android version supported by the app.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }

    fun colorFor(h: Float, s: Float, v: Float): Color =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

    var hex by remember {
        mutableStateOf(colorFor(initialHsv[0], initialHsv[1], initialHsv[2]).toHex())
    }
    val color = colorFor(hue, saturation, value)

    fun syncHex(c: Color) {
        hex = c.toHex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(color)
                            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = t("Preview"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = color.toHex(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedTextField(
                    value = hex,
                    onValueChange = { raw ->
                        val cleaned = raw.uppercase(Locale.US).filter { it.isDigit() || it == '#' }
                        hex = if (cleaned.startsWith("#")) cleaned else "#$cleaned"
                        val parsed = runCatching { android.graphics.Color.parseColor(hex) }.getOrNull()
                        if (parsed != null) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed, hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            value = hsv[2]
                        }
                    },
                    label = { Text(t("HEX")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ColorSlider(
                    label = t("Hue"),
                    value = hue,
                    range = 0f..360f,
                    valueText = "${hue.roundToInt()}°"
                ) {
                    hue = it
                    syncHex(colorFor(it, saturation, value))
                }
                ColorSlider(
                    label = t("Saturation"),
                    value = saturation,
                    range = 0f..1f,
                    valueText = "${(saturation * 100).roundToInt()}%"
                ) {
                    saturation = it
                    syncHex(colorFor(hue, it, value))
                }
                ColorSlider(
                    label = t("Brightness"),
                    value = value,
                    range = 0f..1f,
                    valueText = "${(value * 100).roundToInt()}%"
                ) {
                    value = it
                    syncHex(colorFor(hue, saturation, it))
                }

                // Quick presets so a nice accent is always one tap away.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        0xFF7DC4E4, 0xFFA6DA95, 0xFFF5BDE6, 0xFFF5A97F,
                        0xFFEED49F, 0xFF8AADF4, 0xFFB7BDF8, 0xFFED8796
                    ).forEach { preset ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(preset.toInt()))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(preset.toInt(), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    value = hsv[2]
                                    hex = Color(preset.toInt()).toHex()
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color.toArgb()) }) {
                Text(t("Apply"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t("Cancel"))
            }
        }
    )
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

private fun Color.toHex(): String =
    String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)
