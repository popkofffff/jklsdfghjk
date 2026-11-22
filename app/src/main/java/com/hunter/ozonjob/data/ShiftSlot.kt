package com.hunter.ozonjob.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ShiftSlot(
    val id: String,
    val startIso: String,
    val durationHours: Int,
    val available: Boolean
) {
    private fun parsed(): LocalDateTime? = runCatching {
        LocalDateTime.parse(startIso.take(19))
    }.getOrNull()

    fun startLocal(fallbackDate: LocalDate): LocalDateTime {
        val parsed = parsed()
        if (parsed != null) return parsed
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-ddHH:mm")
        return runCatching {
            LocalDateTime.parse(
                fallbackDate.toString() + startIso.take(5).replace(":", ":"),
                formatter
            )
        }.getOrElse { fallbackDate.atStartOfDay() }
    }

    fun timeLabel(fallbackDate: LocalDate): String {
        val dt = startLocal(fallbackDate)
        val end = dt.plusHours(durationHours.toLong())
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        return "%s–%s".format(dt.format(fmt), end.format(fmt))
    }
}

data class DaySlots(
    val date: LocalDate,
    val durationHours: Int,
    val slots: List<ShiftSlot>
)
