package com.eirahoutmoss.nexsense.core

import com.eirahoutmoss.nexsense.core.model.SampleSource
import com.eirahoutmoss.nexsense.core.model.SensorSample
import com.eirahoutmoss.nexsense.core.model.SensorType
import com.eirahoutmoss.nexsense.core.model.Validation
import com.eirahoutmoss.nexsense.core.model.ValidationIssue
import com.eirahoutmoss.nexsense.core.sensors.Channel
import com.eirahoutmoss.nexsense.core.sensors.ChannelState
import com.eirahoutmoss.nexsense.core.sensors.PermissionStatus
import com.eirahoutmoss.nexsense.core.sensors.Reading
import com.eirahoutmoss.nexsense.core.sensors.SensorPanel
import com.eirahoutmoss.nexsense.core.signal.SoundLevel
import com.eirahoutmoss.nexsense.core.signal.VibrationWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorAndSignalTest {

    private val reading = Reading(mapOf("value" to 12.0), "2026-10-27T10:00:00Z", "high")

    @Test fun unsupportedSensorIsReportedNotFaked() {
        val s = SensorPanel.state(Channel.AMBIENT_TEMPERATURE, hardware = false, PermissionStatus.GRANTED, true, null)
        assertEquals(ChannelState.Unsupported, s)
    }

    @Test fun microphoneStatesFollowPermissionAndUserAction() {
        val mic = Channel.MICROPHONE
        assertEquals(ChannelState.Off, SensorPanel.state(mic, true, PermissionStatus.NOT_ASKED, false, null))
        assertEquals(ChannelState.NeedsPermission, SensorPanel.state(mic, true, PermissionStatus.NOT_ASKED, true, null))
        assertEquals(ChannelState.PermissionDenied, SensorPanel.state(mic, true, PermissionStatus.DENIED, true, null))
        assertEquals(ChannelState.PermissionDenied, SensorPanel.state(mic, true, PermissionStatus.DENIED, false, reading))
        assertEquals(ChannelState.Waiting, SensorPanel.state(mic, true, PermissionStatus.GRANTED, true, null))
        assertTrue(SensorPanel.state(mic, true, PermissionStatus.GRANTED, true, reading) is ChannelState.Live)
        assertEquals(ChannelState.Unsupported, SensorPanel.state(mic, false, PermissionStatus.GRANTED, true, reading))
    }

    @Test fun onlyLiveChannelsAreRecorded() {
        val states = mapOf(
            Channel.LIGHT to ChannelState.Live(reading),
            Channel.MICROPHONE to ChannelState.PermissionDenied,
            Channel.HUMIDITY to ChannelState.Unsupported,
            Channel.GYROSCOPE to ChannelState.Waiting,
        )
        assertEquals(setOf(Channel.LIGHT), SensorPanel.recordable(states).keys)
    }

    @Test fun vibrationRmsNeedsEnoughSamples() {
        val w = VibrationWindow(capacity = 20)
        repeat(5) { w.add(0.0, 0.0, 1.0) }
        assertNull("az örnek: ölçülmedi, sıfır değil", w.rms())
        repeat(20) { w.add(3.0, 4.0, 0.0) }
        assertEquals(5.0, w.rms()!!, 1e-9)
        w.clear()
        assertNull(w.rms())
    }

    @Test fun soundLevelDbfs() {
        val full = ShortArray(100) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        val lvl = SoundLevel.measure(full)!!
        assertEquals(0.0, lvl.peakDbfs, 0.01)
        assertEquals(0.0, lvl.rmsDbfs, 0.01)
        val silent = SoundLevel.measure(ShortArray(100))!!
        assertEquals(SoundLevel.FLOOR_DBFS, silent.rmsDbfs, 1e-9)
        assertNull(SoundLevel.measure(ShortArray(0)))
        val half = SoundLevel.measure(ShortArray(10) { 16384 })!!
        assertEquals(-6.02, half.rmsDbfs, 0.01)
    }

    @Test fun validationCatchesUnitsNullsAndNaN() {
        fun s(values: Map<String, Double?>, unit: String = "lx", ts: String = "2026-10-27T10:00:00Z") =
            SensorSample("i", null, "s", SensorType.LIGHT, values, unit, ts, SampleSource.DEVICE)

        assertTrue(Validation.sample(s(mapOf("value" to null))).isEmpty()) // null geçerli: "ölçülmedi"
        assertEquals(ValidationIssue.Code.NOT_FINITE, Validation.sample(s(mapOf("value" to Double.NaN))).single().code)
        assertEquals(ValidationIssue.Code.MISSING_UNIT, Validation.sample(s(mapOf("value" to 1.0), unit = " ")).single().code)
        assertEquals(ValidationIssue.Code.WRONG_UNIT, Validation.sample(s(mapOf("value" to 1.0), unit = "lux")).single().code)
        assertEquals(ValidationIssue.Code.UNKNOWN_COMPONENT, Validation.sample(s(mapOf("x" to 1.0))).single().code)
        assertEquals(ValidationIssue.Code.NO_VALUES, Validation.sample(s(emptyMap())).single().code)
        assertEquals(ValidationIssue.Code.BAD_TIMESTAMP, Validation.sample(s(mapOf("value" to 1.0), ts = "dün")).single().code)
        assertTrue(Validation.position(null, null, null).isEmpty())
        assertTrue(Validation.position(0.0, 0.0, 0.0).isEmpty())
        assertEquals(ValidationIssue.Code.PARTIAL_POSITION, Validation.position(1.0, 2.0, null).single().code)
    }

    @Test fun everySensorTypeHasUnitAndComponents() {
        for (t in SensorType.entries) {
            assertTrue(t.name, t.unit.isNotBlank())
            assertTrue(t.name, t.components.isNotEmpty())
            assertEquals(t, SensorType.fromKey(t.key))
        }
    }
}
