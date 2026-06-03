package validation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Aynı (esIndex + fieldPath + expectedType + actualType) için aggregate.
 *
 * Discovery testinin asıl çıktısı bu sınıftır:
 *   "data.songsPopularity (long → NUMBER_FLOAT) — 47 farklı doc'ta görüldü"
 *
 * Aynı bug'a saplanmış 47 doc yerine, ekiple konuşulurken 1 satırla anlatılır.
 */
public final class DiscoveryFieldSummary {

    private final String indexLabel;
    private final String esIndex;
    private final String fieldPath;
    private final String expectedType;
    private final String actualType;
    private final Set<String> affectedDocs = new LinkedHashSet<>();
    private final Set<String> sampleValues = new LinkedHashSet<>();
    private final Set<String> sampleTerms = new LinkedHashSet<>();

    public DiscoveryFieldSummary(String indexLabel, String esIndex, String fieldPath,
                                 String expectedType, String actualType) {
        this.indexLabel = indexLabel;
        this.esIndex = esIndex;
        this.fieldPath = fieldPath;
        this.expectedType = expectedType;
        this.actualType = actualType;
    }

    public void addOccurrence(String docId, String value, String term) {
        affectedDocs.add(docId);
        if (value != null && sampleValues.size() < 10) sampleValues.add(value);
        if (term != null && sampleTerms.size() < 10) sampleTerms.add(term);
    }

    public String indexLabel()  { return indexLabel; }
    public String esIndex()     { return esIndex; }
    public String fieldPath()   { return fieldPath; }
    public String expectedType(){ return expectedType; }
    public String actualType()  { return actualType; }
    public Set<String> affectedDocs() { return affectedDocs; }
    public Set<String> sampleValues() { return sampleValues; }
    public Set<String> sampleTerms()  { return sampleTerms; }

    public String key() {
        return esIndex + "|" + fieldPath + "|" + expectedType + "|" + actualType;
    }
}
