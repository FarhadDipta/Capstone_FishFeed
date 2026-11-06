package com.fishfeed.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TimeUtils {
    private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    private val dayNames = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

    fun now(): Long = System.currentTimeMillis()

    /** Nama waktu dalam sehari untuk memberi nama jadwal otomatis. */
    fun periodLabel(hour: Int): String = when {
        hour < 11 -> "Pagi"
        hour < 15 -> "Siang"
        hour < 19 -> "Sore"
        else -> "Malam"
    }

    fun todayKey(): String = LocalDate.now().toString()

    fun dateKeyDaysAgo(days: Long): String = LocalDate.now().minusDays(days).toString()

    fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    fun formatClock(millis: Long): String {
        return try {
            val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
            formatTime(t.hour, t.minute)
        } catch (e: Exception) {
            "--:--"
        }
    }

    /** "2025-10-27" -> "27 Okt 2025" */
    fun formatDate(dateKey: String): String {
        return try {
            val d = LocalDate.parse(dateKey)
            "${d.dayOfMonth} ${monthNames[(d.monthValue - 1).coerceIn(0, 11)]} ${d.year}"
        } catch (e: Exception) {
            dateKey
        }
    }

    /** Nama hari singkat, mis. "Sen". */
    fun dayShort(date: LocalDate): String = try {
        dayNames[(date.dayOfWeek.value - 1).coerceIn(0, 6)]
    } catch (e: Exception) {
        ""
    }

    /** Waktu (epoch ms) hari ini pada jam:menit tertentu. */
    fun todayAt(hour: Int, minute: Int): Long = try {
        LocalDate.now().atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    /**
     * Waktu pemicu berikutnya untuk jam:menit tertentu, SELALU setelah [after].
     * Jika jam hari ini sudah lewat, hasilnya besok.
     */
    fun nextTrigger(hour: Int, minute: Int, after: Long = now()): Long {
        return try {
            val h = hour.coerceIn(0, 23)
            val m = minute.coerceIn(0, 59)
            val zone = ZoneId.systemDefault()
            val afterDate = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
            var candidate = afterDate.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
            if (candidate <= after) {
                candidate = afterDate.plusDays(1).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
            }
            candidate
        } catch (e: Exception) {
            after + 86_400_000L
        }
    }

    /** Selisih menit antara jam jadwal pada [dateKey] dan [millis] (bisa negatif). */
    fun minutesAfterSchedule(dateKey: String, scheduledTime: String, millis: Long): Long {
        return try {
            val parts = scheduledTime.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
            val at = LocalDate.parse(dateKey).atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            (millis - at) / 60_000
        } catch (e: Exception) {
            0L
        }
    }

    fun isToday(millis: Long): Boolean = try {
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
    } catch (e: Exception) {
        false
    }
}
