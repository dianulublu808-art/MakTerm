package com.rk.server

import android.content.Context
import com.rk.libcommons.child
import com.rk.libcommons.localBinDir
import com.rk.libcommons.localLibDir
import com.rk.settings.Settings
import com.rk.terminal.App
import com.rk.terminal.BuildConfig
import com.rk.terminal.ui.screens.terminal.ExecMode
import com.rk.terminal.ui.screens.terminal.Rootfs
import java.io.File
import java.util.Locale

/**
 * Builds a process that runs either in the Android shell or inside the
 * installed Linux root filesystem (through the same init-host wrapper used by
 * terminal sessions). Shared by the process host and one-shot tasks.
 */
object ProcessLauncher {

    fun create(
        context: Context,
        command: String,
        linux: Boolean,
        workDir: String,
        id: String
    ): Process {
        val wrapped = wrapWorkDir(workDir, command)
        val hostDir = File(context.filesDir, "host").apply { mkdirs() }
        val args = if (linux) {
            val initHost = context.localBinDir().child(
                if (Rootfs.execMode.value == ExecMode.CHROOT) "init-host-chroot" else "init-host"
            )
            // Write the command to a script under PREFIX. The chroot wrapper
            // passes its arguments through `su -c`, which loses shell quoting,
            // so a file path is the only reliable way to hand over a command
            // that contains spaces, quotes or semicolons.
            val script = File(hostDir, "host-${id.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.sh")
            script.writeText("#!/bin/sh\n$wrapped\n")
            script.setReadable(true, false)
            script.setExecutable(true, false)
            arrayOf(initHost.absolutePath, "sh", script.absolutePath)
        } else {
            arrayOf("/system/bin/sh", "-c", wrapped)
        }

        val builder = ProcessBuilder(*args)
            .redirectErrorStream(true)
            .directory(hostDir)
        builder.environment().putAll(environment(context, id))
        return builder.start()
    }

    fun wrapWorkDir(workDir: String, command: String): String {
        val dir = workDir.trim()
        if (dir.isEmpty()) return command
        val escaped = dir.replace("'", "'\\''")
        return "cd '$escaped' 2>/dev/null || true; $command"
    }

    private fun environment(context: Context, id: String): Map<String, String> {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val prefix = context.filesDir.parentFile?.path.orEmpty()
        val tmp = App.getTempDir(context).child("host-$id").apply { mkdirs() }
        val env = mutableMapOf(
            "PATH" to "${System.getenv("PATH")}:/sbin:${context.localBinDir().absolutePath}",
            "HOME" to "/sdcard",
            "PREFIX" to prefix,
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "LANG" to "C.UTF-8",
            "LD_LIBRARY_PATH" to context.localLibDir().absolutePath,
            "NATIVE_LIB_DIR" to nativeDir,
            "PROOT" to "$nativeDir/libproot.so",
            "PROOT_LOADER" to "$nativeDir/libloader.so",
            "PROOT_TMP_DIR" to tmp.absolutePath,
            "TMPDIR" to App.getTempDir(context).absolutePath,
            "USE_CHROOT" to if (Rootfs.execMode.value == ExecMode.CHROOT) "1" else "0",
            "CHROOT" to if (File("/system/bin/chroot").exists()) "/system/bin/chroot" else "/system/xbin/chroot",
            "RETERM_LOGIN_SHELL" to Settings.login_shell.toString(),
            "PKG" to context.packageName,
            "PKG_PATH" to context.applicationInfo.sourceDir,
            "DEBUG" to BuildConfig.DEBUG.toString(),
            "PIP_BREAK_SYSTEM_PACKAGES" to "1"
        )
        val loader32 = "$nativeDir/libloader32.so"
        if (File(loader32).exists()) env["PROOT_LOADER_32"] = loader32
        return env
    }

    fun formatPort(port: Int): String = if (port > 0) ":$port" else ""

    fun modeLabel(linux: Boolean): String = if (linux) "Linux" else "Android"

    fun sizeLabel(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${bytes} ${units[0]}"
        else String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}
