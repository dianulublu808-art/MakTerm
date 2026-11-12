package com.rk.server

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.settings.Settings
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Small, dependency-free HTTP file server used by the in-app server panel.
 *
 * It serves one directory (default: shared storage) over the local network,
 * optionally protected with HTTP Basic authentication. Only GET and HEAD are
 * implemented; anything else gets 405.
 */
object FileServer {
    var running by mutableStateOf(false)
        private set
    var port by mutableIntStateOf(0)
        private set
    var rootPath by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var servedRequests by mutableLongStateOf(0L)
        private set

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var executor: ExecutorService? = null
    private val clients = Collections.synchronizedSet(HashSet<Socket>())

    /** LAN URL of the running server, or null when there is no Wi-Fi/Ethernet address. */
    fun lanUrl(): String? {
        if (!running) return null
        val host = lanAddress() ?: return null
        return "http://$host:$port/"
    }

    fun lanAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }

    /** Starts the server. Returns an error message, or null on success. */
    @Synchronized
    fun start(): String? {
        if (running) return null
        error = null

        val requestedPort = Settings.server_port.coerceIn(MIN_PORT, MAX_PORT)
        val root = File(Settings.server_root.ifBlank { DEFAULT_ROOT }).canonicalFile
        if (!root.isDirectory) {
            val message = "Folder not found: ${root.path}"
            error = message
            return message
        }
        if (!root.canRead()) {
            val message = "Folder is not readable: ${root.path}"
            error = message
            return message
        }

        return try {
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(requestedPort))
            }
            serverSocket = socket
            port = requestedPort
            rootPath = root.absolutePath
            servedRequests = 0
            executor = Executors.newFixedThreadPool(6)
            running = true
            acceptThread = Thread({ acceptLoop(socket) }, "MakTerm-FileServer").apply {
                isDaemon = true
                start()
            }
            null
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            error = message
            runCatching { serverSocket?.close() }
            serverSocket = null
            running = false
            message
        }
    }

    @Synchronized
    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        synchronized(clients) {
            clients.forEach { runCatching { it.close() } }
            clients.clear()
        }
        executor?.shutdownNow()
        executor = null
        acceptThread = null
        port = 0
        rootPath = ""
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            } catch (_: Exception) {
                continue
            }
            clients.add(client)
            val pool = executor
            if (pool == null || pool.isShutdown) {
                runCatching { client.close() }
                clients.remove(client)
                break
            }
            runCatching { pool.execute { handleClient(client) } }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 20_000
            val input = client.getInputStream()
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) {
                writeText(client.getOutputStream(), 400, "Bad Request", "Malformed request")
                return
            }
            val method = parts[0].uppercase(Locale.US)
            val rawTarget = parts[1]

            var authorization: String? = null
            var rangeHeader: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator <= 0) continue
                val name = line.substring(0, separator).trim().lowercase(Locale.US)
                val value = line.substring(separator + 1).trim()
                when (name) {
                    "authorization" -> authorization = value
                    "range" -> rangeHeader = value
                }
            }

            if (!isAuthorized(authorization)) {
                writeText(
                    client.getOutputStream(), 401, "Unauthorized",
                    "Authentication required",
                    extraHeaders = "WWW-Authenticate: Basic realm=\"MakTerm\", charset=\"UTF-8\"\r\n"
                )
                return
            }

            if (method != "GET" && method != "HEAD") {
                writeText(
                    client.getOutputStream(), 405, "Method Not Allowed", "Only GET and HEAD are supported",
                    extraHeaders = "Allow: GET, HEAD\r\n"
                )
                return
            }

            servedRequests++

            val pathPart = rawTarget.substringBefore('?').substringBefore('#')
            val decoded = decodePath(pathPart)
            val root = File(rootPath).canonicalFile
            val target = File(root, decoded.trimStart('/')).canonicalFile
            if (target != root && !target.path.startsWith(root.path + File.separator)) {
                writeText(client.getOutputStream(), 403, "Forbidden", "Path escapes the shared folder")
                return
            }

            when {
                !target.exists() -> writeText(client.getOutputStream(), 404, "Not Found", "Not found")
                target.isDirectory -> serveDirectory(client.getOutputStream(), root, target, method == "HEAD")
                else -> serveFile(client.getOutputStream(), target, rangeHeader, method == "HEAD")
            }
        } catch (_: Exception) {
            // Client disconnected or malformed request; nothing useful to report.
        } finally {
            clients.remove(client)
            runCatching { client.close() }
        }
    }

    private fun isAuthorized(header: String?): Boolean {
        if (!Settings.server_auth) return true
        val password = Settings.server_password
        if (password.isBlank()) return true
        if (header == null || !header.startsWith("Basic ", ignoreCase = true)) return false
        val decoded = runCatching {
            String(android.util.Base64.decode(header.substring(6).trim(), android.util.Base64.DEFAULT), StandardCharsets.UTF_8)
        }.getOrNull() ?: return false
        val separator = decoded.indexOf(':')
        if (separator < 0) return false
        val user = decoded.substring(0, separator)
        val supplied = decoded.substring(separator + 1)
        return (user == "admin" || user == "makterm") && supplied == password
    }

    private fun serveDirectory(out: OutputStream, root: File, dir: File, headOnly: Boolean) {
        val relative = dir.relativeTo(root).invariantSeparatorsPath
        val title = if (relative.isEmpty()) "/" else "/$relative/"
        val entries = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
            ?: emptyList()

        val html = buildString {
            append("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<title>MakTerm · ").append(escapeHtml(title)).append("</title>")
            append("<style>")
            append(":root{color-scheme:dark}body{margin:0;padding:28px;font:15px/1.5 system-ui,sans-serif;")
            append("background:radial-gradient(circle at 20% 0%,#24324a,#0d1117 55%);color:#e6edf3;min-height:100vh}")
            append("h1{font-size:20px;margin:0 0 18px;word-break:break-all}")
            append("a{color:#8ecdf7;text-decoration:none}a:hover{text-decoration:underline}")
            append("ul{list-style:none;padding:0;margin:0;max-width:900px}")
            append("li{display:flex;gap:14px;align-items:center;padding:11px 16px;margin:6px 0;")
            append("border:1px solid #ffffff1f;border-radius:14px;background:#ffffff0d;backdrop-filter:blur(10px)}")
            append("li .n{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}")
            append("li .s{color:#9aa7b4;font-size:13px;white-space:nowrap}")
            append("li .i{width:22px;text-align:center}")
            append(".empty{color:#9aa7b4;padding:16px 0}</style></head><body>")
            append("<h1>📁 ").append(escapeHtml(title)).append("</h1><ul>")
            if (relative.isNotEmpty()) {
                append("<li><span class=\"i\">↩</span><a class=\"n\" href=\"../\">Наверх</a></li>")
            }
            if (entries.isEmpty()) {
                append("</ul><p class=\"empty\">Папка пуста</p>")
            } else {
                entries.forEach { file ->
                    val name = file.name + if (file.isDirectory) "/" else ""
                    val icon = if (file.isDirectory) "📁" else iconFor(file.name)
                    val size = if (file.isDirectory) "" else formatSize(file.length())
                    append("<li><span class=\"i\">").append(icon).append("</span>")
                    append("<a class=\"n\" href=\"").append(encodePath(name)).append("\">")
                    append(escapeHtml(name)).append("</a>")
                    append("<span class=\"s\">").append(size).append("</span></li>")
                }
                append("</ul>")
            }
            append("<p class=\"empty\">MakTerm · встроенный файловый сервер</p></body></html>")
        }

        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        out.write(header)
        if (!headOnly) out.write(bytes)
        out.flush()
    }

    private fun serveFile(out: OutputStream, file: File, rangeHeader: String?, headOnly: Boolean) {
        val length = file.length()
        val mime = mimeType(file.name)
        val range = parseRange(rangeHeader, length)
        val buffered = BufferedOutputStream(out, 64 * 1024)

        if (range != null) {
            val (start, end) = range
            val chunk = end - start + 1
            val header = buildString {
                append("HTTP/1.1 206 Partial Content\r\n")
                append("Content-Type: ").append(mime).append("\r\n")
                append("Content-Length: ").append(chunk).append("\r\n")
                append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(length).append("\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.US_ASCII)
            buffered.write(header)
            if (!headOnly) {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(start)
                    copyRange(raf, buffered, chunk)
                }
            }
        } else {
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: ").append(mime).append("\r\n")
                append("Content-Length: ").append(length).append("\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(StandardCharsets.US_ASCII)
            buffered.write(header)
            if (!headOnly) {
                FileInputStream(file).use { input -> input.copyTo(buffered, 64 * 1024) }
            }
        }
        buffered.flush()
    }

    private fun copyRange(input: RandomAccessFile, out: OutputStream, count: Long) {
        val buffer = ByteArray(64 * 1024)
        var remaining = count
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) break
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun parseRange(header: String?, length: Long): Pair<Long, Long>? {
        if (header == null || length <= 0) return null
        val value = header.trim()
        if (!value.startsWith("bytes=", ignoreCase = true)) return null
        val spec = value.substring(6).substringBefore(',').trim()
        if (spec.isEmpty()) return null
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        return runCatching {
            val startText = spec.substring(0, dash)
            val endText = spec.substring(dash + 1)
            if (startText.isEmpty()) {
                // Suffix range: last N bytes.
                val suffix = endText.toLong()
                if (suffix <= 0) return null
                val start = (length - suffix).coerceAtLeast(0)
                start to (length - 1)
            } else {
                val start = startText.toLong()
                if (start >= length) return null
                val end = if (endText.isEmpty()) length - 1 else endText.toLong().coerceAtMost(length - 1)
                if (end < start) null else start to end
            }
        }.getOrNull()
    }

    private fun writeText(
        out: OutputStream,
        code: Int,
        reason: String,
        message: String,
        extraHeaders: String = ""
    ) {
        val body = "<!doctype html><html lang=\"ru\"><meta charset=\"utf-8\">" +
            "<body style=\"font:16px system-ui;background:#0d1117;color:#e6edf3;padding:32px\">" +
            "<h1>$code $reason</h1><p>${escapeHtml(message)}</p></body></html>"
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            append(extraHeaders)
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        runCatching {
            out.write(headers)
            out.write(bytes)
            out.flush()
        }
    }

    private fun decodePath(path: String): String {
        // URLDecoder turns '+' into a space, which is wrong for paths.
        return runCatching { URLDecoder.decode(path.replace("+", "%2B"), "UTF-8") }.getOrDefault(path)
    }

    private fun encodePath(name: String): String {
        val bytes = name.toByteArray(StandardCharsets.UTF_8)
        val builder = StringBuilder()
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (char.isLetterOrDigit() && value < 128 || char in "-._~!$&'()*+,;=:@/") {
                builder.append(char)
            } else {
                builder.append('%').append(String.format(Locale.US, "%02X", value))
            }
        }
        return builder.toString()
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = bytes.toDouble() / 1024.0
        var unit = 0
        while (size >= 1024.0 && unit < units.lastIndex) {
            size /= 1024.0
            unit++
        }
        return String.format(Locale.US, "%.1f %s", size, units[unit])
    }

    private fun iconFor(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "apk" -> "🤖"
        "zip", "tar", "gz", "xz", "7z", "rar" -> "🗜"
        "jpg", "jpeg", "png", "gif", "webp", "svg" -> "🖼"
        "mp3", "flac", "ogg", "wav", "m4a" -> "🎵"
        "mp4", "mkv", "avi", "webm" -> "🎬"
        "pdf" -> "📄"
        "txt", "md", "log", "json", "xml", "kt", "java", "sh" -> "📝"
        else -> "📄"
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "html", "htm" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "txt", "md", "log", "sh", "kt", "java", "xml", "csv" -> "text/plain; charset=utf-8"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "pdf" -> "application/pdf"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "gz" -> "application/gzip"
        "xz" -> "application/x-xz"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> "application/octet-stream"
    }

    const val MIN_PORT = 1024
    const val MAX_PORT = 65535
    const val DEFAULT_ROOT = "/sdcard"
}
