// ============================================================
//  MuudSearchApi — HMUUD ORTAMI (UAT / hmuud tenant)
//  Bu dosyanin TAMAMINI src/test/java/client/MuudSearchApi.java
//  uzerine kopyala. Baska hicbir yeri degistirmen gerekmiyor;
//  rapor adi (TestReport_<zaman>_HMUUD.xlsx) otomatik gelir.
//
//  Kimlik: Salih Oran tarafından 15.09.2026 tarihinde iletildi.
// ============================================================
package client;

import io.restassured.RestAssured;
import io.restassured.response.Response;

public class MuudSearchApi {

    private static final String BASE_URL = "https://mirketgateway.apps.erdek.paas.turktelekom.intra";

    // ------------------------------------------------------------------
    // Ortam etiketi — rapor dosya adinda kullanilir.
    // ------------------------------------------------------------------

    public static String baseUrl() {
        return BASE_URL;
    }

    public static String ortam() {
        return "HMUUD";
    }

    /** correction=true ile ara (eski davranış, geriye dönük uyumlu). */
    public Response search(String term, String indexId, int limit) {
        return search(term, indexId, limit, true);
    }

    /**
     * correction parametresi ayarlanabilir arama.
     * correction=false → n-gram / özel karakter / etiket / playlist_group — doğrudan arama
     * correction=true  → klavye yazım düzeltme aktif
     */
    public Response search(String term, String indexId, int limit, boolean correction) {

        String path;
        if ("active-indices".equals(indexId)) {
            path = "/gateway/search/rest/v10/active-indices/search";
        } else {
            path = "/gateway/search/rest/v10/indices/" + indexId + "/search";
        }

        String newPath = BASE_URL + path;
        int safeLimit = limit <= 0 ? 10 : limit;

        String safeTerm = escapeJsonForApi(term);

        String apiQueryBody = "{\n" +
                "  \"text\": \"" + safeTerm + "\",\n" +
                "  \"filters\": null,\n" +
                "  \"fields\": null,\n" +
                "  \"suggestion\": true,\n" +
                "  \"correction\": " + correction + ",\n" +
                "  \"limit\": " + safeLimit + ",\n" +
                "  \"offset\": 0\n" +
                "}";

        return RestAssured
                .given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .header("Accept", "*/*")
                .header("X-SEARCH-APP-KEY", "muudelk9")
                // hmuud tenant kimliği — Salih Oran, 15.09.2026
                .header("Authorization", "Basic bXV1ZGVsazk6NUt5Y09ESlp4aFFxQXZtNQ==")
                .body(apiQueryBody)
                .when()
                .post(newPath)
                .then()
                .log().ifError()
                .extract().response();
    }

    /**
     * Tek bir term filtresiyle arama — örn. itemKind=playlistGroup.
     * correction=false, suggestion=false (filtreli arama için standart).
     */
    public Response searchFiltered(String term, String indexId, int limit,
                                   String filterField, String filterValue) {
        String path = "active-indices".equals(indexId)
                ? "/gateway/search/rest/v10/active-indices/search"
                : "/gateway/search/rest/v10/indices/" + indexId + "/search";

        String newPath = BASE_URL + path;
        int safeLimit = limit <= 0 ? 10 : limit;
        String safeTerm = escapeJsonForApi(term);

        String apiQueryBody = "{\n" +
                "  \"text\": \"" + safeTerm + "\",\n" +
                "  \"filters\": [{\"type\": \"term\", \"field\": \"" + filterField
                + "\", \"value\": \"" + filterValue + "\"}],\n" +
                "  \"fields\": null,\n" +
                "  \"suggestion\": false,\n" +
                "  \"correction\": false,\n" +
                "  \"limit\": " + safeLimit + ",\n" +
                "  \"offset\": 0\n" +
                "}";

        return RestAssured
                .given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .header("Accept", "*/*")
                .header("X-SEARCH-APP-KEY", "muudelk9")
                .header("Authorization", "Basic bXV1ZGVsazk6NUt5Y09ESlp4aFFxQXZtNQ==")
                .body(apiQueryBody)
                .when()
                .post(newPath)
                .then()
                .log().ifError()
                .extract().response();
    }

    private String escapeJsonForApi(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
