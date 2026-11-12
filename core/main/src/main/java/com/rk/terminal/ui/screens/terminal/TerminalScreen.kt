package com.rk.terminal.ui.screens.terminal

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.libcommons.child
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.activities.terminal.MainViewModel
import com.rk.terminal.ui.components.SetStatusBarTextColor
import com.rk.terminal.ui.screens.settings.SettingsCard
import com.rk.terminal.ui.screens.settings.WorkingMode
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.ui.theme.AppearanceState
import com.rk.terminal.ui.theme.TerminalGradientBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rk.i18n.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    mainActivity: MainActivity,
    navController: NavController,
    mainViewModel: MainViewModel = viewModel(mainActivity),
    terminalViewModel: TerminalViewModel = viewModel(mainActivity)
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDarkActive = if (mainViewModel.followSystemTheme) systemDark else mainViewModel.isDarkMode
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp * 0.84).dp
    var showAddDialog by remember { mutableStateOf(false) }
    val appearanceRevision = AppearanceState.revision

    val view = LocalView.current
    DisposableEffect(Settings.keep_screen_on) {
        view.keepScreenOn = Settings.keep_screen_on
        onDispose { view.keepScreenOn = false }
    }

    val sessionBinder = mainViewModel.sessionBinder

    LaunchedEffect(isDarkActive, appearanceRevision, Settings.terminal_background_style) {
        withContext(Dispatchers.IO) {
            val style = Settings.terminal_background_style
            if (style == 1) {
                val file = context.filesDir.child("background")
                if (file.exists()) {
                    TerminalUtils.hasCustomBackground.value = true
                    if (terminalViewModel.bitmap == null) {
                        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()?.let {
                            withContext(Dispatchers.Main) {
                                terminalViewModel.bitmap = it
                            }
                        }
                    }
                } else {
                    TerminalUtils.hasCustomBackground.value = false
                }
            } else {
                TerminalUtils.hasCustomBackground.value = false
                when (style) {
                    2 -> Unit
                    3 -> TerminalUtils.darkText.value = false
                    else -> TerminalUtils.darkText.value = !isDarkActive
                }
            }
        }
    }

    terminalViewModel.virtualKeysView?.apply {
        virtualKeysViewClient = terminalViewModel.terminalView?.mTermSession?.let { VirtualKeysListener(it) }
        buttonTextColor = TerminalUtils.getViewColor()
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val terminalTextLuminance = ColorUtils.calculateLuminance(TerminalUtils.getViewColor())
    val isDarkIcons = if (drawerState.isClosed) terminalTextLuminance < 0.5 else !isDarkActive
    SetStatusBarTextColor(isDarkIcons = isDarkIcons)

    if (showAddDialog && sessionBinder != null) {
        AddSessionDialog(
            onDismiss = { showAddDialog = false },
            onCreateSession = { mode ->
                val sessionId = generateUniqueSessionId(sessionBinder.getService().sessionList.keys.toList())
                val terminal = terminalViewModel.terminalView ?: return@AddSessionDialog
                val client = TerminalBackEnd(terminal, mainActivity)
                sessionBinder.createSession(sessionId, client, mode)
                terminalViewModel.changeSession(context, sessionBinder, sessionId)
                showAddDialog = false
            },
            onCreateCustomSession = { custom ->
                val terminal = terminalViewModel.terminalView ?: return@AddSessionDialog
                val client = TerminalBackEnd(terminal, mainActivity)
                val pendingCommand = MkSession.buildCustomPendingCommand(context, custom)
                sessionBinder.createSession(custom.name, client, WorkingMode.ALPINE, pendingCommand)
                terminalViewModel.changeSession(context, sessionBinder, custom.name)
                showAddDialog = false
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || !terminalViewModel.showToolbar,
        drawerContent = {
            TerminalDrawer(
                drawerWidth = drawerWidth,
                sessionBinder = sessionBinder,
                navController = navController,
                onAddSession = { showAddDialog = true },
                onSessionSelected = { id ->
                    sessionBinder?.let { terminalViewModel.changeSession(context, it, id) }
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TerminalBackground(terminalViewModel)

            Column {
                if (terminalViewModel.showToolbar) {
                    TerminalTopBar(
                        sessionBinder = sessionBinder,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onAddClick = { showAddDialog = true },
                        color = TerminalUtils.getComposeColor()
                    )
                }

                val density = LocalDensity.current
                val topPadding = if (terminalViewModel.showToolbar) 0.dp else {
                    with(density) { TopAppBarDefaults.windowInsets.getTop(this).toDp() }
                }

                if (sessionBinder != null) {
                    TerminalViewLayout(
                        viewModel = terminalViewModel,
                        mainActivity = mainActivity,
                        sessionBinder = sessionBinder,
                        modifier = Modifier
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(top = topPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalBackground(viewModel: TerminalViewModel) {
    val style = Settings.terminal_background_style
    when (style) {
        1 -> {
            viewModel.bitmap?.let { bitmap ->
                Box(Modifier.fillMaxSize().zIndex(-1f)) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(viewModel.wallAlpha)
                            .let {
                                if (viewModel.backgroundBlur > 0.5f) {
                                    it.blur(viewModel.backgroundBlur.dp)
                                } else {
                                    it
                                }
                            }
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = Settings.terminal_bg_dim.coerceIn(0f, 0.9f)))
                    )
                }
            }
        }

        2 -> Box(
            Modifier
                .fillMaxSize()
                .zIndex(-1f)
                .background(
                    Color(Settings.terminal_bg_color)
                        .copy(alpha = Settings.terminal_bg_opacity.coerceIn(0f, 1f))
                )
        )

        3 -> Box(Modifier.fillMaxSize().zIndex(-1f)) {
            TerminalGradientBackground()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = Settings.terminal_bg_dim.coerceIn(0f, 0.75f)))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSessionDialog(
    onDismiss: () -> Unit,
    onCreateSession: (Int) -> Unit,
    onCreateCustomSession: (CustomSession) -> Unit
) {
    val customSessions = remember { CustomSessions.getAll() }
    val isArch = Settings.rootfs_distro == "arch"
    BasicAlertDialog(onDismissRequest = onDismiss) {
        PreferenceGroup {
            SettingsCard(
                title = { Text(t(if (isArch) "Arch Linux ARM" else "Alpine")) },
                description = { Text(if (isArch) "Arch Linux ARM" else stringResource(strings.alpine_desc)) },
                onClick = { onCreateSession(WorkingMode.ALPINE) }
            )
            SettingsCard(
                title = { Text(t("Android")) },
                description = { Text(stringResource(strings.android_desc)) },
                onClick = { onCreateSession(WorkingMode.ANDROID) }
            )
            customSessions.forEach { session ->
                SettingsCard(
                    title = { Text(session.name) },
                    description = { Text(session.shellPath) },
                    onClick = { onCreateCustomSession(session) }
                )
            }
        }
    }
}

private fun generateUniqueSessionId(existingIds: List<String>): String {
    var index = 1
    var newId: String
    do {
        newId = "main$index"
        index++
    } while (newId in existingIds)
    return newId
}
