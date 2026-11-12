package com.rk.server

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import java.util.Locale

data class SystemSnapshot(
    val cpuLoad: String,
    val ramUsed: Long,
    val ramTotal: Long,
    val diskUsed: Long,
    val diskTotal: Long,
    val batteryPercent: Int,
    val charging: Boolean,
    val uptime: String,
    val lanAddress: String?
)

object SystemStats {
    fun snapshot(context: Context): SystemSnapshot {
        val memory = ActivityManager.MemoryInfo()
        runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        }
        val ramTotal = memory.totalMem
        val ramUsed = (memory.totalMem - memory.availMem).coerceAtLeast(0)

        val disk = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val diskTotal = disk?.blockCount?.times(disk.blockSizeLong) ?: 0L
        val diskFree = disk?.availableBlocks?.times(disk.blockSizeLong) ?: 0L
        val diskUsed = (diskTotal - diskFree).coerceAtLeast(0)

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = runCatching {
            val intent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }.getOrDefault(false)

        return SystemSnapshot(
            cpuLoad = readLoadAverage(),
            ramUsed = ramUsed,
            ramTotal = ramTotal,
            diskUsed = diskUsed,
            diskTotal = diskTotal,
            batteryPercent = battery,
            charging = charging,
            uptime = formatUptime(SystemClock.elapsedRealtime()),
            lanAddress = FileServer.lanAddress()
        )
    }

    private fun readLoadAverage(): String {
        return runCatching {
            val text = File("/proc/loadavg").readText().trim().split(Regex("\\s+"))
            text.take(3).joinToString(" ")
        }.getOrDefault("—")
    }

    private fun formatUptime(millis: Long): String {
        val totalMinutes = millis / 60_000
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> String.format(Locale.US, "%dд %dч %dм", days, hours, minutes)
            hours > 0 -> String.format(Locale.US, "%dч %dм", hours, minutes)
            else -> String.format(Locale.US, "%dм", minutes)
        }
    }
}
