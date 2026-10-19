package com.lightreader.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {

    fun fileSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) {
            "${bytes} ${units[unit]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unit])
        }
    }

    fun dateTime(millis: Long?): String {
        if (millis == null || millis <= 0) return "—"
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return format.format(Date(millis))
    }

    fun relativeTime(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null || millis <= 0) return "—"
        val diff = now - millis
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> AppStrings.get(com.lightreader.R.string.time_just_now)
            diff < TimeUnit.HOURS.toMillis(1) -> AppStrings.get(com.lightreader.R.string.time_minutes_ago, TimeUnit.MILLISECONDS.toMinutes(diff))
            diff < TimeUnit.DAYS.toMillis(1) -> AppStrings.get(com.lightreader.R.string.time_hours_ago, TimeUnit.MILLISECONDS.toHours(diff))
            diff < TimeUnit.DAYS.toMillis(30) -> AppStrings.get(com.lightreader.R.string.time_days_ago, TimeUnit.MILLISECONDS.toDays(diff))
            else -> dateTime(millis)
        }
    }

    fun percent(value: Float): String = "${(value.coerceIn(0f, 1f) * 100).toInt()}%"
}
