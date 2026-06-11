package client;

import io.restassured.RestAssured;
import io.restassured.response.Response;

public class MuudSearchApi {

    private static final String BASE_URL = "https://mirketgateway.apps.erdek.paas.turktelekom.intra";

    public Response search(String term, String indexId, int limit) {
        return search(term, indexId, limit, true);
    }

    public Response search(String term, String indexId, int limit, boolean correction) {
        return search(term, indexId, limit, correction, true);
    }

    public Response search(String term, String indexId, int limit, boolean correction, boolean suggestion) {

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
                "  \"suggestion\": " + suggestion + ",\n" +
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