package analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * VERI KALITESI DENETIMI — B1 (ad alanlari bozuk) ve B2 (popularity olcegi) bulgularini
 * HAM RESPONSE'LARDAN yeniden uretir ve her iddia icin kaynak dosyayi yazar.
 *
 * NEDEN VAR: PROD bulgular raporundaki veri bulgulari "yapay zeka soyledi" degil, olculmus
 * sayilardir. Bu sinif o olcumu tekrarlanabilir hale getirir — developer/ETL ekibi kendisi
 * kosup ayni sayilari gorebilir, hatta tek tek kaynak JSON dosyasini acip dogrulayabilir.
 *
 * GIRDI : reports/api-responses/{uat,prod}/*.json
 *         ResponseDumper'in topladigi, API'nin dondugu HAM cevaplar. Elle duzenlenmedi.
 * YONTEM: Hicbir sey tahmin edilmiyor; her sayi bu dosyalar uzerinde sayiliyor.
 *         Tekrar kosuldugunda ayni sonucu verir.
 *
 * CIKTI :
 *   dogrulama/KANIT_B1_B2.txt                     — metin kanit, her satirda kaynak dosya
 *   reports/veri-kalitesi/VeriKalitesi_<ts>.xlsx  — filtrelenebilir bulgu listesi
 *
 * NEDEN MainCases ICINDE DEGIL: B1.1 iki ortamin ayni id'lerini karsilastirir, yani hem
 * uat/ hem prod/ dump'i hazir olmali. Tek ortam kosumunun sonunda otomatik calissa yarim
 * cevap uretirdi. Bu yuzden ResponseDumper gibi bagimsiz main olarak calisir.
 *
 * KULLANIM (IntelliJ'de main olarak calistir, ya da):
 *   mvn -q test-compile exec:java -Dexec.classpathScope=test ^
 *       -Dexec.mainClass=analyzer.VeriKalitesiDenetimi
 *
 * Opsiyonel arguman: response klasoru (varsayilan reports/api-responses).
 *
 * CAPRAZ DOGRULAMA: dogrulama/kanit_uret.py ayni olcumu bagimsiz olarak yapar. Ikisi ayni
 * response klasorunde kosuldugunda ayni sayilari uretir — biri digerinin kontrolu.
 *
 * NOT: JSON dosya adlarinda Turkce karakter var. JVM'in dosya adi kod cozumu (sun.jnu.encoding)
 * UTF-8 degilse rapordaki 'kaynak:' satirlarinda soru isareti gorunur — sayilar etkilenmez.
 * Windows + JDK 17'de sorun cikmaz; cikarsa -Dsun.jnu.encoding=UTF-8 ile kosun.
 */
public final class VeriKalitesiDenetimi {

    private VeriKalitesiDenetimi() { }

    private static final String[] ORTAMLAR   = {"uat", "prod"};
    private static final String[] INDEXLER   = {"songs", "performers", "albums", "playlists", "videos"};
    private static final String[] AD_ALANLARI = {"performerName", "songName", "albumName", "playlistName"};
    private static final String[] POP_ALANLARI = {"popularity", "prodPopularity", "popularity2", "performerPopularity"};

    /** Her index'in KENDI ad alani — varlik bazli sayimda kullanilir. */
    private static final Map<String, String> KENDI_ALANI = Map.of(
            "songs", "songName",
            "performers", "performerName",
            "albums", "albumName",
            "playlists", "playlistName");

    private static final String DIAKRITIKLER = "çğıöşüÇĞİÖŞÜéèêáàâäñôûù";

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ── Okunan ham veri ─────────────────────────────────────────────────────────

    /** Bir ad alani okumasi. */
    private record Ad(String index, String alan, String id, String deger, String dosya) { }

    /** Excel'e yazilacak tek bulgu satiri. */
    private record Bulgu(String kod, String ortam, String index, String alan,
                         String id, String deger, String sorun, String dosya) { }

    /** ortam -> id -> performerName (yalnizca performers index'inin kendi kaydi) */
    private static final Map<String, Map<String, String>> ADLAR = yeniOrtamHaritasi();
    /** ortam -> id -> bu id'nin ilk gorulldugu dosya */
    private static final Map<String, Map<String, String>> KAYNAK = yeniOrtamHaritasi();
    /** ortam -> tum ad alani okumalari */
    private static final Map<String, List<Ad>> AD_TUM = new LinkedHashMap<>();
    /** "ortam|alan|index" -> id -> deger */
    private static final Map<String, Map<String, Double>> POP_TUM = new LinkedHashMap<>();
    private static final Map<String, Integer> DOSYA_SAYISI = new LinkedHashMap<>();

    private static final List<Bulgu> BULGULAR = new ArrayList<>();

    private static Map<String, Map<String, String>> yeniOrtamHaritasi() {
        Map<String, Map<String, String>> m = new LinkedHashMap<>();
        for (String o : ORTAMLAR) m.put(o, new LinkedHashMap<>());
        return m;
    }

    private static PrintStream out;

    // ════════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) throws Exception {
        Path kok = Path.of(args.length > 0 ? args[0] : "reports/api-responses")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(kok)) {
            System.err.println("HATA: " + kok + " yok. Once ResponseDumper'i kosun.");
            System.exit(1);
        }

        Path txt = Path.of("dogrulama", "KANIT_B1_B2.txt").toAbsolutePath().normalize();
        Files.createDirectories(txt.getParent());

        // Butun metin once bellekte toplanir, sonra UTF-8 olarak dosyaya yazilir.
        // (Windows konsolu Turkce karakteri bozabilir; dosya her zaman dogru cikar.)
        java.io.ByteArrayOutputStream tampon = new java.io.ByteArrayOutputStream();
        out = new PrintStream(tampon, true, StandardCharsets.UTF_8);

        oku(kok);
        out.println("MUUD — B1/B2 BULGU DOGRULAMA");
        out.println("girdi klasoru: " + kok);
        for (String o : ORTAMLAR) {
            out.printf("  %-5s %d response dosyasi, %d ad alani okundu%n",
                    o, DOSYA_SAYISI.getOrDefault(o, 0), AD_TUM.get(o).size());
        }

        List<String[]> sadeceDiakritik = b11();
        b12(sadeceDiakritik);
        b13();
        b14();
        b2();

        baslik("SON", "OZET");
        out.println("Yukaridaki her sayi " + kok + " altindaki JSON dosyalarindan");
        out.println("sayilarak uretildi; hicbiri elle girilmedi. Tekrar kosuldugunda ayni sonucu verir.");
        out.println("Bir satiri kendiniz dogrulamak icin yaninda yazan kaynak dosyayi acip ilgili id'yi aratin.");
        out.flush();

        Files.write(txt, tampon.toByteArray());

        Path xlsx = excelYaz();
        System.out.println();
        System.out.println("[veri-kalitesi] metin kanit : " + txt);
        System.out.println("[veri-kalitesi] excel       : " + xlsx);
        System.out.println("[veri-kalitesi] bulgu satiri: " + BULGULAR.size());
    }

    // ── Tek gecis okuma ─────────────────────────────────────────────────────────

    private static void oku(Path kok) throws Exception {
        ObjectMapper om = new ObjectMapper();
        for (String ortam : ORTAMLAR) {
            AD_TUM.put(ortam, new ArrayList<>());
            Path dizin = kok.resolve(ortam);
            if (!Files.isDirectory(dizin)) {
                out.println("  ! klasor yok: " + dizin);
                continue;
            }
            List<Path> dosyalar;
            try (Stream<Path> s = Files.list(dizin)) {
                dosyalar = s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted().toList();
            }
            for (Path p : dosyalar) {
                String dosya = p.getFileName().toString();
                DOSYA_SAYISI.merge(ortam, 1, Integer::sum);
                JsonNode r;
                try {
                    r = om.readTree(Files.readString(p, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    out.println("  ! okunamadi: " + dosya + " (" + e.getMessage() + ")");
                    continue;
                }
                for (String index : INDEXLER) {
                    JsonNode blok = r.path(index).path("content");
                    if (!blok.isArray()) continue;
                    for (JsonNode h : blok) {
                        JsonNode d = h.path("data");
                        if (d.isMissingNode()) continue;
                        String id = d.path("id").asText(null);
                        for (String alan : AD_ALANLARI) {
                            JsonNode v = d.get(alan);
                            if (v != null && v.isTextual()) {
                                AD_TUM.get(ortam).add(new Ad(index, alan, id, v.asText(), dosya));
                            }
                        }
                        if ("performers".equals(index)) {
                            JsonNode pn = d.get("performerName");
                            if (pn != null && pn.isTextual() && !pn.asText().isEmpty()) {
                                ADLAR.get(ortam).put(id, pn.asText());
                                KAYNAK.get(ortam).putIfAbsent(id, dosya);
                            }
                        }
                        for (String alan : POP_ALANLARI) {
                            JsonNode v = d.get(alan);
                            if (v != null && v.isNumber()) {
                                POP_TUM.computeIfAbsent(ortam + "|" + alan + "|" + index,
                                        k -> new LinkedHashMap<>()).put(id, v.asDouble());
                            }
                        }
                    }
                }
            }
        }
    }

    // ── B1.1  ayni id, farkli ad ────────────────────────────────────────────────

    /** @return farki yalnizca diakritik olan ciftler: {id, uatAd, prodAd} */
    private static List<String[]> b11() {
        baslik("B1.1", "AYNI ID, FARKLI AD — diakritik kaybi");
        out.println("Yontem: iki ortamdaki performers kayitlarinin id -> performerName eslemesi kurulur.");
        out.println("Ayni id'de ad FARKLI ve fark yalnizca diakritik/aksan ise listelenir.");
        out.println("'Mukerrer kayit degil' iddiasinin dayanagi: ID'ler AYNI.");

        Map<String, String> u = ADLAR.get("uat");
        Map<String, String> p = ADLAR.get("prod");
        List<String> ortak = u.keySet().stream().filter(p::containsKey).sorted().toList();

        List<String[]> farkli = new ArrayList<>();
        for (String id : ortak) {
            if (!u.get(id).equals(p.get(id))) farkli.add(new String[]{id, u.get(id), p.get(id)});
        }
        List<String[]> sadece = farkli.stream()
                .filter(t -> fold(t[1]).equals(fold(t[2]))).toList();
        List<String[]> kayip = sadece.stream()
                .filter(t -> diaSay(t[1]) > diaSay(t[2])).toList();
        List<String[]> tersi = sadece.stream()
                .filter(t -> diaSay(t[1]) < diaSay(t[2])).toList();

        out.println();
        out.println("SAYILAR");
        out.printf("  iki ortamda da gorulen performer id      : %d%n", ortak.size());
        out.printf("  ayni id'de ad farkli                     : %d%n", farkli.size());
        out.printf("  farki YALNIZCA diakritik olan            : %d%n", sadece.size());
        out.printf("  UAT'ta diakritik VAR, PROD'da YOK        : %d%n", kayip.size());
        out.printf("  ters yon (PROD'da var, UAT'ta yok)       : %d%n", tersi.size());

        // Iki farkli durum karistirilmamali:
        //   (a) GERCEK diakritik kaybi — PROD'da harf duz yazilmis (Çelik -> celik)
        //   (b) SADECE NFC/NFD form farki — gozle ayni, byte farkli (bkz. B1.2)
        List<String[]> gercek = new ArrayList<>(), nfdSadece = new ArrayList<>();
        for (String[] t : kayip) {
            if (nfd(t[1]).equals(nfd(t[2]))) nfdSadece.add(t); else gercek.add(t);
        }
        out.println();
        out.printf("  bunlardan GERCEK diakritik kaybi         : %d%n", gercek.size());
        out.printf("  bunlardan yalnizca NFC/NFD form farki    : %d  (gozle ayni gorunur, bkz. B1.2)%n",
                nfdSadece.size());

        out.println();
        out.println("LISTE A — GERCEK diakritik kaybi (PROD'da harf duz yazilmis)");
        gercek.stream().sorted(Comparator.comparing(t -> t[1])).forEach(t -> {
            String kaynak = KAYNAK.get("prod").getOrDefault(t[0], "-");
            out.printf("  id=%-9s UAT='%s'%s PROD='%s'%s kaynak: prod/%s%n",
                    t[0], t[1], bosluk(t[1], 30), t[2], bosluk(t[2], 30), kaynak);
            BULGULAR.add(new Bulgu("B1.1", "prod", "performers", "performerName", t[0], t[2],
                    "UAT'ta '" + t[1] + "' — diakritik kaybi", kaynak));
        });

        out.println();
        out.println("LISTE B — yalnizca NFC/NFD form farki (yazim ayni)");
        nfdSadece.stream().sorted(Comparator.comparing(t -> t[1])).forEach(t ->
                out.printf("  id=%-9s UAT uzunluk=%-3d PROD uzunluk=%-3d  '%s'  kaynak: prod/%s%n",
                        t[0], t[1].length(), t[2].length(), t[1],
                        KAYNAK.get("prod").getOrDefault(t[0], "-")));

        out.println();
        out.println("TERS YON — PROD'da diakritik var, UAT'ta yok");
        tersi.stream().sorted(Comparator.comparing(t -> t[2])).forEach(t -> {
            String kaynak = KAYNAK.get("uat").getOrDefault(t[0], "-");
            out.printf("  id=%-9s UAT='%s'%s PROD='%s'%s kaynak: uat/%s%n",
                    t[0], t[1], bosluk(t[1], 30), t[2], bosluk(t[2], 30), kaynak);
            BULGULAR.add(new Bulgu("B1.1-ters", "uat", "performers", "performerName", t[0], t[1],
                    "PROD'da '" + t[2] + "' — diakritik kaybi UAT tarafinda", kaynak));
        });

        return sadece;
    }

    // ── B1.2  unicode normalizasyon formu ──────────────────────────────────────

    private static void b12(List<String[]> sadeceDiakritik) {
        baslik("B1.2", "UNICODE NORMALIZASYON FORMU — gozle ayni, byte farkli");
        out.println("Yontem: her ad alani NFC kontrolunden gecirilir. Metin NFD ise NFC'ye esit olmaz.");
        out.println("Ornek: 'g' + U+0306 (birlesik olmayan) yerine tek kod noktasi U+011F ('ğ') beklenir.");

        // IKI FARKLI SAYIM — karistirilmamali:
        //  (1) VARLIK BAZLI: her index'in KENDI ad alani. Bir katalog varligi bir kez sayilir.
        //      RAPORDAKI SAYI BUDUR.
        //  (2) KAYIT BAZLI: ad alani nerede gorunuyorsa. performerName sarki/album
        //      kayitlarinda da bulunur, ayni sanatci onlarca kez sayilir. Ayni seyi olcmez.
        Map<String, Map<String, Map<String, Ad>>> varlik = new LinkedHashMap<>();
        Map<String, Map<String, Set<String>>> kayit = new LinkedHashMap<>();
        for (String ortam : ORTAMLAR) {
            varlik.put(ortam, new LinkedHashMap<>());
            kayit.put(ortam, new LinkedHashMap<>());
            for (String alan : AD_ALANLARI) {
                varlik.get(ortam).put(alan, new LinkedHashMap<>());
                kayit.get(ortam).put(alan, new LinkedHashSet<>());
            }
            for (Ad a : AD_TUM.get(ortam)) {
                if (a.deger().isEmpty() || Normalizer.isNormalized(a.deger(), Normalizer.Form.NFC)) continue;
                kayit.get(ortam).get(a.alan()).add(a.index() + "|" + a.id());
                if (a.alan().equals(KENDI_ALANI.get(a.index()))) {
                    varlik.get(ortam).get(a.alan()).putIfAbsent(a.id(), a);
                }
            }
        }

        out.println();
        out.println("SAYIM 1 — VARLIK BAZLI (index'in kendi ad alani) — RAPORDAKI SAYI");
        for (String ortam : ORTAMLAR) {
            int tot = 0;
            for (String alan : AD_ALANLARI) {
                int n = varlik.get(ortam).get(alan).size();
                if (n > 0) out.printf("  %-5s %-14s %d%n", ortam, alan, n);
                tot += n;
            }
            out.printf("  %-5s %-14s %d%n", ortam, "TOPLAM", tot);
        }

        out.println();
        out.println("SAYIM 2 — KAYIT BAZLI (ad alani nerede gorunuyorsa)");
        out.println("  Not: performerName sarki/album kayitlarinda da bulunur; ayni sanatci");
        out.println("       birden fazla kez sayilir. Rapordaki sayi bu DEGIL.");
        for (String ortam : ORTAMLAR) {
            int tot = 0;
            for (String alan : AD_ALANLARI) {
                int n = kayit.get(ortam).get(alan).size();
                if (n > 0) out.printf("  %-5s %-14s %d%n", ortam, alan, n);
                tot += n;
            }
            out.printf("  %-5s %-14s %d%n", ortam, "TOPLAM", tot);
        }

        // Ayni id iki ortamda: kod noktasi karsilastirmasi
        out.println();
        out.println("ORNEK — ayni id, iki ortamda kod noktasi karsilastirmasi");
        String secili = null;
        for (String[] t : sadeceDiakritik) {
            if (!Normalizer.isNormalized(t[1], Normalizer.Form.NFC)
                    || !Normalizer.isNormalized(t[2], Normalizer.Form.NFC)) { secili = t[0]; break; }
        }
        if (secili != null) {
            for (String ortam : ORTAMLAR) {
                String ad = ADLAR.get(ortam).get(secili);
                out.printf("  %-5s id=%-9s '%s'%n", ortam, secili, ad);
                out.printf("        uzunluk=%d  kod noktalari=%s%n", ad.length(), kodNoktalari(ad, 99));
                out.printf("        kaynak: %s/%s%n", ortam, KAYNAK.get(ortam).getOrDefault(secili, "-"));
            }
        }

        out.println();
        out.println("DIGER NFD ORNEKLERI (prod) — ilk 6");
        int c = 0;
        for (String alan : AD_ALANLARI) {
            for (Ad a : varlik.get("prod").get(alan).values()) {
                if (c >= 6) break;
                out.printf("  %-14s id=%-9s '%s'%n", alan, a.id(), a.deger());
                out.printf("        var olan  =%s%n", kodNoktalari(a.deger(), 14));
                out.printf("        NFC hali  =%s%n",
                        kodNoktalari(Normalizer.normalize(a.deger(), Normalizer.Form.NFC), 14));
                out.printf("        kaynak: prod/%s%n", a.dosya());
                c++;
            }
        }

        // Excel: butun NFD varliklari
        for (String ortam : ORTAMLAR) {
            for (String alan : AD_ALANLARI) {
                for (Ad a : varlik.get(ortam).get(alan).values()) {
                    BULGULAR.add(new Bulgu("B1.2", ortam, a.index(), alan, a.id(), a.deger(),
                            "NFD formunda — exact-match alanlarinda eslesmez", a.dosya()));
                }
            }
        }
    }

    // ── B1.3  karakter kaybi ve gorunmez karakterler ───────────────────────────

    private static void b13() {
        baslik("B1.3", "KARAKTER KAYBI (?) ve GORUNMEZ KARAKTERLER");
        out.println("Yontem: ad alanlarinda regex taramasi; her bulgu kaynak dosyasiyla listelenir.");

        Object[][] desenler = {
                {"kelime icinde '?' — kodlanamayan harf", Pattern.compile("\\w\\?\\w", Pattern.UNICODE_CHARACTER_CLASS)},
                {"sonda TAB",                             Pattern.compile("\\t$")},
                {"sonda NBSP (U+00A0)",                   Pattern.compile("\\u00A0$")},
                {"bas veya sonda bosluk",                 Pattern.compile("^\\s|\\s$", Pattern.UNICODE_CHARACTER_CLASS)},
                {"cift bosluk",                           Pattern.compile("\\u0020{2,}")},
                {"cift-encode izi (Ã/Ä/Å)",               Pattern.compile("[\\u00C3\\u00C4\\u00C5][\\u0080-\\u00BF]")},
        };

        for (Object[] d : desenler) {
            String etiket = (String) d[0];
            Pattern rx = (Pattern) d[1];
            out.println();
            out.println("  --- " + etiket + " ---");
            for (String ortam : ORTAMLAR) {
                // Ayni ad birden fazla sorguda gorunur; deger bazinda tekilleştirilir.
                Map<String, Ad> bul = new TreeMap<>();
                for (Ad a : AD_TUM.get(ortam)) {
                    if (a.deger().isEmpty()) continue;
                    Matcher m = rx.matcher(a.deger());
                    if (m.find()) bul.putIfAbsent(a.deger(), a);
                }
                out.printf("    %-5s %d farkli ad%n", ortam, bul.size());
                int i = 0;
                for (Map.Entry<String, Ad> e : bul.entrySet()) {
                    Ad a = e.getValue();
                    if (i < 6) {
                        out.printf("       %-14s id=%-9s '%s'   (%s/%s)%n",
                                a.alan(), a.id(), gorunurYap(a.deger()), ortam, a.dosya());
                        i++;
                    }
                    BULGULAR.add(new Bulgu("B1.3", ortam, a.index(), a.alan(), a.id(),
                            gorunurYap(a.deger()), etiket, a.dosya()));
                }
            }
        }
    }

    // ── B1.4  bos ad ───────────────────────────────────────────────────────────

    private static void b14() {
        baslik("B1.4", "BOS AD");
        for (String ortam : ORTAMLAR) {
            List<Ad> bos = AD_TUM.get(ortam).stream().filter(a -> a.deger().isBlank()).toList();
            Set<String> tekil = new LinkedHashSet<>();
            for (Ad a : bos) tekil.add(a.alan() + "|" + a.id());
            out.printf("  %-5s %d kayit (tekil %d)%n", ortam, bos.size(), tekil.size());
            Set<String> yazildi = new HashSet<>();
            for (Ad a : bos) {
                String k = a.alan() + "|" + a.id();
                if (!yazildi.add(k)) continue;
                out.printf("       %-14s id=%-9s kaynak: %s/%s%n", a.alan(), a.id(), ortam, a.dosya());
                BULGULAR.add(new Bulgu("B1.4", ortam, a.index(), a.alan(), a.id(), "",
                        "ad alani tamamen bos", a.dosya()));
            }
        }
    }

    // ── B2  popularity olcegi ──────────────────────────────────────────────────

    private static void b2() {
        baslik("B2", "popularity ALANI INDEX'LER ARASINDA AYNI OLCEKTE DEGIL");
        out.println("Yontem: her (ortam, alan, index) icin TEKIL id bazinda deger toplanir; medyan ve");
        out.println("maksimum hesaplanir. 'Normalize' iddiasinin dayanagi maksimumun tam 100 olmasi;");
        out.println("'ham sayac' iddiasinin dayanagi maksimumun medyanin yuzlerce kati olmasi.");

        for (String ortam : new String[]{"prod", "uat"}) {
            out.println();
            out.println("  --- " + ortam.toUpperCase(Locale.ROOT) + " ---");
            for (String alan : POP_ALANLARI) {
                for (String index : new String[]{"songs", "videos", "performers", "albums"}) {
                    Map<String, Double> d = POP_TUM.get(ortam + "|" + alan + "|" + index);
                    if (d == null || d.isEmpty()) continue;
                    List<Double> v = new ArrayList<>(d.values());
                    Collections.sort(v);
                    double m = medyan(v);
                    double maks = v.get(v.size() - 1);
                    out.printf("    %-19s %-11s n=%-6d medyan=%-9.2f maks=%-10.2f maks/medyan=%s%n",
                            alan, index, v.size(), m, maks,
                            m != 0 ? String.format("%.0fx", maks / m) : "-");
                    BULGULAR.add(new Bulgu("B2", ortam, index, alan, "-",
                            String.format(Locale.ROOT, "medyan=%.2f maks=%.2f", m, maks),
                            m != 0 && maks / m > 50 ? "ham sayac — olcek normalize degil"
                                    : (maks == 100.0 ? "0-100 normalize" : "olcek kontrol edilmeli"),
                            "n=" + v.size() + " tekil id"));
                }
            }
        }
    }

    // ── Excel ──────────────────────────────────────────────────────────────────

    private static Path excelYaz() throws Exception {
        Path dizin = Path.of("reports", "veri-kalitesi").toAbsolutePath().normalize();
        Files.createDirectories(dizin);
        Path hedef = dizin.resolve("VeriKalitesi_" + LocalDateTime.now().format(TS) + ".xlsx");

        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle baslikStil = wb.createCellStyle();
            Font kalin = wb.createFont();
            kalin.setBold(true);
            baslikStil.setFont(kalin);
            baslikStil.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            baslikStil.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            baslikStil.setBorderBottom(BorderStyle.THIN);

            // ── Sheet: Ozet
            Sheet ozet = wb.createSheet("Ozet");
            String[] ozetBaslik = {"Bulgu", "Aciklama", "UAT", "PROD"};
            satir(ozet, 0, baslikStil, ozetBaslik);
            int r = 1;
            r = ozetSatir(ozet, r, "B1.1", "Ayni id, PROD'da diakritik kaybi",
                    say("B1.1-ters", "uat"), say("B1.1", "prod"));
            r = ozetSatir(ozet, r, "B1.2", "NFD formunda ad (exact-match'te eslesmez)",
                    say("B1.2", "uat"), say("B1.2", "prod"));
            r = ozetSatir(ozet, r, "B1.3", "Gorunmez/bozuk karakter tasiyan ad",
                    say("B1.3", "uat"), say("B1.3", "prod"));
            r = ozetSatir(ozet, r, "B1.4", "Ad alani tamamen bos",
                    say("B1.4", "uat"), say("B1.4", "prod"));
            ozetSatir(ozet, r, "B2", "Olcek olcumu (index x alan)",
                    say("B2", "uat"), say("B2", "prod"));
            for (int i = 0; i < ozetBaslik.length; i++) ozet.autoSizeColumn(i);

            // ── Sheet: Bulgular
            Sheet sh = wb.createSheet("Bulgular");
            String[] basliklar = {"No", "Bulgu", "Ortam", "Index", "Alan", "ID", "Deger", "Sorun", "Kaynak Dosya"};
            satir(sh, 0, baslikStil, basliklar);
            int no = 1;
            for (Bulgu b : BULGULAR) {
                Row row = sh.createRow(no);
                row.createCell(0).setCellValue(no);
                row.createCell(1).setCellValue(b.kod());
                row.createCell(2).setCellValue(b.ortam());
                row.createCell(3).setCellValue(b.index());
                row.createCell(4).setCellValue(b.alan());
                row.createCell(5).setCellValue(b.id());
                row.createCell(6).setCellValue(b.deger());
                row.createCell(7).setCellValue(b.sorun());
                row.createCell(8).setCellValue(b.dosya());
                no++;
            }
            if (no > 1) {
                sh.setAutoFilter(new CellRangeAddress(0, no - 1, 0, basliklar.length - 1));
            }
            int[] genislik = {6, 10, 8, 13, 16, 12, 38, 46, 44};
            for (int i = 0; i < genislik.length; i++) sh.setColumnWidth(i, genislik[i] * 256);
            // Ust satir kilitlenmez — rapor bastan sona serbest kaysin.

            try (FileOutputStream fos = new FileOutputStream(hedef.toFile())) {
                wb.write(fos);
            }
        }
        return hedef;
    }

    private static int say(String kod, String ortam) {
        return (int) BULGULAR.stream()
                .filter(b -> b.kod().equals(kod) && b.ortam().equals(ortam)).count();
    }

    private static int ozetSatir(Sheet sh, int r, String kod, String aciklama, int uat, int prod) {
        Row row = sh.createRow(r);
        row.createCell(0).setCellValue(kod);
        row.createCell(1).setCellValue(aciklama);
        row.createCell(2).setCellValue(uat);
        row.createCell(3).setCellValue(prod);
        return r + 1;
    }

    private static void satir(Sheet sh, int r, CellStyle stil, String[] degerler) {
        Row row = sh.createRow(r);
        for (int i = 0; i < degerler.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(degerler[i]);
            c.setCellStyle(stil);
        }
    }

    // ── Yardimcilar ────────────────────────────────────────────────────────────

    private static void baslik(String no, String s) {
        out.println();
        out.println("=".repeat(100));
        out.println(no + "  " + s);
        out.println("=".repeat(100));
    }

    /** Diakritikleri soyup kucuk harfe indirir — 'fark yalnizca aksan mi' testi icin. */
    private static String fold(String s) {
        String t = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            if (Character.getType(t.charAt(i)) != Character.NON_SPACING_MARK) sb.append(t.charAt(i));
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
                .replace("ı", "i").replace("İ", "i")
                .toLowerCase(Locale.ROOT);
    }

    private static String nfd(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD);
    }

    private static int diaSay(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (DIAKRITIKLER.indexOf(s.charAt(i)) >= 0) n++;
        return n;
    }

    /** Python statistics.median ile ayni: cift sayida elemanda iki ortanin ortalamasi. */
    private static double medyan(List<Double> sirali) {
        int n = sirali.size();
        if (n == 0) return 0;
        return n % 2 == 1 ? sirali.get(n / 2)
                : (sirali.get(n / 2 - 1) + sirali.get(n / 2)) / 2.0;
    }

    private static String kodNoktalari(String s, int limit) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < s.length() && i < limit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("U+%04X", (int) s.charAt(i)));
        }
        return sb.append("]").toString();
    }

    /** Gorunmez karakterleri raporda okunur hale getirir. */
    private static String gorunurYap(String s) {
        return s.replace("\t", "<TAB>")
                .replace("\u00A0", "<NBSP>")
                .replace("\u200B", "<ZWSP>");
    }

    private static String bosluk(String s, int genislik) {
        int n = genislik - s.length();
        return n > 0 ? " ".repeat(n) : " ";
    }
}
