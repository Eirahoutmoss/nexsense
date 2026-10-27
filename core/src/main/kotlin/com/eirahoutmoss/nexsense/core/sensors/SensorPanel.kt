package com.eirahoutmoss.nexsense.core.sensors

import com.eirahoutmoss.nexsense.core.model.SensorType

/** Canlı panelde gösterilen ölçüm kanalları (kart başına bir tane). */
enum class Channel(val sensorType: SensorType, val needsPermission: Boolean = false) {
    MAGNETOMETER(SensorType.MAGNETIC_FIELD),
    ACCELEROMETER(SensorType.ACCELEROMETER),
    GYROSCOPE(SensorType.GYROSCOPE),
    VIBRATION(SensorType.VIBRATION_RMS),
    LIGHT(SensorType.LIGHT),
    PRESSURE(SensorType.PRESSURE),
    AMBIENT_TEMPERATURE(SensorType.AMBIENT_TEMPERATURE),
    HUMIDITY(SensorType.RELATIVE_HUMIDITY),
    MICROPHONE(SensorType.SOUND_LEVEL, needsPermission = true),
}

enum class PermissionStatus { GRANTED, NOT_ASKED, DENIED }

/** Bir kartın durumu. Arayüz her durumu ayrı tasarlar; hiçbiri çökme nedeni değildir. */
sealed interface ChannelState {
    /** Bu cihazda donanım yok: "Bu cihazda desteklenmiyor". */
    data object Unsupported : ChannelState

    /** İzin gerekiyor; kullanıcı düğmeye basınca istenir. */
    data object NeedsPermission : ChannelState

    /** Kullanıcı izni reddetti; ayarlardan açılabilir. */
    data object PermissionDenied : ChannelState

    /** Destekleniyor ama kullanıcı başlatmadı (yalnızca mikrofon). */
    data object Off : ChannelState

    /** Çalışıyor, ilk değer bekleniyor. */
    data object Waiting : ChannelState

    data class Live(val reading: Reading) : ChannelState
}

/** Canlı okuma. Bileşenler SensorType.components sırasındadır. */
data class Reading(
    val values: Map<String, Double?>,
    val timestampIso: String,
    val accuracy: String?,
)

object SensorPanel {
    /**
     * Kart durumunu belirler.
     * @param hardware cihazda bu sensör var mı
     * @param permission yalnızca izin gerektiren kanallar için anlamlıdır
     * @param userEnabled mikrofon gibi kullanıcı eylemiyle açılan kanallar için
     */
    fun state(
        channel: Channel,
        hardware: Boolean,
        permission: PermissionStatus,
        userEnabled: Boolean,
        reading: Reading?,
    ): ChannelState = when {
        !hardware -> ChannelState.Unsupported
        channel.needsPermission && permission == PermissionStatus.DENIED -> ChannelState.PermissionDenied
        channel.needsPermission && !userEnabled -> ChannelState.Off
        channel.needsPermission && permission != PermissionStatus.GRANTED -> ChannelState.NeedsPermission
        reading == null -> ChannelState.Waiting
        else -> ChannelState.Live(reading)
    }

    /** Ölçüm noktası eklenirken yalnızca gerçekten okunan (Live) kanallar kaydedilir. */
    fun recordable(states: Map<Channel, ChannelState>): Map<Channel, Reading> =
        states.mapNotNull { (c, s) -> (s as? ChannelState.Live)?.let { c to it.reading } }.toMap()
}
