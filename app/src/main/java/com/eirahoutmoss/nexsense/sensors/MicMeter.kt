package com.eirahoutmoss.nexsense.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.eirahoutmoss.nexsense.core.sensors.Reading
import com.eirahoutmoss.nexsense.core.signal.SoundLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Mikrofon seviyesi (dBFS). Yalnızca kullanıcı başlatınca ve izin varsa çalışır.
 * Ses KAYDEDİLMEZ: örnekler bellekte seviyeye çevrilip atılır. Değer kalibre değildir (dB SPL değildir).
 */
class MicMeter(private val context: Context) {

    val hasHardware: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _reading = MutableStateFlow<Reading?>(null)
    val reading: StateFlow<Reading?> = _reading.asStateFlow()

    private val _failed = MutableStateFlow(false)
    /** Mikrofon açılamadı (başka uygulama kullanıyor olabilir). */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (job?.isActive == true || !hasHardware || !hasPermission()) return
        _failed.value = false
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        _reading.value = null
    }

    // İzin start() içinde denetlenir; yine de reddedilirse SecurityException yakalanır.
    @SuppressLint("MissingPermission")
    private fun CoroutineScope.loop() {
        val rate = 44_100
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { _failed.value = true; return }
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate / 5 * 2))
        } catch (_: SecurityException) {
            _failed.value = true; return
        } catch (_: IllegalArgumentException) {
            _failed.value = true; return
        }
        try {
            if (rec.state != AudioRecord.STATE_INITIALIZED) { _failed.value = true; return }
            rec.startRecording()
            val buf = ShortArray(rate / 10) // 100 ms
            while (isActive) {
                val n = rec.read(buf, 0, buf.size)
                if (n < 0) { _failed.value = true; break }
                val lvl = SoundLevel.measure(buf, n) ?: continue
                _reading.value = Reading(
                    values = linkedMapOf("rms" to round2(lvl.rmsDbfs), "peak" to round2(lvl.peakDbfs)),
                    timestampIso = Instant.now().toString(),
                    accuracy = null,
                )
            }
        } catch (_: IllegalStateException) {
            _failed.value = true
        } catch (_: SecurityException) {
            _failed.value = true
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0
}
