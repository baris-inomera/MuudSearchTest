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

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * INTENT KIND + PERFORMANS TESTİ — 277 Case
 *
 * AramaKalitesiTest'in 277 case'ini iki ayrı boyutta değerlendirir:
 *
 *   1. KIND   — 1. sıradaki sonucun türü beklenenle eşleşiyor mu?
 *               (songs / performers / playlists)
 *   2. İÇERİK — Beklenen şarkı / sanatçı topN içinde bulundu mu?
 *   3. SÜRE   — Her sorgu için response time ölçülür (ms)
 *
 * Status değerleri:
 *   OK          — kind ✅  içerik ✅
 *   KIND_NOK    — kind ❌  içerik ✅  (içerik bulundu ama 1. sıra yanlış tür)
 *   CONTENT_NOK — kind ✅  içerik ❌  (1. sıra doğru tür ama içerik topN'de yok)
 *   NOK         — kind ❌  içerik ❌
 *
 * Çalıştırmak:
 *   mvn test -Dtest=IntentKindTest
 *
 * Çıktı: reports/intent-kind/IntentKindTest_dd.MM.yyyy_HH.mm_ORTAM.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntentKindTest extends TestConfig {

    private static final int    TOP_N = 20;
    private static final Locale TR    = Locale.forLanguageTag("tr");

    private static final List<TestResultRow>   ROWS      = new ArrayList<>();
    // Performans istatistikleri: kategori → ms listesi
    private static final Map<String, List<Long>> PERF_MAP = new LinkedHashMap<>();

    private static MuudSearchApi api;

    private static final String KIND_SONG     = "songs";
    private static final String KIND_PERF     = "performers";
    private static final String KIND_PLAYLIST = "playlists";

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("▶ IntentKindTest başlatıldı — 277 case, kind + performans");
        System.out.println("  URL : " + MuudSearchApi.baseUrl());
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        printPerfSummary();
        ExcelTestReportWriter.writeBulgu(ROWS, "reports/intent-kind", MuudSearchApi.ortam(), "IntentKindTest");
    }

    // =========================================================================
    // CASES
    // AramaKalitesiTest'in 277 case'i + IntentKind'e özgü 47 ek case
    // =========================================================================

    static Stream<AramaKalitesiTest.BulguCase> cases() {
        List<AramaKalitesiTest.BulguCase> all = new ArrayList<>();
        AramaKalitesiTest.cases().forEach(all::add);
        all.addAll(intentOnlyCases());
        return all.stream();
    }

    /** AramaKalitesiTest'te olmayan, IntentKind'e özgü ek case'ler (47 adet). */
    private static List<AramaKalitesiTest.BulguCase> intentOnlyCases() {
        // Section sabitleri — AramaKalitesiTest'tekilerle aynı değerler
        final String S_SARKI_SANAT  = "Şarkı · Sanatçı + Şarkı";
        final String S_SARKI_LYRICS = "Şarkı · Lyrics";
        final String S_SANAT_KISMI  = "Sanatçı · Kısmi Ad";
        return java.util.Arrays.asList(
            // ── Şarkı · Sanatçı + Şarkı (24 ek case) ──────────────────────────
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_12", "tarkan şımarık", "Tarkan", "Şımarık", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_13", "sezen aksu vay", "Sezen Aksu", "Vay", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_14", "hadise aşk kaç beden", "Hadise", "Aşk Kaç Beden", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_15", "sefo yerinde dur", "Sefo", "Yerinde Dur", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_16", "gökhan türkmen vazgeçme", "Gökhan Türkmen", "Vazgeçme", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_17", "aleyna tilki sen olsan bari", "Aleyna Tilki", "Sen Olsan Bari", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_18", "ceza yerlan", "Ceza", "Yerlan", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_19", "norm ender günah bende değil", "Norm Ender", "Günah Bende Değil", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_20", "semicenk hisler", "Semicenk", "Hisler", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_21", "duman sığınak", "Duman", "Sığınak", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_22", "teoman bir aşk masalı", "Teoman", "Bir Aşk Masalı", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_23", "gülşen çılgın biri", "Gülşen", "Çılgın Biri", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_24", "mor ve ötesi dünya yalan", "Mor ve Ötesi", "Dünya Yalan", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_25", "yüzyüzeyken konuşuruz yaz", "Yüzyüzeyken Konuşuruz", "Yaz", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_26", "kolektif istanbul seninle güzel", "Kolektif İstanbul", "Seninle Güzel", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_27", "hande yener süper", "Hande Yener", "Süper", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_28", "murat boz aşkın yüzünden", "Murat Boz", "Aşkın Yüzünden", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_29", "sagopa kajmer bir meçhul", "Sagopa Kajmer", "Bir Meçhul", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_30", "emre aydın sana bir şey sormak istiyorum", "Emre Aydın", "Sana Bir Şey Sormak İstiyorum", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_31", "hayko cepkin acı aşk", "Hayko Cepkin", "Acı Aşk", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_32", "tarkan acımasız", "Tarkan", "Acımasız", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_33", "sıla dünya seni sevmiyor", "Sıla", "Dünya Seni Sevmiyor", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_34", "gripin seni istiyorum", "Gripin", "Seni İstiyorum", S_SARKI_SANAT, 1),
            new AramaKalitesiTest.BulguCase("SARKI_SANAT_35", "simge aşk ağladı", "Simge", "Aşk Ağladı", S_SARKI_SANAT, 1),
            // ── Şarkı · Lyrics (6 ek case) ─────────────────────────────────────
            new AramaKalitesiTest.BulguCase("SARKI_LYRICS_17", "ölürsem eğer", "Tarkan", "Adımı Kalbine Yaz", S_SARKI_LYRICS, 3),
            new AramaKalitesiTest.BulguCase("SARKI_LYRICS_18", "çok geç fark ettim seni", "Sıla", "Fark Ettim", S_SARKI_LYRICS, 3),
            new AramaKalitesiTest.BulguCase("SARKI_LYRICS_19", "beni bulmak istersen", "Teoman", "Bir Aşk Hikayesi", S_SARKI_LYRICS, 3),
            new AramaKalitesiTest.BulguCase("SARKI_LYRICS_20", "sevda mı bu ne bileyim", "Mor ve Ötesi", "Sevda", S_SARKI_LYRICS, 3),
            new AramaKalitesiTest.BulguCase("SARKI_LYRICS_21", "deli deli ağlıyorum", "Gülşen", "Deli", S_SARKI_LYRICS, 3),
            new AramaKalitesiTest.BulguCase("SARKI_LYRICS_22", "ah be namuslu kız", "Ezhel", "Müptezel", S_SARKI_LYRICS, 3),
            // ── Sanatçı · Kısmi Ad (17 ek case) ────────────────────────────────
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_46", "ezhel", "Ezhel", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_47", "sefo", "Sefo", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_48", "poizi", "Poizi", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_49", "murda", "Murda", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_50", "gripin", "Gripin", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_51", "duman", "Duman", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_52", "mabel", "Mabel Matiz", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_53", "sertap", "Sertab Erener", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_54", "atiye", "Atiye", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_55", "zeynep bastık", "Zeynep Bastık", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_56", "hayko", "Hayko Cepkin", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_57", "teoman", "Teoman", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_58", "kolektif", "Kolektif İstanbul", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_59", "aleyna", "Aleyna Tilki", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_60", "norm", "Norm Ender", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_61", "yüzyüz", "Yüzyüzeyken Konuşuruz", "", S_SANAT_KISMI, 1),
            new AramaKalitesiTest.BulguCase("SANAT_KISMI_62", "ben fero", "Ben Fero", "", S_SANAT_KISMI, 1)
        );
    }

    // =========================================================================
    // TEST
    // =========================================================================

    @DisplayName("[Kind+Perf] 1. sıra tür & içerik & süre kontrolü")
    @ParameterizedTest(name = "[{0}] \"{1}\"")
    @MethodSource("cases")
    @Order(1)
    void intent_kind_ve_performans(AramaKalitesiTest.BulguCase bc) {
        long start = System.currentTimeMillis();
        Response res = api.search(bc.term(), "active-indices", TOP_N);
        long ms = System.currentTimeMillis() - start;

        // Performans kaydı
        PERF_MAP.computeIfAbsent(bc.section(), k -> new ArrayList<>()).add(ms);

        String expKind  = expectedKind(bc.section());
        String testName = bc.section() + " | \"" + bc.term() + "\"";
        String expected = "1. sıra kind=" + expKind
                + (bc.expTrack().isEmpty()  ? "" : " | şarkı top-" + bc.topN() + ": \"" + bc.expTrack() + "\"")
                + (bc.expArtist().isEmpty() ? "" : " | sanatçı: \"" + bc.expArtist() + "\"");

        if (res.statusCode() != 200) {
            ROWS.add(row(bc, "NOK", testName, expected,
                    "❌ API " + res.statusCode() + " | " + ms + " ms"));
            return;
        }

        JsonPath   jp    = res.jsonPath();
        String     base  = detectBase(jp);
        List<Item> items = extractItems(jp, base, TOP_N);

        if (items.isEmpty()) {
            ROWS.add(row(bc, "NOK", testName, expected, "❌ Sonuç dönmedi | " + ms + " ms"));
            return;
        }

        boolean kindOk    = expKind.equals(items.get(0).kind());
        boolean contentOk = contentFound(bc, items);
        boolean perfTop5  = perfInTop5(bc, items);

        // Durum etiketi
        String status;
        if (kindOk && contentOk && perfTop5)      status = "OK";
        else if (!kindOk && contentOk && perfTop5) status = "KIND_NOK";
        else if (kindOk && !contentOk)             status = "CONTENT_NOK";
        else                                        status = "NOK";

        String detail = buildDetail(bc, items, expKind, kindOk, contentOk, perfTop5, ms);
        ROWS.add(row(bc, status, testName, expected, detail));
    }

    // =========================================================================
    // YARDIMCI — Mantık
    // =========================================================================

    private String expectedKind(String section) {
        if (section == null) return KIND_SONG;
        String s = section.toLowerCase(TR);
        if (s.startsWith("playlist")) return KIND_PLAYLIST;
        if (s.startsWith("sanatçı")) return KIND_PERF;
        return KIND_SONG;
    }

    private boolean contentFound(AramaKalitesiTest.BulguCase bc, List<Item> items) {
        int limit = Math.min(bc.topN(), items.size());
        for (int i = 0; i < limit; i++) {
            Item it = items.get(i);
            boolean artistMatch = bc.expArtist().isEmpty()
                    || it.artist().toLowerCase(TR).contains(bc.expArtist().toLowerCase(TR));
            boolean trackMatch  = bc.expTrack().isEmpty()
                    || it.song().toLowerCase(TR).contains(bc.expTrack().toLowerCase(TR))
                    || it.artist().toLowerCase(TR).contains(bc.expTrack().toLowerCase(TR));
            if (artistMatch && trackMatch) return true;
        }
        return false;
    }

    /** Şarkı+Sanatçı aramaları: top-5'te performer geldi mi? */
    private boolean perfInTop5(AramaKalitesiTest.BulguCase bc, List<Item> items) {
        if (bc.section() == null || !bc.section().contains("Sanatçı + Şarkı")) return true;
        return items.stream().limit(5).anyMatch(it -> KIND_PERF.equals(it.kind())
                && (bc.expArtist().isEmpty()
                    || it.artist().toLowerCase(TR).contains(bc.expArtist().toLowerCase(TR))));
    }

    // =========================================================================
    // YARDIMCI — Detay metni
    // =========================================================================

    private String buildDetail(AramaKalitesiTest.BulguCase bc, List<Item> items,
                               String expKind, boolean kindOk, boolean contentOk,
                               boolean perfTop5, long ms) {
        Item first = items.get(0);
        StringBuilder sb = new StringBuilder();

        // Özet satır
        sb.append("⏱ ").append(ms).append(" ms")
          .append("  |  kind=").append(kindOk ? "✅" : "❌ (beklenen: " + expKind + ")")
          .append("  içerik-top").append(bc.topN()).append("=").append(contentOk ? "✅" : "❌");
        if (bc.section() != null && bc.section().contains("Sanatçı + Şarkı")) {
            sb.append("  sanatçı-top5=").append(perfTop5 ? "✅" : "❌");
        }

        // 1. sıra
        sb.append("\n1. sıra: [").append(first.kind()).append("] ").append(first.artist());
        if (!first.song().isEmpty()) sb.append(" — ").append(first.song());
        sb.append(" | score=").append(String.format("%.2f", first.score()));

        // Top-N liste
        sb.append("\n\nİlk ").append(items.size()).append(" sonuç:\n");
        for (Item it : items) {
            sb.append(String.format("  %2d. [%-12s] %s%s | score=%.2f%n",
                    it.pos(), it.kind(), it.artist(),
                    it.song().isEmpty() ? "" : " — " + it.song(), it.score()));
        }
        return sb.toString().trim();
    }

    // =========================================================================
    // YARDIMCI — Performans özeti (konsol)
    // =========================================================================

    private static void printPerfSummary() {
        System.out.println("\n═══════════════════════════════════════════════════════════════");
        System.out.println("  PERFORMANS ÖZETİ (ms)");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.printf("  %-45s %6s %6s %6s %6s %6s%n",
                "Kategori", "N", "Ort", "Min", "Max", "p95");
        System.out.println("  " + "─".repeat(78));

        List<Long> all = new ArrayList<>();
        for (Map.Entry<String, List<Long>> e : PERF_MAP.entrySet()) {
            List<Long> ms = new ArrayList<>(e.getValue());
            Collections.sort(ms);
            all.addAll(ms);
            long avg = (long) ms.stream().mapToLong(Long::longValue).average().orElse(0);
            long min = ms.get(0);
            long max = ms.get(ms.size() - 1);
            long p95 = ms.get((int) Math.min(ms.size() - 1, Math.ceil(ms.size() * 0.95) - 1));
            System.out.printf("  %-45s %6d %6d %6d %6d %6d%n",
                    e.getKey(), ms.size(), avg, min, max, p95);
        }

        // Genel toplam
        Collections.sort(all);
        long avg = (long) all.stream().mapToLong(Long::longValue).average().orElse(0);
        long p95 = all.get((int) Math.min(all.size() - 1, Math.ceil(all.size() * 0.95) - 1));
        System.out.println("  " + "─".repeat(78));
        System.out.printf("  %-45s %6d %6d %6d %6d %6d%n",
                "GENEL TOPLAM", all.size(), avg, all.get(0), all.get(all.size()-1), p95);
        System.out.println("═══════════════════════════════════════════════════════════════");
    }

    // =========================================================================
    // YARDIMCI — Parse & Row
    // =========================================================================

    private String detectBase(JsonPath jp) {
        try { if (jp.getList("topHits") != null && !jp.getList("topHits").isEmpty()) return "topHits"; } catch (Exception ignored) {}
        return "content";
    }

    private List<Item> extractItems(JsonPath jp, String base, int n) {
        List<Item> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String kind = safeStr(jp.getString(base + "[" + i + "].data.kind"));
            if (kind.isEmpty()) kind = safeStr(jp.getString(base + "[" + i + "].data.itemKind"));
            if (kind.isEmpty()) break;
            String artist = safeStr(jp.getString(base + "[" + i + "].data.performerName"));
            if (artist.isEmpty()) artist = safeStr(jp.getString(base + "[" + i + "].data.name"));
            String song = safeStr(jp.getString(base + "[" + i + "].data.songName"));
            double score = 0;
            try { score = jp.getDouble(base + "[" + i + "].score"); } catch (Exception ignored) {}
            list.add(new Item(i + 1, kind, artist, song, score));
        }
        return list;
    }

    private TestResultRow row(AramaKalitesiTest.BulguCase bc, String status,
                              String testName, String expected, String detail) {
        return new TestResultRow(bc.caseId(), testName,
                "\"" + bc.term() + "\"", expected,
                bc.section(), "active-indices", status, detail);
    }

    private record Item(int pos, String kind, String artist, String song, double score) {}

    private static String safeStr(String s) { return s == null ? "" : s.trim(); }
}
