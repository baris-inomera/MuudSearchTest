package analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * KURAL MOTORU — LLM'siz, sifir maliyetli, tamamen deterministik analiz.
 *
 * Kurallar kodda DEGIL, src/test/resources/analyzer/kurallar.json icinde durur.
 * Ekip yeni bir yorum urettikce oraya kural ekler; kod degismez, yeniden derleme gerekmez.
 * "Kendi yorumlarimizla besleyecegiz" dongusu tam olarak budur.
 *
 * Calisma sekli: kurallar oncelik (buyukten kucuge) sirasiyla denenir, ILK eslesen kazanir.
 * Hicbiri eslesmezse null doner -> satir LLM'e gider (anahtar varsa), yoksa INCELENMELI kalir.
 */
public final class KuralMotoru {

    private static final ObjectMapper M = new ObjectMapper();

    private final List<Kural> kurallar;

    public KuralMotoru() {
        this.kurallar = yukle();
    }

    public int kuralSayisi() {
        return kurallar.size();
    }

    /** Eslesen ilk kuralin sonucunu dondurur; eslesme yoksa null. */
    public BulguAnalysis uygula(RowContext c, String kanit) {
        for (Kural k : kurallar) {
            if (k.esler(c)) {
                return new BulguAnalysis(
                        c.caseId,
                        k.bulgu,
                        k.kategori,
                        c.doldur(k.cozum),
                        // Kanit seviyesi analiz metninin BASINA eklenir: aksiyona bakan kisi
                        // arkasindaki delilin ne kadar saglam oldugunu gormeden karar vermesin.
                        (k.kanit == null || k.kanit.isBlank()
                                ? c.doldur(k.devNotu)
                                : "[KANIT: " + k.kanit + "]\n" + c.doldur(k.devNotu)),
                        k.guven,
                        c.doldur(k.kokNeden),
                        k.id,
                        kanit,
                        false);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------

    private static List<Kural> yukle() {
        List<Kural> out = new ArrayList<>();
        try {
            JsonNode arr = oku();
            if (arr == null || !arr.isArray()) return out;
            for (JsonNode n : arr) {
                try { out.add(Kural.of(n)); }
                catch (Exception e) {
                    BulguAnalyzer.log("Kural atlandi (" + n.path("id").asText("?") + "): " + e.getMessage());
                }
            }
            out.sort(Comparator.comparingInt((Kural k) -> k.oncelik).reversed());
        } catch (Exception e) {
            BulguAnalyzer.log("kurallar.json okunamadi: " + e.getMessage());
        }
        return out;
    }

    private static JsonNode oku() throws Exception {
        Path local = Path.of("src/test/resources/analyzer/kurallar.json");
        if (Files.exists(local)) {
            return M.readTree(Files.readString(local, StandardCharsets.UTF_8));
        }
        try (InputStream in = KuralMotoru.class.getResourceAsStream("/analyzer/kurallar.json")) {
            if (in == null) return null;
            return M.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------

    /** Tek bir kural: kosullar (hepsi VE ile baglidir) + sonuc. */
    private static final class Kural {
        String id, kategori, cozum, devNotu, kokNeden, kanit;
        boolean bulgu;
        double guven;
        int oncelik;

        // kosullar — null / -1 = "bakma"
        String testTipiIcerir, sorguRegex, ilkIndex, devrilenUstIndex, devrilenAltIndex, oluPopulerlik,
               populerlikKarsilastirmasi;
        Boolean karsilastirmaAyniIndex, tekIndexBaskini;
        int sorguKelimeEnAz = -1, sorguKelimeEnCok = -1, sorguUzunlukEnCok = -1;
        Boolean bulunamadi, agirlikDevrilmesi, duplikasyon, regresyon, ustteperformerVar,
                ilkSonucSorguylaAyni, apiHatasi;
        int beklenenSiraEnAz = -1, beklenenSiraEnCok = -1;
        int birebirEslesmeEnAz = -1, populerlikEksikEnAz = -1, indexCesidiEnAz = -1;
        int sanatciVaryantEnAz = -1;

        static Kural of(JsonNode n) {
            Kural k = new Kural();
            k.id = req(n, "id");
            k.oncelik = n.path("oncelik").asInt(0);
            // Bu kuralin dayandigi olcumun gucu (kac sorgu / kac gozlem).
            k.kanit = n.path("kanit").asText("").trim();

            JsonNode s = n.path("sonuc");
            k.bulgu = s.path("bulgu").asBoolean(true);
            k.kategori = s.path("kategori").asText("RANKING").trim().toUpperCase();
            if (!BulguAnalysis.KATEGORILER.contains(k.kategori)) {
                throw new IllegalArgumentException("gecersiz kategori: " + k.kategori);
            }
            k.cozum = s.path("cozum").asText("");
            k.devNotu = s.path("devNotu").asText("");
            k.kokNeden = s.path("kokNeden").asText("");
            k.guven = Math.max(0, Math.min(1, s.path("guven").asDouble(0.8)));

            JsonNode c = n.path("kosullar");
            k.testTipiIcerir    = txt(c, "testTipiIcerir");
            k.sorguRegex        = txt(c, "sorguRegex");
            k.ilkIndex          = txt(c, "ilkIndex");
            k.devrilenUstIndex  = txt(c, "devrilenUstIndex");
            k.devrilenAltIndex  = txt(c, "devrilenAltIndex");
            k.oluPopulerlik     = txt(c, "oluPopulerlik");
            k.bulunamadi        = bool(c, "bulunamadi");
            k.agirlikDevrilmesi = bool(c, "agirlikDevrilmesi");
            k.duplikasyon       = bool(c, "duplikasyon");
            k.regresyon         = bool(c, "regresyon");
            k.ustteperformerVar = bool(c, "ustteperformerVar");
            k.ilkSonucSorguylaAyni = bool(c, "ilkSonucSorguylaAyni");
            k.apiHatasi         = bool(c, "apiHatasi");
            k.sanatciVaryantEnAz = c.path("sanatciVaryantEnAz").asInt(-1);
            k.populerlikKarsilastirmasi = txt(c, "populerlikKarsilastirmasi");
            k.karsilastirmaAyniIndex    = bool(c, "karsilastirmaAyniIndex");
            k.tekIndexBaskini           = bool(c, "tekIndexBaskini");
            k.sorguKelimeEnAz           = c.path("sorguKelimeEnAz").asInt(-1);
            k.sorguKelimeEnCok          = c.path("sorguKelimeEnCok").asInt(-1);
            k.sorguUzunlukEnCok         = c.path("sorguUzunlukEnCok").asInt(-1);
            k.beklenenSiraEnAz  = c.path("beklenenSiraEnAz").asInt(-1);
            k.beklenenSiraEnCok = c.path("beklenenSiraEnCok").asInt(-1);
            k.birebirEslesmeEnAz = c.path("birebirEslesmeEnAz").asInt(-1);
            k.populerlikEksikEnAz = c.path("populerlikEksikEnAz").asInt(-1);
            k.indexCesidiEnAz   = c.path("indexCesidiEnAz").asInt(-1);
            return k;
        }

        boolean esler(RowContext c) {
            if (testTipiIcerir != null
                    && !ExcelReport.norm(c.testTipi).contains(ExcelReport.norm(testTipiIcerir))) return false;
            if (sorguRegex != null && !c.sorgu.matches(sorguRegex)) return false;
            if (ilkIndex != null && !ilkIndex.equalsIgnoreCase(c.ilkIndex)) return false;
            if (devrilenUstIndex != null && !devrilenUstIndex.equalsIgnoreCase(c.devrilenUstIndex)) return false;
            if (devrilenAltIndex != null && !devrilenAltIndex.equalsIgnoreCase(c.devrilenAltIndex)) return false;
            if (oluPopulerlik != null && !c.oluPopulerlikIndexleri.contains(oluPopulerlik)) return false;
            if (bulunamadi != null && bulunamadi != c.bulunamadi) return false;
            if (agirlikDevrilmesi != null && agirlikDevrilmesi != c.agirlikDevrilmesi) return false;
            if (duplikasyon != null && duplikasyon != c.duplikasyon) return false;
            if (regresyon != null && regresyon != c.regresyon) return false;
            if (ustteperformerVar != null && ustteperformerVar != c.ustteperformerVar) return false;
            if (ilkSonucSorguylaAyni != null && ilkSonucSorguylaAyni != c.ilkSonucSorguylaAyni) return false;
            if (apiHatasi != null && apiHatasi != c.apiHatasi) return false;
            if (sanatciVaryantEnAz >= 0 && c.sanatciVaryantSlot < sanatciVaryantEnAz) return false;
            if (populerlikKarsilastirmasi != null
                    && !populerlikKarsilastirmasi.equalsIgnoreCase(c.populerlikKarsilastirmasi)) return false;
            if (karsilastirmaAyniIndex != null && karsilastirmaAyniIndex != c.karsilastirmaAyniIndex) return false;
            if (tekIndexBaskini != null && tekIndexBaskini != c.tekIndexBaskini) return false;
            if (sorguKelimeEnAz >= 0 && c.sorguKelimeSayisi < sorguKelimeEnAz) return false;
            if (sorguKelimeEnCok >= 0 && c.sorguKelimeSayisi > sorguKelimeEnCok) return false;
            if (sorguUzunlukEnCok >= 0 && c.sorgu.trim().length() > sorguUzunlukEnCok) return false;
            if (beklenenSiraEnAz >= 0 && (c.beklenenSira < 0 || c.beklenenSira < beklenenSiraEnAz)) return false;
            if (beklenenSiraEnCok >= 0 && (c.beklenenSira < 0 || c.beklenenSira > beklenenSiraEnCok)) return false;
            if (birebirEslesmeEnAz >= 0 && c.birebirBaslikEslesmesi < birebirEslesmeEnAz) return false;
            if (populerlikEksikEnAz >= 0 && c.populerlikEksik < populerlikEksikEnAz) return false;
            if (indexCesidiEnAz >= 0 && c.indexCesidi < indexCesidiEnAz) return false;
            return true;
        }

        private static String req(JsonNode n, String f) {
            String v = n.path(f).asText("").trim();
            if (v.isEmpty()) throw new IllegalArgumentException(f + " zorunlu");
            return v;
        }

        private static String txt(JsonNode c, String f) {
            if (!c.has(f) || c.path(f).isNull()) return null;
            String v = c.path(f).asText("").trim();
            return v.isEmpty() ? null : v;
        }

        private static Boolean bool(JsonNode c, String f) {
            if (!c.has(f) || c.path(f).isNull()) return null;
            return c.path(f).asBoolean();
        }
    }
}
