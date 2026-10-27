# NexSense yol haritası

> İlke: NexSense'in farkı her şeyi ölçtüğünü iddia etmesi değil; farklı ölçüm kaynaklarını tek bir mekânsal modelde birleştirmesi ve her bulgunun kaynağını göstermesidir. Önce sağlam bir ölçüm ve 3B görselleştirme çekirdeği; sonra sensörler, tarama ve örüntü analizi katman katman.

## Faz 1 — MVP (bu sürüm, 0.1.x)

- [x] Proje yönetimi: oluştur, listele, aç, yeniden adlandır, sil; veriler yeniden açılışta korunur
- [x] Ölçüm oturumları (başlat/bitir; ilk noktayla kendiliğinden açılır)
- [x] Canlı sensör paneli: manyetometre, ivmeölçer, jiroskop, titreşim RMS, ortam ışığı, basınç, ortam sıcaklığı, nem, mikrofon seviyesi (izinle, kullanıcı başlatınca)
- [x] Desteklenmeyen sensör → "Bu cihazda desteklenmiyor"; batarya/CPU sıcaklığı ortam sıcaklığı yerine kullanılmaz
- [x] Ölçüm noktası: anlık değerler + ad, not, zaman, kaynak, birim, doğruluk, cihaz yönelimi; koordinat bilinmiyorsa `null`
- [x] Kullanıcı tanımlı göreli referans sistemi (metre, x/y/z, tarif edilen orijin)
- [x] Etkileşimli göreli 3B sahne: döndür, yakınlaştır, kaydır, nokta seç, ayrıntı kartı
- [x] Katmanlar: geometri, manyetik alan, titreşim, akustik, sıcaklık, nem, notlar; "veri yok" durumu
- [x] Bilimsel dürüstlük: interpolasyon yok, ölçülmeyen nokta içi boş gri halka, demo verisi her yerde etiketli
- [x] CSV ve JSON dışa aktarma, JSON içe alma (kimlik çakışmasında kopya)
- [x] Ayarlar: tema, örnekleme hızı, izin durumu, demo projesi, uygulama içi GitHub güncellemesi (Nex standardı)
- [x] Çekirdek birim testleri, Android lint, GitHub Actions ile deneme APK'sı

**Bilinen eksikler (Faz 1 sonrası küçük işler):**
- Compose arayüz testleri ve cihaz üstü (instrumented) testler yok; elle deneme listesi README'de.
- Noktayı sahnede sürükleyerek taşıma yok (koordinat elle düzenlenir).
- Oturum notu düzenleme arayüzü yok (veri modelinde var).

## Faz 2 — Gelişmiş tarama

- ARCore Depth API destekleyen cihazlarda nokta bulutu ve düzlem (zemin/duvar) algılama
- AR çapası ile ölçüm noktasının konumunu otomatik doldurma (`positionSource = "arcore"`, `positionConfidence` dolu)
- Proje koordinat sistemini AR oturumuna hizalama (referans noktası işaretleme)
- Desteklemeyen cihazlarda bugünkü elle girişli göreli sahne aynen kalır

## Faz 3 — Haricî sensörler

- BLE sıcaklık/nem, hava kalitesi (CO₂, VOC, PM) ve titreşim sensörleri
- Sensör kimliği, kalibrasyon kaydı (`calibrationInfo`), `source = "external"`
- Bluetooth izinleri yalnızca bu özellik açılınca istenir

## Faz 4 — Akustik

- Kalibrasyon profiliyle yaklaşık dB SPL (kalibresiz değer yine dBFS olarak kalır)
- Spektrum (FFT), kontrollü darbe/eko ölçümü, RT60 ve oda akustiği metrikleri

## Faz 5 — Mekânsal örüntü motoru

- Zaman çizgisi ve oturum karşılaştırma
- Yönelim bilgisiyle vektörleri oda eksenlerine çevirme
- Yöntemi açıkça belirtilen interpolasyon (ör. IDW) — tahmin bölgeleri ölçümden görsel olarak ayrı
- Eşik uyarıları, katmanlar arası ilişki, anomali adayları, "ölçüm yetersiz" işaretleri

## Faz 6 — Raporlama

- PDF rapor, 3B görünüm görüntüsü, vaka dosyası
- Kullanıcı onayıyla paylaşım (varsayılan: veri cihazda kalır)
