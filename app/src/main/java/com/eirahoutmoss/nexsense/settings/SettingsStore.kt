package com.eirahoutmoss.nexsense.settings

import android.content.Context
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode(val code: String) {
    DARK("dark"), LIGHT("light"), SYSTEM("system");

    companion object {
        /** Varsayılan koyu (Nex ailesi). */
        fun fromCode(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: DARK
    }
}

/** Sensör örnekleme hızı. Yüksek hız daha çok pil tüketir. */
enum class SamplingRate(val code: String, val androidDelay: Int) {
    LOW("low", SensorManager.SENSOR_DELAY_NORMAL),
    NORMAL("normal", SensorManager.SENSOR_DELAY_UI),
    HIGH("high", SensorManager.SENSOR_DELAY_GAME);

    companion object {
        fun fromCode(code: String?): SamplingRate = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

data class Settings(
    val theme: ThemeMode,
    val sampling: SamplingRate,
    val activeProjectId: String?,
    /** Kullanıcı mikrofon iznini bir kez reddettiyse true; izin verilince sıfırlanır. */
    val micDenied: Boolean,
    /** Ses basıncı ofseti (dB SPL = dBFS + ofset). Referans ölçerle belirlenir; yoksa null (kalibre değil). */
    val splOffsetDb: Double?,
    /** Otomatik ölçümde iki nokta arası en az yer değiştirme (m). */
    val autoStepM: Double,
    /** Adım tabanlı modda adım boyu (m) ve telefonun tutulduğu yükseklik (m). */
    val pdrStepLengthM: Double,
    val pdrHoldHeightM: Double,
)

/** Uygulama ayarları. Cihazda saklanır; hiçbir yere gönderilmez. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private fun load() = Settings(
        theme = ThemeMode.fromCode(prefs.getString(K_THEME, null)),
        sampling = SamplingRate.fromCode(prefs.getString(K_SAMPLING, null)),
        activeProjectId = prefs.getString(K_ACTIVE, null),
        micDenied = prefs.getBoolean(K_MIC_DENIED, false),
        splOffsetDb = if (prefs.contains(K_SPL)) prefs.getFloat(K_SPL, 0f).toDouble() else null,
        autoStepM = prefs.getFloat(K_AUTO_STEP, 0.5f).toDouble(),
        pdrStepLengthM = prefs.getFloat(K_PDR_STEP, 0.7f).toDouble(),
        pdrHoldHeightM = prefs.getFloat(K_PDR_HEIGHT, 1.0f).toDouble(),
    )

    fun setSplOffset(db: Double?) {
        prefs.edit().apply { if (db == null) remove(K_SPL) else putFloat(K_SPL, db.toFloat()) }.apply()
        _state.update { it.copy(splOffsetDb = db) }
    }

    fun setAutoStep(m: Double) {
        prefs.edit().putFloat(K_AUTO_STEP, m.toFloat()).apply()
        _state.update { it.copy(autoStepM = m) }
    }

    fun setPdr(stepM: Double, heightM: Double) {
        prefs.edit().putFloat(K_PDR_STEP, stepM.toFloat()).putFloat(K_PDR_HEIGHT, heightM.toFloat()).apply()
        _state.update { it.copy(pdrStepLengthM = stepM, pdrHoldHeightM = heightM) }
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(K_THEME, mode.code).apply()
        _state.update { it.copy(theme = mode) }
    }

    fun setSampling(rate: SamplingRate) {
        prefs.edit().putString(K_SAMPLING, rate.code).apply()
        _state.update { it.copy(sampling = rate) }
    }

    fun setActiveProject(id: String?) {
        prefs.edit().putString(K_ACTIVE, id).apply()
        _state.update { it.copy(activeProjectId = id) }
    }

    fun setMicDenied(denied: Boolean) {
        prefs.edit().putBoolean(K_MIC_DENIED, denied).apply()
        _state.update { it.copy(micDenied = denied) }
    }

    private companion object {
        const val K_THEME = "theme"
        const val K_SAMPLING = "sampling"
        const val K_ACTIVE = "active_project"
        const val K_MIC_DENIED = "mic_denied"
        const val K_SPL = "spl_offset_db"
        const val K_AUTO_STEP = "auto_step_m"
        const val K_PDR_STEP = "pdr_step_m"
        const val K_PDR_HEIGHT = "pdr_height_m"
    }
}
