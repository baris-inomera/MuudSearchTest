package tests.arama;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import report.ExcelTestReportWriter;
import report.TestResultRow;
import util.MuudSearchUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * PLAYLIST GROUP DEPLOYMENT TESTİ
 *
 * Amaç: muud_playlist_all_v2 (index 4) devreye alımını doğrular.
 *
 * Schema:
 *   data.itemKind  = "playlist" | "playlistGroup"
 *   data.itemName  = grup / playlist adı
 *   data.items[]   = alt öğeler
 *
 * TEK testi (indices/4): 30 case, assert eder.
 *   Deployment dedektörü — PROD'da 0/30 NOK → deployment sonrası 30/30 OK.
 *
 * AKT testi (active-indices, Tümü sekmesi): 30 case, bilgi amaçlı (assert yok).
 *   Tüm caseler Tümü'nde de koşulur; sonuçlar TEK ile yan yana raporda görünür.
 *   PG_25–PG_30 (AKT-A): Discovery'de Tümü'nde top-10'a giren keyword'ler.
 *   PG_01–PG_24: Listeler'e özgü; AKT'ta NOK beklenir, hata değildir.
 *
 * Çalıştırmak için:
 *   mvn test -Dtest=PlaylistGroupTest
 *   mvn test -Dtest="PlaylistGroupTest#playlist_tekli_group_kontrolu"
 *   mvn test -Dtest="PlaylistGroupTest#aktif_indeks_playlist_group_kontrolu"
 *
 * Çıktı: reports/playlist-group/PlaylistGroupTest_dd.MM.yyyy_HH.mm_ORTAM.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlaylistGroupTest extends TestConfig {

    private static final int TOP_N    = 20;
    private static final Locale TR    = Locale.forLanguageTag("tr");

    private static final String PLAYLIST_INDEX = "4";   // muud_playlist_all_v2

    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("▶ PlaylistGroupTest başlatıldı.");
        System.out.println("  Kontrol edilen alan: data.itemKind / data.itemName");
        System.out.println("  Tekli indeks: " + PLAYLIST_INDEX + " (muud_playlist_all_v2)");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        ExcelTestReportWriter.writeBulgu(ROWS, "reports/playlist-group", MuudSearchApi.ortam(), "PlaylistGroupTest");
    }

    // ── Case tanımı ───────────────────────────────────────────────────────────
    public record PgCase(String caseId, String term, String expGroupKeyword) {}

    /**
     * Discovery (TOP_N=20) sonucuna göre seçilmiş 30 case.
     *
     * PG_01–PG_24: TEK=OK + AKT=NOK — Listeler sekmesine özgü, grup Tümü'ne çıkmıyor.
     * PG_25–PG_30: TEK=OK + AKT=OK  — Tümü sekmesinde de grup çıkıyor (AKT-A seti).
     *
     * "expGroupKeyword" — itemName'de aranan kelime (kısmi eşleşme, TR ignore-case).
     * Sağ yorum — Discovery'de bulunan grup adı (referans).
     *
     * Generik grup adları ("Türe Göre", "Moda Göre", "Emre Kategorisi" vb.) elendi.
     */
    static Stream<PgCase> cases() {
        return Stream.of(
            // Spor & Sağlık
            new PgCase("PG_01", "pilates",        "Pilates"),         // → Pilates
            new PgCase("PG_02", "yoga",           "Yoga"),            // → Yoga ve Nefes
            new PgCase("PG_03", "fitness",        "Fitness"),         // → Fitness Motivasyonu
            new PgCase("PG_04", "sabah",          "Sabah"),           // → Sabah Egzersizi
            new PgCase("PG_05", "motivasyon",     "Motivasyon"),      // → Fitness Motivasyonu

            // Odak & Huzur
            new PgCase("PG_06", "huzur",          "Meditasyon"),      // → Meditasyon
            new PgCase("PG_07", "beyaz gürültü",  "Konsantrasyon"),   // → Konsantrasyon
            new PgCase("PG_08", "çalışma",        "Konsantrasyon"),   // → Konsantrasyon
            new PgCase("PG_09", "lofi",           "Kahve"),           // → Kahve Molası

            // Eğlence & Etkinlik
            new PgCase("PG_10", "parti",          "Parti"),           // → Parti Zamanı
            new PgCase("PG_11", "düğün",          "Düğün"),           // → Düğün Listeleri
            new PgCase("PG_12", "halay",          "Düğün"),           // → Düğün Listeleri
            new PgCase("PG_13", "konser",         "Konser"),          // → Konserler

            // Tatil & Mevsim
            new PgCase("PG_14", "tatil",          "Tatil"),           // → Tatil
            new PgCase("PG_15", "plaj",           "Tatil"),           // → Tatil
            new PgCase("PG_16", "kamp",           "Tatil"),           // → Tatil
            new PgCase("PG_17", "ramazan",        "Tatil"),           // → Tatil

            // Yöresel
            new PgCase("PG_18", "akdeniz",        "Yöre"),            // → Yörelere Göre Müzik
            new PgCase("PG_19", "zeybek",         "Yöre"),            // → Yörelere Göre Müzik

            // Özel İçerik
            new PgCase("PG_20", "film müzikleri", "Soundtrack"),      // → Soundtrack
            new PgCase("PG_21", "ninni",          "Çocuk"),           // → Çocuk
            new PgCase("PG_22", "nostalji",       "Unutulmayan"),     // → Unutulmayanlar

            // Ruh Hali
            new PgCase("PG_23", "romantik",       "Aşk"),             // → Aşk
            new PgCase("PG_24", "özlem",          "Zamanda"),         // → Zamanda Yolculuk

            // AKT-A — Tümü sekmesinde de group çıkan keyword'ler (Discovery UAT doğrulamalı)
            new PgCase("PG_25", "konsantrasyon",  "Konsantrasyon"),   // AKT sıra 2
            new PgCase("PG_26", "2000ler",        "Zamanda"),         // AKT sıra 2
            new PgCase("PG_27", "çocuk",          "Çocuk"),           // AKT sıra 2
            new PgCase("PG_28", "spor",           "Spor"),            // AKT sıra 3
            new PgCase("PG_29", "egzersiz",       "Sabah"),           // AKT sıra 6
            new PgCase("PG_30", "meditasyon",     "Meditasyon")       // AKT sıra 4
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1: TEKLİ PLAYLIST INDEX — /indices/4/search
    //   Beklenti: sonuçlar içinde itemKind="playlistGroup" olan kayıt bulunmalı
    //   Alanlar: data.itemName, data.itemKind, data.items (alt playlistler)
    // ─────────────────────────────────────────────────────────────────────────
    @DisplayName("[Tekli] Playlist index'te playlistGroup kaydı kontrolü")
    @ParameterizedTest(name = "[{0}] \"{1}\" — indices/4")
    @MethodSource("cases")
    @Order(1)
    void playlist_tekli_group_kontrolu(PgCase pg) {
        Response res = api.search(pg.term(), PLAYLIST_INDEX, TOP_N, false);
        evalItemKind(pg, res, "indices/" + PLAYLIST_INDEX, "content");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2: AKTİF İNDEKSLER — active-indices/search  [BİLGİ AMAÇLI]
    //   muud_playlist_all_v2 (index 4) active-indices'e dahildir.
    //   Dolayısıyla itemKind=playlistGroup kayıtları Tümü sekmesinde de
    //   çıkabilir — bu beklenen davranıştır, hata değildir.
    //   Test assert ETMEZ; sadece hangi keyword'lerin Tümü'nde de group
    //   getirdiğini raporlar (ranking bağımlı, şarkılar önce gelir).
    // ─────────────────────────────────────────────────────────────────────────
    @DisplayName("[Aktif] Tümü sekmesinde playlistGroup — bilgi amaçlı")
    @ParameterizedTest(name = "[{0}] \"{1}\" — active-indices")
    @MethodSource("cases")
    @Order(2)
    void aktif_indeks_playlist_group_kontrolu(PgCase pg) {
        Response res = api.search(pg.term(), "active-indices", TOP_N, false);
        evalItemKindBilgi(pg, res, "active-indices", "topHits");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3: FİLTRELİ ARAMA — /indices/4/search  [EN NET DEPLOYMENT SINYALI]
    //   itemKind=playlistGroup filtresiyle arama yapılır.
    //   Filtre sayesinde sonuçlar SADECE playlistGroup içerir — ranking bağımsız.
    //   PROD deployment öncesi: 0 sonuç → NOK (eski schema'da itemKind yok)
    //   PROD deployment sonrası: ≥1 sonuç → OK (yeni schema devrede)
    // ─────────────────────────────────────────────────────────────────────────
    @DisplayName("[Filtreli] itemKind=playlistGroup filtresiyle deployment tespiti")
    @ParameterizedTest(name = "[{0}] \"{1}\" — indices/4 [filtreli]")
    @MethodSource("cases")
    @Order(3)
    void playlist_filtreli_group_kontrolu(PgCase pg) {
        Response res = api.searchFiltered(pg.term(), PLAYLIST_INDEX, TOP_N, "itemKind", "playlistGroup");
        evalItemKindFiltered(pg, res, "indices/" + PLAYLIST_INDEX + " [filtreli]", "content");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ORTAK DEĞERLENDİRME
    //   itemKind="playlistGroup" olan ve itemName'i keyword içeren kayıt arar.
    //   Alanlar (gerçek response yapısından):
    //     data.itemKind  = "playlist" | "playlistGroup"
    //     data.itemName  = grup / playlist adı
    //     data.items[]   = alt öğeler (playlist için şarkılar, group için playlistler)
    // ─────────────────────────────────────────────────────────────────────────
    private void evalItemKind(PgCase pg, Response res, String endpoint, String base) {
        String testName = endpoint + " | topN=" + TOP_N + " | " + pg.term();
        String expected = "Top-" + TOP_N + " içinde itemKind='playlistGroup' ve itemName'de '"
                + pg.expGroupKeyword() + "' geçen kayıt bulunmalı.";

        if (res.statusCode() != 200) {
            String detail = "❌ API " + res.statusCode() + " döndü.";
            addRow(pg, endpoint, "NOK", testName, expected, detail);
            org.junit.jupiter.api.Assertions.fail(detail);
            return;
        }

        JsonPath jp = res.jsonPath();

        for (int i = 0; i < TOP_N; i++) {
            String itemKind = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.itemKind"));
            if (!"playlistGroup".equals(itemKind)) continue;

            String itemName = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.itemName"));
            boolean nameMatch = pg.expGroupKeyword().isEmpty()
                    || itemName.toLowerCase(TR).contains(pg.expGroupKeyword().toLowerCase(TR));

            if (nameMatch) {
                // Alt öğe sayısını da logla
                List<?> items = jp.getList(base + "[" + i + "].data.items");
                int itemCount = items == null ? 0 : items.size();
                double score = 0;
                try { score = jp.getDouble(base + "[" + i + "].score"); } catch (Exception ignored) {}

                String detail = "✅ pos " + (i + 1) + ". sırada playlistGroup bulundu: '"
                        + itemName + "' | " + itemCount + " alt playlist | score=" + String.format("%.2f", score)
                        + "\n" + topNDesc(jp, base);
                addRow(pg, endpoint, "OK", testName, expected, detail);
                return;
            }
        }

        // Bulunamadı — ne vardığını göster
        String detail = "❌ Top-" + TOP_N + " içinde uygun playlistGroup bulunamadı.\n"
                + topNDesc(jp, base);
        addRow(pg, endpoint, "NOK", testName, expected, detail);
        org.junit.jupiter.api.Assertions.fail(detail);
    }

    /**
     * Bilgi amaçlı varyant — NOK olsa bile test fail etmez.
     * muud_playlist_all_v2 active-indices'e dahil olduğundan playlistGroup
     * Tümü sekmesinde de çıkabilir; bu beklenen davranıştır.
     * Test sadece hangi keyword'lerin Tümü'nde group getirdiğini raporlar.
     */
    private void evalItemKindBilgi(PgCase pg, Response res, String endpoint, String base) {
        String testName = endpoint + " | topN=" + TOP_N + " | " + pg.term();
        String expected = "[BİLGİ] Top-" + TOP_N + " içinde itemKind='playlistGroup' var mı?";

        if (res.statusCode() != 200) {
            addRow(pg, endpoint, "NOK", testName, expected, "⚠️ API " + res.statusCode() + " döndü.");
            return;
        }

        JsonPath jp = res.jsonPath();
        for (int i = 0; i < TOP_N; i++) {
            String itemKind = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.itemKind"));
            if (!"playlistGroup".equals(itemKind)) continue;
            String itemName = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.itemName"));
            boolean nameMatch = pg.expGroupKeyword().isEmpty()
                    || itemName.toLowerCase(TR).contains(pg.expGroupKeyword().toLowerCase(TR));
            if (nameMatch) {
                double score = 0;
                try { score = jp.getDouble(base + "[" + i + "].score"); } catch (Exception ignored) {}
                addRow(pg, endpoint, "OK", testName, expected,
                        "✅ pos " + (i + 1) + ". sırada bulundu: '" + itemName
                                + "' | score=" + String.format("%.2f", score)
                                + "\n" + topNDesc(jp, base));
                return;
            }
        }
        addRow(pg, endpoint, "NOK", testName, expected,
                "— Tümü top-" + TOP_N + "'de bu keyword için group çıkmadı (ranking bağımlı, normal)\n"
                        + topNDesc(jp, base));
        // Assertions.fail() YOK — ranking bağımlı, assert edilmez
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private static String topNDesc(JsonPath jp, String base) {
        StringBuilder sb = new StringBuilder("İlk " + TOP_N + " sonuç:\n");
        for (int i = 0; i < TOP_N; i++) {
            // itemKind öncelikli (playlist/playlistGroup), yoksa data.kind (songs, albums vb.)
            String kind = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.itemKind"));
            if (kind.isEmpty()) kind = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.kind"));
            if (kind.isEmpty()) break;
            String name = resolveLabel(jp, base, i, kind);
            double score = 0;
            try { score = jp.getDouble(base + "[" + i + "].score"); } catch (Exception ignored) {}
            sb.append(String.format("%d. [%s] %s | score=%.2f%n", i + 1, kind, name, score));
        }
        return sb.toString().trim();
    }

    /**
     * Filtreli arama değerlendirmesi — tüm sonuçlar zaten playlistGroup.
     * Sonuç geliyorsa OK (deployment tamam), boşsa NOK (eski schema).
     * TEK gibi assert eder.
     */
    private void evalItemKindFiltered(PgCase pg, Response res, String endpoint, String base) {
        String testName = endpoint + " | topN=" + TOP_N + " | " + pg.term();
        String expected = "itemKind=playlistGroup filtresiyle en az 1 sonuç gelmeli.";

        if (res.statusCode() != 200) {
            String detail = "❌ API " + res.statusCode() + " döndü.";
            addRow(pg, endpoint, "_FIL", "NOK", testName, expected, detail);
            org.junit.jupiter.api.Assertions.fail(detail);
            return;
        }

        JsonPath jp = res.jsonPath();
        String itemKind0 = MuudSearchUtils.safeStr(jp.getString(base + "[0].data.itemKind"));

        if ("playlistGroup".equals(itemKind0)) {
            String itemName = MuudSearchUtils.safeStr(jp.getString(base + "[0].data.itemName"));
            List<?> items  = jp.getList(base + "[0].data.items");
            int itemCount  = items == null ? 0 : items.size();
            double score   = 0;
            try { score = jp.getDouble(base + "[0].score"); } catch (Exception ignored) {}

            String detail = "✅ Filtreli: 1. sırada '" + itemName + "' | " + itemCount
                    + " alt playlist | score=" + String.format("%.2f", score)
                    + "\n" + topNDesc(jp, base);
            addRow(pg, endpoint, "_FIL", "OK", testName, expected, detail);
        } else {
            String detail = "❌ Filtreli arama sonuç döndürmedi — eski schema (itemKind alanı yok).\n"
                    + topNDesc(jp, base);
            addRow(pg, endpoint, "_FIL", "NOK", testName, expected, detail);
            org.junit.jupiter.api.Assertions.fail(detail);
        }
    }

    private static String resolveLabel(JsonPath jp, String base, int i, String kind) {
        String data = base + "[" + i + "].data";
        return switch (kind) {
            case "songs"           -> MuudSearchUtils.safeStr(jp.getString(data + ".songName"))
                                      + " — " + MuudSearchUtils.safeStr(jp.getString(data + ".performerName"));
            case "performers"      -> MuudSearchUtils.safeStr(jp.getString(data + ".performerName"));
            case "playlists"       -> {
                // active-indices: eski şemada playlistName, yeni şemada itemName
                String n = MuudSearchUtils.safeStr(jp.getString(data + ".itemName"));
                if (n.isEmpty()) n = MuudSearchUtils.safeStr(jp.getString(data + ".playlistName"));
                yield n;
            }
            case "playlist_groups" -> MuudSearchUtils.safeStr(jp.getString(data + ".playlistGroupName"));
            case "albums"          -> MuudSearchUtils.safeStr(jp.getString(data + ".albumName"));
            // playlist index itemKind değerleri
            case "playlist"        -> MuudSearchUtils.safeStr(jp.getString(data + ".itemName"));
            case "playlistGroup"   -> MuudSearchUtils.safeStr(jp.getString(data + ".itemName"));
            default                -> "(" + kind + ")";
        };
    }

    /** TEK ve AKT çağrıları için — suffix endpoint'ten otomatik türetilir. */
    private static void addRow(PgCase pg, String endpoint, String status,
                                String testName, String expected, String sonuc) {
        String suffix = endpoint.contains("active") ? "_AKT" : "_TEK";
        addRow(pg, endpoint, suffix, status, testName, expected, sonuc);
    }

    /** Suffix açıkça verildiğinde (FIL gibi). */
    private static void addRow(PgCase pg, String endpoint, String suffix, String status,
                                String testName, String expected, String sonuc) {
        ROWS.add(new TestResultRow(
                pg.caseId() + suffix,
                testName,
                "\"" + pg.term() + "\" → playlistGroup kontrolü",
                expected,
                "PlaylistGroup",
                endpoint,
                status,
                sonuc
        ));
    }
}
