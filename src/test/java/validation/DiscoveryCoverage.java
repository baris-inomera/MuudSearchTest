package validation;

/**
 * Her index için tarama kapsamı özeti.
 * "Bu indekste kaç farklı search atıldı, kaç doc geldi, kaç tanesi tekrarsız, kaç FAIL bulundu?"
 */
public record DiscoveryCoverage(
        String indexLabel,
        String esIndex,
        String indexId,
        int searchCount,        // kaç arama yapıldı
        int docEvaluations,     // toplam doc-evaluation (aynı doc birden çok aramada gelmiş olabilir)
        int uniqueDocs,         // tekrarsız doc sayısı
        int violationCount,     // bu indekste kaç FAIL bulundu
        int passedSearches      // başarılı (200) arama sayısı
) {}
