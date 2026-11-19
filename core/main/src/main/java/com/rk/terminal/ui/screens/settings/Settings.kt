package com.rk.terminal.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.rk.components.compose.glass.GlassSurface
import com.rk.components.compose.glass.LocalGlassRadius
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.libcommons.child
import com.rk.libcommons.localDir
import com.rk.resources.strings
import com.rk.server.FileServer
import com.rk.server.HostService
import com.rk.server.HostedProcess
import com.rk.server.HostedProcessManager
import com.rk.server.HostedProcesses
import com.rk.server.ProcessLauncher
import com.rk.server.SystemStats
import com.rk.server.TaskRunner
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.components.SettingsToggle
import com.rk.terminal.ui.routes.MainActivityRoutes
import com.rk.terminal.ui.screens.terminal.CustomSessions
import com.rk.terminal.ui.screens.terminal.ExecMode
import com.rk.terminal.ui.screens.terminal.Rootfs
import com.rk.i18n.t
import com.rk.update.GitHubUpdater
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    title: @Composable () -> Unit,
    description: @Composable () -> Unit = {},
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    PreferenceTemplate(
        modifier = modifier.combinedClickable(
            enabled = isEnabled,
            indication = ripple(),
            interactionSource = interactionSource,
            onClick = onClick
        ),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(start = 16.dp),
        title = title,
        description = description,
        startWidget = startWidget,
        endWidget = endWidget,
        applyPaddings = false
    )
}

object WorkingMode {
    const val ALPINE = 0
    const val ANDROID = 1
}

object InputMode {
    const val DEFAULT = 0
    const val TYPE_NULL = 1
    const val VISIBLE_PASSWORD = 2
}

object LoginShell {
    const val DISTRO = 0
    const val BASH = 1
    const val SH = 2
    const val ASH = 3
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    navController: NavController,
    mainActivity: MainActivity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedWorkingMode by remember { mutableIntStateOf(Settings.working_Mode) }
    var selectedInputMode by remember { mutableIntStateOf(Settings.input_mode) }
    var selectedLoginShell by remember { mutableIntStateOf(Settings.login_shell) }
    var selectedExecMode by remember { mutableStateOf(Rootfs.execMode.value) }
    var customSessions by remember { mutableStateOf(CustomSessions.getAll()) }
    var showAddCustomSession by remember { mutableStateOf(false) }
    var defaultIsCustom by remember { mutableStateOf(Settings.default_is_custom) }
    var defaultCustomId by remember { mutableStateOf(CustomSessions.getDefaultId()) }
    var selectedLanguage by remember { mutableStateOf(Settings.app_language) }
    var reduceMotion by remember { mutableStateOf(Settings.reduce_motion) }
    var keepScreenOn by remember { mutableStateOf(Settings.keep_screen_on) }
    var showResetRootfs by remember { mutableStateOf(false) }
    var resettingRootfs by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<GitHubUpdater.Release?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var updateUpToDate by remember { mutableStateOf(false) }
    var showServerPort by remember { mutableStateOf(false) }
    var showServerRoot by remember { mutableStateOf(false) }
    var showServerPassword by remember { mutableStateOf(false) }
    var serverAuth by remember { mutableStateOf(Settings.server_auth) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf(SystemStats.snapshot(context)) }
    var hostedProcesses by remember { mutableStateOf(HostedProcesses.getAll()) }
    var showHostEditor by remember { mutableStateOf(false) }
    var editingHost by remember { mutableStateOf<HostedProcess?>(null) }
    var showHostLog by remember { mutableStateOf<HostedProcess?>(null) }
    var deleteHost by remember { mutableStateOf<HostedProcess?>(null) }
    var showTaskLog by remember { mutableStateOf(false) }
    val currentVersion = remember { GitHubUpdater.currentVersion(context) }
    val displayVersion = currentVersion.substringBefore('-')
    val settingsScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            stats = SystemStats.snapshot(context)
            delay(2000)
        }
    }

    fun checkForUpdates() {
        if (checkingUpdate || downloadingUpdate) return
        checkingUpdate = true
        updateError = null
        updateUpToDate = false
        updateRelease = null
        settingsScope.launch {
            val release = withContext(Dispatchers.IO) { GitHubUpdater.fetchLatest() }
            checkingUpdate = false
            if (release == null) {
                updateError = t("Could not check for updates")
            } else if (GitHubUpdater.isNewer(release.version, currentVersion)) {
                updateRelease = release
                updateProgress = 0
            } else {
                updateRelease = release
                updateUpToDate = true
            }
            showUpdateDialog = true
        }
    }

    fun downloadAndInstall(release: GitHubUpdater.Release) {
        val url = release.apkUrl
        if (url.isNullOrBlank() || downloadingUpdate) return
        downloadingUpdate = true
        updateProgress = 0
        updateError = null
        settingsScope.launch {
            val result = runCatching {
                val apk = withContext(Dispatchers.IO) {
                    GitHubUpdater.download(context, url) { percent ->
                        withContext(Dispatchers.Main) { updateProgress = percent }
                    }
                }
                GitHubUpdater.install(context, apk)
                apk
            }
            downloadingUpdate = false
            result.onSuccess {
                showUpdateDialog = false
            }.onFailure {
                updateError = it.message ?: t("Update failed")
            }
        }
    }

    PreferenceLayout(
        label = stringResource(strings.settings),
        modifier = modifier,
        onBack = { navController.popBackStack() }
    ) {
        SettingsHero()

        PreferenceGroup(heading = stringResource(strings.default_working_mode)) {
            WorkingModeOption(
                title = t("Alpine"),
                description = stringResource(strings.alpine_desc),
                selected = !defaultIsCustom && selectedWorkingMode == WorkingMode.ALPINE
            ) {
                defaultIsCustom = false
                Settings.default_is_custom = false
                selectedWorkingMode = WorkingMode.ALPINE
                Settings.working_Mode = WorkingMode.ALPINE
            }
            WorkingModeOption(
                title = t("Android"),
                description = stringResource(strings.android_desc),
                selected = !defaultIsCustom && selectedWorkingMode == WorkingMode.ANDROID
            ) {
                defaultIsCustom = false
                Settings.default_is_custom = false
                selectedWorkingMode = WorkingMode.ANDROID
                Settings.working_Mode = WorkingMode.ANDROID
            }
            customSessions.forEach { session ->
                WorkingModeOption(
                    title = session.name,
                    description = session.shellPath,
                    selected = defaultIsCustom && defaultCustomId == session.id
                ) {
                    defaultIsCustom = true
                    defaultCustomId = session.id
                    Settings.default_is_custom = true
                    CustomSessions.setDefault(session.id)
                }
            }
        }

        PreferenceGroup(heading = t("Execution Mode")) {
            ExecModeOption("Chroot", "Requires root, faster, real bind mounts", ExecMode.CHROOT, selectedExecMode) {
                selectedExecMode = it
                Rootfs.setExecMode(it)
            }
            ExecModeOption("Proot", "No root required, slightly slower", ExecMode.PROOT, selectedExecMode) {
                selectedExecMode = it
                Rootfs.setExecMode(it)
            }
        }

        PreferenceGroup(heading = t("Login Shell")) {
            LoginShellOption(
                title = t("Distro default"),
                description = t("ash on Alpine, bash on Arch"),
                mode = LoginShell.DISTRO,
                currentMode = selectedLoginShell
            ) {
                selectedLoginShell = it
                Settings.login_shell = it
            }
            LoginShellOption(
                title = t("bash"),
                description = t("/bin/bash (runs apk add bash if missing)"),
                mode = LoginShell.BASH,
                currentMode = selectedLoginShell
            ) {
                selectedLoginShell = it
                Settings.login_shell = it
            }
            LoginShellOption(
                title = t("sh"),
                description = t("/bin/sh"),
                mode = LoginShell.SH,
                currentMode = selectedLoginShell
            ) {
                selectedLoginShell = it
                Settings.login_shell = it
            }
            LoginShellOption(
                title = t("ash"),
                description = t("/bin/ash"),
                mode = LoginShell.ASH,
                currentMode = selectedLoginShell
            ) {
                selectedLoginShell = it
                Settings.login_shell = it
            }
        }

        PreferenceGroup(heading = stringResource(strings.input_mode)) {
            InputModeOption(stringResource(strings.input_mode_default), stringResource(strings.input_mode_default_desc), InputMode.DEFAULT, selectedInputMode) {
                selectedInputMode = it
                Settings.input_mode = it
            }
            InputModeOption(stringResource(strings.input_mode_type_null), stringResource(strings.input_mode_type_null_desc), InputMode.TYPE_NULL, selectedInputMode) {
                selectedInputMode = it
                Settings.input_mode = it
            }
            InputModeOption(stringResource(strings.input_mode_visible_password), stringResource(strings.input_mode_visible_password_desc), InputMode.VISIBLE_PASSWORD, selectedInputMode) {
                selectedInputMode = it
                Settings.input_mode = it
            }
        }

        PreferenceGroup(heading = t("Language")) {
            WorkingModeOption(
                title = t("System default"),
                description = "Русский / English",
                selected = selectedLanguage == "system"
            ) {
                selectedLanguage = "system"
                Settings.app_language = "system"
                mainActivity.applyLanguage("system")
            }
            WorkingModeOption(
                title = "English",
                description = "English",
                selected = selectedLanguage == "en"
            ) {
                selectedLanguage = "en"
                Settings.app_language = "en"
                mainActivity.applyLanguage("en")
            }
            WorkingModeOption(
                title = "Русский",
                description = "Russian",
                selected = selectedLanguage == "ru"
            ) {
                selectedLanguage = "ru"
                Settings.app_language = "ru"
                mainActivity.applyLanguage("ru")
            }
        }

        PreferenceGroup(heading = t("Terminal behaviour")) {
            SettingsToggle(
                label = t("Keep screen on"),
                description = t("Keep screen on while the terminal is open"),
                showSwitch = true,
                default = keepScreenOn,
                sideEffect = {
                    keepScreenOn = it
                    Settings.keep_screen_on = it
                }
            )
            SettingsToggle(
                label = t("Reduce motion"),
                description = t("Disable the animated liquid background"),
                showSwitch = true,
                default = reduceMotion,
                sideEffect = {
                    reduceMotion = it
                    Settings.reduce_motion = it
                    com.rk.terminal.ui.theme.AppearanceState.refresh()
                }
            )
        }

        PreferenceGroup(heading = t("Distribution")) {
            SettingsCard(
                title = { Text(t("Installed distribution")) },
                description = {
                    Text(if (Settings.rootfs_distro == "arch") "Arch Linux ARM" else "Alpine Linux")
                },
                onClick = {}
            )
            SettingsCard(
                title = { Text(t("Reinstall root filesystem")) },
                description = {
                    Text(
                        if (resettingRootfs) {
                            t("Deleting…")
                        } else {
                            t("Deletes the installed Linux system and downloads it again")
                        }
                    )
                },
                isEnabled = !resettingRootfs,
                onClick = { showResetRootfs = true }
            )
        }

        PreferenceGroup(heading = t("Custom Sessions")) {
            customSessions.forEach { session ->
                SettingsCard(
                    title = { Text(session.name) },
                    description = { Text(session.shellPath) },
                    onClick = {},
                    endWidget = {
                        IconButton(onClick = {
                            CustomSessions.remove(session.id)
                            customSessions = CustomSessions.getAll()
                            defaultCustomId = CustomSessions.getDefaultId()
                            defaultIsCustom = Settings.default_is_custom
                        }) {
                            Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                        }
                    }
                )
            }
            SettingsCard(
                title = { Text(t("Add Custom Session")) },
                onClick = { showAddCustomSession = true },
                endWidget = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
        }

        PreferenceGroup {
            SettingsCard(
                title = { Text(stringResource(strings.customizations)) },
                description = { Text(t("Liquid glass, colours, wallpaper, terminal background and keyboard")) },
                onClick = { navController.navigate(MainActivityRoutes.Customization.route) },
                startWidget = {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                },
                endWidget = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            )
        }

        PreferenceGroup(heading = t("Server")) {
            val running = FileServer.running
            val lanUrl = if (running) FileServer.lanUrl() else null
            SettingsCard(
                title = { Text(if (running) t("Stop server") else t("Start server")) },
                description = {
                    Text(
                        when {
                            running && lanUrl != null -> lanUrl
                            running -> t("Not connected to Wi-Fi")
                            else -> t("Share files from your phone over Wi-Fi")
                        }
                    )
                },
                startWidget = {
                    Icon(
                        imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                },
                onClick = {
                    if (running) {
                        FileServer.stop()
                    } else {
                        serverError = FileServer.start()
                    }
                }
            )
            if (running) {
                SettingsCard(
                    title = { Text(t("Open in browser")) },
                    description = { Text(lanUrl ?: t("Not connected to Wi-Fi")) },
                    isEnabled = lanUrl != null,
                    startWidget = {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    },
                    onClick = {
                        if (lanUrl != null) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, lanUrl.toUri()))
                            }
                        }
                    }
                )
                SettingsCard(
                    title = { Text(t("Copy link")) },
                    description = { Text("${t("Requests served")}: ${FileServer.servedRequests}") },
                    isEnabled = lanUrl != null,
                    startWidget = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    },
                    onClick = {
                        if (lanUrl != null) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MakTerm", lanUrl))
                            Toast.makeText(context, t("Link copied"), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            SettingsCard(
                title = { Text(t("System status")) },
                description = {
                    Text(
                        buildString {
                            append("CPU: ${stats.cpuLoad}\n")
                            append("RAM: ${ProcessLauncher.sizeLabel(stats.ramUsed)} / ${ProcessLauncher.sizeLabel(stats.ramTotal)}\n")
                            append("${t("Disk")}: ${ProcessLauncher.sizeLabel(stats.diskUsed)} / ${ProcessLauncher.sizeLabel(stats.diskTotal)}\n")
                            append("${t("Battery")}: ")
                            if (stats.batteryPercent >= 0) append("${stats.batteryPercent}%") else append("—")
                            if (stats.charging) append(" · ${t("Charging")}")
                            append("\n${t("Uptime")}: ${stats.uptime}")
                            stats.lanAddress?.let { append("\nIP: $it") }
                        }
                    )
                },
                onClick = {}
            )
            SettingsCard(
                title = { Text(t("Port")) },
                description = {
                    Text(
                        if (running) "${Settings.server_port} · ${t("Stop the server to change this")}"
                        else "${Settings.server_port}"
                    )
                },
                isEnabled = !running,
                onClick = { showServerPort = true }
            )
            SettingsCard(
                title = { Text(t("Shared folder")) },
                description = {
                    Text(
                        if (running) "${Settings.server_root} · ${t("Stop the server to change this")}"
                        else Settings.server_root
                    )
                },
                isEnabled = !running,
                startWidget = {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                },
                onClick = { showServerRoot = true }
            )
            SettingsToggle(
                label = t("Require password"),
                description = t("Protect the file server with a password"),
                showSwitch = true,
                default = Settings.server_auth,
                sideEffect = {
                    Settings.server_auth = it
                    serverAuth = it
                }
            )
            if (serverAuth) {
                SettingsCard(
                    title = { Text(t("Password")) },
                    description = {
                        Text(
                            if (Settings.server_password.isBlank()) t("No password set")
                            else "•••••••• · ${t("Login")}: admin"
                        )
                    },
                    startWidget = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    },
                    onClick = { showServerPassword = true }
                )
            }
        }

        PreferenceGroup(heading = t("Hosting")) {
            SettingsCard(
                title = { Text(t("Add process")) },
                description = { Text(t("Run a Telegram bot, a script or any background command")) },
                startWidget = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                },
                onClick = {
                    editingHost = null
                    showHostEditor = true
                }
            )
            hostedProcesses.forEach { config ->
                val runtime = HostedProcessManager.runtime(config.id)
                val running = runtime?.running == true
                SettingsCard(
                    title = { Text(config.name) },
                    description = {
                        Text(
                            buildString {
                                append(ProcessLauncher.modeLabel(config.linux))
                                if (config.port > 0) append(":${config.port}")
                                append(" · ")
                                append(if (running) t("Running") else t("Stopped"))
                                if (config.keepAlive) append(" · ${t("Auto-restart")}")
                            }
                        )
                    },
                    startWidget = {
                        Icon(
                            imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    },
                    endWidget = {
                        Row(modifier = Modifier.padding(end = 4.dp)) {
                            IconButton(onClick = { showHostLog = config }) {
                                Icon(Icons.Outlined.Article, contentDescription = t("Logs"))
                            }
                            IconButton(onClick = {
                                editingHost = config
                                showHostEditor = true
                            }) {
                                Icon(Icons.Outlined.Edit, contentDescription = t("Edit"))
                            }
                            IconButton(onClick = { deleteHost = config }) {
                                Icon(Icons.Outlined.Delete, contentDescription = t("Delete"))
                            }
                        }
                    },
                    onClick = {
                        if (running) {
                            HostedProcessManager.stop(config.id)
                        } else {
                            HostedProcessManager.start(config)
                            HostService.start(context)
                        }
                    }
                )
            }
            if (hostedProcesses.isEmpty()) {
                SettingsCard(
                    title = { Text(t("No processes yet")) },
                    description = { Text(t("Add a command above to host it in the background")) },
                    onClick = {
                        editingHost = null
                        showHostEditor = true
                    }
                )
            }
        }

        PreferenceGroup(heading = t("Linux tools")) {
            PackageTool("Python", "python3 py3-pip", "python python-pip") {
                showTaskLog = true
                TaskRunner.run(context, t("Installing") + " Python", linuxInstallCommand("python3 py3-pip", "python python-pip"))
            }
            PackageTool("Node.js", "nodejs npm", "nodejs npm") {
                showTaskLog = true
                TaskRunner.run(context, t("Installing") + " Node.js", linuxInstallCommand("nodejs npm", "nodejs npm"))
            }
            PackageTool("Git", "git", "git") {
                showTaskLog = true
                TaskRunner.run(context, t("Installing") + " Git", linuxInstallCommand("git", "git"))
            }
            PackageTool("OpenSSH", "openssh", "openssh") {
                showTaskLog = true
                TaskRunner.run(context, t("Installing") + " OpenSSH", linuxInstallCommand("openssh", "openssh"))
            }
            PackageTool("FFmpeg", "ffmpeg", "ffmpeg") {
                showTaskLog = true
                TaskRunner.run(context, t("Installing") + " FFmpeg", linuxInstallCommand("ffmpeg", "ffmpeg"))
            }
            SettingsCard(
                title = { Text(t("Backup Linux system")) },
                description = { Text(t("Create a tar.gz archive in /sdcard/Download")) },
                onClick = {
                    val rootfs = context.localDir().child("alpine")
                    val output = "/sdcard/Download/MakTerm-rootfs-${System.currentTimeMillis()}.tar.gz"
                    val tar = if (File("/system/bin/tar").exists()) "/system/bin/tar" else "tar"
                    showTaskLog = true
                    TaskRunner.run(
                        context,
                        t("Backup Linux system"),
                        "echo 'Архивирую ${rootfs.absolutePath}...'; $tar -czf '$output' -C '${rootfs.absolutePath}' . && echo 'Готово: $output'",
                        linux = false
                    )
                }
            )
        }

        PreferenceGroup(heading = t("Updates")) {
            SettingsCard(
                title = { Text(t("Check for updates")) },
                description = {
                    Text(
                        when {
                            checkingUpdate -> t("Checking for updates…")
                            downloadingUpdate -> "${t("Downloading update…")} $updateProgress%"
                            updateRelease != null && updateUpToDate ->
                                "${t("Latest version installed")} · $displayVersion"
                            updateRelease != null ->
                                "${t("Update available:")} ${updateRelease?.version}"
                            else -> "${t("Current version:")} $displayVersion"
                        }
                    )
                },
                isEnabled = !checkingUpdate && !downloadingUpdate,
                onClick = { checkForUpdates() }
            )
            SettingsCard(
                title = { Text(t("GitHub repository")) },
                description = { Text(GitHubUpdater.REPO) },
                onClick = { GitHubUpdater.openReleasePage(context, "https://github.com/${GitHubUpdater.REPO}") }
            )
        }

        PreferenceGroup(heading = t("About")) {
            SettingsCard(
                title = { Text(t("MakTerm")) },
                description = { Text("${t("Material 3 liquid glass terminal")} · $displayVersion") },
                onClick = {}
            )
            SettingsCard(
                title = { Text(t("Rootless Linux")) },
                description = { Text(t("Alpine, Android and custom sessions via proot or chroot")) },
                onClick = {}
            )
        }

        PreferenceGroup {
            SettingsToggle(
                label = stringResource(strings.seccomp),
                description = stringResource(strings.seccomp_desc),
                showSwitch = true,
                default = Settings.seccomp,
                sideEffect = { Settings.seccomp = it }
            )

            SettingsToggle(
                label = stringResource(strings.all_file_access),
                description = stringResource(strings.all_file_access_desc),
                showSwitch = false,
                default = false,
                sideEffect = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
                    } else {
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    }
                    runCatching { context.startActivity(intent) }.onFailure {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    }
                }
            )
        }
    }

    if (showResetRootfs) {
        AlertDialog(
            onDismissRequest = { showResetRootfs = false },
            title = { Text(t("Reinstall root filesystem")) },
            text = {
                Text(t("All files inside the Linux system will be deleted. This cannot be undone."))
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetRootfs = false
                    resettingRootfs = true
                    settingsScope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { context.filesDir.child("alpine.tar.gz").delete() }
                            runCatching { context.localDir().child("alpine").deleteRecursively() }
                        }
                        Rootfs.isInstalled.value = false
                        resettingRootfs = false
                        mainActivity.recreate()
                    }
                }) { Text(t("Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { showResetRootfs = false }) { Text(t("Cancel")) }
            }
        )
    }

    if (showUpdateDialog) {
        val release = updateRelease
        AlertDialog(
            onDismissRequest = { if (!downloadingUpdate) showUpdateDialog = false },
            title = {
                Text(
                    when {
                        updateError != null -> t("Update failed")
                        updateUpToDate -> t("You are up to date")
                        else -> "${t("Update available:")} ${release?.version.orEmpty()}"
                    }
                )
            },
            text = {
                Column {
                    when {
                        updateError != null -> {
                            Text(updateError.orEmpty())
                        }
                        updateUpToDate -> {
                            Text("${t("Latest version installed")} · $displayVersion")
                        }
                        else -> {
                            val notes = release?.notes.orEmpty().trim()
                            if (notes.isNotEmpty()) {
                                Text(
                                    notes.take(1200),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            if (downloadingUpdate) {
                                Text("${t("Downloading update…")} $updateProgress%")
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { updateProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    "${t("Current version:")} $displayVersion → ${release?.version.orEmpty()}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (release?.apkUrl.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        t("This release has no APK attached. Open the release page to download it manually."),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    updateError != null -> TextButton(onClick = { checkForUpdates() }) {
                        Text(t("Retry"))
                    }
                    updateUpToDate -> TextButton(onClick = { showUpdateDialog = false }) {
                        Text(t("OK"))
                    }
                    else -> TextButton(
                        enabled = !downloadingUpdate && !release?.apkUrl.isNullOrBlank(),
                        onClick = {
                            val value = release
                            if (value != null) {
                                downloadAndInstall(value)
                            }
                        }
                    ) {
                        Text(
                            if (downloadingUpdate) t("Downloading…")
                            else t("Download and install")
                        )
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = !downloadingUpdate,
                        onClick = {
                            GitHubUpdater.openReleasePage(context, release?.pageUrl.orEmpty())
                        }
                    ) { Text(t("GitHub")) }
                    TextButton(
                        enabled = !downloadingUpdate,
                        onClick = { showUpdateDialog = false }
                    ) { Text(t("Later")) }
                }
            }
        )
    }

    if (showServerPort) {
        ServerFieldDialog(
            title = t("Port"),
            initialValue = Settings.server_port.toString(),
            label = t("Port"),
            helper = t("Use a port from 1024 to 65535"),
            numeric = true,
            validator = { value ->
                val number = value.trim().toIntOrNull()
                if (number == null || number !in FileServer.MIN_PORT..FileServer.MAX_PORT) {
                    t("Use a port from 1024 to 65535")
                } else null
            },
            onDismiss = { showServerPort = false },
            onSave = { value ->
                Settings.server_port = value.trim().toInt()
                showServerPort = false
            }
        )
    }

    if (showServerRoot) {
        ServerFieldDialog(
            title = t("Shared folder"),
            initialValue = Settings.server_root,
            label = t("Shared folder path"),
            helper = t("Absolute path, for example /sdcard"),
            validator = { value ->
                val path = value.trim()
                when {
                    path.isEmpty() -> t("Enter a folder path")
                    !File(path).isDirectory -> t("Folder not found")
                    !File(path).canRead() -> t("Folder is not readable")
                    else -> null
                }
            },
            onDismiss = { showServerRoot = false },
            onSave = { value ->
                Settings.server_root = value.trim()
                showServerRoot = false
            }
        )
    }

    if (showServerPassword) {
        ServerFieldDialog(
            title = t("Password"),
            initialValue = Settings.server_password,
            label = t("Password"),
            helper = t("Login: admin. Leave empty to disable protection."),
            password = true,
            onDismiss = { showServerPassword = false },
            onSave = { value ->
                Settings.server_password = value
                showServerPassword = false
            }
        )
    }

    serverError?.let { message ->
        AlertDialog(
            onDismissRequest = { serverError = null },
            title = { Text(t("Server error")) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { serverError = null }) { Text(t("OK")) }
            }
        )
    }

    if (showHostEditor) {
        HostProcessDialog(
            initial = editingHost,
            onDismiss = { showHostEditor = false },
            onSave = { process ->
                if (editingHost == null) {
                    HostedProcesses.add(process)
                } else {
                    HostedProcesses.update(process)
                }
                hostedProcesses = HostedProcesses.getAll()
                showHostEditor = false
            }
        )
    }

    showHostLog?.let { process ->
        HostLogDialog(process = process, onDismiss = { showHostLog = null })
    }

    deleteHost?.let { process ->
        AlertDialog(
            onDismissRequest = { deleteHost = null },
            title = { Text(t("Delete process?")) },
            text = { Text(t("The process will be stopped and removed from the list.")) },
            confirmButton = {
                TextButton(onClick = {
                    HostedProcessManager.stop(process.id)
                    HostedProcesses.remove(process.id)
                    hostedProcesses = HostedProcesses.getAll()
                    deleteHost = null
                }) { Text(t("Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { deleteHost = null }) { Text(t("Cancel")) }
            }
        )
    }

    if (showTaskLog) {
        AlertDialog(
            onDismissRequest = { if (!TaskRunner.running) showTaskLog = false },
            title = { Text(TaskRunner.title.ifBlank { t("Task") }) },
            text = {
                Column {
                    if (TaskRunner.running) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Box(modifier = Modifier.heightIn(max = 360.dp)) {
                        SelectionContainer {
                            Text(
                                TaskRunner.log.takeLast(20_000).ifBlank { t("Starting…") },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showTaskLog = false },
                    enabled = !TaskRunner.running
                ) { Text(t("Close")) }
            },
            dismissButton = {
                if (TaskRunner.running) {
                    TextButton(onClick = { TaskRunner.cancel() }) { Text(t("Cancel")) }
                }
            }
        )
    }

    if (showAddCustomSession) {
        CustomSessionDialog(
            onDismiss = { showAddCustomSession = false },
            onSave = { name, shellPath ->
                if (name.isNotBlank() && shellPath.isNotBlank()) {
                    CustomSessions.add(name, shellPath)
                    customSessions = CustomSessions.getAll()
                }
                showAddCustomSession = false
            }
        )
    }
}

@Composable
private fun WorkingModeOption(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    SettingsCard(
        title = { Text(title) },
        description = { Text(description) },
        startWidget = {
            RadioButton(
                modifier = Modifier.padding(start = 8.dp),
                selected = selected,
                onClick = onSelect
            )
        },
        onClick = onSelect
    )
}

@Composable
private fun InputModeOption(title: String, description: String, mode: Int, currentMode: Int, onSelect: (Int) -> Unit) {
    SettingsCard(
        title = { Text(title) },
        description = { Text(description) },
        startWidget = {
            RadioButton(
                modifier = Modifier.padding(start = 8.dp),
                selected = currentMode == mode,
                onClick = { onSelect(mode) }
            )
        },
        onClick = { onSelect(mode) }
    )
}

@Composable
private fun ExecModeOption(title: String, description: String, mode: ExecMode, currentMode: ExecMode?, onSelect: (ExecMode) -> Unit) {
    SettingsCard(
        title = { Text(title) },
        description = { Text(description) },
        startWidget = {
            RadioButton(
                modifier = Modifier.padding(start = 8.dp),
                selected = currentMode == mode,
                onClick = { onSelect(mode) }
            )
        },
        onClick = { onSelect(mode) }
    )
}

@Composable
private fun LoginShellOption(title: String, description: String, mode: Int, currentMode: Int, onSelect: (Int) -> Unit) {
    SettingsCard(
        title = { Text(title) },
        description = { Text(description) },
        startWidget = {
            RadioButton(
                modifier = Modifier.padding(start = 8.dp),
                selected = currentMode == mode,
                onClick = { onSelect(mode) }
            )
        },
        onClick = { onSelect(mode) }
    )
}

@Composable
private fun PackageTool(name: String, apkPackages: String, pacmanPackages: String, onClick: () -> Unit) {
    SettingsCard(
        title = { Text(name) },
        description = { Text(t("Install into the Linux system")) },
        startWidget = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp)
            )
        },
        onClick = onClick
    )
}

private fun linuxInstallCommand(apkPackages: String, pacmanPackages: String): String {
    return "if command -v apk >/dev/null 2>&1; then " +
        "apk add --no-cache $apkPackages; " +
        "elif command -v pacman >/dev/null 2>&1; then " +
        "pacman -Sy --noconfirm --needed $pacmanPackages; " +
        "else echo 'No apk or pacman found'; exit 1; fi"
}

@Composable
private fun HostProcessDialog(
    initial: HostedProcess?,
    onDismiss: () -> Unit,
    onSave: (HostedProcess) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var command by remember(initial) { mutableStateOf(initial?.command.orEmpty()) }
    var workDir by remember(initial) { mutableStateOf(initial?.workDir ?: "/sdcard") }
    var port by remember(initial) { mutableStateOf(initial?.port?.takeIf { it > 0 }?.toString().orEmpty()) }
    var linux by remember(initial) { mutableStateOf(initial?.linux ?: true) }
    var keepAlive by remember(initial) { mutableStateOf(initial?.keepAlive ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) t("New process") else t("Edit process")) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(t("Name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it; error = null },
                    label = { Text(t("Command")) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = workDir,
                    onValueChange = { workDir = it },
                    label = { Text(t("Working directory")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { value -> port = value.filter { it.isDigit() } },
                    label = { Text(t("Port (optional)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = linux, onCheckedChange = { linux = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(t("Run in Linux"))
                        Text(
                            t("Uncheck to run in the Android shell"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = keepAlive, onCheckedChange = { keepAlive = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(t("Auto-restart on crash"))
                        Text(
                            t("Restart up to 5 times after an unexpected exit"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    error = t("Enter a name")
                    return@TextButton
                }
                if (command.isBlank()) {
                    error = t("Enter a command")
                    return@TextButton
                }
                val portValue = port.toIntOrNull() ?: 0
                if (portValue !in 0..65535) {
                    error = t("Use a port from 1024 to 65535")
                    return@TextButton
                }
                onSave(
                    HostedProcess(
                        id = initial?.id.orEmpty(),
                        name = name.trim(),
                        command = command.trim(),
                        workDir = workDir.trim().ifBlank { "/sdcard" },
                        linux = linux,
                        port = portValue,
                        keepAlive = keepAlive
                    )
                )
            }) { Text(t("Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel")) }
        }
    )
}

@Composable
private fun HostLogDialog(process: HostedProcess, onDismiss: () -> Unit) {
    var log by remember(process.id) { mutableStateOf("") }
    LaunchedEffect(process.id) {
        while (true) {
            log = withContext(Dispatchers.IO) { HostedProcessManager.log(process.id) }
            delay(500)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${process.name} · ${t("Logs")}") },
        text = {
            Box(modifier = Modifier.heightIn(max = 360.dp)) {
                SelectionContainer {
                    Text(
                        log.takeLast(20_000).ifBlank { t("No logs yet") },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(t("Close")) }
        },
        dismissButton = {
            TextButton(onClick = {
                HostedProcessManager.clearLog(process.id)
                log = ""
            }) {
                Text(t("Clear"))
            }
        }
    )
}

@Composable
private fun ServerFieldDialog(
    title: String,
    initialValue: String,
    label: String,
    helper: String? = null,
    numeric: Boolean = false,
    password: Boolean = false,
    validator: (String) -> String? = { null },
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                label = { Text(label) },
                singleLine = true,
                isError = error != null,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
                ),
                supportingText = {
                    val hint = error ?: helper
                    if (hint != null) Text(hint)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = validator(value)
                if (problem != null) error = problem else onSave(value)
            }) { Text(t("Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("Cancel")) }
        }
    )
}

@Composable
private fun SettingsHero() {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(LocalGlassRadius.current),
        tint = MaterialTheme.colorScheme.primaryContainer,
        alpha = 0.36f,
        borderAlpha = 0.55f
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Brush.linearGradient(listOf(primary, tertiary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text(
                    text = t("MakTerm"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = t("Liquid glass · Material 3 · rootless Linux"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
