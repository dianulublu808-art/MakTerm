package com.rk.terminal.ui.screens.terminal

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doOnTextChanged
import com.rk.libcommons.dpToPx
import com.rk.settings.Settings
import com.rk.terminal.service.SessionService
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.theme.AppearanceState
import com.rk.terminal.ui.screens.terminal.virtualkeys.*
import com.termux.view.TerminalView

@Composable
fun TerminalViewLayout(
    viewModel: TerminalViewModel,
    mainActivity: MainActivity,
    sessionBinder: SessionService.SessionBinder,
    modifier: Modifier = Modifier
) {
    AppearanceState.revision
    val terminalPadding = Settings.terminal_padding.dp
    // Tracks what was already pushed into the Android view. Re-applying the
    // font, typeface and colour palette on every recomposition invalidates the
    // whole terminal and makes typing feel laggy.
    val appliedStyle = remember { AppliedTerminalStyle() }

    Column(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    viewModel.setTerminalView(this)
                    setTextSize(dpToPx(Settings.terminal_font_size.toFloat(), ctx))
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    val client = TerminalBackEnd(this, mainActivity)
                    val service = sessionBinder.getService()

                    val session = sessionBinder.getSession(service.currentSession.value.first)
                        ?: run {
                            val custom = service.currentCustomSession
                            if (custom != null) {
                                val pendingCommand = MkSession.buildCustomPendingCommand(ctx, custom)
                                sessionBinder.createSession(
                                    service.currentSession.value.first,
                                    client,
                                    service.currentSession.value.second,
                                    pendingCommand
                                )
                            } else {
                                sessionBinder.createSession(
                                    service.currentSession.value.first,
                                    client,
                                    Settings.working_Mode
                                )
                            }
                        }

                    session.updateTerminalSessionClient(client)
                    attachSession(session)
                    setTerminalViewClient(client)
                    setTypeface(TerminalUtils.typeface)

                    post {
                        keepScreenOn = true
                        requestFocus()
                        isFocusableInTouchMode = true
                        TerminalUtils.applyColors(this)
                        appliedStyle.revision = AppearanceState.revision
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = terminalPadding, vertical = terminalPadding * 0.5f),
            update = { view ->
                val textSize = dpToPx(Settings.terminal_font_size.toFloat(), view.context)
                if (appliedStyle.textSize != textSize) {
                    view.setTextSize(textSize)
                    appliedStyle.textSize = textSize
                }
                if (appliedStyle.typeface !== TerminalUtils.typeface) {
                    view.setTypeface(TerminalUtils.typeface)
                    appliedStyle.typeface = TerminalUtils.typeface
                }
                if (appliedStyle.revision != AppearanceState.revision) {
                    TerminalUtils.applyColors(view)
                    appliedStyle.revision = AppearanceState.revision
                }
            }
        )

        if (viewModel.showVirtualKeys) {
            VirtualKeysPager(viewModel)
        }
    }
}

private class AppliedTerminalStyle {
    var textSize: Int = -1
    var typeface: android.graphics.Typeface? = null
    var revision: Int = -1
}

@Composable
private fun VirtualKeysPager(viewModel: TerminalViewModel) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()

    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().height(75.dp)
    ) { page ->
        when (page) {
            0 -> {
                AndroidView(
                    factory = { ctx ->
                        VirtualKeysView(ctx, null).apply {
                            viewModel.setVirtualKeysView(this)
                            virtualKeysViewClient = viewModel.terminalView?.mTermSession?.let {
                                VirtualKeysListener(it)
                            }
                            buttonTextColor = onSurfaceColor
                            reload(VirtualKeysInfo(Settings.virtual_keys_string, "", VirtualKeysConstants.CONTROL_CHARS_ALIASES))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(75.dp),
                    update = { view ->
                        view.buttonTextColor = onSurfaceColor
                    }
                )
            }
            1 -> {
                var text by rememberSaveable { mutableStateOf("") }
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(75.dp),
                    factory = { ctx ->
                        EditText(ctx).apply {
                            maxLines = 1
                            isSingleLine = true
                            imeOptions = EditorInfo.IME_ACTION_DONE
                            setTextColor(onSurfaceColor)
                            setHintTextColor(onSurfaceVariantColor)
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            doOnTextChanged { t, _, _, _ -> text = t.toString() }
                            setOnEditorActionListener { _, actionId, _ ->
                                if (actionId == EditorInfo.IME_ACTION_DONE) {
                                    val terminal = viewModel.terminalView
                                    if (text.isEmpty()) {
                                        terminal?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                                        terminal?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                                    } else {
                                        terminal?.currentSession?.write(text)
                                        setText("")
                                    }
                                    true
                                } else false
                            }
                        }
                    },
                    update = { editText ->
                        editText.setTextColor(onSurfaceColor)
                        editText.setHintTextColor(onSurfaceVariantColor)
                        if (editText.text.toString() != text) {
                            editText.setText(text)
                            editText.setSelection(text.length)
                        }
                    }
                )
            }
        }
    }
}
