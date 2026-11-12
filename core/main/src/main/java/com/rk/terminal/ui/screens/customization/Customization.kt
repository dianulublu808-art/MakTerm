package com.rk.terminal.ui.screens.customization

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.components.compose.glass.GlassSurface
import com.rk.components.compose.glass.GlassSwatch
import com.rk.components.compose.glass.LocalGlassRadius
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.libcommons.*
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainViewModel
import com.rk.terminal.ui.components.AccentColorPicker
import com.rk.terminal.ui.components.ColorPickerDialog
import com.rk.terminal.ui.components.SettingsToggle
import com.rk.terminal.ui.screens.settings.SettingsCard
import com.rk.terminal.ui.screens.terminal.*
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.ui.screens.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.ui.theme.AppearanceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.RoundingMode
import java.text.DecimalFormat
import com.rk.i18n.t

private const val MIN_TEXT_SIZE = 10f
private const val MAX_TEXT_SIZE = 20f

private const val APP_BG_LIQUID = 0
private const val APP_BG_SOLID = 1
private const val APP_BG_IMAGE = 2

private const val TERM_BG_FOLLOW = 0
private const val TERM_BG_IMAGE = 1
private const val TERM_BG_SOLID = 2
private const val TERM_BG_GRADIENT = 3

private const val TEXT_MODE_AUTO = 0
private const val TEXT_MODE_LIGHT = 1
private const val TEXT_MODE_DARK = 2
private const val TEXT_MODE_CUSTOM = 3

@Composable
fun Customization(
    mainActivity: com.rk.terminal.ui.activities.terminal.MainActivity,
    navController: NavController,
    mainViewModel: MainViewModel = viewModel(mainActivity),
    terminalViewModel: TerminalViewModel = viewModel(mainActivity)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isSystemDark = isSystemInDarkTheme()
    val isDarkActive = if (mainViewModel.followSystemTheme) isSystemDark else mainViewModel.isDarkMode

    var resetTick by remember { mutableIntStateOf(0) }

    var appBgStyle by remember { mutableIntStateOf(Settings.app_background_style) }
    var appBgColor by remember { mutableIntStateOf(Settings.app_background_color) }
    var appBgBlur by remember { mutableFloatStateOf(Settings.app_background_blur) }
    var appBgDim by remember { mutableFloatStateOf(Settings.app_background_dim) }
    var liquidSpeed by remember { mutableFloatStateOf(Settings.liquid_speed) }
    var liquidIntensity by remember { mutableFloatStateOf(Settings.liquid_intensity) }

    var glassEnabled by remember { mutableStateOf(Settings.glass_enabled) }
    var glassAlpha by remember { mutableFloatStateOf(Settings.glass_alpha) }
    var glassRadius by remember { mutableFloatStateOf(Settings.glass_corner_radius) }
    var glassBorder by remember { mutableFloatStateOf(Settings.glass_border_alpha) }

    var useCustomAccent by remember { mutableStateOf(Settings.use_custom_accent) }
    var accentColor by remember { mutableIntStateOf(Settings.custom_accent_color) }

    var terminalStyle by remember { mutableIntStateOf(Settings.terminal_background_style) }
    var terminalBgColor by remember { mutableIntStateOf(Settings.terminal_bg_color) }
    var terminalBgOpacity by remember { mutableFloatStateOf(Settings.terminal_bg_opacity) }
    var terminalBgDim by remember { mutableFloatStateOf(Settings.terminal_bg_dim) }
    var terminalBgBlur by remember { mutableFloatStateOf(Settings.terminal_bg_blur) }
    var terminalTextMode by remember { mutableIntStateOf(Settings.terminal_text_mode) }
    var terminalFg by remember { mutableIntStateOf(Settings.terminal_fg_color) }
    var terminalCursor by remember { mutableIntStateOf(Settings.terminal_cursor_color) }
    var terminalPadding by remember { mutableFloatStateOf(Settings.terminal_padding) }
    var terminalPalette by remember { mutableStateOf(Settings.terminal_palette) }
    var terminalFontSize by remember { mutableFloatStateOf(Settings.terminal_font_size.toFloat()) }

    var showAccentPicker by remember { mutableStateOf(false) }
    var showAppBgColorPicker by remember { mutableStateOf(false) }
    var showTerminalBgPicker by remember { mutableStateOf(false) }
    var showTerminalFgPicker by remember { mutableStateOf(false) }
    var showCursorPicker by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val applyTerminalColors: () -> Unit = {
        terminalViewModel.terminalView?.let { TerminalUtils.applyColors(it) }
    }

    val appBgFile = context.filesDir.child("app_background")
    var appBgExists by remember { mutableStateOf(appBgFile.exists() && appBgFile.canRead()) }
    var appBgName by remember { mutableStateOf(Settings.app_background_name) }
    val appBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = context.getFileNameFromUri(uri)?.takeIf { it.isNotBlank() } ?: "Custom image"
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        appBgFile.createFileIfNot()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            appBgFile.outputStream().use { output -> input.copyTo(output) }
                        } != null
                    }.getOrDefault(false)
                }
                if (copied) {
                    Settings.app_background_name = name
                    Settings.app_background_style = APP_BG_IMAGE
                    appBgName = name
                    appBgExists = true
                    appBgStyle = APP_BG_IMAGE
                    AppearanceState.refresh()
                } else {
                    Toast.makeText(context, t("Could not load image"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val terminalBgFile = context.filesDir.child("background")
    var terminalBgExists by remember { mutableStateOf(terminalBgFile.exists() && terminalBgFile.canRead()) }
    var terminalBgName by remember { mutableStateOf(Settings.custom_background_name) }
    val noImageSelected = stringResource(strings.no_image_selected)
    val terminalBgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = context.getFileNameFromUri(uri)?.takeIf { it.isNotBlank() } ?: "Custom image"
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        terminalBgFile.createFileIfNot()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            terminalBgFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        BitmapFactory.decodeFile(terminalBgFile.absolutePath)
                    }.getOrNull()
                }
                if (bitmap != null) {
                    val dominant = Palette.from(bitmap).generate()
                        .getDominantColor(android.graphics.Color.WHITE)
                    val bright = ColorUtils.calculateLuminance(dominant) > 0.5f

                    Settings.custom_background_name = name
                    Settings.blackTextColor = bright
                    Settings.terminal_background_style = TERM_BG_IMAGE
                    TerminalUtils.hasCustomBackground.value = true
                    TerminalUtils.darkText.value = bright
                    terminalViewModel.bitmap = bitmap.asImageBitmap()
                    terminalBgName = name
                    terminalBgExists = true
                    terminalStyle = TERM_BG_IMAGE
                    applyTerminalColors()
                    AppearanceState.refresh()
                } else {
                    Toast.makeText(context, t("Could not load image"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    PreferenceLayout(
        label = stringResource(strings.customizations),
        onBack = { navController.popBackStack() }
    ) {
        key(resetTick) {
            HeroCard()

            PreferenceGroup(heading = stringResource(strings.app_theme)) {
                SettingsToggle(
                    label = stringResource(strings.follow_system_theme),
                    description = stringResource(strings.follow_system_theme_desc),
                    showSwitch = true,
                    default = mainViewModel.followSystemTheme,
                    sideEffect = {
                        Settings.follow_system_theme = it
                        mainViewModel.followSystemTheme = it
                        AppearanceState.refresh()
                    }
                )

                if (!mainViewModel.followSystemTheme) {
                    SettingsToggle(
                        label = stringResource(strings.dark_mode),
                        description = stringResource(strings.dark_mode_desc),
                        showSwitch = true,
                        default = mainViewModel.isDarkMode,
                        sideEffect = {
                            Settings.dark_mode = it
                            mainViewModel.isDarkMode = it
                            AppearanceState.refresh()
                        }
                    )
                }

                if (isDarkActive) {
                    SettingsToggle(
                        label = stringResource(strings.amoled),
                        description = stringResource(strings.amoled_desc),
                        showSwitch = true,
                        default = mainViewModel.isAmoled,
                        sideEffect = {
                            Settings.amoled = it
                            mainViewModel.isAmoled = it
                            AppearanceState.refresh()
                        }
                    )
                }

                SettingsToggle(
                    label = stringResource(strings.monet),
                    description = stringResource(strings.monet_desc),
                    showSwitch = true,
                    default = mainViewModel.isMonet,
                    sideEffect = {
                        Settings.monet = it
                        mainViewModel.isMonet = it
                        AppearanceState.refresh()
                    }
                )

                if (!mainViewModel.isMonet) {
                    AccentColorPicker(
                        selectedPalette = mainViewModel.themePalette,
                        isDarkTheme = isDarkActive,
                        onPaletteSelected = {
                            Settings.theme_palette = it
                            mainViewModel.themePalette = it
                            AppearanceState.refresh()
                        }
                    )
                }

                SettingsToggle(
                    label = t("Custom accent colour"),
                    description = t("Override the palette with your own colour"),
                    showSwitch = true,
                    default = useCustomAccent,
                    sideEffect = {
                        useCustomAccent = it
                        Settings.use_custom_accent = it
                        AppearanceState.refresh()
                    }
                )

                if (useCustomAccent) {
                    ColorRow(
                        title = t("Accent colour"),
                        description = t("Used by buttons, highlights and the liquid background"),
                        color = Color(accentColor)
                    ) {
                        showAccentPicker = true
                    }
                }
            }

            PreferenceGroup(heading = t("App background"), showDividers = false) {
                ChoiceChips(
                    title = t("Background style"),
                    options = listOf(
                        APP_BG_LIQUID to "Liquid",
                        APP_BG_SOLID to "Solid",
                        APP_BG_IMAGE to "Image"
                    ),
                    selected = appBgStyle
                ) { style ->
                    appBgStyle = style
                    Settings.app_background_style = style
                    AppearanceState.refresh()
                }

                when (appBgStyle) {
                    APP_BG_SOLID -> {
                        ColorRow(
                            title = t("Background colour"),
                            description = t("Solid colour behind every screen"),
                            color = Color(appBgColor)
                        ) {
                            showAppBgColorPicker = true
                        }
                    }

                    APP_BG_IMAGE -> {
                        PreferenceTemplate(
                            modifier = Modifier.clickable { appBgLauncher.launch("image/*") },
                            title = { Text(t("Wallpaper")) },
                            description = { Text(appBgName) },
                            endWidget = {
                                if (appBgExists) {
                                    IconButton(onClick = {
                                        appBgFile.delete()
                                        Settings.app_background_name = "Liquid gradient"
                                        Settings.app_background_style = APP_BG_LIQUID
                                        appBgName = "Liquid gradient"
                                        appBgExists = false
                                        appBgStyle = APP_BG_LIQUID
                                        AppearanceState.refresh()
                                    }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = t("Remove wallpaper"))
                                    }
                                }
                            }
                        )
                        SettingSlider(
                            title = t("Wallpaper blur"),
                            value = appBgBlur,
                            valueText = "${appBgBlur.toInt()} dp",
                            valueRange = 0f..40f,
                            steps = 39,
                            onValueChange = {
                                appBgBlur = it
                                Settings.app_background_blur = it
                                AppearanceState.refresh()
                            }
                        )
                        SettingSlider(
                            title = t("Wallpaper dim"),
                            value = appBgDim,
                            valueText = "${(appBgDim * 100).toInt()}%",
                            valueRange = 0f..0.9f,
                            onValueChange = {
                                appBgDim = it
                                Settings.app_background_dim = it
                                AppearanceState.refresh()
                            }
                        )
                    }
                }

                SettingSlider(
                    title = t("Liquid speed"),
                    value = liquidSpeed,
                    valueText = DecimalFormat("0.0").apply { roundingMode = RoundingMode.HALF_UP }.format(liquidSpeed) + "x",
                    valueRange = 0.15f..3f,
                    onValueChange = {
                        liquidSpeed = it
                        Settings.liquid_speed = it
                        AppearanceState.refresh()
                    }
                )
                SettingSlider(
                    title = t("Liquid intensity"),
                    value = liquidIntensity,
                    valueText = "${(liquidIntensity * 100).toInt()}%",
                    valueRange = 0f..1f,
                    onValueChange = {
                        liquidIntensity = it
                        Settings.liquid_intensity = it
                        AppearanceState.refresh()
                    }
                )
            }

            PreferenceGroup(heading = t("Glass"), showDividers = false) {
                SettingsToggle(
                    label = t("Liquid glass"),
                    description = t("Translucent, rounded surfaces across the app"),
                    showSwitch = true,
                    default = glassEnabled,
                    sideEffect = {
                        glassEnabled = it
                        Settings.glass_enabled = it
                        AppearanceState.refresh()
                    }
                )
                if (glassEnabled) {
                    SettingSlider(
                        title = t("Glass opacity"),
                        value = glassAlpha,
                        valueText = "${(glassAlpha * 100).toInt()}%",
                        valueRange = 0.08f..0.95f,
                        onValueChange = {
                            glassAlpha = it
                            Settings.glass_alpha = it
                            AppearanceState.refresh()
                        }
                    )
                    SettingSlider(
                        title = t("Corner radius"),
                        value = glassRadius,
                        valueText = "${glassRadius.toInt()} dp",
                        valueRange = 8f..40f,
                        steps = 31,
                        onValueChange = {
                            glassRadius = it
                            Settings.glass_corner_radius = it
                            AppearanceState.refresh()
                        }
                    )
                    SettingSlider(
                        title = t("Rim brightness"),
                        value = glassBorder,
                        valueText = "${(glassBorder * 100).toInt()}%",
                        valueRange = 0f..1f,
                        onValueChange = {
                            glassBorder = it
                            Settings.glass_border_alpha = it
                            AppearanceState.refresh()
                        }
                    )
                }
            }

            PreferenceGroup(heading = t("Terminal text"), showDividers = false) {
                SettingSlider(
                    title = stringResource(strings.text_size),
                    value = terminalFontSize,
                    valueText = terminalFontSize.toInt().toString(),
                    valueRange = MIN_TEXT_SIZE..MAX_TEXT_SIZE,
                    steps = (MAX_TEXT_SIZE - MIN_TEXT_SIZE).toInt() - 1,
                    onValueChange = {
                        terminalFontSize = it
                        Settings.terminal_font_size = it.toInt()
                        terminalViewModel.terminalView?.setTextSize(dpToPx(it, context))
                    }
                )

                FontSection(terminalViewModel)

                SettingSlider(
                    title = t("Terminal padding"),
                    value = terminalPadding,
                    valueText = "${terminalPadding.toInt()} dp",
                    valueRange = 0f..24f,
                    steps = 23,
                    onValueChange = {
                        terminalPadding = it
                        Settings.terminal_padding = it
                        AppearanceState.refresh()
                    }
                )

                ChoiceChips(
                    title = t("Text colour"),
                    options = listOf(
                        TEXT_MODE_AUTO to "Auto",
                        TEXT_MODE_LIGHT to "Light",
                        TEXT_MODE_DARK to "Dark",
                        TEXT_MODE_CUSTOM to "Custom"
                    ),
                    selected = terminalTextMode
                ) { mode ->
                    terminalTextMode = mode
                    Settings.terminal_text_mode = mode
                    applyTerminalColors()
                    AppearanceState.refresh()
                }

                if (terminalTextMode == TEXT_MODE_CUSTOM) {
                    ColorRow(
                        title = t("Custom text colour"),
                        description = t("Foreground used by the terminal"),
                        color = Color(terminalFg)
                    ) {
                        showTerminalFgPicker = true
                    }
                }

                ColorRow(
                    title = t("Cursor colour"),
                    description = t("Blinking cursor and selection handles"),
                    color = Color(terminalCursor)
                ) {
                    showCursorPicker = true
                }

                PaletteChips(
                    selected = terminalPalette,
                    onSelected = { palette ->
                        terminalPalette = palette.name
                        Settings.terminal_palette = palette.name
                        applyTerminalColors()
                        AppearanceState.refresh()
                    }
                )
            }

            PreferenceGroup(heading = t("Terminal background"), showDividers = false) {
                ChoiceChips(
                    title = t("Background style"),
                    options = listOf(
                        TERM_BG_FOLLOW to "App",
                        TERM_BG_IMAGE to "Image",
                        TERM_BG_SOLID to "Colour",
                        TERM_BG_GRADIENT to "Gradient"
                    ),
                    selected = terminalStyle
                ) { style ->
                    terminalStyle = style
                    Settings.terminal_background_style = style
                    if (style == TERM_BG_IMAGE) {
                        TerminalUtils.hasCustomBackground.value = terminalBgExists
                    }
                    applyTerminalColors()
                    AppearanceState.refresh()
                }

                when (terminalStyle) {
                    TERM_BG_IMAGE -> {
                        PreferenceTemplate(
                            modifier = Modifier.clickable { terminalBgLauncher.launch("image/*") },
                            title = { Text(stringResource(strings.custom_background)) },
                            description = { Text(if (terminalBgExists) terminalBgName else noImageSelected) },
                            endWidget = {
                                if (terminalBgExists) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { terminalBgFile.delete() }
                                            Settings.custom_background_name = noImageSelected
                                            Settings.blackTextColor = !isDarkActive
                                            TerminalUtils.hasCustomBackground.value = false
                                            TerminalUtils.darkText.value = !isDarkActive
                                            terminalViewModel.bitmap = null
                                            terminalBgExists = false
                                            terminalBgName = noImageSelected
                                            terminalStyle = TERM_BG_FOLLOW
                                            Settings.terminal_background_style = TERM_BG_FOLLOW
                                            applyTerminalColors()
                                            AppearanceState.refresh()
                                        }
                                    }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = t("Remove image"))
                                    }
                                }
                            }
                        )
                        SettingSlider(
                            title = t("Wallpaper alpha"),
                            value = terminalViewModel.wallAlpha,
                            valueText = "${(terminalViewModel.wallAlpha * 100).toInt()}%",
                            valueRange = 0f..1f,
                            onValueChange = {
                                terminalViewModel.wallAlpha = it
                                Settings.wallTransparency = it
                            }
                        )
                        SettingSlider(
                            title = t("Wallpaper blur"),
                            value = terminalBgBlur,
                            valueText = "${terminalBgBlur.toInt()} dp",
                            valueRange = 0f..40f,
                            steps = 39,
                            onValueChange = {
                                terminalBgBlur = it
                                terminalViewModel.backgroundBlur = it
                                Settings.background_blur = it
                                Settings.terminal_bg_blur = it
                            }
                        )
                        SettingSlider(
                            title = t("Dim"),
                            value = terminalBgDim,
                            valueText = "${(terminalBgDim * 100).toInt()}%",
                            valueRange = 0f..0.9f,
                            onValueChange = {
                                terminalBgDim = it
                                Settings.terminal_bg_dim = it
                                AppearanceState.refresh()
                            }
                        )
                    }

                    TERM_BG_SOLID -> {
                        ColorRow(
                            title = t("Terminal colour"),
                            description = t("Solid colour behind the terminal"),
                            color = Color(terminalBgColor)
                        ) {
                            showTerminalBgPicker = true
                        }
                        SettingSlider(
                            title = t("Opacity"),
                            value = terminalBgOpacity,
                            valueText = "${(terminalBgOpacity * 100).toInt()}%",
                            valueRange = 0f..1f,
                            onValueChange = {
                                terminalBgOpacity = it
                                Settings.terminal_bg_opacity = it
                                AppearanceState.refresh()
                            }
                        )
                    }

                    TERM_BG_GRADIENT -> {
                        SettingSlider(
                            title = t("Gradient dim"),
                            value = terminalBgDim,
                            valueText = "${(terminalBgDim * 100).toInt()}%",
                            valueRange = 0f..0.75f,
                            onValueChange = {
                                terminalBgDim = it
                                Settings.terminal_bg_dim = it
                                AppearanceState.refresh()
                            }
                        )
                    }
                }
            }

            PreferenceGroup(heading = t("Interface")) {
                SettingsToggle(
                    label = stringResource(strings.statusbar),
                    description = stringResource(strings.statusbar_desc),
                    showSwitch = true,
                    default = Settings.statusBar,
                    sideEffect = {
                        Settings.statusBar = it
                        mainViewModel.showStatusBar = it
                        AppearanceState.refresh()
                    }
                )

                SettingsToggle(
                    label = stringResource(strings.horizontal_statusbar),
                    description = stringResource(strings.horizontal_statusbar_desc),
                    showSwitch = true,
                    default = Settings.horizontal_statusBar,
                    sideEffect = {
                        Settings.horizontal_statusBar = it
                        mainViewModel.horizontalStatusBar = it
                        AppearanceState.refresh()
                    }
                )

                ToolbarSection(terminalViewModel)

                SettingsToggle(
                    label = stringResource(strings.horizontal_titlebar),
                    description = stringResource(strings.horizontal_titlebar_desc),
                    showSwitch = true,
                    isEnabled = terminalViewModel.showToolbar,
                    default = Settings.toolbar_in_horizontal,
                    sideEffect = {
                        Settings.toolbar_in_horizontal = it
                        terminalViewModel.showHorizontalToolbar = it
                    }
                )

                SettingsToggle(
                    label = stringResource(strings.virtual_keys),
                    description = stringResource(strings.virtual_keys_desc),
                    showSwitch = true,
                    default = Settings.virtualKeys,
                    sideEffect = {
                        Settings.virtualKeys = it
                        terminalViewModel.showVirtualKeys = it
                    }
                )

                var showVirtualKeysEdit by remember { mutableStateOf(false) }

                if (showVirtualKeysEdit) {
                    VirtualKeysEditDialog(
                        currentKeys = Settings.virtual_keys_string,
                        onDismiss = { showVirtualKeysEdit = false },
                        onConfirm = { newKeys ->
                            Settings.virtual_keys_string = newKeys
                            terminalViewModel.virtualKeysView?.reload(
                                VirtualKeysInfo(
                                    newKeys,
                                    "",
                                    VirtualKeysConstants.CONTROL_CHARS_ALIASES
                                )
                            )
                            showVirtualKeysEdit = false
                        }
                    )
                }

                SettingsToggle(
                    isEnabled = terminalViewModel.showVirtualKeys,
                    label = stringResource(strings.edit_virtual_keys),
                    description = stringResource(strings.edit_virtual_keys_desc),
                    showSwitch = false,
                    default = false,
                    sideEffect = { showVirtualKeysEdit = true }
                )

                SettingsToggle(
                    label = stringResource(strings.hide_soft_keyboard),
                    description = stringResource(strings.hide_soft_keyboard_desc),
                    showSwitch = true,
                    default = Settings.hide_soft_keyboard_if_hwd,
                    sideEffect = { Settings.hide_soft_keyboard_if_hwd = it }
                )

                SettingsToggle(
                    label = stringResource(strings.bell),
                    description = stringResource(strings.bell_desc),
                    showSwitch = true,
                    default = Settings.bell,
                    sideEffect = { Settings.bell = it }
                )

                SettingsToggle(
                    label = stringResource(strings.vibrate),
                    description = stringResource(strings.vibrate_desc),
                    showSwitch = true,
                    default = Settings.vibrate,
                    sideEffect = { Settings.vibrate = it }
                )
            }

            ShortcutSection()

            PreferenceGroup {
                SettingsCard(
                    title = { Text(t("Reset appearance")) },
                    description = { Text(t("Restore the default liquid glass look and terminal colours")) },
                    onClick = { showResetDialog = true }
                )
            }
        }
    }

    if (showAccentPicker) {
        ColorPickerDialog(
            title = t("Accent colour"),
            initialColor = accentColor,
            onDismiss = { showAccentPicker = false },
            onConfirm = { color ->
                accentColor = color
                Settings.custom_accent_color = color
                Settings.use_custom_accent = true
                useCustomAccent = true
                showAccentPicker = false
                AppearanceState.refresh()
            }
        )
    }

    if (showAppBgColorPicker) {
        ColorPickerDialog(
            title = t("App background colour"),
            initialColor = appBgColor,
            onDismiss = { showAppBgColorPicker = false },
            onConfirm = { color ->
                appBgColor = color
                Settings.app_background_color = color
                showAppBgColorPicker = false
                AppearanceState.refresh()
            }
        )
    }

    if (showTerminalBgPicker) {
        ColorPickerDialog(
            title = t("Terminal colour"),
            initialColor = terminalBgColor,
            onDismiss = { showTerminalBgPicker = false },
            onConfirm = { color ->
                terminalBgColor = color
                Settings.terminal_bg_color = color
                Settings.terminal_background_style = TERM_BG_SOLID
                terminalStyle = TERM_BG_SOLID
                showTerminalBgPicker = false
                applyTerminalColors()
                AppearanceState.refresh()
            }
        )
    }

    if (showTerminalFgPicker) {
        ColorPickerDialog(
            title = t("Terminal text colour"),
            initialColor = terminalFg,
            onDismiss = { showTerminalFgPicker = false },
            onConfirm = { color ->
                terminalFg = color
                Settings.terminal_fg_color = color
                Settings.terminal_text_mode = TEXT_MODE_CUSTOM
                terminalTextMode = TEXT_MODE_CUSTOM
                showTerminalFgPicker = false
                applyTerminalColors()
                AppearanceState.refresh()
            }
        )
    }

    if (showCursorPicker) {
        ColorPickerDialog(
            title = t("Cursor colour"),
            initialColor = terminalCursor,
            onDismiss = { showCursorPicker = false },
            onConfirm = { color ->
                terminalCursor = color
                Settings.terminal_cursor_color = color
                showCursorPicker = false
                applyTerminalColors()
                AppearanceState.refresh()
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(t("Reset appearance?")) },
            text = { Text(t("Your wallpaper and font files are kept. Every appearance option returns to its default value.")) },
            confirmButton = {
                TextButton(onClick = {
                    Settings.resetAppearance()

                    appBgStyle = APP_BG_LIQUID
                    appBgColor = 0xFF0B0E16.toInt()
                    appBgBlur = 18f
                    appBgDim = 0.24f
                    liquidSpeed = 1f
                    liquidIntensity = 0.62f
                    glassEnabled = true
                    glassAlpha = 0.58f
                    glassRadius = 26f
                    glassBorder = 0.45f
                    useCustomAccent = false
                    accentColor = 0xFF7DC4E4.toInt()
                    terminalStyle = TERM_BG_FOLLOW
                    terminalBgColor = 0xFF101014.toInt()
                    terminalBgOpacity = 0.86f
                    terminalBgDim = 0.22f
                    terminalBgBlur = 0f
                    terminalTextMode = TEXT_MODE_AUTO
                    terminalFg = 0xFFE8E8EF.toInt()
                    terminalCursor = 0xFF7DC4E4.toInt()
                    terminalPadding = 8f
                    terminalPalette = "Default"
                    terminalFontSize = 13f

                    terminalViewModel.wallAlpha = Settings.wallTransparency
                    terminalViewModel.backgroundBlur = Settings.terminal_bg_blur
                    terminalViewModel.bitmap = null
                    TerminalUtils.hasCustomBackground.value = false
                    TerminalUtils.darkText.value = false
                    applyTerminalColors()
                    showResetDialog = false
                    resetTick++
                    AppearanceState.refresh()
                }) {
                    Text(t("Reset"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(strings.cancel))
                }
            }
        )
    }
}

@Composable
private fun HeroCard() {
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
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(primary, tertiary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = t("Liquid glass studio"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = t("Make MakTerm truly yours — colours, glass, wallpaper and terminal."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChoiceChips(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
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
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun ColorRow(
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    PreferenceTemplate(
        modifier = Modifier.clickable(onClick = onClick),
        title = { Text(title) },
        description = { Text(description) },
        endWidget = {
            GlassSwatch(
                color = color,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(32.dp)
            )
        }
    )
}

@Composable
private fun PaletteChips(
    selected: String,
    onSelected: (TerminalPalette) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = t("ANSI palette"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalPalettes.all.forEach { palette ->
                FilterChip(
                    selected = palette.name.equals(selected, ignoreCase = true),
                    onClick = { onSelected(palette) },
                    label = { Text(palette.name) }
                )
            }
        }
    }
}

@Composable
private fun FontSection(viewModel: TerminalViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontFile = context.filesDir.child("font.ttf")
    var fontExists by remember { mutableStateOf(fontFile.exists() && fontFile.canRead()) }
    val noFontSelected = stringResource(strings.no_font_selected)
    var fontName by remember { mutableStateOf(if (!fontExists) noFontSelected else Settings.custom_font_name) }

    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val loadedName = withContext(Dispatchers.IO) {
                try {
                    val name = context.getFileNameFromUri(uri)?.takeIf { it.isNotBlank() } ?: "font.ttf"
                    val input = context.contentResolver.openInputStream(uri)
                    if (input == null) {
                        return@withContext null
                    }
                    input.use { stream ->
                        fontFile.outputStream().use { output ->
                            stream.copyTo(output)
                        }
                    }
                    Settings.custom_font_name = name
                    name
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            if (loadedName != null) {
                fontName = loadedName
                fontExists = true
                try {
                    viewModel.setFont(Typeface.createFromFile(fontFile))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, t("Failed to load font"), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, t("Failed to load font"), Toast.LENGTH_LONG).show()
            }
        }
    }

    PreferenceTemplate(
        modifier = Modifier.clickable {
            fontLauncher.launch("font/ttf")
        },
        title = { Text(stringResource(strings.custom_font)) },
        description = { Text(fontName) },
        endWidget = {
            if (fontExists) {
                IconButton(onClick = {
                    scope.launch {
                        fontFile.delete()
                        fontName = noFontSelected
                        Settings.custom_font_name = noFontSelected
                        viewModel.setFont(Typeface.MONOSPACE)
                        fontExists = false
                    }
                }) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = t("delete"))
                }
            }
        }
    )
}

@Composable
private fun ToolbarSection(viewModel: TerminalViewModel) {
    val context = LocalContext.current
    val attentionTitle = stringResource(strings.attention)
    val toolbarWarning = stringResource(strings.toolbar_warning)
    val cancelStr = stringResource(strings.cancel)

    val toggleToolbar: (Boolean) -> Unit = { checked ->
        if (!checked && viewModel.showToolbar) {
            MaterialAlertDialogBuilder(context).apply {
                setTitle(attentionTitle)
                setMessage(toolbarWarning)
                setPositiveButton(t("OK")) { _, _ ->
                    Settings.toolbar = false
                    viewModel.showToolbar = false
                }
                setNegativeButton(cancelStr, null)
                show()
            }
        } else {
            Settings.toolbar = checked
            viewModel.showToolbar = checked
        }
    }

    PreferenceSwitch(
        checked = viewModel.showToolbar,
        onCheckedChange = toggleToolbar,
        label = stringResource(strings.titlebar),
        description = stringResource(strings.titlebar_desc),
        onClick = { toggleToolbar(!viewModel.showToolbar) }
    )
}

@Composable
private fun ShortcutSection() {
    PreferenceGroup(heading = stringResource(strings.keyboard_shortcuts)) {
        var shortcutsEnabled by remember { mutableStateOf(Settings.shortcuts_enabled) }
        var showCaptureFor by remember { mutableStateOf<ShortcutAction?>(null) }

        SettingsToggle(
            label = stringResource(strings.keyboard_shortcuts),
            description = stringResource(strings.keyboard_shortcuts_desc),
            showSwitch = true,
            default = Settings.shortcuts_enabled,
            sideEffect = {
                Settings.shortcuts_enabled = it
                shortcutsEnabled = it
            }
        )

        ShortcutAction.entries.forEach { action ->
            val binding = Settings.getShortcutBinding(action)
            val labelRes = when (action) {
                ShortcutAction.PASTE -> strings.shortcut_paste
                ShortcutAction.NEW_SESSION -> strings.shortcut_new_session
                ShortcutAction.CLOSE_SESSION -> strings.shortcut_close_session
                ShortcutAction.SWITCH_SESSION_PREV -> strings.shortcut_switch_prev
                ShortcutAction.SWITCH_SESSION_NEXT -> strings.shortcut_switch_next
            }
            val descRes = when (action) {
                ShortcutAction.PASTE -> strings.shortcut_paste_desc
                ShortcutAction.NEW_SESSION -> strings.shortcut_new_session_desc
                ShortcutAction.CLOSE_SESSION -> strings.shortcut_close_session_desc
                ShortcutAction.SWITCH_SESSION_PREV -> strings.shortcut_switch_prev_desc
                ShortcutAction.SWITCH_SESSION_NEXT -> strings.shortcut_switch_next_desc
            }
            SettingsToggle(
                isEnabled = shortcutsEnabled,
                label = stringResource(labelRes),
                description = "${stringResource(descRes)} (${binding.toDisplayString()})",
                showSwitch = false,
                default = false,
                sideEffect = { showCaptureFor = action },
            )
        }

        if (showCaptureFor != null) {
            ShortcutCaptureDialog(
                action = showCaptureFor!!,
                onDismiss = { showCaptureFor = null },
                onConfirm = { binding ->
                    Settings.setShortcutBinding(showCaptureFor!!, binding)
                    showCaptureFor = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VirtualKeysEditDialog(
    currentKeys: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentKeys) }
    var isError by remember { mutableStateOf(false) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(strings.edit_virtual_keys),
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        isError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 300.dp),
                    label = { Text(t("Layout JSON")) },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = stringResource(strings.invalid_json),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(strings.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isValidJsonArray(text)) {
                                onConfirm(text)
                            } else {
                                isError = true
                            }
                        }
                    ) {
                        Text(stringResource(strings.apply))
                    }
                }
            }
        }
    }
}

private fun isValidJsonArray(json: String): Boolean {
    return try {
        org.json.JSONArray(json)
        true
    } catch (e: Exception) {
        false
    }
}
