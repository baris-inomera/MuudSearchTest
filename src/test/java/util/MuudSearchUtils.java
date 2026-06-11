package util;

import domain.Rule;
import io.restassured.path.json.JsonPath;

import java.util.List;
import java.util.Locale;

public class MuudSearchUtils {

    // Türkçe Locale — büyük/küçük harf normalizasyonu için
    private static final Locale TR = new Locale("tr", "TR");

    // --- YENİ EKLENEN DİNAMİK YOL BULUCU ---
    // Gateway'in huyuna göre JSON'da topHits varsa onu, yoksa content'i seçer
    public static String getBasePath(JsonPath jp) {
        if (jp.get("topHits") != null) {
            return "topHits"; // General (active-indices) aramasıysa
        }
        return "content"; // Tekil indeks aramasıysa
    }

    public static String getIndexName(String excelType) {
        String t = excelType == null ? "" : excelType.toLowerCase(Locale.ROOT);
        return switch (t) {
            // Güncel aktif index ID'leri (Mayıs 2026)
            //   Albums       → 2   (muud_album_flat_v2)
            //   Performers   → 3   (muud_performer_flat_v2)
            //   Playlists    → 4   (muud_playlist_flat_v2)
            //   Songs        → 5   (muud_song_flat_v2)
            //   Videos       → 6   (muud_video_flat_v2)
            //   Vektors      → 49  (muud_song_vector_v4)
            case "album"     -> "2";
            case "performer" -> "3";
            case "playlist"  -> "4";

            //     case "songs", "song" -> "5";
            case "video", "videos" -> "6";
              case "vector", "vectors" -> "49";
            case "general"   -> "active-indices"; // Tüm aktif indekslerde ara
            default -> "active-indices"; // Tanımsızsa yine tüm aktiflerde ara
        };
    }

    public static List<Object> resultsList(JsonPath jp) {
        List<Object> list = jp.getList(getBasePath(jp));
        return list == null ? List.of() : list;
    }

    public static String safeStr(String s) {
        return s == null ? "" : s;
    }

    /**
     * performerName (flat indeks) veya performerNames (vector indeks) alanından
     * sanatçı adını okur. İkisini de dener, doldurulan birini döndürür.
     */
    public static String getPerformerName(JsonPath jp, String dataPath) {
        String name = safeStr(jp.getString(dataPath + ".performerName"));
        if (name.isEmpty()) {
            name = safeStr(jp.getString(dataPath + ".performerNames"));
        }
        return name;
    }

    /**
     * Büyük/küçük harf DUYARSIZ ama karakter DUYARLI karşılaştırma.
     *
     * Türkçe locale kullanılır:
     *   - "TARKAN" == "Tarkan" → true  (harf büyüklüğü önemsiz)
     *   - "Sıla"   == "sıla"  → true  (harf büyüklüğü önemsiz)
     *   - "Sila"   == "Sıla"  → FALSE (i ≠ ı, farklı karakterler → doğru davranış!)
     *
     * ÖNEMLİ: eski sürümdeki Türkçe→ASCII dönüşümü (ş→s, ğ→g vb.) KALDIRILDI.
     * Bu sayede G14 (büyük/küçük harf) ve G15 (ASCII alternatif) testleri gerçekçi sonuç
     * verir; dönüşümü test edilen şey test yardımcısı değil, arama motoru olur.
     */
    public static boolean containsTRInsensitive(String actual, String expected) {
        if (expected == null || expected.isBlank()) return true;
        if (actual == null) return false;
        return actual.toLowerCase(TR).contains(expected.toLowerCase(TR));
    }

    public static String prettyRule(Rule rule) {
        if (rule == null) return "KURAL TANIMSIZ";
        return rule.name();
    }

    // --- ARAMA METOTLARI (DİNAMİK YOL KULLANIYORLAR) ---

    public static int findArtistIndex(JsonPath jp, int n, String expArtist) {
        String basePath = getBasePath(jp);
        for (int i = 0; i < Math.max(0, n); i++) {
            String artist = getPerformerName(jp, basePath + "[" + i + "].data");
            if (containsTRInsensitive(artist, expArtist)) return i;
        }
        return -1;
    }

    public static int findArtistAndTrackIndex(JsonPath jp, int n, String expArtist, String expTrack) {
        String basePath = getBasePath(jp);
        for (int i = 0; i < Math.max(0, n); i++) {
            String artist = getPerformerName(jp, basePath + "[" + i + "].data");

            // Active-indices karışık içerik döner: şarkı, albüm ve sanatçı kayıtları.
            //
            //  Şarkı kaydı  → songName dolu,  albumName boş
            //  Albüm kaydı  → songName boş,   albumName dolu   → albumName'e fallback
            //  Sanatçı kaydı→ songName boş,   albumName boş    → iki alan da boş
            //
            String track = safeStr(jp.getString(basePath + "[" + i + "].data.songName"));
            if (track.isEmpty()) {
                track = safeStr(jp.getString(basePath + "[" + i + "].data.albumName"));
            }

            boolean artistOk = expArtist.isBlank() || containsTRInsensitive(artist, expArtist);

            // expTrack belirtilmişse kayıt içinde track/albüm adı MUTLAKA eşleşmeli.
            // Performer-only kayıtları (songName ve albumName boş) geçerli eşleşme sayılmaz.
            // Örn. "ille de sen" → Azer Bülbül sanatçı kaydı bulunsa da
            //      İlle De Sen şarkısı aynı kayıtta yoksa NOK olarak işaretlenir.
            boolean trackOk = expTrack.isBlank() || containsTRInsensitive(track, expTrack);

            if (artistOk && trackOk) return i;
        }
        return -1;
    }

    public static int findAlbumKeywordIndex(JsonPath jp, int n, String keyword) {
        String basePath = getBasePath(jp);
        for (int i = 0; i < Math.max(0, n); i++) {
            // Active-indices'te karışık kayıt tipleri gelir:
            //   Playlist kaydı   → playlistName dolu
            //   Albüm kaydı      → albumName dolu
            //   Şarkı kaydı      → songName dolu  (active-indices'te sık görülür)
            //   Sanatçı kaydı    → performerName dolu
            // Herhangi birinde keyword geçiyorsa geçerli eşleşme sayılır.
            String albumName    = safeStr(jp.getString(basePath + "[" + i + "].data.albumName"));
            String playlistName = safeStr(jp.getString(basePath + "[" + i + "].data.playlistName"));
            String songName     = safeStr(jp.getString(basePath + "[" + i + "].data.songName"));
            String perfName     = getPerformerName(jp, basePath + "[" + i + "].data");

            if (containsTRInsensitive(albumName, keyword)
                    || containsTRInsensitive(playlistName, keyword)
                    || containsTRInsensitive(songName, keyword)
                    || containsTRInsensitive(perfName, keyword)) {
                return i;
            }
        }
        return -1;
    }
}