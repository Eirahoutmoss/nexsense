# NexSense — Görünmeyen dünyayı görünür kılar

Nex uygulama ailesinin mekânsal sensör uygulaması (Android). Telefonun yerleşik sensörlerinden ölçüm toplar, ölçümleri zaman ve **kullanıcının kendi referans noktasına göre** konumla ilişkilendirir ve sonuçları katmanlı, etkileşimli bir 3B sahnede gösterir.

**0.5.0'da yeni — Dinleme ve kaynak bulma:** arama-kurtarmadaki "sessiz an" telefonla: vuruş, ses benzeri sinyal ve (yüzeye dayalıyken) yapıdan gelen sarsıntı algılama; "3'lü gruplar" gibi bilinçli vuruş deseni; birkaç telefonun kayıtlarını (`.nexlisten.json`, ses içermez) dosyayla paylaşıp birleştirerek saat hizalama ve düzey farklarından **yaklaşık kaynak bölgesi**; bilinen yerde yapay vuruş çalan **tatbikat** modu (hata ve mikrofon farkı ölçümü). Tasarım notları: [`docs/DINLEME.md`](docs/DINLEME.md).

**0.4.0'da yeni — Her ortamda haritalama:** köşe ya da duvar varsaymayan **boşluk haritası** (boş / dolu / bilinmeyen hücreler), olduğun yerde dönerken her yöne ses darbesi gönderen **akustik radar** ve haritanın bilinmeyen kenarına yönlendiren **serbest keşif** görevi. Mağara, koridor, açık alan ya da yıkıntı çevresi gibi oda olmayan yerler için. Mekân ekranında ve PDF'te boşluk haritası. Tasarım notları: [`docs/KESIF.md`](docs/KESIF.md).

**0.3.0'da yeni — Rehberli Keşif:** meraklı kullanıcı için. Uygulama tek tek ne yapacağını söyler ("telefonu diz hizasına indir", "2 m ileri yürü", "yavaşça dön", "masaya bırak"), sensörlerle hareketi denetler, gerekirse düzeltir ("biraz daha yukarı", "yavaşla") ve veriyi doğru anda kendisi kaydeder. Sesli yönerge, titreşim, "neden?" açıklamaları, atlanabilir adımlar ve dürüst kalite özeti var. Tasarım notları: [`docs/REHBER.md`](docs/REHBER.md).

**0.2.0'da yeni:** koordinat yazmadan **otomatik ölçüm** (başlat ve yürü: ARCore ile konum izleme, nokta bulutu, duvar/zemin düzlemleri; ARCore yoksa adım sayımı), **sabit nokta akustiği** (yankı süresi RT60, sonar mesafe, kalibre ses basıncı, akustik yön taraması), **analiz** (eşik, anomali, ilişki, zaman çizgisi, IDW tahmini) ve **raporlama** (PDF, 3B model PLY, vaka dosyası, onaylı paylaşım). Haricî sensörler (Faz 3) bilerek bekletiliyor.

> **Temel ilke:** Gerçek ölçüm, algoritmik tahmin ve bilinmeyen alanlar görsel olarak ayrıdır. Ölçülmeyen yer ölçülmüş gibi gösterilmez; desteklenmeyen sensör "Bu cihazda desteklenmiyor" der; demo verisi her yerde **DEMO VERİSİ** etiketiyle görünür.

- Konsept görseli: [`assets/NexSense-konsept.png`](assets/NexSense-konsept.png) (tasarım referansı; birebir kopya hedeflenmedi)
- Veri modeli: [`docs/VERI-MODELI.md`](docs/VERI-MODELI.md) · Yol haritası: [`ROADMAP.md`](ROADMAP.md)
- Türkçe + İngilizce (telefonun diline göre; Android 13+ "Uygulama dili" ayarı da çalışır)
- Çevrimdışı çalışır. İnternet yalnızca **Güncellemeleri denetle** düğmesi için kullanılır; ölçüm verisi hiçbir yere gönderilmez.

## Özellikler (MVP)

| Ekran | İçerik |
|---|---|
| **Ana Sayfa** | Projeler (oluştur, aç, yeniden adlandır, sil), ölçüme başla, demo projesi |
| **Rehberli keşif** | Görevler: İlk keşif, **Serbest keşif**, **Ses radarı**, Oda haritası, Duvar manyetik izi, Oda sesi (1–8 dk). Radar ekranı ve canlı boşluk haritası; Tek yönerge + büyük ok + ilerleme; sesli yönerge; doğrulanınca otomatik kayıt; sensör yoksa elle onay ("tahmini" işaretli); sonunda otomatik/onaylı/atlanan adımlar ve kalite |
| **Dinleme** | Dinle (gürültü tabanı, sesli çağrı, 20 sn sessiz an döngüsü, canlı olay listesi ve desen), Birleştir (içe al, seç, paylaş, saat hizalama, olay eşleme, belirsizlik bölgeli kuşbakışı harita, en yüksek düzeyle duyan), Tatbikat (bilinen yerde "3–3–3" çal, hata ve mikrofon farkı). Ses kaydedilmez |
| **Otomatik ölçüm** | Başlat → yürü. ARCore modu: telefonun konumu cm düzeyinde izlenir; her 0,25–2 m'de ölçüm noktası (konum + o anki sensör değerleri + yönelim) kendiliğinden kaydedilir; nokta bulutu (özellik noktaları + destekleyen cihazda derinlik), yatay/düşey düzlemler, yürüme yolu. Başlangıç = referans noktası ya da var olan bir nokta. ARCore yoksa adım modu (kaba, "tahmini konum") |
| **Sabit nokta: akustik** | Telefon sabit bir yerde: oda yankı süresi (oktav bantları, kalite, oda modları), yankı mesafesi (sonar, 3 yankıya kadar), ses düzeyi / yaklaşık dB SPL / baskın frekans, akustik yön taraması (yüzeylere doğrultarak 3B noktalar, deneysel). Sonuçlar seçilen noktaya kaydedilir |
| **Analiz** | Ölçüm yeterliliği, eşik uyarıları, anomali adayları (sağlam z), katman ilişkileri (Pearson), zaman çizgisi ve aynı yerde oturumlar arası değişim, yönden bağımsız manyetik bileşenler, IDW tahmin ayarları, yaklaşık oda boyutu |
| **Canlı** | Cihazın gerçekten sunduğu sensörler: manyetometre (x/y/z + toplam, µT), ivmeölçer, jiroskop, titreşim RMS, ortam ışığı, basınç, ortam sıcaklığı, nem; mikrofon seviyesi (dBFS, yalnızca izinle ve kullanıcı başlatınca). Her kartta birim, örnek zamanı, doğruluk ve kaynak sensör. Cihaz yönelimi. **Ölçüm noktası ekle** |
| **Mekân** | Göreli 3B sahne (tek parmak döndür, iki parmak yakınlaştır/kaydır, dokun seç). Katmanlar: geometri, manyetik alan, titreşim, akustik (dBFS), ses basıncı, yankı süresi, sıcaklık, nem, notlar, tarama (nokta bulutu, düzlemler, yollar). İsteğe bağlı tahmin (IDW, soluk taralı hücreler). Oturum filtresi (zaman çizgisi). **Boşluk haritası** (kuşbakışı; bilinmeyen beyaz kalır) |
| **Ölçümler** | Nokta listesi; konum/oturum/sensör filtreleri; ayrıntı (tüm örnekler, kaynak, kalite, yönelim); düzenle, sil |
| **Rapor** | Özet; CSV, JSON; **PDF rapor**, **3B model (PLY)**, **vaka dosyası (ZIP + SHA-256 listesi)**, **onaylı paylaşım**; taramalar; JSON içe alma; oturum geçmişi |
| **Ayarlar** | Tema (koyu varsayılan), örnekleme hızı, izin durumu, **ses kalibrasyonu**, **adım modu**, demo verisi, uygulama içi güncelleme, hakkında |

Yükleniyor, boş, hata ve izin-reddedildi durumları her ekranda ayrı tasarlandı; izin reddi uygulamayı çökertmez.

### Dürüstlük kuralları

- Ortam sıcaklığı yalnızca `TYPE_AMBIENT_TEMPERATURE` sensöründen okunur. **Batarya/CPU sıcaklığı yerine kullanılmaz.** Çoğu telefonda bu sensör yoktur.
- Mikrofon değeri **dBFS**'tir, kalibre değildir, dB SPL olarak sunulmaz. Ses kaydedilmez.
- GPS iç mekânda güvenilir olmadığı için kullanılmaz. Konum ya ARCore hareket izlemesinden (otomatik), ya adım sayımından (kaba tahmin, açıkça etiketli) ya da elle gelir. Konum yoksa `null` kalır ve nokta sahneye konmaz — sahte koordinat üretilmez. ARCore izlemeyi kaybettiği anda kayıt durur.
- Tarama gerçek bir API'ye dayanır (ARCore özellik noktaları, Depth API, düzlem algılama). ARCore'un "yukarı bakan yatay düzlemi" zemin de olabilir masa da; uygulama ayırt ediyormuş gibi yapmaz.
- **Tahmin ayrıdır:** varsayılan olarak yalnızca ölçülen noktalar renklenir. IDW tahmini yalnızca açılınca, ölçüme en fazla 1,5 m (ayarlanabilir) uzaklıkta, soluk ve taralı hücrelerle gösterilir; 4'ten az konumlu ölçümle hiç üretilmez.
- Akustik ölçümler telefon hoparlörü/mikrofonuyla yaklaşıktır: hoparlör yönlü değildir, bas zayıftır; yön taraması deneysel olarak işaretlidir. Ses ve görüntü diske yazılmaz.
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
- Otomatik ölçüm: `ar/ArCapture` (GLSurfaceView + ARCore 1.45.0, NexYapı ile aynı sürüm); konum dönüşümü, voksel seyreltme ve kayıt kuralı çekirdekte (`core/spatial`) test edilir.
- Akustik: `core/acoustic` (FFT, üstel tarama, Schroeder RT60 — NexTools'tan; sonar darbe hizalama, ses basıncı), `sensors/AcousticIo` (çal + kaydet).
- Analiz: `core/analysis`; rapor: `core/report` (PLY, vaka dosyası) + `export/` (PDF, sahne görüntüleri).
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
| Şema 2 | v1 dosyasının yükseltilmesi; tarama kalıcılığı ve içe almada kimlik eşleme; geçersiz tarama/eşik reddi; kuzey açısı |
| Konumlama | ARCore → proje çerçevesi, döndürme/hizalama, ENU → proje; voksel seyreltme; otomatik kayıt kuralı (izleme yokken asla); iz seyreltme; adım konumu ve güven; adım algılama |
| Akustik | RT60 (yapay odalarla, NexTools testleri), çoklu yankı sırası, bilinmeyen hoparlör gecikmesinde darbe hizalama, dBFS/ofset/baskın frekans |
| Analiz | IDW (ölçüm noktasında doğru, uzakta boş, az noktada yok), anomali, eşik, yeterlilik, ilişki, zaman çizgisi, manyetik dünya bileşenleri, oda boyutu |
| Rapor | PLY başlık/gövde tutarlılığı, vaka dosyası SHA-256 listesi |
| Dinleme | yapay vuruş dizisinden 9 vuruş ve "3'lü × 3" deseni; ses benzeri ile uğultunun ayrılması; titreşim kalibrasyonu ve bekleme süresi; düzenli/rastgele desen ve mikrofon+ivmeölçer çift sayımı; kayıt biçimi gidiş-dönüşü ve hatalı dosya reddi; saat kaymasının olay dizisinden bulunması (ortak olay yoksa uydurulmaması); 4 ve 3 dinleyiciyle konum ve belirsizlik; uçtan uca tatbikat (saat düzeltmesi, 5 dB mikrofon farkının bulunması, hata < 0,6 m) |
| Her ortamda haritalama | ışınla boşluk/dolu işaretleme, çelişen kanıt, Bresenham; koridorda sınırın yalnızca açık uçta çıkması; sınır seçimi; radar dilimleri, dönüş yönü, ışın güven sınırı; radar adımının her dilimi bir kez ölçmesi; sınır kalmayınca görevin bitmesi; demo verisinden harita; radar taramasının JSON gidiş-dönüşü ve geçersiz ışın reddi |
| Rehberli keşif | her adım türü yapay sensör akışıyla: durgunluk, 8 kalibrasyonu, yükseklik ipuçları, ileri/yana yürüme ve sapma düzeltmesi, dönüş ve hız uyarısı, başlangıca dönüş, köşe işaretleme, masaya bırakınca akustik, sensör yoksa dürüst elle onay, kalite ve konum kaybı sayımı |

**Gerçek cihazda denenmedi (0.1.0–0.5.0).** Compose arayüz testleri ve cihaz üstü testler bu sürümde yok; emülatörde de çalıştırılmadı (CI'da emülatör yok). Sensör okuma, izin akışı, 3B sahne hareketleri ve dosya kaydetme diyalogları gerçek telefonda elle denenmelidir:

1. Uygulamayı aç → **Yeni proje** → Canlı: sensör kartları dolmalı; olmayanlar "desteklenmiyor" demeli.
2. Ses seviyesini başlat → izni **reddet** → uygulama çökmemeli, kart "izin verilmedi" demeli.
3. **Ölçüm noktası ekle**: koordinatsız bir nokta, x/y/z'li iki nokta → Mekân: iki nokta sahnede, "1 nokta konumsuz".
4. Uygulamayı tamamen kapatıp aç → noktalar duruyor olmalı.
5. Katmanları aç/kapat; döndür, yakınlaştır, kaydır, noktaya dokun.
6. Rapor → CSV ve JSON kaydet → JSON'u içe al → kopya proje oluşmalı.
7. Canlı → **Rehberli keşif** → İlk keşif: sesli yönergelerle adımları yap; "biraz daha yukarı/aşağı", "yavaşla" uyarılarının doğru çıktığını ve sonunda özetin geldiğini gör. Oda sesi görevinde telefonu masaya bırakınca ölçüm kendiliğinden başlamalı.
8. Rehberli keşif → **Ses radarı**: olduğun yerde dön; her yönde bir an durunca "tık" sesleri çalmalı, radar ekranında dilimler dolmalı. **Serbest keşif**: radar sonrası haritada mavi (boş) alan ve sarı hedef halkası görünmeli; hedefe yürüyünce yeni radar başlamalı. Mekân → **Boşluk haritası**.
9. Canlı → **Dinleme**: Dinle → sessiz anda masaya üç kez, iki tur vur → olaylar "Vuruş" ve "3'lü gruplar, 2 kez" görünmeli; konuşunca "Ses benzeri". Kaydet → Paylaş. Üç-dört telefonla: hepsi "Tatbikat" açık dinlesin, biri Tatbikat sekmesinden çalsın; kayıtları bir telefonda içe al → Birleştir → tatbikat hatası ve mikrofon farkları.
10. Canlı → **Otomatik ölçüm** → ARCore → kamera izni → başlat, odada yavaşça bir tur at → noktalar kendiliğinden eklenmeli; Bitir → Mekân'da nokta bulutu, düzlemler, yol ve renkli noktalar.
11. ARCore'u kapatıp (ya da desteklemeyen telefonda) **Adım sayımı** ile aynı turu at → kesikli halkalı noktalar.
12. **Sabit nokta: akustik** → telefonu masaya koy → Oda akustiği, Yankı mesafesi, Ses düzeyi → "Noktaya kaydet"; Yön taraması ile birkaç yüzey ölç → Taramayı kaydet.
13. Rapor → PDF, PLY, vaka dosyası kaydet; Paylaş → onay ekranı çıkmalı. PLY'yi MeshLab/CloudCompare'de aç.

## Sınırlamalar

- Haricî sensör (BLE) yok — Faz 3 bilerek bekletiliyor; sıcaklık/nem çoğu telefonda "desteklenmiyor" görünür.
- ARCore yalnızca destekli telefonlarda (Google Play Hizmetleri – AR). Düz, desensiz duvarlarda ve karanlıkta izleme kaybolabilir. Ayrı taramalar ancak aynı başlangıç noktasıyla aynı çerçevede birleşir (otomatik eşleştirme yok).
- Adım modu kabadır: hata her adımda birikir, yükseklik ölçülmez.
- Dinlemede yer bulma genlik tabanlıdır (serbest alan varsayımı); duvar, enkaz ve yankı sonucu bozar. Sonuç bir ipucudur; profesyonel arama-kurtarma araçlarının yerini tutmaz.
- Akustik radar yönlü değildir (telefon hoparlörü her yöne çalar); halkalar düşük güvenle işlenir. Boşluk haritası 2B'dir (telefon yüksekliği bandı).
- Ses basıncı yalnızca kullanıcı ofsetiyle ve ağırlıksız (A/C yok) yaklaşık dB SPL'dir.
- Yalnızca Android. iOS planlanmadı.
