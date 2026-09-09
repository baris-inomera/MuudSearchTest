package analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TestResultRow.sonuc metnini yapisal veriye cevirir.
 *
 * Desteklenen iki format:
 *   yeni (01.09.2026):  1.[songs]Phonk - Castle.
 *                        | popularity2= 2.28 | score= 87.83274
 *   eski (04.08.2026):  1.[performers][Sanatci] 'Deyim Yerindeyse'
 *                        | prodPopularity= 22.0 | score= 67.467926
 *
 * Skor satiri, basliktan SONRAKI satirda gelir; ikisi birlestirilerek okunur.
 */
public final class SonucParser {

    private SonucParser() {}

    private static final Pattern HEAD =
            Pattern.compile("^\\s*(\\d+)\\s*\\.\\s*\\[(\\w+)\\]\\s*(.*)$");

    private static final Pattern SCORE =
            Pattern.compile("score\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)");

    /** ilk "| <alan>= <deger>" ciftini yakalar (score haric) */
    private static final Pattern POP =
            Pattern.compile("\\|\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^|]*)");

    /** "7. sirada bulundu" / "5. sırada bulundu" */
    private static final Pattern FOUND_AT =
            Pattern.compile("(\\d+)\\s*\\.\\s*s[ıi]rada bulundu");

    /** "top-10'da da bulunamadi" */
    private static final Pattern NOT_FOUND =
            Pattern.compile("bulunama");

    public static List<SearchHit> parseHits(String sonuc) {
        List<SearchHit> out = new ArrayList<>();
        if (sonuc == null || sonuc.isBlank()) return out;

        String[] lines = sonuc.split("\\r?\\n");
        int rank = -1;
        String idx = null, label = null;

        for (String line : lines) {
            Matcher h = HEAD.matcher(line);
            if (h.matches()) {
                rank = safeInt(h.group(1), out.size() + 1);
                idx = h.group(2).trim().toLowerCase(Locale.ROOT);
                label = cleanLabel(h.group(3));
                continue;
            }
            if (idx == null) continue;

            Matcher s = SCORE.matcher(line);
            if (!s.find()) continue;

            double raw = safeDouble(s.group(1));
            String popField = "";
            Double popValue = null;

            Matcher p = POP.matcher(line);
            while (p.find()) {
                String name = p.group(1);
                if (name.equalsIgnoreCase("score")) continue;
                popField = name;
                popValue = parsePop(p.group(2));
                break;
            }

            out.add(new SearchHit(rank, idx, label, popField, popValue, raw));
            idx = null;
            label = null;
        }
        return out;
    }

    /**
     * Beklenen icerigin kacinci sirada bulundugunu dondurur.
     * Bulunamadiysa -1.
     */
    public static int expectedFoundRank(String sonuc) {
        if (sonuc == null) return -1;
        String head = sonuc.split("\\r?\\n")[0];
        Matcher m = FOUND_AT.matcher(head);
        if (m.find()) return safeInt(m.group(1), -1);
        if (NOT_FOUND.matcher(head).find()) return -1;
        return -1;
    }

    /** Sonuc metninin ilk satiri (ozet cumlesi). */
    public static String headline(String sonuc) {
        if (sonuc == null || sonuc.isBlank()) return "";
        return sonuc.split("\\r?\\n")[0].trim();
    }

    private static String cleanLabel(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        // eski formattaki "[Sanatci] " / "[Album] " on ekini at
        s = s.replaceAll("^\\[[^\\]]*\\]\\s*", "");
        // bas/son tirnaklari at
        s = s.replaceAll("^'|'$", "").trim();
        return s.isEmpty() ? "(bos)" : s;
    }

    private static Double parsePop(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.equals("-") || s.equalsIgnoreCase("null")) return null;
        try {
            return Double.valueOf(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int safeInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static double safeDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; }
    }
}
