package analyzer;

/**
 * Tek bir NOK satiri icin uretilen analiz sonucu.
 *
 * kategori degerleri (sabit enum — hem kural motoru hem LLM bunlara uymak zorunda):
 *   RANKING          Sonuc var ama yanlis sirada
 *   INDEX_MISSING    Sorgu sonuc dondurmuyor / alan aranmiyor
 *   SINONIM          Yazim / sinonim / analyzer kurali eksik veya yanlis esliyor
 *   AGIRLIK          Relevance weight (boost, topHitsScoreWeight, script) ayari gerekiyor
 *   YENI_GELISTIRME  Yeni ozellik gerekiyor
 *   YANLIS_BEKLENTI  Test case'in beklentisi hatali veya icerik ortamda yok
 *
 * kaynak: kararin nereden geldigi — kural id'si (ornek "K03-birebir-baslik-tier-bonusu"),
 *         "LLM", ya da "-" (analiz yapilamadi). Ekibin hangi satira guvenecegini bilmesi
 *         ve yanlis kurali duzeltebilmesi icin sart.
 *
 * guven: 0.0 - 1.0. Esik altinda kalanlar "INCELENMELI" olarak isaretlenir, ASLA gizlenmez.
 */
public record BulguAnalysis(
        String caseId,
        boolean bulgu,
        String kategori,
        String cozum,
        String devNotu,
        double guven,
        String kokNeden,
        String kaynak,
        String kanit,
        boolean analizEdilemedi
) {

    public static final java.util.List<String> KATEGORILER = java.util.List.of(
            "RANKING", "INDEX_MISSING", "SINONIM", "AGIRLIK", "YENI_GELISTIRME", "YANLIS_BEKLENTI");

    /** Ne kural eslesti ne LLM cagrilabildi: satiri kaybetme, deterministik olgularla doldur. */
    public static BulguAnalysis incelenmeli(String caseId, String kanit, String neden) {
        return new BulguAnalysis(caseId, true, "RANKING",
                "Otomatik analiz yapilamadi (" + neden + "). Yandaki 'Agirlikli Skor' sutunu ve "
                + "asagidaki olgular deterministik olarak hesaplandi; satir insan incelemesi bekliyor.",
                "Bu satir icin kurallar.json'a yeni bir kural eklenebilir — bkz. analyzer/README.md.",
                0.0, "BELIRSIZ", "-", kanit, true);
    }

    /** Yeni cozum metniyle kopya (regresyon on eki gibi eklemeler icin). */
    public BulguAnalysis withCozum(String yeni) {
        return new BulguAnalysis(caseId, bulgu, kategori, yeni, devNotu,
                guven, kokNeden, kaynak, kanit, analizEdilemedi);
    }

    /** Bulgu=false karari yukseltildiginde kullanilir. */
    public BulguAnalysis yukselt(String yeniCozum) {
        return new BulguAnalysis(caseId, true, kategori, yeniCozum, devNotu,
                guven, kokNeden, kaynak, kanit, analizEdilemedi);
    }

    public String bulguText() {
        if (analizEdilemedi) return "INCELENMELI";
        return bulgu ? "EVET" : "HAYIR";
    }

    public String guvenText() {
        if (analizEdilemedi) return "-";
        if (guven >= 0.8) return "Yuksek";
        if (guven >= 0.5) return "Orta";
        return "Dusuk";
    }
}
