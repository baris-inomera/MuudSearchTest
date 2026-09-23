package tests;

import client.MuudSearchApi;
import client.SpotifyApi;
import config.TestConfig;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import util.MuudSearchUtils;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * MuudKapsamliCollector — Muud vs Spotify yan yana karşılaştırma raporu
 *
 * Her keyword için:
 *  1) Muud active-indices API'sini çağırır (top-50)
 *  2) Spotify track+artist araması yapar (client credentials — kullanıcı girişi yok)
 *  3) Excel'e her iki sonucu yan yana yazar
 *
 * Credentials (proje kök / src/test/resources/spotify.properties):
 *   client.id=...
 *   client.secret=...
 * veya -Dspotify.clientId=... -Dspotify.clientSecret=... ile override edilebilir.
 *
 * mvn test -Dtest=MuudKapsamliCollector
 * Çıktı: reports/kapsamli/MuudKapsamli_<zaman>_<ortam>.xlsx
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MuudKapsamliCollector extends TestConfig {

    // ─────────────────────────────────────────────────────────────────────────
    // Case record
    // ─────────────────────────────────────────────────────────────────────────
    record MCase(String caseId, String term, String expArtist, String expTrack,
                 String category, int topN, String variant, boolean correction) {
        MCase(String caseId, String term, String expArtist, String expTrack,
              String category, int topN, String variant) {
            this(caseId, term, expArtist, expTrack, category, topN, variant, false);
        }
    }

    // Kategori sabitleri
    private static final String TAM      = "Şarkı · Tam Eşleşme";
    private static final String VERSIYON = "Şarkı · Versiyon Etiketi";
    private static final String OZEL     = "Şarkı · Özel Karakter";
    private static final String KISMI    = "Şarkı · Kısmi Ad";
    private static final String LYRICS   = "Şarkı · Söz / Lyrics";
    private static final String COMBO    = "Şarkı · Sanatçı + Şarkı";
    private static final String SYAZIM   = "Şarkı · Yazım Hatası";
    private static final String SAT      = "Sanatçı · Tam Eşleşme";
    private static final String SAK      = "Sanatçı · Kısmi Ad";
    private static final String SAA      = "Sanatçı · Kısaltma / Alias";
    private static final String SAYAZIM  = "Sanatçı · Yazım Hatası";
    private static final String KLAVYE   = "Sanatçı · Klavye Komşusu";
    private static final String PL       = "Playlist";
    private static final String ALB      = "Albüm";

    // ─────────────────────────────────────────────────────────────────────────
    // CASE LİSTESİ
    // ─────────────────────────────────────────────────────────────────────────
    static Stream<MCase> cases() {
        return Stream.of(

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · TAM EŞLEŞME (50 case — kaliteli, UAT'ta kesin olan şarkılar)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_TAM_01","a canım","Mabel Matiz","A Canım",TAM,3,"Tam şarkı adı"),
            new MCase("SARKI_TAM_02","olmazlara vuruluyorum","Mert Demir","Olmazlara Vuruluyorum",TAM,3,"Tam şarkı adı"),
            new MCase("SARKI_TAM_03","çıkmaz bir sokakta","Semicenk","Çıkmaz Bir Sokakta",TAM,3,"Tam şarkı adı"),
            new MCase("SARKI_TAM_04","kusura bakma","Blok3","Kusura Bakma",TAM,3,"Blok3 versiyonu"),
            new MCase("SARKI_TAM_05","maraton","Ati242","Maraton",TAM,3,"Tek kelime şarkı"),
            new MCase("SARKI_TAM_06","geri ver","Wegh","Geri Ver",TAM,3,"İki kelime"),
            new MCase("SARKI_TAM_07","saygımdan","Bengü","Saygımdan",TAM,3,"Tek kelime TR"),
            new MCase("SARKI_TAM_08","meğerse","Linet","Meğerse",TAM,3,"Tek kelime"),
            new MCase("SARKI_TAM_09","yapar mısın","Poizi","YAPAR MISIN?",TAM,1,"Soru işaretli"),
            new MCase("SARKI_TAM_10","yerinde dur","Sefo","Yerinde Dur",TAM,1,"İki kelime"),
            new MCase("SARKI_TAM_11","ey aşk","Sezen Aksu","Ey Aşk",TAM,1,"İki kelime TR"),
            new MCase("SARKI_TAM_12","yalnızlığın çaresini bulmuşlar","Gripin","Yalnızlığın Çaresini Bulmuşlar",TAM,3,"Uzun başlık"),
            new MCase("SARKI_TAM_13","giderim kırağınan","Onur Şan","Giderim Kırağınan",TAM,3,"Yöresel"),
            new MCase("SARKI_TAM_14","ara beni lütfen","Kenan Doğulu","Ara Beni Lütfen",TAM,3,"Çok kelime"),
            new MCase("SARKI_TAM_15","vay dayı","Aynur Polat","Vay Dayı",TAM,1,"İki kelime"),
            new MCase("SARKI_TAM_16","perde","Poizi","PERDE",TAM,3,"Tek kelime"),
            new MCase("SARKI_TAM_17","kömür","Mabel Matiz","Kömür",TAM,3,"Tek kelime"),
            new MCase("SARKI_TAM_18","git","Blok3","git",TAM,3,"Tek kelime kısa"),
            new MCase("SARKI_TAM_19","ağlama ben ağlarım","Canozan","Ağlama Ben Ağlarım",TAM,3,"Dört kelime"),
            new MCase("SARKI_TAM_20","bunca yıl","Dedublüman","Bunca Yıl",TAM,3,"İki kelime"),
            new MCase("SARKI_TAM_21","sen yanlış yaptın","Şahin Kendirci","Sen Yanlış Yaptın",TAM,3,"Üç kelime"),
            new MCase("SARKI_TAM_22","çıkar biri karşıma","Poizi","ÇIKAR BİRİ KARŞIMA",TAM,1,"Üç kelime"),
            new MCase("SARKI_TAM_23","dacia","Blok3","DACIA",TAM,3,"Marka isim"),
            new MCase("SARKI_TAM_24","şımarık","Tarkan","Şımarık",TAM,3,"TR pop klasik"),
            new MCase("SARKI_TAM_25","kuzu kuzu","Tarkan","Kuzu Kuzu",TAM,3,"Tarkan hit"),
            new MCase("SARKI_TAM_34","dolu kadehi ters tut","Dolu Kadehi Ters Tut","",TAM,3,"Grup adı = şarkı adı"),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · VERSİYON ETİKETİ (20 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_VER_01","saygımdan akustik","Bengü","Saygımdan",VERSIYON,5,"Akustik versiyonu"),
            new MCase("SARKI_VER_02","saygımdan remix","Bengü","Saygımdan",VERSIYON,5,"Remix versiyonu"),
            new MCase("SARKI_VER_03","saygımdan [akustik]","Bengü","Saygımdan",VERSIYON,5,"[Akustik] köşeli"),
            new MCase("SARKI_VER_04","saygımdan [remix]","Bengü","Saygımdan",VERSIYON,5,"[Remix] köşeli"),
            new MCase("SARKI_VER_05","yerinde dur remix","Sefo","Yerinde Dur",VERSIYON,5,"Remix"),
            new MCase("SARKI_VER_06","yerinde dur [remix]","Sefo","Yerinde Dur",VERSIYON,5,"[Remix] köşeli"),
            new MCase("SARKI_VER_07","ara beni lütfen remix","Kenan Doğulu","Ara Beni Lütfen",VERSIYON,5,"Klasik remix"),
            new MCase("SARKI_VER_08","ağlama ben ağlarım sped up","Canozan","Ağlama Ben Ağlarım",VERSIYON,5,"Sped up"),
            new MCase("SARKI_VER_09","ağlama ben ağlarım slowed","Canozan","Ağlama Ben Ağlarım",VERSIYON,5,"Slowed"),
            new MCase("SARKI_VER_10","ağlama ben ağlarım live","Canozan","Ağlama Ben Ağlarım",VERSIYON,5,"Live"),
            new MCase("SARKI_VER_11","bunca yıl dedub sessions","Dedublüman","Bunca Yıl",VERSIYON,5,"[Dedub Sessions]"),
            new MCase("SARKI_VER_12","yalnızlığın çaresini bulmuşlar tek buzlu","Gripin","Yalnızlığın Çaresini Bulmuşlar",VERSIYON,5,"Özel versiyon"),
            new MCase("SARKI_VER_13","şımarık remix","Tarkan","Şımarık",VERSIYON,5,"Tarkan remix"),
            new MCase("SARKI_VER_14","şımarık akustik","Tarkan","Şımarık",VERSIYON,5,"Tarkan akustik"),
            new MCase("SARKI_VER_15","dacia x yatıya","Lvbel C5","",VERSIYON,5,"Kollab remix"),
            new MCase("SARKI_VER_16","blok3 live","Blok3","",VERSIYON,5,"Sanatçı + live"),
            new MCase("SARKI_VER_17","blok3 [live]","Blok3","",VERSIYON,5,"Sanatçı + [live]"),
            new MCase("SARKI_VER_18","tarkan live","","",VERSIYON,5,"Tarkan live — UAT'ta veri varsa OK"),
            new MCase("SARKI_VER_19","ağlama ben ağlarım [sped up]","Canozan","Ağlama Ben Ağlarım",VERSIYON,5,"[Sped up] köşeli"),
            new MCase("SARKI_VER_20","mabel matiz akustik","Mabel Matiz","",VERSIYON,5,"Sanatçı + akustik"),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · ÖZEL KARAKTER ($, !, ?) (12 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_OZEL_01","yapar mısın?","Poizi","YAPAR MISIN?",OZEL,1,"? karakteri"),
            new MCase("SARKI_OZEL_02","yapar mısın !","Poizi","YAPAR MISIN?",OZEL,3,"! karakteri"),
            new MCase("SARKI_OZEL_03","çıkar biri karşıma!","Poizi","ÇIKAR BİRİ KARŞIMA",OZEL,3,"! ekli sorgu"),
            new MCase("SARKI_OZEL_04","geri ver!","Wegh","Geri Ver",OZEL,3,"! ekli"),
            new MCase("SARKI_OZEL_05","yerinde dur!","Sefo","Yerinde Dur",OZEL,3,"! ekli"),
            new MCase("SARKI_OZEL_06","ey aşk!","Sezen Aksu","Ey Aşk",OZEL,3,"! ekli"),
            new MCase("SARKI_OZEL_07","kuzu kuzu!","Tarkan","Kuzu Kuzu",OZEL,3,"! ekli"),
            new MCase("SARKI_OZEL_08","a canım!","Mabel Matiz","A Canım",OZEL,3,"! ekli"),
            new MCase("SARKI_OZEL_09","kusura bakma?","","Kusura Bakma",OZEL,3,"? ekli"),
            new MCase("SARKI_OZEL_10","dacia $","","DACIA",OZEL,5,"$ karakteri"),
            new MCase("SARKI_OZEL_12","r&b","","",OZEL,3,"& karakteri"),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · KISMİ AD (20 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_KISMI_01","çıkmaz sokakta","Semicenk","Çıkmaz Bir Sokakta",KISMI,5,"Eksik kelime"),
            new MCase("SARKI_KISMI_02","yalnızlığın çaresi","Gripin","Yalnızlığın Çaresini Bulmuşlar",KISMI,3,"Kısmi uzun"),
            new MCase("SARKI_KISMI_03","saygımdan bengü","Bengü","Saygımdan",KISMI,1,"Sanatçı + şarkı"),
            new MCase("SARKI_KISMI_04","yapar mısın poizi","Poizi","YAPAR MISIN?",KISMI,1,"Sanatçı + şarkı"),
            new MCase("SARKI_KISMI_05","perde poizi","Poizi","PERDE",KISMI,1,"Sanatçı + şarkı"),
            new MCase("SARKI_KISMI_06","yerinde dur sefo","Sefo","Yerinde Dur",KISMI,1,"Sanatçı + şarkı"),
            new MCase("SARKI_KISMI_07","ey aşk sezen","Sezen Aksu","Ey Aşk",KISMI,3,"Kısmi sanatçı"),
            new MCase("SARKI_KISMI_08","giderim kırağ","","Giderim Kırağınan",KISMI,3,"Kısmi şarkı"),
            new MCase("SARKI_KISMI_09","ara beni kenan","Kenan Doğulu","Ara Beni Lütfen",KISMI,3,"Kısmi + sanatçı"),
            new MCase("SARKI_KISMI_10","vay dayı aynur","Aynur Polat","Vay Dayı",KISMI,3,"Kısmi + sanatçı"),
            new MCase("SARKI_KISMI_11","dolu kadehi ters","","Dolu Kadehi Ters Tut",KISMI,3,"Kısmi uzun"),
            new MCase("SARKI_KISMI_12","ağlama ben canozan","Canozan","Ağlama Ben Ağlarım",KISMI,3,"Kısmi + sanatçı"),
            new MCase("SARKI_KISMI_13","kömür mabel","Mabel Matiz","Kömür",KISMI,3,"Kısmi sanatçı"),
            new MCase("SARKI_KISMI_14","git blok","Blok3","git",KISMI,3,"Kısmi sanatçı"),
            new MCase("SARKI_KISMI_15","dacia blok","Blok3","DACIA",KISMI,3,"Kısmi sanatçı"),
            new MCase("SARKI_KISMI_16","şımarık tar","Tarkan","Şımarık",KISMI,3,"Kısmi sanatçı"),
            new MCase("SARKI_KISMI_17","bunca yıl dedublüman","Dedublüman","Bunca Yıl",KISMI,3,"Sanatçı + şarkı"),
            new MCase("SARKI_KISMI_18","olmazlara mert","Mert Demir","Olmazlara Vuruluyorum",KISMI,3,"Kısmi + sanatçı"),
            new MCase("SARKI_KISMI_19","çıkar karşıma poizi","Poizi","ÇIKAR BİRİ KARŞIMA",KISMI,3,"Kısmi + sanatçı"),
            new MCase("SARKI_KISMI_20","maraton ati","Ati242","Maraton",KISMI,3,"Kısmi sanatçı"),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · SANATÇI + ŞARKI KOMBİNASYON (15 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_COMBO_01","poizi yapar mısın","Poizi","YAPAR MISIN?",COMBO,1,"Sanatçı önce"),
            new MCase("SARKI_COMBO_02","blok3 kusura bakma","Blok3","Kusura Bakma",COMBO,1,"Sanatçı önce"),
            new MCase("SARKI_COMBO_03","mabel matiz a canım","Mabel Matiz","A Canım",COMBO,1,"Sanatçı önce"),
            new MCase("SARKI_COMBO_04","tarkan şımarık","Tarkan","Şımarık",COMBO,3,"Tarkan + şarkı"),
            new MCase("SARKI_COMBO_05","sezen aksu ey aşk","Sezen Aksu","Ey Aşk",COMBO,1,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_06","sefo yerinde dur","Sefo","Yerinde Dur",COMBO,1,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_07","gripin yalnızlığın çaresini","Gripin","Yalnızlığın Çaresini Bulmuşlar",COMBO,3,"Kısmi combo"),
            new MCase("SARKI_COMBO_08","bengü saygımdan","Bengü","Saygımdan",COMBO,1,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_09","linet meğerse","Linet","Meğerse",COMBO,1,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_10","ati242 maraton","Ati242","Maraton",COMBO,1,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_11","tarkan kuzu kuzu","Tarkan","Kuzu Kuzu",COMBO,1,"Tarkan + şarkı"),
            new MCase("SARKI_COMBO_12","poizi perde","Poizi","PERDE",COMBO,1,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_13","dedublüman bunca yıl","Dedublüman","Bunca Yıl",COMBO,3,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_14","canozan ağlama ben","Canozan","Ağlama Ben Ağlarım",COMBO,3,"Kısmi combo"),
            new MCase("SARKI_COMBO_15","aynur polat vay dayı","Aynur Polat","Vay Dayı",COMBO,1,"Sanatçı + şarkı"),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · YAZIM HATASI — ASCII fold + bitişik yazım (15 case)
            // NOT: Correctionlu klavye typo'lar KLAVYE bölümüne taşındı
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_YAZ_01","a canim","Mabel Matiz","A Canım",SYAZIM,5,"ı→i ASCII"),
            new MCase("SARKI_YAZ_02","acanım","Mabel Matiz","A Canım",SYAZIM,5,"Bitişik yazım"),
            new MCase("SARKI_YAZ_03","yapar misin","Poizi","YAPAR MISIN?",SYAZIM,3,"ı→i ASCII"),
            new MCase("SARKI_YAZ_04","yalnizligin caresini bulmusslar","Gripin","Yalnızlığın Çaresini Bulmuşlar",SYAZIM,5,"Tüm TR harflersiz"),
            new MCase("SARKI_YAZ_05","simarik","Tarkan","Şımarık",SYAZIM,5,"ş/ı→s/i ASCII"),
            new MCase("SARKI_YAZ_06","ey ask","Sezen Aksu","Ey Aşk",SYAZIM,3,"ş→s ASCII"),
            new MCase("SARKI_YAZ_07","vay dayi","Aynur Polat","Vay Dayı",SYAZIM,3,"ı→i ASCII"),
            new MCase("SARKI_YAZ_08","bunca yil","Dedublüman","Bunca Yıl",SYAZIM,5,"ı→i ASCII"),
            new MCase("SARKI_YAZ_09","saygimdan","Bengü","Saygımdan",SYAZIM,5,"ı→i ASCII"),
            new MCase("SARKI_YAZ_10","megerse","Linet","Meğerse",SYAZIM,5,"ğ→g ASCII"),
            new MCase("SARKI_YAZ_11","çıkmaz bir sokakda","Semicenk","Çıkmaz Bir Sokakta",SYAZIM,5,"ta→da"),
            new MCase("SARKI_YAZ_12","kusurabakma","Blok3","Kusura Bakma",SYAZIM,5,"Bitişik yazım"),
            new MCase("SARKI_YAZ_13","yerindedur","Sefo","Yerinde Dur",SYAZIM,5,"Bitişik yazım"),
            new MCase("SARKI_YAZ_14","olmazlara vuruluyrom","Mert Demir","Olmazlara Vuruluyorum",SYAZIM,5,"Harf eksik",true),
            new MCase("SARKI_YAZ_15","agla ben aglarim","Canozan","Ağlama Ben Ağlarım",SYAZIM,5,"ğ→g ASCII kısmi"),

            // ══════════════════════════════════════════════════════════════════
            // SANATÇI · TAM EŞLEŞME (35 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SANATCI_TAM_01","tarkan","Tarkan","",SAT,1,"İkonik sanatçı"),
            new MCase("SANATCI_TAM_02","sezen aksu","Sezen Aksu","",SAT,1,"İkonik sanatçı"),
            new MCase("SANATCI_TAM_03","poizi","Poizi","",SAT,1,"Pop sanatçı"),
            new MCase("SANATCI_TAM_04","blok3","Blok3","",SAT,1,"Rap grubu"),
            new MCase("SANATCI_TAM_05","mabel matiz","Mabel Matiz","",SAT,1,"Pop sanatçı"),
            new MCase("SANATCI_TAM_06","gripin","Gripin","",SAT,1,"Rock grubu"),
            new MCase("SANATCI_TAM_07","semicenk","Semicenk","",SAT,1,"Pop sanatçı"),
            new MCase("SANATCI_TAM_08","sefo","Sefo","",SAT,1,"Trap sanatçı"),
            new MCase("SANATCI_TAM_09","linet","Linet","",SAT,1,"Pop sanatçı"),
            new MCase("SANATCI_TAM_10","ati242","Ati242","",SAT,1,"Rap sanatçı"),
            new MCase("SANATCI_TAM_11","bengü","Bengü","",SAT,1,"Pop sanatçı"),
            new MCase("SANATCI_TAM_12","teoman","Teoman","",SAT,1,"Rock sanatçı"),
            new MCase("SANATCI_TAM_13","manga","Manga","",SAT,1,"Rock grubu"),
            new MCase("SANATCI_TAM_14","dedublüman","Dedublüman","",SAT,1,"Rock grubu"),
            new MCase("SANATCI_TAM_15","ceza","Ceza","",SAT,1,"Hip-hop"),
            new MCase("SANATCI_TAM_16","sagopa kajmer","Sagopa Kajmer","",SAT,1,"Hip-hop"),
            new MCase("SANATCI_TAM_17","norm ender","Norm Ender","",SAT,1,"Rap"),
            new MCase("SANATCI_TAM_18","ben fero","Ben Fero","",SAT,1,"Rap"),
            new MCase("SANATCI_TAM_19","uzi","Uzi","",SAT,3,"Kısa isim"),
            new MCase("SANATCI_TAM_20","murda","Murda","",SAT,1,"Rap"),
            new MCase("SANATCI_TAM_21","bts","BTS","",SAT,3,"K-pop grubu"),
            new MCase("SANATCI_TAM_22","drake","Drake","",SAT,3,"Global pop"),
            new MCase("SANATCI_TAM_23","haluk levent","Haluk Levent","",SAT,1,"Pop"),
            new MCase("SANATCI_TAM_24","şebnem ferah","Şebnem Ferah","",SAT,1,"Rock"),
            new MCase("SANATCI_TAM_25","hadise","Hadise","",SAT,1,"Pop"),
            new MCase("SANATCI_TAM_26","hande yener","Hande Yener","",SAT,1,"Pop"),
            new MCase("SANATCI_TAM_27","demet akalın","Demet Akalın","",SAT,1,"Pop"),
            new MCase("SANATCI_TAM_28","zeynep bastık","Zeynep Bastık","",SAT,1,"Pop"),
            new MCase("SANATCI_TAM_29","melike şahin","Melike Şahin","",SAT,1,"Pop"),
            new MCase("SANATCI_TAM_30","emre aydın","Emre Aydın","",SAT,1,"Pop-rock"),
            new MCase("SANATCI_TAM_31","emir can iğrek","Emir Can İğrek","",SAT,1,"Pop"),
            new MCase("SANATCI_TAM_32","ece seçkin","Ece Seçkin","",SAT,1,"Pop"),
            new MCase("SANATCI_TAM_33","yüzyüzeyken konuşuruz","Yüzyüzeyken Konuşuruz","",SAT,1,"Indie grubu"),
            new MCase("SANATCI_TAM_34","dolu kadehi ters tut","Dolu Kadehi Ters Tut","",SAT,1,"Indie grubu"),
            new MCase("SANATCI_TAM_35","velet","Velet","",SAT,1,"Trap"),

            // ══════════════════════════════════════════════════════════════════
            // SANATÇI · KLAVYE KOMŞUSU (20 case)
            // Spotify ile en anlamlı karşılaştırma burası!
            // Sadece QWERTY'de gerçekten komşu tuş olan hatalar
            // ══════════════════════════════════════════════════════════════════
            // BTS: b↔v, t↔d, t↔g, b↔n tuşları komşu
            new MCase("KLAVYE_01","vts","BTS","",KLAVYE,5,"b→v QWERTY komşu",true),
            // UZI: u↔y komşu
            new MCase("KLAVYE_04","yzi","Uzi","",KLAVYE,3,"u→y QWERTY komşu",true),
            // Drake: k↔j, k↔l komşu
            new MCase("KLAVYE_05","draje","Drake","",KLAVYE,5,"k→j QWERTY komşu",true),
            new MCase("KLAVYE_06","drale","Drake","",KLAVYE,5,"k→l QWERTY komşu",true),
            // Tarkan: a↔s komşu
            new MCase("KLAVYE_07","tarksn","Tarkan","",KLAVYE,3,"a→s QWERTY komşu",true),
            // Ceza: z↔x komşu, e→r komşu
            new MCase("KLAVYE_08","ceza","Ceza","",KLAVYE,1,"Doğru - baseline"),
            new MCase("KLAVYE_09","cexa","Ceza","",KLAVYE,5,"z→x QWERTY komşu",true),
            // Poizi: i→ş TR klavye
            new MCase("KLAVYE_10","poizş","Poizi","",KLAVYE,3,"i→ş TR klavye",true),
            // Gripin: i→ı TR klavye
            new MCase("KLAVYE_11","grıpin","Gripin","",KLAVYE,5,"i→ı TR klavye"),
            // Manga: m↔n komşu (bu daha makul çünkü sanatçı adı tek)
            new MCase("KLAVYE_12","nanga","Manga","",KLAVYE,5,"m→n QWERTY komşu",true),
            // Sefo: s→a, e→r komşu
            new MCase("KLAVYE_13","srfo","Sefo","",KLAVYE,5,"e→r QWERTY komşu",true),
            // Ed Sheeran: e→r komşu yaygın
            new MCase("KLAVYE_14","ed sheran","Ed Sheeran","",KLAVYE,5,"Harf eksik",true),
            // Billie Eilish: baseline
            new MCase("KLAVYE_15","billie eilish","Billie Eilish","",KLAVYE,3,"Doğru - baseline"),
            // Haluk Levent: e→r komşu
            new MCase("KLAVYE_16","haluk levnt","Haluk Levent","",KLAVYE,5,"Harf eksik",true),
            // Eminem: yaygın yazım hatası
            new MCase("KLAVYE_17","eminem","Eminem","",KLAVYE,3,"Doğru - baseline"),
            new MCase("KLAVYE_18","eminim","Eminem","",KLAVYE,5,"e→i transposisyon",true),
            // The Weeknd
            new MCase("KLAVYE_19","the weeknd","The Weeknd","",KLAVYE,3,"Doğru - baseline"),
            new MCase("KLAVYE_20","the weekd","The Weeknd","",KLAVYE,5,"Harf eksik",true),

            // ══════════════════════════════════════════════════════════════════
            // SANATÇI · KISMİ AD (20 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SANATCI_KISMI_01","mabel","Mabel Matiz","",SAK,3,"İlk isim"),
            new MCase("SANATCI_KISMI_02","sezen","Sezen Aksu","",SAK,3,"İlk isim"),
            new MCase("SANATCI_KISMI_03","gripi","Gripin","",SAK,3,"Son harf eksik"),
            new MCase("SANATCI_KISMI_04","semicen","Semicenk","",SAK,3,"Son harf eksik"),
            new MCase("SANATCI_KISMI_05","haluk","Haluk Levent","",SAK,3,"İlk isim"),
            new MCase("SANATCI_KISMI_06","şebnem","Şebnem Ferah","",SAK,3,"İlk isim"),
            new MCase("SANATCI_KISMI_07","sagopa","Sagopa Kajmer","",SAK,1,"İlk isim uzun"),
            new MCase("SANATCI_KISMI_08","kenan","Kenan Doğulu","",SAK,3,"İlk isim yaygın"),
            new MCase("SANATCI_KISMI_09","aynur","Aynur Polat","",SAK,3,"İlk isim"),
            new MCase("SANATCI_KISMI_10","wegh","Wegh","",SAK,1,"Kısa isim"),
            new MCase("SANATCI_KISMI_11","hande","Hande Yener","",SAK,3,"İlk isim"),
            new MCase("SANATCI_KISMI_12","demet","Demet Akalın","",SAK,3,"İlk isim"),
            new MCase("SANATCI_KISMI_13","zeynep","Zeynep Bastık","",SAK,3,"İlk isim"),
            new MCase("SANATCI_KISMI_14","yüzyüzeyken","Yüzyüzeyken Konuşuruz","",SAK,1,"İlk kelime"),
            new MCase("SANATCI_KISMI_15","dolu kadehi","Dolu Kadehi Ters Tut","",SAK,1,"İlk iki kelime"),
            new MCase("SANATCI_KISMI_16","emir can","Emir Can İğrek","",SAK,3,"İlk iki isim"),
            new MCase("SANATCI_KISMI_17","gülş","Gülşen","",SAK,5,"Kısaltma"),
            new MCase("SANATCI_KISMI_18","sıl","Sıla","",SAK,5,"Kısaltma"),
            new MCase("SANATCI_KISMI_19","cem b","Cem Belevi","",SAK,5,"İlk isim + harf"),
            new MCase("SANATCI_KISMI_20","norm","Norm Ender","",SAK,3,"İlk isim"),

            // ══════════════════════════════════════════════════════════════════
            // SANATÇI · YAZIM HATASI — ASCII fold (10 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SANATCI_YAZ_01","grıpin","Gripin","",SAYAZIM,5,"i→ı TR klavye"),
            new MCase("SANATCI_YAZ_02","mabel matız","Mabel Matiz","",SAYAZIM,5,"i→ı TR klavye"),
            new MCase("SANATCI_YAZ_03","demet akalin","Demet Akalın","",SAYAZIM,5,"ı→i ASCII"),
            new MCase("SANATCI_YAZ_04","hande yenr","Hande Yener","",SAYAZIM,5,"Harf eksik",true),
            new MCase("SANATCI_YAZ_05","ece seckin","Ece Seçkin","",SAYAZIM,5,"ç→c ASCII"),
            new MCase("SANATCI_YAZ_06","emre aydin","Emre Aydın","",SAYAZIM,5,"ı→i ASCII"),
            new MCase("SANATCI_YAZ_07","kenan dogulu","Kenan Doğulu","",SAYAZIM,5,"ğ→g ASCII"),
            new MCase("SANATCI_YAZ_08","normender","Norm Ender","",SAYAZIM,5,"Bitişik yazım"),
            new MCase("SANATCI_YAZ_09","sezen aksu ","Sezen Aksu","",SAYAZIM,3,"Sonda boşluk"),
            new MCase("SANATCI_YAZ_10","sıla","Sıla","",SAYAZIM,3,"Doğru TR yazım"),

            // ══════════════════════════════════════════════════════════════════
            // PLAYLIST (15 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("PLAYLIST_01","türkçe pop","","",PL,3,"Genel tür"),
            new MCase("PLAYLIST_02","en iyi türkçe şarkılar","","",PL,3,"Genel"),
            new MCase("PLAYLIST_03","sabah müziği","","",PL,5,"Sabah"),
            new MCase("PLAYLIST_04","hip hop türkçe","","",PL,5,"Tür"),
            new MCase("PLAYLIST_05","rap türkiye","","",PL,5,"Rap"),
            new MCase("PLAYLIST_06","rock türkçe","","",PL,5,"Rock"),
            new MCase("PLAYLIST_07","romantik şarkılar","","",PL,3,"Romantik"),
            new MCase("PLAYLIST_08","üzgün şarkılar","","",PL,3,"Duygu"),
            new MCase("PLAYLIST_09","top 50 türkiye","","",PL,5,"Chart"),
            new MCase("PLAYLIST_10","en çok dinlenenler","","",PL,3,"Popüler"),
            new MCase("PLAYLIST_11","yeni çıkanlar","","",PL,5,"Yeni müzik"),
            new MCase("PLAYLIST_12","90lar türkçe","","",PL,5,"Dönem"),
            new MCase("PLAYLIST_13","parti müziği","","",PL,5,"Parti"),
            new MCase("PLAYLIST_14","halk müziği","","",PL,5,"Tür"),
            new MCase("PLAYLIST_15","klasik türk müziği","","",PL,5,"Tür"),

            // ══════════════════════════════════════════════════════════════════
            // ALBÜM (15 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("ALBUM_01","tarkan albüm","Tarkan","",ALB,5,"Sanatçı + albüm"),
            new MCase("ALBUM_02","sezen aksu albüm","Sezen Aksu","",ALB,5,"Sanatçı + albüm"),
            new MCase("ALBUM_03","gripin albüm","Gripin","",ALB,5,"Rock albüm"),
            new MCase("ALBUM_04","blok3 albüm","Blok3","",ALB,5,"Rap albüm"),
            new MCase("ALBUM_05","mabel matiz albüm","Mabel Matiz","",ALB,5,"Pop albüm"),
            new MCase("ALBUM_06","dedublüman albüm","Dedublüman","",ALB,5,"Rock albüm"),
            new MCase("ALBUM_07","sagopa kajmer albüm","Sagopa Kajmer","",ALB,5,"Hip-hop albüm"),
            new MCase("ALBUM_08","ceza albüm","Ceza","",ALB,5,"Hip-hop albüm"),
            new MCase("ALBUM_09","hadise albüm","Hadise","",ALB,5,"Pop albüm"),
            new MCase("ALBUM_10","hande yener albüm","Hande Yener","",ALB,5,"Pop albüm"),
            new MCase("ALBUM_11","demet akalın albüm","Demet Akalın","",ALB,5,"Pop albüm"),
            new MCase("ALBUM_12","emre aydın albüm","Emre Aydın","",ALB,5,"Pop-rock albüm"),
            new MCase("ALBUM_13","sefo albüm","Sefo","",ALB,5,"Trap albüm"),
            new MCase("ALBUM_14","poizi albüm","Poizi","",ALB,5,"Pop albüm"),
            new MCase("ALBUM_15","zeynep bastık albüm","Zeynep Bastık","",ALB,5,"Pop albüm"),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · TAM EŞLEŞME — EK (30 case, gerçekçi UAT şarkıları)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_TAM_E01","kusura bakma blok3","Blok3","Kusura Bakma",TAM,3,"Sanatçı + şarkı tam"),
            new MCase("SARKI_TAM_E02","olmazlara vuruluyorum mert demir","Mert Demir","Olmazlara Vuruluyorum",TAM,3,"Tam combo"),
            new MCase("SARKI_TAM_E03","ağlama ben ağlarım","Canozan","Ağlama Ben Ağlarım",TAM,3,"Tam şarkı adı"),
            new MCase("SARKI_TAM_E04","perde","Poizi","PERDE",TAM,3,"Tek kelime"),
            new MCase("SARKI_TAM_E05","kömür","Mabel Matiz","Kömür",TAM,3,"Tek kelime"),
            new MCase("SARKI_TAM_E06","git","Blok3","git",TAM,3,"Çok kısa"),
            new MCase("SARKI_TAM_E07","geri ver","Wegh","Geri Ver",TAM,3,"İki kelime"),
            new MCase("SARKI_TAM_E08","meğerse","Linet","Meğerse",TAM,3,"Tek kelime"),
            new MCase("SARKI_TAM_E09","maraton","Ati242","Maraton",TAM,3,"Tekrar farklı topN"),
            new MCase("SARKI_TAM_E10","giderim kırağınan","Onur Şan","Giderim Kırağınan",TAM,3,"Yöresel şarkı"),
            new MCase("SARKI_TAM_E11","canım","","",TAM,5,"Yaygın kelime"),
            new MCase("SARKI_TAM_E12","sevda","","",TAM,5,"Yaygın kelime"),
            new MCase("SARKI_TAM_E13","aşk","","",TAM,5,"Yaygın kelime"),
            new MCase("SARKI_TAM_E14","gece","","",TAM,5,"Yaygın kelime"),
            new MCase("SARKI_TAM_E15","yağmur","","",TAM,5,"Hava durumu kelime"),
            new MCase("SARKI_TAM_E16","kalbim","","",TAM,5,"Duygusal kelime"),
            new MCase("SARKI_TAM_E17","seninle","","",TAM,5,"Duygusal kelime"),
            new MCase("SARKI_TAM_E18","özledim","","",TAM,5,"Duygusal kelime"),
            new MCase("SARKI_TAM_E19","hayat","","",TAM,5,"Genel kelime"),
            new MCase("SARKI_TAM_E20","istanbul","","",TAM,5,"Şehir adı"),
            new MCase("SARKI_TAM_E21","sabah","","",TAM,5,"Zaman kelimesi"),
            new MCase("SARKI_TAM_E22","gün sonu","","",TAM,5,"İki kelime"),
            new MCase("SARKI_TAM_E23","yalnız","","",TAM,5,"Tek kelime duygu"),
            new MCase("SARKI_TAM_E24","ağla","","",TAM,5,"Fiil"),
            new MCase("SARKI_TAM_E25","sensiz","","",TAM,5,"Duygusal"),
            new MCase("SARKI_TAM_E26","ay tenli","","",TAM,5,"İki kelime"),
            new MCase("SARKI_TAM_E27","kıyma bana","","",TAM,5,"İki kelime"),
            new MCase("SARKI_TAM_E28","kahraman","","",TAM,5,"Tek kelime"),
            new MCase("SARKI_TAM_E29","son dans","","",TAM,5,"Son + dans"),
            new MCase("SARKI_TAM_E30","gönlüm","","",TAM,5,"Tek kelime TR"),

            // ══════════════════════════════════════════════════════════════════
            // SANATÇI · TAM EŞLEŞME — EK ULUSLARARASI (20 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SANATCI_TAM_E01","ed sheeran","Ed Sheeran","",SAT,3,"Global pop"),
            new MCase("SANATCI_TAM_E02","eminem","Eminem","",SAT,3,"Global hip-hop"),
            new MCase("SANATCI_TAM_E03","the weeknd","The Weeknd","",SAT,3,"Global r&b"),
            new MCase("SANATCI_TAM_E04","taylor swift","Taylor Swift","",SAT,3,"Global pop"),
            new MCase("SANATCI_TAM_E05","coldplay","Coldplay","",SAT,3,"Global rock"),
            new MCase("SANATCI_TAM_E06","ariana grande","Ariana Grande","",SAT,3,"Global pop"),
            new MCase("SANATCI_TAM_E07","billie eilish","Billie Eilish","",SAT,3,"Global pop"),
            new MCase("SANATCI_TAM_E08","post malone","Post Malone","",SAT,3,"Global hip-hop"),
            new MCase("SANATCI_TAM_E09","bad bunny","Bad Bunny","",SAT,3,"Reggaeton"),
            new MCase("SANATCI_TAM_E10","dua lipa","Dua Lipa","",SAT,3,"Global pop"),
            new MCase("SANATCI_TAM_E11","weeknd","The Weeknd","",SAT,5,"The olmadan"),
            new MCase("SANATCI_TAM_E12","sheeran","Ed Sheeran","",SAT,5,"Sadece soyad"),
            new MCase("SANATCI_TAM_E13","kendrick lamar","Kendrick Lamar","",SAT,3,"Hip-hop"),
            new MCase("SANATCI_TAM_E14","travis scott","Travis Scott","",SAT,3,"Hip-hop"),
            new MCase("SANATCI_TAM_E15","olivia rodrigo","Olivia Rodrigo","",SAT,3,"Pop"),
            new MCase("SANATCI_TAM_E16","lady gaga","Lady Gaga","",SAT,3,"Pop"),
            new MCase("SANATCI_TAM_E17","rihanna","Rihanna","",SAT,3,"R&B"),
            new MCase("SANATCI_TAM_E18","harry styles","Harry Styles","",SAT,3,"Pop"),
            new MCase("SANATCI_TAM_E19","justin bieber","Justin Bieber","",SAT,3,"Pop"),
            new MCase("SANATCI_TAM_E20","adele","Adele","",SAT,3,"Pop/soul"),

            // ══════════════════════════════════════════════════════════════════
            // KLAVYE KOMŞUSU — EK (10 case, daha fazla QWERTY senaryosu)
            // ══════════════════════════════════════════════════════════════════
            new MCase("KLAVYE_E01","draje","Drake","",KLAVYE,5,"k→j QWERTY komşu",true),
            new MCase("KLAVYE_E02","taylir swift","Taylor Swift","",KLAVYE,5,"o→i QWERTY komşu",true),
            new MCase("KLAVYE_E03","colplay","Coldplay","",KLAVYE,5,"d eksik",true),
            new MCase("KLAVYE_E04","billie eilsh","Billie Eilish","",KLAVYE,5,"i eksik",true),
            new MCase("KLAVYE_E05","atiana grande","Ariana Grande","",KLAVYE,5,"r→t komşu",true),
            new MCase("KLAVYE_E06","bad buny","Bad Bunny","",KLAVYE,5,"n eksik",true),
            new MCase("KLAVYE_E07","rihnna","Rihanna","",KLAVYE,5,"a eksik",true),
            new MCase("KLAVYE_E08","jsutin bieber","Justin Bieber","",KLAVYE,5,"transposisyon",true),
            new MCase("KLAVYE_E09","dua lıpa","Dua Lipa","",KLAVYE,5,"i→ı TR klavye",true),
            new MCase("KLAVYE_E10","olivia rodrigi","Olivia Rodrigo","",KLAVYE,5,"o→i son harf",true),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · VERSİYON ETİKETİ — EK (10 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_VER_E01","şımarık tarkan","Tarkan","Şımarık",VERSIYON,3,"Sanatçı son"),
            new MCase("SARKI_VER_E02","a canım live","Mabel Matiz","A Canım",VERSIYON,5,"Live"),
            new MCase("SARKI_VER_E04","kusura bakma [remix]","Blok3","Kusura Bakma",VERSIYON,5,"[Remix] köşeli"),
            new MCase("SARKI_VER_E05","maraton sped up","Ati242","Maraton",VERSIYON,5,"Sped up"),
            new MCase("SARKI_VER_E06","maraton slowed","Ati242","Maraton",VERSIYON,5,"Slowed"),
            new MCase("SARKI_VER_E07","kuzu kuzu remix","Tarkan","Kuzu Kuzu",VERSIYON,5,"Tarkan remix"),
            new MCase("SARKI_VER_E08","ağlama ben ağlarım akustik","Canozan","Ağlama Ben Ağlarım",VERSIYON,5,"Akustik"),
            new MCase("SARKI_VER_E09","geri ver remix","Wegh","Geri Ver",VERSIYON,5,"Remix"),
            new MCase("SARKI_VER_E10","bunca yıl akustik","Dedublüman","Bunca Yıl",VERSIYON,5,"Akustik"),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · YAZIM HATASI — EK (15 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_YAZ_E01","giderim kiragınan","Onur Şan","Giderim Kırağınan",SYAZIM,5,"ı→i ASCII"),
            new MCase("SARKI_YAZ_E02","kömür mabel matiz","Mabel Matiz","Kömür",SYAZIM,3,"Combo ASCII"),
            new MCase("SARKI_YAZ_E03","şımarik","Tarkan","Şımarık",SYAZIM,5,"k→ik yazım"),
            new MCase("SARKI_YAZ_E04","a canim mabel","Mabel Matiz","A Canım",SYAZIM,3,"ı→i + sanatçı"),
            new MCase("SARKI_YAZ_E05","kusur bakma","Blok3","Kusura Bakma",SYAZIM,5,"a eksik",true),
            new MCase("SARKI_YAZ_E06","perde poizi","Poizi","PERDE",SYAZIM,1,"Tam combo"),
            new MCase("SARKI_YAZ_E07","maraton ati 242","Ati242","Maraton",SYAZIM,3,"Boşluklu yazım"),
            new MCase("SARKI_YAZ_E08","git blok 3","Blok3","git",SYAZIM,3,"Boşluklu yazım"),
            new MCase("SARKI_YAZ_E09","semicenk çıkmaz","Semicenk","Çıkmaz Bir Sokakta",SYAZIM,3,"Kısmi ASCII"),
            new MCase("SARKI_YAZ_E10","vay dayi aynur","Aynur Polat","Vay Dayı",SYAZIM,5,"ı→i + sanatçı"),
            new MCase("SARKI_YAZ_E11","meğers","Linet","Meğerse",SYAZIM,5,"Son harf eksik",true),
            new MCase("SARKI_YAZ_E12","geri vr","Wegh","Geri Ver",SYAZIM,5,"e eksik",true),
            new MCase("SARKI_YAZ_E13","benga saygımdan","Bengü","Saygımdan",SYAZIM,5,"ü→a typo",true),
            new MCase("SARKI_YAZ_E14","ey ask sezen aksu","Sezen Aksu","Ey Aşk",SYAZIM,3,"Tam + ASCII"),
            new MCase("SARKI_YAZ_E15","kusura bak","Blok3","Kusura Bakma",SYAZIM,5,"Kısmi",true),

            // ══════════════════════════════════════════════════════════════════
            // SANATÇI · KISMİ AD — EK (10 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SANATCI_KISMI_E01","ben fer","Ben Fero","",SAK,5,"Son harf eksik"),
            new MCase("SANATCI_KISMI_E02","uzi rap","Uzi","",SAK,3,"Tür + isim"),
            new MCase("SANATCI_KISMI_E03","norm end","Norm Ender","",SAK,3,"Kısmi soyad"),
            new MCase("SANATCI_KISMI_E04","emre ay","Emre Aydın","",SAK,5,"Kısmi soyad"),
            new MCase("SANATCI_KISMI_E05","ilyas yalçın","İlyas Yalçıntaş","",SAK,5,"Kısmi soyad"),
            new MCase("SANATCI_KISMI_E06","emir iğrek","Emir Can İğrek","",SAK,5,"Kısmi isim"),
            new MCase("SANATCI_KISMI_E07","cem belev","Cem Belevi","",SAK,5,"Son harf eksik"),
            new MCase("SANATCI_KISMI_E08","melike şah","Melike Şahin","",SAK,5,"Kısmi soyad"),
            new MCase("SANATCI_KISMI_E09","yüzyüzeyken kon","Yüzyüzeyken Konuşuruz","",SAK,5,"Uzun kısaltma"),
            new MCase("SANATCI_KISMI_E10","dolu kadehi ters t","Dolu Kadehi Ters Tut","",SAK,5,"Neredeyse tam"),

            // ══════════════════════════════════════════════════════════════════
            // ALBÜM — EK (10 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("ALBUM_E01","tarkan en iyi","Tarkan","",ALB,5,"Best-of sorgu"),
            new MCase("ALBUM_E02","blok3 en iyi","Blok3","",ALB,5,"Best-of sorgu"),
            new MCase("ALBUM_E03","poizi yeni albüm","Poizi","",ALB,5,"Yeni albüm"),
            new MCase("ALBUM_E04","semicenk albüm","Semicenk","",ALB,5,"Pop albüm"),
            new MCase("ALBUM_E05","mert demir albüm","Mert Demir","",ALB,5,"Pop albüm"),
            new MCase("ALBUM_E06","linet albüm","Linet","",ALB,5,"Pop albüm"),
            new MCase("ALBUM_E07","teoman albüm","Teoman","",ALB,5,"Rock albüm"),
            new MCase("ALBUM_E08","manga albüm","Manga","",ALB,5,"Rock albüm"),
            new MCase("ALBUM_E09","norm ender albüm","Norm Ender","",ALB,5,"Rap albüm"),
            new MCase("ALBUM_E10","velet albüm","Velet","",ALB,5,"Trap albüm"),

            // ══════════════════════════════════════════════════════════════════
            // PLAYLİST — EK (10 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("PLAYLIST_E01","türkçe rap","","",PL,5,"Tür"),
            new MCase("PLAYLIST_E02","trap türkçe","","",PL,5,"Alt tür"),
            new MCase("PLAYLIST_E03","arabesk","","",PL,5,"Türk türü"),
            new MCase("PLAYLIST_E04","türk pop hits","","",PL,5,"Chart"),
            new MCase("PLAYLIST_E05","workout müzik","","",PL,5,"Aktivite"),
            new MCase("PLAYLIST_E06","uyku müziği","","",PL,5,"Aktivite"),
            new MCase("PLAYLIST_E07","çalışma müziği","","",PL,5,"Aktivite"),
            new MCase("PLAYLIST_E08","yemek müziği","","",PL,5,"Aktivite"),
            new MCase("PLAYLIST_E09","2000ler türkçe","","",PL,5,"Dönem"),
            new MCase("PLAYLIST_E10","indie türkçe","","",PL,5,"Alt tür"),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · ÖZEL KARAKTER — EK (5 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_OZEL_E01","duman!","","",OZEL,5,"! ekli tek kelime"),
            new MCase("SARKI_OZEL_E03","git!","Blok3","git",OZEL,5,"! ekli kısa"),
            new MCase("SARKI_OZEL_E04","a canım..","Mabel Matiz","A Canım",OZEL,3,".. ekli"),
            new MCase("SARKI_OZEL_E05","geri ver #1","Wegh","Geri Ver",OZEL,5,"#1 ekli"),

            // ══════════════════════════════════════════════════════════════════
            // SANATÇI · YAZIM HATASI — EK (10 case, daha fazla ASCII fold + sık hata)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SANATCI_YAZ_E01","sagopa kajmr","Sagopa Kajmer","",SAYAZIM,5,"Harf eksik",true),
            new MCase("SANATCI_YAZ_E02","semıcenk","Semicenk","",SAYAZIM,5,"i→ı TR"),
            new MCase("SANATCI_YAZ_E03","blok 3","Blok3","",SAYAZIM,5,"Boşluklu yazım"),
            new MCase("SANATCI_YAZ_E04","ati 242","Ati242","",SAYAZIM,5,"Boşluklu yazım"),
            new MCase("SANATCI_YAZ_E05","sezn aksu","Sezen Aksu","",SAYAZIM,5,"e eksik",true),
            new MCase("SANATCI_YAZ_E06","yuzüzeyken","Yüzyüzeyken Konuşuruz","",SAYAZIM,5,"ü→u ASCII"),
            new MCase("SANATCI_YAZ_E07","mabel matıs","Mabel Matiz","",SAYAZIM,5,"z→s son harf",true),
            new MCase("SANATCI_YAZ_E08","poizı","Poizi","",SAYAZIM,5,"i→ı TR"),
            new MCase("SANATCI_YAZ_E09","hadıse","Hadise","",SAYAZIM,5,"i→ı TR"),
            new MCase("SANATCI_YAZ_E10","seldar ortaç","Serdar Ortaç","",SAYAZIM,5,"r→l typo",true),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · KISMİ AD — EK (13 case, pop/hip-hop/rock mix)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_KISMI_E01","şımarık tarkan","Tarkan","Şımarık",KISMI,1,"Şarkı + sanatçı"),
            new MCase("SARKI_KISMI_E02","kuzu kuzu tarkan","Tarkan","Kuzu Kuzu",KISMI,1,"Şarkı + sanatçı"),
            new MCase("SARKI_KISMI_E04","kötü adam sefo","Sefo","",KISMI,3,"Şarkı + sanatçı"),
            new MCase("SARKI_KISMI_E05","hata velet","Velet","hata",KISMI,3,"Şarkı + sanatçı"),
            new MCase("SARKI_KISMI_E06","bela sagopa","Sagopa Kajmer","Bela",KISMI,3,"Şarkı + sanatçı"),
            new MCase("SARKI_KISMI_E07","dur ceza","Ceza","Dur",KISMI,3,"Şarkı + sanatçı"),
            new MCase("SARKI_KISMI_E08","duman manga","Manga","Duman",KISMI,3,"Şarkı + sanatçı"),
            new MCase("SARKI_KISMI_E09","söyle yüzyüzeyken","Yüzyüzeyken Konuşuruz","",KISMI,3,"Şarkı + kısmi sanatçı"),
            new MCase("SARKI_KISMI_E10","dans et hadise","Hadise","Dans Et",KISMI,3,"Şarkı + sanatçı"),
            new MCase("SARKI_KISMI_E11","hüp hadise","Hadise","Hüp",KISMI,3,"Şarkı + sanatçı"),
            new MCase("SARKI_KISMI_E12","maraton ati242","Ati242","Maraton",KISMI,1,"Tam combo"),
            new MCase("SARKI_KISMI_E13","meğerse linet","Linet","Meğerse",KISMI,1,"Şarkı + sanatçı"),

            // ══════════════════════════════════════════════════════════════════
            // ŞARKI · COMBO — EK (10 case)
            // ══════════════════════════════════════════════════════════════════
            new MCase("SARKI_COMBO_E01","linet meğerse","Linet","Meğerse",COMBO,1,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_E02","wegh geri ver","Wegh","Geri Ver",COMBO,1,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_E03","dedublüman bunca","Dedublüman","Bunca Yıl",COMBO,3,"Kısmi şarkı"),
            new MCase("SARKI_COMBO_E04","canozan ağlama","Canozan","Ağlama Ben Ağlarım",COMBO,3,"Kısmi şarkı"),
            new MCase("SARKI_COMBO_E05","onur şan giderim","Onur Şan","Giderim Kırağınan",COMBO,3,"Kısmi şarkı"),
            new MCase("SARKI_COMBO_E06","velet hata","Velet","hata",COMBO,3,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_E07","sagopa bela","Sagopa Kajmer","Bela",COMBO,3,"Kısmi sanatçı"),
            new MCase("SARKI_COMBO_E08","ceza dur","Ceza","Dur",COMBO,3,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_E09","manga duman","Manga","Duman",COMBO,3,"Sanatçı + şarkı"),
            new MCase("SARKI_COMBO_E10","hadise hüp","Hadise","Hüp",COMBO,1,"Sanatçı + şarkı")

        ); // Stream.of
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Result record — hem Muud hem Spotify sonuçlarını tutar
    // ─────────────────────────────────────────────────────────────────────────
    record ResultRow(
            int no, String caseId, String term, String expArtist, String expTrack,
            String category, int topN, String variant, boolean correction,
            // Muud
            String muudStatus, String muudPos, String muudTop5,
            // Spotify
            String spotifyTop5,
            // Yorum
            String comparison
    ) {}

    private static final List<ResultRow>  ROWS    = Collections.synchronizedList(new ArrayList<>());
    private static MuudSearchApi          muud;
    private static SpotifyApi             spotify;
    private static boolean                spotifyEnabled = false;
    private static int                    rowNo   = 0;

    // ── Spotify cache ─────────────────────────────────────────────────────────
    private static final Path CACHE_FILE = Path.of("reports/kapsamli/spotify_cache.json");
    private static final Map<String, List<String>> SPOTIFY_CACHE = new LinkedHashMap<>();

    /** Cache'i diskten yükle */
    private static void loadCache() {
        if (!Files.exists(CACHE_FILE)) return;
        try {
            String json = Files.readString(CACHE_FILE);
            // Basit JSON parser — {"term":["r1","r2",...], ...}
            json = json.trim();
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}"))  json = json.substring(0, json.length() - 1);
            // Her entry: "term": ["r1","r2"]
            for (String entry : splitJsonEntries(json)) {
                int colon = entry.indexOf(':');
                if (colon < 0) continue;
                String key = entry.substring(0, colon).trim().replaceAll("^\"|\"$", "");
                String arrStr = entry.substring(colon + 1).trim();
                List<String> vals = parseJsonStringArray(arrStr);
                SPOTIFY_CACHE.put(key, vals);
            }
            System.out.println("📦 Spotify cache yüklendi: " + SPOTIFY_CACHE.size() + " entry");
        } catch (Exception e) {
            System.err.println("Cache okuma hatası: " + e.getMessage());
        }
    }

    /** Cache'i diske kaydet */
    private static synchronized void saveCache() {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            StringBuilder sb = new StringBuilder("{\n");
            boolean first = true;
            for (var e : SPOTIFY_CACHE.entrySet()) {
                if (!first) sb.append(",\n");
                sb.append("  ").append(jsonStr(e.getKey())).append(": [");
                List<String> vals = e.getValue();
                for (int i = 0; i < vals.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(jsonStr(vals.get(i)));
                }
                sb.append("]");
                first = false;
            }
            sb.append("\n}");
            Files.writeString(CACHE_FILE, sb.toString());
        } catch (IOException ex) {
            System.err.println("Cache yazma hatası: " + ex.getMessage());
        }
    }

    private static String jsonStr(String s) {
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
    }

    private static List<String> splitJsonEntries(String json) {
        // "key": [...] bloklarını bölmek için basit state machine
        List<String> entries = new ArrayList<>();
        int depth = 0; int start = 0;
        boolean inStr = false; char prev = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && prev != '\\') inStr = !inStr;
            if (!inStr) {
                if (c == '[') depth++;
                else if (c == ']') depth--;
                else if (c == ',' && depth == 0) {
                    entries.add(json.substring(start, i).trim());
                    start = i + 1;
                }
            }
            prev = c;
        }
        if (start < json.length()) entries.add(json.substring(start).trim());
        return entries;
    }

    private static List<String> parseJsonStringArray(String arrStr) {
        List<String> result = new ArrayList<>();
        arrStr = arrStr.trim();
        if (arrStr.startsWith("[")) arrStr = arrStr.substring(1);
        if (arrStr.endsWith("]"))  arrStr = arrStr.substring(0, arrStr.length() - 1);
        boolean inStr = false; StringBuilder cur = new StringBuilder(); char prev = 0;
        for (int i = 0; i < arrStr.length(); i++) {
            char c = arrStr.charAt(i);
            if (c == '"' && prev != '\\') {
                inStr = !inStr;
                if (!inStr && cur.length() > 0) { result.add(cur.toString()); cur.setLength(0); }
            } else if (inStr) {
                if (c == 'n' && prev == '\\') { cur.setLength(cur.length()-1); cur.append('\n'); }
                else if (c == '\\' && prev != '\\') { /* skip - will handle next char */ cur.append(c); }
                else cur.append(c);
            }
            prev = c;
        }
        return result;
    }

    @BeforeAll
    static void init() {
        muud = new MuudSearchApi();
        System.out.println("🎵 MuudKapsamliCollector → " + MuudSearchApi.baseUrl());
        loadCache();

        // Spotify credentials — önce system property, sonra spotify.properties
        String clientId     = System.getProperty("spotify.clientId");
        String clientSecret = System.getProperty("spotify.clientSecret");

        if (clientId == null || clientId.isBlank()) {
            // properties dosyasından dene
            try (var is = MuudKapsamliCollector.class.getClassLoader()
                    .getResourceAsStream("spotify.properties")) {
                if (is != null) {
                    Properties p = new Properties();
                    p.load(is);
                    clientId     = p.getProperty("client.id", "").trim();
                    clientSecret = p.getProperty("client.secret", "").trim();
                }
            } catch (IOException ignored) {}
        }

        if (clientId != null && !clientId.isBlank()
                && !clientId.equals("YOUR_CLIENT_ID_HERE")) {
            try {
                spotify       = new SpotifyApi(clientId, clientSecret);
                spotifyEnabled = true;
                System.out.println("✅ Spotify bağlantısı kuruldu — kıyaslama aktif");
            } catch (Exception e) {
                System.err.println("⚠ Spotify bağlanamadı: " + e.getMessage());
            }
        } else {
            System.out.println("ℹ Spotify credentials bulunamadı — sadece Muud sonuçları kaydedilecek");
        }
    }

    @AfterAll
    static void writeReport() throws IOException {
        long ok  = ROWS.stream().filter(r -> "OK".equals(r.muudStatus())).count();
        long nok = ROWS.stream().filter(r -> "NOK".equals(r.muudStatus())).count();
        System.out.printf("%nTamamlandı: %d case | Muud OK=%d | NOK=%d%n", ROWS.size(), ok, nok);
        writeExcel(ROWS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST RUNNER
    // ─────────────────────────────────────────────────────────────────────────
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    @Order(1)
    void run(MCase mc) {
        // ── MUUD ──────────────────────────────────────────────────────────────
        String muudStatus = "NOK";
        String muudPos    = "TOP-50'DE YOK";
        StringBuilder muudSb = new StringBuilder();

        try {
            Response res  = muud.search(mc.term(), "active-indices", 50, mc.correction());
            JsonPath jp   = res.jsonPath();
            String base   = MuudSearchUtils.getBasePath(jp);
            List<Object> list = MuudSearchUtils.resultsList(jp);

            int preview = Math.min(5, list.size());
            for (int i = 0; i < preview; i++) {
                String kind  = safeStr(jp.getString(base + "[" + i + "].data.kind"));
                String track = safeStr(jp.getString(base + "[" + i + "].data.songName"));
                if (track.isEmpty()) track = safeStr(jp.getString(base + "[" + i + "].data.albumName"));
                if (track.isEmpty()) track = safeStr(jp.getString(base + "[" + i + "].data.playlistName"));
                if (track.isEmpty()) track = safeStr(jp.getString(base + "[" + i + "].data.performerName"));
                String artist = MuudSearchUtils.getPerformerName(jp, base + "[" + i + "].data");
                Object popObj   = jp.get(base + "[" + i + "].data.popularity");
                Object scoreObj = jp.get(base + "[" + i + "].score");
                String pop   = popObj   != null ? String.format("%.2f", Double.parseDouble(popObj.toString()))   : "?";
                String score = scoreObj != null ? String.format("%.2f", Double.parseDouble(scoreObj.toString())) : "?";
                muudSb.append(i + 1).append(". [").append(kind).append("] ")
                      .append(track.isEmpty() ? artist : track)
                      .append(" — ").append(artist)
                      .append(" | pop=").append(pop).append(" | score=").append(score).append("\n");
            }
            if (list.isEmpty()) muudSb.append("(Boş sonuç)");

            String ea = mc.expArtist() == null ? "" : mc.expArtist().trim();
            String et = mc.expTrack()  == null ? "" : mc.expTrack().trim();

            if (!ea.isEmpty() || !et.isEmpty()) {
                int idx = !et.isEmpty()
                        ? MuudSearchUtils.findArtistAndTrackIndex(jp, 50, ea, et)
                        : MuudSearchUtils.findArtistIndex(jp, 50, ea);
                int pos = idx >= 0 ? idx + 1 : -1;
                muudPos    = pos > 0 ? "pos " + pos + "/50" : "TOP-50'DE YOK";
                muudStatus = pos > 0 && pos <= mc.topN() ? "OK" : "NOK";
            } else {
                muudStatus = list.isEmpty() ? "NOK" : "OK";
                muudPos    = list.isEmpty() ? "BOŞ" : "sonuç var";
            }
        } catch (Exception e) {
            muudSb.append("API HATA: ").append(e.getMessage());
        }

        // ── SPOTIFY (cache-first) ─────────────────────────────────────────────
        String spotifyTop5 = "N/A";
        if (spotifyEnabled || SPOTIFY_CACHE.containsKey(mc.term())) {
            try {
                List<String> results;
                if (SPOTIFY_CACHE.containsKey(mc.term())) {
                    // Cache hit — API çağrısı yok
                    results = SPOTIFY_CACHE.get(mc.term());
                } else {
                    // Cache miss — Spotify API çağır ve cache'e yaz
                    Thread.sleep(300); // rate limit önlemi
                    results = spotify.searchAll(mc.term(), 5);
                    SPOTIFY_CACHE.put(mc.term(), results);
                    saveCache(); // her yeni sonucu anında diske yaz
                }
                if (results.isEmpty()) {
                    spotifyTop5 = "(Boş sonuç)";
                } else {
                    StringBuilder sb2 = new StringBuilder();
                    int limit = Math.min(5, results.size());
                    for (int i = 0; i < limit; i++) {
                        sb2.append(i + 1).append(". ").append(results.get(i)).append("\n");
                    }
                    spotifyTop5 = sb2.toString().trim();
                }
            } catch (Exception e) {
                spotifyTop5 = "Spotify HATA: " + e.getMessage();
            }
        }

        // ── Kıyaslama yorumu ──────────────────────────────────────────────────
        String comparison = buildComparison(muudStatus, muudPos, spotifyTop5,
                mc.expArtist(), mc.expTrack());

        System.out.printf("[%s] %-40s Muud:%-15s Spotify:%s%n",
                mc.caseId(), "\"" + mc.term() + "\"",
                muudPos, spotifyEnabled ? "✓" : "–");

        synchronized (ROWS) {
            ROWS.add(new ResultRow(
                    ++rowNo, mc.caseId(), mc.term(),
                    mc.expArtist() == null ? "" : mc.expArtist(),
                    mc.expTrack()  == null ? "" : mc.expTrack(),
                    mc.category(), mc.topN(), mc.variant(), mc.correction(),
                    muudStatus, muudPos, muudSb.toString().trim(),
                    spotifyTop5, comparison
            ));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Basit kıyaslama yorum üretici
    // ─────────────────────────────────────────────────────────────────────────
    private static String buildComparison(String muudStatus, String muudPos,
                                          String spotifyTop5, String expArtist, String expTrack) {
        if ("N/A".equals(spotifyTop5) || spotifyTop5.startsWith("Spotify HATA")) {
            return "–";
        }
        String target = (expArtist != null && !expArtist.isBlank()) ? expArtist : expTrack;
        if (target == null || target.isBlank()) return "–";

        boolean muudOk    = "OK".equals(muudStatus);
        boolean spotifyOk = spotifyTop5.toLowerCase(java.util.Locale.ROOT)
                                       .contains(target.toLowerCase(java.util.Locale.ROOT));

        if (muudOk && spotifyOk)  return "✅ Her ikisi de buluyor";
        if (muudOk && !spotifyOk) return "🟢 Muud daha iyi";
        if (!muudOk && spotifyOk) return "🔴 Spotify daha iyi";
        return "⚠ İkisi de bulamadı";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXCEL YAZICI
    // ─────────────────────────────────────────────────────────────────────────
    private static void writeExcel(List<ResultRow> rows) throws IOException {
        Path dir = Path.of("reports/kapsamli");
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy_HH.mm"));
        String fn = dir + "/MuudKapsamli_" + ts + "_" + MuudSearchApi.ortam() + ".xlsx";

        XSSFWorkbook wb = new XSSFWorkbook();
        CellStyle hdr    = makeHeaderStyle(wb);
        CellStyle okSt   = makeColorStyle(wb, IndexedColors.LIGHT_GREEN);
        CellStyle nokSt  = makeColorStyle(wb, IndexedColors.ROSE);
        CellStyle muudWin= makeColorStyle(wb, new XSSFColor(new byte[]{(byte)198,(byte)239,(byte)206}, null));
        CellStyle spotWin= makeColorStyle(wb, new XSSFColor(new byte[]{(byte)255,(byte)199,(byte)206}, null));
        CellStyle bothOk = makeColorStyle(wb, new XSSFColor(new byte[]{(byte)220,(byte)230,(byte)241}, null));
        CellStyle wrap   = wb.createCellStyle(); wrap.setWrapText(true);
        wrap.setVerticalAlignment(VerticalAlignment.TOP);
        CellStyle boldSt = wb.createCellStyle();
        XSSFFont bf = wb.createFont(); bf.setBold(true); boldSt.setFont(bf);

        // ── Sheet 1: Özet ─────────────────────────────────────────────────────
        XSSFSheet ws0 = wb.createSheet("Özet");
        cell(ws0.createRow(0), 0, "MUUD vs SPOTIFY — KAPSAMLI KARŞILAŞTIRMA RAPORU", boldSt);
        cell(ws0.createRow(1), 0, "Tarih: " + ts + " | Ortam: " + MuudSearchApi.ortam()
                + " | Spotify: " + (spotifyEnabled ? "AKTİF" : "DEVRE DIŞI"), null);

        Row sh = ws0.createRow(3);
        for (int i = 0; i < new String[]{"Kategori","Toplam","Muud OK","Muud NOK","Muud %",
                "Spotify > Muud","Muud > Spotify","Her ikisi OK","İkisi de YOK"}.length; i++)
            cell(sh, i, new String[]{"Kategori","Toplam","Muud OK","Muud NOK","Muud %",
                    "Spotify > Muud","Muud > Spotify","Her ikisi OK","İkisi de YOK"}[i], hdr);

        Map<String, long[]> stats = new LinkedHashMap<>();
        for (String c : new String[]{TAM,VERSIYON,OZEL,KISMI,COMBO,SYAZIM,SAT,KLAVYE,SAK,SAYAZIM,PL,ALB})
            stats.put(c, new long[6]); // [muudOk, muudNok, spotWin, muudWin, bothOk, bothNok]

        for (ResultRow r : rows) {
            long[] s = stats.getOrDefault(r.category(), new long[6]);
            if ("OK".equals(r.muudStatus())) s[0]++; else s[1]++;
            String cmp = r.comparison();
            if ("🔴 Spotify daha iyi".equals(cmp))  s[2]++;
            if ("🟢 Muud daha iyi".equals(cmp))     s[3]++;
            if ("✅ Her ikisi de buluyor".equals(cmp)) s[4]++;
            if ("⚠ İkisi de bulamadı".equals(cmp))  s[5]++;
            stats.put(r.category(), s);
        }

        int sr = 4; long gOk = 0, gNok = 0;
        for (var e : stats.entrySet()) {
            long[] s = e.getValue();
            long tot = s[0] + s[1];
            if (tot == 0) continue;
            Row r = ws0.createRow(sr++);
            cell(r, 0, e.getKey(), null);
            cell(r, 1, tot, null);
            cell(r, 2, s[0], null);
            cell(r, 3, s[1], null);
            cell(r, 4, "%" + (tot > 0 ? 100 * s[0] / tot : 0), null);
            cell(r, 5, s[2], null);
            cell(r, 6, s[3], null);
            cell(r, 7, s[4], null);
            cell(r, 8, s[5], null);
            gOk += s[0]; gNok += s[1];
        }
        Row totRow = ws0.createRow(sr);
        cell(totRow, 0, "GENEL TOPLAM", boldSt);
        cell(totRow, 1, gOk + gNok, boldSt);
        cell(totRow, 2, gOk, boldSt);
        cell(totRow, 3, gNok, boldSt);
        cell(totRow, 4, "%" + (gOk + gNok > 0 ? 100 * gOk / (gOk + gNok) : 0), boldSt);

        int[] sw = {40,10,10,10,10,15,15,15,15};
        for (int i = 0; i < sw.length; i++) ws0.setColumnWidth(i, sw[i] * 256);

        // ── Sheet 2: Tüm Sonuçlar ─────────────────────────────────────────────
        XSSFSheet ws1 = wb.createSheet("Tüm Sonuçlar");
        String[] cols = {"No","Case ID","Arama Terimi","Beklenen Sanatçı","Beklenen Şarkı",
                "Kategori","TopN","Varyant","Correction",
                "Muud Durum","Muud Konum",
                "Muud Top 5 Sonuç",
                "Spotify Top 5 Sonuç",
                "Kıyaslama"};
        Row hdRow = ws1.createRow(0);
        for (int i = 0; i < cols.length; i++) cell(hdRow, i, cols[i], hdr);

        int ri = 1;
        for (ResultRow rr : rows) {
            Row dr = ws1.createRow(ri++);
            CellStyle mst = "OK".equals(rr.muudStatus()) ? okSt : nokSt;
            CellStyle cst = null;
            String cmp = rr.comparison();
            if ("✅ Her ikisi de buluyor".equals(cmp))  cst = bothOk;
            else if ("🟢 Muud daha iyi".equals(cmp))    cst = muudWin;
            else if ("🔴 Spotify daha iyi".equals(cmp)) cst = spotWin;

            cell(dr, 0,  rr.no(), null);
            cell(dr, 1,  rr.caseId(), null);
            cell(dr, 2,  rr.term(), null);
            cell(dr, 3,  rr.expArtist(), null);
            cell(dr, 4,  rr.expTrack(), null);
            cell(dr, 5,  rr.category(), null);
            cell(dr, 6,  rr.topN(), null);
            cell(dr, 7,  rr.variant(), null);
            cell(dr, 8,  rr.correction() ? "evet" : "hayır", null);
            cell(dr, 9,  rr.muudStatus(), mst);
            cell(dr, 10, rr.muudPos(), null);
            cell(dr, 11, rr.muudTop5(), wrap);
            cell(dr, 12, rr.spotifyTop5(), wrap);
            cell(dr, 13, cmp, cst);
            dr.setHeightInPoints(80);
        }
        int[] cw = {5,18,28,22,22,32,7,28,10,10,14,60,60,22};
        for (int i = 0; i < cw.length; i++) ws1.setColumnWidth(i, cw[i] * 256);

        // ── Sheet 3: Sadece NOK + Spotify daha iyi ───────────────────────────
        XSSFSheet ws2 = wb.createSheet("NOK & Spotify Farkı");
        Row hd2 = ws2.createRow(0);
        for (int i = 0; i < cols.length; i++) cell(hd2, i, cols[i], hdr);
        int r2i = 1;
        for (ResultRow rr : rows) {
            if ("OK".equals(rr.muudStatus())
                    && !"🔴 Spotify daha iyi".equals(rr.comparison())) continue;
            Row dr = ws2.createRow(r2i++);
            CellStyle mst = "OK".equals(rr.muudStatus()) ? okSt : nokSt;
            cell(dr, 0,  rr.no(), null);
            cell(dr, 1,  rr.caseId(), nokSt);
            cell(dr, 2,  rr.term(), null);
            cell(dr, 3,  rr.expArtist(), null);
            cell(dr, 4,  rr.expTrack(), null);
            cell(dr, 5,  rr.category(), null);
            cell(dr, 6,  rr.topN(), null);
            cell(dr, 7,  rr.variant(), null);
            cell(dr, 8,  rr.correction() ? "evet" : "hayır", null);
            cell(dr, 9,  rr.muudStatus(), mst);
            cell(dr, 10, rr.muudPos(), null);
            cell(dr, 11, rr.muudTop5(), wrap);
            cell(dr, 12, rr.spotifyTop5(), wrap);
            cell(dr, 13, rr.comparison(), "🔴 Spotify daha iyi".equals(rr.comparison()) ? spotWin : null);
            dr.setHeightInPoints(80);
        }
        for (int i = 0; i < cw.length; i++) ws2.setColumnWidth(i, cw[i] * 256);

        try (FileOutputStream fos = new FileOutputStream(fn)) { wb.write(fos); }
        wb.close();
        System.out.println("📂 Rapor kaydedildi: " + fn);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // YARDIMCI
    // ─────────────────────────────────────────────────────────────────────────
    private static String safeStr(String s) { return s == null ? "" : s.trim(); }

    private static void cell(Row r, int col, Object val, CellStyle st) {
        Cell c = r.createCell(col);
        if (val instanceof Number n) c.setCellValue(n.doubleValue());
        else c.setCellValue(val == null ? "" : val.toString());
        if (st != null) c.setCellStyle(st);
    }

    private static CellStyle makeHeaderStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont(); f.setBold(true); s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private static CellStyle makeColorStyle(XSSFWorkbook wb, IndexedColors color) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(color.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static CellStyle makeColorStyle(XSSFWorkbook wb, XSSFColor color) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(color);
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }
}
