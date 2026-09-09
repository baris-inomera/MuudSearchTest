package analyzer;

import java.util.Locale;
import java.util.Map;

/**
 * Index'ler arasi harmanlama modeli — 277 UAT + 277 prod sorgusundan olculdu (04.09.2026).
 *
 * Raporda yazan "score" o sonucun KENDI index'i icindeki skorudur ve index'ler arasinda
 * dogrudan karsilastirilamaz. Her index ICINDE siralama ham skora gore azalir
 * (UAT'ta 7559/7560 = %99.99, prod'da 7713/7717 = %99.95). Index'ler ARASI birlestirme
 * bir agirlik carpani uzerinden yapilir.
 *
 * ==============================================================================
 * ONEMLI: PROD VE UAT FARKLI AGIRLIK KULLANIYOR.
 *
 * Testler UAT'ta kosuyor, bu yuzden BURADAKI DEGERLER UAT DEGERLERIDIR.
 *
 *                    UAT (testler burada)   PROD (kullanicilar burada)
 *   performers              0.38                    ~0.41
 *   playlists               0.335                   ~0.18
 *   songs                   0.27                    ~0.27
 *   albums                  0.15                    ~0.15
 *   videos                  0.00                    ~0.27
 *   (prod degerleri songs=0.27'ye normalize edilmis fit sonucudur)
 *
 * En carpici fark: UAT'ta playlists songs'un USTUNDE (0.335 > 0.27), prod'da ALTINDA.
 * Ve videos UAT'ta 0.00 iken prod'da songs ile ayni seviyede.
 *
 * DOGRULAMA: Bu tablo + playlist-intent carpani UAT'ta 1859 index-arasi ardisik ciftin
 * 1855'ini aciklar (%99.78). AYNI tablo prod'da yalnizca %54.31 aciklar. Yani tablo
 * dogrudur ama SADECE UAT icin.
 *
 * Bir arama davranisi hakkinda "kullanicilar boyle goruyor" demek icin prod'a bakmak
 * gerekir; bu analyzer'in gordugu her sey UAT'tir.
 * ==============================================================================
 *
 * PLAYLIST-INTENT CARPANI (yeni bulgu, eski modelde yoktu):
 * Sorgu bir tur/mood ifadesi oldugunda (Playlist ailesi) playlists agirligi ~4.0 ile
 * carpiliyor. UAT'ta 51 gozlemde medyan 3.87, prod'da 80 gozlemde medyan 3.96 — iki
 * ortamda da ayni. Index tanimindaki intentBoostEnabled=true alaninin gozlenen etkisi.
 * Bu carpan modele eklenince UAT aciklama orani %97.10'dan %99.78'e cikiyor.
 */
public final class IndexWeights {

    private IndexWeights() {}

    /**
     * UAT topHitsScoreWeight degerleri — muud.indexDefinitions.json'dan gelir ve
     * 277 UAT sorgusunda dogrulanmistir. Index tanimlarinda agirlik degisirse
     * SADECE burasi guncellenir.
     */
    private static final Map<String, Double> WEIGHTS = Map.of(
            "performers",      0.38,
            "playlists",       0.335,
            "songs",           0.27,
            "playlist_groups", 0.18,
            "albums",          0.15,
            "videos",          0.0
    );

    /** Playlist niyetli sorgularda playlists agirligina uygulanan carpan (iki ortamda da ~4.0). */
    public static final double PLAYLIST_INTENT_CARPANI = 4.0;

    /** apiResultType -> topHitsScoreWeight (UAT). Bilinmeyen tip icin 1.0 (notr). */
    public static double of(String apiResultType) {
        if (apiResultType == null) return 1.0;
        Double w = WEIGHTS.get(apiResultType.trim().toLowerCase(Locale.ROOT));
        return w == null ? 1.0 : w;
    }

    /**
     * Playlist-intent'i hesaba katan agirlik. Sorgu bir tur/mood ifadesiyse
     * (Playlist ailesi case'i) playlists ~4.0 carpan alir.
     */
    public static double of(String apiResultType, boolean playlistIntent) {
        double w = of(apiResultType);
        if (playlistIntent && "playlists".equalsIgnoreCase(String.valueOf(apiResultType).trim())) {
            w *= PLAYLIST_INTENT_CARPANI;
        }
        return w;
    }

    public static boolean isKnown(String apiResultType) {
        return apiResultType != null && WEIGHTS.containsKey(apiResultType.trim().toLowerCase(Locale.ROOT));
    }

    public static String describe() {
        return "UAT: performers=0.38, playlists=0.335, songs=0.27, playlist_groups=0.18, "
             + "albums=0.15, videos=0.00 (+ playlist niyetli sorgularda playlists x4.0) — "
             + "277 UAT sorgusunda 1855/1859 cift (%99.78). PROD FARKLI: orada playlists "
             + "songs'un ALTINDA ve videos songs ile ayni; ayni tablo prod'da %54 aciklar";
    }

    // ------------------------------------------------------------------
    // POPULERLIK — ALAN ADI ORTAMA GORE DEGISIYOR.
    // ------------------------------------------------------------------

    /**
     * Skorlamayi belirleyen populerlik alani ortama gore FARKLI:
     *
     *   UAT  -> `prodPopularity` (== `oldpopularity`). `popularity` alani UAT'ta
     *           performers/albums'te TUM kayitlarda 0 — UAT o alani hesaplamiyor.
     *   PROD -> `popularity`. Prod response'unda `prodPopularity` alani HIC YOK.
     *
     * Bu yuzden rapor hangi ortamda kosuluyorsa o ortamin alanini basmali; tek bir
     * alan adina sabitlemek digerinde bos deger uretir.
     */
    public static final String POP_ALANI_UAT  = "prodPopularity";
    public static final String POP_ALANI_PROD = "popularity";

    /**
     * Populerlik teriminin puan katsayisi (UAT olcumu):
     *     skor = taban + KATSAYI x prodPopularity
     *
     *   songs      = 0.58  — 143 ayni-adli grupta R2 > 0.9995
     *   performers = 0.85  — 5 grupta R2 = 1.00000
     *
     * PROD'DA BU MODEL TUTMUYOR: prod'da hicbir populerlik alani skoru dogrusal
     * aciklamiyor (4 grup, K degerleri 0.30-0.46 arasi tutarsiz) ve olcek tamamen
     * farkli (prod songs.popularity maks 2083.9, UAT prodPopularity maks 81.1).
     * Prod skorlama formulu ayrica olculmelidir.
     */
    private static final Map<String, Double> POP_COEF_UAT = Map.of(
            "songs",      0.58,
            "performers", 0.85
    );

    /** UAT populerlik katsayisi; olculmemis index'ler icin null. */
    public static Double popCoef(String apiResultType) {
        if (apiResultType == null) return null;
        return POP_COEF_UAT.get(apiResultType.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Skordan populerlik katkisini cikarip "taban skor"u dondurur (SADECE UAT).
     * Taban skorlar kumelenir; kumeler tier seviyelerini gosterir. Iki kume arasindaki
     * fark o sorgudaki tam-eslesme avantajidir — SABIT DEGIL, 5 sorguda 5.83-51.36
     * puan arasinda olculdu, her sorgu icin ayrica hesaplanmali.
     */
    public static Double tabanSkor(String apiResultType, double score, Double populerlik) {
        Double k = popCoef(apiResultType);
        if (k == null || populerlik == null) return null;
        return score - k * populerlik;
    }

    // ==================================================================
    // AKTIF ORTAM — kural metinlerindeki yer tutucular bunu okur.
    // BulguAnalyzer rapor dosya adindan ("..._PROD.xlsx") tespit edip ayarlar.
    // Varsayilan UAT: testler tarihsel olarak UAT'ta kosuyordu.
    // ==================================================================
    private static volatile String aktifOrtam = "UAT";

    public static void ortamAyarla(String o) {
        if (o != null && !o.isBlank()) {
            aktifOrtam = o.trim().toUpperCase(Locale.ROOT);
        }
    }

    public static String ortam() {
        return aktifOrtam;
    }

    public static boolean prodMu() {
        return "PROD".equals(aktifOrtam);
    }

    /** Bu ortamda skorlamayi belirleyen populerlik alani. */
    public static String popAlani() {
        return prodMu() ? POP_ALANI_PROD : POP_ALANI_UAT;
    }

    /** Bu ortam icin index bazli populerlik katsayisi — olculmediyse aciklayici metin. */
    public static String popKatsayiMetni(String apiResultType) {
        if (prodMu()) return "PROD icin olculmedi";
        Double k = popCoef(apiResultType);
        return k == null ? "olculmedi" : String.valueOf(k);
    }

    /**
     * "Taban skoru nasil cikarirsin" talimati. UAT'ta formul olculmustur;
     * PROD'da hicbir populerlik alani skoru dogrusal aciklamadigi icin
     * ayni talimati vermek yanlis olur.
     */
    public static String tabanFormulMetni() {
        if (prodMu()) {
            return "DIKKAT — PROD: populerlik katsayisi bu ortamda OLCULMEDI (hicbir populerlik "
                 + "alani skoru dogrusal aciklamiyor, denenen 4 grupta katsayi 0.30-0.46 arasi "
                 + "tutarsiz cikti). Taban skor cikarimi YAPMAYIN; ayni sorgudaki ham skorlari "
                 + "dogrudan karsilastirin ve `popularity` degerleriyle birlikte okuyun.";
        }
        return "Postman'den sorguyu kos, her songs kaydi icin `skor - 0.58 x prodPopularity` "
             + "hesapla. Cikan taban skorlar kumelenir; kumeler tier seviyeleridir. Iki kume "
             + "arasindaki fark bu sorgudaki gercek engeldir.";
    }

    /** Bu ortamin agirlik tablosu — tek satir. */
    public static String agirlikTablosuMetni() {
        if (prodMu()) {
            return "PROD (fit sonucu, songs=0.27'ye normalize): performers ~0.41 / playlists ~0.18 "
                 + "/ songs ~0.27 / albums ~0.15 / videos ~0.27";
        }
        return "UAT (indexDefinitions'dan, dogrulanmis): performers 0.38 / playlists 0.335 "
             + "/ songs 0.27 / albums 0.15 / videos 0.00";
    }

    /** Agirlik tablosunun bu ortamdaki aciklama gucu. */
    public static String dogrulamaMetni() {
        if (prodMu()) {
            return "PROD agirliklari 277 prod sorgusundan FIT EDILDI, index tanimindan "
                 + "okunmadi — kesinlestirmek icin prod indexDefinitions gorulmeli. UAT tablosu "
                 + "prod'da yalnizca %54 acikliyor, bu yuzden iki ortam ayri ele alinmalidir.";
        }
        return "Bu tablo + playlist-intent carpani UAT'ta 1859 index-arasi ardisik ciftin "
             + "1855'ini acikliyor (%99.78).";
    }

    public static String describePopCoef() {
        return "UAT populerlik katsayisi: songs=0.58 (143 grup, R2>0.9995), performers=0.85 "
             + "(5 grup, R2=1.0). Alan adi: UAT'ta prodPopularity, PROD'da popularity. "
             + "Prod skorlama formulu farkli ve henuz cozulmedi";
    }
}
