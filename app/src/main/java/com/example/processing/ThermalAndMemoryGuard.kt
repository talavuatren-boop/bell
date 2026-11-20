package com.example.processing

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.example.model.AiProcessingMode

data class ThermalHealthSnapshot(
    val thermalLevel: Int,
    val statusLabel: String,
    val availableRamMb: Long,
    val advisedMode: AiProcessingMode,
    val shouldThrottle: Boolean
)

class ThermalAndMemoryGuard(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun inspect(requestedMode: AiProcessingMode): ThermalHealthSnapshot {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024L * 1024L)

        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else {
            0
        }

        val label = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> "Severe Thermal Throttling"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> "Warm - Adapting Quality"
            availMb < 450L -> "Low RAM (${availMb}MB)"
            else -> "Nominal"
        }

        val shouldThrottle = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) || availMb < 450L

        val advised = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> AiProcessingMode.FAST
            shouldThrottle && requestedMode == AiProcessingMode.QUALITY -> AiProcessingMode.BALANCED
            else -> requestedMode
        }

        return ThermalHealthSnapshot(
            thermalLevel = thermalStatus,
            statusLabel = label,
            availableRamMb = availMb,
            advisedMode = advised,
            shouldThrottle = shouldThrottle
        )
    }
}
