package analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ayni sorgu + ayni sonuc kumesi tekrar analiz edilmesin diye kalici onbellek.
 *
 * Imza = caseId + beklenen + (index, ad, skor) ucluleri.
 * Sonuclar degistigi anda imza degisir ve satir yeniden analiz edilir.
 *
 * 269 case'lik bir kosuda NOK'larin cogu bir onceki kosudakiyle ayni oldugundan
 * pratikte cagri sayisi ciddi olcude duser.
 */
public final class AnalysisCache {

    private static final ObjectMapper M = new ObjectMapper();

    private final Path file;
    private final Map<String, ObjectNode> mem = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public AnalysisCache(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        try {
            if (file == null || !Files.exists(file)) return;
            JsonNode root = M.readTree(Files.readString(file, StandardCharsets.UTF_8));
            root.fields().forEachRemaining(e -> {
                if (e.getValue().isObject()) mem.put(e.getKey(), (ObjectNode) e.getValue());
            });
        } catch (Exception ignored) {
            // bozuk onbellek — yok say, yeniden uretilir
        }
    }

    public static String signature(ReportRow row) {
        StringBuilder sb = new StringBuilder();
        sb.append(row.caseId).append('|').append(row.expected).append('|').append(row.headline());
        for (SearchHit h : row.hits()) {
            sb.append('|').append(h.indexType()).append(':').append(h.label())
              .append(':').append(String.format(java.util.Locale.ROOT, "%.3f", h.rawScore()));
        }
        return sha256(sb.toString());
    }

    public BulguAnalysis get(String sig, String caseId) {
        ObjectNode n = mem.get(sig);
        if (n == null) return null;
        return new BulguAnalysis(
                caseId,
                n.path("bulgu").asBoolean(true),
                n.path("kategori").asText("RANKING"),
                n.path("cozum").asText(""),
                n.path("devNotu").asText(""),
                n.path("guven").asDouble(0.0),
                n.path("kokNeden").asText(""),
                n.path("kaynak").asText("LLM"),
                n.path("kanit").asText(""),
                false);
    }

    public void put(String sig, BulguAnalysis a) {
        if (a.analizEdilemedi()) return;              // basarisiz analizi onbellege alma
        ObjectNode n = M.createObjectNode();
        n.put("bulgu", a.bulgu());
        n.put("kategori", a.kategori());
        n.put("cozum", a.cozum());
        n.put("devNotu", a.devNotu());
        n.put("guven", a.guven());
        n.put("kokNeden", a.kokNeden());
        n.put("kaynak", a.kaynak());
        n.put("kanit", a.kanit());
        mem.put(sig, n);
        dirty = true;
    }

    public void flush() {
        if (!dirty || file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            ObjectNode root = M.createObjectNode();
            for (Map.Entry<String, ObjectNode> e : mem.entrySet()) root.set(e.getKey(), e.getValue());
            Files.writeString(file, M.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                    StandardCharsets.UTF_8);
            dirty = false;
        } catch (Exception ignored) {
            // onbellek yazilamadi — kritik degil
        }
    }

    public int size() {
        return mem.size();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** few-shot dosyasini buyutmek icin: onaylanmis kararlari disari aktar. */
    public List<ObjectNode> all() {
        return List.copyOf(mem.values());
    }
}
