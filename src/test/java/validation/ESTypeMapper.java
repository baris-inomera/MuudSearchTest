package validation;

import io.restassured.path.json.JsonPath;

import java.util.Set;

/**
 * Elasticsearch mapping tipi ile dönen JSON değerinin tipinin
 * uyumlu olup olmadığını söyler.
 *
 * Kaynak: https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-types.html
 *
 * Buraya RestAssured JsonPath kullanılarak hangi tipte bir değer geldiğini
 * öğrenmek için pratik bir yardımcı eklenmiştir.
 */
public final class ESTypeMapper {

    private static final Set<String> INTEGER_TYPES =
            Set.of("long", "integer", "short", "byte", "unsigned_long");

    private static final Set<String> FLOAT_TYPES =
            Set.of("float", "double", "half_float", "scaled_float");

    private static final Set<String> STRING_TYPES =
            Set.of("keyword", "text", "constant_keyword", "wildcard", "ip", "version");

    private static final Set<String> BOOL_TYPES = Set.of("boolean");

    /** date alanı ES'de epoch ms (number) veya ISO string olarak gelebilir. */
    private static final Set<String> DATE_TYPES = Set.of("date", "date_nanos");

    /**
     * Tip kontrolü atlanan kategoriler — bunlar response'ta zaten bulunmayabilir
     * ya da yapısı çok özel. Manuel kontrol edilmesi tavsiye edilir.
     */
    private static final Set<String> SKIP_TYPES =
            Set.of("completion", "binary", "geo_point", "geo_shape",
                   "search_as_you_type", "rank_feature", "rank_features",
                   "histogram", "join", "alias");

    private ESTypeMapper() {}

    /**
     * Bir ES tipinin verilen değerle uyumlu olup olmadığını söyler.
     *
     * @param esType   "long", "keyword" gibi
     * @param value    JSON'dan gelen ham nesne (Integer/Long/String/Boolean/Map/List/null)
     */
    public static boolean isCompatible(String esType, Object value) {
        if (esType == null) return true;
        if (value == null) return true; // null ayrı raporlanır
        String t = esType.toLowerCase();
        if (SKIP_TYPES.contains(t)) return true;

        if (INTEGER_TYPES.contains(t)) {
            // long bekleniyor; tam sayı (Integer/Long) kabul. Boolean SAYI sayılmaz.
            return (value instanceof Integer) || (value instanceof Long) || (value instanceof Short) || (value instanceof Byte);
        }
        if (FLOAT_TYPES.contains(t)) {
            // float/double her türlü Number'ı kabul edebilir (long da olabilir)
            return (value instanceof Number) && !(value instanceof Boolean);
        }
        if (BOOL_TYPES.contains(t)) {
            return value instanceof Boolean;
        }
        if (STRING_TYPES.contains(t)) {
            return value instanceof String;
        }
        if (DATE_TYPES.contains(t)) {
            // ISO string veya epoch number
            return (value instanceof String)
                    || ((value instanceof Number) && !(value instanceof Boolean));
        }
        if ("object".equals(t) || "nested".equals(t)) {
            return (value instanceof java.util.Map) || (value instanceof java.util.List);
        }
        // tanınmayan tip → uyarı için PASS dön
        return true;
    }

    /**
     * JSON değeri için okunabilir bir tip ismi döner.
     * Excel raporunda "actualJsonType" sütununa basılır.
     */
    public static String jsonTypeName(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) return "NUMBER_INT";
        if (value instanceof Float || value instanceof Double) return "NUMBER_FLOAT";
        if (value instanceof Number) return "NUMBER";
        if (value instanceof String) return "STRING";
        if (value instanceof java.util.List) return "ARRAY";
        if (value instanceof java.util.Map) return "OBJECT";
        return value.getClass().getSimpleName().toUpperCase();
    }

    public static boolean isSkippable(String esType) {
        if (esType == null) return true;
        return SKIP_TYPES.contains(esType.toLowerCase());
    }

    public static boolean isObjectOrNested(String esType) {
        if (esType == null) return false;
        String t = esType.toLowerCase();
        return "object".equals(t) || "nested".equals(t);
    }

    /**
     * RestAssured JsonPath kolaylığı — verilen yolun değerini ham olarak çeker.
     * Tip dönüştürmesi YAPMAZ; .getString gibi metotlar agresif dönüşüm yapar
     * ve "true" → boolean gibi karışıklığa yol açar. .get() ham nesneyi verir.
     */
    public static Object rawAt(JsonPath jp, String path) {
        return jp.get(path);
    }
}
