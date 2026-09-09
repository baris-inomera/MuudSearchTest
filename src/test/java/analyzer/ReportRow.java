package analyzer;

import java.util.List;

/**
 * Excel raporundaki tek bir test satiri (yazilmis dosyadan okunur).
 * Boylece analyzer, ExcelTestReportWriter'in ic yapisindan bagimsiz calisir
 * ve eski formatli raporlara da uygulanabilir.
 */
public final class ReportRow {

    public final int excelRow;      // 0-tabanli POI satir indeksi
    public final String caseId;
    public final String testName;
    public final String expected;
    public final String type;
    public final String status;     // OK / NOK
    public final String sonuc;

    private List<SearchHit> hits;   // lazy

    public ReportRow(int excelRow, String caseId, String testName, String expected,
                     String type, String status, String sonuc) {
        this.excelRow = excelRow;
        this.caseId   = nz(caseId);
        this.testName = nz(testName);
        this.expected = nz(expected);
        this.type     = nz(type);
        this.status   = nz(status).trim().toUpperCase();
        this.sonuc    = nz(sonuc);
    }

    public boolean isNok() {
        return !status.isEmpty() && !status.equals("OK");
    }

    public List<SearchHit> hits() {
        if (hits == null) hits = SonucParser.parseHits(sonuc);
        return hits;
    }

    public int expectedFoundRank() {
        return SonucParser.expectedFoundRank(sonuc);
    }

    public String headline() {
        return SonucParser.headline(sonuc);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
