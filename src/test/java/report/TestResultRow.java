package report;

/**
 * Excel raporunda tek bir satırı temsil eder.
 *
 * Karşılaştırma alanları (specificType … specificSonuc):
 *   - Sadece MuudSearchApiAktifIndexTest'in sub-sheet satırlarında doldurulur (G1–G10).
 *   - G11 ve MuudSearchApiTekliTest satırlarında null olarak kalır.
 *   - ExcelTestReportWriter bu alanlara bakarak "karşılaştırma modu"nu otomatik devreye alır.
 */
public record TestResultRow(
        String caseId,
        String testName,
        String description,
        String expectedResult,
        String type,           // Genel test tipi ("general") veya spesifik tip
        String indexName,      // Genel test indeks adı
        String uatStatus,      // Genel test sonucu: "OK" / "NOK"
        String sonuc,          // Genel test detay mesajı
        // ---- Karşılaştırma alanları (AktifIndexTest sub-sheetleri için) ----
        String specificType,        // "performer" / "songs" / "playlist"  (null = yok)
        String specificIndexName,   // "3" / "5" / "4" (Mayıs 2026 güncel ID'leri)
        String specificStatus,      // "OK" / "NOK"
        String specificSonuc        // Tekli test detay mesajı
) {
    /**
     * TekliTest ve G11 satırları için 8-parametreli kısaltılmış constructor.
     * Karşılaştırma alanları otomatik olarak null atanır.
     */
    public TestResultRow(String caseId, String testName, String description,
                         String expectedResult, String type, String indexName,
                         String uatStatus, String sonuc) {
        this(caseId, testName, description, expectedResult, type, indexName,
             uatStatus, sonuc, null, null, null, null);
    }
}
