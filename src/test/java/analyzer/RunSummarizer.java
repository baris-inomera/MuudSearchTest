package analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KATMAN 2 — kosu basina TEK bir guclu model cagrisi.
 *
 * Satir bazli analiz, "tum performer'larda populerlik 0" veya "27 case synonym ile
 * kapatilmis" gibi KOSU GENELI oruntuleri goremez; onlari ancak butun tabloya bakan
 * bir cagri yakalar. Maliyet: kosu basina 1 istek.
 */
public final class RunSummarizer {

    private RunSummarizer() {}

    private static final String SYSTEM = """
        Sen MUUD arama motorunun test sonuclarini degerlendiren bir kalite muhendisisin.
        Sana bir kosunun TUM basarisiz satirlarinin ozeti veriliyor. Gorevin tek tek
        satirlari tekrar yorumlamak DEGIL; satirlarin ustunde duran ORUNTULERI bulmak.

        Ozellikle su tur bulgulari ara:
        - Ayni mekanizmadan kaynaklanan case gruplari (tek duzeltmeyle kapanacak olanlar)
        - Bir index'in tamamini etkileyen veri/konfig problemleri
        - Onceki kosuya gore regresyonlar
        - Basari oranini yaniltici sekilde yukselten/dusuren durumlar
        - Test tarafinin kendi problemleri (hatali beklenti, ortamda olmayan icerik)

        Sonuclari etki buyuklugune gore sirala. Somut ol: hangi index, hangi alan, hangi ayar.
        Turkce yaz. Sadece verilen verilere dayan, sayi uydurma.
        """;

    public static String summarize(RunSignals signals,
                                   Map<String, BulguAnalysis> results,
                                   List<ReportRow> allRows) {
        if (!AnalyzerConfig.enabled()) return "";
        try {
            StringBuilder u = new StringBuilder();
            u.append(signals.globalFactsBlock()).append('\n');

            // kok neden gruplari (satir bazli LLM'in verdigi etiketler)
            Map<String, List<String>> groups = new LinkedHashMap<>();
            for (Map.Entry<String, BulguAnalysis> e : results.entrySet()) {
                String k = e.getValue().kokNeden();
                if (k == null || k.isBlank()) k = "(etiketsiz)";
                groups.computeIfAbsent(k, x -> new java.util.ArrayList<>()).add(e.getKey());
            }
            u.append("SATIR BAZLI ANALIZDEN CIKAN KOK NEDEN ETIKETLERI\n");
            groups.entrySet().stream()
                    .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                    .forEach(e -> u.append("- ").append(e.getKey())
                            .append(" (").append(e.getValue().size()).append(" case): ")
                            .append(String.join(", ", e.getValue())).append('\n'));

            u.append("\nNOK SATIRLARI\n");
            for (ReportRow r : allRows) {
                if (!r.isNok()) continue;
                BulguAnalysis a = results.get(r.caseId);
                u.append("- ").append(r.caseId).append(" [").append(r.type).append("] ")
                 .append(r.testName).append(" -> ").append(r.headline());
                if (a != null) {
                    u.append("  {bulgu=").append(a.bulguText())
                     .append(", kategori=").append(a.kategori())
                     .append(", kok=").append(a.kokNeden()).append("}");
                }
                u.append('\n');
            }

            ObjectNode schema = schema();
            JsonNode out = new ClaudeClient().structured(
                    AnalyzerConfig.runModel(), SYSTEM, u.toString(),
                    "kosu_ozeti", "Kosu geneli oruntu analizi.", schema, 3000);

            return render(out);

        } catch (Exception e) {
            BulguAnalyzer.log("Kosu ozeti uretilemedi: " + e.getMessage());
            return "";
        }
    }

    private static ObjectNode schema() {
        var M = ClaudeClient.mapper();

        ObjectNode grpProps = M.createObjectNode();
        grpProps.set("baslik", str(M, "Kok nedenin kisa adi."));
        grpProps.set("aciklama", str(M, "Mekanizmanin aciklamasi ve dayanagi. Somut sayi/alan adi kullan."));
        grpProps.set("caseIdler", arr(M, "Bu kok nedene ait case ID'ler."));
        grpProps.set("aksiyon", str(M, "Onerilen teknik aksiyon."));
        ObjectNode onc = M.createObjectNode();
        onc.put("type", "string");
        ArrayNode oe = M.createArrayNode(); oe.add("Yuksek"); oe.add("Orta"); oe.add("Dusuk");
        onc.set("enum", oe);
        grpProps.set("oncelik", onc);

        ObjectNode grp = M.createObjectNode();
        grp.put("type", "object");
        grp.set("properties", grpProps);
        ArrayNode gr = M.createArrayNode();
        gr.add("baslik"); gr.add("aciklama"); gr.add("caseIdler"); gr.add("aksiyon"); gr.add("oncelik");
        grp.set("required", gr);

        ObjectNode groups = M.createObjectNode();
        groups.put("type", "array");
        groups.set("items", grp);
        groups.put("description", "Kok neden gruplari, etki buyuklugune gore sirali.");

        ObjectNode props = M.createObjectNode();
        props.set("ozet", str(M, "Kosunun 3-5 cumlelik degerlendirmesi."));
        props.set("kokNedenler", groups);
        props.set("oncelikliAksiyonlar", arr(M, "En cok kazandiracak 3 aksiyon, sirali."));
        props.set("dikkatEdilecekler", arr(M, "Basari oranini yaniltabilecek durumlar, regresyonlar, test borclari."));

        ObjectNode schema = M.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", props);
        ArrayNode req = M.createArrayNode();
        req.add("ozet"); req.add("kokNedenler"); req.add("oncelikliAksiyonlar");
        schema.set("required", req);
        return schema;
    }

    private static ObjectNode str(com.fasterxml.jackson.databind.ObjectMapper M, String d) {
        ObjectNode n = M.createObjectNode();
        n.put("type", "string");
        n.put("description", d);
        return n;
    }

    private static ObjectNode arr(com.fasterxml.jackson.databind.ObjectMapper M, String d) {
        ObjectNode items = M.createObjectNode();
        items.put("type", "string");
        ObjectNode n = M.createObjectNode();
        n.put("type", "array");
        n.set("items", items);
        n.put("description", d);
        return n;
    }

    private static String render(JsonNode o) {
        StringBuilder sb = new StringBuilder();
        sb.append("OZET\n").append(o.path("ozet").asText("")).append("\n\n");

        sb.append("KOK NEDENLER\n");
        for (JsonNode g : o.path("kokNedenler")) {
            sb.append("• [").append(g.path("oncelik").asText("")).append("] ")
              .append(g.path("baslik").asText("")).append('\n');
            sb.append("  ").append(g.path("aciklama").asText("")).append('\n');
            List<String> ids = new java.util.ArrayList<>();
            g.path("caseIdler").forEach(x -> ids.add(x.asText()));
            sb.append("  Case'ler: ").append(String.join(", ", ids)).append('\n');
            sb.append("  Aksiyon: ").append(g.path("aksiyon").asText("")).append("\n\n");
        }

        sb.append("ONCELIKLI AKSIYONLAR\n");
        int i = 1;
        for (JsonNode a : o.path("oncelikliAksiyonlar")) {
            sb.append(i++).append(". ").append(a.asText()).append('\n');
        }

        if (o.has("dikkatEdilecekler") && o.path("dikkatEdilecekler").size() > 0) {
            sb.append("\nDIKKAT\n");
            for (JsonNode a : o.path("dikkatEdilecekler")) sb.append("• ").append(a.asText()).append('\n');
        }
        return sb.toString();
    }
}
