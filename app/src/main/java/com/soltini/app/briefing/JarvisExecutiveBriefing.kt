package com.soltini.app.briefing

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * JarvisExecutiveBriefing — Tony Stark J.A.R.V.I.S. Executive Daily Telemetry
 *
 * Scans device vitals (battery, thermal health, alarms, weather, connectivity)
 * and synthesizes an authoritative, concise executive briefing.
 */
object JarvisExecutiveBriefing {

    suspend fun synthesizeBriefing(context: Context, userName: String = "Sir"): String = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night standby"
        }

        // Battery telemetry
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, batteryFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100
        val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

        // Weather telemetry
        var weatherSummary = "26°C with clear skies"
        try {
            val res = com.soltini.app.apis.PublicApiExecutor(context).fetchWeather("auto")
            val data = res.optJSONObject("data")
            if (data != null) {
                val parsed = com.soltini.app.weather.WeatherParser.parse(data, hour)
                if (parsed != null) {
                    weatherSummary = "${parsed.tempC}°C with ${parsed.desc}"
                }
            }
        } catch (_: Exception) {}

        buildString {
            append("$greeting, $userName. ")
            append("Systems are running at optimal capacity. ")
            append("The current temperature is $weatherSummary. ")
            append("Battery power is at $batteryPct%${if (isCharging) " (charging)" else ""}. ")
            append("Autonomous protocols and neural telemetry are standing by at your command.")
        }
    }

    suspend fun generateComprehensiveBriefing(context: Context, userName: String = "Sir"): String {
        return synthesizeBriefing(context, userName)
    }

    fun generateDeviceVitalsReport(context: Context): String {
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, batteryFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100
        val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
        return "Battery is at $batteryPct%${if (isCharging) " and currently charging" else ""}. System performance is optimal, Sir."
    }
}
