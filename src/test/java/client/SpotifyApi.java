package client;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Spotify Web API istemcisi.
 *
 * Auth: Client Credentials Flow
 *   POST https://accounts.spotify.com/api/token
 *   Authorization: Basic base64(clientId:clientSecret)
 *   body: grant_type=client_credentials
 *
 * Kullanım:
 *   SpotifyApi api = new SpotifyApi(clientId, clientSecret);
 *   List<String> results = api.searchTracks("tarkan şımarık", 5);
 *   // → ["Şımarık — Tarkan", "Karma — Tarkan", ...]
 *
 * Credentials sistem property'sinden okunur:
 *   -Dspotify.clientId=xxx -Dspotify.clientSecret=yyy
 */
public class SpotifyApi {

    private static final String ACCOUNTS = "https://accounts.spotify.com";
    private static final String API       = "https://api.spotify.com";

    private final String clientId;
    private final String clientSecret;
    private volatile String accessToken;

    public SpotifyApi(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank())
            throw new IllegalArgumentException(
                "Spotify clientId eksik! -Dspotify.clientId=... ile ver.");
        if (clientSecret == null || clientSecret.isBlank())
            throw new IllegalArgumentException(
                "Spotify clientSecret eksik! -Dspotify.clientSecret=... ile ver.");
        this.clientId     = clientId;
        this.clientSecret = clientSecret;
        this.accessToken  = fetchToken();
    }

    // ── Public factory — sistem property'den okur ─────────────────────────────
    public static SpotifyApi fromSystemProperties() {
        return new SpotifyApi(
            System.getProperty("spotify.clientId"),
            System.getProperty("spotify.clientSecret")
        );
    }

    // ── Token alma ────────────────────────────────────────────────────────────
    private String fetchToken() {
        String creds = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Authorization", "Basic " + creds)
                .contentType("application/x-www-form-urlencoded")
                .body("grant_type=client_credentials")
                .post(ACCOUNTS + "/api/token");

        if (r.statusCode() != 200) {
            throw new RuntimeException(
                "Spotify token hatası HTTP " + r.statusCode()
                + "\nBody: " + r.body().asString()
                + "\nCredentials doğru mu? Spotify Developer Console'da kontrol et.");
        }

        String token = r.jsonPath().getString("access_token");
        System.out.println("🎵 Spotify access_token alındı. (ilk 8 char: "
                + token.substring(0, Math.min(8, token.length())) + "...)");
        return token;
    }

    /**
     * Spotify'da track + artist + album araması yapar; sonuçları interleave ederek
     * MUUD'daki gibi karma top-N liste döner.
     *
     * Format:
     *   [tracks]  TrackName — ArtistName
     *   [artists] ArtistName
     *   [albums]  AlbumName — ArtistName
     *
     * @param query  arama terimi
     * @param limit  toplam max sonuç sayısı (örn. 5)
     * @return karma sonuç listesi
     */
    public List<String> searchAll(String query, int limit) {
        // Her type için ayrı ayrı limit kadar çek, sonra interleave et
        int perType = limit; // her typeden en fazla limit kadar alınır
        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .queryParam("q", query)
                .queryParam("type", "track,artist,album")
                .queryParam("limit", Math.min(perType, 10))
                .get(API + "/v1/search");

        if (r.statusCode() == 401) {
            System.out.println("⚠ Spotify token süresi doldu, yenileniyor...");
            accessToken = fetchToken();
            return searchAll(query, limit);
        }
        if (r.statusCode() == 429) {
            String retryAfter = r.header("Retry-After");
            int waitSec = retryAfter != null ? Integer.parseInt(retryAfter.trim()) : 10;
            if (waitSec > 60) {
                System.out.printf("⛔ Spotify rate limit çok uzun (%dsn) — '%s' atlanıyor%n", waitSec, query);
                return new ArrayList<>();
            }
            System.out.printf("⏳ Spotify rate limit (429) — %dsn bekleniyor | q=%s%n", waitSec, query);
            try { Thread.sleep(waitSec * 1000L); } catch (InterruptedException ignored) {}
            return searchAll(query, limit);
        }
        if (r.statusCode() != 200) {
            System.err.println("⚠ Spotify arama hatası HTTP " + r.statusCode()
                    + " | q=" + query);
            return new ArrayList<>();
        }

        JsonPath jp = r.jsonPath();

        // ── Her type'tan sonuçları topla ──────────────────────────────────────
        List<String> tracks  = parseTracks(jp, perType);
        List<String> artists = parseArtists(jp, perType);
        List<String> albums  = parseAlbums(jp, perType);

        // ── Kind-grouped: songs → performers → albums (interleave değil) ─────
        // Spotify API zaten tek istekte her type için limit kadar sonuç döndürür.
        // Önce interleave edip 20'sini atmak yerine hepsini saklıyoruz (≤30 item).
        // similarity() artık kind-relative rank karşılaştırması yapıyor.
        List<String> merged = new ArrayList<>(tracks);
        merged.addAll(artists);
        merged.addAll(albums);
        return merged;
    }

    /** Geriye dönük uyumluluk için — sadece track arar */
    public List<String> searchTracks(String query, int limit) {
        Response r = RestAssured.given()
                .relaxedHTTPSValidation()
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .queryParam("q", query)
                .queryParam("type", "track")
                .queryParam("market", "TR")
                .queryParam("limit", Math.min(limit, 50))
                .get(API + "/v1/search");

        if (r.statusCode() == 401) {
            accessToken = fetchToken();
            return searchTracks(query, limit);
        }
        if (r.statusCode() != 200) return new ArrayList<>();
        return parseTracks(r.jsonPath(), limit);
    }

    // ── Parser yardımcıları ───────────────────────────────────────────────────
    private List<String> parseTracks(JsonPath jp, int limit) {
        List<Object> items = jp.getList("tracks.items");
        if (items == null) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(items.size(), limit); i++) {
            String name   = safe(jp.getString("tracks.items[" + i + "].name"));
            String artist = safe(jp.getString("tracks.items[" + i + "].artists[0].name"));
            if (!name.isEmpty()) out.add("[songs] " + name + " — " + artist);
        }
        return out;
    }

    private List<String> parseArtists(JsonPath jp, int limit) {
        List<Object> items = jp.getList("artists.items");
        if (items == null) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(items.size(), limit); i++) {
            String name = safe(jp.getString("artists.items[" + i + "].name"));
            if (!name.isEmpty()) out.add("[performers] " + name);
        }
        return out;
    }

    private List<String> parseAlbums(JsonPath jp, int limit) {
        List<Object> items = jp.getList("albums.items");
        if (items == null) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(items.size(), limit); i++) {
            String name   = safe(jp.getString("albums.items[" + i + "].name"));
            String artist = safe(jp.getString("albums.items[" + i + "].artists[0].name"));
            if (!name.isEmpty()) out.add("[albums] " + name + (artist.isEmpty() ? "" : " — " + artist));
        }
        return out;
    }

    private List<String> parsePlaylists(JsonPath jp, int limit) {
        List<Object> items = jp.getList("playlists.items");
        if (items == null) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(items.size(), limit); i++) {
            String name = safe(jp.getString("playlists.items[" + i + "].name"));
            if (!name.isEmpty()) out.add("[playlists] " + name);
        }
        return out;
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
