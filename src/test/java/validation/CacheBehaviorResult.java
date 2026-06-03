package validation;

/**
 * Davranış tabanlı cache testinin sonucu.
 *
 * Test akışı:
 *   1. Search yap → original değeri kaydet (R1)
 *   2. ES'de aynı dokümanı güncelle (örn manualScoreBoost: 100 → 999999)
 *   3. Hemen tekrar search yap → R2:
 *        - R2 == original ise → CACHE HIT (cache eski veriyi koruyor)
 *        - R2 == updated ise → cache yok veya invalidate edildi
 *   4. TTL kadar bekle (11 sn)
 *   5. Tekrar search yap → R3:
 *        - R3 == updated ise → TTL DOĞRU (cache expire oldu, ES'den taze geldi)
 *        - R3 == original ise → TTL çalışmıyor veya çok uzun
 *   6. Cleanup: ES'de eski değere döndür
 *
 * Verdict:
 *   CACHE_WORKING       — hem hit hem TTL kanıtlandı (asıl beklenen)
 *   CACHE_DISABLED      — ilk update'ten sonra cache eski tutmadı, ES'den geldi
 *                         (cacheEnabled=false durumunda beklenen davranış)
 *   TTL_NOT_EXPIRED     — hit kanıtı var ama TTL'den sonra hala eski veri
 *   CACHE_PARTIAL       — beklenmedik davranış kombinasyonu
 *   ERROR               — ES update/search başarısız
 *   SKIPPED             — ES tunnel açık değil
 */
public record CacheBehaviorResult(
        String caseId,
        String term,
        String indexId,
        String esIndex,
        String docId,
        String trackField,
        Object originalValue,
        Object updatedValue,
        Object afterUpdateSearchValue,   // ES update sonrası ilk search'te dönen
        Object afterTtlSearchValue,      // TTL beklemeden sonra dönen
        boolean hitDetected,
        boolean ttlDetected,
        Verdict verdict,
        String note
) {
    public enum Verdict {
        CACHE_WORKING,
        CACHE_DISABLED,
        TTL_NOT_EXPIRED,
        CACHE_PARTIAL,
        ERROR,
        SKIPPED
    }

    public String origValueStr() {
        return originalValue == null ? "null" : originalValue.toString();
    }

    public String updatedValueStr() {
        return updatedValue == null ? "null" : updatedValue.toString();
    }

    public String afterUpdateStr() {
        return afterUpdateSearchValue == null ? "null" : afterUpdateSearchValue.toString();
    }

    public String afterTtlStr() {
        return afterTtlSearchValue == null ? "null" : afterTtlSearchValue.toString();
    }
}
