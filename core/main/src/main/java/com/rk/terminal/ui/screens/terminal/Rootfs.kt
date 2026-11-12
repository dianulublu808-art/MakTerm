package com.rk.terminal.ui.screens.terminal

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.child
import com.rk.libcommons.localDir
import com.rk.settings.Settings
import java.io.File

enum class ExecMode(val value: Int) {
    CHROOT(0),
    PROOT(1);

    companion object {
        fun fromInt(v: Int): ExecMode? = entries.firstOrNull { it.value == v }
    }
}

object Rootfs {
    var isInstalled = mutableStateOf(false)
    var execMode = mutableStateOf(ExecMode.fromInt(Settings.exec_mode))

    fun setExecMode(mode: ExecMode) {
        execMode.value = mode
        Settings.exec_mode = mode.value
    }

    fun checkInstallation(context: Context) {
        isInstalled.value = isRootfsInstalled(context)
    }

    fun isRootfsInstalled(context: Context): Boolean {
        val alpineDir = context.localDir().child("alpine")
        // /bin/sh is an absolute symlink inside the rootfs, so test real files.
        val hasOsRelease = alpineDir.child("etc/os-release").exists() ||
            alpineDir.child("etc/alpine-release").exists()
        val isExtracted = hasOsRelease && (
            alpineDir.child("bin/busybox").exists() ||
                alpineDir.child("usr/bin/bash").exists() ||
                alpineDir.child("bin/bash").exists() ||
                alpineDir.child("usr/bin/pacman").exists()
            )

        // A truncated download must not be treated as an installed rootfs,
        // otherwise the app never offers to download it again.
        val minArchiveSize = if (Settings.rootfs_distro == "arch") {
            500L * 1024 * 1024
        } else {
            1024L * 1024
        }
        val archive = context.filesDir.child("alpine.tar.gz")
        val isArchivePresent = archive.exists() && archive.length() >= minArchiveSize
        return isExtracted || isArchivePresent
    }
}
