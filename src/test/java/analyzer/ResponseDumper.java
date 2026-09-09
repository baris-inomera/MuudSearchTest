package analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URI;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OLCUM CORPUSU TOPLAYICI — her case icin HAM API response'unu diske yazar.
 *
 * NEDEN VAR: Kural motorundaki katsayilar (topHitsScoreWeight, populerlik katsayilari,
 * birebir ad bonusu, tier farki) ancak ham response'lardan olculebiliyor. Excel raporu
 * skorlari ozetliyor ama tum alanlari tasimiyor. Tek tek Postman'den kopyalamak yerine
 * butun case'lerin response'unu bir kerede toplar.
 *
 * NASIL CALISIR: En yeni Excel raporundan case id + sorgu ciftlerini okur (boylece case
 * listesi ikinci bir yerde tekrarlanmaz), her sorgu icin API'yi cagirir ve cevabi
 * reports/api-responses/<ortam>/<caseId>__<sorgu>.json olarak yazar.
 *
 * KIMLIK BILGISI KODA YAZILMAZ. URL ve header'lar sistem property'sinden ya da
 * proje kokundeki analyzer.properties'ten okunur (o dosya .gitignore'da).
 *
 * KULLANIM (IntelliJ'de main olarak calistir, ya da):
 *   mvn -q test-compile exec:java -Dexec.classpathScope=test ^
 *       -Dexec.mainClass=analyzer.ResponseDumper ^
 *       -Dexec.args="reports/main-cases prod"
 *
 * analyzer.properties ornegi:
 *   dump.prod.url=https://searchapi.turktelekom.com.tr/gateway/search/rest/v10/active-indices/search
 *   dump.prod.appKey=muud
 *   dump.prod.auth=Basic xxxxxxxx
 *   dump.uat.url=https://mirketgateway.apps.erdek.paas.turktelekom.intra/gateway/search/rest/v10/active-indices/search
 *   dump.uat.appKey=muudelk9
 *   dump.uat.auth=Basic yyyyyyyy
 *
 * Yeniden calistirilabilir: zaten yazilmis dosyalari atlar, yarida kesilirse kaldigi
 * yerden devam eder.
 */
public final class ResponseDumper {

    private ResponseDumper() {}

    private static final ObjectMapper M = new ObjectMapper();
    /** Iki istek arasi bekleme — prod'a yuklenmemek icin. */
    private static final long GECIKME_MS = Long.getLong("dump.delayMs", 300L);
    private static final int LIMIT = Integer.getInteger("dump.limit", 30);
    private static final Pattern QUERY = Pattern.compile("\"([^\"]+)\"");

    public static void main(String[] args) throws Exception {
        String reportDir = args.length > 0 ? args[0] : "reports/main-cases";

        // ORTAM ARGUMANI ZORUNLU. Eskiden verilmezse sessizce "uat"a dusuyordu ve
        // prod bekleyen kullanici UAT'a istek attigini fark etmiyordu — o tuzagi kaldirdik.
        if (args.length < 2) {
            System.out.println("[dump] ORTAM BELIRTILMEDI.");
            System.out.println("[dump] Program arguments alanina yazin:  " + reportDir + " prod");
            System.out.println("[dump]                               ya da:  " + reportDir + " uat");
            System.out.println("[dump] IntelliJ: sag ustteki config listesinden");
            System.out.println("[dump]           \"ResponseDumper (prod)\" secilebilir.");
            System.out.println();
            System.out.println("[dump] analyzer.properties'te tanimli ortamlar:");
            for (String o : new String[]{"prod", "uat"}) {
                Ayar a = Ayar.yukle(o);
                System.out.println("[dump]   " + o + ": "
                        + (a == null ? "ayar EKSIK" : a.url)
                        + (a == null ? "" : "  (auth " + (a.auth.length() > 10 ? "dolu" : "BOS") + ")"));
            }
            return;
        }
        String ortam = args[1].trim().toLowerCase(Locale.ROOT);

        Ayar ayar = Ayar.yukle(ortam);
        if (ayar == null) {
            System.out.println("[dump] '" + ortam + "' ortami icin ayar bulunamadi.");
            System.out.println("[dump] analyzer.properties'e su satirlari ekleyin:");
            System.out.println("         dump." + ortam + ".url=...");
            System.out.println("         dump." + ortam + ".appKey=...");
            System.out.println("         dump." + ortam + ".auth=Basic ...");
            return;
        }
        System.out.println("[dump] ORTAM: " + ortam.toUpperCase(Locale.ROOT) + "  ->  " + ayar.url);

        Path rapor = BulguAnalyzer.enYeniRapor(reportDir);
        if (rapor == null) {
            System.out.println("[dump] " + reportDir + " altinda rapor bulunamadi.");
            return;
        }
        Map<String, String> sorgular = sorgulariOku(rapor);
        System.out.println("[dump] rapor: " + rapor.getFileName() + " -> " + sorgular.size() + " case");

        Path out = Path.of("reports", "api-responses", ortam);
        Files.createDirectories(out);

        // AG AYARI — Postman Windows'un sistem proxy'sini kullanir, Java KULLANMAZ.
        // Netzilo/kurumsal proxy varsa dogrudan baglanti ConnectException verir.
        // Varsayilan olarak sistem proxy'sini devreye aliyoruz; -Ddump.proxy=host:port
        // ile elle de verilebilir, -Ddump.proxy=none ile tamamen kapatilir.
        System.setProperty("java.net.useSystemProxies", "true");
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL);
        String proxyOpt = System.getProperty("dump.proxy", "system");
        if ("system".equalsIgnoreCase(proxyOpt)) {
            b.proxy(ProxySelector.getDefault());
            System.out.println("[dump] proxy: sistem ayari (java.net.useSystemProxies=true)");
        } else if (!"none".equalsIgnoreCase(proxyOpt)) {
            String[] hp = proxyOpt.split(":");
            int port = hp.length > 1 ? Integer.parseInt(hp[1]) : 8080;
            b.proxy(ProxySelector.of(new java.net.InetSocketAddress(hp[0], port)));
            System.out.println("[dump] proxy: " + hp[0] + ":" + port);
        } else {
            System.out.println("[dump] proxy: yok (dogrudan baglanti)");
        }
        // TLS — UAT gateway'i ic CA ile imzali bir sertifika sunuyor ve JDK truststore'unda
        // yok; dogrulama acikken SSLHandshakeException (PKIX path building failed) aliniyor.
        // Projedeki MuudSearchApi de ayni sebeple RestAssured'da relaxedHTTPSValidation()
        // kullaniyor. Bu yuzden dogrulama ORTAM BAZINDA ve ACIKCA kapatilabilir.
        //
        // DIKKAT: Bu bir guvenlik gevsetmesidir. Sadece ic ag hostlari icin ve yalnizca
        // okuma amacli sorgular icin kullanin. Dogru cozum sertifikayi truststore'a almaktir:
        //   keytool -importcert -alias muud-uat -cafile <cert.crt> \
        //           -keystore "%JAVA_HOME%\lib\security\cacerts" -storepass changeit
        if (ayar.insecureTls) {
            System.out.println("[dump] UYARI: TLS sertifika dogrulamasi bu ortam icin KAPATILDI "
                    + "(dump." + ortam + ".insecureTls=true).");
            b.sslContext(guvenmeyenSslContext());
        }

        HttpClient http = b.build();

        if (!onKontrol(http, ayar)) return;

        int yazilan = 0, atlanan = 0, hata = 0, i = 0;
        for (Map.Entry<String, String> e : sorgular.entrySet()) {
            i++;
            String caseId = e.getKey(), sorgu = e.getValue();
            Path hedef = out.resolve(dosyaAdi(caseId, sorgu));
            if (Files.exists(hedef) && Files.size(hedef) > 200) { atlanan++; continue; }

            try {
                String body = M.writeValueAsString(Map.of(
                        "text", sorgu,
                        "suggestion", true,
                        "correction", true,
                        "limit", LIMIT,
                        "offset", 0));
                // filters/fields null olarak gonderilmeli — Map.of null kabul etmiyor
                body = body.substring(0, body.length() - 1)
                        + ",\"filters\":null,\"fields\":null}";

                HttpRequest req = HttpRequest.newBuilder(URI.create(ayar.url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Accept", "*/*")
                        .header("X-SEARCH-APP-KEY", ayar.appKey)
                        .header("Authorization", ayar.auth)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() / 100 != 2) {
                    System.out.printf(Locale.ROOT, "[dump] %3d/%d  %-20s HTTP %d%n",
                            i, sorgular.size(), caseId, res.statusCode());
                    hata++;
                } else {
                    Files.writeString(hedef, res.body(), StandardCharsets.UTF_8);
                    yazilan++;
                    if (yazilan % 25 == 0) {
                        System.out.printf(Locale.ROOT, "[dump] %3d/%d yazildi...%n", i, sorgular.size());
                    }
                }
            } catch (Exception ex) {
                System.out.printf(Locale.ROOT, "[dump] %3d/%d  %-20s HATA: %s%n",
                        i, sorgular.size(), caseId, hataMetni(ex));
                hata++;
                if (hata == 5) {
                    System.out.println("[dump] 5 ust uste hata — devam etmenin anlami yok, duruyorum.");
                    System.out.println("[dump] Tesihs icin yukaridaki ON KONTROL cikitisina bakin.");
                    break;
                }
            }
            if (GECIKME_MS > 0) Thread.sleep(GECIKME_MS);
        }
        System.out.printf(Locale.ROOT,
                "[dump] BITTI — yazilan %d, atlanan (zaten vardi) %d, hata %d -> %s%n",
                yazilan, atlanan, hata, out.toAbsolutePath());
    }

    /**
     * ON KONTROL — 277 istek atmadan once baglantiyi tek seferde test eder ve
     * basarisizsa NEDENINI acikca yazar. ConnectException'in mesaji null geldigi
     * icin tum sebep zinciri basilir.
     */
    private static boolean onKontrol(HttpClient http, Ayar ayar) {
        System.out.println("[dump] --- ON KONTROL ---");
        URI u = URI.create(ayar.url);
        System.out.println("[dump] hedef: " + u.getHost() + ":" + (u.getPort() > 0 ? u.getPort() : 443));
        try {
            InetAddress[] adr = InetAddress.getAllByName(u.getHost());
            StringBuilder sb = new StringBuilder();
            for (InetAddress a : adr) { if (sb.length() > 0) sb.append(", "); sb.append(a.getHostAddress()); }
            System.out.println("[dump] DNS: " + sb);
        } catch (Exception e) {
            System.out.println("[dump] DNS COZULEMEDI: " + hataMetni(e));
            System.out.println("[dump] -> Ag/VPN baglantisini kontrol edin.");
            return false;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(u)
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-SEARCH-APP-KEY", ayar.appKey)
                    .header("Authorization", ayar.auth)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"text\":\"test\",\"filters\":null,\"fields\":null,"
                          + "\"suggestion\":false,\"correction\":false,\"limit\":1,\"offset\":0}",
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            System.out.println("[dump] HTTP " + res.statusCode()
                    + " (" + res.body().length() + " byte)");
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                System.out.println("[dump] -> Baglanti VAR ama yetki reddedildi. "
                        + "dump." + " auth / appKey degerlerini kontrol edin.");
                return false;
            }
            if (res.statusCode() / 100 != 2) {
                System.out.println("[dump] -> Beklenmeyen durum kodu, yine de devam ediyorum.");
            }
            System.out.println("[dump] on kontrol TAMAM, toplamaya basliyorum.");
            System.out.println("[dump] ------------------");
            return true;
        } catch (Exception e) {
            System.out.println("[dump] BAGLANTI KURULAMADI: " + hataMetni(e));
            System.out.println();
            System.out.println("[dump] OLASI SEBEPLER VE COZUMLERI:");
            System.out.println("[dump]  1) Kurumsal proxy: Postman Windows sistem proxy'sini kullanir, Java kullanmaz.");
            System.out.println("[dump]     Windows > Ayarlar > Ag ve Internet > Proxy'den adres/portu bulun ve");
            System.out.println("[dump]     VM options'a ekleyin:  -Ddump.proxy=PROXY_HOST:PORT");
            System.out.println("[dump]  2) Netzilo/VPN kapali olabilir — Postman'de ayni istegi simdi deneyin.");
            System.out.println("[dump]  3) Guvenlik duvari java.exe'yi engelliyor olabilir.");
            System.out.println("[dump]  4) SSLHandshakeException / PKIX path building failed goruyorsaniz:");
            System.out.println("[dump]     sunucunun sertifikasi JDK truststore'unda yok. analyzer.properties'e");
            System.out.println("[dump]     ekleyin:  dump.<ortam>.insecureTls=true");
            System.out.println("[dump]     (MuudSearchApi de ayni sebeple relaxedHTTPSValidation kullaniyor)");
            System.out.println("[dump]  Proxy'yi tamamen kapatmak icin: -Ddump.proxy=none");
            return false;
        }
    }

    /** Sebep zincirini duz metne cevirir — ConnectException'in mesaji genelde null. */
    private static String hataMetni(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null && sb.length() < 300; c = c.getCause()) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(c.getClass().getSimpleName());
            if (c.getMessage() != null && !c.getMessage().isBlank()) sb.append(": ").append(c.getMessage());
            if (c.getCause() == c) break;
        }
        return sb.toString();
    }

    /** Excel raporundan caseId -> sorgu haritasi. Test adindaki tirnak ici sorguyu alir. */
    private static Map<String, String> sorgulariOku(Path rapor) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        ExcelReport xls = null;
        try {
            xls = ExcelReport.open(rapor);
            for (ReportRow r : xls.rows) {
                if (r.caseId.isBlank()) continue;
                Matcher m = QUERY.matcher(r.testName);
                String q = m.find() ? m.group(1) : r.testName;
                q = q.replace("araması yapılır", "").trim();
                if (!q.isBlank()) out.putIfAbsent(r.caseId, q);
            }
        } catch (Exception e) {
            throw new IOException("rapor okunamadi: " + rapor, e);
        } finally {
            if (xls != null) xls.close();
        }
        return out;
    }

    private static String dosyaAdi(String caseId, String sorgu) {
        String slug = sorgu.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9çğıöşü ]", "")
                .trim().replaceAll("\\s+", "_");
        if (slug.length() > 40) slug = slug.substring(0, 40);
        return caseId + "__" + (slug.isBlank() ? "sorgu" : slug) + ".json";
    }

    /**
     * Sertifika dogrulamasi yapmayan SSLContext. Sadece ayar dosyasinda acikca
     * insecureTls=true diyen ortamlar icin kullanilir.
     */
    private static SSLContext guvenmeyenSslContext() throws Exception {
        TrustManager[] tm = { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) { }
            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) { }
        }};
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tm, new java.security.SecureRandom());
        // Hostname dogrulamasini da kapat — ic hostlarda CN sik sik uyusmuyor.
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        return ctx;
    }

    /** URL + header'lari sistem property'sinden, yoksa analyzer.properties'ten okur. */
    private static final class Ayar {
        final String url, appKey, auth;
        final boolean insecureTls;
        Ayar(String url, String appKey, String auth, boolean insecureTls) {
            this.url = url; this.appKey = appKey; this.auth = auth; this.insecureTls = insecureTls;
        }

        static Ayar yukle(String ortam) {
            String p = "dump." + ortam + ".";
            String url = deger(p + "url"), key = deger(p + "appKey"), auth = deger(p + "auth");
            if (url == null || url.isBlank() || auth == null || auth.isBlank()) return null;
            boolean ins = "true".equalsIgnoreCase(String.valueOf(deger(p + "insecureTls")));
            return new Ayar(url, key == null ? "muud" : key, auth, ins);
        }

        private static String deger(String anahtar) {
            String v = System.getProperty(anahtar);
            if (v != null && !v.isBlank()) return v;
            return AnalyzerConfig.raw(anahtar);
        }
    }

    /** JSON dosyalarini geri okumak icin yardimci — olcum scriptleri kullanabilir. */
    public static List<Path> mevcutDosyalar(String ortam) throws IOException {
        Path dir = Path.of("reports", "api-responses", ortam);
        if (!Files.isDirectory(dir)) return List.of();
        List<Path> out = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(x -> x.toString().endsWith(".json")).sorted().forEach(out::add);
        }
        return out;
    }

    /** Bir response dosyasindan topHits dizisini dondurur. */
    public static JsonNode topHits(Path json) throws IOException {
        return M.readTree(Files.readString(json, StandardCharsets.UTF_8)).path("topHits");
    }
}
