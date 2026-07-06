package tests;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Senaryo A — muud_song_flat_v2 → muud_song_flat_v3 Reindex
 *
 * Çalıştırmadan önce SSH tunnel açık olmalı:
 *   ssh -L 9200:localhost:9200 <bastion>
 *
 * Çalıştırma:
 *   mvn test -Dtest=ReindexTest
 *   mvn test -Dtest=ReindexTest#step1_createIndex       (sadece index oluştur)
 *   mvn test -Dtest=ReindexTest#step2_reindex           (sadece veri kopyala)
 *   mvn test -Dtest=ReindexTest#step3_verifyCount       (sadece sayı doğrula)
 *   mvn test -Dtest=ReindexTest#step4_deleteIndex       (geri alma — gerekirse)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReindexTest {

    private static final String ES_BASE    = "http://127.0.0.1:9200";
    private static final String SOURCE_IDX = "muud_song_flat_v2";
    private static final String DEST_IDX   = "muud_song_flat_v3";

    // Mapping JSON dosyası (src/test/resources/ altında)
    private static final String MAPPING_FILE =
            "src/test/resources/muud_song_flat_v2.mapping.json";

    // ─────────────────────────────────────────────────────────────────────────
    // Adım 1 — Yeni index'i mapping ile oluştur
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("Adım 1 — muud_song_flat_v3 index oluştur")
    void step1_createIndex() throws IOException {
        String rawJson = new String(Files.readAllBytes(Paths.get(MAPPING_FILE)));

        // JSON dosyası { "muud_song_flat_v2": { "mappings": {...} } } şeklinde.
        // PUT /{index} için sadece { "mappings": {...} } göndermek yeterli.
        // "muud_song_flat_v2": { içini al → mappings bloğunu çıkar.
        String mappingsBlock = extractMappingsBlock(rawJson);

        System.out.println("=== Adım 1: Index oluşturuluyor: " + DEST_IDX + " ===");
        System.out.println("Mapping body:\n" + mappingsBlock);

        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .body(mappingsBlock)
                .when()
                .put(ES_BASE + "/" + DEST_IDX);

        System.out.println("HTTP " + r.statusCode() + ": " + r.asString());

        // acknowledged:true → başarılı; index zaten varsa 400 döner
        Assertions.assertTrue(
                r.statusCode() == 200 || r.statusCode() == 400,
                "Beklenmedik HTTP kodu: " + r.statusCode() + " — " + r.asString()
        );

        if (r.statusCode() == 400 && r.asString().contains("resource_already_exists_exception")) {
            System.out.println("ℹ️  Index zaten mevcut, devam ediliyor.");
        } else {
            Assertions.assertEquals(200, r.statusCode(),
                    "Index oluşturma başarısız: " + r.asString());
            System.out.println("✅ Index oluşturuldu: " + DEST_IDX);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adım 2 — _reindex ile veriyi kopyala (async)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    @DisplayName("Adım 2 — _reindex başlat (async, wait_for_completion=false)")
    void step2_reindex() {
        String body = "{"
                + "\"source\":{\"index\":\"" + SOURCE_IDX + "\"},"
                + "\"dest\":{\"index\":\"" + DEST_IDX + "\"}"
                + "}";

        System.out.println("=== Adım 2: Reindex başlatılıyor ===");
        System.out.println("Source: " + SOURCE_IDX + " → Dest: " + DEST_IDX);

        // wait_for_completion=false → hemen task_id döner, arka planda çalışır
        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .body(body)
                .queryParam("wait_for_completion", "false")
                .when()
                .post(ES_BASE + "/_reindex");

        System.out.println("HTTP " + r.statusCode() + ": " + r.asString());
        Assertions.assertEquals(200, r.statusCode(),
                "Reindex başlatma başarısız: " + r.asString());

        String taskId = r.jsonPath().getString("task");
        System.out.println("✅ Reindex task başlatıldı. Task ID: " + taskId);
        System.out.println("   İlerlemeyi takip et:");
        System.out.println("   GET " + ES_BASE + "/_tasks/" + taskId);
        System.out.println("   veya step3_verifyCount testini çalıştır (tamamlanınca).");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adım 3 — Doküman sayısını doğrula
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    @DisplayName("Adım 3 — Doküman sayılarını karşılaştır (v2 vs v3)")
    void step3_verifyCount() {
        System.out.println("=== Adım 3: Doküman sayısı karşılaştırılıyor ===");

        long countV2 = getCount(SOURCE_IDX);
        long countV3 = getCount(DEST_IDX);

        System.out.println(SOURCE_IDX + " doküman sayısı: " + countV2);
        System.out.println(DEST_IDX   + " doküman sayısı: " + countV3);

        if (countV3 < countV2) {
            System.out.println("⚠️  v3 henüz tam dolmamış. Reindex devam ediyor olabilir.");
            System.out.println("   Tamamlanınca bu testi tekrar çalıştır.");
        } else {
            System.out.println("✅ Reindex tamamlandı. Sayılar eşit: " + countV3);
        }

        // Test fail etmez — sadece gözlemler
        Assertions.assertTrue(countV3 >= 0, "v3 index'e erişilemiyor.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // (İsteğe bağlı) Adım 4 — v3 index'i sil (geri alma)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    @Disabled("Sadece gerektiğinde: @Disabled kaldır ve çalıştır")
    @DisplayName("Adım 4 — muud_song_flat_v3 index sil (geri alma)")
    void step4_deleteIndex() {
        System.out.println("=== Adım 4: Index siliniyor: " + DEST_IDX + " ===");

        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .when()
                .delete(ES_BASE + "/" + DEST_IDX);

        System.out.println("HTTP " + r.statusCode() + ": " + r.asString());
        Assertions.assertEquals(200, r.statusCode(),
                "Index silme başarısız: " + r.asString());
        System.out.println("✅ Index silindi: " + DEST_IDX);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcı: Task durumu sorgula
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    @Order(5)
    @DisplayName("Adım 5 — Aktif reindex task'larını listele")
    void step5_listReindexTasks() {
        System.out.println("=== Aktif reindex task'ları ===");

        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .queryParam("actions", "*reindex")
                .queryParam("detailed", "true")
                .when()
                .get(ES_BASE + "/_tasks");

        System.out.println("HTTP " + r.statusCode() + ": " + r.asString());
        Assertions.assertEquals(200, r.statusCode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcılar
    // ─────────────────────────────────────────────────────────────────────────

    private long getCount(String index) {
        try {
            Response r = RestAssured.given()
                    .relaxedHTTPSValidation()
                    .when()
                    .get(ES_BASE + "/" + index + "/_count");
            if (r.statusCode() != 200) {
                System.out.println("WARN: " + index + " _count HTTP " + r.statusCode());
                return -1;
            }
            return r.jsonPath().getLong("count");
        } catch (Exception e) {
            System.out.println("WARN: " + index + " _count exception: " + e.getMessage());
            return -1;
        }
    }

    /**
     * muud_song_flat_v2.mapping.json dosyasındaki wrapping'i çıkarır.
     *
     * Gelen format:
     *   { "muud_song_flat_v2": { "mappings": { ... } } }
     *
     * Dönen format (PUT /{index} body'si için):
     *   { "mappings": { ... } }
     *
     * Brace balancing kullanır — sadece son '}' kesmek güvenli değil.
     */
    private String extractMappingsBlock(String rawJson) {
        int mappingsIdx = rawJson.indexOf("\"mappings\"");
        if (mappingsIdx < 0) {
            throw new IllegalStateException("mapping.json içinde 'mappings' anahtarı bulunamadı");
        }

        // "mappings" anahtarının value'sunu başlatan '{' bul
        int openBrace = rawJson.indexOf('{', mappingsIdx);
        if (openBrace < 0) {
            throw new IllegalStateException("mapping.json: 'mappings' değeri için '{' bulunamadı");
        }

        // Eşleşen kapanış '}' brace-balancing ile bul
        int depth = 0;
        boolean inString = false;
        int closePos = -1;
        for (int i = openBrace; i < rawJson.length(); i++) {
            char c = rawJson.charAt(i);
            // String içindeyken escape kontrolü
            if (c == '"') {
                int backslashes = 0;
                for (int j = i - 1; j >= 0 && rawJson.charAt(j) == '\\'; j--) backslashes++;
                if (backslashes % 2 == 0) inString = !inString;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { closePos = i; break; }
                }
            }
        }
        if (closePos < 0) {
            throw new IllegalStateException("mapping.json: 'mappings' değeri için eşleşen '}' bulunamadı");
        }

        // "mappings": { ... }  → { "mappings": { ... } }
        String mappingsKV = rawJson.substring(mappingsIdx, closePos + 1);
        return "{\n  " + mappingsKV + "\n}";
    }
}
