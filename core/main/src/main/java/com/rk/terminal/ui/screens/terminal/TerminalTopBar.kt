package com.rk.terminal.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.components.compose.glass.GlassSurface
import com.rk.terminal.service.SessionService
import com.rk.i18n.t

@Composable
fun TerminalTopBar(
    sessionBinder: SessionService.SessionBinder?,
    onMenuClick: () -> Unit,
    onAddClick: () -> Unit,
    color: Color
) {
    val current = sessionBinder?.getService()?.currentSession?.value
    val sessionCount = sessionBinder?.getService()?.sessionList?.size ?: 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = t("Sessions"), tint = color)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = t("MakTerm"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = color
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = buildString {
                                append(current?.first ?: "terminal")
                                append("  ·  ")
                                append(TerminalUtils.getNameOfWorkingMode(current?.second))
                                if (sessionCount > 1) {
                                    append("  ·  ")
                                    append(sessionCount)
                                    append(" sessions")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = color.copy(alpha = 0.72f),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = t("New session"), tint = color)
                }
            }
        }
    }
}
