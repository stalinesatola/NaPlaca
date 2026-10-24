package ao.naplaca.app.data.remote

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

internal object TimeParse {
    // Aceita "2026-10-24T11:30Z", "2026-10-24T11:30:00Z", "2026-10-24T14:00:00+00:00".
    private val withOffset: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm")
        .optionalStart().appendPattern(":ss").optionalEnd()
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
        .appendOffset("+HH:MM", "Z")
        .toFormatter()

    private val local: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm")
        .optionalStart().appendPattern(":ss").optionalEnd()
        .toFormatter()

    /** Converte para epoch millis; sem fuso explícito assume UTC (padrão da TheSportsDB). */
    fun epochMillis(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val t = text.trim().replace(' ', 'T')
        return runCatching { OffsetDateTime.parse(t, withOffset).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(t, local).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrNull()
    }
}
