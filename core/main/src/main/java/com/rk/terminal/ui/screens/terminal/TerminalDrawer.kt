package com.rk.terminal.ui.screens.terminal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rk.components.compose.glass.GlassSurface
import com.rk.components.compose.glass.LocalGlassAlpha
import com.rk.components.compose.glass.LocalGlassBorderAlpha
import com.rk.components.compose.glass.LocalGlassRadius
import com.rk.resources.strings
import com.rk.terminal.service.SessionService
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.i18n.t

@Composable
fun TerminalDrawer(
    drawerWidth: Dp,
    sessionBinder: SessionService.SessionBinder?,
    navController: NavController,
    onAddSession: () -> Unit,
    onSessionSelected: (String) -> Unit
) {
    var sessionToRename by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    ModalDrawerSheet(
        modifier = Modifier.width(drawerWidth),
        drawerContainerColor = Color.Transparent,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
        drawerTonalElevation = 0.dp
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            alpha = 0.72f
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DrawerHeader(
                    onCustomize = {
                        navController.navigate(MainActivityRoutes.Customization.route)
                        keyboardController?.hide()
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilledTonalIconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(strings.sort_sessions)
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(strings.sort_az)) },
                                onClick = {
                                    showSortMenu = false
                                    sessionBinder?.sortSessions(ascending = true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(strings.sort_za)) },
                                onClick = {
                                    showSortMenu = false
                                    sessionBinder?.sortSessions(ascending = false)
                                }
                            )
                        }
                    }

                    FilledTonalIconButton(onClick = {
                        navController.navigate(MainActivityRoutes.Settings.route)
                        keyboardController?.hide()
                    }) {
                        Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
                    }

                    Spacer(Modifier.weight(1f))

                    Button(
                        onClick = onAddSession,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("New"))
                    }
                }

                Text(
                    text = stringResource(strings.session),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )

                val service = sessionBinder?.getService()
                val sessions: List<String> = if (service != null && service.sessionOrder.isNotEmpty()) {
                    service.sessionOrder.toList()
                } else {
                    service?.sessionList?.keys?.toList() ?: emptyList()
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = sessions,
                        key = { _, sessionId -> sessionId }
                    ) { index, sessionId ->
                        val isSelected = sessionId == service?.currentSession?.value?.first
                        SelectableCard(
                            selected = isSelected,
                            onSelect = { onSessionSelected(sessionId) },
                            onLongClick = { sessionToRename = sessionId },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sessionId,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = { sessionBinder?.moveSession(index, index - 1) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = stringResource(strings.move_up),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    if (index < sessions.size - 1) {
                                        IconButton(
                                            onClick = { sessionBinder?.moveSession(index, index + 1) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = stringResource(strings.move_down),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { sessionToRename = sessionId },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = stringResource(strings.rename),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (!isSelected || sessions.size > 1) {
                                        IconButton(
                                            onClick = { sessionBinder?.terminateSession(sessionId) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
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

    sessionToRename?.let { currentName ->
        var newName by remember { mutableStateOf(currentName) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val alreadyExistsText = stringResource(strings.name_already_exists)
        val cannotBeEmptyText = stringResource(strings.name_cannot_be_empty)

        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text(text = stringResource(strings.rename_session)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = {
                            newName = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(strings.session_name)) },
                        singleLine = true,
                        isError = errorMessage != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isEmpty()) {
                            errorMessage = cannotBeEmptyText
                        } else if (trimmed != currentName && sessionBinder?.getService()?.sessionList?.containsKey(trimmed) == true) {
                            errorMessage = alreadyExistsText
                        } else {
                            sessionBinder?.renameSession(currentName, trimmed)
                            sessionToRename = null
                        }
                    }
                ) {
                    Text(stringResource(strings.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text(stringResource(strings.cancel))
                }
            }
        )
    }
}

@Composable
private fun DrawerHeader(onCustomize: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tint = MaterialTheme.colorScheme.primaryContainer,
        alpha = 0.38f,
        borderAlpha = 0.55f
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(primary, tertiary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = t("MakTerm"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = t("Liquid glass terminal"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCustomize) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = t("Customize"),
                    tint = primary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableCard(
    selected: Boolean,
    onSelect: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "containerColor"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 0.95f else LocalGlassBorderAlpha.current,
        label = "cardBorderAlpha"
    )

    GlassSurface(
        modifier = modifier.combinedClickable(
            onClick = onSelect,
            onLongClick = onLongClick,
            enabled = enabled
        ),
        shape = RoundedCornerShape(LocalGlassRadius.current),
        tint = containerColor,
        alpha = if (selected) 0.74f else LocalGlassAlpha.current,
        borderAlpha = borderAlpha
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            content()
        }
    }
}
