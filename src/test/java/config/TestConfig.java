package config;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;

/**
 * Ortak test konfigürasyonu.
 * - Fail olduğunda request/response loglamak gibi ayarlar burada toplanır.
 * - İleride baseUrl/token gibi şeyleri sistem property üzerinden vermek istersen hazır.
 */
public class TestConfig {

    @BeforeAll
    static void setupLogging() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    public static String baseUrl() {
        return System.getProperty("baseUrl", "https://CHANGE-ME");
    }

    public static String token() {
        return System.getProperty("token", "CHANGE-ME");
    }

    public static String searchPath() {
        return System.getProperty("searchPath", "/search");
    }

    public static int limit() {
        return Integer.parseInt(System.getProperty("limit", "20"));
    }
}
