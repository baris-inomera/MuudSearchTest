package analyzer;

/**
 * TestResultRow.sonuc icindeki "Ilk 5 sonuc" blogundan ayristirilmis tek bir sonuc satiri.
 *
 * rank        : 1..N (raporda yazan sira)
 * indexType   : "songs" | "performers" | "playlists" | "albums" | "playlist_groups" | "videos"
 * label       : sonucun adi (ornek: "Kaybedemem - Maya Perest")
 * popField    : raporun o satirda yazdigi populerlik alaninin ADI (popularity / popularity2 / prodPopularity)
 * popValue    : populerlik degeri; alan bos ise ('-') null
 * rawScore    : raporda yazan skor (index-ici)
 */
public record SearchHit(
        int rank,
        String indexType,
        String label,
        String popField,
        Double popValue,
        double rawScore
) {

    /** Index'ler arasi karsilastirilabilir skor. */
    public double weightedScore() {
        return rawScore * IndexWeights.of(indexType);
    }

    /** Populerlik alani raporda "-" olarak gelmis mi. */
    public boolean popMissing() {
        return popValue == null;
    }

    public String toPromptLine() {
        String pop = popValue == null ? "yok" : trim(popValue);
        return String.format(
                "%d. [%s] %s | %s=%s | skor=%s | agirlikli=%s",
                rank, indexType, label, popField, pop,
                trim(rawScore), trim(weightedScore()));
    }

    private static String trim(double d) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", d);
        return s.endsWith(".00") ? s.substring(0, s.length() - 3) : s;
    }
}
