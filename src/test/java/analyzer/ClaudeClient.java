package analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Anthropic Messages API istemcisi.
 *
 * Tasarim kararlari:
 *  - temperature = 0  -> ayni girdi ayni cikti (eski denemede "tutarsiz sonuc" sorununun ana sebebi)
 *  - tool_choice ile ZORUNLU yapisal cikti -> serbest metin parse edilmez, uydurma kategori gelmez
 *  - 429/5xx icin ustel backoff + Retry-After
 *  - Ek bagimlilik yok: java.net.http + Jackson (spring-boot-starter-web ile zaten geliyor)
 */
public final class ClaudeClient {

    private static final ObjectMapper M = new ObjectMapper();

    private final HttpClient http;
    private final String apiKey;
    private final String baseUrl;

    public ClaudeClient() {
        this.apiKey = AnalyzerConfig.apiKey();
        this.baseUrl = AnalyzerConfig.baseUrl();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * Verilen tool semasina uygun yapisal cikti dondurur.
     *
     * @return tool_use bloklarindaki "input" nesnesi
     */
    public JsonNode structured(String model, String system, String userMessage,
                               String toolName, String toolDescription,
                               ObjectNode inputSchema, int maxTokens) throws Exception {

        ObjectNode tool = M.createObjectNode();
        tool.put("name", toolName);
        tool.put("description", toolDescription);
        tool.set("input_schema", inputSchema);

        ArrayNode tools = M.createArrayNode();
        tools.add(tool);

        ObjectNode toolChoice = M.createObjectNode();
        toolChoice.put("type", "tool");
        toolChoice.put("name", toolName);

        ObjectNode msg = M.createObjectNode();
        msg.put("role", "user");
        msg.put("content", userMessage);
        ArrayNode messages = M.createArrayNode();
        messages.add(msg);

        ObjectNode body = M.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0);
        body.put("system", system);
        body.set("messages", messages);
        body.set("tools", tools);
        body.set("tool_choice", toolChoice);

        String payload = M.writeValueAsString(body);

        Exception last = null;
        int retries = AnalyzerConfig.maxRetries();

        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/v1/messages"))
                        .timeout(Duration.ofSeconds(AnalyzerConfig.timeoutSeconds()))
                        .header("content-type", "application/json")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .POST(HttpRequest.BodyPublishers.ofString(payload,
                                java.nio.charset.StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(
                        java.nio.charset.StandardCharsets.UTF_8));

                int sc = res.statusCode();
                if (sc == 200) {
                    return extractToolInput(M.readTree(res.body()), toolName);
                }
                if (sc == 429 || sc >= 500) {
                    long wait = retryAfterMillis(res, attempt);
                    last = new IllegalStateException("HTTP " + sc + ": " + brief(res.body()));
                    if (attempt < retries) { Thread.sleep(wait); continue; }
                }
                throw new IllegalStateException("HTTP " + sc + ": " + brief(res.body()));

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            } catch (Exception e) {
                last = e;
                if (attempt < retries) {
                    Thread.sleep(backoff(attempt));
                    continue;
                }
            }
        }
        throw last == null ? new IllegalStateException("bilinmeyen hata") : last;
    }

    private static JsonNode extractToolInput(JsonNode root, String toolName) {
        JsonNode content = root.path("content");
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("tool_use".equals(block.path("type").asText())
                        && toolName.equals(block.path("name").asText())) {
                    return block.path("input");
                }
            }
        }
        throw new IllegalStateException("Yanitta tool_use blogu yok: " + brief(root.toString()));
    }

    private static long retryAfterMillis(HttpResponse<String> res, int attempt) {
        return res.headers().firstValue("retry-after")
                .map(v -> {
                    try { return Long.parseLong(v.trim()) * 1000L; }
                    catch (Exception e) { return backoff(attempt); }
                })
                .orElse(backoff(attempt));
    }

    private static long backoff(int attempt) {
        long base = 1000L << Math.min(attempt, 4);           // 1s, 2s, 4s, 8s, 16s
        return base + (long) (Math.random() * 400);          // jitter
    }

    private static String brief(String s) {
        if (s == null) return "";
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 400 ? one.substring(0, 400) + "..." : one;
    }

    public static ObjectMapper mapper() {
        return M;
    }
}
