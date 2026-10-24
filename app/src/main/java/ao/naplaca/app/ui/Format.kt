package ao.naplaca.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    val locale: Locale = Locale("pt", "AO")
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayShort = DateTimeFormatter.ofPattern("EEE d", locale)
    private val dayLong = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale)
    private val dateTime = DateTimeFormatter.ofPattern("d MMM · HH:mm", locale)

    private fun zoned(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())

    fun time(epoch: Long): String = zoned(epoch).format(time)
    fun dateTime(epoch: Long): String = zoned(epoch).format(dateTime)
    fun dayLong(date: LocalDate): String = date.format(dayLong).replaceFirstChar { it.titlecase(locale) }

    fun dayChip(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        today.plusDays(1) -> "Amanhã"
        else -> date.format(dayShort).replaceFirstChar { it.titlecase(locale) }
    }

    fun ago(epoch: Long, now: Long = System.currentTimeMillis()): String {
        if (epoch <= 0) return ""
        val min = (now - epoch) / 60_000
        return when {
            min < 1 -> "agora mesmo"
            min < 60 -> "há $min min"
            min < 24 * 60 -> "há ${min / 60} h"
            else -> dateTime(epoch)
        }
    }

    fun number(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(locale, "%.2f", v)
}
