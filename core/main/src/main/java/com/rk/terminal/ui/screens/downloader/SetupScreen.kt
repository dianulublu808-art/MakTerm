package com.rk.terminal.ui.screens.downloader

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rk.i18n.t
import com.rk.libcommons.*
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.terminal.ExecMode
import com.rk.terminal.ui.screens.terminal.Rootfs
import com.rk.terminal.ui.screens.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private fun hasRootAccess(): Boolean {
    val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")
    if (paths.none { File(it).exists() }) return false
    return try {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val exited = process.waitFor()
        exited == 0 && output.contains("uid=0")
    } catch (e: Exception) {
        false
    }
}

private fun currentAbi(): String {
    val abis = Build.SUPPORTED_ABIS
    return abis.firstOrNull { it in listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        ?: throw RuntimeException("Unsupported CPU architectures: ${abis.joinToString()}")
}

private const val ARCH_MIN_ARCHIVE_BYTES = 500L * 1024 * 1024

/**
 * Checks for real marker files. /bin/sh is usually an absolute symlink (Alpine
 * points it at /bin/busybox), so [File.exists] on it fails outside the rootfs.
 */
private fun isRootfsExtracted(rootfsDir: File): Boolean {
    val hasOsRelease = rootfsDir.child("etc/os-release").exists() ||
        rootfsDir.child("etc/alpine-release").exists()
    if (!hasOsRelease) return false
    return rootfsDir.child("bin/busybox").exists() ||
        rootfsDir.child("usr/bin/bash").exists() ||
        rootfsDir.child("bin/bash").exists() ||
        rootfsDir.child("usr/bin/pacman").exists()
}

/**
 * Mirrors are tried in order. The official host redirects to plain HTTP and its
 * HTTP endpoint currently answers 502, so a TLS mirror that actually serves the
 * tarball comes first; the official URL stays as a last resort.
 */
private fun archDownloadUrls(): List<String> = when (currentAbi()) {
    "arm64-v8a" -> listOf(
        "https://mirrors.tuna.tsinghua.edu.cn/archlinuxarm/os/ArchLinuxARM-aarch64-latest.tar.gz",
        "https://mirrors.bfsu.edu.cn/archlinuxarm/os/ArchLinuxARM-aarch64-latest.tar.gz",
        "https://mirrors.nju.edu.cn/archlinuxarm/os/ArchLinuxARM-aarch64-latest.tar.gz",
        "https://mirrors.sjtug.sjtu.edu.cn/archlinuxarm/os/ArchLinuxARM-aarch64-latest.tar.gz",
        "http://mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz",
        "https://archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
    )
    "armeabi-v7a" -> listOf(
        "https://mirrors.tuna.tsinghua.edu.cn/archlinuxarm/os/ArchLinuxARM-armv7-latest.tar.gz",
        "https://mirrors.bfsu.edu.cn/archlinuxarm/os/ArchLinuxARM-armv7-latest.tar.gz",
        "https://mirrors.nju.edu.cn/archlinuxarm/os/ArchLinuxARM-armv7-latest.tar.gz",
        "https://mirrors.sjtug.sjtu.edu.cn/archlinuxarm/os/ArchLinuxARM-armv7-latest.tar.gz",
        "http://mirror.archlinuxarm.org/os/ArchLinuxARM-armv7-latest.tar.gz",
        "https://archlinuxarm.org/os/ArchLinuxARM-armv7-latest.tar.gz"
    )
    else -> throw RuntimeException("Arch Linux ARM is not available for ${currentAbi()}")
}

/**
 * Opens [url] and follows redirects manually. The Arch Linux ARM mirrors
 * redirect from https to http, which [HttpURLConnection] refuses to follow on
 * its own, so relying on [HttpURLConnection.getInstanceFollowRedirects] fails
 * with "HTTP 301/302".
 */
private fun openFollowingRedirects(url: String): HttpURLConnection {
    var current = url
    repeat(8) {
        val connection = (URL(current).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "MakTerm/1.5")
        }
        connection.connect()
        val code = connection.responseCode
        if (code in 300..399) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank()) throw IOException("HTTP $code without Location")
            current = URL(URL(current), location).toString()
            return@repeat
        }
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $code")
        }
        return connection
    }
    throw IOException("Too many redirects")
}

private suspend fun downloadRootfs(
    url: String,
    target: File,
    onProgress: suspend (Int) -> Unit
) {
    val partial = File(target.parentFile, target.name + ".part")
    partial.delete()
    val connection = openFollowingRedirects(url)
    var downloaded = 0L
    var total = -1L
    try {
        total = connection.contentLengthLong
        connection.inputStream.use { input ->
            FileOutputStream(partial).use { output ->
                val buffer = ByteArray(256 * 1024)
                var lastPercent = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0L) {
                        val percent = ((downloaded * 100) / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        partial.delete()
        throw e
    } finally {
        connection.disconnect()
    }

    if (total > 0L && downloaded != total) {
        partial.delete()
        throw IOException("Incomplete download: $downloaded of $total bytes")
    }
    if (!partial.renameTo(target)) {
        partial.copyTo(target, overwrite = true)
        partial.delete()
    }
}

/**
 * Extracts [archive] into [target]. A non-zero exit code is tolerated because
 * tarballs contain device nodes that cannot be created without root; the
 * caller verifies that the result actually contains a shell.
 */
private fun extractRootfs(archive: File, target: File): Boolean {
    target.mkdirs()
    val tarBinary = if (File("/system/bin/tar").exists()) "/system/bin/tar" else "tar"
    val process = ProcessBuilder(tarBinary, "-xf", archive.absolutePath, "-C", target.absolutePath)
        .redirectErrorStream(true)
        .start()
    // Drain the output, but do not keep it in memory: extracting a full Arch
    // root filesystem can produce a lot of harmless device-node warnings.
    process.inputStream.bufferedReader().use { reader ->
        val buffer = CharArray(8192)
        while (reader.read(buffer) > 0) {
            // discard
        }
    }
    return process.waitFor() == 0
}

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    mainActivity: MainActivity,
    navController: NavHostController
) {
    val context = LocalContext.current
    val installingStr = stringResource(strings.installing)
    val setupFailedStr = stringResource(strings.setup_failed)

    var isSetupComplete by remember { mutableStateOf(Rootfs.isRootfsInstalled(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var rootChecked by remember { mutableStateOf(false) }
    var showExecModeDialog by remember { mutableStateOf(false) }
    var showDistroDialog by remember { mutableStateOf(false) }
    var extractionStarted by remember { mutableStateOf(false) }
    var selectedDistro by remember { mutableStateOf(Settings.rootfs_distro) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var setupStage by remember { mutableStateOf<String?>(null) }

    fun continueSetup() {
        if (isSetupComplete) {
            Rootfs.isInstalled.value = true
        } else {
            showDistroDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (Rootfs.execMode.value != null) {
            rootChecked = true
            continueSetup()
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val hasRoot = hasRootAccess()
            withContext(Dispatchers.Main) {
                rootChecked = true
                if (hasRoot) {
                    showExecModeDialog = true
                } else {
                    Rootfs.setExecMode(ExecMode.PROOT)
                    continueSetup()
                }
            }
        }
    }

    if (showExecModeDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(t("Root Access Detected")) },
            text = {
                Text(t("Root access was found. Run the terminal with chroot (faster, requires root for every session) or proot (no root required, slightly slower)? You can change this later in Settings."))
            },
            confirmButton = {
                TextButton(onClick = {
                    Rootfs.setExecMode(ExecMode.CHROOT)
                    showExecModeDialog = false
                    continueSetup()
                }) { Text(t("Chroot")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Rootfs.setExecMode(ExecMode.PROOT)
                    showExecModeDialog = false
                    continueSetup()
                }) { Text(t("Proot")) }
            }
        )
    }

    if (showDistroDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(t("Choose distribution")) },
            text = {
                Column {
                    Text(
                        t("Small and fast (bundled)"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { selectedDistro = "alpine" }) {
                        RadioButton(selected = selectedDistro == "alpine", onClick = { selectedDistro = "alpine" })
                        Text("  " + t("Alpine Linux"))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t("Full Arch Linux, downloaded once (~830 MB)"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { selectedDistro = "arch" }) {
                        RadioButton(selected = selectedDistro == "arch", onClick = { selectedDistro = "arch" })
                        Text("  " + t("Arch Linux ARM"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Settings.rootfs_distro = selectedDistro
                    showDistroDialog = false
                    extractionStarted = true
                }) { Text(t("Install")) }
            }
        )
    }

    LaunchedEffect(extractionStarted) {
        if (!extractionStarted || isSetupComplete) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val isArch = Settings.rootfs_distro == "arch"
            val outputFile = context.filesDir.child("alpine.tar.gz")
            val rootfsDir = context.localDir().child("alpine")
            try {
                val alreadyExtracted = isRootfsExtracted(rootfsDir)
                if (!alreadyExtracted) {
                    val minArchiveSize = if (isArch) ARCH_MIN_ARCHIVE_BYTES else 1024L * 1024
                    if (!outputFile.exists() || outputFile.length() < minArchiveSize) {
                        outputFile.delete()
                        if (isArch) {
                            var lastError: Exception? = null
                            for (mirror in archDownloadUrls()) {
                                try {
                                    downloadRootfs(mirror, outputFile) { percent ->
                                        withContext(Dispatchers.Main) { downloadProgress = percent }
                                    }
                                    lastError = null
                                    break
                                } catch (e: Exception) {
                                    // Keep the mirror host in the message: the
                                    // setup screen shows it when every mirror fails.
                                    lastError = IOException("${URL(mirror).host}: ${e.message}", e)
                                    runCatching { outputFile.delete() }
                                }
                            }
                            if (lastError != null) throw lastError
                        } else {
                            val alpineArch = when (currentAbi()) {
                                "arm64-v8a" -> "aarch64"
                                "armeabi-v7a" -> "armhf"
                                "x86_64" -> "x86_64"
                                else -> throw RuntimeException("Unsupported ABI: ${currentAbi()}")
                            }
                            val assetName = "alpine-$alpineArch.tar.gz.rootfs"
                            context.assets.open(assetName).use { input ->
                                FileOutputStream(outputFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }

                    val requiredSpace = if (isArch) 3L * 1024 * 1024 * 1024 else 300L * 1024 * 1024
                    if (rootfsDir.usableSpace in 1L until requiredSpace) {
                        throw IOException("Not enough free space for the root filesystem")
                    }

                    withContext(Dispatchers.Main) {
                        downloadProgress = -1
                        setupStage = t("Extracting root filesystem...")
                    }
                    extractRootfs(outputFile, rootfsDir)

                    if (!isRootfsExtracted(rootfsDir)) {
                        throw IOException("The root filesystem could not be extracted")
                    }
                }
                withContext(Dispatchers.Main) {
                    setupStage = null
                    Rootfs.isInstalled.value = true
                    isSetupComplete = true
                }
            } catch (e: Exception) {
                if (isArch) {
                    runCatching { outputFile.delete() }
                }
                withContext(Dispatchers.Main) {
                    setupStage = null
                    error = e.javaClass.simpleName + ": " + e.message
                    toast(setupFailedStr.format(e.message))
                }
            }
        }
    }

    val ready = isSetupComplete && Rootfs.execMode.value != null

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!ready) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (error != null) {
                    Text(
                        "${t("Setup Failed:")} $error",
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!rootChecked) {
                    Text(t("Checking root access..."), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                } else if (!showExecModeDialog && !showDistroDialog) {
                    Text(
                        text = run {
                            val stage = setupStage
                            when {
                                stage != null -> stage
                                downloadProgress >= 0 -> t("Downloading root filesystem...") + " $downloadProgress%"
                                else -> installingStr
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (downloadProgress >= 0) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.width(240.dp)
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            TerminalScreen(mainActivity = mainActivity, navController = navController)
        }
    }
}
