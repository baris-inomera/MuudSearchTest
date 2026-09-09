package client;

import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * Türk Telekom Smart Search API istemcisi.
 *
 * Endpoint: https://smartsearch.turktelekom.com.tr/tt-search-engine/search/search/default
 * Method  : GET
 * Auth    : Yok — public endpoint
 *
 * Örnek istek (browser'dan gözlemlenen):
 *   GET /tt-search-engine/search/search/default
 *       ?alias=web
 *       &search_text=iphone
 *       &start=0
 *       &rows=30
 *       &additional_fields=true
 *       &entity_type=*
 *       &group_by_kind=true
 *
 * Yanıt yapısı:
 *   {
 *     "result": [{ "url": "https://...", "kind": "Cihazlar", "title": "..." }, ...],
 *     "facets": { "count": 96, ... }
 *   }
 *
 * System property override'ları:
 *   -DttSearchBaseUrl=https://smartsearch.turktelekom.com.tr
 *   -DttAlias=web
 */
public class TtComtrSearchApi {

    public static final String BASE_URL = System.getProperty(
            "ttSearchBaseUrl", "https://smartsearch.turktelekom.com.tr");

    private static final String ALIAS = System.getProperty("ttAlias", "web");

    /**
     * Arama yapar ve Response döner.
     *
     * @param searchText Arama terimi (boş string geçilebilir)
     * @param entityType İçerik tipi filtresi: "*" = hepsi, "Cihazlar", "Tarife", "Blog", "Yardım/Destek"
     * @param start      Sayfalama başlangıcı (0, 30, 60, ...)
     * @param rows       Sayfa başına sonuç sayısı
     */
    public Response search(String searchText, String entityType, int start, int rows) {
        return RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (compatible; TtComtrTest/1.0)")
                .queryParam("alias",            ALIAS)
                .queryParam("search_text",      searchText == null ? "" : searchText)
                .queryParam("start",            start)
                .queryParam("rows",             rows)
                .queryParam("additional_fields","true")
                .queryParam("entity_type",      entityType == null ? "*" : entityType)
                .queryParam("group_by_kind",    "true")
                .when()
                .get(BASE_URL + "/tt-search-engine/search/search/default")
                .then()
                .log().ifError()
                .extract().response();
    }

    /** Tüm türleri döndürür. */
    public Response searchAll(String searchText, int start, int rows) {
        return search(searchText, "*", start, rows);
    }
}
