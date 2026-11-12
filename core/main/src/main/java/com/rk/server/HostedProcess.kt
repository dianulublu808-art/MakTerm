package com.rk.server

import android.content.Context
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
import java.util.concurrent.ConcurrentHashMap

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
    var process: Process? = null
    var running by mutableStateOf(false)
    var stopping by mutableStateOf(false)
    var log by mutableStateOf("")
    var startedAt by mutableLongStateOf(0L)
    var restarts = 0
}

object HostedProcessManager {
    private const val MAX_LOG = 120_000
    private const val MAX_RESTARTS = 5
    val runtimes = mutableStateMapOf<String, HostRuntime>()
    private val threads = ConcurrentHashMap<String, Thread>()

    fun runtime(id: String): HostRuntime? = runtimes[id]

    fun isRunning(id: String): Boolean = runtimes[id]?.running == true

    fun start(config: HostedProcess): String? {
        if (config.command.isBlank()) return "Command is empty"
        val existing = runtimes[config.id]
        if (existing?.running == true) return null
        val runtime = existing ?: HostRuntime(config.id).also { runtimes[config.id] = it }
        runtime.stopping = false
        runtime.restarts = 0
        runtime.log = ""
        HostService.start(application!!)
        launch(config, runtime)
        HostService.refresh(application!!)
        return null
    }

    fun stop(id: String) {
        val runtime = runtimes[id] ?: return
        runtime.stopping = true
        runtime.running = false
        runtime.process?.let { process ->
            runCatching { process.destroy() }
            Thread {
                Thread.sleep(1500)
                if (runCatching { process.isAlive }.getOrDefault(false)) {
                    runCatching { process.destroyForcibly() }
                }
            }.apply { isDaemon = true; start() }
        }
        HostService.refresh(application!!)
        maybeStopService()
    }

    fun stopAll() {
        runtimes.keys.toList().forEach { stop(it) }
    }

    fun clearLog(id: String) {
        runtimes[id]?.log = ""
    }

    private fun launch(config: HostedProcess, runtime: HostRuntime) {
        runtime.running = true
        runtime.startedAt = System.currentTimeMillis()
        val thread = Thread {
            try {
                val process = ProcessLauncher.create(
                    application!!,
                    config.command,
                    config.linux,
                    config.workDir,
                    config.id
                )
                runtime.process = process
                append(runtime, "▶ ${config.name}\n")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val buffer = CharArray(4096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    append(runtime, String(buffer, 0, read))
                }
                val exit = process.waitFor()
                runtime.process = null
                runtime.running = false
                append(runtime, "\n■ Процесс завершён, код $exit\n")
                if (!runtime.stopping && config.keepAlive && runtime.restarts < MAX_RESTARTS) {
                    runtime.restarts++
                    append(runtime, "↻ Перезапуск через 3 секунды (${runtime.restarts}/$MAX_RESTARTS)\n")
                    Thread.sleep(3000)
                    if (!runtime.stopping) launch(config, runtime)
                } else {
                    maybeStopService()
                }
            } catch (e: Exception) {
                runtime.running = false
                runtime.process = null
                append(runtime, "✖ ${e.message ?: e.javaClass.simpleName}\n")
                maybeStopService()
            }
        }.apply {
            isDaemon = true
            name = "MakTerm-host-${config.id}"
            start()
        }
        threads[config.id] = thread
    }

    private fun append(runtime: HostRuntime, text: String) {
        val combined = runtime.log + text
        runtime.log = if (combined.length > MAX_LOG) combined.takeLast(MAX_LOG) else combined
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
