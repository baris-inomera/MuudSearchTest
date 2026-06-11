package validation;

/**
 * Mapping doğrulamasında bulunan tek bir kayıt (PASS, FAIL, eksik veya null).
 * Excel raporuna bu sınıftan satır basılır.
 *
 * Mevcut TestResultRow ile karıştırılmasın — orası arama sonucu testlerinin satırı.
 * Burası bambaşka bir test türü: alan-bazlı tip doğrulaması.
 */
public record TypeMismatch(
        String docId,           // hangi doküman (örn. "song-12345")
        String fieldPath,       // "data.numPlays" veya "data.performers.performerId"
        String expectedEsType,  // long, keyword, boolean, ...
        String actualJsonType,  // NUMBER_INT, STRING, BOOLEAN, ...
        String actualValue,     // gerçek değerin string gösterimi (kırpılmış)
        Status status,          // PASS / FAIL / MISSING / NULL
        String note             // opsiyonel açıklama
) {
    public enum Status { PASS, FAIL, MISSING_IN_MAPPING, NULL_VALUE, MISSING_IN_RESPONSE }

    public boolean isFailure() {
        return status == Status.FAIL;
    }

    public boolean isPass() {
        return status == Status.PASS;
    }
}
