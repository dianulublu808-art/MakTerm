package com.rk.server

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.libcommons.application
import com.rk.settings.Preference
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class HostedProcess(
    val id: String,
    val name: String,
    val command: String,
    val workDir: String = "/sdcard",
    val linux: Boolean = true,
    val port: Int = 0,
    val keepAlive: Boolean = false
)

object HostedProcesses {
    private const val KEY = "hosted_processes"

    fun getAll(): List<HostedProcess> {
        val raw = Preference.getString(key = KEY, default = "[]")
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                HostedProcess(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    command = obj.optString("command"),
                    workDir = obj.optString("workDir", "/sdcard"),
                    linux = obj.optBoolean("linux", true),
                    port = obj.optInt("port", 0),
                    keepAlive = obj.optBoolean("keepAlive", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(process: HostedProcess): HostedProcess {
        val withId = if (process.id.isBlank()) {
            process.copy(id = System.currentTimeMillis().toString())
        } else process
        val list = getAll().toMutableList()
        list.add(withId)
        save(list)
        return withId
    }

    fun update(process: HostedProcess) {
        val list = getAll().toMutableList()
        val index = list.indexOfFirst { it.id == process.id }
        if (index >= 0) list[index] = process else list.add(process)
        save(list)
    }

    fun remove(id: String) {
        save(getAll().filterNot { it.id == id })
    }

    private fun save(list: List<HostedProcess>) {
        val array = JSONArray()
        list.forEach { process ->
            array.put(JSONObject().apply {
                put("id", process.id)
                put("name", process.name)
                put("command", process.command)
                put("workDir", process.workDir)
                put("linux", process.linux)
                put("port", process.port)
                put("keepAlive", process.keepAlive)
            })
        }
        Preference.setString(key = KEY, value = array.toString())
    }
}

class HostRuntime(val id: String) {
    var running by mutableStateOf(false)
    var stopping by mutableStateOf(false)
    var startedAt by mutableLongStateOf(0L)
    var restarts by mutableStateOf(0)
    var exitCode by mutableStateOf<Int?>(null)
}

/**
 * Android-side view of the Rust process supervisor.
 *
 * All process lifecycle work happens in `libmakterm_core.so`. This object
 * builds the launch spec, keeps the Compose state in sync by polling a small
 * JSON snapshot, and drives [HostService] while something is running.
 */
object HostedProcessManager {
    private const val POLL_INTERVAL_MS = 700L
    val runtimes = mutableStateMapOf<String, HostRuntime>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var poller: Thread? = null

    fun runtime(id: String): HostRuntime? = runtimes[id]

    fun isRunning(id: String): Boolean = runtimes[id]?.running == true

    fun start(config: HostedProcess): String? {
        if (config.command.isBlank()) return "Command is empty"
        if (!RustProcessHost.available) return "Rust core is not loaded"

        val runtime = runtimes.getOrPut(config.id) { HostRuntime(config.id) }
        runtime.stopping = false
        runtime.exitCode = null
        val spec = ProcessLauncher.jsonSpec(application!!, config)
        val error = RustProcessHost.nativeStart(spec)
        if (error.isNotBlank()) return error

        runtime.running = true
        runtime.startedAt = System.currentTimeMillis()
        runtime.restarts = 0
        HostService.start(application!!)
        HostService.refresh(application!!)
        ensurePoller()
        return null
    }

    fun stop(id: String) {
        RustProcessHost.nativeStop(id)
        runtimes[id]?.let {
            it.stopping = true
            it.running = false
        }
        HostService.refresh(application!!)
        maybeStopService()
    }

    fun stopAll() {
        RustProcessHost.nativeStopAll()
        runtimes.values.forEach {
            it.stopping = true
            it.running = false
        }
        maybeStopService()
    }

    fun clearLog(id: String) {
        RustProcessHost.nativeClearLog(id)
    }

    fun log(id: String): String = if (RustProcessHost.available) {
        runCatching { RustProcessHost.nativeLog(id) }.getOrDefault("")
    } else ""

    private data class RustState(
        val id: String,
        val running: Boolean,
        val restarting: Boolean,
        val startedAt: Long,
        val exitCode: Int,
        val restarts: Int
    )

    private fun ensurePoller() {
        if (poller?.isAlive == true) return
        poller = Thread {
            while (true) {
                val states = snapshot()
                mainHandler.post { applySnapshot(states) }
                if (states.none { it.running || it.restarting }) break
                Thread.sleep(POLL_INTERVAL_MS)
            }
            poller = null
        }.apply {
            isDaemon = true
            name = "MakTerm-rust-poll"
            start()
        }
    }

    private fun snapshot(): List<RustState> {
        if (!RustProcessHost.available) return emptyList()
        val raw = runCatching { RustProcessHost.nativeSnapshot() }.getOrDefault("[]")
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                RustState(
                    id = item.optString("id"),
                    running = item.optBoolean("running"),
                    restarting = item.optBoolean("restarting"),
                    startedAt = item.optLong("startedAt"),
                    exitCode = item.optInt("exitCode", -999),
                    restarts = item.optInt("restarts")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun applySnapshot(states: List<RustState>) {
        val byId = states.associateBy { it.id }
        var changed = false
        runtimes.forEach { (id, runtime) ->
            val state = byId[id] ?: return@forEach
            if (runtime.running != state.running) changed = true
            runtime.running = state.running
            runtime.startedAt = state.startedAt
            runtime.restarts = state.restarts
            runtime.exitCode = state.exitCode.takeIf { it != -999 }
            if (state.running) runtime.stopping = false
        }
        if (changed) HostService.refresh(application!!)
        if (states.none { it.running || it.restarting }) maybeStopService()
    }

    private fun maybeStopService() {
        if (runtimes.values.any { it.running }) return
        if (FileServer.running) return
        runCatching { HostService.stop(application!!) }
    }
}

/**
 * One-shot background task (package installation, rootfs backup) with a live
 * log. The UI opens a dialog and observes [title], [log] and [running].
 */
object TaskRunner {
    private const val MAX_LOG = 120_000
    var title by mutableStateOf("")
        private set
    var log by mutableStateOf("")
        private set
    var running by mutableStateOf(false)
        private set

    private var process: Process? = null

    fun run(context: Context, title: String, command: String, linux: Boolean = true, onFinish: (Int) -> Unit = {}) {
        if (running) return
        this.title = title
        log = ""
        running = true
        Thread {
            var exit = -1
            try {
                val p = ProcessLauncher.create(context, command, linux, "", "task")
                process = p
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                val buffer = CharArray(4096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    append(String(buffer, 0, read))
                }
                exit = p.waitFor()
                append("\n■ Готово, код $exit\n")
            } catch (e: Exception) {
                append("✖ ${e.message ?: e.javaClass.simpleName}\n")
            } finally {
                process = null
                running = false
                onFinish(exit)
            }
        }.apply {
            isDaemon = true
            name = "MakTerm-task"
            start()
        }
    }

    fun cancel() {
        process?.let { runCatching { it.destroy() } }
        running = false
    }

    private fun append(text: String) {
        val combined = log + text
        log = if (combined.length > MAX_LOG) combined.takeLast(MAX_LOG) else combined
    }
}
