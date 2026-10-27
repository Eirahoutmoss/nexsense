# NexSense veri modeli — şema 2

Kaynak: [`core/src/main/kotlin/.../core/model/Model.kt`](../core/src/main/kotlin/com/eirahoutmoss/nexsense/core/model/Model.kt)

Her proje tek bir JSON dosyasıdır (`ProjectBundle`). Uygulama içinde `files/projects/<proje-id>.json` olarak saklanır; **JSON dışa aktarma aynı dosyanın kopyasıdır** ve içe alınabilir.

```json
{
  "format": "nexsense-project",
  "schemaVersion": 1,
  "project":  { ... },
  "sessions": [ ... ],
  "points":   [ ... ],
  "samples":  [ ... ],
  "scans":    [ ... ],
  "analysis": { ... }
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
| Sürüm | `schemaVersion` artınca `Migrations.upgrade` adımı eklenir; daha yeni şemalı dosya içe alınmaz. **1 → 2**: yeni alanlar varsayılanla gelir, ölçüm verisi değişmez |

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
| `northAngleDeg` | `null` | (şema 2) Manyetik kuzeyin proje +y ekseninden saat yönünde açısı; akustik yön taraması için |

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
| `positionSource` | `manual` \| `none` \| `arcore` \| `pdr` \| `demo` | `manual`: elle; `arcore`: ARCore hareket izleme (otomatik ölçüm); `pdr`: adım sayımı + pusula (kaba tahmin, z = tutma yüksekliği varsayımı) |
| `positionConfidence` | number (0..1) \| null | Elle: `null`. ARCore: 0,9. Adım: 0,6'dan başlar, her adımda 0,01 düşer (en az 0,1) |

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
| `deviceOrientation` | nesne \| null | `azimuthDeg`, `pitchDeg`, `rollDeg`, `method`, (şema 2) `rotationMatrix`: cihaz → Doğu/Kuzey/Yukarı, satır sıralı 9 değer |
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
| `sound_spl` | dB SPL | leq, peak | (şema 2) dBFS + kullanıcının referans ölçerle bulduğu ofset; ağırlıksız, yaklaşık |
| `dominant_frequency` | Hz | frequency | (şema 2) FFT (32768, Hann), 40–8000 Hz |
| `reverb_time` | s | rt60_mid, rt60_125 … rt60_4000 | (şema 2) üstel tarama ×2, Schroeder T20 → RT60; güvenilmez bant `null` |
| `echo_distance` | m | first, second, third | (şema 2) sonar: 8 darbe, eşleşik süzgeç, hizalanıp ortalanır; ses hızı ortam sıcaklığından (yoksa 20 °C) |

## Scan (şema 2)

| Alan | Tür | Not |
|---|---|---|
| `id`, `projectId`, `sessionId` | string | |
| `method` | `arcore` \| `pdr` \| `acoustic_echo` \| `demo` | |
| `startedAt`, `endedAt` | ISO 8601 | |
| `points` | `[{x,y,z,c,k}]` | proje koordinatı (m); `c` güven 0..1; `k`: `f` ARCore özellik noktası, `d` derinlik, `a` akustik. 5 cm vokselle seyreltilir, en fazla 30 000 |
| `planes` | `[{type, polygon, areaM2}]` | `horizontal_up` (zemin **ya da** masa), `horizontal_down`, `vertical`; köşeler `[x,y,z]` |
| `trajectory` | `[{t,x,y,z}]` | telefonun yolu, 10 cm'de bir |
| `alignment` | `{startPointId, offsetX/Y/Z, yawDeg, note}` | tarama çerçevesi → proje çerçevesi: önce z etrafında `yawDeg`, sonra öteleme |

**ARCore çerçevesi:** y yukarı, oturum başında orijin. Eşleme `(x, y, z)ₚ = (x, −z, y)ₐᵣ`; ilk izlenen karede telefonun konumu orijin, baktığı yatay yön +y olur; sonra `alignment` uygulanır.

## analysis (şema 2)

`thresholds: [{layer, min, max}]` (katman adı: `MAGNETIC`, `MOTION`, `ACOUSTIC`, `SPL`, `REVERB`, `TEMPERATURE`, `HUMIDITY`), `gridCellM` (IDW hücre boyu), `maxEstimateDistanceM` (bu uzaklıktan öte tahmin yok).

## Eksen dönüşümleri

- Sensör eksenleri **cihaz koordinat sistemindedir** (Android `SensorEvent`: ekrana bakarken x sağ, y yukarı, z ekrandan dışarı). NexSense bunları dünya/oda eksenlerine **döndürmez**.
- Her örnekle birlikte cihaz yönelimi (`deviceOrientation`) saklanır: `SensorManager.getRotationMatrix(ivmeölçer, manyetometre)` → `getOrientation` → azimut/eğim/yuvarlanma (derece). İleride (Faz 5) vektörler bu bilgiyle oda eksenlerine çevrilebilir.
- Analiz ekranındaki "yönden bağımsız manyetik bileşenler", kayıtlı `rotationMatrix` ile vektörü Doğu-Kuzey-Yukarı çerçevesine çevirir; yatay (H), düşey (Z) bileşen ve eğim açısı telefonun duruşundan bağımsızdır. (Doğu bileşeni tanım gereği ~0'dır, çünkü matris aynı manyetometreden hesaplanır.)
- Ham x/y/z bileşenleri farklı yönelimde alınmışsa doğrudan karşılaştırılmamalıdır.
- Nokta koordinatları (x, y, z) ise **proje koordinat sistemindedir** ve kullanıcının referans noktasına göredir.

## CSV biçimi

Uzun biçim: her satır bir bileşendir. UTF-8 (BOM ile), `\r\n` satır sonu, RFC 4180 tırnaklama, ondalık ayırıcı nokta.

```
timestamp,project,session_id,point_id,point_label,sensor_type,component,value,unit,x,y,z,position_source,source,quality,confidence,calibration_info
```

Eksik değer ve bilinmeyen koordinat **boş hücredir**.
