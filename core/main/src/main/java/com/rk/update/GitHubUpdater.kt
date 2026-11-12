package com.rk.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal updater that talks to GitHub Releases. The repository is public, so
 * no token is needed on the device: the app only reads the latest release and
 * downloads the attached APK.
 */
object GitHubUpdater {
    const val REPO = "dianulublu808-art/MakTerm"

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String?,
        val pageUrl: String
    )

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** Compares dotted versions, ignoring a "-DEBUG" style suffix. */
    fun isNewer(remote: String, current: String): Boolean {
        val a = parseVersion(remote)
        val b = parseVersion(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parseVersion(value: String): List<Int> =
        value.trim()
            .removePrefix("v")
            .substringBefore('-')
            .split('.')
            .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }

    fun fetchLatest(): Release? {
        val connection = (URL("https://api.github.com/repos/$REPO/releases/latest")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MakTerm")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            if (tag.isBlank()) return null

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                        break
                    }
                }
            }

            return Release(
                version = tag.removePrefix("v"),
                notes = json.optString("body"),
                apkUrl = apkUrl,
                pageUrl = json.optString("html_url")
            )
        } catch (_: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(
        context: Context,
        url: String,
        onProgress: suspend (Int) -> Unit
    ): File {
        val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
        val target = File(dir, "MakTerm-update.apk")
        val partial = File(dir, "MakTerm-update.apk.part")
        target.delete()
        partial.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MakTerm")
        }
        var downloaded = 0L
        var total = -1L
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
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
        return target
    }

    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openReleasePage(context: Context, url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
