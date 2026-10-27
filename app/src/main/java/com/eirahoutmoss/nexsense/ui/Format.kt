package com.eirahoutmoss.nexsense.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Fmt {
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

    fun dateTime(iso: String?): String = iso?.let { runCatching { dateTime.format(Instant.parse(it)) }.getOrNull() } ?: "—"
    fun time(iso: String?): String = iso?.let { runCatching { time.format(Instant.parse(it)) }.getOrNull() } ?: "—"

    /** Sayıyı kullanıcının yerel ayarıyla yazar; null "—" olur (sıfır değil). */
    fun num(v: Double?, digits: Int = 2): String =
        if (v == null) "—" else String.format(Locale.getDefault(), "%.${digits}f", v)

    fun coord(x: Double?, y: Double?, z: Double?): String? =
        if (x == null || y == null || z == null) null else "x ${num(x)} · y ${num(y)} · z ${num(z)} m"

    /** Kullanıcı girdisini sayıya çevirir ("1,5" ve "1.5" kabul). Boş → null. Geçersiz → NaN. */
    fun parseOptional(text: String): Double? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        return t.toDoubleOrNull()?.takeIf { it.isFinite() } ?: Double.NaN
    }

    fun digitsFor(unit: String): Int = when (unit) {
        "lx", "hPa" -> 1
        "rad/s", "m/s²" -> 3
        else -> 2
    }
}
