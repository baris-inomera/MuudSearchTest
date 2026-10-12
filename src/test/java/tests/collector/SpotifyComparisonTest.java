package tests.collector;

import client.MuudSearchApi;
import config.TestConfig;
import tests.arama.AramaKalitesiTest;
import client.SpotifyApi;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import report.SpotifyComparisonReportWriter;
import report.SpotifyComparisonReportWriter.ComparisonRow;
import util.MuudSearchUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MUUD vs Spotify arama karşılaştırması.
 *
 * Her keyword için:
 *   1. MUUD active-indices'te top-10 arar
 *   2. Spotify'da top-10 arar (track + artist + album karma)
 *   3. Spotify sonuçları spotify_cache.json'a kaydedilir — tekrar çalışınca API'ye gitmiyor
 *   4. Top1 / Top2 / Top3 benzerlik sütunları ile Excel üretir
 *
 * Çalıştırma:
 *   mvn test -Dtest=SpotifyComparisonTest \
 *            -Dspotify.clientId=YOUR_CLIENT_ID \
 *            -Dspotify.clientSecret=YOUR_CLIENT_SECRET
 *
 * Opsiyonel parametreler:
 *   -Dspotify.limit=5   → ilk N keyword'ü koşar (format testi için; 0 = tümü)
 *   -Dspotify.delay=300 → API istekleri arası bekleme ms (default: 300)
 *
 * Test ASLA fail etmez — saf gözlem & karşılaştırma raporu üretir.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SpotifyComparisonTest extends TestConfig {

    private static final int    TOP        = SpotifyComparisonReportWriter.TOP; // 10
    private static final String CACHE_FILE = "spotify_cache.json";

    // ── Hardcoded fallback keyword listesi ────────────────────────────────────
    private static final List<String> KEYWORDS = List.of(
        "şımarık", "a canım", "saygımdan", "geri ver", "kömür",
        "meğerse", "yerinde dur", "ey aşk", "bodrum", "nasır",
        "düldül", "halbuki", "ara beni lütfen", "sana güvenmiyorum",
        "bunca yıl", "just the way you are",
        "tarkan", "mabel matiz", "sezen aksu", "haluk levent",
        "barış manço", "kenan doğulu", "melike şahin", "yüzyüzeyken konuşuruz",
        "lvbel c5", "poizi", "sefo", "manifest", "ceza",
        "tarkan şımarık", "mabel kömür", "messy lola young", "rüya manifest",
        "there is a light that never goes out"
    );

    private static MuudSearchApi muudApi;
    private static SpotifyApi    spotifyApi;
    private static final List<ComparisonRow>       ROWS         = new ArrayList<>();
    private static       Map<String, List<String>> spotifyCache = new LinkedHashMap<>();

    // ── Setup ─────────────────────────────────────────────────────────────────
    @BeforeAll
    static void init() {
        muudApi    = new MuudSearchApi();
        spotifyApi = SpotifyApi.fromSystemProperties();
        loadCache();
        System.out.printf("▶ SpotifyComparisonTest başlatıldı | TOP=%d | cache: %d keyword%n",
                TOP, spotifyCache.size());
    }

    @AfterAll
    static void writeReport() {
        saveCache();
        System.out.printf("%n▶ %d keyword işlendi, rapor yazılıyor...%n", ROWS.size());
        SpotifyComparisonReportWriter.write(ROWS);
    }

    // ── Ana test ──────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    void compareAllKeywords() {
        int limit = Integer.parseInt(System.getProperty("spotify.limit", "0")); // 0 = tümü
        int delay = Integer.parseInt(System.getProperty("spotify.delay", "300"));

        List<String> kws = keywords();
        if (limit > 0) kws = kws.subList(0, Math.min(limit, kws.size()));

        System.out.printf("▶ %d keyword koşulacak | delay: %dms%n", kws.size(), delay);

        int processed = 0;
        for (String keyword : kws) {
            List<String> muud    = muudResults(keyword);
            List<String> spotify = spotifyResults(keyword, delay);

            int s1 = similarity(muud, spotify, 1);
            int s2 = similarity(muud, spotify, 2);
            int s3 = similarity(muud, spotify, 3);
            Set<String> matchedTop3 = matchedMuudKeys(muud, spotify, 3);

            ROWS.add(new ComparisonRow(keyword, muud, spotify, s1, s2, s3, matchedTop3));

            // Her 20 keyword'de cache kaydet — test yarıda patlasa bile veri korunur
            if (++processed % 20 == 0) {
                saveCache();
                System.out.printf("  💾 Cache kaydedildi (%d/%d)%n", processed, kws.size());
            }
        }
    }

    // ── MUUD: top-N sonuç alma ────────────────────────────────────────────────
    private List<String> muudResults(String query) {
        try {
            Response resp = muudApi.search(query, "active-indices", TOP);
            if (resp.statusCode() != 200) {
                System.err.printf("  ⚠ MUUD hata %d — %s%n", resp.statusCode(), query);
                return new ArrayList<>();
            }
            JsonPath jp   = resp.jsonPath();
            String   base = MuudSearchUtils.getBasePath(jp);
            List<Object> items = jp.getList(base);
            if (items == null) return new ArrayList<>();

            List<String> results = new ArrayList<>();
            for (int i = 0; i < items.size() && results.size() < TOP; i++) {
                String fmt = formatMuudItem(jp, base, i);
                // Playlist her iki tarafta da yok — karşılaştırma dışında tut
                if (!fmt.isBlank() && !fmt.startsWith("[playlists]"))
                    results.add(fmt);
            }
            return results;
        } catch (Exception e) {
            System.err.printf("  ⚠ MUUD exception — %s: %s%n", query, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * MUUD item → okunabilir string.
     * Format: [kind] Ad — Sanatçı
     */
    private String formatMuudItem(JsonPath jp, String base, int i) {
        String p         = base + "[" + i + "].data.";
        String kind      = safe(jp.getString(p + "kind"));
        String song      = safe(jp.getString(p + "songName"));
        String album     = safe(jp.getString(p + "albumName"));
        String playlist  = safe(jp.getString(p + "playlistName"));
        String performer = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
        String kindTag   = kind.isEmpty() ? "" : "[" + kind + "] ";

        if (!song.isEmpty())
            return kindTag + song + (performer.isEmpty() ? "" : " — " + performer);
        if (!playlist.isEmpty())
            return kindTag + playlist;
        if (!album.isEmpty())
            return kindTag + album + (performer.isEmpty() ? "" : " — " + performer);
        if (!performer.isEmpty())
            return kindTag + performer;
        return "";
    }

    // ── Spotify: cache varsa cache'den, yoksa API'den ─────────────────────────
    private List<String> spotifyResults(String keyword, int delay) {
        List<String> cached = spotifyCache.get(keyword);
        if (cached != null && !cached.isEmpty()) {
            System.out.printf("  💾 \"%s\" (cache)%n", keyword);
            return cached;
        }
        System.out.printf("  🎵 \"%s\"%n", keyword);
        List<String> results = spotifyApi.searchAll(keyword, TOP);
        spotifyCache.put(keyword, results);
        sleep(delay);
        return results;
    }

    // ── Benzerlik skoru ───────────────────────────────────────────────────────
    /**
     * Kind-bounded set karşılaştırması:
     *
     *   MUUD top-N alınır, kind'e göre gruplanır.
     *   Her kind için: MUUD'un o kind'deki item sayısı = pencere boyutu.
     *   Spotify'ın aynı kind'deki ilk [pencere] item'ı bir havuz oluşturur.
     *   MUUD'un her kind item'ı bu havuzda var mı diye kontrol edilir (sıra fark etmez).
     *
     * Örnek — "sen yanlış yaptın" Top3 (3 şarkı):
     *   MUUD songs[0..2]: {Şahin Kendirci, İmera, Damla Arıcan}
     *   Spotify songs[0..2]: {Damla Arıcan, Şahin Kendirci, Müslüm Gürses}
     *   Kesişim: Şahin ✓ + Damla ✓ → 2/3 = %67
     *
     * Örnek — "manifest" Top1 (1 performer):
     *   MUUD performers[0..0]: {Manifest}
     *   Spotify performers[0..0]: {Manifest}
     *   Kesişim: 1/1 = %100
     *
     * Denominator = MUUD top-N item sayısı.
     */
    private int similarity(List<String> muud, List<String> spotify, int n) {
        if (muud.isEmpty() || spotify.isEmpty()) return 0;

        // Duplicate key'leri at (örn. aynı performer kaydı iki kez dönebilir).
        // LinkedHashSet ile sırayı koruyarak deduplicate ediyoruz.
        List<String> muudDedup = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (String item : muud) {
            String k = resultKey(item);
            if (!k.isBlank() && seenKeys.add(k)) muudDedup.add(item);
        }
        List<String> muudN = muudDedup.subList(0, Math.min(n, muudDedup.size()));

        // MUUD top-N'i kind'e göre grupla
        Map<String, List<String>> muudByKind = groupByKind(muudN);
        // Spotify sonuçlarını kind'e göre grupla
        Map<String, List<String>> spotByKind = groupByKind(spotify);

        int matched = 0;
        int total   = 0;

        for (Map.Entry<String, List<String>> entry : muudByKind.entrySet()) {
            String kind          = entry.getKey();
            List<String> muudKind = entry.getValue();           // MUUD'un bu kind'deki top-N item'ları
            int window           = muudKind.size();             // pencere boyutu = MUUD'da kaç tane var
            List<String> spotKind = spotByKind.getOrDefault(kind, Collections.emptyList());
            // Spotify'ın aynı kind'inden aynı pencere kadar al → havuz
            List<String> spotPool = spotKind.subList(0, Math.min(window, spotKind.size()));

            // Havuzdaki key'leri önceden hesapla
            List<String> spotKeys = spotPool.stream()
                    .map(SpotifyComparisonTest::resultKey)
                    .filter(k -> !k.isBlank())
                    .collect(Collectors.toList());

            for (String mitem : muudKind) {
                String mk = resultKey(mitem);
                if (mk.isBlank()) continue;
                total++;
                if (spotKeys.stream().anyMatch(sk -> keysMatch(mk, sk))) matched++;
            }
        }

        return total == 0 ? 0 : (int) Math.round((matched * 100.0) / total);
    }

    /**
     * Top-N dedup sonrası hangi MUUD öğelerinin Spotify ile eşleştiğini döndürür.
     * similarity() ile aynı mantık — sadece sayı yerine eşleşen item string'lerini toplar.
     */
    private Set<String> matchedMuudKeys(List<String> muud, List<String> spotify, int n) {
        Set<String> result = new LinkedHashSet<>();
        if (muud.isEmpty() || spotify.isEmpty()) return result;

        List<String> muudDedup = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (String item : muud) {
            String k = resultKey(item);
            if (!k.isBlank() && seenKeys.add(k)) muudDedup.add(item);
        }
        List<String> muudN = muudDedup.subList(0, Math.min(n, muudDedup.size()));

        Map<String, List<String>> muudByKind = groupByKind(muudN);
        Map<String, List<String>> spotByKind = groupByKind(spotify);

        for (Map.Entry<String, List<String>> entry : muudByKind.entrySet()) {
            String kind            = entry.getKey();
            List<String> muudKind = entry.getValue();
            int window             = muudKind.size();
            List<String> spotKind = spotByKind.getOrDefault(kind, Collections.emptyList());
            List<String> spotPool = spotKind.subList(0, Math.min(window, spotKind.size()));
            List<String> spotKeys = spotPool.stream()
                    .map(SpotifyComparisonTest::resultKey)
                    .filter(k -> !k.isBlank())
                    .collect(Collectors.toList());

            for (String mitem : muudKind) {
                String mk = resultKey(mitem);
                if (!mk.isBlank() && spotKeys.stream().anyMatch(sk -> keysMatch(mk, sk)))
                    result.add(mitem);
            }
        }
        return result;
    }

    /** Item listesini kind'e göre gruplar; sıra korunur. */
    private static Map<String, List<String>> groupByKind(List<String> items) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String item : items) {
            map.computeIfAbsent(kindOf(item), k -> new ArrayList<>()).add(item);
        }
        return map;
    }

    /** "[songs] ..." → "songs", "[performers] ..." → "performers", vb. */
    private static String kindOf(String item) {
        if (item == null || item.isBlank()) return "";
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^\\[(\\w+)\\]").matcher(item.trim());
        return m.find() ? m.group(1) : "";
    }

    /**
     * [kind] prefix soyulmuş, versiyon etiketleri temizlenmiş, normalize edilmiş anahtar.
     *
     * Temizlenen şeyler (şarkı adı kısmından):
     *   [Live], [Dedub Sessions], [Remix] → köşeli parantez içi
     *   (feat. X), (Live), (Cover)        → yuvarlak parantez içi
     *   " - 2011 Remaster", " - Dedub Sessions" → tire sonrası versiyon suffix
     *
     * Sanatçı kısmı değiştirilmez — farklı sanatçı = farklı sonuç, doğru davranış.
     */
    private static String resultKey(String item) {
        if (item == null) return "";
        // [kind] prefix soy
        String clean = item.replaceAll("^\\[\\w+\\]\\s*", "");
        // " — " ile şarkı ve sanatçıyı ayır
        String[] parts = clean.split(" — ", 2);
        String song   = parts[0];
        String artist = parts.length > 1 ? parts[1] : "";
        // Şarkı adından versiyon etiketlerini temizle
        song = song.replaceAll("\\[.*?\\]", "");       // [Live], [Dedub Sessions]
        song = song.replaceAll("\\(.*?\\)", "");       // (feat. X), (Cover)
        song = song.replaceAll("\\s+-\\s+.*$", "");    // - 2011 Remaster, - Dedub Sessions
        song = song.trim();
        String combined = artist.isEmpty() ? song : song + " — " + artist;
        return normalize(combined);
    }

    /**
     * İki normalize edilmiş key eşleşiyor mu?
     *
     * Kural:
     *   • Her iki key de " — " içeriyorsa (song—artist) veya hiçbiri içermiyorsa
     *     (performer/playlist): mevcut contains mantığı uygulanır.
     *   • Biri " — " içerip diğeri içermiyorsa (performer vs song):
     *     " — " içermeyen key, " — " içeren key'in ŞARKI ADINA karşı karşılaştırılır,
     *     sanatçı adına DEĞIL.
     *     → [performers] Manifest  vs  [songs] Hileli — Manifest  = HAYIR (yanlış eşleşme)
     *     → [performers] Manifest  vs  [songs] Manifest — Manifest = EVET (şarkı adı eşleşti)
     */
    private static boolean keysMatch(String a, String b) {
        if (a.equals(b)) return true;
        boolean aHasArtist = a.contains(" — ");
        boolean bHasArtist = b.contains(" — ");
        if (aHasArtist && bHasArtist) {
            // Her ikisinde de " — " var: şarkı vs şarkı veya albüm vs albüm
            // Substring: "Şımarık — Tarkan" ~ "Şımarık (Live) — Tarkan"
            if (a.length() >= 5 && b.contains(a)) return true;
            if (b.length() >= 5 && a.contains(b)) return true;
        } else if (!aHasArtist && !bHasArtist) {
            // İkisinde de " — " yok: performer veya basit albüm adı
            // "Simge" ≠ "Simge Bağdatlı" → sadece exact equals (zaten yukarıda kontrol edildi)
            return false;
        } else {
            // Biri performer (adsız), diğeri song—artist: sadece şarkı adı kısmıyla karşılaştır
            String withArtist    = aHasArtist ? a : b;
            String withoutArtist = aHasArtist ? b : a;
            String songPart = withArtist.split(" — ", 2)[0];
            // Sadece tam eşleşme kabul edilir; contains kullanmıyoruz.
            // Örn: [performers]"Sezen Aksu" vs [albums]"Sezen Aksu '88 — Sezen Aksu"
            //      songPart="sezen aksu '88", withoutArtist="sezen aksu" → HAYIR (contains olsaydı YES)
            // Örn: [performers]"Manga" vs [songs]"Mangara — maNga"
            //      songPart="mangara", withoutArtist="manga" → HAYIR (contains olsaydı YES)
            if (withoutArtist.equals(songPart)) return true;
        }
        return false;
    }

    /**
     * Karşılaştırma için normalize:
     * - Büyük/küçük harf → küçük
     * - Türkçe özel karakterler ASCII'ye: ş→s, ı→i, ğ→g, ü→u, ö→o, ç→c
     * - Yabancı aksanlar kaldırılır (é→e, â→a vb.)
     * Böylece "Nurşani" == "Nursani", "İbrahim" == "ibrahim" eşleşir.
     */
    private static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        String tr = lower
                .replace('ş', 's').replace('ı', 'i').replace('ğ', 'g')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
                .replace('İ', 'i').replace('Ş', 's').replace('Ğ', 'g'); // uppercase fallback
        String nfd   = Normalizer.normalize(tr, Normalizer.Form.NFD);
        String clean = nfd.replaceAll("[\\u0300-\\u036f]", "");
        return Normalizer.normalize(clean, Normalizer.Form.NFC)
                .replaceAll("\\s+", " ").trim();
    }

    // ── Cache: yükle / kaydet ─────────────────────────────────────────────────
    private static void loadCache() {
        Path file = Path.of(System.getProperty("user.dir"), CACHE_FILE);
        if (!Files.exists(file)) return;
        try {
            ObjectMapper mapper = new ObjectMapper();
            spotifyCache = mapper.readValue(file.toFile(),
                    new TypeReference<LinkedHashMap<String, List<String>>>() {});
            System.out.printf("💾 Cache yüklendi: %s (%d keyword)%n",
                    file.getFileName(), spotifyCache.size());
        } catch (Exception e) {
            System.err.println("⚠ Cache yüklenemedi: " + e.getMessage());
            spotifyCache = new LinkedHashMap<>();
        }
    }

    private static void saveCache() {
        Path file = Path.of(System.getProperty("user.dir"), CACHE_FILE);
        try {
            new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValue(file.toFile(), spotifyCache);
            System.out.printf("💾 Cache kaydedildi: %s (%d keyword)%n",
                    file.getFileName(), spotifyCache.size());
        } catch (Exception e) {
            System.err.println("⚠ Cache kaydedilemedi: " + e.getMessage());
        }
    }

    // ── Keyword kaynağı ───────────────────────────────────────────────────────
    /**
     * Öncelik sırası:
     *   1. AramaKalitesiTest.cases() → tüm case'lerin unique term'leri (268)
     *   2. spotify_keywords.txt
     *   3. Hardcoded KEYWORDS fallback
     */
    private static List<String> keywords() {
        // 1. VM override: -Dspotify.keywords.file=real_keywords_top150.txt
        String kwFile = System.getProperty("spotify.keywords.file");
        if (kwFile != null) {
            Path file = Path.of(System.getProperty("user.dir"), kwFile);
            if (Files.exists(file)) {
                try {
                    List<String> kws = Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8).stream()
                            .map(String::trim)
                            .filter(l -> !l.isBlank() && !l.startsWith("#"))
                            .distinct().collect(Collectors.toList());
                    System.out.println("📂 " + file.getFileName() + ": " + kws.size() + " keyword.");
                    return kws;
                } catch (IOException e) {
                    System.err.println("⚠ Keyword dosyası okunamadı: " + e.getMessage());
                }
            } else {
                System.err.println("⚠ Keyword dosyası bulunamadı: " + file);
            }
        }

        // 2. AramaKalitesiTest (tüm section'lar dahil)
        try {
            List<String> kws = AramaKalitesiTest.cases()
                    .map(AramaKalitesiTest.BulguCase::term)
                    .filter(t -> t != null && !t.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
            if (!kws.isEmpty()) {
                System.out.println("📋 AramaKalitesiTest'ten " + kws.size() + " unique keyword okundu.");
                return kws;
            }
        } catch (Exception e) {
            System.err.println("⚠ AramaKalitesiTest okunamadı: " + e.getMessage());
        }

        // 3. spotify_keywords.txt
        Path file = Path.of(System.getProperty("user.dir"), "spotify_keywords.txt");
        if (Files.exists(file)) {
            try {
                List<String> kws = Files.readAllLines(file).stream()
                        .map(String::trim)
                        .filter(l -> !l.isBlank() && !l.startsWith("#"))
                        .distinct().collect(Collectors.toList());
                System.out.println("📂 spotify_keywords.txt: " + kws.size() + " keyword.");
                return kws;
            } catch (IOException ignored) {}
        }

        System.out.println("⚠ Hardcoded keyword listesi kullanılıyor.");
        return KEYWORDS;
    }

    // ── Yardımcılar ────────────────────────────────────────────────────────────
    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
