package com.rk.terminal.ui.screens.terminal

/**
 * ANSI palettes for the terminal. Keeping them in code makes the presets instant and avoids
 * writing extra files into the rootfs.
 */
data class TerminalPalette(
    val name: String,
    val colors: IntArray,
    val foreground: Int,
    val background: Int,
    val cursor: Int
) {
    val isDefault: Boolean get() = colors.isEmpty()
}

object TerminalPalettes {
    private fun c(value: Long): Int = value.toInt()

    /** Termux' original ANSI colours, used when the user goes back to "Default". */
    val DEFAULT_COLORS = intArrayOf(
        c(0xFF000000), c(0xFFCD0000), c(0xFF00CD00), c(0xFFCDCD00),
        c(0xFF0000EE), c(0xFFCD00CD), c(0xFF00CDCD), c(0xFFE5E5E5),
        c(0xFF7F7F7F), c(0xFFFF0000), c(0xFF00FF00), c(0xFFFFFF00),
        c(0xFF5C5CFF), c(0xFFFF00FF), c(0xFF00FFFF), c(0xFFFFFFFF)
    )

    val DEFAULT = TerminalPalette(
        name = "Default",
        colors = IntArray(0),
        foreground = 0,
        background = 0,
        cursor = 0
    )

    val DRACULA = TerminalPalette(
        name = "Dracula",
        colors = intArrayOf(
            c(0xFF21222C), c(0xFFFF5555), c(0xFF50FA7B), c(0xFFF1FA8C),
            c(0xFFBD93F9), c(0xFFFF79C6), c(0xFF8BE9FD), c(0xFFF8F8F2),
            c(0xFF6272A4), c(0xFFFF6E6E), c(0xFF69FF94), c(0xFFFFFFA5),
            c(0xFFD6ACFF), c(0xFFFF92DF), c(0xFFA4FFFF), c(0xFFFFFFFF)
        ),
        foreground = c(0xFFF8F8F2),
        background = c(0xFF282A36),
        cursor = c(0xFFF8F8F2)
    )

    val NORD = TerminalPalette(
        name = "Nord",
        colors = intArrayOf(
            c(0xFF3B4252), c(0xFFBF616A), c(0xFFA3BE8C), c(0xFFEBCB8B),
            c(0xFF81A1C1), c(0xFFB48EAD), c(0xFF88C0D0), c(0xFFE5E9F0),
            c(0xFF4C566A), c(0xFFBF616A), c(0xFFA3BE8C), c(0xFFEBCB8B),
            c(0xFF81A1C1), c(0xFFB48EAD), c(0xFF8FBCBB), c(0xFFECEFF4)
        ),
        foreground = c(0xFFD8DEE9),
        background = c(0xFF2E3440),
        cursor = c(0xFF88C0D0)
    )

    val TOKYO_NIGHT = TerminalPalette(
        name = "Tokyo Night",
        colors = intArrayOf(
            c(0xFF15161E), c(0xFFF7768E), c(0xFF9ECE6A), c(0xFFE0AF68),
            c(0xFF7AA2F7), c(0xFFBB9AF7), c(0xFF7DCFFF), c(0xFFA9B1D6),
            c(0xFF414868), c(0xFFF7768E), c(0xFF9ECE6A), c(0xFFE0AF68),
            c(0xFF7AA2F7), c(0xFFBB9AF7), c(0xFF7DCFFF), c(0xFFC0CAF5)
        ),
        foreground = c(0xFFC0CAF5),
        background = c(0xFF1A1B26),
        cursor = c(0xFF7AA2F7)
    )

    val SOLARIZED_DARK = TerminalPalette(
        name = "Solarized Dark",
        colors = intArrayOf(
            c(0xFF073642), c(0xFFDC322F), c(0xFF859900), c(0xFFB58900),
            c(0xFF268BD2), c(0xFFD33682), c(0xFF2AA198), c(0xFFEEE8D5),
            c(0xFF002B36), c(0xFFCB4B16), c(0xFF586E75), c(0xFF657B83),
            c(0xFF839496), c(0xFF6C71C4), c(0xFF93A1A1), c(0xFFFDF6E3)
        ),
        foreground = c(0xFF839496),
        background = c(0xFF002B36),
        cursor = c(0xFF93A1A1)
    )

    val GRUVBOX_DARK = TerminalPalette(
        name = "Gruvbox Dark",
        colors = intArrayOf(
            c(0xFF282828), c(0xFFCC241D), c(0xFF98971A), c(0xFFD79921),
            c(0xFF458588), c(0xFFB16286), c(0xFF689D6A), c(0xFFA89984),
            c(0xFF928374), c(0xFFFB4934), c(0xFFB8BB26), c(0xFFFABD2F),
            c(0xFF83A598), c(0xFFD3869B), c(0xFF8EC07C), c(0xFFEBDBB2)
        ),
        foreground = c(0xFFEBDBB2),
        background = c(0xFF282828),
        cursor = c(0xFFEBDBB2)
    )

    val ONE_DARK = TerminalPalette(
        name = "One Dark",
        colors = intArrayOf(
            c(0xFF282C34), c(0xFFE06C75), c(0xFF98C379), c(0xFFE5C07B),
            c(0xFF61AFEF), c(0xFFC678DD), c(0xFF56B6C2), c(0xFFABB2BF),
            c(0xFF5C6370), c(0xFFE06C75), c(0xFF98C379), c(0xFFE5C07B),
            c(0xFF61AFEF), c(0xFFC678DD), c(0xFF56B6C2), c(0xFFFFFFFF)
        ),
        foreground = c(0xFFABB2BF),
        background = c(0xFF282C34),
        cursor = c(0xFF528BFF)
    )

    val CATPPUCCIN = TerminalPalette(
        name = "Catppuccin Mocha",
        colors = intArrayOf(
            c(0xFF45475A), c(0xFFF38BA8), c(0xFFA6E3A1), c(0xFFF9E2AF),
            c(0xFF89B4FA), c(0xFFF5C2E7), c(0xFF94E2D5), c(0xFFBAC2DE),
            c(0xFF585B70), c(0xFFF38BA8), c(0xFFA6E3A1), c(0xFFF9E2AF),
            c(0xFF89B4FA), c(0xFFF5C2E7), c(0xFF94E2D5), c(0xFFA6ADC8)
        ),
        foreground = c(0xFFCDD6F4),
        background = c(0xFF1E1E2E),
        cursor = c(0xFFF5E0DC)
    )

    val all = listOf(
        DEFAULT,
        DRACULA,
        NORD,
        TOKYO_NIGHT,
        SOLARIZED_DARK,
        GRUVBOX_DARK,
        ONE_DARK,
        CATPPUCCIN
    )

    fun byName(name: String): TerminalPalette =
        all.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
}
