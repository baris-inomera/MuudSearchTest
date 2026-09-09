package analyzer;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Konfigurasyon oncelik sirasi:
 *   1) -D sistem property     (ornek: -Danalyzer.model=claude-haiku-4-5)
 *   2) ortam degiskeni        (ANTHROPIC_API_KEY, ANALYZER_MODEL ...)
 *   3) analyzer.properties    (calisma dizini veya classpath)
 *   4) varsayilan
 *
 * API KEY ASLA KODA YAZILMAZ. Bulunamazsa analyzer sessizce devre disi kalir,
 * test kosusu etkilenmez.
 */
public final class AnalyzerConfig {

    private static final Properties FILE_PROPS = load();

    private AnalyzerConfig() {}

    private static Properties load() {
        Properties p = new Properties();
        try {
            Path local = Path.of("analyzer.properties");
            if (Files.exists(local)) {
                try (var in = Files.newInputStream(local)) { p.load(in); }
                return p;
            }
            try (InputStream in = AnalyzerConfig.class
                    .getResourceAsStream("/analyzer/analyzer.properties")) {
                if (in != null) p.load(in);
            }
        } catch (Exception ignored) {
            // konfig okunamadi — varsayilanlarla devam
        }
        return p;
    }

    private static String get(String propKey, String envKey, String def) {
        String v = System.getProperty(propKey);
        if (isSet(v)) return v.trim();
        v = System.getenv(envKey);
        if (isSet(v)) return v.trim();
        v = FILE_PROPS.getProperty(propKey);
        if (isSet(v)) return v.trim();
        return def;
    }

    private static boolean isSet(String v) {
        return v != null && !v.isBlank();
    }

    /**
     * Rastgele bir anahtari ham olarak dondurur (sistem property -> ortam degiskeni ->
     * analyzer.properties sirasiyla). ResponseDumper gibi yardimci araclar, kimlik
     * bilgisini koda yazmadan buradan okur. Bulunamazsa null.
     */
    public static String raw(String key) {
        String v = get(key, key.toUpperCase(java.util.Locale.ROOT).replace('.', '_'), null);
        return isSet(v) ? v : null;
    }

    public static String apiKey() {
        return get("analyzer.apiKey", "ANTHROPIC_API_KEY", "");
    }

    public static boolean enabled() {
        if ("false".equalsIgnoreCase(get("analyzer.enabled", "ANALYZER_ENABLED", "true"))) return false;
        return !apiKey().isBlank();
    }

    /** Satir basina cagrilan ucuz model. */
    public static String rowModel() {
        return get("analyzer.model.row", "ANALYZER_MODEL_ROW", "claude-haiku-4-5");
    }

    /** Kosu basina bir kez cagrilan guclu model. */
    public static String runModel() {
        return get("analyzer.model.run", "ANALYZER_MODEL_RUN", "claude-sonnet-4-5");
    }

    public static String baseUrl() {
        return get("analyzer.baseUrl", "ANTHROPIC_BASE_URL", "https://api.anthropic.com");
    }

    /** Ayni anda kac NOK satiri analiz edilsin. */
    public static int concurrency() {
        return intOf(get("analyzer.concurrency", "ANALYZER_CONCURRENCY", "4"), 4);
    }

    /** Bu esigin altindaki guven "INCELENMELI" olarak isaretlenir. */
    public static double confidenceThreshold() {
        try {
            return Double.parseDouble(get("analyzer.confidenceThreshold",
                    "ANALYZER_CONFIDENCE_THRESHOLD", "0.5"));
        } catch (Exception e) {
            return 0.5;
        }
    }

    public static int timeoutSeconds() {
        return intOf(get("analyzer.timeoutSeconds", "ANALYZER_TIMEOUT_SECONDS", "90"), 90);
    }

    public static int maxRetries() {
        return intOf(get("analyzer.maxRetries", "ANALYZER_MAX_RETRIES", "3"), 3);
    }

    /** Ayni sorgu+sonuc imzasi icin tekrar LLM cagrilmasin diye onbellek dosyasi. */
    public static String cachePath() {
        return get("analyzer.cachePath", "ANALYZER_CACHE_PATH", "reports/.analyzer-cache.json");
    }

    /** Kosu ozeti (Sonnet) uretilsin mi. */
    public static boolean runSummaryEnabled() {
        return !"false".equalsIgnoreCase(
                get("analyzer.runSummary", "ANALYZER_RUN_SUMMARY", "true"));
    }

    private static int intOf(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
