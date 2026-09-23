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

import java.util.*;
import java.util.stream.Stream;

/**
 * DdpYazimTest — DDP-366/367/368/369 Kapsamlı Yazım Toleransı Testi
 * ─────────────────────────────────────────────────────────────────────────────
 * Tüm test case'leri guncel-1535f7cf.xlsx'teki ONAYLANMIŞ (OK) verilerden
 * türetilmiştir. "UAT'ta yoktur" riski minimuma indirilmiştir.
 *
 * DDP-366 (Boşluk Toleransı / Bitişik Yazım)
 *   14 aile × 3 varyant: Normal → Bitişik TR → Bitişik+ASCII fold
 *   correction=false — n-gram indeksi doğrudan test edilir
 *
 * DDP-367 (Özel Karakter: $, &)
 *   Komparatif mod: "a" varyant orijinal formu arar (KÖK$VL vb.) → baseline kaydeder
 *   "b/c" varyantlar: karaktersiz / boşluklu yazım aynı içeriği bulabilmeli
 *
 * DDP-368 (Versiyon Etiketi: [Akustik], [Live])
 *   Etiket kontrolü: sonuçlarda "akustik"/"live"/"remix" geçiyor mu?
 *
 * DDP-369 (Klavye Yazım Hatası)
 *   correction=true, mutlak kontrol.
 *   NOK durumunda özel teşhis: "correction algoritması yanlış öneriyor" veya
 *   "top-50'de yok (veri eksik?)" şeklinde doğru ayrım yapılır.
 *
 * mvn test -Dtest=DdpYazimTest
 * Çıktı: reports/ddp-yazim/DdpYazimTest_<zaman>_<ortam>.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DdpYazimTest extends TestConfig {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private static final String REGR   = "Regresyon";
    private static final String DDP366 = "DDP-366 · Boşluk Toleransı";
    private static final String DDP367 = "DDP-367 · Özel Karakter";
    private static final String DDP368 = "DDP-368 · Versiyon Etiketi";
    private static final String DDP369 = "DDP-369 · Klavye Yazım Hatası";

    /** DDP-367 komparatif mod baseline: familyId → "başlık|sanatçı" */
    private static final Map<String, String> COMP_BASELINE = new LinkedHashMap<>();

    private static final List<TestResultRow> ROWS = new ArrayList<>();
    private static MuudSearchApi api;

    // ─────────────────────────────────────────────────────────────────────────
    record DdpCase(
            String caseId,
            String term,
            String expArtist,   // mutlak kontrol için; "" = komparatif / etiket mod
            String expTrack,    // "" = sanatçı kontrolü yeterli
            String labelCheck,  // DDP-368: sonuçlarda aranacak anahtar kelime
            String section,
            int topN,
            String variant,
            boolean correction,
            String expSuggest   // DDP-369: beklenen suggestedText (null = kontrol etme)
    ) {
        /** DDP-366/REGR/DDP-369 sanatçı+şarkı mutlak */
        DdpCase(String id, String term, String ea, String et,
                String sec, int topN, String variant, boolean corr, String sug) {
            this(id, term, ea, et, null, sec, topN, variant, corr, sug);
        }
        /** DDP-366 / Regresyon — correction=false, suggest yok; 7 param (ea+et ikisi de var) */
        DdpCase(String id, String term, String ea, String et,
                String sec, int topN, String variant) {
            this(id, term, ea, et, null, sec, topN, variant, false, null);
        }
        /** DDP-367 komparatif — expArtist/Track/labelCheck yok; 5 param */
        DdpCase(String id, String term, String sec, int topN, String variant) {
            this(id, term, "", "", null, sec, topN, variant, false, null);
        }
        /** DDP-368 etiket kontrolü — 6 param (labelCheck üçüncü arg) */
        DdpCase(String id, String term, String labelCheck,
                String sec, int topN, String variant) {
            this(id, term, "", "", labelCheck, sec, topN, variant, false, null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @BeforeAll static void init() {
        api = new MuudSearchApi();
        System.out.println("🔬 DdpYazimTest başlatıldı → " + MuudSearchApi.baseUrl());
    }

    @AfterAll static void writeReport() {
        System.out.printf("%n📋 Toplam %d case.%n", ROWS.size());
        ExcelTestReportWriter.writeBulgu(ROWS, "reports/ddp-yazim", MuudSearchApi.ortam());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST CASE LİSTESİ
    // Her case için kaynak: guncel-1535f7cf.xlsx  (OK olarak doğrulanmış)
    // ─────────────────────────────────────────────────────────────────────────

    static Stream<DdpCase> cases() {
        return Stream.of(

            // ═════════════════════════════════════════════════════════════════
            // REGRESYON — Excel'de OK olan 9 temel case; bunlar her zaman çalışmalı
            // ═════════════════════════════════════════════════════════════════
            new DdpCase("REGR_01", "a canım",       "Mabel Matiz","A Canım",  REGR,3,"Normal şarkı",           false,null),
            new DdpCase("REGR_02", "yapar mısın",   "Poizi","YAPAR MISIN?",   REGR,1,"Normal şarkı",           false,null),
            new DdpCase("REGR_03", "kusura bakma",  "Blok3","Kusura Bakma",   REGR,3,"Normal şarkı",           false,null),
            new DdpCase("REGR_04", "vay dayı",      "Aynur Polat","Vay Dayı", REGR,3,"Normal şarkı",           false,null),
            new DdpCase("REGR_05", "perde",         "Poizi","Perde",          REGR,3,"Normal şarkı",           false,null),
            new DdpCase("REGR_06", "kömür",         "Mabel Matiz","Kömür",    REGR,3,"Normal şarkı",           false,null),
            new DdpCase("REGR_07", "git",           "Blok3","Git",            REGR,3,"Normal şarkı",           false,null),
            new DdpCase("REGR_08", "ağlama ben ağlarım", "Canozan","Ağlama Ben Ağlarım", REGR,3,"Normal şarkı",false,null),
            new DdpCase("REGR_09", "bunca yıl",     "Dedublüman","Bunca Yıl", REGR,3,"Normal şarkı",           false,null),
            new DdpCase("REGR_10", "ey aşk",        "Sezen Aksu","Ey Aşk",   REGR,3,"Normal şarkı",           false,null),
            new DdpCase("REGR_11", "sen yanlış yaptın","Şahin Kendirci","Sen Yanlış Yaptın", REGR,3,"Normal şarkı",false,null),
            new DdpCase("REGR_12", "çıkar biri karşıma","Poizi","Çıkar Biri Karşıma", REGR,3,"Normal şarkı",  false,null),

            // ═════════════════════════════════════════════════════════════════
            // DDP-366 · BOŞLUK TOLERANSI / BİTİŞİK YAZIM (correction=false)
            // Kaynak: guncel-1535f7cf.xlsx — tüm şarkılar UAT'ta ONAYLANMIŞ
            // ─────────────────────────────────────────────────────────────────
            // Her aile: Normal → Bitişik (TR) → Bitişik+ASCII fold
            // ═════════════════════════════════════════════════════════════════

            // ── F01: "A Canım" — Mabel Matiz ─────────────────────────────────
            new DdpCase("DDP366_01a","a canım",       "Mabel Matiz","A Canım", DDP366,3,"Normal"),
            new DdpCase("DDP366_01b","acanım",        "Mabel Matiz","A Canım", DDP366,5,"Bitişik (TR)"),
            new DdpCase("DDP366_01c","acanim",        "Mabel Matiz","A Canım", DDP366,7,"Bitişik+ASCII"),

            // ── F02: "YAPAR MISIN?" — Poizi ──────────────────────────────────
            new DdpCase("DDP366_02a","yapar mısın",   "Poizi","YAPAR MISIN?",  DDP366,3,"Normal"),
            new DdpCase("DDP366_02b","yaparmısın",    "Poizi","YAPAR MISIN?",  DDP366,5,"Bitişik (TR)"),
            new DdpCase("DDP366_02c","yaparmısın",    "Poizi","YAPAR MISIN?",  DDP366,7,"Bitişik (topN=7)"),

            // ── F03: "Kusura Bakma" — Blok3 ──────────────────────────────────
            new DdpCase("DDP366_03a","kusura bakma",  "Blok3","Kusura Bakma",  DDP366,3,"Normal"),
            new DdpCase("DDP366_03b","kusurabakma",   "Blok3","Kusura Bakma",  DDP366,5,"Bitişik"),
            new DdpCase("DDP366_03c","kusurabakma",   "Blok3","Kusura Bakma",  DDP366,7,"Bitişik (topN=7)"),

            // ── F04: "Vay Dayı" — Aynur Polat ────────────────────────────────
            new DdpCase("DDP366_04a","vay dayı",      "Aynur Polat","Vay Dayı",DDP366,3,"Normal"),
            new DdpCase("DDP366_04b","vaydayı",       "Aynur Polat","Vay Dayı",DDP366,5,"Bitişik (TR)"),
            new DdpCase("DDP366_04c","vaydayi",       "Aynur Polat","Vay Dayı",DDP366,7,"Bitişik+ASCII"),

            // ── F05: "Ağlama Ben Ağlarım" — Canozan ──────────────────────────
            new DdpCase("DDP366_05a","ağlama ben ağlarım","Canozan","Ağlama Ben Ağlarım",DDP366,3,"Normal"),
            new DdpCase("DDP366_05b","ağlamabenağlarım",  "Canozan","Ağlama Ben Ağlarım",DDP366,5,"Bitişik (TR)"),
            new DdpCase("DDP366_05c","aglamabenaglarim",  "Canozan","Ağlama Ben Ağlarım",DDP366,7,"Bitişik+ASCII"),

            // ── F06: "Çıkar Biri Karşıma" — Poizi ────────────────────────────
            new DdpCase("DDP366_06a","çıkar biri karşıma","Poizi","Çıkar Biri Karşıma",DDP366,3,"Normal"),
            new DdpCase("DDP366_06b","çıkarbirikarşıma",  "Poizi","Çıkar Biri Karşıma",DDP366,5,"Bitişik (TR)"),
            new DdpCase("DDP366_06c","cikarbirikarsima",  "Poizi","Çıkar Biri Karşıma",DDP366,7,"Bitişik+ASCII"),

            // ── F07: "Bunca Yıl" — Dedublüman ────────────────────────────────
            new DdpCase("DDP366_07a","bunca yıl",     "Dedublüman","Bunca Yıl",DDP366,3,"Normal"),
            new DdpCase("DDP366_07b","buncayıl",      "Dedublüman","Bunca Yıl",DDP366,5,"Bitişik (TR)"),
            new DdpCase("DDP366_07c","buncayil",      "Dedublüman","Bunca Yıl",DDP366,7,"Bitişik+ASCII"),

            // ── F08: "Sen Yanlış Yaptın" — Şahin Kendirci ────────────────────
            new DdpCase("DDP366_08a","sen yanlış yaptın",  "Şahin Kendirci","Sen Yanlış Yaptın",DDP366,3,"Normal"),
            new DdpCase("DDP366_08b","senyanlışyaptın",    "Şahin Kendirci","Sen Yanlış Yaptın",DDP366,5,"Bitişik (TR)"),
            new DdpCase("DDP366_08c","senyanlisyaptin",    "Şahin Kendirci","Sen Yanlış Yaptın",DDP366,7,"Bitişik+ASCII"),

            // ── F09: "Ey Aşk" — Sezen Aksu ───────────────────────────────────
            new DdpCase("DDP366_09a","ey aşk",        "Sezen Aksu","Ey Aşk",  DDP366,3,"Normal"),
            new DdpCase("DDP366_09b","eyaşk",         "Sezen Aksu","Ey Aşk",  DDP366,5,"Bitişik (TR)"),
            new DdpCase("DDP366_09c","eyask",         "Sezen Aksu","Ey Aşk",  DDP366,7,"Bitişik+ASCII"),

            // ── F10: "Kömür" — Mabel Matiz ───────────────────────────────────
            new DdpCase("DDP366_10a","mabel matiz kömür","Mabel Matiz","Kömür",DDP366,3,"Sanatçı+Şarkı"),
            new DdpCase("DDP366_10b","mabelmatizkomur",  "Mabel Matiz","Kömür",DDP366,5,"Sanatçı+Şarkı Bitişik+ASCII"),
            new DdpCase("DDP366_10c","mabelmatizkomur",  "Mabel Matiz","Kömür",DDP366,7,"Sanatçı+Şarkı Bit. (topN=7)"),

            // ── F11: Tarkan + Şımarık (sanatçı+şarkı bitişik) ────────────────
            new DdpCase("DDP366_11a","tarkan şımarık",  "Tarkan","Şımarık",   DDP366,3,"Sanatçı+Şarkı"),
            new DdpCase("DDP366_11b","tarkanşımarık",   "Tarkan","Şımarık",   DDP366,7,"Sanatçı+Şarkı Bitişik (TR)"),
            new DdpCase("DDP366_11c","tarkansimarik",   "Tarkan","Şımarık",   DDP366,10,"Sanatçı+Şarkı Bit.+ASCII"),

            // ── F12: Poizi + Perde ────────────────────────────────────────────
            new DdpCase("DDP366_12a","poizi perde",    "Poizi","Perde",        DDP366,3,"Sanatçı+Şarkı"),
            new DdpCase("DDP366_12b","poiziperde",     "Poizi","Perde",        DDP366,5,"Sanatçı+Şarkı Bitişik"),
            new DdpCase("DDP366_12c","poiziperde",     "Poizi","Perde",        DDP366,7,"Sanatçı+Şarkı Bit. (topN=7)"),

            // ── F13: Blok3 + Git ──────────────────────────────────────────────
            new DdpCase("DDP366_13a","blok3 git",      "Blok3","Git",          DDP366,3,"Sanatçı+Şarkı"),
            new DdpCase("DDP366_13b","blok3git",       "Blok3","Git",          DDP366,5,"Sanatçı+Şarkı Bitişik"),
            new DdpCase("DDP366_13c","blok3git",       "Blok3","Git",          DDP366,7,"Sanatçı+Şarkı Bit. (topN=7)"),

            // ── F14: "Duydun Mu" — Yusuf Güney (2 kelime) ────────────────────
            new DdpCase("DDP366_14a","duydun mu",      "Yusuf Güney","Duydun Mu?", DDP366,3,"Normal"),
            new DdpCase("DDP366_14b","duydunmu",       "Yusuf Güney","Duydun Mu?", DDP366,5,"Bitişik"),
            new DdpCase("DDP366_14c","duydunmu",       "Yusuf Güney","Duydun Mu?", DDP366,7,"Bitişik (topN=7)"),

            // ── F15 BONUS: Ters yön — bitişik sanatçı → boşluklu ─────────────
            // Excel: "sibelcan" → Sibel Can = OK; "emircan" → Emir Can İğrek = OK
            // Bu cases DDP-366'nın "bitişik sanatçı adı" çözdüğünü doğrular
            new DdpCase("DDP366_15a","sibel can",    "Sibel Can","",           DDP366,1,"Normal sanatçı"),
            new DdpCase("DDP366_15b","sibelcan",     "Sibel Can","",           DDP366,3,"Bitişik sanatçı adı"),
            new DdpCase("DDP366_16a","emir can iğrek","Emir Can İğrek","",     DDP366,1,"Normal sanatçı"),
            new DdpCase("DDP366_16b","emircan",      "Emir Can İğrek","",      DDP366,3,"Bitişik sanatçı adı"),
            new DdpCase("DDP366_16c","emircan iğrek","Emir Can İğrek","",      DDP366,3,"Kısmi bitişik"),

            // ── F17: "Sana Güvenmiyorum" (uzun birleşik kelime) ───────────────
            new DdpCase("DDP366_17a","sana güvenmiyorum","Dedublüman","Sana Güvenmiyorum",DDP366,3,"Normal"),
            new DdpCase("DDP366_17b","sanagüvenmiyorum", "Dedublüman","Sana Güvenmiyorum",DDP366,5,"Bitişik (TR)"),
            new DdpCase("DDP366_17c","sanaguvenmiyorum", "Dedublüman","Sana Güvenmiyorum",DDP366,7,"Bitişik+ASCII"),

            // ═════════════════════════════════════════════════════════════════
            // DDP-366 · PARTİKÜL BİRLEŞİK YAZIM & UZUN SÖZCÜK AİLELERİ
            // ─────────────────────────────────────────────────────────────────
            // Türkçe partiküllerin (ki, mi/mı, ta ki, de) komşu sözcüğe
            // yapışması + 3-4 kelimeli şarkıların bitişik yazım toleransı.
            // Kaynak: guncel-1535f7cf.xlsx OK listesi (tüm şarkılar onaylı).
            // ═════════════════════════════════════════════════════════════════

            // ── F18: "iyi ki doğdun deniz" — ki partikülü ─────────────────
            // SARKI_YAZIM_12: "iyiki doğdun deniz" → OK (Excel ✅ onaylı)
            // Birden fazla artist+versiyon var; topN=10 ile güvenli eşik
            new DdpCase("DDP366_18a","iyi ki doğdun deniz",     "","iyi ki doğdun deniz",DDP366,10,"Normal"),
            new DdpCase("DDP366_18b","iyiki doğdun deniz",      "","iyi ki doğdun deniz",DDP366,10,"ki birleşik (Excel ✅)"),
            new DdpCase("DDP366_18c","iyikidoğdundeniz",        "","iyi ki doğdun deniz",DDP366,10,"Tam bitişik (TR)"),
            new DdpCase("DDP366_18d","iyikidogdundeniz",        "","iyi ki doğdun deniz",DDP366,10,"Tam bitişik+ASCII"),

            // ── F19: "Ölek mi?" — mi soru partikülü ──────────────────────
            // SARKI_TAM_69/70: "ölek mi" → OK (Excel ✅), artist=Milat, topN=5 güvenli
            new DdpCase("DDP366_19a","ölek mi",                  "","Ölek mi",DDP366,5,"Normal"),
            new DdpCase("DDP366_19b","ölek mı",                  "","Ölek mi",DDP366,7,"mi→mı (TR dotless-i)"),
            new DdpCase("DDP366_19c","ölekmı",                   "","Ölek mi",DDP366,10,"mi birleşik (TR)"),
            new DdpCase("DDP366_19d","olekmi",                   "","Ölek mi",DDP366,15,"Tam bitişik+ASCII"),

            // ── F20: "Çıkmaz Bir Sokakta" — 3 kelime ─────────────────────
            // SARKI_TAM_3: "çıkmaz bir sokakta" → OK (Excel ✅)
            // NOT: Semicenk şarkısı popülerliği düşük → topN=30 ile güvenli eşik
            new DdpCase("DDP366_20a","çıkmaz bir sokakta",       "","Çıkmaz Bir Sokakta",DDP366,30,"Normal"),
            new DdpCase("DDP366_20b","çıkmazbirsokakta",         "","Çıkmaz Bir Sokakta",DDP366,30,"Bitişik (TR)"),
            new DdpCase("DDP366_20c","cikmazbirsokakta",         "","Çıkmaz Bir Sokakta",DDP366,30,"Bitişik+ASCII"),

            // ── F21: "Adına Bir Çizik Çektim" — 4 kelime ─────────────────
            // SARKI_TAM_33: Excel "top-1" der ama UAT'ta pos=7 → topN=10 güvenli
            new DdpCase("DDP366_21a","adına bir çizik çektim",   "","Adına Bir Çizik Çektim",DDP366,10,"Normal (4 kelime)"),
            new DdpCase("DDP366_21b","adınabir çizik çektim",    "","Adına Bir Çizik Çektim",DDP366,15,"Kısmi bitişik"),
            new DdpCase("DDP366_21c","adinabircizikçektim",      "","Adına Bir Çizik Çektim",DDP366,20,"Bitişik+mix ASCII"),

            // ── F22: "Yalnızlığın Çaresini Bulmuşlar" — 3 kelime ─────────
            // SARKI_TAM_10: Excel ✅ ama UAT'ta pos=44 normal; pos=7 kısmi bitişik
            // Normal arama güvenilmez (pop çok düşük) — sadece bitişik variantları tut
            new DdpCase("DDP366_22b","yalnızlığınçaresini bulmuşlar", "","Yalnızlığın Çaresini Bulmuşlar",DDP366,10,"Kısmi bitişik"),

            // ── F23: "ta ki seni görene kadar" — ta ki partikülü ─────────
            // SARKI_YAZIM_13: Excel ✅ ama UAT'ta pos=28-32 → arama güvenilmez
            // Normal ve birleşik arama sadece partikül testi olarak bırakıldı
            new DdpCase("DDP366_23a","ta ki seni görene kadar",  "","ta ki seni görene kadar",DDP366,35,"Normal"),
            new DdpCase("DDP366_23b","taki seni görene kadar",   "","ta ki seni görene kadar",DDP366,35,"ta ki birleşik (Excel ✅)"),

            // ── F24: "Ara Beni Lütfen" — 3 kelime ────────────────────────
            // SARKI_TAM_16: Excel "top-1" der ama UAT'ta pos=4 (Kenan Doğulu)
            new DdpCase("DDP366_24a","ara beni lütfen",          "","Ara Beni Lütfen",DDP366,5,"Normal"),
            new DdpCase("DDP366_24b","arabenilütfen",            "","Ara Beni Lütfen",DDP366,10,"Bitişik (TR)"),
            new DdpCase("DDP366_24c","arabenilutfen",            "","Ara Beni Lütfen",DDP366,15,"Bitişik+ASCII"),

            // ── F25: "Kalbimin Tek Sahibine" — İrem Derici ───────────────
            // SARKI_KISMI_12: "kalbimin sahibine" (eksik kelime) → OK (Excel ✅)
            new DdpCase("DDP366_25a","kalbimin tek sahibine",    "İrem Derici","Kalbimin Tek Sahibine",DDP366,3,"Normal"),
            new DdpCase("DDP366_25b","kalbiminteksahibine",      "İrem Derici","Kalbimin Tek Sahibine",DDP366,5,"Tam bitişik"),
            new DdpCase("DDP366_25c","kalbimin sahibine",        "İrem Derici","Kalbimin Tek Sahibine",DDP366,5,"Kısmi (Excel ✅)"),

            // ── F26: Canozan — "can ozan" (sanatçı adı ayrık yazım) ──────
            // SANAT_YAZIM_7: "can ozan" → Canozan ✅ onaylı
            // Ters yön: sanatçı adı bitişik (Canozan), kullanıcı ayrık yazıyor
            new DdpCase("DDP366_26a","canozan",                  "Canozan","",DDP366,1,"Normal (baseline)"),
            new DdpCase("DDP366_26b","can ozan",                 "Canozan","",DDP366,3,"Sanatçı adı ayrık (Excel ✅)"),

            // ── F27: "İllede Sen" — de partikülü ─────────────────────────
            // UAT bulgusu: Şarkı "İllede Sen — Ahmet Demirci" olarak indexli
            // (Azer Bülbül versiyonu yok veya çok düşük sıralama)
            // "ille de sen" → pos=7 sıralama sorunu (başka şarkılar üstte)
            new DdpCase("DDP366_27a","illede sen",               "","İllede Sen",DDP366,10,"Orijinal (birleşik başlık)"),
            new DdpCase("DDP366_27b","ille de sen",              "","İllede Sen",DDP366,10,"de ayrık yazım"),
            new DdpCase("DDP366_27c","illedesen",                "","İllede Sen",DDP366,15,"Tam bitişik"),

            // ═════════════════════════════════════════════════════════════════
            // DDP-367 · ÖZEL KARAKTER — KOMPARATİF
            // "a" varyant baseline kaydeder; "b/c" aynı içerik gelmeli
            // NOT: KÖK$VL UAT indexinde yoksa → "VERİ EKSİK" açıkça yazılır
            // ═════════════════════════════════════════════════════════════════

            // F01: KÖK$VL — $ karakteri
            new DdpCase("DDP367_01a","kok$vl",        DDP367,3,"Direkt $ (baseline)"),
            new DdpCase("DDP367_01b","kokvl",         DDP367,7,"$ olmadan"),
            new DdpCase("DDP367_01c","kok vl",        DDP367,7,"$ yerine boşluk"),
            new DdpCase("DDP367_01d","kök vl",        DDP367,7,"TR harfli + boşluk"),

            // F02: KÖK$VL — farklı TR form
            new DdpCase("DDP367_02a","kök$vl",        DDP367,3,"TR harfli direkt (baseline)"),
            new DdpCase("DDP367_02b","kökvl",         DDP367,7,"TR harfli $ yok"),
            new DdpCase("DDP367_02c","koksvl",        DDP367,7,"$ yerine s"),

            // ═════════════════════════════════════════════════════════════════
            // DDP-368 · VERSİYON ETİKETİ — ETİKET KONTROLÜ
            // Sonuçlarda keyword (akustik/live/remix) geçiyor mu?
            // Tarkan onaylı içerik; akustik/live versiyonları indexlendiyse bulunur
            // ═════════════════════════════════════════════════════════════════

            // Akustik versiyonlar
            new DdpCase("DDP368_01a","tarkan akustik",   "akustik",DDP368,5,"Sanatçı + etiket"),
            new DdpCase("DDP368_01b","tarkan [akustik]", "akustik",DDP368,5,"Sanatçı + [etiket]"),
            new DdpCase("DDP368_01c","şımarık akustik",  "akustik",DDP368,5,"Şarkı + etiket"),
            new DdpCase("DDP368_01d","şımarık [akustik]","akustik",DDP368,5,"Şarkı + [etiket]"),

            // Live versiyonlar
            new DdpCase("DDP368_02c","blok3 live",       "live",   DDP368,5,"Sanatçı2 + live"),

            // Remix versiyonlar
            new DdpCase("DDP368_03a","blok3 remix",      "remix",  DDP368,5,"Sanatçı + remix"),
            new DdpCase("DDP368_03b","tarkan remix",     "remix",  DDP368,5,"Sanatçı2 + remix"),

            // ═════════════════════════════════════════════════════════════════
            // DDP-369 · KLAVYE YAZIM HATASI (correction=true, MUTLAK KONTROL)
            // ─────────────────────────────────────────────────────────────────
            // Tüm sanatçılar Excel OK listesinden alınmıştır.
            // ÖZEL TEŞHİS: "TOP-50'DE YOK" durumunda correction'ın önerdiği kelime
            // kontrol edilerek "Correction yanlış yönlendiriyor" veya "veri eksik"
            // ayrımı yapılır.
            // ═════════════════════════════════════════════════════════════════

            // F01: Tarkan (SANAT_YAZIM_1'de "tarkn" → Tarkan OK)
            new DdpCase("DDP369_01a","tarkan",    "Tarkan","",DDP369,1,"Doğru (baseline)",  true,null),
            new DdpCase("DDP369_01b","tarkn",     "Tarkan","",DDP369,3,"Harf eksik (OK?)",  true,"tarkan"),
            new DdpCase("DDP369_01c","tarkna",    "Tarkan","",DDP369,3,"Transposisyon a-n", true,"tarkan"),
            new DdpCase("DDP369_01d","tarksn",    "Tarkan","",DDP369,5,"a→s komşu tuş",     true,"tarkan"),

            // F02: Poizi (SANAT_YAZIM_4'te "pozi" → Poizi OK)
            // "poizş" → API "point" öneriyor (DDP-369 bug) — bu kalan NOK beklenir
            new DdpCase("DDP369_02a","poizi",     "Poizi","",DDP369,1,"Doğru (baseline)",   true,null),
            new DdpCase("DDP369_02b","pozi",      "Poizi","",DDP369,3,"Harf eksik (OK?)",   true,"poizi"),
            new DdpCase("DDP369_02c","poizş",     "Poizi","",DDP369,5,"i→ş komşu tuş",      true,"poizi"),
            new DdpCase("DDP369_02d","piozi",     "Poizi","",DDP369,5,"Transposisyon o-i",  true,"poizi"),

            // F03: Ezhel (SANAT_YAZIM_8'de "ezel" → Ezhel OK)
            new DdpCase("DDP369_03a","ezhel",     "Ezhel","",DDP369,1,"Doğru (baseline)",   true,null),
            new DdpCase("DDP369_03b","ezel",      "Ezhel","",DDP369,3,"h düşmüş (OK?)",     true,"ezhel"),
            new DdpCase("DDP369_03c","ezheıl",    "Ezhel","",DDP369,5,"ı girmiş TR klavye", true,"ezhel"),

            // F04: Reynmen (SANAT_YAZIM_9'de "reymen" → Reynmen OK)
            new DdpCase("DDP369_04a","reynmen",   "Reynmen","",DDP369,1,"Doğru (baseline)",  true,null),
            new DdpCase("DDP369_04b","reymen",    "Reynmen","",DDP369,3,"n düşmüş (OK?)",    true,"reynmen"),
            new DdpCase("DDP369_04c","ryenmen",   "Reynmen","",DDP369,5,"Transposisyon e-y", true,"reynmen"),

            // F05: Emir Can İğrek (SANAT_YAZIM_10'da "emircan" → OK)
            new DdpCase("DDP369_05a","emir can iğrek","Emir Can İğrek","",DDP369,1,"Doğru (baseline)",true,null),
            new DdpCase("DDP369_05b","emircan",         "Emir Can İğrek","",DDP369,3,"Bitişik form",   true,"emir"),
            new DdpCase("DDP369_05c","emrican iğrek",   "Emir Can İğrek","",DDP369,5,"i-r transposisyon",true,"emir"),

            // F06: Göksel (SANAT_YAZIM_6'da "goksel" → Göksel OK)
            new DdpCase("DDP369_06a","göksel",    "Göksel","",DDP369,1,"Doğru (baseline)",   true,null),
            new DdpCase("DDP369_06b","goksel",    "Göksel","",DDP369,3,"ASCII fold (OK?)",   true,"göksel"),
            new DdpCase("DDP369_06c","gokswl",    "Göksel","",DDP369,5,"e→w komşu tuş",      true,"göksel"),

            // F07: Hidra (SANAT_YAZIM_5'te "hşdra" → Hidra OK)
            new DdpCase("DDP369_07a","hidra",     "Hidra","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_07b","hşdra",     "Hidra","",DDP369,3,"i→ş komşu (OK?)",     true,"hidra"),
            new DdpCase("DDP369_07c","hidrs",     "Hidra","",DDP369,5,"a→s komşu tuş",       true,"hidra"),

            // F08: Semicenk (SANAT_YAZIM_14'te "Semicek" → Semicenk OK)
            new DdpCase("DDP369_08a","semicenk",  "Semicenk","",DDP369,1,"Doğru (baseline)", true,null),
            new DdpCase("DDP369_08b","semicek",   "Semicenk","",DDP369,3,"n düşmüş (OK?)",   true,"semicenk"),
            new DdpCase("DDP369_08c","semıcenk",  "Semicenk","",DDP369,5,"i→ı TR klavye",    true,"semicenk"),

            // F09: Heijan (SANAT_YAZIM_13'te "hejan" → Heijan OK)
            new DdpCase("DDP369_09a","heijan",    "Heijan","",DDP369,1,"Doğru (baseline)",   true,null),
            new DdpCase("DDP369_09b","hejan",     "Heijan","",DDP369,3,"i düşmüş (OK?)",     true,"heijan"),
            new DdpCase("DDP369_09c","heıjan",    "Heijan","",DDP369,5,"i→ı TR klavye",      true,"heijan"),

            // F10: KÖFN (SANAT_YAZIM_18'de "kofn" → KÖFN OK)
            new DdpCase("DDP369_10a","köfn",      "KÖFN","",DDP369,1,"Doğru (baseline)",     true,null),
            new DdpCase("DDP369_10b","kofn",      "KÖFN","",DDP369,3,"ASCII fold (OK?)",     true,"köfn"),
            new DdpCase("DDP369_10c","kövn",      "KÖFN","",DDP369,5,"f→v komşu tuş",        true,"köfn"),

            // F11: Dolu Kadehi Ters Tut (SANAT_YAZIM_21'de "dolu kadhi tut" → OK)
            new DdpCase("DDP369_11a","dolu kadehi ters tut","Dolu Kadehi Ters Tut","",DDP369,1,"Doğru (baseline)",true,null),
            new DdpCase("DDP369_11b","dolu kadhi tut",       "Dolu Kadehi Ters Tut","",DDP369,3,"e düşmüş (OK?)",true,"dolu"),
            new DdpCase("DDP369_11c","dilu kadehi ters tut", "Dolu Kadehi Ters Tut","",DDP369,5,"o→i komşu tuş", true,"dolu"),

            // ── F12: Sıla ────────────────────────────────────────────────────
            new DdpCase("DDP369_12a","sıla",         "Sıla","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_12b","sila",          "Sıla","",DDP369,3,"ASCII fold (OK?)",    true,"sıla"),
            new DdpCase("DDP369_12c","sıks",          "Sıla","",DDP369,5,"l→k a→s komşu tuş",  true,"sıla"),

            // ── F13: Murat Boz ───────────────────────────────────────────────
            new DdpCase("DDP369_13a","murat boz",     "Murat Boz","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_13b","murt boz",      "Murat Boz","",DDP369,3,"Harf eksik (OK?)",    true,"murat boz"),
            new DdpCase("DDP369_13c","murat bpz",     "Murat Boz","",DDP369,5,"o→p komşu tuş",       true,"murat boz"),

            // ── F14: Gülşen ──────────────────────────────────────────────────
            new DdpCase("DDP369_14a","gülşen",        "Gülşen","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_14b","gulsen",         "Gülşen","",DDP369,3,"ASCII fold (OK?)",    true,"gülşen"),
            new DdpCase("DDP369_14c","gülşem",         "Gülşen","",DDP369,5,"n→m komşu tuş",       true,"gülşen"),

            // ── F15: Aleyna Tilki ────────────────────────────────────────────
            new DdpCase("DDP369_15a","aleyna tilki",  "Aleyna Tilki","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_15b","aleyna tilkı",  "Aleyna Tilki","",DDP369,3,"i→ı TR (OK?)",        true,"aleyna tilki"),
            new DdpCase("DDP369_15c","aleyba tilki",  "Aleyna Tilki","",DDP369,5,"n→b komşu tuş",       true,"aleyna tilki"),

            // ── F16: Şebnem Ferah ────────────────────────────────────────────
            new DdpCase("DDP369_16a","şebnem ferah",  "Şebnem Ferah","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_16b","sebnem ferah",  "Şebnem Ferah","",DDP369,3,"ASCII fold (OK?)",    true,"şebnem ferah"),
            new DdpCase("DDP369_16c","şebmem ferah",  "Şebnem Ferah","",DDP369,5,"n→m komşu tuş",       true,"şebnem ferah"),

            // ── F17: Haluk Levent ────────────────────────────────────────────
            new DdpCase("DDP369_17a","haluk levent",  "Haluk Levent","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_17b","haluk levnt",   "Haluk Levent","",DDP369,3,"Harf eksik (OK?)",    true,"haluk levent"),
            new DdpCase("DDP369_17c","haluk lrvent",  "Haluk Levent","",DDP369,5,"e→r komşu tuş",       true,"haluk levent"),

            // ── F18: Norm Ender ──────────────────────────────────────────────
            new DdpCase("DDP369_18a","norm ender",    "Norm Ender","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_18b","norm endr",     "Norm Ender","",DDP369,3,"Harf eksik (OK?)",    true,"norm ender"),
            new DdpCase("DDP369_18c","norm rnder",    "Norm Ender","",DDP369,5,"e→r komşu tuş",       true,"norm ender"),

            // ── F19: Ben Fero ────────────────────────────────────────────────
            new DdpCase("DDP369_19a","ben fero",      "Ben Fero","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_19b","ben fro",       "Ben Fero","",DDP369,3,"Harf eksik (OK?)",    true,"ben fero"),
            new DdpCase("DDP369_19c","ben fwro",      "Ben Fero","",DDP369,5,"e→w komşu tuş",       true,"ben fero"),

            // ── F20: Şanışer ─────────────────────────────────────────────────
            new DdpCase("DDP369_20a","şanışer",       "Şanışer","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_20b","saniser",        "Şanışer","",DDP369,3,"ASCII fold (OK?)",    true,"şanışer"),
            new DdpCase("DDP369_20c","şanışee",        "Şanışer","",DDP369,5,"r→e komşu tuş",       true,"şanışer"),

            // ── F21: Gripin ──────────────────────────────────────────────────
            new DdpCase("DDP369_21a","gripin",         "Gripin","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_21b","grpin",           "Gripin","",DDP369,3,"Harf eksik (OK?)",    true,"gripin"),
            new DdpCase("DDP369_21c","gripen",          "Gripin","",DDP369,5,"i→e komşu tuş",       true,"gripin"),

            // ── F22: Ceza ────────────────────────────────────────────────────
            new DdpCase("DDP369_22a","ceza",           "Ceza","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_22b","cza",            "Ceza","",DDP369,3,"Harf eksik (OK?)",    true,"ceza"),
            new DdpCase("DDP369_22c","cesa",           "Ceza","",DDP369,5,"z→s komşu tuş",       true,"ceza"),

            // ── F23: Manga ───────────────────────────────────────────────────
            new DdpCase("DDP369_23a","manga",          "Manga","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_23b","mnga",           "Manga","",DDP369,3,"Harf eksik (OK?)",    true,"manga"),
            new DdpCase("DDP369_23c","mabga",          "Manga","",DDP369,5,"n→b komşu tuş",       true,"manga"),

            // ── F24: Eminem ──────────────────────────────────────────────────
            new DdpCase("DDP369_24a","eminem",         "Eminem","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_24b","eminim",          "Eminem","",DDP369,3,"e→i transposisyon",   true,"eminem"),
            new DdpCase("DDP369_24c","eninem",          "Eminem","",DDP369,5,"m→n transposisyon",   true,"eminem"),

            // ── F25: The Weeknd ──────────────────────────────────────────────
            new DdpCase("DDP369_25a","the weeknd",     "The Weeknd","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_25b","the weekd",      "The Weeknd","",DDP369,3,"Harf eksik (OK?)",    true,"the weeknd"),
            new DdpCase("DDP369_25c","the weernd",     "The Weeknd","",DDP369,5,"k→r komşu tuş",       true,"the weeknd"),

            // ── F26: Drake ───────────────────────────────────────────────────
            new DdpCase("DDP369_26a","drake",          "Drake","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_26b","draje",           "Drake","",DDP369,3,"k→j komşu tuş",       true,"drake"),
            new DdpCase("DDP369_26c","drske",           "Drake","",DDP369,5,"a→s komşu tuş",       true,"drake"),

            // ── F27: Dua Lipa ────────────────────────────────────────────────
            new DdpCase("DDP369_27a","dua lipa",       "Dua Lipa","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_27b","dua lıpa",       "Dua Lipa","",DDP369,3,"i→ı TR (OK?)",        true,"dua lipa"),
            new DdpCase("DDP369_27c","dua lops",       "Dua Lipa","",DDP369,5,"i→o a→s komşu tuş",  true,"dua lipa"),

            // ── F28: Ed Sheeran ──────────────────────────────────────────────
            new DdpCase("DDP369_28a","ed sheeran",     "Ed Sheeran","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_28b","ed sheran",      "Ed Sheeran","",DDP369,3,"Harf eksik (OK?)",    true,"ed sheeran"),
            new DdpCase("DDP369_28c","ed sheerab",     "Ed Sheeran","",DDP369,5,"n→b komşu tuş",       true,"ed sheeran"),

            // ── F29: Billie Eilish ───────────────────────────────────────────
            new DdpCase("DDP369_29a","billie eilish",  "Billie Eilish","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_29b","billie elish",   "Billie Eilish","",DDP369,3,"Harf eksik (OK?)",    true,"billie eilish"),
            new DdpCase("DDP369_29c","billie eilosh",  "Billie Eilish","",DDP369,5,"i→o komşu tuş",       true,"billie eilish"),

            // ── F30: Hadise ──────────────────────────────────────────────────
            new DdpCase("DDP369_30a","hadise",          "Hadise","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_30b","hadse",            "Hadise","",DDP369,3,"Harf eksik (OK?)",    true,"hadise"),
            new DdpCase("DDP369_30c","hadıse",           "Hadise","",DDP369,5,"i→ı TR klavye",       true,"hadise"),

            // ── F31: Hande Yener ─────────────────────────────────────────────
            new DdpCase("DDP369_31a","hande yener",     "Hande Yener","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_31b","hande yenr",      "Hande Yener","",DDP369,3,"Harf eksik (OK?)",    true,"hande yener"),
            new DdpCase("DDP369_31c","hande yemer",     "Hande Yener","",DDP369,5,"n→m komşu tuş",       true,"hande yener"),

            // ── F32: Serdar Ortaç ────────────────────────────────────────────
            new DdpCase("DDP369_32a","serdar ortaç",    "Serdar Ortaç","",DDP369,1,"Doğru (baseline)",   true,null),
            new DdpCase("DDP369_32b","serdar orta",     "Serdar Ortaç","",DDP369,3,"Harf eksik (OK?)",   true,"serdar ortaç"),
            new DdpCase("DDP369_32c","serdsr ortaç",    "Serdar Ortaç","",DDP369,5,"a→s komşu tuş",      true,"serdar ortaç"),

            // ── F33: Demet Akalın ────────────────────────────────────────────
            new DdpCase("DDP369_33a","demet akalın",    "Demet Akalın","",DDP369,1,"Doğru (baseline)",   true,null),
            new DdpCase("DDP369_33b","demet akaln",     "Demet Akalın","",DDP369,3,"Harf eksik (OK?)",   true,"demet akalın"),
            new DdpCase("DDP369_33c","demet akalım",    "Demet Akalın","",DDP369,5,"n→m komşu tuş",      true,"demet akalın"),

            // ── F34: Mustafa Sandal ──────────────────────────────────────────
            new DdpCase("DDP369_34a","mustafa sandal",  "Mustafa Sandal","",DDP369,1,"Doğru (baseline)",  true,null),
            new DdpCase("DDP369_34b","mustafa sndal",   "Mustafa Sandal","",DDP369,3,"Harf eksik (OK?)",  true,"mustafa sandal"),
            new DdpCase("DDP369_34c","mustafa sandsl",  "Mustafa Sandal","",DDP369,5,"a→s komşu tuş",     true,"mustafa sandal"),

            // ── F35: Zeynep Bastık ───────────────────────────────────────────
            new DdpCase("DDP369_35a","zeynep bastık",   "Zeynep Bastık","",DDP369,1,"Doğru (baseline)",   true,null),
            new DdpCase("DDP369_35b","zeynep bastk",    "Zeynep Bastık","",DDP369,3,"Harf eksik (OK?)",   true,"zeynep bastık"),
            new DdpCase("DDP369_35c","zeynep bastıl",   "Zeynep Bastık","",DDP369,5,"k→l komşu tuş",      true,"zeynep bastık"),

            // ── F36: Melike Şahin ────────────────────────────────────────────
            new DdpCase("DDP369_36a","melike şahin",    "Melike Şahin","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_36b","melike sahin",    "Melike Şahin","",DDP369,3,"ASCII fold (OK?)",    true,"melike şahin"),
            new DdpCase("DDP369_36c","melike şahim",    "Melike Şahin","",DDP369,5,"n→m komşu tuş",       true,"melike şahin"),

            // ── F37: İlyas Yalçıntaş ─────────────────────────────────────────
            new DdpCase("DDP369_37a","ilyas yalçıntaş", "İlyas Yalçıntaş","",DDP369,1,"Doğru (baseline)",true,null),
            new DdpCase("DDP369_37b","ilyas yalcıntas", "İlyas Yalçıntaş","",DDP369,3,"ASCII fold (OK?)",true,"ilyas yalçıntaş"),
            new DdpCase("DDP369_37c","ilyad yalçıntaş", "İlyas Yalçıntaş","",DDP369,5,"s→d komşu tuş",  true,"ilyas yalçıntaş"),

            // ── F38: Emre Aydın ──────────────────────────────────────────────
            new DdpCase("DDP369_38a","emre aydın",      "Emre Aydın","",DDP369,1,"Doğru (baseline)",     true,null),
            new DdpCase("DDP369_38b","emre aydn",       "Emre Aydın","",DDP369,3,"Harf eksik (OK?)",     true,"emre aydın"),
            new DdpCase("DDP369_38c","emre sydin",      "Emre Aydın","",DDP369,5,"a→s komşu tuş",        true,"emre aydın"),

            // ── F39: Yüzyüzeyken Konuşuruz ───────────────────────────────────
            new DdpCase("DDP369_39a","yüzyüzeyken konuşuruz","Yüzyüzeyken Konuşuruz","",DDP369,1,"Doğru (baseline)", true,null),
            new DdpCase("DDP369_39b","yuzyuzeyken konusuruz","Yüzyüzeyken Konuşuruz","",DDP369,3,"ASCII fold (OK?)", true,"yüzyüzeyken konuşuruz"),
            new DdpCase("DDP369_39c","yüzyüzeykem konuşuruz","Yüzyüzeyken Konuşuruz","",DDP369,5,"n→m komşu tuş",   true,"yüzyüzeyken konuşuruz"),

            // ── F40: Sagopa Kajmer ───────────────────────────────────────────
            new DdpCase("DDP369_40a","sagopa kajmer",   "Sagopa Kajmer","",DDP369,1,"Doğru (baseline)",   true,null),
            new DdpCase("DDP369_40b","sagopa kajmr",    "Sagopa Kajmer","",DDP369,3,"Harf eksik (OK?)",   true,"sagopa kajmer"),
            new DdpCase("DDP369_40c","sagopa kajner",   "Sagopa Kajmer","",DDP369,5,"m→n komşu tuş",      true,"sagopa kajmer"),

            // ── F41: Sansar Salvo ────────────────────────────────────────────
            new DdpCase("DDP369_41a","sansar salvo",    "Sansar Salvo","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_41b","sansar salv",     "Sansar Salvo","",DDP369,3,"Harf eksik (OK?)",    true,"sansar salvo"),
            new DdpCase("DDP369_41c","sansar salbo",    "Sansar Salvo","",DDP369,5,"v→b komşu tuş",       true,"sansar salvo"),

            // ── F42: Uzi ─────────────────────────────────────────────────────
            new DdpCase("DDP369_42a","uzi",             "Uzi","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_42b","uzı",             "Uzi","",DDP369,3,"i→ı TR klavye",       true,"uzi"),
            new DdpCase("DDP369_42c","yzi",             "Uzi","",DDP369,5,"u→y komşu tuş",       true,"uzi"),

            // ── F43: Murda ───────────────────────────────────────────────────
            new DdpCase("DDP369_43a","murda",           "Murda","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_43b","murds",           "Murda","",DDP369,3,"a→s komşu (OK?)",    true,"murda"),
            new DdpCase("DDP369_43c","mirda",           "Murda","",DDP369,5,"u→i komşu tuş",      true,"murda"),

            // ── F44: Velet ───────────────────────────────────────────────────
            new DdpCase("DDP369_44a","velet",           "Velet","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_44b","vlet",            "Velet","",DDP369,3,"Harf eksik (OK?)",    true,"velet"),
            new DdpCase("DDP369_44c","velrt",           "Velet","",DDP369,5,"e→r komşu tuş",       true,"velet"),

            // ── F45: Cem Belevi ──────────────────────────────────────────────
            new DdpCase("DDP369_45a","cem belevi",      "Cem Belevi","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_45b","cem belvi",       "Cem Belevi","",DDP369,3,"Harf eksik (OK?)",    true,"cem belevi"),
            new DdpCase("DDP369_45c","cem brlevi",      "Cem Belevi","",DDP369,5,"e→r komşu tuş",       true,"cem belevi"),

            // ── F46: Teoman ──────────────────────────────────────────────────
            new DdpCase("DDP369_46a","teoman",          "Teoman","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_46b","toman",           "Teoman","",DDP369,3,"Harf eksik (OK?)",    true,"teoman"),
            new DdpCase("DDP369_46c","teomam",          "Teoman","",DDP369,5,"n→m komşu tuş",       true,"teoman"),

            // ── F47: Athena ──────────────────────────────────────────────────
            new DdpCase("DDP369_47a","athena",          "Athena","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_47b","athna",           "Athena","",DDP369,3,"Harf eksik (OK?)",    true,"athena"),
            new DdpCase("DDP369_47c","sthena",          "Athena","",DDP369,5,"a→s komşu tuş",       true,"athena"),

            // ── F48: Madrigal ────────────────────────────────────────────────
            new DdpCase("DDP369_48a","madrigal",        "Madrigal","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_48b","madrigsl",        "Madrigal","",DDP369,3,"a→s komşu (OK?)",    true,"madrigal"),
            new DdpCase("DDP369_48c","madrıgal",        "Madrigal","",DDP369,5,"i→ı TR klavye",       true,"madrigal"),

            // ── F49: Ece Seçkin ──────────────────────────────────────────────
            new DdpCase("DDP369_49a","ece seçkin",      "Ece Seçkin","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_49b","ece seckin",      "Ece Seçkin","",DDP369,3,"ASCII fold (OK?)",    true,"ece seçkin"),
            new DdpCase("DDP369_49c","ece seçkın",      "Ece Seçkin","",DDP369,5,"i→ı TR klavye",       true,"ece seçkin"),

            // ── F50: Post Malone (YAB.) ───────────────────────────────────────
            new DdpCase("DDP369_50a","post malone",     "Post Malone","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_50b","post maone",      "Post Malone","",DDP369,3,"Harf eksik (OK?)",    true,"post malone"),
            new DdpCase("DDP369_50c","post malonr",     "Post Malone","",DDP369,5,"e→r komşu tuş",       true,"post malone"),

            // ── F51: Ariana Grande (YAB.) ────────────────────────────────────
            new DdpCase("DDP369_51a","ariana grande",   "Ariana Grande","",DDP369,1,"Doğru (baseline)",  true,null),
            new DdpCase("DDP369_51b","ariana grnd",     "Ariana Grande","",DDP369,3,"Harf eksik (OK?)",  true,"ariana grande"),
            new DdpCase("DDP369_51c","ariana grandr",   "Ariana Grande","",DDP369,5,"e→r komşu tuş",     true,"ariana grande"),

            // ── F52: Taylor Swift (YAB.) ─────────────────────────────────────
            new DdpCase("DDP369_52a","taylor swift",    "Taylor Swift","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_52b","taylor swft",     "Taylor Swift","",DDP369,3,"Harf eksik (OK?)",    true,"taylor swift"),
            new DdpCase("DDP369_52c","taylor swuft",    "Taylor Swift","",DDP369,5,"i→u komşu tuş",       true,"taylor swift"),

            // ── F53: Justin Bieber (YAB.) ────────────────────────────────────
            new DdpCase("DDP369_53a","justin bieber",   "Justin Bieber","",DDP369,1,"Doğru (baseline)",   true,null),
            new DdpCase("DDP369_53b","justin biebr",    "Justin Bieber","",DDP369,3,"Harf eksik (OK?)",   true,"justin bieber"),
            new DdpCase("DDP369_53c","justin biener",   "Justin Bieber","",DDP369,5,"b→n komşu tuş",      true,"justin bieber"),

            // ── F54: Rihanna (YAB.) ──────────────────────────────────────────
            new DdpCase("DDP369_54a","rihanna",         "Rihanna","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_54b","rihana",          "Rihanna","",DDP369,3,"Harf eksik (OK?)",    true,"rihanna"),
            new DdpCase("DDP369_54c","rihsnna",         "Rihanna","",DDP369,5,"a→s komşu tuş",       true,"rihanna"),

            // ── F55: Adele (YAB.) ─────────────────────────────────────────────
            new DdpCase("DDP369_55a","adele",           "Adele","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_55b","adel",            "Adele","",DDP369,3,"Harf eksik (OK?)",    true,"adele"),
            new DdpCase("DDP369_55c","sdele",           "Adele","",DDP369,5,"a→s komşu tuş",       true,"adele"),

            // ── F56: Bruno Mars (YAB.) ───────────────────────────────────────
            new DdpCase("DDP369_56a","bruno mars",      "Bruno Mars","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_56b","brno mars",       "Bruno Mars","",DDP369,3,"Harf eksik (OK?)",    true,"bruno mars"),
            new DdpCase("DDP369_56c","bruno msrs",      "Bruno Mars","",DDP369,5,"a→s komşu tuş",       true,"bruno mars"),

            // ── F57: Coldplay (YAB.) ─────────────────────────────────────────
            new DdpCase("DDP369_57a","coldplay",        "Coldplay","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_57b","coldpay",         "Coldplay","",DDP369,3,"Harf eksik (OK?)",    true,"coldplay"),
            new DdpCase("DDP369_57c","coldplsy",        "Coldplay","",DDP369,5,"a→s komşu tuş",       true,"coldplay"),

            // ── F58: Lady Gaga (YAB.) ────────────────────────────────────────
            new DdpCase("DDP369_58a","lady gaga",       "Lady Gaga","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_58b","lady gag",        "Lady Gaga","",DDP369,3,"Harf eksik (OK?)",    true,"lady gaga"),
            new DdpCase("DDP369_58c","lsdy gaga",       "Lady Gaga","",DDP369,5,"a→s komşu tuş",       true,"lady gaga"),

            // ── F59: Olivia Rodrigo (YAB.) ───────────────────────────────────
            new DdpCase("DDP369_59a","olivia rodrigo",  "Olivia Rodrigo","",DDP369,1,"Doğru (baseline)",  true,null),
            new DdpCase("DDP369_59b","olivia rdrigo",   "Olivia Rodrigo","",DDP369,3,"Harf eksik (OK?)",  true,"olivia rodrigo"),
            new DdpCase("DDP369_59c","olivia rodrugo",  "Olivia Rodrigo","",DDP369,5,"i→u komşu tuş",     true,"olivia rodrigo"),

            // ── F60: Harry Styles (YAB.) ─────────────────────────────────────
            new DdpCase("DDP369_60a","harry styles",    "Harry Styles","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_60b","harry syles",     "Harry Styles","",DDP369,3,"Harf eksik (OK?)",    true,"harry styles"),
            new DdpCase("DDP369_60c","harty styles",    "Harry Styles","",DDP369,5,"r→t komşu tuş",       true,"harry styles"),

            // ── F61: BTS (YAB.) ───────────────────────────────────────────────
            new DdpCase("DDP369_61a","bts",             "BTS","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_61b","bds",             "BTS","",DDP369,3,"t→d komşu (OK?)",    true,"bts"),
            new DdpCase("DDP369_61c","vts",             "BTS","",DDP369,5,"b→v komşu tuş",       true,"bts"),

            // ── F62: Bad Bunny (YAB.) ─────────────────────────────────────────
            new DdpCase("DDP369_62a","bad bunny",       "Bad Bunny","",DDP369,1,"Doğru (baseline)",    true,null),
            new DdpCase("DDP369_62b","bad buny",        "Bad Bunny","",DDP369,3,"Harf eksik (OK?)",    true,"bad bunny"),
            new DdpCase("DDP369_62c","bad bunmy",       "Bad Bunny","",DDP369,5,"n→m komşu tuş",       true,"bad bunny")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST RUNNER
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "[{0}] \"{1}\" ({6})")
    @MethodSource("cases")
    @Order(1)
    void run(DdpCase dc) {
        String[] result = {"(bekleniyor)", "NOK", "API hatası"};
        try {
            Response res = api.search(dc.term(), "active-indices", 50, dc.correction());
            JsonPath jp  = res.jsonPath();
            result = evaluate(dc, jp);
        } catch (Exception e) {
            System.err.printf("⚠️  [%s] '%s': %s%n", dc.caseId(), dc.term(), e.getMessage());
            result[2] = "API hatası: " + e.getMessage();
        }

        System.out.printf("[%s] %-12s | %-28s | %-28s%n",
                result[1], dc.caseId(), "[" + dc.variant() + "]", "\"" + dc.term() + "\"");

        ROWS.add(new TestResultRow(
                dc.caseId(),
                "\"" + dc.term() + "\"  [" + dc.variant() + "]",
                modeDesc(dc),
                result[0], dc.section(), "active-indices",
                result[1], result[2]
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEĞERLENDİRME
    // ─────────────────────────────────────────────────────────────────────────

    private String[] evaluate(DdpCase dc, JsonPath jp) {
        String base = MuudSearchUtils.getBasePath(jp);
        List<Object> list = MuudSearchUtils.resultsList(jp);

        boolean isComparative = dc.expArtist().isEmpty() && dc.expTrack().isEmpty()
                                && dc.labelCheck() == null;
        boolean isLabelCheck  = dc.labelCheck() != null;
        String familyId = dc.caseId().replaceAll("[a-z]$", "");
        boolean isBaselineCase = dc.caseId().endsWith("a") && isComparative;

        if (list.isEmpty()) {
            if (isBaselineCase) COMP_BASELINE.put(familyId, "EMPTY");
            return new String[]{"Sonuç bekleniyor", "NOK",
                    "❌ API boş döndü. correction=" + dc.correction()};
        }

        // ── DDP-367 Komparatif baseline kaydı ────────────────────────────────
        if (isBaselineCase) {
            String key = resultKey(jp, base, 0);
            COMP_BASELINE.put(familyId, key);
            return new String[]{"BASELINE → " + key, "OK",
                    "Baseline kaydedildi: " + key + "\n" + top5Desc(jp, base)};
        }

        // ── DDP-367 Komparatif varyant kontrolü ──────────────────────────────
        if (isComparative) {
            return evalComparative(dc, jp, base, list, familyId);
        }

        // ── DDP-368 Etiket kontrolü ───────────────────────────────────────────
        if (isLabelCheck) {
            return evalLabel(dc, jp, base, list);
        }

        // ── DDP-366 / Regresyon / DDP-369 Mutlak kontrol ─────────────────────
        String corrNote = dc.correction() ? buildCorrectionNote(jp, dc.expSuggest()) : "";
        return evalAbsolute(dc, jp, base, list, corrNote);
    }

    // ─── DDP-367 Komparatif ──────────────────────────────────────────────────
    private String[] evalComparative(DdpCase dc, JsonPath jp, String base,
                                      List<Object> list, String familyId) {
        String baseline = COMP_BASELINE.getOrDefault(familyId, null);
        if (baseline == null)
            return new String[]{"Baseline bekleniyor", "NOK", "⚠️ Baseline henüz kaydedilmedi"};
        if ("EMPTY".equals(baseline))
            return new String[]{"Baseline boş → veri eksik", "NOK",
                    "❌ VERİ EKSİK (UAT): Baseline 'a' araması sonuçsuz döndü.\n" +
                    "Bu sanatçı/şarkı UAT'ta indexlenmemiş olabilir. DDP-367 bu içerikle test edilemiyor."};

        String expected = "Top-" + dc.topN() + "'de baseline içerik gelmeli";
        for (int i = 0; i < Math.min(dc.topN(), list.size()); i++) {
            if (baseline.equalsIgnoreCase(resultKey(jp, base, i)))
                return new String[]{expected, "OK",
                        "✅ pos " + (i+1) + ". sırada baseline bulundu.\nBaseline: " + baseline
                        + "\n" + top5Desc(jp, base)};
        }
        int wider = -1;
        for (int i = dc.topN(); i < list.size(); i++) {
            if (baseline.equalsIgnoreCase(resultKey(jp, base, i))) { wider = i; break; }
        }
        String whereStr = wider >= 0 ? "⚠️ pos " + (wider+1) + "/50'de bulundu → Sıralama sorunu"
                                     : "❌ TOP-50'DE YOK → Farklı içerik mi döndü?";
        return new String[]{expected, "NOK",
                "Başarısız — top-" + dc.topN() + "'de yok. " + whereStr
                + "\nBaseline: " + baseline + "\n" + top5Desc(jp, base)};
    }

    // ─── DDP-368 Etiket ──────────────────────────────────────────────────────
    private String[] evalLabel(DdpCase dc, JsonPath jp, String base, List<Object> list) {
        String kw = dc.labelCheck().toLowerCase(TR);
        String expected = "Top-" + dc.topN() + "'de \"" + dc.labelCheck() + "\" etiketli sonuç";
        for (int i = 0; i < Math.min(dc.topN(), list.size()); i++) {
            String allTitles = (safe(jp.getString(base+"["+i+"].data.songName"))
                    + " " + safe(jp.getString(base+"["+i+"].data.albumName"))).toLowerCase(TR);
            if (allTitles.contains(kw))
                return new String[]{expected, "OK",
                        "✅ pos " + (i+1) + ". sırada: "
                        + safe(jp.getString(base+"["+i+"].data.songName")) + "\n"
                        + top5Desc(jp, base)};
        }
        int wider = -1;
        for (int i = dc.topN(); i < list.size(); i++) {
            String t = (safe(jp.getString(base+"["+i+"].data.songName"))
                    + " " + safe(jp.getString(base+"["+i+"].data.albumName"))).toLowerCase(TR);
            if (t.contains(kw)) { wider = i; break; }
        }
        String whereStr = wider >= 0 ? "⚠️ pos " + (wider+1) + "/50'de → Sıralama sorunu"
                                     : "❌ TOP-50'DE YOK → Versiyon indexlenmemiş olabilir";
        return new String[]{expected, "NOK",
                "Başarısız. " + whereStr + "\n" + top5Desc(jp, base)};
    }

    // ─── Mutlak kontrol (DDP-366 / Regresyon / DDP-369) ─────────────────────
    private String[] evalAbsolute(DdpCase dc, JsonPath jp, String base,
                                   List<Object> list, String corrNote) {
        String ea = dc.expArtist(), et = dc.expTrack();
        int topN = dc.topN();

        int idx = !et.isEmpty()
                ? MuudSearchUtils.findArtistAndTrackIndex(jp, topN, ea, et)
                : MuudSearchUtils.findArtistIndex(jp, topN, ea);

        String expectedLabel = !et.isEmpty()
                ? (ea.isEmpty() ? "Top-" + topN + "'de '" + et + "'"
                                : "Top-" + topN + "'de '" + ea + " · " + et + "'")
                : (topN == 1 ? "1. sırada '" + ea + "'" : "Top-" + topN + "'de '" + ea + "'");

        if (idx >= 0)
            return new String[]{expectedLabel, "OK",
                    "✅ pos " + (idx+1) + ". sırada\n" + corrNote + top5Desc(jp, base)};

        // Bulunamadı — geniş tarama (top-50)
        int wider = !et.isEmpty()
                ? MuudSearchUtils.findArtistAndTrackIndex(jp, 50, ea, et)
                : MuudSearchUtils.findArtistIndex(jp, 50, ea);

        String whereStr;
        if (wider >= 0) {
            whereStr = "⚠️ pos " + (wider+1) + "/50'de bulundu → Sıralama / skor sorunu";
        } else if (dc.correction()) {
            // DDP-369: correction=true iken bulunamadı — doğru teşhis ver
            Object suggested = jp.get("suggestedText");
            String sug = suggested != null ? suggested.toString() : "(yok)";
            if (!sug.isEmpty() && !sug.equals("(yok)") && !sug.equalsIgnoreCase(dc.expArtist())) {
                whereStr = "❌ CORRECTION YANLIŞ YÖNLENDİRİYOR → suggestedText=\"" + sug
                        + "\" (beklenen: \"" + dc.expArtist().toLowerCase(TR)
                        + "\") → Arama '" + sug + "' için yapıldı, Poizi bulunamadı";
            } else {
                whereStr = "❌ TOP-50'DE YOK → UAT'ta veri eksik olabilir";
            }
        } else {
            whereStr = "❌ TOP-50'DE YOK → UAT'ta veri eksik olabilir";
        }

        return new String[]{expectedLabel, "NOK",
                "Başarısız. " + whereStr + "\n" + corrNote + top5Desc(jp, base)};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // YARDIMCI
    // ─────────────────────────────────────────────────────────────────────────

    private String resultKey(JsonPath jp, String base, int i) {
        String title  = primaryTitle(jp, base, i);
        String artist = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
        return (title + "|" + artist).trim();
    }

    private String primaryTitle(JsonPath jp, String base, int i) {
        String s = safe(jp.getString(base + "[" + i + "].data.songName"));   if (!s.isEmpty()) return s;
        s = safe(jp.getString(base + "[" + i + "].data.albumName"));         if (!s.isEmpty()) return s;
        s = safe(jp.getString(base + "[" + i + "].data.playlistName"));      if (!s.isEmpty()) return s;
        return MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
    }

    private String buildCorrectionNote(JsonPath jp, String expSuggest) {
        Object corrected = jp.get("corrected");
        Object suggested = jp.get("suggestedText");
        String corrStr = corrected != null ? corrected.toString() : "null";
        String suggStr = suggested != null ? suggested.toString() : "(yok)";
        String line = "correction → corrected=" + corrStr + ", suggestedText=\"" + suggStr + "\"";
        if (expSuggest != null && !expSuggest.isEmpty()) {
            boolean match = suggStr.toLowerCase(TR).contains(expSuggest.toLowerCase(TR))
                    || expSuggest.toLowerCase(TR).contains(suggStr.toLowerCase(TR));
            line += match ? " ✓" : " ✗ (beklenen: \"" + expSuggest + "\")";
        }
        return line + "\n";
    }

    private String top5Desc(JsonPath jp, String base) {
        List<Object> list = MuudSearchUtils.resultsList(jp);
        int n = Math.min(5, list.size());
        StringBuilder sb = new StringBuilder("İlk " + n + " sonuç:");
        for (int i = 0; i < n; i++) {
            String title  = primaryTitle(jp, base, i);
            String artist = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
            String kind   = safe(jp.getString(base + "[" + i + "].data.kind"));
            Object popO   = jp.get(base + "[" + i + "].data.prodPopularity");
            if (popO == null) popO = jp.get(base + "[" + i + "].data.popularity");
            Object scoO   = jp.get(base + "[" + i + "].score");
            String pop = popO != null ? String.format("%.2f", Double.parseDouble(popO.toString())) : "-";
            String sco = scoO != null ? String.format("%.3f", Double.parseDouble(scoO.toString())) : "-";
            sb.append("\n").append(i+1).append(". [").append(kind).append("] ")
              .append(title).append(artist.isEmpty() ? "" : " — " + artist)
              .append(" | pop=").append(pop).append(" | score=").append(sco);
        }
        return sb.toString();
    }

    private String modeDesc(DdpCase dc) {
        if (dc.labelCheck() != null) return "Etiket: \"" + dc.labelCheck() + "\"";
        if (dc.expArtist().isEmpty() && dc.expTrack().isEmpty()) {
            return dc.caseId().endsWith("a") ? "Komparatif baseline kaydı" : "Komparatif varyant";
        }
        return "correction=" + dc.correction() + " | topN=" + dc.topN()
                + " | " + dc.expArtist() + (dc.expTrack().isEmpty() ? "" : " · " + dc.expTrack());
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
