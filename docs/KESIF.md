# Her ortamda haritalama — tasarım notları (0.4.0)

**Sorun:** "Oda haritası" görevi dört köşe varsayar. Gerçek dünya her zaman oda değildir: mağara, uzun koridor, kemerli bodrum, yıkıntı çevresi, ağaçlık, açık alan. Köşe yoksa köşe görevi anlamsızdır; duvar yoksa duvar düzlemi bulunmaz. Bir keşif aracı ortamın şeklini **varsaymamalı**, ölçerek öğrenmelidir.

**Yaklaşım:** Haritayı geometrik şekiller (düzlem, köşe) yerine **hücrelerle** tutmak. Bu, keşif robotlarının (ve arama-kurtarma robotlarının) kullandığı olasılıksal boşluk haritasıdır (occupancy grid). Her 10 cm'lik hücre üç durumdan birindedir: **boş**, **dolu**, **bilinmeyen**. Telefon her kanıtı (yürünen yol, kamera noktası, ses yankısı) hücrelere işler; kanıt biriktikçe güven artar, çelişen kanıt birbirini dengeler.

Kod: `core/spatial/Occupancy.kt` (harita, sınırlar, radar; test edilir), `core/guide/Guide.kt` (`RadarSweep`, `Frontier` adımları; `explore`, `radar` görevleri), `ui/screens/GuidedScreen.kt`, `ui/scene/OccupancyView.kt`.

## Boşluk haritası

Her hücre bir log-olasılık tutar (Bayes güncellemesi; Thrun, Burgard ve Fox, *Probabilistic Robotics*, 2005):

| Kanıt | Etkisi |
|---|---|
| Telefonun yolu (iz) | çevresi 25 cm yarıçapla **boş** (orada bir insan geçti) |
| Konumlu ölçüm noktası | çevresi 20 cm **boş** |
| ARCore noktası (telefon yüksekliğinin −1,2…+0,8 m bandında) | **dolu**, güvenle ağırlıklı; tavan ve zemin noktaları plana karışmaz |
| Akustik yön taraması noktası | zayıf **dolu** |
| Radar ışını | telefondan yankıya kadar **boş** (Bresenham çizgisi), yankı hücresi **dolu**; yankı yoksa 5 m'ye kadar zayıf boş |

Eşikler: log-olasılık ≥ 0,3 → dolu, ≤ −0,15 → boş, arası bilinmeyen; |L| en fazla 4. Güven = |L| / 4. Bilinmeyen hücre çizilmez; **ölçülmemiş yer boş gibi gösterilmez**.

## Sınır tabanlı keşif

Sınır (frontier; Yamauchi, 1997): boş olup dört komşusundan en az biri bilinmeyen hücre. Sınır hücreleri 8-bağlı kümelere ayrılır; 4 hücreden küçük kümeler gürültü sayılır. Gidilecek sınır puanla seçilir:

```
puan = büyüklük / (1 + uzaklık) × (1 − 0,3 × |dönüş açısı| / 180°)
```

Büyük, yakın ve önündeki sınır öne çıkar; 0,5 m'den yakın sınır atlanır (orası zaten görülüyor). "Serbest keşif" görevi: radar → sınıra yürü → radar → … (en fazla 5 tur). Haritada en az ~0,3 m² boş alan varken hiç sınır kalmazsa görev **kendiliğinden biter**: alan kapalı ve keşfedilmiş demektir.

Bu kural şekil varsaymaz: koridorda sınır yalnızca açık uçta çıkar (test: `frontierAppearsOnlyAtOpenEnd`), mağarada her dalın ucunda, açık alanda her yönde.

## Akustik radar

Kullanıcı olduğu yerde yavaşça döner; uygulama 12 dilimin (30°) her birinde telefon bir an durunca kısa bir sonar ölçümü alır (8 darbe, 3–9 kHz chirp, eşleşik süzgeç; Faz 4'teki sonar). Ölçülmemiş en yakın dilime "sola/sağa dön" yönergesi verilir.

**Dürüstlük:** Telefon hoparlörü ve mikrofonu yönlü değildir. İlk yankı baktığın yönden değil, en yakın büyük yüzeyden gelebilir. Bu yüzden:

- ışın güveni en fazla **0,6**'dır (sonar güveniyle de sınırlanır),
- yankısız ışın 0,2 güvenle "zayıf boş" sayılır,
- başarısız ölçüm (güven 0) haritaya hiç işlenmez,
- mikrofon izni yoksa radar adımı **atlanır**; sahte okuma üretilmez,
- ekranda "Halkalar tahmindir: telefon hoparlörü yönlü değildir." yazar.

Radar halkası tek başına bir harita değildir; ARCore noktaları ve yürünen yolla birleştiğinde anlam kazanır. Ses kaydedilmez; yalnızca yankı uzaklıkları tutulur.

## Veri

Radar ışınları `ScanMethod.ACOUSTIC_RADAR` taramasının `rays` alanında saklanır (şema 3): `{ox, oy, oz, x, y, z, hit, c}`. Harita kaydedilmez; her açılışta ham kanıttan yeniden kurulur. Böylece eşikler değişse bile eski veriler yeni haritaya dönüşür.

Görünür olduğu yerler: Rehberli keşifte radar ekranı ve canlı boşluk haritası, Mekân ekranında **Boşluk haritası** düğmesi, PDF raporda "Boşluk haritası" bölümü.

## Neden önemli (toplum açısından)

Bir telefonun kamerası, mikrofonu ve hareket sensörleri birlikte basit bir keşif aracıdır. Aynı mantık (boş/dolu/bilinmeyen, sınıra git, yeniden dinle) mağara araştırmacısının, okulda fen öğretmeninin ve ileride haricî sensörlerle donatılmış keşif cihazlarının ortak dilidir. NexSense bu dili ücretsiz ve açık bir biçimde, cebimizdeki cihazla öğretmeyi amaçlar. Haricî donanım bu sürümün kapsamında değildir.

## Sınırlar

- Harita 2B'dir (telefon yüksekliği bandı). Merdiven, katlar ve tavan yüksekliği değişen mağaralar tek düzlemde görünür.
- Adım modunda (ARCore yok) konum kabadır; sınır hedefleri ve radar ışınlarının yeri de kaba olur ("tahmini").
- Yumuşak yüzeyler (perde, kumaş, toprak) sesi az yansıtır; yankı çıkmayabilir.
- Gürültülü ortamda sonar güveni düşer.
- Gerçek cihazda denenmedi. Eşikler (hücre boyu, log-olasılık adımları, sınır küme boyu) ilk sahada ayarlanmalı.
- Güvenlik: Uygulama tehlikeli bir yere yönlendirebilir; çünkü yalnızca "bilinmeyen"i görür, "tehlikeli"yi bilemez. Her sınır adımı atlanabilir ve bu, "neden?" metninde açıkça söylenir.
