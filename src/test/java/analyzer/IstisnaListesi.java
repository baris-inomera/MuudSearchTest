package analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BILINEN ISTISNALAR — ekibin elle verdigi kalici kararlar.
 *
 * Kural motoru mekanizmayi teshis edebilir ama YARGI veremez: "beklenti hatali mi?",
 * "icerik ortamda var mi?" gibi sorular insan karari gerektirir. Bir kez karar verilen
 * case'i her kosuda yeniden tartismamak icin karar buraya yazilir.
 *
 * Dosya: src/test/resources/analyzer/bilinen-istisnalar.json
 * Bu liste TUM kurallardan ONCE bakilir ve kural motorunu ezer.
 *
 * durum degerleri:
 *   BLOCKED        Icerik ortamda yok / case kosulamiyor. Bulgu degil.
 *   YANLIS_BEKLENTI Test beklentisi hatali. Bulgu degil.
 *   KABUL_EDILDI   Bulgu gercek ama bilincli kabul edildi (su an duzeltilmeyecek).
 *
 * aktif=false olan kayitlar YOK SAYILIR — onay bekleyen oneriler boyle tutulur.
 */
public final class IstisnaListesi {

    private static final ObjectMapper M = new ObjectMapper();

    private final Map<String, Istisna> map = new LinkedHashMap<>();

    public IstisnaListesi() {
        yukle();
    }

    public int aktifSayisi() {
        return map.size();
    }

    /** Bu case icin kalici bir karar var mi? Yoksa null. */
    public BulguAnalysis bak(String caseId, String kanit) {
        Istisna i = map.get(caseId);
        if (i == null) return null;

        boolean bulgu = "KABUL_EDILDI".equals(i.durum);

        // "NE YAPILMALI" sutunu: istisnalarda motor tarafinda aksiyon yoktur,
        // o yuzden bunu acikca yaz — bos ya da analiz metni birakma.
        String cozum = ("KABUL_EDILDI".equals(i.durum)
                ? "AKSIYON BEKLETILIYOR — bulgu gercek ama ekip bilincli olarak kabul etti.\n"
                : "MOTOR TARAFINDA AKSIYON YOK — bu case icin kalici ekip karari var.\n")
                + "DURUM: " + i.durum + "\n"
                + "KARAR: " + i.karar + "\n"
                + "NE ZAMAN YENIDEN ACILIR: "
                + (i.gecerlilik == null || i.gecerlilik.isBlank()
                        ? "Belirtilmemis — bilinen-istisnalar.json icinde 'gecerlilik' alanini doldurun."
                        : i.gecerlilik);

        String dev = (i.aciklama == null || i.aciklama.isBlank()
                ? "Aciklama girilmemis."
                : i.aciklama)
                + " (Kaynak: bilinen-istisnalar.json — bu satir tum kurallardan once degerlendirildi.)";

        return new BulguAnalysis(caseId, bulgu, i.kategori, cozum, dev,
                1.0, "bilinen istisna", "ISTISNA:" + i.durum, kanit, false);
    }

    /** Ozet sayfasinda "bu case'ler basari oranindan dusulmeli" uyarisi icin. */
    public boolean bloke(String caseId) {
        Istisna i = map.get(caseId);
        return i != null && !"KABUL_EDILDI".equals(i.durum);
    }

    private void yukle() {
        try {
            JsonNode arr = oku();
            if (arr == null || !arr.isArray()) return;
            for (JsonNode n : arr) {
                if (!n.path("aktif").asBoolean(true)) continue;      // onay bekleyen oneriler
                String id = n.path("caseId").asText("").trim();
                if (id.isEmpty()) continue;
                Istisna i = new Istisna();
                i.durum = n.path("durum").asText("BLOCKED").trim().toUpperCase();
                i.kategori = n.path("kategori").asText("YANLIS_BEKLENTI").trim().toUpperCase();
                if (!BulguAnalysis.KATEGORILER.contains(i.kategori)) i.kategori = "YANLIS_BEKLENTI";
                i.aciklama = n.path("aciklama").asText("");
                i.karar = n.path("karar").asText("karar sahibi belirtilmemis");
                i.gecerlilik = n.path("gecerlilik").asText("");
                map.put(id, i);
            }
        } catch (Exception e) {
            BulguAnalyzer.log("bilinen-istisnalar.json okunamadi: " + e.getMessage());
        }
    }

    private static JsonNode oku() throws Exception {
        Path local = Path.of("src/test/resources/analyzer/bilinen-istisnalar.json");
        if (Files.exists(local)) {
            return M.readTree(Files.readString(local, StandardCharsets.UTF_8));
        }
        try (InputStream in = IstisnaListesi.class
                .getResourceAsStream("/analyzer/bilinen-istisnalar.json")) {
            if (in == null) return null;
            return M.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static final class Istisna {
        String durum, kategori, aciklama, karar, gecerlilik;
    }
}
