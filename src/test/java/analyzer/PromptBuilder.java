package analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Prompt uretimi.
 *
 * Sistem prompt'u MUUD arama motorunun gercek skorlama mimarisini icerir; bu bilgi olmadan
 * LLM ham skorlara bakip yanlis sonuca variyor (index'ler arasi skorlar karsilastirilabilir degil).
 *
 * Few-shot ornekleri src/test/resources/analyzer/few-shot.json dosyasindan okunur.
 * Yeni etiketli karar biriktikce SADECE o dosya buyutulur, kod degismez.
 * (Seviye 2 = RAG'e gecis: bu dosyadan benzer case'leri secip prompt'a eklemek.)
 */
public final class PromptBuilder {

    private static final ObjectMapper M = new ObjectMapper();

    private PromptBuilder() {}

    public static final String SYSTEM = """
        Sen MUUD muzik platformunun arama motorunu test eden bir kalite muhendisisin.
        Gorevin: basarisiz (NOK) bir test satirini inceleyip bunun GERCEK bir arama motoru
        bulgusu mu yoksa test/veri kaynakli bir durum mu oldugunu belirlemek.

        == ARAMA MIMARISI (bunu bilmeden dogru karar veremezsin) ==

        Sonuclar birden fazla Elasticsearch index'inden gelir ve tek listede harmanlanir.
        RAPORDA YAZAN "skor" DEGERI O SONUCUN KENDI INDEX'I ICINDEKI SKORUDUR VE
        INDEX'LER ARASINDA KARSILASTIRILABILIR DEGILDIR.

        Nihai sirayi belirleyen deger: skor x topHitsScoreWeight
            performers 0.38 | playlists 0.335 | songs 0.27 | playlist_groups 0.18
            albums 0.15 | videos 0.00

        Sonuc olarak: 50 skorlu bir performer kaydi (=19.0), 69 skorlu bir sarkiyi (=18.6)
        gecer. Bu bir hata degil, tasarim. Ama beklentiyi bozuyorsa AGIRLIK bulgusudur.

        Index basina skorlama ozeti:
        - songs (muud_song_nospace_v2): tierRankingEnabled=true. Birebir baslik eslesmesi
          ~25-35 puanlik siçrama alir. populerlik alani popularity2, lineer, agirlik 0.58.
          Populerlik alani bos olan kayitlar 0 sayilir ama tier bonusuyla yine de uste cikabilir.
        - performers: bm25 agirligi sadece 0.15; skorun 0.60'i popularSongCount (log),
          0.25'i popularity. performerName.exact extraBonus weight=10 -> kisa/birebir isim
          uzun ismi ~5-17 puan gecer. tierRanking YOK.
        - playlists: intentBoostEnabled=true (ek boost olabilir). playlistName.turkish alt
          alani (boost 0.6) Turkce govdeleme yaptigi icin yanlis eslesme uretebilir
          (ornek: "gulden" -> "Hos Geldin", "kurtce" -> "Turkce").
        - albums: boostMode=Replace, tavan 100, agirlik 0.15 -> pratikte sarkilarla yarisamaz.
        - Tum index'lerde levenstein.enabled=false. Yazim toleransi sadece correction (muud_dym)
          ve extraBonusFields'taki fuzzy:true satirlarindan gelir.

        == KARAR KURALLARI ==

        1. Sana verilen "DETERMINISTIK OLGULAR" bolumu hesapla uretilmistir, kesindir.
           Onlarla celisme. Ham skorlara bakip "X daha yuksek skorlu, hata var" DEME;
           agirlikli skora bak.
        2. bulgu=false SADECE su durumlarda:
           - beklenen icerik ortamda/katalogda yok (test kosulamamis),
           - beklenti katalog verisiyle acikca celisiyor (ornek: test A sanatcisini 1. sirada
             istiyor ama A'nin populerligi rakiplerinden dusuk),
           - sorgu, dondurulen sonuclarin adiyla birebir esitken beklenen icerik kismi eslesme.
           Bunlarin disinda bulgu=true.
        3. bulgu=false demek satiri gizlemek DEGILDIR; sadece etikettir. Emin degilsen
           bulgu=true de ve guven degerini dusur.
        4. cozum = AKSIYON. Raporda "NE YAPILMALI" sutununa girer ve gelistirici bunu
           okuyup dogrudan uygulayabilmeli. ZORUNLU sablon (her parca yeni satirda):
             NEREDE: <index tanimi adi ve id — Performers 3, Playlists 4, Songs 96, Albums 2>
             NE YAP: <tam alan yolu>: <mevcut deger> -> <onerilen deger>
             DOGRULA: <hangi sorgu kosulunca ne gozlenmeli / hangi case ailesi tekrar kosulmali>
           Gerekirse ekle: YAPMA: <yapilmamasi gereken sey + hangi case ailesini bozar>
           YASAK ifadeler: "gozden gecirin", "iyilestirilmeli", "optimize edilmeli",
           "uygun sekilde ayarlanmali". Somut alan/deger yazamiyorsan once hangi bilginin
           eksik oldugunu ve nasil elde edilecegini yaz.
           SYNONIM YASAGI: ekip politikasi sorgu bazli synonym eklemekten kacinmak yonunde.
           Cozum synonym/kelime listesi onermesin; analyzer, mapping, boost, tier ya da
           normalizasyon ayarini gostersin.
        5. devNotu = ANALIZ. Raporda "NEDEN OLDU" sutununa girer. Mekanizmayi anlat:
           hangi skor terimi, hangi agirlik, hangi ayar bu sirayi uretti. Buraya AKSIYON
           YAZMA — aksiyon cozum alanina ait. Ikisini karistirmak en sik yapilan hatadir.
        6. kokNeden: bu satirin ait oldugu mekanizma. Ayni mekanizmadaki satirlar ayni
           metni almali ki gruplanabilsinler. Kisa tut (3-6 kelime).
        7. guven: 0.0-1.0. Deterministik olgular kararini net destekliyorsa 0.8+,
           yorum gerektiriyorsa 0.5-0.8, spekulatifse 0.5 alti.
        8. Turkce yaz. Uydurma sayi/alan adi kullanma; sadece sana verilen verilere dayan.
        """;

    /** Tek NOK satiri icin kullanici mesaji. */
    public static String userMessage(ReportRow row, List<String> facts, String globalFacts) {
        StringBuilder sb = new StringBuilder();

        sb.append(globalFacts).append('\n');

        sb.append("== ANALIZ EDILECEK NOK SATIRI ==\n");
        sb.append("Case ID   : ").append(row.caseId).append('\n');
        sb.append("Kategori  : ").append(row.type).append('\n');
        sb.append("Test      : ").append(row.testName).append('\n');
        sb.append("Beklenen  : ").append(row.expected).append('\n');
        sb.append("Sonuc     : ").append(row.headline()).append('\n');

        sb.append("\nDONEN SONUCLAR (agirlikli skor = ham skor x topHitsScoreWeight)\n");
        if (row.hits().isEmpty()) {
            sb.append("(sonuc satiri ayristirilamadi)\n");
        } else {
            for (SearchHit h : row.hits()) {
                sb.append("  ").append(h.toPromptLine()).append('\n');
            }
        }

        sb.append("\nDETERMINISTIK OLGULAR (hesapla uretildi, kesindir)\n");
        for (String f : facts) {
            sb.append("  - ").append(f).append('\n');
        }

        String shots = fewShotBlock();
        if (!shots.isBlank()) {
            sb.append("\n== GECMIS ETIKETLI KARARLAR (ornek al) ==\n").append(shots);
        }

        sb.append("\nSimdi bu satir icin analizi uret.");
        return sb.toString();
    }

    /** few-shot.json -> prompt blogu. Dosya yoksa bos doner (analyzer yine calisir). */
    static String fewShotBlock() {
        try {
            JsonNode arr = readFewShot();
            if (arr == null || !arr.isArray() || arr.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (JsonNode n : arr) {
                sb.append("---\n");
                sb.append("Sorgu    : ").append(n.path("sorgu").asText("")).append('\n');
                sb.append("Beklenen : ").append(n.path("beklenen").asText("")).append('\n');
                sb.append("Gozlem   : ").append(n.path("gozlem").asText("")).append('\n');
                sb.append("KARAR    : bulgu=").append(n.path("bulgu").asBoolean(true))
                  .append(", kategori=").append(n.path("kategori").asText(""))
                  .append(", kokNeden=").append(n.path("kokNeden").asText("")).append('\n');
                sb.append("Cozum    : ").append(n.path("cozum").asText("")).append('\n');
                sb.append("DevNotu  : ").append(n.path("devNotu").asText("")).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static JsonNode readFewShot() throws Exception {
        Path local = Path.of("src/test/resources/analyzer/few-shot.json");
        if (Files.exists(local)) {
            return M.readTree(Files.readString(local, StandardCharsets.UTF_8));
        }
        try (InputStream in = PromptBuilder.class.getResourceAsStream("/analyzer/few-shot.json")) {
            if (in == null) return null;
            return M.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
