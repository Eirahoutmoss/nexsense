package com.eirahoutmoss.nexsense.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.eirahoutmoss.nexsense.core.model.DeviceOrientation
import com.eirahoutmoss.nexsense.core.sensors.Channel
import com.eirahoutmoss.nexsense.core.sensors.Reading
import com.eirahoutmoss.nexsense.core.signal.VectorMath
import com.eirahoutmoss.nexsense.core.signal.VibrationWindow
import com.eirahoutmoss.nexsense.settings.SamplingRate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * Yerleşik sensörleri keşfeder ve okur.
 *
 * - Yalnızca cihazın gerçekten sunduğu sensörler açılır; olmayanlar "desteklenmiyor" görünür.
 * - Ortam sıcaklığı yalnızca TYPE_AMBIENT_TEMPERATURE'dan okunur. Batarya/CPU sıcaklığı KULLANILMAZ.
 * - Dinleyiciler yalnızca Canlı ekran görünürken kayıtlıdır (pil). Ekran güncellemesi ~10 Hz ile sınırlıdır.
 * - Eksenler Android cihaz koordinat sistemindedir (ekrana bakarken x sağ, y yukarı, z ekrandan dışarı).
 */
class SensorHub(context: Context) {

    private val sm: SensorManager? = context.getSystemService(SensorManager::class.java)

    private val sensors: Map<Channel, Sensor?> = mapOf(
        Channel.MAGNETOMETER to sm?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
        Channel.ACCELEROMETER to sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
        Channel.GYROSCOPE to sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
        Channel.VIBRATION to sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
        Channel.LIGHT to sm?.getDefaultSensor(Sensor.TYPE_LIGHT),
        Channel.PRESSURE to sm?.getDefaultSensor(Sensor.TYPE_PRESSURE),
        Channel.AMBIENT_TEMPERATURE to sm?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE),
        Channel.HUMIDITY to sm?.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY),
    )

    fun hasHardware(channel: Channel): Boolean = sensors[channel] != null

    /** Sensörün adı ve üreticisi (kaynak bilgisi olarak gösterilir). */
    fun sensorInfo(channel: Channel): String? = sensors[channel]?.let { "${it.name} · ${it.vendor}" }

    private val _readings = MutableStateFlow<Map<Channel, Reading>>(emptyMap())
    val readings: StateFlow<Map<Channel, Reading>> = _readings.asStateFlow()

    private val _orientation = MutableStateFlow<DeviceOrientation?>(null)
    val orientation: StateFlow<DeviceOrientation?> = _orientation.asStateFlow()

    private var activeRate: SamplingRate? = null
    private val lastEmitMs = mutableMapOf<Channel, Long>()
    private val vibration = VibrationWindow(capacity = 100)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false
    private val rot = FloatArray(9)
    private val orient = FloatArray(3)

    fun start(rate: SamplingRate) {
        if (activeRate == rate) return
        stop()
        val manager = sm ?: return
        for ((_, s) in sensors) if (s != null) manager.registerListener(listener, s, rate.androidDelay)
        activeRate = rate
    }

    fun stop() {
        sm?.unregisterListener(listener)
        activeRate = null
        vibration.clear()
        hasGravity = false
        hasGeomagnetic = false
        lastEmitMs.clear()
        _readings.value = emptyMap()
        _orientation.value = null
    }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) = handle(e)
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun handle(e: SensorEvent) {
        val channel = sensors.entries.firstOrNull { it.value == e.sensor }?.key ?: return
        val v = e.values
        val values: Map<String, Double?> = when (channel) {
            Channel.MAGNETOMETER, Channel.ACCELEROMETER, Channel.GYROSCOPE -> {
                if (channel == Channel.ACCELEROMETER) { System.arraycopy(v, 0, gravity, 0, 3); hasGravity = true }
                if (channel == Channel.MAGNETOMETER) { System.arraycopy(v, 0, geomagnetic, 0, 3); hasGeomagnetic = true }
                val x = v[0].toDouble(); val y = v[1].toDouble(); val z = v[2].toDouble()
                linkedMapOf("x" to x, "y" to y, "z" to z, "magnitude" to VectorMath.magnitude(x, y, z))
            }
            Channel.VIBRATION -> {
                vibration.add(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                linkedMapOf("rms" to vibration.rms())
            }
            else -> linkedMapOf("value" to v[0].toDouble())
        }
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - (lastEmitMs[channel] ?: 0L) < EMIT_INTERVAL_MS) return
        lastEmitMs[channel] = nowMs
        // Bir değeri hiç hesaplanamayan okuma (ör. titreşim penceresi dolmadı) gösterilmez.
        if (values.values.all { it == null }) return

        val accuracy = if (channel == Channel.VIBRATION) null else accuracyName(e.accuracy)
        val reading = Reading(values, eventTimeIso(e.timestamp), accuracy)
        _readings.update { it + (channel to reading) }
        if (channel == Channel.ACCELEROMETER || channel == Channel.MAGNETOMETER) updateOrientation()
    }

    private fun updateOrientation() {
        if (!hasGravity || !hasGeomagnetic) return
        if (!SensorManager.getRotationMatrix(rot, null, gravity, geomagnetic)) return
        SensorManager.getOrientation(rot, orient)
        _orientation.value = DeviceOrientation(
            azimuthDeg = Math.toDegrees(orient[0].toDouble()),
            pitchDeg = Math.toDegrees(orient[1].toDouble()),
            rollDeg = Math.toDegrees(orient[2].toDouble()),
            method = "SensorManager.getRotationMatrix(accelerometer, magnetometer)",
        )
    }

    private fun eventTimeIso(eventNanos: Long): String {
        // SensorEvent.timestamp açılıştan beri nanosaniyedir; duvar saatine çevrilir.
        val ageMs = (SystemClock.elapsedRealtimeNanos() - eventNanos) / 1_000_000L
        return Instant.ofEpochMilli(System.currentTimeMillis() - ageMs.coerceAtLeast(0L)).toString()
    }

    private fun accuracyName(a: Int): String? = when (a) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "high"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "medium"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "low"
        SensorManager.SENSOR_STATUS_UNRELIABLE -> "unreliable"
        else -> null
    }

    private companion object {
        const val EMIT_INTERVAL_MS = 100L
    }
}
