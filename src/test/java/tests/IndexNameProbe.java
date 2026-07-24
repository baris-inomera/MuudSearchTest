package tests;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tek amaç: API response'unda her sonucun hangi field'larla geldiğini görmek.
 * "A Canım" araması yaparak ilk 5 sonucun tüm field'larını loglar.
 * Çalıştır: mvn test -Dtest=IndexNameProbe
 */
public class IndexNameProbe extends TestConfig {

    @Test
    void probeIndexField() {
        MuudSearchApi api = new MuudSearchApi();
        Response res = api.search("a canım", "active-indices", 5);

        System.out.println("=== RAW STATUS: " + res.statusCode() + " ===");

        // topHits varsa onu, yoksa content'i al
        List<Map<String, Object>> hits = res.jsonPath().getList("topHits");
        if (hits == null) hits = res.jsonPath().getList("content");

        if (hits == null || hits.isEmpty()) {
            System.out.println("Sonuç yok!");
            return;
        }

        System.out.println("=== İLK " + hits.size() + " SONUCUN TÜM FIELD'LARI ===\n");

        for (int i = 0; i < hits.size(); i++) {
            Map<String, Object> hit = hits.get(i);
            System.out.println("--- Sonuç #" + (i + 1) + " ---");

            // topHits seviyesindeki field'lar (data dışı)
            for (Map.Entry<String, Object> entry : hit.entrySet()) {
                if (!entry.getKey().equals("data")) {
                    System.out.println("  [" + entry.getKey() + "] = " + entry.getValue());
                }
            }

            // data içindeki field'lar
            Object dataObj = hit.get("data");
            if (dataObj instanceof Map) {
                Map<?, ?> data = (Map<?, ?>) dataObj;
                System.out.println("  data:");
                for (Map.Entry<?, ?> e : data.entrySet()) {
                    System.out.println("    [" + e.getKey() + "] = " + e.getValue());
                }
            }
            System.out.println();
        }
    }
}
