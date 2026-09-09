package tests.ttcomtr;

import client.TtComtrDeviceCrawler;
import client.TtComtrSearchApi;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import report.TtComtrUrlReportWriter;
import report.TtComtrUrlReportWriter.UrlResult;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TTComtr — Detail URL Geçerlilik Testi
 * ─────────────────────────────────────
 * Her entity_type için geniş arama terimleriyle smartsearch tarıyor,
 * tüm URL'leri (sınırsız sayfalama) topluyor ve HTTP GET ile doğruluyor.
 *
 * ── System property override'ları ────────────────────────────────────────
 *  -DttSearchBaseUrl=https://smartsearch.turktelekom.com.tr
 *  -DttRows=100             (sayfa başına sonuç)
 *  -DttMaxPages=-1          (-1 = tüm sonuçlar; pozitif = sayfa limiti)
 *  -DttReportPath=target/ttcomtr-url-report.xlsx
 *  -DttFailOnError=true     (false = URL hataları testi fail etmez)
 *  -DttUrlDelayMs=200       (URL GET'leri arası bekleme, WAF koruması)
 */
public class TtComtrDetailUrlTest {

    // ── Her entity_type için arama terimleri ────────────────────────────────
    // Deduplication aktif — aynı URL birden fazla term'den gelse de tek kontrol edilir.
    private static final Map<String, List<String>> ENTITY_TERMS = new LinkedHashMap<>();
    static {
        ENTITY_TERMS.put("Cihazlar", List.of(
                // Genel
                "telefon", "tablet", "akıllı", "watch", "modem", "router", "laptop",
                // Spesifikasyon terimleri (neredeyse tüm cihazlarda geçer)
                "gb", "mp", "mah", "ekran", "kamera", "pil",
                // Markalar (bireysel.turktelekom.com.tr filtre listesinden)
                "apple", "general mobile", "hiking", "nothing", "nubia", "omix",
                "oppo", "ovion", "realme", "samsung", "tecno", "vivo", "xiaomi", "zte"));
        ENTITY_TERMS.put("Tarife", List.of(
                "tarife", "paket", "internet", "gb", "sınırsız", "aylık",
                "fatura", "aile", "genç", "superbox"));
        ENTITY_TERMS.put("Blog", List.of(
                "teknoloji", "dijital", "mobil", "internet", "uygulama",
                "oyun", "yapay", "güvenlik", "sosyal"));
        ENTITY_TERMS.put("Yardım/Destek", List.of(
                "nasıl", "ayarlar", "internet", "sorun", "şifre",
                "fatura", "numara", "sözleşme", "iptal", "aktarım"));
    }

    // ── Konfigürasyon ─────────────────────────────────────────────────────────
    private static final int     ROWS          = Integer.parseInt(System.getProperty("ttRows", "100"));
    /** -1 = tüm sonuçlar (önerilen); pozitif = her term için sayfa limiti */
    private static final int     MAX_PAGES     = Integer.parseInt(System.getProperty("ttMaxPages", "-1"));
    private static final String  REPORT_PATH   = System.getProperty("ttReportPath",
            "target/ttcomtr-url-report-" + java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".xlsx");
    private static final boolean FAIL_ON_ERROR = Boolean.parseBoolean(System.getProperty("ttFailOnError", "true"));
    private static final long    URL_DELAY_MS  = Long.parseLong(System.getProperty("ttUrlDelayMs", "200"));

    private final TtComtrSearchApi api = new TtComtrSearchApi();

    @Test
    void detailUrlGeçerliliğiKontrolü() throws IOException {

        List<UrlResult> allResults = new ArrayList<>();
        List<String>    failLines  = new ArrayList<>();
        Set<String>     seenUrls   = new LinkedHashSet<>(); // duplicate URL'leri atla

        System.out.printf("TTComtr URL testi başlıyor (rows=%d, maxPages=%s)%n",
                ROWS, MAX_PAGES == -1 ? "tümü" : String.valueOf(MAX_PAGES));

        // ── 1. Aşama: HTML Crawler — kategori sayfalarından tüm cihaz URL'leri ──
        System.out.println("%n╔══ HTML Crawler — /cihazlar/* sayfaları ══╗".formatted());
        List<TtComtrDeviceCrawler.DeviceUrl> crawledDevices = new TtComtrDeviceCrawler().crawlAll();
        for (TtComtrDeviceCrawler.DeviceUrl d : crawledDevices) {
            if (!seenUrls.add(d.fullUrl())) continue;
            if (URL_DELAY_MS > 0) {
                try { Thread.sleep(URL_DELAY_MS); } catch (InterruptedException ignored) {}
            }
            int    status = httpGet(d.fullUrl());
            String result = (status >= 200 && status < 400) ? "PASS" : "FAIL";
            allResults.add(new UrlResult("Cihazlar/" + d.category(), d.slug(), "-", d.fullUrl(), status, result));
            if ("FAIL".equals(result)) {
                String line = String.format("  ✗ [Cihazlar/%s] %s → HTTP %s", d.category(), d.fullUrl(), status == -1 ? "HATA" : status);
                failLines.add(line);
                System.err.println(line);
            } else {
                System.out.printf("  ✓ [%s] %s%n", d.category(), d.fullUrl());
            }
        }

        // ── 2. Aşama: smartsearch — Tarife, Blog, Yardım/Destek vb. ──
        System.out.println("%n╔══ smartsearch — Tarife / Blog / Yardım ══╗".formatted());
        for (Map.Entry<String, List<String>> entry : ENTITY_TERMS.entrySet()) {
            String       entityType = entry.getKey();
            List<String> terms      = entry.getValue();

            System.out.printf("%n╔══ EntityType: %s ══╗%n", entityType);

            for (String term : terms) {
                System.out.printf("%n  ── Terim: \"%s\" ──%n", term);

                int start     = 0;
                int pageCount = 0;
                int totalHits = Integer.MAX_VALUE;

                while (start < totalHits) {
                    if (MAX_PAGES >= 0 && pageCount >= MAX_PAGES) {
                        System.out.printf("  MaxPages=%d sınırına ulaşıldı.%n", MAX_PAGES);
                        break;
                    }

                    Response resp = api.search(term.trim(), entityType, start, ROWS);

                    if (resp.statusCode() != 200) {
                        System.err.printf("  ⚠ API %d döndü — terim atlanıyor.%n", resp.statusCode());
                        break;
                    }

                    JsonPath jp = resp.jsonPath();

                    if (pageCount == 0) {
                        Integer count = jp.get("facets.count");
                        totalHits = (count != null) ? count : 0;
                        System.out.printf("  Toplam: %d sonuç%n", totalHits);
                        if (totalHits == 0) break;
                    }

                    List<?> results = jp.getList("result");
                    if (results == null || results.isEmpty()) break;

                    int yeniUrl = 0;
                    for (int i = 0; i < results.size(); i++) {
                        String fullUrl = safeStr(jp.getString("result[" + i + "].url"));
                        String kind    = safeStr(jp.getString("result[" + i + "].kind"));
                        String title   = safeStr(jp.getString("result[" + i + "].title"));

                        if (fullUrl.isBlank()) {
                            allResults.add(new UrlResult(kind, title, "(yok)", "", 0, "SKIP"));
                            continue;
                        }

                        if (!seenUrls.add(fullUrl)) continue; // zaten kontrol edildi
                        yeniUrl++;

                        if (URL_DELAY_MS > 0) {
                            try { Thread.sleep(URL_DELAY_MS); } catch (InterruptedException ignored) {}
                        }

                        int    status = httpGet(fullUrl);
                        String result = (status >= 200 && status < 400) ? "PASS" : "FAIL";

                        allResults.add(new UrlResult(kind, title, "-", fullUrl, status, result));

                        if ("FAIL".equals(result)) {
                            String line = String.format("  ✗ [%s] %s → HTTP %s",
                                    kind, fullUrl, status == -1 ? "HATA" : status);
                            failLines.add(line);
                            System.err.println(line);
                        } else {
                            System.out.printf("  ✓ [%s] %s%n", kind, fullUrl);
                        }
                    }

                    System.out.printf("  Sayfa %d: %d yeni URL (toplam kontrol: %d)%n",
                            pageCount + 1, yeniUrl, seenUrls.size());

                    start += results.size();
                    pageCount++;
                }
            }
        }

        // ── Rapor ────────────────────────────────────────────────────────────
        new TtComtrUrlReportWriter().write(allResults, REPORT_PATH);

        // ── Özet ─────────────────────────────────────────────────────────────
        long pass = allResults.stream().filter(r -> "PASS".equals(r.result())).count();
        long fail = allResults.stream().filter(r -> "FAIL".equals(r.result())).count();
        long skip = allResults.stream().filter(r -> "SKIP".equals(r.result())).count();

        System.out.printf(
                "%n══════════════════════════════════%n" +
                "  PASS: %d   FAIL: %d   SKIP: %d%n" +
                "  Toplam kontrol: %d%n" +
                "  Rapor: %s%n" +
                "══════════════════════════════════%n",
                pass, fail, skip, allResults.size(), REPORT_PATH);

        if (FAIL_ON_ERROR) {
            assertTrue(failLines.isEmpty(),
                    "Geçersiz URL'ler (%d adet):\n".formatted(failLines.size())
                            + String.join("\n", failLines));
        }
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private int httpGet(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(8_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; TtComtrTest/1.0)");
            conn.connect();
            int status = conn.getResponseCode();
            conn.disconnect();
            return status;
        } catch (Exception e) {
            System.err.println("  Bağlantı hatası: " + urlStr + " → " + e.getMessage());
            return -1;
        }
    }

    private static String safeStr(String s) {
        return s == null ? "" : s.trim();
    }
}
