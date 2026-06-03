package validation;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import util.MuudSearchUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ES mapping ile bir search response'unu karşılaştırır.
 *
 *  Kullanım:
 *
 *     MappingTypeValidator v = MappingTypeValidator.fromElasticsearch(
 *         "http://127.0.0.1:9200", "muud_song_flat_v2");
 *
 *     // ya da mapping JSON'unu dosyadan oku (ES gateway arkasındaysa):
 *     MappingTypeValidator v = MappingTypeValidator.fromMappingFile(
 *         Path.of("src/test/resources/muud_song_flat_v2.mapping.json"));
 *
 *     List<TypeMismatch> results = v.validateGatewayResponse(searchApiResponse);
 *
 *  Muud gateway response'unda dokümanlar topHits[i].data.* veya
 *  content[i].data.* altında olduğu için validator her bir dokümanın "data"
 *  alanına iner ve onun içindeki alanları ES mapping'iyle karşılaştırır.
 */
public class MappingTypeValidator {

    /** "data.numPlays" -> "long" gibi flatten edilmiş alan tipleri. */
    private final Map<String, String> flatExpectedTypes = new LinkedHashMap<>();

    /** "data" prefix'i her doküman içeriği bu prefix altında gezilir. */
    private String documentRootPrefix = "data";

    /** Sadece belirli alanları kontrol etmek istersen buraya eklenir. */
    private final List<String> includeOnly = new ArrayList<>();

    private MappingTypeValidator() {}

    // ---------------------------------------------------------------------
    // FACTORY METHODS
    // ---------------------------------------------------------------------

    /**
     * ES'ye doğrudan HTTP GET ile mapping'i çeker.
     * Mapping endpoint örneği: http://127.0.0.1:9200/muud_song_flat_v2/_mapping
     */
    public static MappingTypeValidator fromElasticsearch(String esBaseUrl, String indexName) {
        MappingTypeValidator v = new MappingTypeValidator();
        Response resp = RestAssured.given()
                .relaxedHTTPSValidation()
                .when()
                .get(esBaseUrl + "/" + indexName + "/_mapping")
                .then()
                .extract().response();
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("ES mapping çekilemedi. HTTP " + resp.statusCode()
                    + " body=" + resp.asString());
        }
        v.parseMapping(resp.jsonPath());
        return v;
    }

    /**
     * Mapping'i bir dosyadan yükler. ES'ye doğrudan erişimin yoksa (gateway arkasındaysa)
     * mapping JSON'unu sen kaydet, validator buradan okusun.
     *
     *   ÖRNEK: src/test/resources/muud_song_flat_v2.mapping.json
     */
    public static MappingTypeValidator fromMappingFile(Path path) throws IOException {
        MappingTypeValidator v = new MappingTypeValidator();
        String json = Files.readString(path);
        v.parseMapping(JsonPath.from(json));
        return v;
    }

    /**
     * Mapping JSON'unu doğrudan string olarak yükler — testlerde işine yarar.
     */
    public static MappingTypeValidator fromMappingJson(String json) {
        MappingTypeValidator v = new MappingTypeValidator();
        v.parseMapping(JsonPath.from(json));
        return v;
    }

    // ---------------------------------------------------------------------
    // CONFIG
    // ---------------------------------------------------------------------

    /**
     * Doküman içindeki alanların başladığı prefix.
     * Muud gateway response'unda her doküman "data" altında geldiği için varsayılan "data".
     * ES'ye doğrudan vuruyorsan "_source" kullanmalısın.
     */
    public MappingTypeValidator withDocumentRootPrefix(String prefix) {
        this.documentRootPrefix = prefix == null ? "" : prefix;
        return this;
    }

    /**
     * Sadece belirli alanları kontrol et.
     * Örn: onlyValidate("numPlays", "isActive", "performers")
     */
    public MappingTypeValidator onlyValidate(String... paths) {
        includeOnly.clear();
        for (String p : paths) includeOnly.add(p);
        return this;
    }

    public Map<String, String> getFlatExpectedTypes() {
        return flatExpectedTypes;
    }

    // ---------------------------------------------------------------------
    // VALIDATE — Muud Gateway response
    // ---------------------------------------------------------------------

    /**
     * Muud gateway'inden gelen response'u doğrular.
     * topHits varsa onu, yoksa content listesini gezer. Her dokümanın
     * 'data' alanını alıp mapping'le karşılaştırır.
     */
    public List<TypeMismatch> validateGatewayResponse(Response response) {
        return validateGatewayResponse(response.jsonPath());
    }

    public List<TypeMismatch> validateGatewayResponse(JsonPath jp) {
        String basePath = MuudSearchUtils.getBasePath(jp); // "topHits" veya "content"
        List<Object> docs = jp.getList(basePath);
        if (docs == null) return List.of();

        List<TypeMismatch> out = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            String docId = resolveDocId(jp, basePath, i);
            // Her doküman içinde data alanı → bunun altındaki alanları gez
            Object dataNode = jp.get(basePath + "[" + i + "].data");
            if (!(dataNode instanceof Map<?, ?>)) {
                continue; // data yoksa veya tanımsızsa atla
            }
            validateMap("", (Map<?, ?>) dataNode, docId, out);
        }
        return out;
    }

    /**
     * Tipik ES response'u (hits.hits[]._source).
     * Senin projende gerek olmayabilir ama bütünlük için ekledim.
     */
    public List<TypeMismatch> validateElasticsearchResponse(Response response) {
        JsonPath jp = response.jsonPath();
        List<Object> docs = jp.getList("hits.hits");
        if (docs == null) return List.of();
        List<TypeMismatch> out = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            String docId = jp.getString("hits.hits[" + i + "]._id");
            if (docId == null) docId = "doc#" + i;
            Object src = jp.get("hits.hits[" + i + "]._source");
            if (src instanceof Map<?, ?>) {
                validateMap("", (Map<?, ?>) src, docId, out);
            }
        }
        return out;
    }

    private String resolveDocId(JsonPath jp, String basePath, int i) {
        // Önce data.id, sonra _id, yoksa indeks numarası
        Object id = jp.get(basePath + "[" + i + "].data.id");
        if (id == null) id = jp.get(basePath + "[" + i + "].id");
        if (id == null) id = jp.get(basePath + "[" + i + "]._id");
        return id == null ? ("doc#" + i) : id.toString();
    }

    // ---------------------------------------------------------------------
    // MAPPING PARSE
    // ---------------------------------------------------------------------

    /**
     * ES'nin _mapping endpoint response'u şu yapıdadır:
     *   { "<indexName>": { "mappings": { "properties": {...} } } }
     *
     * Bu metot ilk anahtarı bulup .mappings.properties altına iner ve düzleştirir.
     */
    private void parseMapping(JsonPath jp) {
        // En üst anahtarı bul (ES gerçek index ismini döner, alias ile çağırsak bile)
        Map<String, Object> root = jp.getMap("");
        if (root == null || root.isEmpty()) {
            throw new IllegalStateException("Mapping response'u boş.");
        }
        String topKey = root.keySet().iterator().next();

        Object propsObj = jp.get(topKey + ".mappings.properties");
        if (!(propsObj instanceof Map<?, ?>)) {
            throw new IllegalStateException("'" + topKey + ".mappings.properties' bulunamadı.");
        }
        flatExpectedTypes.clear();
        flattenProperties("", (Map<?, ?>) propsObj, flatExpectedTypes);
    }

    @SuppressWarnings("unchecked")
    private void flattenProperties(String prefix, Map<?, ?> props, Map<String, String> out) {
        for (Map.Entry<?, ?> e : props.entrySet()) {
            String name = String.valueOf(e.getKey());
            Object def  = e.getValue();
            if (!(def instanceof Map<?, ?> defMap)) continue;

            String path = prefix.isEmpty() ? name : prefix + "." + name;
            Object t = defMap.get("type");
            if (t instanceof String typeStr) {
                out.put(path, typeStr);
            }
            Object nested = defMap.get("properties");
            if (nested instanceof Map<?, ?> nestedMap) {
                if (!(t instanceof String)) {
                    out.put(path, "object");
                }
                flattenProperties(path, nestedMap, out);
            }
        }
    }

    // ---------------------------------------------------------------------
    // DOKÜMAN GEZME
    // ---------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void validateMap(String prefix, Map<?, ?> node, String docId, List<TypeMismatch> out) {
        for (Map.Entry<?, ?> entry : node.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            String path = prefix.isEmpty() ? key : prefix + "." + key;

            if (!includeOnly.isEmpty() && !matchesIncludeFilter(path)) continue;

            String expected = flatExpectedTypes.get(path);

            if (expected == null) {
                out.add(new TypeMismatch(docId, fullPath(path), "(none)",
                        ESTypeMapper.jsonTypeName(value), trim(value),
                        TypeMismatch.Status.MISSING_IN_MAPPING,
                        "Mapping'de tanımlı değil"));
                continue;
            }

            if (value == null) {
                out.add(new TypeMismatch(docId, fullPath(path), expected, "NULL", "null",
                        TypeMismatch.Status.NULL_VALUE, "Değer null"));
                continue;
            }

            if (ESTypeMapper.isSkippable(expected)) {
                out.add(new TypeMismatch(docId, fullPath(path), expected,
                        ESTypeMapper.jsonTypeName(value), trim(value),
                        TypeMismatch.Status.PASS, "Tip kontrolü atlanan kategori"));
                continue;
            }

            if (ESTypeMapper.isObjectOrNested(expected)) {
                if (value instanceof List<?> list) {
                    for (Object child : list) {
                        if (child instanceof Map<?, ?> childMap) {
                            validateMap(path, childMap, docId, out);
                        }
                    }
                } else if (value instanceof Map<?, ?> map) {
                    validateMap(path, map, docId, out);
                } else {
                    out.add(new TypeMismatch(docId, fullPath(path), expected,
                            ESTypeMapper.jsonTypeName(value), trim(value),
                            TypeMismatch.Status.FAIL,
                            "Object/Nested bekleniyordu"));
                }
                continue;
            }

            if (value instanceof List<?> list) {
                // skaler alan ama array gelmiş — her elemanı tek tek doğrula
                int idx = 0;
                for (Object item : list) {
                    String itemPath = path + "[" + (idx++) + "]";
                    if (!ESTypeMapper.isCompatible(expected, item)) {
                        out.add(new TypeMismatch(docId, fullPath(itemPath), expected,
                                ESTypeMapper.jsonTypeName(item), trim(item),
                                TypeMismatch.Status.FAIL,
                                "Array elemanı beklenen tipte değil"));
                    }
                }
                continue;
            }

            boolean ok = ESTypeMapper.isCompatible(expected, value);
            out.add(new TypeMismatch(docId, fullPath(path), expected,
                    ESTypeMapper.jsonTypeName(value), trim(value),
                    ok ? TypeMismatch.Status.PASS : TypeMismatch.Status.FAIL,
                    ok ? "" : "Beklenen tip ile uyuşmuyor"));
        }
    }

    private boolean matchesIncludeFilter(String path) {
        for (String f : includeOnly) {
            if (path.equals(f) || path.startsWith(f + ".")) return true;
        }
        return false;
    }

    private String fullPath(String innerPath) {
        return documentRootPrefix.isEmpty()
                ? innerPath
                : documentRootPrefix + "." + innerPath;
    }

    private static String trim(Object value) {
        if (value == null) return "null";
        String s = value.toString();
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }
}
