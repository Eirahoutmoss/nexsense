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

    /** Kullanıcının referans ölçerle girdiği ofsetle yaklaşık ses basınç düzeyi. Ofset calibrationInfo'da yazar. */
    @SerialName("sound_spl")
    SOUND_SPL("sound_spl", "dB SPL", listOf("leq", "peak")),

    /** Mikrofon spektrumunda baskın frekans. */
    @SerialName("dominant_frequency")
    DOMINANT_FREQUENCY("dominant_frequency", "Hz", listOf("frequency")),

    /** Yankı süresi (ISO 3382 benzeri, T20 → RT60), oktav bantları ve orta frekans ortalaması. */
    @SerialName("reverb_time")
    REVERB_TIME("reverb_time", "s", listOf("rt60_mid", "rt60_125", "rt60_250", "rt60_500", "rt60_1000", "rt60_2000", "rt60_4000")),

    /** Sonar: hoparlör → yüzey → mikrofon yankısından mesafe (ilk ve sonraki yankılar). */
    @SerialName("echo_distance")
    ECHO_DISTANCE("echo_distance", "m", listOf("first", "second", "third")),
    ;

    companion object {
        fun fromKey(key: String): SensorType? = entries.firstOrNull { it.key == key }
    }
}
