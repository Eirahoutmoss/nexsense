# NexSense yol haritası

> İlke: NexSense'in farkı her şeyi ölçtüğünü iddia etmesi değil; farklı ölçüm kaynaklarını tek bir mekânsal modelde birleştirmesi ve her bulgunun kaynağını göstermesidir.

| Faz | Durum |
|---|---|
| 1 — MVP: projeler, canlı sensörler, ölçüm noktası, göreli 3B sahne, katmanlar, CSV/JSON | ✅ 0.1.0 |
| 2 — Otomatik ölçüm ve tarama (ARCore + adım yedeği) | ✅ 0.2.0 |
| 3 — Haricî sensörler (BLE) | ⏸ bekletiliyor (kullanıcı kararı) |
| 4 — Akustik (RT60, sonar, kalibre ses basıncı, yön taraması) | ✅ 0.2.0 |
| 5 — Mekânsal örüntü motoru | ✅ 0.2.0 |
| 6 — Raporlama (PDF, 3B model, vaka dosyası, onaylı paylaşım) | ✅ 0.2.0 |
| Rehberli Keşif (meraklı kullanıcı için yönlendirmeli ölçüm) | ✅ 0.3.0 |

## Faz 1 — MVP (0.1.0) ✅

Proje yönetimi, oturumlar, canlı sensör paneli (desteklenmeyen → "Bu cihazda desteklenmiyor"; batarya/CPU sıcaklığı ortam sıcaklığı yerine kullanılmaz), elle konumlu ölçüm noktası, göreli 3B sahne, katmanlar, CSV/JSON, güncelleme düğmesi.

## Faz 2 — Otomatik ölçüm ve tarama (0.2.0) ✅

- **Otomatik ölçüm:** "Başlat" → telefon konumunu kendisi izler, her 0,25/0,5/1/2 m'de ölçüm noktasını kendisi kaydeder. Koordinat yazılmaz.
- **ARCore modu:** görsel-ataletsel izleme; özellik noktaları + (destekleyen cihazda) Depth API ile nokta bulutu (5 cm voksel, en fazla 30 000), yatay/düşey düzlemler, yürüme yolu.
- **Hizalama:** başlangıç = referans noktası (telefon yüksekliği girilir) ya da var olan bir nokta; yön düzeltmesi. Aynı başlangıçla yapılan taramalar aynı çerçevede birleşir.
- **Adım modu (ARCore yoksa):** adım algılama + pusula; "tahmini konum" (kesikli halka), güven her adımda düşer, z = tutma yüksekliği varsayımı.
- İzleme kaybolduğunda kayıt yapılmaz.

## Faz 3 — Haricî sensörler ⏸

BLE sıcaklık/nem, hava kalitesi (CO₂, VOC, PM), haricî titreşim sensörleri; sensör kimliği ve kalibrasyon; haricî hoparlörle bas akustiği. Şema hazır (`source = "external"`).

## Faz 4 — Akustik (0.2.0) ✅

- **Oda akustiği:** üstel tarama ×2, dürtü yanıtı, oktav bantlarında T20 → RT60, kalite değerlendirmesi, tarama ölçülerinden oda modları.
- **Yankı mesafesi (sonar):** 8 darbe, eşleşik süzgeç, hizalayıp ortalama; 3 yankıya kadar; ses hızı ortam sıcaklığından.
- **Ses basıncı:** dBFS + referans ölçerle bulunan ofset → yaklaşık dB SPL; baskın frekans.
- **Akustik yön taraması (deneysel):** sabit noktada telefonun doğrultulduğu yönde ilk yankı → 3B yapıya nokta. Proje kuzey açısıyla hizalanır.

## Faz 5 — Mekânsal örüntü motoru (0.2.0) ✅

Ölçüm yeterliliği işaretleri; eşik uyarıları; sağlam z-puanıyla anomali adayları; katmanlar arası Pearson ilişkisi; zaman çizgisi (oturum özeti, aynı yerde oturumlar arası değişim, sahnede oturum filtresi); yönden bağımsız manyetik bileşenler; IDW tahmini (yalnızca kullanıcı açınca, sınırlı uzaklıkta, ölçümden görsel olarak ayrı); tarama noktalarından yaklaşık oda boyutu.

## Faz 6 — Raporlama (0.2.0) ✅

PDF rapor (özet, perspektif ve katman başına plan görüntüleri, tablolar, analiz, yöntemler); PLY 3B model; vaka dosyası (ZIP: JSON, CSV, PDF, PLY, SHA-256 bütünlük listesi); açık onaylı paylaşım.

## Rehberli Keşif (0.3.0) ✅

Uygulama kullanıcıya eylem söyler (kaldır, indir, yürü, dön, köşeye git, masaya bırak), sensörlerle doğrular, düzeltir ve veriyi kendisi kaydeder. 4 görev, sesli yönerge, titreşim, "neden?" açıklamaları, dürüst kalite özeti. Ayrıntı: `docs/REHBER.md`.

Sonra: gerçek kullanıcı denemeleriyle eşik ayarı; görevleri birbirine bağlayan "keşif yolu" (kullanıcının önceki sonuçlarına göre bir sonraki görevi önermek); 3B sahnede adım hedefini gösteren AR oku.

## Sonraki küçük işler

- Cihaz üstü (instrumented) ve Compose arayüz testleri.
- ARCore taramalarını birbirine otomatik hizalama (ortak düzlemlerle); şimdilik ortak başlangıç noktasıyla.
- Noktayı sahnede sürükleyerek taşıma.
