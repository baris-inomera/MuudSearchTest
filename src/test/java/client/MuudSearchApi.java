// ============================================================
//  MuudSearchApi — UAT ORTAMI
//  Bu dosyanin TAMAMINI src/test/java/client/MuudSearchApi.java
//  uzerine kopyala. Baska hicbir yeri degistirmen gerekmiyor;
//  rapor adi (TestReport_<zaman>_UAT.xlsx) BASE_URL'den otomatik gelir.
// ============================================================
package client;

import io.restassured.RestAssured;
import io.restassured.response.Response;

public class MuudSearchApi {

    private static final String BASE_URL = "https://mirketgateway.apps.erdek.paas.turktelekom.intra";

    // ------------------------------------------------------------------
    // Ortam etiketi — rapor dosya adinda kullanilir:
    //     TestReport_<zaman>_PROD.xlsx  /  TestReport_<zaman>_UAT.xlsx
    // Etiket YUKARIDAKI BASE_URL'den turetilir. Ortam degistirmek icin
    // yalnizca o satiri (ve gerekiyorsa app-key / Authorization) duzenle;
    // bu iki metoda dokunmak gerekmez.
    // ------------------------------------------------------------------

    /** Aktif base URL — log ve rapor adlandirmasi icin. */
    public static String baseUrl() {
        return BASE_URL;
    }

    /**
     * BASE_URL'den ortam etiketi uretir: PROD / UAT / LOCAL. Taninmayan bir host
     * icin host adinin ilk parcasini buyuk harfle dondurur, boylece yeni bir
     * ortam eklenirse adlandirma yine calisir.
     */
    public static String ortam() {
        String u = BASE_URL.toLowerCase(java.util.Locale.ROOT);
        if (u.contains("searchapi.turktelekom.com.tr")) return "PROD";
        if (u.contains("mirketgateway") || u.contains("erdek.paas")) return "UAT";
        if (u.contains("localhost") || u.contains("127.0.0.1")) return "LOCAL";
        try {
            String h = new java.net.URI(BASE_URL).getHost();
            if (h != null && !h.isBlank()) {
                return h.split("\\.")[0].toUpperCase(java.util.Locale.ROOT);
            }
        } catch (Exception ignore) { /* etiket uretilemedi */ }
        return "BILINMEYEN";
    }

    /** correction=true ile ara (eski davranış, geriye dönük uyumlu). */
    public Response search(String term, String indexId, int limit) {
        return search(term, indexId, limit, true);
    }

    /**
     * correction parametresi ayarlanabilir arama.
     * DDP-366/367/368: correction=false (n-gram / özel karakter / etiket — doğrudan arama)
     * DDP-369:         correction=true  (klavye yazım düzeltme aktif)
     */
    public Response search(String term, String indexId, int limit, boolean correction) {

        String path;
        if ("active-indices".equals(indexId)) {
            path = "/gateway/search/rest/v10/active-indices/search";
        } else {
            path = "/gateway/search/rest/v10/indices/" + indexId + "/search";
        }

        String new_path = BASE_URL + path;
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
                .header("X-SEARCH-APP-KEY","muudelk9")
                // UAT (mirket) gateway. Kimlik: Basic kullanici WPPeHLyg.
                // NOT: kendi notuna gore WPPeHLyg = consumer "Muud" -> tenant muud;
                // consumer "MuudElk9" -> tenant hmuud. hmuud'a gitmesi gerekiyorsa
                // bu Basic degeri MuudElk9 consumer'inin degeriyle guncellenmeli.
                // X-SEARCH-APP-KEY header'i tenant secimini ETKILEMEZ.
                .header("Authorization", "Basic V1BQZUhMeWc6NUt5Y09ESlp4aFFxQXZtNQ==")
                .body(apiQueryBody)
                .when()
                .post(new_path)
                .then()
                .log().ifError()
                .extract().response();
    }

    // JSON formatını koruyan, Enter (\n) ve Null (\u0000) gibi karakterleri güvenli hale getiren metod
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
                    if (c < 32) { // \u0000 ile \u001f arasındaki tehlikeli karakterleri yakalar
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}