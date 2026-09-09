package analyzer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tek bir NOK satirinin YAPISAL olgulari.
 *
 * RunSignals bunu hesaplar; KuralMotoru bunun uzerinde calisir, PromptBuilder da
 * insan okunur haline (facts) cevirip LLM'e verir. Yani hem kural motoru hem LLM
 * AYNI olgu kumesini gorur.
 */
public final class RowContext {

    public String caseId = "";
    public String testTipi = "";
    /** testName icindeki tirnak ici sorgu: "a canim" arama yapilir -> a canim */
    public String sorgu = "";

    /** Beklenen icerigin bulundugu sira (1 tabanli); bulunamadiysa -1. */
    public int beklenenSira = -1;
    public boolean bulunamadi = true;

    /** Sonuclarda kac farkli index tipi var. */
    public int indexCesidi = 0;
    /** 1. siradaki sonucun index tipi. */
    public String ilkIndex = "";

    /**
     * Ham skoru daha YUKSEK olan bir kayit, farkli index'ten daha DUSUK skorlu
     * bir kaydin ALTINDA mi kalmis? (cross-index harmanlama etkisi)
     */
    public boolean agirlikDevrilmesi = false;
    /** Devrilmede ustte kalan (kazanan) kaydin index tipi. */
    public String devrilenUstIndex = "";
    /** Devrilmede altta kalan (ham skoru daha yuksek olan) kaydin index tipi. */
    public String devrilenAltIndex = "";

    /** Ilk N sonucta ayni kayit birden fazla mi. */
    public boolean duplikasyon = false;

    /** Bu satirdaki sonuclarin ait oldugu, populerligi tamamen 0/bos olan index'ler. */
    public final Set<String> oluPopulerlikIndexleri = new LinkedHashSet<>();

    /** Ilk N sonucta populerlik alani bos ('-') olan kayit sayisi. */
    public int populerlikEksik = 0;

    /** Onceki kosuda OK idi, bu kosuda NOK. */
    public boolean regresyon = false;
    /** Onceki kosuda da NOK idi. */
    public boolean kaliciBulgu = false;

    /** Adi sorguyla BIREBIR ayni olan sonuc sayisi (tier/exact bonusu gostergesi). */
    public int birebirBaslikEslesmesi = 0;

    /** Beklenen sonucun ustunde performer kaydi var mi. */
    public boolean ustteperformerVar = false;

    /**
     * 1. siradaki sonucun adi sorguyla BIREBIR ayni mi.
     * "selda" -> 'Selda' gibi: kisa/birebir isim, daha populer uzun ismi eziyor.
     * Bu, populerlik sinyali oldugunde teshisi kesinlestiren en guclu ipucu.
     */
    public boolean ilkSonucSorguylaAyni = false;

    /**
     * Sonuc metni bir API/altyapi hatasi mi anlatiyor ("API hatasi", "Failed to parse",
     * "bos sonuc")? Bu satirlar ARAMA BULGUSU DEGILDIR; kosunun kendisi basarisiz olmustur
     * ve basari orani hesabindan dusulmelidir.
     */
    public boolean apiHatasi = false;

    /**
     * Ayni sanatcinin farkli kayitlari (solo + "&"/virgullu collab + yalnizca harf
     * buyuklugu farkli kopya) top-N'de kac slot yiyor. 3+ ise katalog problemi.
     */
    public int sanatciVaryantSlot = 0;
    /** Slotlari yiyen sanatcinin adi (varyant tabani). */
    public String sanatciVaryantAdi = "";

    /**
     * POPULERLIK KARSILASTIRMASI — 04.09.2026'da mumkun hale geldi.
     *
     * Rapor artik skorlamanin gercekten okudugu alani (`prodPopularity`) basiyor, bu
     * yuzden ilk kez su soru cevaplanabiliyor: hedef icerik ustundekilerden daha mi
     * populer? Cevap teshisi ikiye ayiriyor:
     *   HEDEF_DAHA_POPULER    -> gercek siralama bulgusu (populerlik eziliyor)
     *   HEDEF_DAHA_AZ_POPULER -> ayni index icindeyse beklenti gozden gecirilmeli
     *   ESIT                  -> populerlik ayirt edemiyor, tie-breaker gerekiyor
     */
    public Double hedefPopulerlik = null;
    public Double ustEnYuksekPopulerlik = null;
    public String populerlikKarsilastirmasi = "YOK";
    /** Karsilastirilan iki kayit AYNI index'ten mi (elma-elma kiyasi mi)? */
    public boolean karsilastirmaAyniIndex = false;
    /** Hedefin ustunde kalan en populer kaydin etiketi ve index'i. */
    public String ustEnPopulerEtiket = "";
    public String ustEnPopulerIndex = "";

    /** Ilk N sonucun tamami tek bir index'ten mi geliyor (ornegin hepsi performers). */
    public boolean tekIndexBaskini = false;
    /** Sorgu kac kelimeden olusuyor. */
    public int sorguKelimeSayisi = 0;

    /**
     * Kazanan kuraldan BAGIMSIZ olarak bu satirda tetiklenen tum deterministik
     * sinyallerin kisa etiketleri. Rapordaki "Ek Sinyaller" sutununa yazilir —
     * boylece ikincil mekanizmalar (ornegin duplikasyon) tek kural kazandi diye
     * gorunmez olmaz.
     */
    public final List<String> ekSinyaller = new java.util.ArrayList<>();

    public List<SearchHit> hits = List.of();

    // ------------------------------------------------------------------

    /**
     * Hedefin konumunu dilbilgisi bozmadan yazar: "3. sırada" ya da "top-10 dışında".
     * Sablonlarda ". sirada" ekleyip {beklenenSira} kullanmak yerine {konum} tercih edin.
     */
    private String konumMetni() {
        return beklenenSira > 0 ? beklenenSira + ". sırada" : "top-10 dışında";
    }

    /** Kural metinlerindeki {yer tutucu}'lari doldurur. */
    public String doldur(String template) {
        if (template == null) return "";
        return template
                .replace("{sorgu}", sorgu)
                .replace("{testTipi}", testTipi)
                // {beklenenSira} ciplak sayi verir; sablonda ". sirada" seklinde kullanilir.
                // Hedef hic bulunamadiysa sayi yok — o yuzden {konum} kullanin, cumle bozulmasin.
                .replace("{beklenenSira}. sirada", konumMetni())
                .replace("{beklenenSira}. sıradan", beklenenSira > 0 ? beklenenSira + ". sıradan" : "top-10 dışından")
                .replace("{beklenenSira}. sırada", konumMetni())
                .replace("{konum}", konumMetni())
                .replace("{beklenenSira}", beklenenSira > 0 ? String.valueOf(beklenenSira) : "top-10 disi")
                .replace("{ilkIndex}", ilkIndex)
                .replace("{ustIndex}", devrilenUstIndex)
                .replace("{altIndex}", devrilenAltIndex)
                .replace("{oluIndex}", String.join(", ", oluPopulerlikIndexleri))
                .replace("{birebirSayisi}", String.valueOf(birebirBaslikEslesmesi))
                .replace("{varyantAdi}", sanatciVaryantAdi)
                .replace("{varyantSlot}", String.valueOf(sanatciVaryantSlot))
                .replace("{hedefPop}", hedefPopulerlik == null ? "-" : fmt(hedefPopulerlik))
                .replace("{ustPop}", ustEnYuksekPopulerlik == null ? "-" : fmt(ustEnYuksekPopulerlik))
                .replace("{ustEtiket}", ustEnPopulerEtiket)
                .replace("{ustPopIndex}", ustEnPopulerIndex)
                // --- ORTAMA BAGLI YER TUTUCULAR ---
                // Kural metinlerine sabit UAT olcumu yazmak yerine bunlar kullanilir;
                // boylece PROD raporunda PROD'a ait dogru bilgi basilir.
                .replace("{ortam}", IndexWeights.ortam())
                .replace("{popAlani}", IndexWeights.popAlani())
                .replace("{popKatsayiSongs}", IndexWeights.popKatsayiMetni("songs"))
                .replace("{popKatsayiPerformers}", IndexWeights.popKatsayiMetni("performers"))
                .replace("{tabanFormul}", IndexWeights.tabanFormulMetni())
                .replace("{agirlikTablosu}", IndexWeights.agirlikTablosuMetni())
                .replace("{dogrulamaOrani}", IndexWeights.dogrulamaMetni())
                .replace("{sorguKelime}", String.valueOf(sorguKelimeSayisi))
                .replace("{ustAgirlik}", fmt(IndexWeights.of(devrilenUstIndex)))
                .replace("{altAgirlik}", fmt(IndexWeights.of(devrilenAltIndex)));
    }

    private static String fmt(double d) {
        String s = String.format(Locale.ROOT, "%.3f", d);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
