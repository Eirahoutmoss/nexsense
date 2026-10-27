package com.eirahoutmoss.nexsense.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Desteklenen ölçüm türleri, birimleri ve bileşenleri.
 * Birimler Android SensorEvent belgelerindeki birimlerdir; uygulama dönüşüm yapmaz.
 */
@Serializable
enum class SensorType(val key: String, val unit: String, val components: List<String>) {
    @SerialName("magnetic_field")
    MAGNETIC_FIELD("magnetic_field", "µT", listOf("x", "y", "z", "magnitude")),

    @SerialName("accelerometer")
    ACCELEROMETER("accelerometer", "m/s²", listOf("x", "y", "z", "magnitude")),

    @SerialName("gyroscope")
    GYROSCOPE("gyroscope", "rad/s", listOf("x", "y", "z", "magnitude")),

    @SerialName("light")
    LIGHT("light", "lx", listOf("value")),

    @SerialName("pressure")
    PRESSURE("pressure", "hPa", listOf("value")),

    /** Yalnızca gerçek ortam sıcaklığı sensörü (TYPE_AMBIENT_TEMPERATURE). Batarya/CPU sıcaklığı DEĞİL. */
    @SerialName("ambient_temperature")
    AMBIENT_TEMPERATURE("ambient_temperature", "°C", listOf("value")),

    @SerialName("relative_humidity")
    RELATIVE_HUMIDITY("relative_humidity", "%", listOf("value")),

    /** Doğrusal ivmenin (yerçekimsiz) kısa penceredeki RMS değeri — türetilmiş. */
    @SerialName("vibration_rms")
    VIBRATION_RMS("vibration_rms", "m/s²", listOf("rms")),

    /** Mikrofon seviyesi, tam ölçeğe göre desibel. Kalibre edilmemiştir; dB SPL değildir. */
    @SerialName("sound_level")
    SOUND_LEVEL("sound_level", "dBFS", listOf("rms", "peak")),
    ;

    companion object {
        fun fromKey(key: String): SensorType? = entries.firstOrNull { it.key == key }
    }
}
