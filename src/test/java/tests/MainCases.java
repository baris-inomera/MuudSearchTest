package tests;

import client.MuudSearchApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import report.ExcelTestReportWriter;
import report.TestResultRow;
import util.MuudSearchUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Her case için:
 * • Kural otomatik türetilir (FIRST_ARTIST_IS / TOPN_HAS_ARTIST_AND_TRACK
 * / TOPN_RELATED_PLAYLIST)
 * • Sonuç değerlendirilir: OK veya NOK
 * • NOK ise — neden başarısız / 1. sırada ne görüldü — raporlanır
 * <p>
 * Test HİÇBİR ZAMAN fail etmez — saf gözlem & kapsayıcı rapor üretir.
 * <p>
 * Kullanım:
 * mvn test -Dtest=BulguFinal2
 * Çıktı: proje kök dizininde TestReport_YYYYMMDD_HHmmss.xlsx
 * ─────────────────────────────────────────────────────────────────────────────
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MainCases extends TestConfig {

    private static final int TOP_N = 10;
    private static final Locale TR = Locale.forLanguageTag("tr");

    // ── Bölüm sabitleri ──────────────────────────────────────────────────────
    private static final String S_SARKI_TAM = "Şarkı · Tam Eşleşme";
    private static final String S_SARKI_KISMI = "Şarkı · Kısmi Ad";
    private static final String S_SARKI_SANAT = "Şarkı · Sanatçı + Şarkı";
    private static final String S_SARKI_YAZIM = "Şarkı · Yazım Toleransı";
    private static final String S_SARKI_LYRICS = "Şarkı · Lyrics";
    private static final String S_SANAT_TAM = "Sanatçı · Tam Eşleşme";
    private static final String S_SANAT_KISMI = "Sanatçı · Kısmi Ad";
    private static final String S_SANAT_YAZIM = "Sanatçı · Yazım Toleransı";
    private static final String S_SANAT_ALIAS = "Sanatçı · Kısaltma / Alias";
    private static final String S_PLAYLIST = "Playlist";

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // =========================================================================
    // SETUP / TEARDOWN
    // =========================================================================

    @BeforeAll
    static void init() {
        api = new MuudSearchApi();
        System.out.println("✅ BulguFinal2 başlatıldı — top-" + TOP_N + " değerlendirilecek.");
    }

    @AfterAll
    static void writeReport() {
        System.out.printf("%n📋 Toplam %d case işlendi.%n", ROWS.size());
        ExcelTestReportWriter.writeBulgu(ROWS);
    }

    // =========================================================================
    // CASE TANIMLAMASI — BulguSnapshotV2Detail'den entegre edildi
    // =========================================================================

    record BulguCase(String caseId, String term, String expArtist, String expTrack, String section, int topN) {
        BulguCase(String caseId, String term, String expArtist, String expTrack, String section) {
            this(caseId, term, expArtist, expTrack, section, 10);
        }
    }

    static Stream<BulguCase> cases() {
        // ── JSON'dan oku ──────────────────────────────────────────────────────
        try {
            File casesFile = new File(System.getProperty("user.dir"), "cases.json");
            if (casesFile.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                List<Map<String, Object>> list = mapper.readValue(casesFile,
                        new TypeReference<>() {
                        });
                return list.stream().map(m -> new BulguCase(
                        str(m, "caseId"),
                        str(m, "term"),
                        str(m, "expArtist"),
                        str(m, "expTrack"),
                        str(m, "section"),
                        m.get("topN") instanceof Number n ? n.intValue() : 10
                ));
            }
        } catch (Exception e) {
            System.err.println("⚠ cases.json okunamadı, hardcoded case'ler kullanılıyor: " + e.getMessage());
        }
        // ── Hardcoded fallback ────────────────────────────────────────────────
        return Stream.of(

                // ═════════════════════════════════════════════════════════════════
                // ŞARKİ · TAM EŞLEŞMe
                // Kullanıcı şarkı adını birebir yazmış (case-insensitive)
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("SARKI_TAM_1", "a canım", "Mabel Matiz", "A Canım", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_2", "olmazlara vuruluyorum", "Mert Demir", "Olmazlara Vuruluyorum", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_3", "çıkmaz bir sokakta", "", "Çıkmaz Bir Sokakta", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_4", "kusura bakma", "Blok3", "Kusura Bakma", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_5", "maraton", "Ati242", "Maraton", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_6", "geri ver", "Wegh", "Geri Ver", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_7", "saygımdan", "Bengü", "Saygımdan", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_8", "meğerse", "Linet", "Meğerse", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_9", "dacia", "Lvbel C5", "DACIA", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_10", "yalnızlığın çaresini bulmuşlar", "", "Yalnızlığın Çaresini Bulmuşlar", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_11", "yapar mısın", "Poizi", "YAPAR MISIN?", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_12", "yerinde dur", "Sefo", "Yerinde Dur", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_13", "ey aşk", "Sezen Aksu", "Ey Aşk", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_14", "şımarık", "Tarkan", "Şımarık", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_15", "giderim kırağınan", "Onur Şan", "Giderim Kırağınan", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_16", "ara beni lütfen", "Kenan Doğulu", "Ara Beni Lütfen", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_17", "aşk yok olmaktır", "", "Aşk Yok Olmaktır", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_18", "can efendim", "", "Can Efendim", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_19", "çıt çıt çedene", "Barış Manço", "Çıt Çıt Çedene", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_20", "çıkar biri karşıma", "Poizi", "Çıkar Biri Karşıma", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_21", "sen yanlış yaptın", "Şahin Kendirci", "Sen Yanlış Yaptın", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_22", "vay dayı", "Aynur Polat", "Vay Dayı", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_23", "silinmez", "Mansur Ark", "Silinmez", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_24", "halbuki", "Yalın", "Halbuki", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_25", "duydun mu", "Yusuf Güney", "Duydun Mu?", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_26", "sana güvenmiyorum", "Dedublüman", "Sana Güvenmiyorum", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_27", "yasemen", "Afra", "Yasemen", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_28", "düşer o", "İzel", "Düşer O", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_29", "kömür", "Mabel Matiz", "Kömür", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_30", "just the way you are", "", "Just The Way You Are", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_31", "snap", "Manifest", "Snap", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_32", "ama başaramadım", "Burak Bulut", "Ama Başaramadım", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_33", "adına bir çizik çektim", "", "Adına Bir Çizik Çektim", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_34", "yaramızda kalsın", "Merve Özbey", "Yaramızda Kalsın", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_35", "sev yeter", "", "Sev Yeter", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_36", "kaybolurum gülüşünde", "İkilem", "Kaybolurum Gülüşünde", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_37", "ağlama ben ağlarım", "Canozan", "Ağlama Ben Ağlarım", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_38", "şikayetim var", "", "Şikayetim Var", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_39", "bunca yıl", "Dedublüman", "Bunca Yıl", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_40", "düldül", "Mabel Matiz", "Düldül", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_41", "perde", "Poizi", "Perde", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_42", "phonk", "DEHA INC.", "Phonk", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_43", "sonbahar", "Era7Capone", "SONBAHAR", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_44", "acem kızı", "", "Acem Kızı", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_45", "hacel obası", "", "Hacel Obası", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_46", "yalan", "", "Yalan", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_47", "bana sor", "Ferdi Tayfur", "Bana Sor", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_48", "rüya", "Manifest", "Rüya", S_SARKI_TAM, 3),
                // Case revizyonu (22 Tem): "ara" cok yaygin kelime; topN 1 -> 3 (is birimi onayina sunuldu)
                new BulguCase("SARKI_TAM_49", "ara", "Zeynep Bastık", "Ara", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_50", "14 bahar", "Mert Demir", "14 Bahar", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_51", "ela mana", "", "Ela Mana", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_52", "yekten", "Demet Akalın", "Yekten", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_53", "erik dalı", "", "Erik Dalı", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_54", "elfida", "Haluk Levent", "Elfida", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_55", "yazan kalem siyah", "", "Yazan Kalem Siyah", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_56", "Mihriban", "", "Mihriban", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_57", "merdo", "", "Merdo", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_58", "sigara", "", "Sigara", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_59", "farzet", "İlyas Yalçıntaş", "Farzet", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_60", "misket", "", "Misket", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_61", "kara sevda", "", "Kara Sevda", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_62", "parla", "", "Parla", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_63", "kırmızı balık", "", "Kırmızı Balık", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_64", "nasır", "Melike Şahin", "Nasır", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_65", "bodrum", "Yüzyüzeyken Konuşuruz", "Bodrum", S_SARKI_TAM, 1),
                new BulguCase("SARKI_TAM_66", "gözlerime bak", "Mert Demir", "Gözlerime Bak", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_67", "türk marşı", "Ceza", "Türk Marşı", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_68", "there is a light that never goes out", "Morrissey", "There Is A Light That Never Goes Out", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_69", "ölek mi", "", "Ölek mi?", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_70", "ölek mi?", "", "Ölek mi?", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_71", "Sen Anlat Geçen Yüzyıl", "", "Sen Anlat Geçen Yüzyıl", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_72", "kürtçe", "", "kürtçe", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_73", "manisa", "", "manisa", S_SARKI_TAM, 3),
                // Case revizyonu (22 Tem): "mebrure" sarki adi degil sanatci adi (Mebrure Avas); beklenti sanatciya cevrildi
                new BulguCase("SARKI_TAM_74", "mebrure", "Mebrure", "", S_SARKI_TAM, 3),
                new BulguCase("SARKI_TAM_75", "köksal", "", "köksal", S_SARKI_TAM, 3),

                // ═════════════════════════════════════════════════════════════════
                // ŞARKİ · KISMİ AD
                // Kullanıcı şarkı adının yalnızca başını veya bir bölümünü yazmış
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("SARKI_KISMI_1", "Hermes", "Batuflex", "Hermès", S_SARKI_KISMI, 1),
                new BulguCase("SARKI_KISMI_2", "yalnızlığın çaresini", "gripin", "Yalnızlığın Çaresini Bulmuşlar", S_SARKI_KISMI, 3),
                new BulguCase("SARKI_KISMI_3", "yerinde", "Sefo", "Yerinde Dur", S_SARKI_KISMI, 1),
                new BulguCase("SARKI_KISMI_4", "vidrado em", "Dj Guuga", "Vidrado Em Você", S_SARKI_KISMI, 3),
                new BulguCase("SARKI_KISMI_5", "çıt çıt", "Barış Manço", "Çıt Çıt Çedene", S_SARKI_KISMI, 3),
                new BulguCase("SARKI_KISMI_6", "ağlama ben", "Canozan", "Ağlama Ben Ağlarım", S_SARKI_KISMI, 1),
                new BulguCase("SARKI_KISMI_7", "erik", "", "Erik Dalı", S_SARKI_KISMI, 1),
                new BulguCase("SARKI_KISMI_8", "doğuştan", "Lvbel C5", "Doğuştan Beri Haklıyım", S_SARKI_KISMI, 3),
                new BulguCase("SARKI_KISMI_9", "dandini", "Ninni Bebek", "Dandini Dandini Dastana", S_SARKI_KISMI, 3),
                new BulguCase("SARKI_KISMI_10", "sus", "Ceza", "Suspus", S_SARKI_KISMI, 1),
                new BulguCase("SARKI_KISMI_11", "pus", "Ceza", "Suspus", S_SARKI_KISMI, 5),
                new BulguCase("SARKI_KISMI_12", "kalbimin sahibine", "İrem Derici", "Kalbimin Tek Sahibine", S_SARKI_KISMI, 3),
                new BulguCase("SARKI_KISMI_13", "there is a light that never goes out (live)", "Morrissey", "There Is A Light That Never Goes Out", S_SARKI_KISMI, 3),
                new BulguCase("SARKI_KISMI_14", "dai", "", "Dai Dai", S_SARKI_KISMI, 3),
                new BulguCase("SARKI_KISMI_15", "alara doğum günün", "", "doğum günün kutlu olsun alara", S_SARKI_KISMI, 3),

                // ═════════════════════════════════════════════════════════════════
                // ŞARKİ · SANATÇI + ŞARKİ
                // Kullanıcı sanatçı adı + şarkı adını birlikte yazmış
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("SARKI_SANAT_1", "messy lola young", "Lola Young", "Messy", S_SARKI_SANAT, 3),
                new BulguCase("SARKI_SANAT_2", "mabel kömür", "Mabel Matiz", "Kömür", S_SARKI_SANAT, 1),
                new BulguCase("SARKI_SANAT_3", "y poizi", "Poizi", "Y", S_SARKI_SANAT, 3),
                new BulguCase("SARKI_SANAT_4", "kts manifest", "Manifest", "KTS", S_SARKI_SANAT, 1),
                new BulguCase("SARKI_SANAT_5", "Dua Lipa Shine", "Cédric", "Shine", S_SARKI_SANAT, 3),
                new BulguCase("SARKI_SANAT_6", "rüya manifest", "Manifest", "Rüya", S_SARKI_SANAT, 1),
                new BulguCase("SARKI_SANAT_7", "bir motive", "Motive", "bir", S_SARKI_SANAT, 3),
                // Case revizyonu (22 Tem): "Kenar Süsü" Sıla'nın şarkısı (ES dogrulandi, prodPop=96); sorgu duzeltildi
                new BulguCase("SARKI_SANAT_8", "Sıla kenar süsü", "", "Kenar Süsü", S_SARKI_SANAT, 3),
                new BulguCase("SARKI_SANAT_9", "Gel lvbel", "Lvbel C5", "GEL GEL GEL", S_SARKI_SANAT, 3),

                // ═════════════════════════════════════════════════════════════════
                // ŞARKİ · YAZIM TOLERANSI
                // Yazım yanlışı / eksik/yanlış karakter / Türkçe karakter eksikliği
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("SARKI_YAZIM_1", "acanım", "Mabel Matiz", "A Canım", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_2", "çok pardon", "Lvbel C5", "COOOK PARDON", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_3", "simarik", "Tarkan", "Şımarık", S_SARKI_YAZIM, 1),
                new BulguCase("SARKI_YAZIM_4", "dame un grr", "Fantomel", "Dame Un Grrr", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_5", "hav hav", "Lvbel C5", "Havhavhav", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_6", "karakedi", "Melis Fis", "Kara Kedi", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_7", "illede sen", "Azer Bülbül", "İlle De Sen", S_SARKI_YAZIM, 1),
                new BulguCase("SARKI_YAZIM_8", "arabam", "Sefo", "Araba", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_9", "lacivert eceler", "Ferhat Göçer", "Lacivert Geceler", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_10", "güldün ne güzel", "Pinhani", "Ne Güzel Güldün", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_11", "dia", "", "Dai Dai", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_12", "iyiki doğdun deniz", "", "iyi ki doğdun deniz", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_13", "taki seni görene kadar", "", "ta ki seni görene kadar", S_SARKI_YAZIM, 3),
                new BulguCase("SARKI_YAZIM_14", "can dostym", "", "can dostum", S_SARKI_YAZIM, 3),
                // Case revizyonu (22 Tem): katalogda basliklar bitisik "[Saygi1]"; beklenti veriyle uyumlandi
                new BulguCase("SARKI_YAZIM_15", "saygı1", "", "Saygi1", S_SARKI_YAZIM, 3),

                // ═════════════════════════════════════════════════════════════════
                // ŞARKİ · LYRİCS
                // Kullanıcı şarkı sözü parçasıyla arama yapmış
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("SARKI_LYRICS_1", "çölüme yağmur oldun", "Müslüm Gürses", "Affet", S_SARKI_LYRICS, 3),
                new BulguCase("SARKI_LYRICS_2", "hadi ya", "Melis Kar", "Yatıya", S_SARKI_LYRICS, 3),
                new BulguCase("SARKI_LYRICS_3", "babalar", "Blok3", "PATLAT", S_SARKI_LYRICS, 1),
                new BulguCase("SARKI_LYRICS_4", "çetin ceviz şerbetli mayam", "Melike Şahin", "Canın Beni Çekti", S_SARKI_LYRICS, 3),
                new BulguCase("SARKI_LYRICS_5", "yandım ah", "Sakiler", "Yalanı Bırak", S_SARKI_LYRICS, 1),
                new BulguCase("SARKI_LYRICS_6", "bak ben yara gibiyim", "Emir Can İğrek", "Nalan", S_SARKI_LYRICS, 1),
                new BulguCase("SARKI_LYRICS_7", "zaten aşklar hep yalan dolan", "Yıldız Tilbe", "Sana Değer", S_SARKI_LYRICS, 1),
                new BulguCase("SARKI_LYRICS_8", "sana hastayım anlasana", "Derya Uluğ", "Yansıma", S_SARKI_LYRICS, 1),
                new BulguCase("SARKI_LYRICS_9", "sarışınlar", "Derya Uluğ", "Esmerin Adı Oya", S_SARKI_LYRICS, 3),
                new BulguCase("SARKI_LYRICS_10", "silemez o beni", "Yıldız Tilbe", "Dizine Dursun", S_SARKI_LYRICS, 1),
                new BulguCase("SARKI_LYRICS_11", "babalar sözünü tutar", "Blok3", "PATLAT", S_SARKI_LYRICS, 3),
                new BulguCase("SARKI_LYRICS_12", "çok geç şmdi", "Edis", "Yalan", S_SARKI_LYRICS, 3),
                new BulguCase("SARKI_LYRICS_13", "affet bu gece istedim ölmek", "Model", "Pembe Mezarlık", S_SARKI_LYRICS, 3),
                new BulguCase("SARKI_LYRICS_14", "teybi bozuk bir arabayla", "Nilipek", "Geçmiyor Zaman", S_SARKI_LYRICS, 3),
                new BulguCase("SARKI_LYRICS_15", "Murat yeter magusa", "", "Mağusa Limanı", S_SARKI_LYRICS, 3),
                new BulguCase("SARKI_LYRICS_16", "Gel buraya", "Lvbel C5", "GEL GEL GEL", S_SARKI_LYRICS, 3),

                // ═════════════════════════════════════════════════════════════════
                // SANATÇI · TAM EŞLEŞMe
                // Kullanıcı sanatçı adını birebir yazmış
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("SANAT_TAM_1", "mfö", "MFÖ", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_2", "mfo", "MFÖ", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_3", "Manifest", "Manifest", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_4", "semicenk", "Semicenk", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_5", "utku akkaya", "Utku Akkaya", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_6", "derya bedavacı", "Derya Bedavacı", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_7", "ceza", "Ceza", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_8", "yaşar", "Yaşar", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_9", "Gökhan Özen", "Gökhan Özen", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_10", "çelik", "Çelik", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_11", "Haluk Levent", "Haluk Levent", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_12", "Mustafa Yıldızdoğan", "Mustafa Yıldızdoğan", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_13", "u2", "u2", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_14", "edis", "Edis", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_15", "uzi", "UZI", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_16", "sıla", "Sıla", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_17", "eminem", "Eminem", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_18", "güneş", "Güneş", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_19", "dua lipa", "Dua Lipa", "", S_SANAT_TAM, 1),
                new BulguCase("SANAT_TAM_20", "mero", "Mero", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_21", "murda", "Murda", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_22", "inna", "Inna", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_23", "adele", "Adele", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_24", "mor ve ötesi", "mor ve ötesi", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_25", "patron", "Patron", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_26", "tefo", "Tefo", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_27", "doğuş", "Doğuş", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_28", "ben fero", "Ben Fero", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_29", "inji", "INJI", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_30", "rihanna", "Rihanna", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_31", "mavi", "Mavi", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_32", "velet", "Velet", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_33", "adamlar", "Adamlar", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_34", "blackpink", "BLACKPINK", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_35", "sia", "Sia", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_36", "shakira", "Shakira", "", S_SANAT_TAM, 3),
                new BulguCase("SANAT_TAM_37", "madonna", "Madonna", "", S_SANAT_TAM, 3),

                // ═════════════════════════════════════════════════════════════════
                // SANATÇI · KISMİ AD
                // Kullanıcı sanatçının adını / soyadını / ilk kelimesini yazmış
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("SANAT_KISMI_1", "blok", "Blok3", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_2", "teo", "Teoman", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_3", "aleyna", "Aleyna Tilki", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_4", "ferdi", "Ferdi Tayfur", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_5", "mabel", "Mabel Matiz", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_6", "yıldız", "Yıldız Tilbe", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_7", "azer", "Azer Bülbül", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_8", "serdar", "Serdar Ortaç", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_9", "cengiz", "Cengiz Kurtoğlu", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_10", "neşet", "Neşet Ertaş", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_11", "melike", "Melike Şahin", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_12", "orhan", "Orhan Gencebay", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_13", "soner", "Soner Sarıkabadayı", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_14", "ati", "Ati242", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_15", "norm", "Norm Ender", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_16", "sibel", "Sibel Can", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_17", "irem", "İrem Derici", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_18", "musa", "Musa Eroğlu", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_19", "kurtuluş", "Kurtuluş Kuş", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_20", "cash", "Cash Flow", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_21", "reyn", "Reynmen", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_22", "mahsun", "Mahsun Kırmızıgül", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_23", "funda", "Funda Arar", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_24", "sura", "Sura İskenderli", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_25", "rafet", "Rafet El Roman", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_26", "haluk", "Haluk Levent", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_27", "lvbel", "Lvbel C5", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_28", "zerrin", "Zerrin Özer", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_29", "selda", "Selda Bağcan", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_30", "bilal", "Bilal Sonses", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_31", "gülden", "Gülden Karaböcek", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_32", "ibrahim tat", "İbrahim Tatlıses", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_33", "engin", "Engin Nurşani", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_34", "şebnem", "Şebnem Ferah", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_35", "ayaz", "Ayaz Erdoğan", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_36", "ajda", "Ajda Pekkan", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_37", "aynur", "Aynur Aydın", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_38", "hayko", "Hayko Cepkin", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_39", "koray", "Koray Avcı", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_40", "ümit", "Ümit Besen", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_41", "elif buse", "Elif Buse Doğan", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_42", "özcan", "Özcan Deniz", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_43", "deha", "DEHA INC.", "", S_SANAT_KISMI, 1),
                new BulguCase("SANAT_KISMI_44", "taylor", "Taylor Swift", "", S_SANAT_KISMI, 1),

                // ═════════════════════════════════════════════════════════════════
                // SANATÇI · YAZIM TOLERANSI
                // Yazım yanlışı / fonetik benzerlik / Türkçe karakter eksikliği
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("SANAT_YAZIM_1", "tarkn", "Tarkan", "", S_SANAT_YAZIM, 1),
                new BulguCase("SANAT_YAZIM_2", "kök$l", "kök$vl", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_3", "çakal", "cakal", "", S_SANAT_YAZIM, 1),
                new BulguCase("SANAT_YAZIM_4", "pozi", "Poizi", "", S_SANAT_YAZIM, 1),
                new BulguCase("SANAT_YAZIM_5", "hşdra", "Hidra", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_6", "goksel", "Göksel", "", S_SANAT_YAZIM, 1),
                new BulguCase("SANAT_YAZIM_7", "can ozan", "Canozan", "", S_SANAT_YAZIM, 1),
                new BulguCase("SANAT_YAZIM_8", "ezel", "Ezhel", "", S_SANAT_YAZIM, 1),
                new BulguCase("SANAT_YAZIM_9", "reymen", "Reynmen", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_10", "emircan", "Emir Can İğrek", "", S_SANAT_YAZIM, 1),
                new BulguCase("SANAT_YAZIM_11", "emircan iğrek", "Emir Can İğrek", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_12", "izel", "İzel", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_13", "hejan", "Heijan", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_14", "Semicek", "Semicenk", "", S_SANAT_YAZIM, 1),
                new BulguCase("SANAT_YAZIM_15", "beyonce", "Beyoncé", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_16", "emre gel", "Emre Fel", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_17", "sibelcan", "Sibel Can", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_18", "kofn", "KÖFN", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_19", "sertap", "Sertab Erener", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_20", "mr ve ötei", "Mor ve Ötesi", "", S_SANAT_YAZIM, 3),
                new BulguCase("SANAT_YAZIM_21", "dolu kadhi tut", "Dolu Kadehi Ters Tut", "", S_SANAT_YAZIM, 3),

                // ═════════════════════════════════════════════════════════════════
                // SANATÇI · KISALTMA / ALİAS
                // Resmi ismin kısaltması, rumuz, sayısal alias
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("SANAT_ALIAS_1", "lvc5", "Lvbel C5", "", S_SANAT_ALIAS, 3),
                new BulguCase("SANAT_ALIAS_2", "level c5", "Lvbel C5", "", S_SANAT_ALIAS, 3),
                new BulguCase("SANAT_ALIAS_3", "levelc5", "Lvbel C5", "", S_SANAT_ALIAS, 3),
                new BulguCase("SANAT_ALIAS_4", "84", "seksendört", "", S_SANAT_ALIAS, 1),
                new BulguCase("SANAT_ALIAS_5", "sago", "Sagopa Kajmer", "", S_SANAT_ALIAS, 1),
                new BulguCase("SANAT_ALIAS_6", "no1", "No.1", "", S_SANAT_ALIAS, 3),
                new BulguCase("SANAT_ALIAS_7", "halo", "Halodayı", "", S_SANAT_ALIAS, 3),
                new BulguCase("SANAT_ALIAS_8", "50", "50 Cent", "", S_SANAT_ALIAS, 3),
                new BulguCase("SANAT_ALIAS_9", "no 1", "No.1", "", S_SANAT_ALIAS, 3),
                new BulguCase("SANAT_ALIAS_10", "dktt", "Dolu Kadehi Ters Tut", "", S_SANAT_ALIAS, 3),

                // ═════════════════════════════════════════════════════════════════
                // PLAYLİST
                // Kullanıcı kategori / tür adıyla çalma listesi arıyor
                // ═════════════════════════════════════════════════════════════════
                new BulguCase("PLAYLIST_1", "akustik", "", "[Playlist] akustik", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_2", "pop", "", "[Playlist] pop", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_3", "90", "", "[Playlist] 90", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_4", "90lar", "", "[Playlist] 90", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_5", "90'lar", "", "[Playlist] 90", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_6", "90s", "", "[Playlist] 90", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_7", "90 lar", "", "[Playlist] 90", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_8", "arabesk", "", "[Playlist] arabesk", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_9", "ilahi", "", "[Playlist] ilahi", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_10", "karadeniz", "", "[Playlist] karadeniz", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_11", "halay", "", "[Playlist] halay", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_12", "yabancı", "", "[Playlist] yabancı", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_13", "roman", "", "[Playlist] roman", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_14", "çocuk", "", "[Playlist] çocuk", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_15", "oyun hava", "", "[Playlist] oyun hava", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_16", "spor", "", "[Playlist] spor", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_17", "klasik", "", "[Playlist] klasik", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_18", "ankara", "", "[Playlist] ankara", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_19", "çocuk şarkıları", "", "[Playlist] çocuk", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_20", "türkçe", "", "[Playlist] türkçe", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_21", "80", "", "[Playlist] 80", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_22", "rock", "", "[Playlist] rock", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_23", "dans", "", "[Playlist] dans", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_24", "türk sanat", "", "[Playlist] türk sanat", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_25", "meditasyon", "", "[Playlist] meditasyon", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_26", "90 lar pop", "", "[Playlist] pop", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_27", "ramazan", "", "[Playlist] ramazan", S_PLAYLIST, 5),
                new BulguCase("PLAYLIST_28", "masumiyet müzesi", "", "[Playlist] Masumiyet Müzesi", S_PLAYLIST, 5)

        );
    }

    // =========================================================================
    // TEST — ASLA FAIL ETMEZ
    // =========================================================================

    @ParameterizedTest(name = "[{0}] \"{1}\"")
    @MethodSource("cases")
    @Order(1)
    void run(BulguCase bc) {
        String[] result = new String[]{"(beklenen tanımlanmamış)", "NOK", "API hatası"};

        try {
            Response res = api.search(bc.term(), "active-indices", TOP_N);
            JsonPath jp = res.jsonPath();
            result = evaluate(bc, jp);
        } catch (Exception e) {
            System.err.printf("⚠️  API hatası [%s '%s']: %s%n",
                    bc.caseId(), bc.term(), e.getMessage());
            result[2] = "API hatası: " + e.getMessage();
        }

        System.out.printf("[%s] %-15s | %-38s | %s%n",
                result[1], bc.caseId(), "\"" + bc.term() + "\"", result[2]);

        ROWS.add(new TestResultRow(
                bc.caseId(),
                "\"" + bc.term() + "\" araması yapılır",
                "Arama terimi: '" + bc.term() + "' — Bölüm: " + bc.section(),
                result[0],
                bc.section(),
                "active-indices",
                result[1],
                result[2]
        ));
    }

    // =========================================================================
    // KURAL DEĞERLENDİRME
    // =========================================================================

    /**
     * BulguCase'den kural türetir ve değerlendirme sonucunu döndürür.
     * <p>
     * Dönüş: String[]{ beklenenAçıklama, "OK"/"NOK", detayMesajı }
     * <p>
     * Kural türetme mantığı:
     * expTrack"[Playlist] ..."  → TOPN_RELATED_PLAYLIST
     * expArtist dolu, expTrack boş → FIRST_ARTIST_IS
     * expArtist ve/veya expTrack dolu → TOPN_HAS_ARTIST_AND_TRACK
     */
    private String[] evaluate(BulguCase bc, JsonPath jp) {
        List<Object> list = MuudSearchUtils.resultsList(jp);
        String base = MuudSearchUtils.getBasePath(jp);

        if (list.isEmpty()) {
            return new String[]{
                    "Arama sonucunda en az 1 kayıt dönmesi beklenir.",
                    "NOK",
                    "API boş sonuç döndürdü."};
        }

        String expTrack = bc.expTrack();
        String expArtist = bc.expArtist();

        if (expTrack.isEmpty() && expArtist.isEmpty()) {
            return new String[]{"(gözlem)", "OK",
                    "Gözlem case'i — beklenen içerik tanımlanmamış, " + list.size() + " sonuç döndü.\n"
                            + top5Desc(jp, base)};
        }

        if (expTrack.toLowerCase(TR).startsWith("[playlist]")) {
            return evalPlaylist(bc, jp, base);
        }

        if (!expArtist.isEmpty() && expTrack.isEmpty()) {
            return evalFirstArtist(bc, jp, base);
        }

        return evalArtistAndTrack(bc, jp, base);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIRST_ARTIST_IS — 1. sırada beklenen sanatçı gelmeli
    // ─────────────────────────────────────────────────────────────────────────

    private String[] evalFirstArtist(BulguCase bc, JsonPath jp, String base) {
        int n = bc.topN();
        String expected = n == 1
                ?"1. sırada '" + bc.expArtist() + "' sanatçısı gelmeli."
                : "İlk" + n + " içinde '" + bc.expArtist() + "' sanatçısı gelmeli.";

        int pos = MuudSearchUtils.findArtistIndex(jp, n, bc.expArtist());

        if (pos != -1) {
            String fa = MuudSearchUtils.getPerformerName(jp, base + "[" + pos + "].data");
            return new String[]{expected, "OK",
                    "Başarılı —" + (pos + 1) + ". sırada '" + fa + "' geldi.\n"
                            + top5Desc(jp, base)};
        }

        int fullPos = MuudSearchUtils.findArtistIndex(jp, TOP_N, bc.expArtist());
        String where = fullPos == -1
                ?"top-" + TOP_N + "'da da bulunamadı"
                : (fullPos + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "İlk" + n + "'da yok —" + bc.expArtist() + ": " + where + ".\n"
                        + top5Desc(jp, base)};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOPN_HAS_ARTIST_AND_TRACK — Top-N içinde sanatçı+şarkı birlikte bulunmalı
    // ─────────────────────────────────────────────────────────────────────────

    private String[] evalArtistAndTrack(BulguCase bc, JsonPath jp, String base) {
        int n = bc.topN();
        String expStr = bc.expArtist().isEmpty()
                ?"'" + bc.expTrack() + "'"
                : "'" + bc.expArtist() + "' – '" + bc.expTrack() + "'";
        String expected = "Top-" + n + " içinde" + expStr + " eşleşmesi bulunmalı.";

        int idx = MuudSearchUtils.findArtistAndTrackIndex(jp, n, bc.expArtist(), bc.expTrack());

        if (idx != -1) {
            String fa = MuudSearchUtils.getPerformerName(jp, base + "[" + idx + "].data");
            String ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.songName"));
            if (ft.isEmpty())
                ft = MuudSearchUtils.safeStr(jp.getString(base + "[" + idx + "].data.albumName"));
            String label = fa.isEmpty() ? ft : fa + "' – '" + ft;
            return new String[]{expected, "OK",
                    "Başarılı —" + (idx + 1) + ". sırada: '" + label + "'.\n"
                            + top5Desc(jp, base)};
        }

        int fullIdx = MuudSearchUtils.findArtistAndTrackIndex(jp, TOP_N, bc.expArtist(), bc.expTrack());
        String where = fullIdx == -1
                ?"top-" + TOP_N + "'da da bulunamadı"
                : (fullIdx + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "Top-" + n + "'da yok —" + expStr + ": " + where + ".\n"
                        + top5Desc(jp, base)};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOPN_RELATED_PLAYLIST — Top-N içinde keyword içeren playlist bulunmalı
    // ─────────────────────────────────────────────────────────────────────────

    private String[] evalPlaylist(BulguCase bc, JsonPath jp, String base) {
        int n = bc.topN();
        String keyword = bc.expTrack().substring("[Playlist]".length()).trim();
        String expected = "Top-" + n + " içinde '" + keyword + "' adını içeren playlist bulunmalı.";

        for (int i = 0; i < n; i++) {
            String pl = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
            if (!pl.isEmpty() && MuudSearchUtils.containsTRInsensitive(pl, keyword)) {
                return new String[]{expected, "OK",
                        "Başarılı —" + (i + 1) + ". sırada playlist bulundu: '" + pl + "'.\n"
                                + top5Desc(jp, base)};
            }
        }

        int fullPos = -1;
        for (int i = 0; i < TOP_N; i++) {
            String pl = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
            if (!pl.isEmpty() && MuudSearchUtils.containsTRInsensitive(pl, keyword)) {
                fullPos = i;
                break;
            }
        }
        String where = fullPos == -1
                ?"top-" + TOP_N + "'da da bulunamadı"
                : (fullPos + 1) + ". sırada bulundu";
        return new String[]{expected, "NOK",
                "Top-" + n + "'da yok — '" + keyword + "': " + where + ".\n"
                        + top5Desc(jp, base)};
    }

    // =========================================================================
    // YARDIMCI — Her case için"İlk 5 sonuç" listesi (numPlays,
    //            performerPopularity, popularity, score alt parametreleriyle)
    // =========================================================================

    private String itemDesc(JsonPath jp, String base, int i) {
        String song = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.songName"));
        String album = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.albumName"));
        String playlist = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
        String performer = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
        String kind = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.kind"));

        String label;
        if (!song.isEmpty())
            label = performer.isEmpty() ?"'" + song + "'" : "'" + performer + " –" + song + "'";
        else if (!album.isEmpty())
            label = "[Albüm] '" + album + "'" + (performer.isEmpty() ?"" : " – '" + performer + "'");
        else if (!playlist.isEmpty())
            label = "[Playlist] '" + playlist + "'";
        else if (!performer.isEmpty())
            label = "[Sanatçı] '" + performer + "'";
        else
            label = "(boş)";

        String kindPrefix = kind.isEmpty() ?"" : "[" + kind + "]";
        Object scoreObj = jp.get(base + "[" + i + "].score");
        String score = scoreObj != null ? scoreObj.toString() : "-";

        String createTime = MuudSearchUtils.safeStr(jp.getString(base + "[" + i + "].data.createTime"));

        String metrics;
        if ("performers".equals(kind)) {
            Object popularSongCountObj = jp.get(base + "[" + i + "].data.popularSongCount");
            String popularSongCount = popularSongCountObj != null ? popularSongCountObj.toString() : "-";
            metrics = "popularSongCount= " + popularSongCount + " | score= " + score;
        } else {

            Object numPlaysObj = jp.get(base + "[" + i + "].data.numPlays");
            Object perfPopObj = jp.get(base + "[" + i + "].data.performerPopularity");
            Object popularityObj = jp.get(base + "[" + i + "].data.popularity");
            String numPlays = numPlaysObj != null ? numPlaysObj.toString() : "-";
            String perfPop = perfPopObj != null ? perfPopObj.toString() : "-";
            String popularity = popularityObj != null ? popularityObj.toString() : "-";
            metrics = " | numPlays= " + numPlays
                    + " | performerPopularity= " + perfPop
                    + " | popularity= " + popularity
                    + " | score= " + score;
        }
        if (!createTime.isEmpty()) {
            metrics += " | createTime= " + createTime;
        }

        return kindPrefix + label + "\n" + metrics;
    }

    private String top5Desc(JsonPath jp, String base) {
        StringBuilder sb = new StringBuilder("İlk 5 sonuç: ");
        for (int i = 0; i < 5; i++) {
            sb.append("\n").append(i + 1).append(".").append(itemDesc(jp, base, i));
        }
        return sb.toString();
    }
}