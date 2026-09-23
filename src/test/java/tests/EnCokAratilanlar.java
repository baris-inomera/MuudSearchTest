package tests;

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
 * Eylül 2026 verilerine göre en çok aratılan 20 keyword'ün
 * arama kalitesi gözlem testleri.
 *
 * Bu class'taki case'ler mevcut MainCases'e ek olarak çalışır;
 * mevcut case'lerle çakışmaz.
 *
 * Çalıştırmak için:
 *   mvn test -Dtest=EnCokAratilanlar
 *
 * Çıktı: reports/en-cok-aratilanlar/ altında Excel raporu
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EnCokAratilanlar extends TestConfig {

    private static final int TOP_N = 10;
    private static final Locale TR = Locale.forLanguageTag("tr");

    // ── Bölüm sabitleri (MainCases ile aynı) ────────────────────────────────
    private static final String S_SARKI_TAM   = "Şarkı · Tam Eşleşme";
    private static final String S_SANAT_TAM   = "Sanatçı · Tam Eşleşme";
    private static final String S_PLAYLIST    = "Playlist";
    private static final String S_GOZLEM      = "Gözlem";

    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // ── Setup / Teardown ─────────────────────────────────────────────────────

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ EnCokAratilanlar başlatıldı — Eylül 2026 top-20 kelimeler test ediliyor.");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        String ortam = MuudSearchApi.ortam();
        ExcelTestReportWriter.writeBulgu(ROWS, "reports/en-cok-aratilanlar", ortam);
    }

    // ── Case tanımları ───────────────────────────────────────────────────────

    record BulguCase(String caseId, String term, String expArtist, String expTrack, String section, int topN, int count) {
        BulguCase(String caseId, String term, String expArtist, String expTrack, String section, int topN) {
            this(caseId, term, expArtist, expTrack, section, topN, 0);
        }
        BulguCase(String caseId, String term, String expArtist, String expTrack, String section) {
            this(caseId, term, expArtist, expTrack, section, 10, 0);
        }
    }

    static Stream<BulguCase> cases() {
        return Stream.of(

            // ═══════════════════════════════════════════════════════════════
            // Kaynak: en çok aratılan eylül.csv — Toplam normalize hacim
            // ═══════════════════════════════════════════════════════════════

            // #1 — manifest (30,248 arama) — Playlist / Sanatçı
            // "Manifest" hem bir sanatçı hem de popüler bir playlist adı
            new BulguCase("ECA_1",  "manifest",     "Manifest",     "",              S_SANAT_TAM, 1, 30248),
            // #2 — blok3 (10,224 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_2",  "blok3",        "Blok3",        "",              S_SANAT_TAM, 1, 10224),
            // #3 — hadise (8,938 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_3",  "hadise",       "Hadise",       "",              S_SANAT_TAM, 1,  8938),
            // #4 — simge (7,560 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_4",  "simge",        "Simge",        "",              S_SANAT_TAM, 1,  7560),
            // #5 — ahmet kaya (5,243 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_5",  "ahmet kaya",   "Ahmet Kaya",   "",              S_SANAT_TAM, 1,  5243),
            // #6 — sezen aksu (4,919 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_6",  "sezen aksu",   "Sezen Aksu",   "",              S_SANAT_TAM, 1,  4919),
            // #7 — crush (4,531 arama) — Sanatçı tam eşleşme
            // "CRUSH" K-pop grubu; şarkısı "CRUSH!" — sanatçı 1. sırada gelmeli
            new BulguCase("ECA_7",  "crush",        "CRUSH",        "",              S_SANAT_TAM, 1,  4531),
            // #8 — ferdi tayfur (3,508 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_8",  "ferdi tayfur", "Ferdi Tayfur", "",              S_SANAT_TAM, 1,  3508),
            // #9 — müslüm gürses (3,302 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_9",  "müslüm gürses","Müslüm Gürses","",              S_SANAT_TAM, 1,  3302),
            // #10 — tarkan (3,220 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_10", "tarkan",       "Tarkan",       "",              S_SANAT_TAM, 1,  3220),
            // #11 — türkü (2,875 arama) — Gözlem (tür araması, beklenen tek sonuç yok)
            new BulguCase("ECA_11", "türkü",        "",             "",              S_GOZLEM,    5,  2875),
            // #12 — blok 3 (2,698 arama) — Sanatçı kısmi/boşluklu varyasyon
            new BulguCase("ECA_12", "blok 3",       "Blok3",        "",              S_SANAT_TAM, 3,  2698),
            // #13 — semicenk (2,555 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_13", "semicenk",     "Semicenk",     "",              S_SANAT_TAM, 1,  2555),
            // #14 — poizi (2,504 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_14", "poizi",        "Poizi",        "",              S_SANAT_TAM, 1,  2504),
            // #15 — uzi (2,494 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_15", "uzi",          "UZI",          "",              S_SANAT_TAM, 1,  2494),
            // #16 — sagopa kajmer (2,255 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_16", "sagopa kajmer","Sagopa Kajmer", "",              S_SANAT_TAM, 1,  2255),
            // #17 — arabesk (2,240 arama) — Gözlem (tür araması)
            new BulguCase("ECA_17", "arabesk",      "",             "",              S_GOZLEM,    5,  2240),
            // #18 — gidişat (2,197 arama) — Şarkı tam eşleşme
            new BulguCase("ECA_18", "gidişat",      "",             "Gidişat",       S_SARKI_TAM, 3,  2197),
            // #19 — mabel matiz (2,186 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_19", "mabel matiz",  "Mabel Matiz",  "",              S_SANAT_TAM, 1,  2186),
            // #20 — ceza (2,061 arama) — Sanatçı tam eşleşme
            new BulguCase("ECA_20", "ceza",         "Ceza",         "",              S_SANAT_TAM, 1,  2061)

        );
    }

    // ── Test metodu ──────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{0}] \"{1}\"")
    @MethodSource("cases")
    @Order(1)
    void run(BulguCase bc) {
        String[] result = new String[]{"(beklenen tanımlanmamış)", "NOK", "API hatası"};

        try {
            Response res = api.search(bc.term(), "active-indices", TOP_N);
            JsonPath jp = res.jsonPath();
            result = evaluate(bc, jp);
        } catch (Exception e) {
            System.err.printf("⚠️  API hatası [%s '%s']: %s%n",
                    bc.caseId(), bc.term(), e.getMessage());
            result[2] = "API hatası: " + e.getMessage();
        }

        System.out.printf("[%s] %-10s | %-30s | %s%n",
                result[1], bc.caseId(), "\"" + bc.term() + "\"", result[2]);

        String countLabel = bc.count() > 0
                ? String.format("%,d arama/ay (Eylül 2026)", bc.count())
                : "";
        ROWS.add(new TestResultRow(
                bc.caseId(),
                "\"" + bc.term() + "\" araması yapılır",
                countLabel,
                result[0],
                bc.section(),
                "active-indices",
                result[1],
                result[2]
        ));
    }

    // ── Değerlendirme (MainCases ile aynı mantık) ────────────────────────────

    private String[] evaluate(BulguCase bc, JsonPath jp) {
        List<Object> list = MuudSearchUtils.resultsList(jp);
        String base = MuudSearchUtils.getBasePath(jp);

        if (list.isEmpty()) {
            return new String[]{
                    "Arama sonucunda en az 1 kayıt dönmesi beklenir.",
                    "NOK",
                    "API boş sonuç döndürdü."};
        }

        String expTrack  = bc.expTrack();
        String expArtist = bc.expArtist();

        // Gözlem case'i — beklenen içerik yok
        if (expTrack.isEmpty() && expArtist.isEmpty()) {
            return new String[]{"(gözlem — beklenen sonuç tanımlanmamış)", "Gözlem",
                    top5Desc(jp, base)};
        }

        // Sadece sanatçı bekleniyor → FIRST_ARTIST_IS
        if (!expArtist.isEmpty() && expTrack.isEmpty()) {
            return evalFirstArtist(bc, jp, base);
        }

        // Şarkı (ve opsiyonel sanatçı) bekleniyor → TOPN_HAS_ARTIST_AND_TRACK
        return evalArtistAndTrack(bc, jp, base);
    }

    private String[] evalFirstArtist(BulguCase bc, JsonPath jp, String base) {
        int n = bc.topN();
        String expected = n == 1
                ? "1. sırada '" + bc.expArtist() + "' sanatçısı gelmeli."
                : "İlk " + n + " içinde '" + bc.expArtist() + "' sanatçısı gelmeli.";

        int pos = MuudSearchUtils.findArtistIndex(jp, n, bc.expArtist());

        if (pos != -1) {
            String fa = MuudSearchUtils.getPerformerName(jp, base + "[" + pos + "].data");
            return new String[]{expected, "OK",
                    "Başarılı — " + (pos + 1) + ". sırada '" + fa + "' geldi.\n" + top5Desc(jp, base)};
        }

        int fullPos = MuudSearchUtils.findArtistIndex(jp, TOP_N, bc.expArtist());
        String where = fullPos == -1
                ? "top-" + TOP_N + "'da da bulunamadı"
                : (fullPos + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "İlk " + n + "'da yok — " + bc.expArtist() + ": " + where + ".\n" + top5Desc(jp, base)};
    }

    private String[] evalArtistAndTrack(BulguCase bc, JsonPath jp, String base) {
        int n = bc.topN();
        String expStr = bc.expArtist().isEmpty()
                ? "'" + bc.expTrack() + "'"
                : "'" + bc.expArtist() + "' – '" + bc.expTrack() + "'";
        String expected = "Top-" + n + " içinde " + expStr + " eşleşmesi bulunmalı.";

        int idx = MuudSearchUtils.findArtistAndTrackIndex(jp, n, bc.expArtist(), bc.expTrack());

        if (idx != -1) {
            String fa = MuudSearchUtils.getPerformerName(jp, base + "[" + idx + "].data");
            String ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.songName"));
            if (ft.isEmpty())
                ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.albumName"));
            String label = fa.isEmpty() ? ft : fa + "' – '" + ft;
            return new String[]{expected, "OK",
                    "Başarılı — " + (idx + 1) + ". sırada: '" + label + "'.\n" + top5Desc(jp, base)};
        }

        int fullIdx = MuudSearchUtils.findArtistAndTrackIndex(jp, TOP_N, bc.expArtist(), bc.expTrack());
        String where = fullIdx == -1
                ? "top-" + TOP_N + "'da da bulunamadı"
                : (fullIdx + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "İlk " + n + "'da yok — " + where + ".\n" + top5Desc(jp, base)};
    }

    // ── MainCases ile birebir aynı top5Desc + itemDesc ──────────────────────

    private String top5Desc(JsonPath jp, String base) {
        StringBuilder sb = new StringBuilder("İlk 5 sonuç: ");
        for (int i = 0; i < 5; i++) {
            sb.append("\n").append(i + 1).append(".").append(itemDesc(jp, base, i));
        }
        return sb.toString();
    }

    private String itemDesc(JsonPath jp, String base, int i) {
        String song      = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.songName"));
        String album     = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.albumName"));
        String playlist  = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
        String performer = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
        String kind      = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.kind"));

        String label;
        if (!song.isEmpty())
            label = song + (performer.isEmpty() ? "" : " — " + performer);
        else if (!album.isEmpty())
            label = album + (performer.isEmpty() ? "" : " — " + performer);
        else if (!playlist.isEmpty())
            label = playlist;
        else if (!performer.isEmpty())
            label = performer;
        else
            label = "(boş)";

        String kindPrefix = kind.isEmpty() ? "" : "[" + kind + "]";

        // UAT → prodPopularity dolu; PROD → popularity dolu (MainCases ile aynı mantık)
        String popFieldName = "prodPopularity";
        Object popObj = jp.get(base + "[" + i + "].data.prodPopularity");
        if (popObj == null) {
            popFieldName = "popularity";
            popObj = jp.get(base + "[" + i + "].data.popularity");
        }
        Object scoreObj = jp.get(base + "[" + i + "].score");
        String pop   = popObj   != null ? popObj.toString()   : "-";
        String score = scoreObj != null ? scoreObj.toString() : "-";

        StringBuilder mb = new StringBuilder(" | ").append(popFieldName).append("= ").append(pop);
        if ("songs".equals(kind)) {
            Object p2 = jp.get(base + "[" + i + "].data.popularity2");
            if (p2 != null) mb.append(" | popularity2= ").append(p2);
        }
        Object idObj = jp.get(base + "[" + i + "].id");
        String id = idObj != null ? idObj.toString() : "-";
        String metrics = mb.append(" | score= ").append(score)
                           .append(" | ID= ").append(id).toString();

        return kindPrefix + label + "\n" + metrics;
    }
}
