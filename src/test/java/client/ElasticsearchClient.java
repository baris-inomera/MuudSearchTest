package client;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.util.Map;

/**
 * Elasticsearch'e dogrudan erisim icin basit REST client.
 *
 * SSH tunnel uzerinden 127.0.0.1:9200'e baglanir.
 *
 * Kullanim:
 *   ElasticsearchClient es = new ElasticsearchClient("http://127.0.0.1:9200");
 *   es.isAvailable();
 *   Map source = es.getDoc("muud_song_flat_v2", "3816461");
 *   es.updateField("muud_song_flat_v2", "3816461", "numPlays", 999999L);
 */
public class ElasticsearchClient {

    private final String baseUrl;

    public ElasticsearchClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public boolean isAvailable() {
        try {
            Response r = RestAssured.given()
                    .relaxedHTTPSValidation()
                    .when()
                    .get(baseUrl + "/");
            return r.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> getDoc(String index, String docId) {
        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .when()
                .get(baseUrl + "/" + index + "/_doc/" + docId);
        if (r.statusCode() != 200) {
            throw new EsException("GET doc HTTP " + r.statusCode() + ": " + r.asString());
        }
        return r.jsonPath().getMap("_source");
    }

    public Object getField(String index, String docId, String field) {
        Map<String, Object> src = getDoc(index, docId);
        return src.get(field);
    }

    /**
     * Bir dokumanin tek alanini gunceller (_update API + refresh=true).
     * Body raw JSON string olarak gonderilir; Jackson/Gson gerektirmez.
     */
    public void updateField(String index, String docId, String field, Object value) {
        String body = "{\"doc\":{\"" + escapeKey(field) + "\":" + toJsonLiteral(value) + "}}";

        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Content-Type", "application/json")
                .body(body)
                .when()
                .post(baseUrl + "/" + index + "/_update/" + docId + "?refresh=true");
        if (r.statusCode() / 100 != 2) {
            throw new EsException("POST _update HTTP " + r.statusCode() + ": " + r.asString());
        }
    }

    private static String toJsonLiteral(Object value) {
        if (value == null) return "null";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        String s = value.toString();
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '"') sb.append("\\\"");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 32) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String escapeKey(String key) {
        return key.replace("\"", "\\\"");
    }

    public static class EsException extends RuntimeException {
        public EsException(String message) {
            super(message);
        }
    }
}
