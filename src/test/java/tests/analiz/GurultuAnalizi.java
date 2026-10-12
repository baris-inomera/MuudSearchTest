package tests.analiz;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import report.GurultuReportWriter;
import util.MuudSearchUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * GurultuAnalizi — Arama Sonucu Kirlilik Testi
 * ─────────────────────────────────────────────────────────────────────────────
 * Her case için top-15 çekilir (uygulamadaki "Tümü" sekmesine karşılık gelir).
 * Tüm 15 sonuç raporda gösterilir; arama terimiyle ilgisi olmayan sonuçlar
 * otomatik olarak tespit edilip sınıflandırılır:
 *
 *   TEMİZ        → Başlık veya sanatçıda arama kelimesi geçiyor
 *   LYRİCS?      → Görünür bağlantı yok ama lyrics match olabilir (manuel kontrol)
 *   GÜRÜLTÜ      → Kesin alakasız (popularity sızması, generic playlist, yabancı içerik)
 *
 * Test HİÇBİR ZAMAN fail etmez — saf gözlem & karşılaştırılabilir rapor üretir.
 * Her koşumda guncel bir excel üretildiğinden zamanla iyileşme takip edilebilir.
 *
 * Kullanım:
 *   mvn test -Dtest=GurultuAnalizi
 * Çıktı:
 *   reports/gurultu/GurultuAnalizi_<zaman>_<ortam>.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GurultuAnalizi extends TestConfig {

    private static final int TOP_N = 15;
    private static final Locale TR = Locale.forLanguageTag("tr");

    // Gürültü sınıflandırması
    public static final String TEMIZ   = "TEMİZ";
    public static final String LYRICS  = "LYRİCS?";
    public static final String GURULTU = "GÜRÜLTÜ";

    // Yüksek populariteli sanatçılar — sık sızma kaynakları
    private static final List<String> YUKSEK_POP_SANATCILAR = List.of(
            "blok3", "tarkan", "reynmen", "ceza", "yildiz tilbe",
            "lvbel", "sagopa", "sezen aksu", "mabel matiz", "ati242"
    );

    // Arama terimiyle hiç ilgisi olmayan generic playlist isimleri
    private static final List<String> GENERIC_PLAYLIST_PARCASI = List.of(
            "top 100", "hit 100", "türkçe", "pop", "rap", "rock", "indie",
            "en cok", "gündem", "akış", "turuncu", "listesi", "weekly"
    );

    // Yabancı dil aksanları (Türkçe harfler değil)
    private static final Pattern YABANCI_AKSAN =
            Pattern.compile("[ñãâęóźłïëôûæøåœß]", Pattern.UNICODE_CASE);

    // ── Rapor satır modeli ────────────────────────────────────────────────────

    public record GurultuRow(
            String caseId,
            String searchTerm,
            String section,
            int position,         // 1–15
            String resultKind,    // songs / albums / performers / playlists / videos
            String title,         // Şarkı/Albüm/Sanatçı/Playlist adı
            String artist,        // Sanatçı (performers veya performerName)
            double popularity,
            double score,
            String id,
            String noiseLabel,    // TEMİZ / LYRİCS? / GÜRÜLTÜ
            String noiseType,     // Popularity Sızması / Generic Playlist / Kısa Kelime / ...
            String noiseReason    // Kısa açıklama
    ) {}

    private static final List<GurultuRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // ─────────────────────────────────────────────────────────────────────────
    // SETUP / TEARDOWN
    // ─────────────────────────────────────────────────────────────────────────

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("🔍 GurultuAnalizi başlatıldı — top-" + TOP_N + " her case için çekilecek.");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 %d case işlendi, toplam %d sonuç satırı.%n",
                ROWS.stream().map(GurultuRow::caseId).distinct().count(), ROWS.size());
        String ortam = MuudSearchApi.ortam();
        GurultuReportWriter.write(ROWS, "reports/gurultu", ortam);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASE TANIMLAMASI — AramaKalitesiTest ile aynı case listesi
    // ─────────────────────────────────────────────────────────────────────────

    record GurultuCase(String caseId, String term, String section) {}

    static Stream<GurultuCase> cases() {
        // cases.json varsa oradan oku (AramaKalitesiTest ile paylaşılan kaynak)
        try {
            File casesFile = new File(System.getProperty("user.dir"), "cases.json");
            if (casesFile.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                List<Map<String, Object>> list = mapper.readValue(casesFile,
                        new TypeReference<>() {});
                return list.stream().map(m -> new GurultuCase(
                        str(m, "caseId"),
                        str(m, "term"),
                        str(m, "section")
                ));
            }
        } catch (Exception e) {
            System.err.println("⚠ cases.json okunamadı, hardcoded liste kullanılıyor: " + e.getMessage());
        }

        // Hardcoded — AramaKalitesiTest'teki case listesinin tam aynısı (sadece term + section)
        return Stream.of(
                // ŞARKİ · TAM EŞLEŞMe
                new GurultuCase("SARKI_TAM_1",  "a canım",                        "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_2",  "olmazlara vuruluyorum",           "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_3",  "çıkmaz bir sokakta",              "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_4",  "kusura bakma",                    "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_5",  "maraton",                         "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_6",  "geri ver",                        "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_7",  "saygımdan",                       "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_8",  "meğerse",                         "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_9",  "dacia",                           "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_10", "yalnızlığın çaresini bulmuşlar",  "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_11", "yapar mısın",                     "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_12", "yerinde dur",                     "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_13", "ey aşk",                          "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_14", "şımarık",                         "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_15", "giderim kırağınan",               "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_16", "ara beni lütfen",                 "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_17", "aşk yok olmaktır",                "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_18", "can efendim",                     "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_19", "çıt çıt çedene",                  "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_20", "çıkar biri karşıma",              "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_21", "sen yanlış yaptın",               "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_22", "vay dayı",                        "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_23", "silinmez",                        "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_24", "halbuki",                         "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_25", "duydun mu",                       "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_26", "sana güvenmiyorum",               "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_27", "yasemen",                         "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_28", "düşer o",                         "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_29", "kömür",                           "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_30", "just the way you are",            "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_31", "snap",                            "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_32", "ama başaramadım",                 "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_33", "adına bir çizik çektim",          "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_34", "yaramızda kalsın",                "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_35", "sev yeter",                       "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_36", "kaybolurum gülüşünde",            "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_37", "ağlama ben ağlarım",              "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_38", "şikayetim var",                   "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_39", "bunca yıl",                       "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_40", "düldül",                          "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_41", "perde",                           "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_42", "phonk",                           "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_43", "sonbahar",                        "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_44", "acem kızı",                       "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_45", "hacel obası",                     "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_46", "yalan",                           "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_47", "bana sor",                        "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_48", "rüya",                            "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_49", "ara",                             "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_50", "14 bahar",                        "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_51", "ela mana",                        "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_52", "yekten",                          "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_53", "erik dalı",                       "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_54", "elfida",                          "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_55", "yazan kalem siyah",               "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_56", "Mihriban",                        "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_57", "merdo",                           "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_58", "sigara",                          "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_59", "farzet",                          "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_60", "misket",                          "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_61", "kara sevda",                      "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_62", "parla",                           "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_63", "kırmızı balık",                   "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_64", "nasır",                           "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_65", "bodrum",                          "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_66", "gözlerime bak",                   "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_67", "türk marşı",                      "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_68", "there is a light that never goes out", "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_69", "ölek mi",                         "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_70", "Sen Anlat Geçen Yüzyıl",         "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_71", "uzaylılar bile dinliyo",          "Şarkı · Tam Eşleşme"),
                new GurultuCase("SARKI_TAM_72", "git",                             "Şarkı · Tam Eşleşme"),
                // ŞARKİ · KISMİ AD
                new GurultuCase("SARKI_KISMI_1",  "Hermes",                        "Şarkı · Kısmi Ad"),
                new GurultuCase("SARKI_KISMI_2",  "yalnızlığın çaresini",          "Şarkı · Kısmi Ad"),
                new GurultuCase("SARKI_KISMI_3",  "yerinde",                       "Şarkı · Kısmi Ad"),
                new GurultuCase("SARKI_KISMI_4",  "vidrado em",                    "Şarkı · Kısmi Ad"),
                new GurultuCase("SARKI_KISMI_5",  "çıt çıt",                       "Şarkı · Kısmi Ad"),
                new GurultuCase("SARKI_KISMI_6",  "ağlama ben",                    "Şarkı · Kısmi Ad"),
                new GurultuCase("SARKI_KISMI_7",  "erik",                          "Şarkı · Kısmi Ad"),
                new GurultuCase("SARKI_KISMI_8",  "doğuştan",                      "Şarkı · Kısmi Ad"),
                new GurultuCase("SARKI_KISMI_9",  "dandini",                       "Şarkı · Kısmi Ad"),
                new GurultuCase("SARKI_KISMI_10", "sus",                           "Şarkı · Kısmi Ad"),
                new GurultuCase("SARKI_KISMI_11", "pus",                           "Şarkı · Kısmi Ad"),
                // ŞARKİ · SANATÇI + ŞARKİ
                new GurultuCase("SARKI_SANAT_1",  "messy lola young",              "Şarkı · Sanatçı + Şarkı"),
                new GurultuCase("SARKI_SANAT_2",  "mabel kömür",                   "Şarkı · Sanatçı + Şarkı"),
                new GurultuCase("SARKI_SANAT_3",  "y poizi",                       "Şarkı · Sanatçı + Şarkı"),
                new GurultuCase("SARKI_SANAT_4",  "kts manifest",                  "Şarkı · Sanatçı + Şarkı"),
                new GurultuCase("SARKI_SANAT_5",  "rüya manifest",                 "Şarkı · Sanatçı + Şarkı"),
                new GurultuCase("SARKI_SANAT_6",  "bir motive",                    "Şarkı · Sanatçı + Şarkı"),
                new GurultuCase("SARKI_SANAT_7",  "cem belevi sevme diyor",        "Şarkı · Sanatçı + Şarkı"),
                // SANATÇI · TAM EŞLEŞMe
                new GurultuCase("SANAT_TAM_1",  "tarkan",                          "Sanatçı · Tam Eşleşme"),
                new GurultuCase("SANAT_TAM_2",  "sezen aksu",                      "Sanatçı · Tam Eşleşme"),
                new GurultuCase("SANAT_TAM_3",  "blok3",                           "Sanatçı · Tam Eşleşme"),
                new GurultuCase("SANAT_TAM_4",  "manifest",                        "Sanatçı · Tam Eşleşme"),
                new GurultuCase("SANAT_TAM_5",  "Semicenk",                        "Sanatçı · Tam Eşleşme"),
                new GurultuCase("SANAT_TAM_6",  "mabel matiz",                     "Sanatçı · Tam Eşleşme"),
                // SANATÇI · KISMİ
                new GurultuCase("SANAT_KISMI_1", "tarkan şımarık",                 "Sanatçı · Kısmi Ad"),
                new GurultuCase("SANAT_KISMI_2", "sezen",                          "Sanatçı · Kısmi Ad"),
                // PLAYLIST
                new GurultuCase("PLAYLIST_1",  "türkçe pop",                       "Playlist"),
                new GurultuCase("PLAYLIST_2",  "gym",                              "Playlist"),
                new GurultuCase("PLAYLIST_3",  "melankoli",                        "Playlist")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{0}] \"{1}\"")
    @MethodSource("cases")
    @Order(1)
    void run(GurultuCase gc) {
        try {
            Response res = api.search(gc.term(), "active-indices", TOP_N);
            JsonPath jp  = res.jsonPath();
            String base  = MuudSearchUtils.getBasePath(jp);
            List<Object> list = MuudSearchUtils.resultsList(jp);

            int count = Math.min(list.size(), TOP_N);
            List<String> keywords = extractKeywords(gc.term());

            for (int i = 0; i < count; i++) {
                GurultuRow row = buildRow(gc, jp, base, i, keywords);
                ROWS.add(row);
                if (!TEMIZ.equals(row.noiseLabel())) {
                    System.out.printf("  [%s] pos-%d: %s — %s → %s (%s)%n",
                            gc.caseId(), row.position(), row.title(), row.artist(),
                            row.noiseLabel(), row.noiseType());
                }
            }

            System.out.printf("[%s] \"%s\" — %d sonuç işlendi%n",
                    gc.caseId(), gc.term(), count);

        } catch (Exception e) {
            System.err.printf("⚠️  API hatası [%s '%s']: %s%n",
                    gc.caseId(), gc.term(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // YARDIMCI — Satır oluşturma
    // ─────────────────────────────────────────────────────────────────────────

    private GurultuRow buildRow(GurultuCase gc, JsonPath jp, String base, int i, List<String> keywords) {
        String kind       = safe(jp.getString(base + "[" + i + "].data.kind"));
        String song       = safe(jp.getString(base + "[" + i + "].data.songName"));
        String album      = safe(jp.getString(base + "[" + i + "].data.albumName"));
        String playlist   = safe(jp.getString(base + "[" + i + "].data.playlistName"));
        String performer  = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
        String id         = safe(jp.getString(base + "[" + i + "].id"));

        String title;
        String artist;
        if (!song.isEmpty())          { title = song;      artist = performer; }
        else if (!album.isEmpty())    { title = album;     artist = performer; }
        else if (!playlist.isEmpty()) { title = playlist;  artist = ""; }
        else                          { title = performer; artist = ""; }

        // Popularity: UAT → prodPopularity, PROD → popularity
        Object popObj = jp.get(base + "[" + i + "].data.prodPopularity");
        if (popObj == null) popObj = jp.get(base + "[" + i + "].data.popularity");
        Object scoreObj = jp.get(base + "[" + i + "].score");
        double pop   = popObj   != null ? toDouble(popObj)   : 0.0;
        double score = scoreObj != null ? toDouble(scoreObj) : 0.0;

        // Gürültü sınıflandır
        NoiseResult noise = classify(gc.term(), kind, title, artist, pop, keywords);

        return new GurultuRow(
                gc.caseId(), gc.term(), gc.section(),
                i + 1, kind, title, artist, pop, score, id,
                noise.label(), noise.type(), noise.reason()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GÜRÜLTÜ SINIFLANDIRMA MOTORU
    // ─────────────────────────────────────────────────────────────────────────

    record NoiseResult(String label, String type, String reason) {}

    private NoiseResult classify(String term, String kind, String title,
                                  String artist, double pop, List<String> keywords) {
        // 1. TEMİZ kontrolü: başlık veya sanatçıda herhangi bir keyword geçiyor mu?
        String titleN  = norm(title);
        String artistN = norm(artist);
        boolean hasMatch = keywords.stream()
                .anyMatch(kw -> titleN.contains(kw) || artistN.contains(kw));
        if (hasMatch) {
            return new NoiseResult(TEMIZ, "-", "-");
        }

        // TEMİZ değil — gürültü tipi belirle:

        // 2. Kısa kelime: aramanın kendisi çok kısa (≤4 harf)
        String termClean = term.replaceAll("\\s+", "");
        if (termClean.length() <= 4) {
            return new NoiseResult(LYRICS,
                    "Kısa Kelime",
                    "Arama terimi çok kısa (" + termClean.length() + " harf) — semantic drift beklenilebilir");
        }

        // 3. Generic playlist sızması
        if ("playlists".equals(kind)) {
            String titleLow = title.toLowerCase(TR);
            boolean isGeneric = GENERIC_PLAYLIST_PARCASI.stream().anyMatch(titleLow::contains);
            if (isGeneric) {
                return new NoiseResult(GURULTU,
                        "Generic Playlist Sızması",
                        "Genel playlist (\"" + title + "\") — aramayla semantik bağı yok");
            }
            return new NoiseResult(LYRICS,
                    "Tematik Playlist?",
                    "Playlist tema/mood bağlantısı olabilir — manuel kontrol");
        }

        // 4. Yabancı içerik sızması
        if (YABANCI_AKSAN.matcher(title).find()) {
            return new NoiseResult(GURULTU,
                    "Yabancı İçerik",
                    "Türkçe arama — yabancı dil karakterler tespit edildi");
        }

        // 5. Yüksek popularite sızması — bilinen sanatçılar
        String artistLow = artist.toLowerCase(TR);
        boolean isHighPopArtist = YUKSEK_POP_SANATCILAR.stream()
                .anyMatch(artistLow::contains);
        if (isHighPopArtist && pop > 20) {
            return new NoiseResult(GURULTU,
                    "Popularity Sızması",
                    artist + " (pop=" + String.format("%.1f", pop) + ") — yüksek popularite nedeniyle sıraya girdi");
        }

        // 6. Çok yüksek popularite (sanatçı tanımada olmasa da)
        if (pop > 60) {
            return new NoiseResult(GURULTU,
                    "Popularity Sızması",
                    "pop=" + String.format("%.1f", pop) + " — arama terimiyle ilgisi yok");
        }

        // 7. Video formatı — müzik aramasında genellikle düşük öncelik
        if ("videos".equals(kind)) {
            return new NoiseResult(LYRICS,
                    "Video Sızması",
                    "Video formatı — kullanıcı genellikle şarkı/sanatçı arıyor");
        }

        // 8. Lyrics / bilinmeyen — manuel kontrol gerekli
        return new NoiseResult(LYRICS,
                "Lyrics / Bilinmeyen",
                "Başlık/sanatçıda eşleşme yok — şarkı sözünde arama kelimesi geçiyor olabilir");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTIL
    // ─────────────────────────────────────────────────────────────────────────

    private List<String> extractKeywords(String term) {
        // Noktalama temizle, lowercase, normalize et, min 2 harf
        String cleaned = term.replaceAll("[\"'?!.,;:]", "").toLowerCase(TR);
        return Arrays.stream(cleaned.split("\\s+"))
                .filter(w -> w.length() >= 2)
                .map(this::norm)
                .distinct()
                .toList();
    }

    /** Türkçe dahil tüm aksanları kaldır, lowercase */
    private String norm(String s) {
        if (s == null || s.isEmpty()) return "";
        String lower = s.toLowerCase(TR);
        // Türkçe harfleri ASCII'ye dönüştür
        lower = lower
                .replace("ş", "s").replace("ç", "c").replace("ğ", "g")
                .replace("ü", "u").replace("ö", "o").replace("ı", "i")
                .replace("â", "a").replace("î", "i").replace("û", "u");
        // NFD → remove combining chars → NFC
        String nfd = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return Normalizer.normalize(
                nfd.replaceAll("[\\p{InCombiningDiacriticalMarks}]", ""),
                Normalizer.Form.NFC);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static double toDouble(Object o) {
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }
}
