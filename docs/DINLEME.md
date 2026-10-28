# Dinleme ve kaynak bulma — tasarım notları (0.5.0)

**Amaç:** Arama-kurtarma ekiplerinin "sessiz an" uygulamasını sıradan telefonlarla yapılabilir kılmak. Herkes susar, makineler durur, telefonlar vuruşları, sese benzeyen sinyalleri ve yapıdan gelen sarsıntıları dinler. Birkaç telefon farklı yerlerde dinlerse, kayıtları birleştirilince sesin **yaklaşık** nereden geldiği görülür.

Bu bir yardım aracıdır: profesyonel ekiplerin, dinleme cihazlarının (jeofon, akustik dinleme sistemleri) ve köpeklerin yerini tutmaz. Acil durumda önce 112 aranır. Haricî donanım bu sürümün kapsamında değildir.

Kod: `core/listen/` (algılama, desen, kayıt biçimi, saat hizalama, konumlama, tatbikat; test edilir), `ui/screens/ListenScreen.kt`, `data/ListenStore.kt`.

## Akış

```
Telefon A ─┐  dinle (sessiz an döngüsü)  → A.nexlisten.json ─┐
Telefon B ─┼─ dinle                       → B.nexlisten.json ─┼─► koordinatör: içe al → birleştir
Telefon C ─┘  dinle                       → C.nexlisten.json ─┘     saat hizala → olay eşle → yer
(Tatbikat: Telefon S bilinen yerde "3–3–3" çalar → S.nexlisten.json, gerçek konum ve hata)
```

## Algılama (ses kaydedilmez)

Mikrofon 16 kHz'de okunur; 10 ms çerçevelerin enerjisi (dBFS) izlenir.

- **Gürültü tabanı:** ilk 2 sn'nin ortancası; sonra sakin çerçevelerle yavaşça uyarlanır (aşağı hızlı, yukarı yavaş). 3 sn'den uzun düzgün ses (makine, rüzgâr) yeni taban olur.
- **Olay:** taban + 12 dB aşılınca başlar, taban + 6 dB'nin altında 30 ms kalınca biter.
- **Vuruş:** ≤ 0,25 sn ve tepe ilk 30 ms içinde (keskin başlangıç).
- **Ses benzeri:** ≥ 0,3 sn ve ses benzerliği ≥ 0,5. Ses benzerliği = 0,6 × konuşma bandı (300–3400 Hz) enerji payı + 0,4 × hecemsi dalgalanma (çerçeve düzeylerinin yayılımı). Bu bir insan sesi tanıma değildir; ekranda "ses benzeri" yazar.
- **Sarsıntı:** ivmeölçerde, ilk 2 sn'de öğrenilen ortalamadan max(6σ, 0,15 m/s²) sapma; 150 ms bekleme süresi. Telefon beton, boru ya da kirişe dayalıyken yapıdan iletilen vuruşu yakalar.

Ses örnekleri yalnızca bir olay süresince ve en fazla 1 sn bellekte tutulur, olay sınıflanınca atılır. Diske yazılan tek şey olayların zamanı, süresi ve düzeyidir.

## Desen: bilinçli vuruş

Enkaz altındaki kişiye genellikle "duyuyorsan üç kez vur" denir. Rastgele düşen taş ya da çıtırtı tek tek gelir; bilinçli vuruş gruplar hâlinde ve tekrarlı gelir.

- Mikrofon ve ivmeölçer aynı vuruşu 0,12 sn içinde duyarsa tek sayılır.
- Aralık 1 sn'den büyükse yeni grup başlar.
- En az iki kez tekrarlanan grup büyüklüğü (≥ 2) varsa "Bilinçli vuruş deseni olabilir: 3'lü gruplar, 2 kez".
- ≥ 4 vuruş düzenli aralıkla gelirse (değişim katsayısı < 0,25) "Düzenli vuruş".

"Olabilir" bilerek seçildi: desen bir ipucudur, kanıt değildir.

## Sessiz an protokolü

1. Gürültü ölçümü (2 sn): "Herkes sussun".
2. İsteğe bağlı sesli çağrı: "Sesimi duyan var mı? Duyuyorsan üç kez vur ya da seslen." Çağrı süresince (6 sn) duyulan sesler sayılmaz; yoksa telefon kendi sesini olay sanır.
3. 20 sn dinleme: "Kimse konuşmasın, yürümesin; makineler dursun."
4. Tekrar. "Bitir ve kaydet" denince kayıt oluşur.

## Kayıt biçimi: `.nexlisten.json`

```json
{
  "format": "nexlisten", "version": 1,
  "id": "…", "label": "Dinleyici A",
  "startEpochMs": 1761650000000, "durationS": 92.4, "fs": 16000,
  "micSource": "UNPROCESSED", "floorDbfs": -58.2, "splOffsetDb": null,
  "position": {"x": 2.0, "y": 0.0, "z": 0.0, "pointId": null, "estimated": true},
  "drill": false, "drillSource": null, "projectId": "…",
  "events": [{"t": 31.42, "kind": "knock", "durationS": 0.05, "peakDb": -21.3, "aboveFloorDb": 36.9, "voiceScore": 0.0}]
}
```

`kind`: `knock`, `voice_like`, `sound`, `vibration`. `peakDb` mikrofon olaylarında dBFS'dir, titreşimde 1 m/s² referanslı dB. `drillSource`: tatbikat kaynağının konumu ve vuruş başlangıçları (`emitT`). İçe almada biçim, sürüm, sonlu değerler ve olay zamanları denetlenir; dosya en fazla 4 MB ve 5000 olay olabilir.

Telefonlar arası alışveriş **dosya paylaşımıyla** yapılır: mesajlaşma, Bluetooth, e-posta. Uygulamanın kendisi ağa veri göndermez.

## Birleştirme

1. **Saat hizalama:** Telefon saatleri ağdan eşitlenir ama yüzlerce ms, ağsız ortamda saniyeler sapabilir. Aynı vuruş dizisini duyan iki telefonun olayları sabit bir kaymayla örtüşür. ±2 sn içinde en çok olayı (tolerans 80 ms) örtüştüren kayma aranır. En az 3 olay örtüşmezse kayma uydurulmaz: "saat hizalanamadı" yazar.
2. **Eşleme:** Her olay için diğer telefonlardan 0,25 sn içindeki en yakın olay. Ses 0,25 sn'de ~85 m yol alır; dinleyiciler arası gecikme bunun çok altındadır.
3. **Yer (genlik tabanlı):** Ses serbest alanda uzaklıkla 20·log10(r) dB zayıflar: L_i = L0 − 20·log10(r_i). Kaynak düzeyi L0 bilinmediği için yalnızca **düzey farkları** işe yarar. 25 cm'lik ızgarada her aday nokta için L0 en küçük kareyle kestirilir. Artıkların karesi tüm olay grupları boyunca toplanır. En küçük artık en olası noktadır; artığı en iyinin 1,5 dB fazlasını aşmayan hücreler **belirsizlik bölgesi** olur (turuncu). Bölge 4 m²'den büyükse "yalnızca kaba yön" uyarısı çıkar.
4. **En yüksek düzeyle duyan:** Yer bulunamasa da (2 telefon, konumsuzlar) "kaynak büyük olasılıkla bu telefona daha yakın" ipucu verilir.

Konum için **konumu bilinen en az 3 telefonun aynı olayı duyması** gerekir. Üç telefonla çözüm genellikle iki noktalıdır (iki Apollonius çemberinin kesişimi); dördüncü telefon bunu çözer. Testte 4 dinleyiciyle 0,4 m altı hata, 3 dinleyiciyle gerçek nokta bölge içinde.

## Tatbikat

Gerçek olaydan önce düzen denenir: bir telefon bilinen bir yerde "3–3–3" yapay vuruş sesi çalar (60 ms sönümlenen gürültü patlaması + 180 Hz gövde tınısı). Dinleyiciler "Tatbikat" açıkken dinler. Birleştirmede kaynak kaydı da seçilirse:

- tahminin gerçek kaynaktan **hatası (m)** gösterilir,
- telefonlar arası **mikrofon farkı** ölçülür (her telefonun beklenen düzeyden ortalama sapması, ortalaması sıfıra çekilmiş) ve düzeltme olarak uygulanır.

Tatbikat kayıtları her ekranda **TATBİKAT** etiketlidir; sinyal gerçek bir kişiden gelmez. Sonradan gerçek kayıtlarla karışmaz.

## Toplum açısından tasarım

- **Sessizlik bir toplumsal sözleşmedir.** Sessiz an ancak herkes uyarsa işe yarar. Ekran büyük harflerle ne yapılacağını söyler ve kuralı açıklar ("kimse konuşmasın, yürümesin; makineler dursun"). Kural uygulamaya değil, ortak amaca bağlanır.
- **Koordinasyon dosyayla:** Afet bölgesinde ağ çoğu zaman yoktur. Kayıtlar küçüktür (birkaç KB) ve Bluetooth ya da yakın paylaşımla geçebilir. Merkezi sunucu, hesap ya da kayıt gerekmez.
- **Yanlış umut vermemek:** Sonuç "önce buraya bak" ipucudur. Belirsizlik bölgesi her zaman çizilir; tek bir nokta gösterilip kesinlik izlenimi verilmez.
- **Gizlilik:** Ses kaydedilmez. Enkaz altındaki kişinin sesi bile saklanmaz; yalnızca "orada bir ses var" bilgisi tutulur.
- **Önce tatbikat:** Ekipler gerçek olaydan önce telefon yerleşimini ve sessiz anı dener. Tatbikat aynı zamanda telefonların mikrofon farkını ölçer.

## Sınırlar

- Genlik yöntemi serbest alan varsayar. Duvar, enkaz, boşluk ve yankı düzeyleri bozar. Kaynak bir boşluğun içindeyse ses en yakın açıklıktan geliyormuş gibi görünebilir.
- Telefon mikrofonları farklıdır, bazıları otomatik kazanç uygular (mümkünse işlenmemiş kaynak kullanılır). Tatbikatsız birleştirmede bu fark düzeltilmez.
- Rüzgâr, yağmur ve uzaktaki makineler tabanı yükseltir; zayıf vuruşlar kaçabilir.
- Varış zamanı farkı (TDOA) kullanılmaz: telefon saatleri ve ses donanımı gecikmeleri ms düzeyinde eşitlenemez.
- Gerçek cihazda ve gerçek ortamda denenmedi. Eşikler (taban + 12 dB, vuruş süresi, ses benzerliği) ilk tatbikatlarda ayarlanmalı.
