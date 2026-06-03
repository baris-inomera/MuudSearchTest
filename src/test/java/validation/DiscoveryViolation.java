package validation;

/**
 * Tek bir tip uyuşmazlığı (FAIL) olayı.
 * MuudSearchMappingDiscoveryTest tarafında doldurulur.
 *
 * Discovery testi binlerce response'u tarar ve her FAIL için tek bir satır üretir;
 * bu kayıtlar Excel raporunun "All Violations" sheet'inin kaynağıdır.
 */
public record DiscoveryViolation(
        String indexLabel,    // "Songs", "Albums" ...
        String esIndex,       // "muud_song_flat_v2"
        String indexId,       // "5"
        String term,          // hangi arama term'i bu doc'u getirdi
        String docId,
        String fieldPath,     // "data.songsPopularity"
        String expectedType,  // ES mapping'deki tip — "long"
        String actualType,    // JSON'da gelen — "NUMBER_FLOAT"
        String actualValue,   // gerçek değer (kırpılmış)
        String note
) {}
