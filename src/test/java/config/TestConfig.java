package config;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;

/**
 * Ortak test konfigürasyonu.
 * Tüm test sınıfları bu sınıfı extend eder.
 * Fail durumunda request/response otomatik loglanır.
 */
public class TestConfig {

    @BeforeAll
    static void setupLogging() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
}
