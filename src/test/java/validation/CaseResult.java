package validation;

import java.util.List;

/**
 * Tek bir test case'inin sonuçları — Excel raporunda
 * "Case Summary" sheet'inin tek bir satırına karşılık gelir.
 *
 *   caseId   : "MT_ALBUM_01"
 *   term     : "best"
 *   indexId  : "2"
 *   status   : "PASS" / "FAIL" / "SKIPPED"
 *   mismatches: o case'de toplanan tüm field-bazlı sonuçlar
 */
public record CaseResult(
        String caseId,
        String term,
        String indexId,
        String status,
        List<TypeMismatch> mismatches
) {

    public long passCount() {
        return mismatches.stream().filter(TypeMismatch::isPass).count();
    }

    public long failCount() {
        return mismatches.stream().filter(TypeMismatch::isFailure).count();
    }

    public long missingCount() {
        return mismatches.stream()
                .filter(m -> m.status() == TypeMismatch.Status.MISSING_IN_MAPPING)
                .count();
    }

    public long nullCount() {
        return mismatches.stream()
                .filter(m -> m.status() == TypeMismatch.Status.NULL_VALUE)
                .count();
    }

    public int total() {
        return mismatches.size();
    }
}
