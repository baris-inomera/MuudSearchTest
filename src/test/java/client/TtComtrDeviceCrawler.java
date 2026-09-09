package client;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * bireysel.turktelekom.com.tr/cihazlar/* sayfalarını HTML olarak çekip
 * cihaz URL'lerini parse eder.
 *
 * Sayfalama: ?page=N parametresi ile çalışır.
 * Her kategori sayfasında cihaz URL'leri server-side render edilmiş
 * olarak gelir — JavaScript gerektirmez.
 *
 * Kullanım:
 *   List<DeviceUrl> urls = new TtComtrDeviceCrawler().crawlAll();
 */
public class TtComtrDeviceCrawler {

    public static final String BASE = "https://bireysel.turktelekom.com.tr";

    /** Cihaz URL pattern — hem absolute hem relative URL'leri yakalar */
    private static final Pattern DEVICE_URL_PATTERN = Pattern.compile(
            "(?:https://bireysel\\.turktelekom\\.com\\.tr)?/cihazlar/([a-z-]+)/([a-z0-9-]+)");

    /** Taranacak kategori sayfaları */
    public static final List<String> CATEGORIES = List.of(
            "/cihazlar/akilli-telefon",
            "/cihazlar/tablet",
            "/cihazlar/aksesuar",
            "/cihazlar/modem-ve-internet",
            "/cihazlar/ev-telefonu",
            "/cihazlar/bilgisayar",
            "/cihazlar/akilli-tercihler"
    );

    public record DeviceUrl(String category, String slug, String fullUrl) {}

    /**
     * Tüm kategorileri sayfalayarak tarar, benzersiz cihaz URL'lerini döner.
     */
    public List<DeviceUrl> crawlAll() {
        Set<String> seen = new LinkedHashSet<>();
        List<DeviceUrl> results = new ArrayList<>();

        for (String categoryPath : CATEGORIES) {
            System.out.printf("%n  ── Kategori: %s ──%n", categoryPath);
            int page = 1;
            int emptyPages = 0;

            while (emptyPages < 2) {
                String url = BASE + categoryPath + "?page=" + page;
                System.out.printf("    Sayfa %d...%n", page);

                String html = fetchHtml(url);
                if (html == null) {
                    System.err.println("    ⚠ Sayfa alınamadı, atlanıyor.");
                    break;
                }

                List<DeviceUrl> found = parseDeviceUrls(html, seen);

                if (found.isEmpty()) {
                    emptyPages++;
                    System.out.printf("    Yeni cihaz yok (boş sayfa %d/2)%n", emptyPages);
                } else {
                    emptyPages = 0;
                    results.addAll(found);
                    System.out.printf("    %d yeni cihaz URL (toplam: %d)%n", found.size(), seen.size());
                }

                page++;

                // Kısa bekleme — WAF koruması
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            }
        }

        System.out.printf("%n  Crawler tamamlandı. Toplam benzersiz cihaz URL: %d%n", results.size());
        return results;
    }

    /**
     * HTML metninden cihaz URL'lerini çıkarır (daha önce görülmeyenleri).
     */
    private List<DeviceUrl> parseDeviceUrls(String html, Set<String> seen) {
        List<DeviceUrl> found = new ArrayList<>();
        Matcher m = DEVICE_URL_PATTERN.matcher(html);
        while (m.find()) {
            String matched  = m.group(0);
            // Relative URL'yi absolute'a çevir
            String fullUrl  = matched.startsWith("http") ? matched : BASE + matched;
            String category = m.group(1);
            String slug     = m.group(2);

            // Kategori sayfasının kendisini atla (sadece /cihazlar/akilli-telefon gibi)
            // ve akilli-tercihler altındaki servis sayfalarını atla
            if (slug.equals("akilli-tercihler") || slug.equals("akilli-telefon")
                    || slug.equals("tablet") || slug.equals("aksesuar")
                    || slug.equals("modem-ve-internet") || slug.equals("ev-telefonu")
                    || slug.equals("bilgisayar")) {
                continue;
            }

            if (seen.add(fullUrl)) {
                found.add(new DeviceUrl(category, slug, fullUrl));
            }
        }
        return found;
    }

    /**
     * Verilen URL'yi HTML olarak çeker.
     */
    private String fetchHtml(String url) {
        try {
            Response resp = RestAssured.given()
                    .relaxedHTTPSValidation()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
                    .redirects().follow(true)
                    .when()
                    .get(url)
                    .then()
                    .extract().response();

            if (resp.statusCode() != 200) {
                System.err.printf("    HTTP %d: %s%n", resp.statusCode(), url);
                return null;
            }
            return resp.body().asString();
        } catch (Exception e) {
            System.err.printf("    Hata: %s → %s%n", url, e.getMessage());
            return null;
        }
    }
}
