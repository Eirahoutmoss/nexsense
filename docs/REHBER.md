# Rehberli Keşif — tasarım notları

**Sorun:** Mühendis için ölçüm noktasını elle eklemek, koordinat girmek, hatta otomatik taramayı ayarlamak keyifli olabilir. Meraklı kullanıcı içinse bunlar giriş eşiğidir. Ne yapacağını bilmeyen kullanıcı ya hiç başlamaz ya da yarım, düzensiz veri toplar.

**Yaklaşım:** Uygulama kullanıcıya ne ölçeceğini değil, **ne yapacağını** söyler: "telefonu diz hizasına indir", "2 metre ileri yürü", "olduğun yerde yavaşça dön", "telefonu masaya bırak". Sensörler eylemin gerçekten yapıldığını denetler ve doğru anda veriyi kendisi kaydeder. Kullanıcı hareket eder; veri toplama işini program üstlenir.

Kod: `core/guide/Guide.kt` (görev motoru, test edilir), `ui/screens/GuidedScreen.kt` (ekran).

## Nasıl çalışır

```
sensörler (10 Hz) ─► GuideSignals ─► GuideEngine ─► GuideFrame
  ARCore / adım+pusula        konum, bakış yönü,        adım, ilerleme,
  barometre, yönelim,         göreli yükseklik,         ipucu (kaldır/indir/…),
  ivme, manyetometre          eğim, durgunluk           kaydedilecek veri
```

| Adım | Kullanıcıya söylenen | Doğrulama | Kaydedilen |
|---|---|---|---|
| HoldStill | "Telefonu kıpırdatmadan tut" | 1 sn penceresinde ivme aralığı < 0,4 m/s², süre dolana kadar | taban noktası (dinlenme değerleri) |
| Figure8 | "Havada yavaşça 8 çiz" | 8 pusula dilimi + yukarı/aşağı eğim görüldü ya da manyetometre "yüksek doğruluk" | — (kalibrasyon) |
| Height | "Diz / göğüs / baş hizası" | başlangıca göre göreli yükseklik ±12 cm içinde 1 sn | o yükseklikte nokta |
| Move | "2 m ileri yürü" / "duvara bakıp sağa kay" | bakış yönüne göre ilerleme; yanal sapma > 40 cm → "biraz sola/sağa"; > 1,3 m/s → "yavaşla"; ters yön uyarısı | her 0,5 m (duvar: 0,25 m) |
| Turn | "Yavaşça bir tur dön" | birikmiş dönüş açısı; > 120°/s → "daha yavaş" | her çeyrek tur |
| Return | "Başladığın yere dön" | hedefe yön → "sola/sağa dön", 40 cm içinde biter | — (yol hatası göstergesi) |
| Mark | "Köşeye git ve dur" | önceki işaretlerden ≥ 0,8 m uzak + 2 sn duruş | köşe noktası |
| PlaceFlat | "Telefonu masaya bırak" | eğim ve yuvarlanma < 10°, 2 sn durgun | oda yankısı + sonar + ses düzeyi |

**Konum kaynağı:** ARCore varsa santimetre düzeyi; yoksa adım sayımı + pusula (kaba, "tahmini") ve yükseklik için barometre (~8,4 m/hPa). Bir adımın ihtiyaç duyduğu veri telefonda yoksa adım **elle onaya** düşer ("Yaptım"). Kaydedilen konum ölçülmediği için **tahmini** işaretlenir (güven 0,2). Sahte ölçüm üretilmez.

## Tasarım ilkeleri (davranış ve toplum bilimi açısından)

1. **Bir anda tek yönerge (bilişsel yük).** Ekranda tek eylem, büyük bir ok ve ilerleme çubuğu var. Talimat listesi yok; kullanıcı okumakla değil yapmakla meşgul.
2. **Anında geri bildirim.** "Biraz daha yukarı", "tamam, böyle tut", "yavaşla". Davranış, sonucu hemen görülünce öğrenilir. Doğru anda kısa titreşim ve sesli yönerge (isteğe bağlı, eller ve gözler serbest kalsın diye) var.
3. **Yetkinlik ve anlam ("neden?").** Her adımın kısa bir gerekçesi var: Dünya'nın manyetik alanı (~45–50 µT, Türkiye'de ~55° eğim), yerçekimi tabanı (9,8 m/s²), sesin hızı (~343 m/s). Kullanıcı yalnız veri toplamaz, ne topladığını anlar. Bu yaklaşım yurttaş bilimi (citizen science) projelerinde katılımı sürdüren temel etkendir.
4. **Özerklik.** Her adım atlanabilir, görev her an bitirilebilir, sesli yönerge kapatılabilir. Zorlanan kullanıcı uygulamayı bırakır; seçeneği olan kullanıcı geri gelir.
5. **Suçlamayan dürüstlük.** Sonunda neyin sensörle ölçüldüğü, neyin onayla girildiği, neyin atlandığı açıkça yazılır. Kalite (iyi/orta/zayıf) bir not değil, verinin güvenilirlik göstergesidir. "Zayıf"ta mesaj kullanıcıyı değil koşulları işaret eder (ışık, desensiz duvar, sensör eksikliği) ve hareket gerektirmeyen bir görev önerir.
6. **Kısa görevler.** 1–3 dakika. Dikkat süresine sığar ve "bir şey bitirdim" duygusu verir; büyük işi küçük, tamamlanabilir parçalara böler.
7. **Oyunlaştırma yok.** Puan, rozet, seri yok; bunlar veri kalitesini değil sayıyı ödüllendirir ve acele ettirir. Ödül, Mekân ekranında beliren sonucun kendisidir.
8. **Gizlilik sözü görünür.** Kamera görüntüsü ve ses kaydedilmez; bu, görev seçim ekranında ve her akustik adımda söylenir.

## Görevler

| Görev | Süre | Ne toplar |
|---|---|---|
| İlk keşif | ~3 dk | taban, kalibrasyon, 3 yükseklik, çeyrek tur ölçümleri, 2 m kesit, dönüş hatası |
| Oda haritası | ~3 dk | tam tur taraması, 4 köşe (oda planı), dönüş |
| Duvar manyetik izi | ~2 dk | göğüs ve diz hizasında duvar boyunca 25 cm aralıklı manyetik profil |
| Oda sesi | ~1 dk | yankı süresi (RT60), yüzeylere mesafe, ses düzeyi, baskın frekans |

Yeni görev eklemek: `Missions` içine adım listesi + metinler (`step_*`, `why_*`, `mission_*`).

## Sınırlar

- Adım modu (ARCore yoksa) kabadır; "ileri" ve "dönüş" adımları pusula sapmasından etkilenir.
- Barometre kapı açılıp kapanınca ya da klimadan etkilenir; yükseklik adımları birkaç saniye dalgalanabilir.
- Gerçek cihazda kullanıcı testi henüz yapılmadı. İlk kullanıcı denemelerinde eşikler (durgunluk, tolerans, hız) ayarlanmalı.
