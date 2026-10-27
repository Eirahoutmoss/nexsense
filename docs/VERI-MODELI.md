# NexSense veri modeli — şema 1

Kaynak: [`core/src/main/kotlin/.../core/model/Model.kt`](../core/src/main/kotlin/com/eirahoutmoss/nexsense/core/model/Model.kt)

Her proje tek bir JSON dosyasıdır (`ProjectBundle`). Uygulama içinde `files/projects/<proje-id>.json` olarak saklanır; **JSON dışa aktarma aynı dosyanın kopyasıdır** ve içe alınabilir.

```json
{
  "format": "nexsense-project",
  "schemaVersion": 1,
  "project":  { ... },
  "sessions": [ ... ],
  "points":   [ ... ],
  "samples":  [ ... ]
}
```

## Genel kurallar

| Kural | Uygulama |
|---|---|
| Zaman damgası | ISO 8601, UTC (`2026-10-27T18:00:00.123Z`) |
| Birim | Her `SensorSample` bir `unit` taşır ve bu birim sensör türünün birimiyle **aynı** olmalıdır; değilse kayıt reddedilir |
| Eksik değer | `null`. Sıfırla karıştırılmaz. NaN/sonsuz kaydedilmez |
| Koordinat | Ya x, y, z üçü birden bilinir ya da üçü de `null`'dır. Yarım koordinat reddedilir; uygulama sahte koordinat üretmez |
| Kaynak | `device` (yerleşik sensör), `external` (haricî; Faz 3), `derived` (ham ölçümden hesaplanmış), `demo` (uydurma) |
| Sürüm | `schemaVersion` artınca `Migrations.upgrade` adımı eklenir; daha yeni şemalı dosya içe alınmaz |

## Project

| Alan | Tür | Not |
|---|---|---|
| `id` | string (UUID) | |
| `name` | string | boş olamaz |
| `description` | string | |
| `createdAt`, `updatedAt` | ISO 8601 | |
| `coordinateSystem` | nesne | aşağıda |
| `schemaVersion` | int | 1 |

### coordinateSystem

| Alan | Varsayılan | Anlam |
|---|---|---|
| `type` | `relative-cartesian` | Göreli kartezyen; GPS değil |
| `unit` | `m` | metre |
| `handedness` | `right` | sağ el kuralı |
| `axes` | `x: right along reference wall, y: forward into room, z: up` | |
| `origin` | `""` | Kullanıcının tarif ettiği referans noktası (ör. "salon kapısının sol alt köşesi, zemin") |

## Session

`id`, `projectId`, `startedAt`, `endedAt` (açık oturumda `null`), `notes`.
Bir projede aynı anda en fazla bir açık oturum vardır. Açık oturum yokken nokta eklenirse oturum kendiliğinden açılır.

## MeasurementPoint

| Alan | Tür | Not |
|---|---|---|
| `id`, `projectId`, `sessionId` | string | |
| `label`, `notes` | string | |
| `x`, `y`, `z` | number \| null | metre, proje koordinat sisteminde |
| `createdAt` | ISO 8601 | |
| `positionSource` | `manual` \| `none` \| `demo` | `manual`: kullanıcı elle girdi |
| `positionConfidence` | number (0..1) \| null | Elle girilen konumda bilinmediği için `null` |

## SensorSample

| Alan | Tür | Not |
|---|---|---|
| `id` | string | |
| `pointId` | string \| null | Noktaya bağlı olmayan örnekler için `null` (şemada hazır, MVP kullanmaz) |
| `sessionId` | string | |
| `sensorType` | enum | aşağıdaki tablo |
| `values` | nesne: bileşen → number \| null | sıra korunur |
| `unit` | string | sensör türünün birimi |
| `timestamp` | ISO 8601 | sensör olayının zamanı (açılıştan beri geçen nanosaniye duvar saatine çevrilir) |
| `source` | enum | `device`, `external`, `derived`, `demo` |
| `quality` | string \| null | Android doğruluk durumu: `high`, `medium`, `low`, `unreliable` |
| `confidence` | number (0..1) \| null | MVP'de hesaplanmaz (`null`) |
| `deviceOrientation` | nesne \| null | `azimuthDeg`, `pitchDeg`, `rollDeg`, `method` |
| `calibrationInfo` | string \| null | Kalibrasyon/türetme yöntemi |

### Sensör türleri

| `sensorType` | Birim | Bileşenler | Kaynak |
|---|---|---|---|
| `magnetic_field` | µT | x, y, z, magnitude | TYPE_MAGNETIC_FIELD |
| `accelerometer` | m/s² | x, y, z, magnitude | TYPE_ACCELEROMETER (yerçekimi dahil) |
| `gyroscope` | rad/s | x, y, z, magnitude | TYPE_GYROSCOPE |
| `light` | lx | value | TYPE_LIGHT |
| `pressure` | hPa | value | TYPE_PRESSURE |
| `ambient_temperature` | °C | value | **yalnızca** TYPE_AMBIENT_TEMPERATURE — batarya/CPU sıcaklığı kullanılmaz |
| `relative_humidity` | % | value | TYPE_RELATIVE_HUMIDITY |
| `vibration_rms` | m/s² | rms | türetilmiş: TYPE_LINEAR_ACCELERATION büyüklüğünün son ~100 örnekteki RMS'i |
| `sound_level` | dBFS | rms, peak | türetilmiş: PCM16 mono, `20·log10(rms/32768)`. **Kalibre değil, dB SPL değil.** Ses kaydedilmez |

## Eksen dönüşümleri

- Sensör eksenleri **cihaz koordinat sistemindedir** (Android `SensorEvent`: ekrana bakarken x sağ, y yukarı, z ekrandan dışarı). NexSense bunları dünya/oda eksenlerine **döndürmez**.
- Her örnekle birlikte cihaz yönelimi (`deviceOrientation`) saklanır: `SensorManager.getRotationMatrix(ivmeölçer, manyetometre)` → `getOrientation` → azimut/eğim/yuvarlanma (derece). İleride (Faz 5) vektörler bu bilgiyle oda eksenlerine çevrilebilir.
- Bu yüzden MVP'de karşılaştırma yalnızca **büyüklükler** (ör. manyetik alanın toplamı) üzerinden yapılır; x/y/z bileşenleri farklı yönelimde alınmışsa doğrudan karşılaştırılmamalıdır.
- Nokta koordinatları (x, y, z) ise **proje koordinat sistemindedir** ve kullanıcının referans noktasına göredir.

## CSV biçimi

Uzun biçim: her satır bir bileşendir. UTF-8 (BOM ile), `\r\n` satır sonu, RFC 4180 tırnaklama, ondalık ayırıcı nokta.

```
timestamp,project,session_id,point_id,point_label,sensor_type,component,value,unit,x,y,z,position_source,source,quality,confidence,calibration_info
```

Eksik değer ve bilinmeyen koordinat **boş hücredir**.
