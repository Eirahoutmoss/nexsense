# NexSense — Görünmeyen dünyayı görünür kılar

Nex uygulama ailesinin mekânsal sensör uygulaması (Android). Telefonun yerleşik sensörlerinden ölçüm toplar, ölçümleri zaman ve **kullanıcının kendi referans noktasına göre** konumla ilişkilendirir ve sonuçları katmanlı, etkileşimli bir 3B sahnede gösterir.

> **Temel ilke:** Gerçek ölçüm, algoritmik tahmin ve bilinmeyen alanlar görsel olarak ayrıdır. Ölçülmeyen yer ölçülmüş gibi gösterilmez; desteklenmeyen sensör "Bu cihazda desteklenmiyor" der; demo verisi her yerde **DEMO VERİSİ** etiketiyle görünür.

- Konsept görseli: [`assets/NexSense-konsept.png`](assets/NexSense-konsept.png) (tasarım referansı; birebir kopya hedeflenmedi)
- Veri modeli: [`docs/VERI-MODELI.md`](docs/VERI-MODELI.md) · Yol haritası: [`ROADMAP.md`](ROADMAP.md)
- Türkçe + İngilizce (telefonun diline göre; Android 13+ "Uygulama dili" ayarı da çalışır)
- Çevrimdışı çalışır. İnternet yalnızca **Güncellemeleri denetle** düğmesi için kullanılır; ölçüm verisi hiçbir yere gönderilmez.

## Özellikler (MVP)

| Ekran | İçerik |
|---|---|
| **Ana Sayfa** | Projeler (oluştur, aç, yeniden adlandır, sil), ölçüme başla, demo projesi |
| **Canlı** | Cihazın gerçekten sunduğu sensörler: manyetometre (x/y/z + toplam, µT), ivmeölçer, jiroskop, titreşim RMS, ortam ışığı, basınç, ortam sıcaklığı, nem; mikrofon seviyesi (dBFS, yalnızca izinle ve kullanıcı başlatınca). Her kartta birim, örnek zamanı, doğruluk ve kaynak sensör. Cihaz yönelimi. **Ölçüm noktası ekle** |
| **Mekân** | Göreli 3B sahne (tek parmak döndür, iki parmak yakınlaştır/kaydır, dokun seç). Zemin ızgarası 1 m. Açılıp kapanan katmanlar: geometri, manyetik alan, titreşim, akustik, sıcaklık, nem, notlar. Renk göstergesi, konumsuz nokta sayısı, referans noktası tarifi |
| **Ölçümler** | Nokta listesi; konum/oturum/sensör filtreleri; ayrıntı (tüm örnekler, kaynak, kalite, yönelim); düzenle, sil |
| **Rapor** | Özet, CSV ve JSON dışa aktarma, JSON içe alma, oturum geçmişi |
| **Ayarlar** | Tema (koyu varsayılan), örnekleme hızı, izin durumu, demo verisi, uygulama içi güncelleme, hakkında |

Yükleniyor, boş, hata ve izin-reddedildi durumları her ekranda ayrı tasarlandı; izin reddi uygulamayı çökertmez.

### Dürüstlük kuralları

- Ortam sıcaklığı yalnızca `TYPE_AMBIENT_TEMPERATURE` sensöründen okunur. **Batarya/CPU sıcaklığı yerine kullanılmaz.** Çoğu telefonda bu sensör yoktur.
- Mikrofon değeri **dBFS**'tir, kalibre değildir, dB SPL olarak sunulmaz. Ses kaydedilmez.
- GPS iç mekânda güvenilir olmadığı için kullanılmaz. Konum, projenin referans noktasına göre elle girilen metre cinsinden x/y/z'dir. Konum girilmezse `null` kalır ve nokta sahneye konmaz — sahte koordinat üretilmez. Yarım koordinat kabul edilmez.
- "Otomatik oda tarama" yoktur; bu sürümde gerçek derinlik/tarama altyapısı kullanılmıyor (bkz. ROADMAP Faz 2).
- **İnterpolasyon yok:** yalnızca ölçülen noktalar renklenir; bir katmanda ölçülmemiş nokta içi boş gri halkadır. Az noktadan ısı haritası üretilmez.
- Sensör eksenleri cihaz koordinatındadır ve oda eksenlerine döndürülmez (yönelim kaydedilir). Ayrıntı: [veri modeli → eksen dönüşümleri](docs/VERI-MODELI.md#eksen-dönüşümleri).
- NexSense profesyonel yapı güvenliği, sağlık ya da laboratuvar cihazı değildir.

## Mimari

```
core/   Saf Kotlin (Android'e bağımsız, JVM'de test edilir)
  model/     Project, Session, MeasurementPoint, SensorSample, SensorType, doğrulama (şema 1)
  store/     ProjectRepository + FileProjectStorage (proje başına atomik JSON dosyası)
  export/    CSV (uzun biçim), JSON kodlayıcı, şema geçişleri
  scene/     Vec3, yörünge kamerası, perspektif izdüşüm, nokta seçimi, zemin ızgarası
  layers/    Katman verisi (ölçüm/veri yok/demo/konumsuz), katman seçimi
  sensors/   Kanal durum makinesi (desteklenmiyor / izin / bekliyor / canlı)
  signal/    Titreşim RMS, ses seviyesi (dBFS)
  demo/      Etiketli örnek proje
  update/    SemVer, sürüm seçimi (Nex güncelleme standardı)
app/    Android (Kotlin, Jetpack Compose, Material 3)
  sensors/   SensorHub (SensorManager, yaşam döngüsüne bağlı), MicMeter (AudioRecord → seviye)
  data/      ProjectActions (IO iş parçacığı, hata → yerelleştirilmiş ileti)
  ui/        Ekranlar, 3B sahne (Compose Canvas), tema
  update/    GitHub sürüm denetimi, SHA-256 + imza doğrulamalı uygulama içi kurulum
```

- 3B sahne OpenGL/harici motor kullanmaz: Compose `Canvas` üzerinde perspektif izdüşüm (çekirdekte test edilen matematik). Hafif ve bağımlılıksız.
- Kalıcılık: `files/projects/<id>.json`, atomik yazma (`.tmp` → yeniden adlandırma). Okunamayan dosya silinmez, kullanıcıya bildirilir. Bulut yedeği kapalı.
- Sensörler yalnızca Canlı ekranı görünürken kayıtlıdır; arayüz güncellemesi ~10 Hz ile sınırlıdır (pil). Mikrofon ekrandan çıkınca kapanır.
- Bağımlılık sürümleri NexTools/NexYapı ile aynıdır (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01); yalnızca `kotlinx-serialization-json 1.7.3` eklendi.

## Gereksinimler

- Android 8.0 (API 26) ve üstü telefon
- Derlemek için: JDK 17+, Android SDK 35 (Android Studio Ladybug veya daha yenisi) — ya da hiçbir şey kurmadan GitHub Actions

## Derleme, test, çalıştırma

**Bilgisayarda kurulum gerekmeden:** Her PR'da ve `main`'e her gönderimde GitHub Actions çekirdek testlerini, Android lint'i ve deneme APK'sını çalıştırır. APK, iş akışının **Artifacts** bölümünde `NexSense-deneme` olarak; `main` için ayrıca Releases sayfasındaki **Deneme derlemesi** (ön sürüm) altında bulunur.

Yerelde (Windows):

```powershell
.\gradlew.bat :core:test           # çekirdek birim testleri
.\gradlew.bat :app:lintDebug       # Android lint (statik analiz)
.\gradlew.bat :app:assembleDebug   # deneme APK'sı → app\build\outputs\apk\debug\app-debug.apk
```

Deneme APK'sının paket adı `com.eirahoutmoss.nexsense.debug`'dır; asıl sürümle yan yana kurulur. APK'yı telefona kopyalayıp açın (bilinmeyen kaynaklara izin gerekir).

### İmzalı sürüm ve uygulama içi güncelleme

Proje kökünde `nexsense-release.jks` ve `keystore.properties` (ikisi de depoya girmez):

```properties
storeFile=nexsense-release.jks
storePassword=...
keyAlias=nexsense
keyPassword=...
```

`.\gradlew.bat :app:assembleRelease` ile üretilen APK, GitHub'da `vX.Y.Z` etiketli **kararlı** bir sürüme yüklenir; yanına `<apk adı>.sha256` dosyası **zorunludur**. Uygulamadaki **Güncellemeleri denetle** düğmesi yalnızca kararlı sürümleri görür, özeti ve imzayı doğrulamadan kurmaz. Her sürüm aynı anahtarla imzalanmalıdır.

## Testler

`core` birim testleri (JUnit, JVM):

| Alan | Testler |
|---|---|
| Proje | oluştur → kaydet → yeni depo örneğiyle yeniden aç → yeniden adlandır → sil; boş ad reddi; Türkçe karakterler |
| Ölçüm | nokta + örnek kalıcılığı, bileşen sırası, birim; bilinmeyen konum `null`; yarım koordinat reddi; hatalı birim reddi (hiçbir şey kaydedilmez); oturum aç/kapat; nokta güncelle/sil |
| Dışa/içe aktarma | CSV başlık, satır sayısı, boş hücre = `null`, RFC 4180 kaçışları; JSON gidiş-dönüş; aynı projeyi ikinci kez içe alma → yeni kimlikler; yabancı/bozuk JSON, yeni şema, oynanmış birim reddi; demo paketi geçerli ve tamamen etiketli |
| Sensör durumu | desteklenmeyen sensör; mikrofon: kapalı / izin gerekli / reddedildi / bekliyor / canlı; yalnızca canlı kanallar kaydedilir |
| Sinyal | titreşim RMS (az örnekte `null`), dBFS (0, −6.02, taban) |
| 3B sahne ve katmanlar | izdüşüm, kamera arkası, yükseklik yönü, kamera sınırları, dokunarak seçim, sığdırma; katman verisi (veri yok / demo / konumsuz), katman açma-kapama |
| Güncelleme | SemVer, sürüm seçimi (NexTools'tan) |

**Gerçek cihazda denenmedi.** Compose arayüz testleri ve cihaz üstü testler bu sürümde yok; emülatörde de çalıştırılmadı (CI'da emülatör yok). Sensör okuma, izin akışı, 3B sahne hareketleri ve dosya kaydetme diyalogları gerçek telefonda elle denenmelidir:

1. Uygulamayı aç → **Yeni proje** → Canlı: sensör kartları dolmalı; olmayanlar "desteklenmiyor" demeli.
2. Ses seviyesini başlat → izni **reddet** → uygulama çökmemeli, kart "izin verilmedi" demeli.
3. **Ölçüm noktası ekle**: koordinatsız bir nokta, x/y/z'li iki nokta → Mekân: iki nokta sahnede, "1 nokta konumsuz".
4. Uygulamayı tamamen kapatıp aç → noktalar duruyor olmalı.
5. Katmanları aç/kapat; döndür, yakınlaştır, kaydır, noktaya dokun.
6. Rapor → CSV ve JSON kaydet → JSON'u içe al → kopya proje oluşmalı.

## Sınırlamalar

- Konumlar elle girilir; AR/derinlik tabanlı konumlama yok (Faz 2).
- Haricî sensör (BLE) yok; sıcaklık/nem çoğu telefonda "desteklenmiyor" görünür (Faz 3).
- Ses seviyesi kalibre değildir (Faz 4).
- Interpolasyon/ısı haritası, eşik uyarıları, anomali analizi yok (Faz 5). PDF rapor yok (Faz 6).
- Yalnızca Android. iOS planlanmadı.
