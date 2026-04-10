package domain;

/**
 * Her test case için uygulanacak doğrulama tipleri.
 *
 * Neden enum?
 * - Switch-case ile kural bazlı doğrulamayı netleştirir.
 * - Yeni kural eklemek istediğinde sadece buraya ekleyip test switch'ine eklersin.
 */
public enum Rule {

    // ------------------------------
    // Functional / Relevance Rules
    // ------------------------------

    FIRST_ARTIST_IS,                  // results[0].artistName bekleneni içeriyor mu?
    TOPN_HAS_ARTIST,                  // ilk N sonuç içinde artistName bekleneni içeriyor mu?
    TOPN_HAS_ARTIST_AND_TRACK,        // ilk N içinde aynı kayıtta artistName + trackName bekleneni içeriyor mu?
    TOPN_RELATED_ALBUM,               // ilk N albüm içinde collectionName keyword içeriyor mu?
    TOPN_RELATED_PLAYLIST,            // ilk N sonuç içinde playlistName keyword içeriyor mu?

    // ------------------------------
    // Contract & Consistency Rules
    // ------------------------------

    CONTRACT_HAS_FIELDS,              // Response JSON contract: resultCount + results alanları var mı?
    RESULTCOUNT_MATCHES_SIZE,         // resultCount ile results.size() tutarlı mı?
    LIMIT_RESPECTED,                  // results.size() <= limit olmalı

    /**
     * Entity'ye göre beklenen alanın dolu gelmesi gerekir.
     * Örnek:
     * - entity=song => trackName dolu olmalı
     * - entity=album => collectionName dolu olmalı
     * - entity=musicArtist => artistName dolu olmalı
     */
    ENTITY_FIELD_PRESENT,

    // ------------------------------
    // Robustness / Discovery Rules
    // ------------------------------

    ALLOW_EMPTY_RESULTS,              // Sonuç boş olabilir ama contract bozulmamalı
    DISCOVER_LIMIT_ZERO_STATUS,       // limit=0 için 200 veya 400 kabul (keşif)
    DISCOVER_NEGATIVE_LIMIT_STATUS,   // limit<0 için 200 veya 400 kabul (invalid boundary)



    /**
     * Lyrics benzeri aramalarda iTunes lyrics index garantisi vermez.
     * - results boşsa FAIL etmiyoruz
     * - results doluysa: TopN içinde beklenen artist+track bulunmalı
     */
    LYRICS_DISCOVERY_IF_RESULTS_THEN_MATCH,

    // ------------------------------
    // Performance
    // ------------------------------

    RESPONSE_TIME_UNDER_THRESHOLD     // Response time belirlenen threshold altında olmalı

}