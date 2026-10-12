# MUUD Arama — UAT vs PROD Karşılaştırmalı Test Raporu
**04.09.2026** · 277 case · kategori denetimi sonrası koşu · analiz motoru 22 kural (LLM'siz)

> Tüm değerler ölçülmüştür. Aile etiketi değişiklikleri hiçbir sonucu değiştirmedi:
> 277 case'ten yalnızca `SANAT_TAM_38`'in durumu değişti, o da beklentisi düzeltildiği için.

## Özet

277 case iki ortamda da koşuldu. Başarı oranları birbirine yakın ama **aynı case'leri
göstermiyorlar**. Testin bugün raporladığı oran PROD kullanıcısının gördüğü davranışı
temsil etmiyor.

|                    | UAT | PROD |
|--------------------|-----|------|
| Toplam case        | 277 | 277  |
| Başarılı           | 238 | 232  |
| NOK                | 39  | 45   |
| **Başarı oranı**   | **%86** | **%84** |

| Grup                  | Adet | Anlamı |
|-----------------------|------|--------|
| Sadece UAT'ta NOK     | 22 | Kullanıcı bu hatayı **görmüyor** — test yanıltıyor (UAT config farkı) |
| İkisinde de NOK       | 17 | Kesin motor bulgusu — hem üretimde var hem testte yakalanıyor |
| Sadece PROD'da NOK    | 28 | Kullanıcı bu hatayı **görüyor** ama test yakalamıyor |
| **Toplam farklı hata**| **67** | Örtüşme yalnızca **%25** (17/67) |

> **28 case PROD'da patlıyor, testte hiç görünmüyor.** Buna karşılık 22 case yalnızca
> UAT'ta patlıyor; bunlar için defect açmak kullanıcı etkisi olmayan işi kuyruğa sokar.

## Aile bazlı (04.09 kategori denetiminden sonra)

> "Yazım Toleransı" üç ayrı ES mekanizmasına bölündü; "Kısmi Ad"dan kelime-bazlı case'ler
> ayrıldı. Aile etiketi yalnızca rapor etiketidir — hangi doğrulayıcının koştuğunu
> `expArtist`/`expTrack` belirler, dolayısıyla etiket değişiklikleri sonucu etkilemez.
> Tek istisna `SANAT_TAM_38`: beklentisi düzeltildi, sonraki koşuda PROD OK / UAT NOK olacak.

| Test ailesi | Case | UAT NOK | PROD NOK | Δ | Okuma |
|---|--:|--:|--:|--:|---|
| Şarkı · Tam Eşleşme | 80 | 9 (%89) | 19 (%76) | +10 | PROD'un en büyük hata kaynağı; 19'un 7'si sıralama, 4'ü harmanlama, 5'i recall |
| Sanatçı · Kısmi Ad | 44 | 6 (%86) | 3 (%93) | −3 | PROD daha iyi |
| Sanatçı · Tam Eşleşme | 38 | 1 (%97) | 1 (%97) | 0 | SANAT_TAM_38 düzeltilince iki ortam eşitlendi |
| Playlist | 30 | 2 (%93) | 4 (%87) | +2 | playlists ağırlığı PROD'da songs'un altında |
| Şarkı · Lyrics | 15 | 8 (%47) | 2 (%87) | −6 | UAT'ın en kötü ailesi PROD'da çalışıyor |
| Sanatçı · Yazım · Harf Hatası | 12 | 1 (%92) | 0 (%100) | −1 | **Fuzzy eşleşmede sorun yok** |
| Şarkı · Kısmi Ad | 12 | 5 (%58) | 6 (%50) | +1 | Kelime-bazlı case'ler ayrılınca gerçek oran çıktı; en zayıf aile |
| Sanatçı · Kısaltma / Alias | 10 | 0 (%100) | 0 (%100) | 0 | Tek tutarlı temiz aile |
| Şarkı · Sanatçı + Şarkı | 10 | 7 (%30) | 2 (%80) | −5 | Cross-field PROD'da büyük ölçüde çözülmüş |
| Şarkı · Yazım · Boşluk | 5 | 0 (%100) | 1 (%80) | +1 | word_delimiter / shingle |
| Sanatçı · Yazım · Boşluk | 5 | 0 (%100) | 1 (%80) | +1 | word_delimiter / shingle |
| Şarkı · Yazım · Harf Hatası | 5 | 0 (%100) | 1 (%80) | +1 | Tek NOK: `dia` → "Dai Dai" |
| Sanatçı · Yazım · Diakritik | 4 | 0 (%100) | 1 (%75) | +1 | NOK mükerrer katalog kaydı |
| Şarkı · Kelime Bazlı Eşleşme | 4 | 0 (%100) | 1 (%75) | +1 | **Yeni aile**: kelime atlama / fazladan kelime / sıra |
| Şarkı · Yazım · Diakritik | 3 | 1 (%67) | 2 (%33) | +1 | Suitin en düşük PROD oranı |
| **TOPLAM** | **277** | **40 (%86)** | **44 (%84)** | **+4** | 67 farklı case, örtüşme %25 |

Son beş satır küçük örneklem (n≤5) — oranı tek başına aksiyona çevirmeyin.

## Kategori denetimi: 17 düzeltme

| Sınıf | Adet | Örnek | Yapılan |
|---|--:|---|---|
| A · Kategori yanlış | 7 | `SARKI_YAZIM_8` "arabam"→"Arabam": sorgu ile başlık birebir aynı, yazım toleransı test etmiyor — PROD'da 21. sırada gizlenmiş bir tam-eşleşme hatası. `SANAT_TAM_77` "değilim": Sanatçı ailesinde ama şarkı arıyor | Doğru aileye taşındı; sonuç değişmiyor |
| B · Beklenti verisi | 3 | `SANAT_TAM_38` "tan taşçı" → beklenti "Tan" yazıyordu; PROD 1. sırada "Tan Taşçı" döndürüyor. UAT'ta "Tan Taşçı" hiç yok, yani UAT **yanlış sebeple geçiyordu** | 2'si düzeltildi, `SARKI_LYRICS_14` karara bırakıldı |
| C · Alt tipe ayrıldı | 4 | "Kısmi Ad" altında kelime atlama / fazladan kelime / sıra değişikliği karışıktı | Yeni aile: Şarkı · Kelime Bazlı Eşleşme |
| D · Motor değil katalog | 3 | `SANAT_TAM_10` "çelik" → PROD 1. sırada diakritiksiz `celik`, asıl `Çelik` top-10'da yok. `SANAT_YAZIM_15` "beyonce" → üç ayrı kayıt | Ağırlıkla düzelmez; katalog dedup işi |

**Sorgu metinlerine dokunulmadı** — kasten hatalı yazılmış sorgular (`simarik`, `tarkn`, `kök$l`) olduğu gibi duruyor.

### En önemli sonuç

35 yazım case'i gerçekte üç bağımsız ES özelliğini test ediyordu:

| Yeni aile | Case | UAT NOK | PROD NOK | Hangi ES özelliği |
|---|--:|--:|--:|---|
| Harf hatası (`tarkn`→Tarkan) | 17 | 1 | 1 | fuzzy / `muud_dym` — **iki ortamda da çalışıyor** |
| Diakritik (`simarik`→Şımarık) | 7 | 1 | 3 | `asciifolding` / `turkish_lowercase` — **hataların yoğunlaştığı yer** |
| Boşluk (`karakedi`→Kara Kedi) | 10 | 0 | 2 | `word_delimiter` / shingle |

"Yazım toleransı PROD'da zayıf, levenstein kapalı" çıkarımı **yanlış olurdu**: 17 harf-hatası case'inin 15'i iki ortamda da geçiyor. Hatalar diakritik normalizasyonunda ve ikisi mükerrer katalog kaydından kaynaklanıyor.

## PROD mekanizma dağılımı (44 NOK)

`K20` bu koşuda ilk kez devrede ve tek başına en büyük kalem — önceki analizde "belirsiz"
görünen 12 satırın açıklaması.

| Kök neden | Kural | PROD NOK | Nerede düzeltilir |
|---|---|--:|---|
| Kısa sorgu performers substring eşleşmesine düşüyor | K20 | 12 | performers · `performerName` partial match → **A3** |
| exact/tier bonusu popülerliği eziyor | K03 | 11 | songs · `scriptSource` tier bonusu → **A1** |
| performerName.exact bonusu popülerliği eziyor | K02 K02a | 5 | performers · `extraBonusFields` |
| Kural yok (BELİRSİZ) | — | 5 | Önceki koşuda 12'ydi; K20 ile 5'e indi |
| performers ağırlığı songs'u eziyor | K09 | 3 | `topHitsScoreWeight` 0.38 vs 0.27 |
| lyrics adayları cross-index birleştirmede kaybediyor | K04 | 2 | songs · lyrics alan ağırlığı |
| Diğer (katalog dup., popülerlik ETL, cross-index, cross-field, hatalı beklenti, istisna) | K08 K12 K10 K07 K18 | 6 | Her biri 1 case |
| **TOPLAM** | 22 kural | **44** | Kural kapsamı %89 (39/44) |

**UAT tarafının kural etiketleri bu koşuda üretilmedi** — UAT raporunda analiz sütunları yok.
Aşağıdaki teşhis kırılımı ham response'lardan hesaplandığı için iki ortam için de eksiksizdir.

## Teşhis: hata hangi aşamada oluşuyor

67 hatalı case'in her biri için beklenen kayıt ham response'ta arandı.

| Teşhis | İkisi | Sadece PROD | Sadece UAT | Toplam | Aksiyon yeri |
|---|--:|--:|--:|--:|---|
| SIRALAMA — hedef topHits'te, beklenen sıranın altında | 8 | 11 | 19 | 38 | Ağırlık / bonus / popülerlik dengesi |
| TOP-30'DA YOK — hedef ilk 30'da hiç yok | 8 | 7 | 0 | 15 | Recall: analyzer, alan kapsamı, katalog |
| HARMANLAMA — index içinde var, topHits'e girmemiş | 1 | 6 | 3 | 10 | Cross-index birleştirme |
| BEKLENTİ / KATALOG — hedef 1. sırada ama test NOK diyor | 0 | 3 | 1 | 4 | Motor tarafında aksiyon yok |

Sadece UAT'ta patlayan 23 case'in **19'u saf sıralama** — hiçbirinde eşleşme kaybı yok. PROD'un 27'sinde ise 7 recall + 6 harmanlama kaybı var. İki ortamın hataları yalnızca farklı case'lerde değil, **farklı türde**.

## Aksiyonlar

### A1 — songs tier bonusunu popülerlik ölçeğine getir  ·  PROD 45 NOK'un 11'i  ·  K03
- **NEREDE:** songs index tanımı → `scriptSource` tier bonusu / `tierRankingEnabled`
- **NE YAP:** Birebir başlık eşleşmesine verilen tier bonusu popülerlik terimini tamamen
  eziyor; doğru şarkı ikinci tier'de kalıp top-3'ün dışına düşüyor. Bonusu popülerlik
  katkısıyla aynı ölçeğe indir.
- **YAPMA:** songs `topHitsScoreWeight` 0.27'yi yükseltme — Şarkı·Kısmi Ad ve Lyrics
  ailelerini bozar.
- **DOĞRULA:** PROD Şarkı·Tam Eşleşme 16 NOK → hedef <5; sonra 277 case PROD'da tekrar koşulur.

### A2 — UAT konfigürasyonunu PROD ile hizala  ·  23+27 case'in kök sebebi
- **NEREDE:** `muud.indexDefinitions.json` — UAT ve PROD kopyaları
- **ÖLÇÜLEN FARK:**

```
                 UAT              PROD
performers       0.38             ~0.41
playlists        0.335            ~0.18    <- UAT'ta songs'un USTUNDE, prod'da ALTINDA
songs            0.27             ~0.27
albums           0.15             ~0.15
videos           0.00             ~0.27
populerlik alani prodPopularity   popularity   (prodPopularity PROD'da HIC YOK)
```

- **NE YAP:** Ağırlık tablosunu ve popülerlik alanını iki ortamda eşitle; mümkün değilse
  regresyon paketini PROD'a taşı.
- **DOĞRULA:** Aynı 277 case iki ortamda koşulduğunda örtüşme %25 → >%80 çıkmalı.

### A3 — performerName kısmi eşleşmesine minimum kapsama oranı  ·  PROD 44 NOK'un 12'si (en büyük kalem)  ·  K20
- **NEREDE:** performers index tanımı → `performerName` partial match
- **NE YAP:** Sorgu, performer adının en az %60'ını kapsamıyorsa aday üretme.
  Ölçülen örnekler: `ara`→Funda ARAr · `erik`→ERIK Satie · `sus`→Arif SUSam ·
  `git`→Kırık GITar · `türkü`/`remix`→performers kayıtları
- **YAPMA:** performers `topHitsScoreWeight` 0.38'i düşürme — 45 case'lik Sanatçı·Kısmi Ad
  ailesini bozar.
- **DOĞRULA:** K20 bu koşuda 12 case etiketledi; kuralsız satır 12'den 5'e indi. Kapsama oranı şartıyla bu 12'nin çoğu OK olmalı.

### A4 — muud_dym yazım düzeltmesini koşullu hale getir  ·  UAT 7/277, PROD 1/277  ·  K19
- **NEREDE:** `muud_dym` düzeltme index'i
- **NE YAP:** Düzeltme yalnızca orijinal sorgu sıfır sonuç döndürdüğünde devreye girsin;
  düzeltilen kelimeye minimum benzerlik eşiği koy.
  Ölçülen hatalı düzeltmeler: mebrure→mecure · manisa→manga · gel lvbel→gel rebel · babalar→babacan
- **DOĞRULA:** SARKI_TAM_74 düzelmeli.

## Ölçüm notu

- Her sayı ölçüldü, tahmin değil. 277 UAT + 277 PROD sorgusunun ham JSON response'ları
  toplandı (`reports/api-responses/`, 116 MB).
- **Harmanlama modeli:** sıralama `skor × topHitsScoreWeight` ile yapılıyor. UAT'ta 1859
  index-arası ardışık çiftin 1855'ini açıklıyor (%99.78). Aynı tablo PROD'da yalnızca %54
  açıklıyor — iki ortamın farklı konfigürasyon koştuğunun doğrudan kanıtı budur.
- **Playlist-intent çarpanı ≈ 4.0:** tür/mood sorgularında playlists ağırlığı 4× alıyor
  (UAT 51 gözlem medyan 3.87 · PROD 80 gözlem medyan 3.96).
- **UAT skor formülü:** `skor = taban + 0.58 × prodPopularity` (songs, 143 grup, R²>0.9995);
  performers katsayısı 0.85 (5 grup, R²=1.0). PROD'da hiçbir popülerlik alanı skoru
  doğrusal açıklamıyor — PROD formülü ayrıca ölçülmeli.

**Ek:** `MUUD_Search_UAT_vs_PROD_20260904.xlsx` — Karşılaştırma sayfasında 277 case'in
tamamı (UAT/PROD durumu, grup, kural id, kök neden, aksiyon metni) filtrelenebilir halde.

## Bu koşuda kalan iki açık iş

1. **UAT raporunda analiz sütunları yok.** 40 hatanın kural etiketi üretilmemiş.
   `annotateLatest` klasördeki en yeni `TestReport_*.xlsx` dosyasını ADINA göre seçiyor —
   çıktı `uat.xlsx` gibi yeniden adlandırılırsa bulamaz. Analiz adımını UAT raporu üzerinde
   dosya adını değiştirmeden tekrar çalıştırın.
2. **K05 kuralı düzeltildi.** Koşulu eski aile adına (`"Yazım Toleransı"`) bağlıydı; aileler
   bölününce hiçbir zaman eşleşmez hâle gelmişti. Koşul `"Yazım"` olarak değiştirildi ve üç
   yeni yazım ailesinin hepsini kapsıyor. Sonraki koşuda 1–2 case'in kural etiketi K05'e
   dönecek; hataların kendisi etkilenmiyor.
