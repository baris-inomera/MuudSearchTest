package domain;

/**
 * Tek bir test senaryosunun datası.
 *
 * Neden record?
 * - Immutable ve kısa
 * - Data-driven (parametrik) testler için ideal
 *
 * Alanlar:
 * - expectedStatus: normalde 200 bekleriz.
 *   - Eğer expectedStatus = -1 ise "200 veya 400 kabul" (discovery case)
 * - allowEmptyResults: bazı inputlar boş sonuç döndürebilir (ör: boş term, özel karakter)
 */
public record SearchCase(
        String caseId,
        String term,
        String excelType,
        int limit,
        Rule rule,
        String expArtistOrKeyword,
        String expTrack,
        int expectedStatus,
        boolean allowEmptyResults
) {}
