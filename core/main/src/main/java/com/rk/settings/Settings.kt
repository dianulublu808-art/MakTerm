package com.rk.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.rk.libcommons.application
import com.rk.terminal.ui.screens.settings.WorkingMode
import com.rk.terminal.ui.screens.settings.InputMode
import com.rk.terminal.ui.screens.settings.LoginShell

object Settings {
    //Boolean
    var seccomp
        get() = Preference.getBoolean(key = "seccomp", default = false)
        set(value) = Preference.setBoolean(key = "seccomp",value)
    var amoled
        get() = Preference.getBoolean(key = "oled", default = false)
        set(value) = Preference.setBoolean(key = "oled",value)
    var monet
        get() = Preference.getBoolean(
            key = "monet",
            default = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )
        set(value) = Preference.setBoolean(key = "monet",value)

    var selected_palette: String
        get() = Preference.getString(key = "selected_palette", default = "CATPPUCCIN")
        set(value) = Preference.setString(key = "selected_palette", value)

    var theme_palette: com.rk.terminal.ui.theme.ThemePalette
        get() = com.rk.terminal.ui.theme.ThemePalette.fromName(selected_palette)
        set(value) { selected_palette = value.name }
    var ignore_storage_permission
        get() = Preference.getBoolean(key = "ignore_storage_permission",default = false)
        set(value) = Preference.setBoolean(key = "ignore_storage_permission",value)
    var github
        get() = Preference.getBoolean(key = "github", default = true)
        set(value) = Preference.setBoolean(key = "github",value)


    var follow_system_theme
        get() = Preference.getBoolean(key = "follow_system_theme", default = true)
        set(value) = Preference.setBoolean(key = "follow_system_theme", value)

    var dark_mode
        get() = Preference.getBoolean(key = "dark_mode", default = false)
        set(value) = Preference.setBoolean(key = "dark_mode", value)

    var default_night_mode: Int
        get() {
            return if (follow_system_theme) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            } else if (dark_mode) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        }
        set(value) {
            when (value) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> {
                    follow_system_theme = true
                }
                AppCompatDelegate.MODE_NIGHT_YES -> {
                    follow_system_theme = false
                    dark_mode = true
                }
                AppCompatDelegate.MODE_NIGHT_NO -> {
                    follow_system_theme = false
                    dark_mode = false
                }
            }
            Preference.setInt(key = "default_night_mode", value)
        }

    var terminal_font_size
        get() = Preference.getInt(key = "terminal_font_size", default = 13)
        set(value) = Preference.setInt(key = "terminal_font_size",value)

    var wallTransparency
        get() = Preference.getFloat(key = "wallTransparency", default = 1f)
        set(value) = Preference.setFloat(key = "wallTransparency",value)

    var working_Mode
        get() = Preference.getInt(key = "workingMode", default = WorkingMode.ALPINE)
        set(value) = Preference.setInt(key = "workingMode",value)

    var input_mode
        get() = Preference.getInt(key = "input_mode", default = InputMode.DEFAULT)
        set(value) = Preference.setInt(key = "input_mode", value)

    var exec_mode
        get() = Preference.getInt(key = "exec_mode", default = -1)
        set(value) = Preference.setInt(key = "exec_mode", value)

    var login_shell
        get() = Preference.getInt(key = "login_shell", default = LoginShell.DISTRO)
        set(value) = Preference.setInt(key = "login_shell", value)

    var default_is_custom
        get() = Preference.getBoolean(key = "default_is_custom", default = false)
        set(value) = Preference.setBoolean(key = "default_is_custom", value)

    var custom_background_name
        get() = Preference.getString(key = "custom_bg_name", default = "No Image Selected")
        set(value) = Preference.setString(key = "custom_bg_name",value)
    var custom_font_name
        get() = Preference.getString(key = "custom_ttf_name", default = "No Font Selected")
        set(value) = Preference.setString(key = "custom_ttf_name",value)

    var background_blur
        get() = Preference.getFloat(key = "background_blur", default = 0f)
        set(value) = Preference.setFloat(key = "background_blur", value)

    var blackTextColor
        get() = Preference.getBoolean(key = "blackText", default = false)
        set(value) = Preference.setBoolean(key = "blackText",value)

    // ---------------------------------------------------------------------------------------------
    // Liquid glass appearance
    // ---------------------------------------------------------------------------------------------

    /** 0 = animated liquid gradient, 1 = solid theme colour, 2 = custom image. */
    var app_background_style
        get() = Preference.getInt(key = "app_background_style", default = 0)
        set(value) = Preference.setInt(key = "app_background_style", value)

    var app_background_blur
        get() = Preference.getFloat(key = "app_background_blur", default = 18f)
        set(value) = Preference.setFloat(key = "app_background_blur", value)

    var app_background_dim
        get() = Preference.getFloat(key = "app_background_dim", default = 0.24f)
        set(value) = Preference.setFloat(key = "app_background_dim", value)

    var app_background_name
        get() = Preference.getString(key = "app_background_name", default = "Liquid gradient")
        set(value) = Preference.setString(key = "app_background_name", value)

    var app_background_color
        get() = Preference.getInt(key = "app_background_color", default = 0xFF0B0E16.toInt())
        set(value) = Preference.setInt(key = "app_background_color", value)

    var glass_enabled
        get() = Preference.getBoolean(key = "glass_enabled", default = true)
        set(value) = Preference.setBoolean(key = "glass_enabled", value)

    var glass_alpha
        get() = Preference.getFloat(key = "glass_alpha", default = 0.58f)
        set(value) = Preference.setFloat(key = "glass_alpha", value)

    var glass_corner_radius
        get() = Preference.getFloat(key = "glass_corner_radius", default = 26f)
        set(value) = Preference.setFloat(key = "glass_corner_radius", value)

    var glass_border_alpha
        get() = Preference.getFloat(key = "glass_border_alpha", default = 0.45f)
        set(value) = Preference.setFloat(key = "glass_border_alpha", value)

    var liquid_speed
        get() = Preference.getFloat(key = "liquid_speed", default = 1f)
        set(value) = Preference.setFloat(key = "liquid_speed", value)

    var liquid_intensity
        get() = Preference.getFloat(key = "liquid_intensity", default = 0.62f)
        set(value) = Preference.setFloat(key = "liquid_intensity", value)

    var use_custom_accent
        get() = Preference.getBoolean(key = "use_custom_accent", default = false)
        set(value) = Preference.setBoolean(key = "use_custom_accent", value)

    var custom_accent_color
        get() = Preference.getInt(key = "custom_accent_color", default = 0xFF7DC4E4.toInt())
        set(value) = Preference.setInt(key = "custom_accent_color", value)

    // ---------------------------------------------------------------------------------------------
    // Terminal appearance
    // ---------------------------------------------------------------------------------------------

    /** 0 = follow app background, 1 = custom image, 2 = solid colour, 3 = accent gradient. */
    var terminal_background_style
        get() = Preference.getInt(key = "terminal_background_style", default = 0)
        set(value) = Preference.setInt(key = "terminal_background_style", value)

    var terminal_bg_color
        get() = Preference.getInt(key = "terminal_bg_color", default = 0xFF101014.toInt())
        set(value) = Preference.setInt(key = "terminal_bg_color", value)

    var terminal_bg_opacity
        get() = Preference.getFloat(key = "terminal_bg_opacity", default = 0.86f)
        set(value) = Preference.setFloat(key = "terminal_bg_opacity", value)

    var terminal_bg_dim
        get() = Preference.getFloat(key = "terminal_bg_dim", default = 0.22f)
        set(value) = Preference.setFloat(key = "terminal_bg_dim", value)

    var terminal_bg_blur
        get() = Preference.getFloat(key = "terminal_bg_blur", default = 0f)
        set(value) = Preference.setFloat(key = "terminal_bg_blur", value)

    /** 0 = automatic, 1 = always light, 2 = always dark, 3 = custom colour. */
    var terminal_text_mode
        get() = Preference.getInt(key = "terminal_text_mode", default = 0)
        set(value) = Preference.setInt(key = "terminal_text_mode", value)

    var terminal_fg_color
        get() = Preference.getInt(key = "terminal_fg_color", default = 0xFFE8E8EF.toInt())
        set(value) = Preference.setInt(key = "terminal_fg_color", value)

    var terminal_cursor_color
        get() = Preference.getInt(key = "terminal_cursor_color", default = 0xFF7DC4E4.toInt())
        set(value) = Preference.setInt(key = "terminal_cursor_color", value)

    var terminal_padding
        get() = Preference.getFloat(key = "terminal_padding", default = 8f)
        set(value) = Preference.setFloat(key = "terminal_padding", value)

    var terminal_palette
        get() = Preference.getString(key = "terminal_palette", default = "Default")
        set(value) = Preference.setString(key = "terminal_palette", value)

    var terminal_color_scheme_applies_background
        get() = Preference.getBoolean(key = "terminal_scheme_bg", default = false)
        set(value) = Preference.setBoolean(key = "terminal_scheme_bg", value)

    /** "system", "en" or "ru". */
    var app_language
        get() = Preference.getString(key = "app_language", default = "system")
        set(value) = Preference.setString(key = "app_language", value)

    var reduce_motion
        get() = Preference.getBoolean(key = "reduce_motion", default = false)
        set(value) = Preference.setBoolean(key = "reduce_motion", value)

    var keep_screen_on
        get() = Preference.getBoolean(key = "keep_screen_on", default = false)
        set(value) = Preference.setBoolean(key = "keep_screen_on", value)

    /** Installed root filesystem: "alpine" or "arch". */
    var rootfs_distro
        get() = Preference.getString(key = "rootfs_distro", default = "alpine")
        set(value) = Preference.setString(key = "rootfs_distro", value)

    fun resetAppearance() {
        app_background_style = 0
        app_background_blur = 18f
        app_background_dim = 0.24f
        app_background_color = 0xFF0B0E16.toInt()
        glass_enabled = true
        glass_alpha = 0.58f
        glass_corner_radius = 26f
        glass_border_alpha = 0.45f
        liquid_speed = 1f
        liquid_intensity = 0.62f
        use_custom_accent = false
        custom_accent_color = 0xFF7DC4E4.toInt()
        selected_palette = "CATPPUCCIN"
        wallTransparency = 1f
        background_blur = 0f
        blackTextColor = false
        terminal_background_style = 0
        terminal_bg_color = 0xFF101014.toInt()
        terminal_bg_opacity = 0.86f
        terminal_bg_dim = 0.22f
        terminal_bg_blur = 0f
        terminal_text_mode = 0
        terminal_fg_color = 0xFFE8E8EF.toInt()
        terminal_cursor_color = 0xFF7DC4E4.toInt()
        terminal_padding = 8f
        terminal_palette = "Default"
        terminal_font_size = 13
    }

    var bell
        get() = Preference.getBoolean(key = "bell", default = false)
        set(value) = Preference.setBoolean(key = "bell",value)

    var vibrate
        get() = Preference.getBoolean(key = "vibrate", default = true)
        set(value) = Preference.setBoolean(key = "vibrate",value)

    var toolbar
        get() = Preference.getBoolean(key = "toolbar", default = true)
        set(value) = Preference.setBoolean(key = "toolbar",value)

    var statusBar
        get() = Preference.getBoolean(key = "statusBar", default = true)
        set(value) = Preference.setBoolean(key = "statusBar",value)

    var horizontal_statusBar
        get() = Preference.getBoolean(key = "horizontal_statusBar", default = true)
        set(value) = Preference.setBoolean(key = "horizontal_statusBar",value)

    var toolbar_in_horizontal
        get() = Preference.getBoolean(key = "toolbar_h", default = true)
        set(value) = Preference.setBoolean(key = "toolbar_h",value)

    var virtualKeys
        get() = Preference.getBoolean(key = "virtualKeys", default = true)
        set(value) = Preference.setBoolean(key = "virtualKeys",value)

    var hide_soft_keyboard_if_hwd
        get() = Preference.getBoolean(key = "force_soft_keyboard", default = true)
        set(value) = Preference.setBoolean(key = "force_soft_keyboard",value)

    var shortcuts_enabled
        get() = Preference.getBoolean(key = "shortcuts_enabled", default = true)
        set(value) = Preference.setBoolean(key = "shortcuts_enabled", value)

    const val default_virtual_keys = "[" +
        "\n  [\"ESC\", {\"key\": \"/\", \"popup\": \"\\\\\"}, {\"key\": \"-\", \"popup\": \"|\"}, \"HOME\", \"UP\", \"END\", \"PGUP\"]," +
        "\n  [\"TAB\", \"CTRL\", \"ALT\", \"LEFT\", \"DOWN\", \"RIGHT\", \"PGDN\"]" +
        "\n]"

    var virtual_keys_string: String
        get() = Preference.getString(key = "virtual_keys_string", default = default_virtual_keys)
        set(value) = Preference.setString(key = "virtual_keys_string", value)

    fun getShortcutBinding(action: com.rk.terminal.ui.screens.terminal.ShortcutAction): com.rk.terminal.ui.screens.terminal.ShortcutBinding {
        val raw = Preference.getString(key = action.prefKey, default = action.default.serialize())
        return com.rk.terminal.ui.screens.terminal.ShortcutBinding.deserialize(raw)
    }

    fun setShortcutBinding(action: com.rk.terminal.ui.screens.terminal.ShortcutAction, binding: com.rk.terminal.ui.screens.terminal.ShortcutBinding) {
        Preference.setString(key = action.prefKey, value = binding.serialize())
    }



}

object Preference {
    private var sharedPreferences: SharedPreferences = application!!.getSharedPreferences("Settings", Context.MODE_PRIVATE)

    private val stringCache = hashMapOf<String, String?>()
    private val boolCache = hashMapOf<String, Boolean>()
    private val intCache = hashMapOf<String, Int>()
    private val longCache = hashMapOf<String, Long>()
    private val floatCache = hashMapOf<String, Float>()

    @SuppressLint("ApplySharedPref")
    fun clearData(){
        sharedPreferences.edit().clear().commit()
    }

    fun removeKey(key: String){
        if (sharedPreferences.contains(key).not()){
            return
        }

        sharedPreferences.edit().remove(key).apply()

        if (stringCache.containsKey(key)){
            stringCache.remove(key)
            return
        }

        if (boolCache.containsKey(key)){
            boolCache.remove(key)
            return
        }

        if (intCache.containsKey(key)){
            intCache.remove(key)
            return
        }

        if (longCache.containsKey(key)){
            longCache.remove(key)
            return
        }

        if (floatCache.containsKey(key)){
            floatCache.remove(key)
            return
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        runCatching {
            return boolCache[key] ?: sharedPreferences.getBoolean(key, default)
                .also { boolCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setBoolean(key, default)
        }
        return default
    }

    fun setBoolean(key: String, value: Boolean) {
        boolCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putBoolean(key, value)
            editor.apply()
        }.onFailure { it.printStackTrace() }
    }

    fun getString(key: String, default: String): String {
        runCatching {
            return stringCache[key] ?: sharedPreferences.getString(key, default)!!
                .also { stringCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setString(key, default)
        }
        return default
    }
    fun setString(key: String, value: String?) {
        stringCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putString(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }

    }

    fun getInt(key: String, default: Int): Int {
        runCatching {
            return intCache[key] ?: sharedPreferences.getInt(key, default)
                .also { intCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setInt(key, default)
        }
        return default
    }

    fun setInt(key: String, value: Int) {
        intCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putInt(key, value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }

    }

    fun getLong(key: String, default: Long): Long {
        runCatching {
            return longCache[key] ?: sharedPreferences.getLong(key, default)
                .also { longCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setLong(key, default)
        }
        return default
    }

    fun setLong(key: String, value: Long) {
        longCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putLong(key,value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }
    }

    fun getFloat(key: String, default: Float): Float {
        runCatching {
            return floatCache[key] ?: sharedPreferences.getFloat(key, default)
                .also { floatCache[key] = it }
        }.onFailure {
            it.printStackTrace()
            setFloat(key, default)
        }
        return default
    }

    fun setFloat(key: String, value: Float) {
        floatCache[key] = value
        runCatching {
            val editor = sharedPreferences.edit()
            editor.putFloat(key,value)
            editor.apply()
        }.onFailure {
            it.printStackTrace()
        }
    }

}
