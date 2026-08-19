package dev.minicoder.llm.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.minicoder.agent.CancellationToken;
import dev.minicoder.llm.LlmModels.ProviderBudget;
import dev.minicoder.llm.LlmModels.ProviderCursor;
import dev.minicoder.llm.LlmModels.ProviderRequest;
import dev.minicoder.llm.LlmModels.ProviderResponse;
import dev.minicoder.llm.LlmModels.ToolExchange;
import dev.minicoder.llm.ProviderException;
import dev.minicoder.security.Redactor;
import dev.minicoder.tool.ToolDefinition;
import dev.minicoder.tool.ToolResult;
import dev.minicoder.tool.ToolSchemas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过本地 HTTP fixture 验证 DeepSeek 无状态回放、游标上限、错误矩阵和脱敏。
 *
 * @author Self David (dsfgis@gmail.com)
 */
class DeepSeekProviderContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TEST_KEY = "sk-deepseek-test-secret-0000";
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void resolvesDeepSeekBaseUrlsWithoutInjectingOpenAiVersionPath() {
        assertEquals("https://api.deepseek.com/responses",
                DeepSeekResponsesProvider.responsesEndpoint(URI.create("https://api.deepseek.com")).toString());
        assertEquals("https://gateway.example/v1/responses",
                DeepSeekResponsesProvider.responsesEndpoint(URI.create("https://gateway.example/v1/")).toString());
        assertEquals("https://gateway.example/v1/responses",
                DeepSeekResponsesProvider.responsesEndpoint(
                        URI.create("https://gateway.example/api/../v1")).toString());
        assertEquals("https://gateway.example/team%20one/responses",
                DeepSeekResponsesProvider.responsesEndpoint(
                        URI.create("https://gateway.example/team%20one")).toString());
        assertThrows(IllegalArgumentException.class, () -> DeepSeekResponsesProvider.responsesEndpoint(
                URI.create("https://api.deepseek.com/responses")));
        assertThrows(IllegalArgumentException.class, () -> DeepSeekResponsesProvider.responsesEndpoint(
                URI.create("https://api.deepseek.com?token=secret")));
        assertThrows(IllegalArgumentException.class, () -> DeepSeekResponsesProvider.responsesEndpoint(
                URI.create("file:///tmp/deepseek")));
    }

    @Test
    void sendsBearerAuthToCustomResponsesPathAndParsesFinalText() throws Exception {
        ArrayDeque<Response> responses = new ArrayDeque<>();
        responses.add(new Response(200, finalResponse("done")));
        Capture capture = start(responses);
        DeepSeekResponsesProvider provider = provider(baseUri().resolve("gateway/"), TEST_KEY);

        ProviderResponse response = provider.generate(request(0), new CancellationToken());

        assertEquals("done", response.finalText().orElseThrow());
        assertEquals(2, response.usage().totalTokens());
        assertEquals(List.of("/gateway/responses"), capture.paths());
        assertEquals(List.of("Bearer " + TEST_KEY), capture.authorizations());
        JsonNode wire = JSON.readTree(capture.bodies().getFirst());
        assertEquals("deepseek-test-model", wire.path("model").asText());
        assertEquals("task", wire.path("input").asText());
        assertFalse(wire.has("previous_response_id"));
    }

    @Test
    void replaysSupportedOutputItemsAndOrderedToolResultsWithoutPreviousResponseId() throws Exception {
        ArrayDeque<Response> responses = new ArrayDeque<>();
        responses.add(new Response(200, """
                {"id":"resp_1","output":[
                  {"type":"reasoning","id":"reason_1","summary":[]},
                  {"type":"function_call","id":"fc_1","call_id":"call_1","name":"read_file","arguments":"{\\"path\\":\\"README.md\\"}"},
                  {"type":"function_call","id":"fc_2","call_id":"call_2","name":"search_code","arguments":"{\\"query\\":\\"Mini Coder\\"}"}
                ],"usage":{"input_tokens":3,"output_tokens":4,"total_tokens":7}}
                """));
        responses.add(new Response(200, finalResponse("complete")));
        Capture capture = start(responses);
        DeepSeekResponsesProvider provider = provider(baseUri(), TEST_KEY);

        ProviderResponse first = provider.generate(new ProviderRequest("system", "task", tools(), List.of(),
                ProviderCursor.empty(), new ProviderBudget(Duration.ofSeconds(3), 0)), new CancellationToken());
        assertEquals(List.of("call_1", "call_2"), first.toolCalls().stream().map(call -> call.callId()).toList());
        assertEquals(7, first.usage().totalTokens());

        ToolResult read = ToolResult.ok("read", JSON.createObjectNode().put("content", "hello"), Duration.ZERO);
        ToolResult search = ToolResult.ok("found", JSON.createObjectNode().put("matches", 1), Duration.ZERO);
        ProviderResponse second = provider.generate(new ProviderRequest("system", "task", tools(), List.of(
                new ToolExchange("call_1", "read_file", read),
                new ToolExchange("call_2", "search_code", search)), first.nextCursor(),
                new ProviderBudget(Duration.ofSeconds(3), 0)), new CancellationToken());

        assertEquals("complete", second.finalText().orElseThrow());
        assertEquals("resp_2", second.nextCursor().responseId());
        assertEquals("2", second.nextCursor().opaque().get(DeepSeekResponsesProvider.CURSOR_ROUNDS));
        JsonNode continuation = JSON.readTree(capture.bodies().get(1));
        assertFalse(continuation.has("previous_response_id"));
        ArrayNode input = (ArrayNode) continuation.path("input");
        assertEquals(6, input.size());
        assertEquals("message", input.get(0).path("type").asText());
        assertEquals("task", input.get(0).path("content").asText());
        assertEquals("reasoning", input.get(1).path("type").asText());
        assertEquals("call_1", input.get(2).path("call_id").asText());
        assertEquals("call_2", input.get(3).path("call_id").asText());
        assertEquals("call_1", input.get(4).path("call_id").asText());
        assertEquals("function_call_output", input.get(4).path("type").asText());
        assertEquals("hello", JSON.readTree(input.get(4).path("output").asText())
                .path("data").path("content").asText());
        assertEquals("call_2", input.get(5).path("call_id").asText());
    }

    @Test
    void classifiesPermanentAndTransientHttpFailuresAndRedactsBodies() throws Exception {
        Map<Integer, ProviderException.Category> categories = new LinkedHashMap<>();
        categories.put(400, ProviderException.Category.PROTOCOL);
        categories.put(401, ProviderException.Category.AUTHENTICATION);
        categories.put(402, ProviderException.Category.INSUFFICIENT_BALANCE);
        categories.put(403, ProviderException.Category.AUTHORIZATION);
        categories.put(422, ProviderException.Category.PROTOCOL);
        categories.put(429, ProviderException.Category.RATE_LIMIT);
        categories.put(500, ProviderException.Category.TRANSIENT);
        categories.put(503, ProviderException.Category.TRANSIENT);
        ArrayDeque<Response> responses = new ArrayDeque<>();
        categories.keySet().forEach(status -> responses.add(new Response(status,
                "{\"error\":\"" + TEST_KEY + "\"}")));
        Capture capture = start(responses);
        DeepSeekResponsesProvider provider = provider(baseUri(), TEST_KEY);

        for (Map.Entry<Integer, ProviderException.Category> expected : categories.entrySet()) {
            ProviderException error = assertThrows(ProviderException.class,
                    () -> provider.generate(request(0), new CancellationToken()));
            assertEquals(expected.getValue(), error.category());
            assertEquals(expected.getKey().intValue(), error.statusCode());
            assertEquals(expected.getKey() >= 500 || expected.getKey() == 429, error.retryable());
            assertFalse(error.getMessage().contains(TEST_KEY));
            assertTrue(error.getMessage().contains("[REDACTED]"));
        }
        assertEquals(categories.size(), capture.calls().get());
    }

    @Test
    void retriesRateLimitWithinBudgetAndReportsTelemetry() throws Exception {
        ArrayDeque<Response> responses = new ArrayDeque<>();
        responses.add(new Response(429, "{\"error\":\"slow\"}"));
        responses.add(new Response(200, finalResponse("recovered")));
        Capture capture = start(responses);
        DeepSeekResponsesProvider provider = provider(baseUri(), TEST_KEY);
        List<String> retries = new ArrayList<>();
        ProviderRequest request = new ProviderRequest("system", "task", tools(), List.of(), ProviderCursor.empty(),
                new ProviderBudget(Duration.ofSeconds(3), 1),
                (attempt, category, delay) -> retries.add(attempt + ":" + category));

        assertEquals("recovered", provider.generate(request, new CancellationToken()).finalText().orElseThrow());
        assertEquals(2, capture.calls().get());
        assertEquals(List.of("1:RATE_LIMIT"), retries);
    }

    @Test
    void rejectsMalformedResponsesUnknownItemsAndInvalidOrOversizedCursors() throws Exception {
        DeepSeekResponsesProvider provider = provider(URI.create("http://127.0.0.1:9"), TEST_KEY);
        assertEquals(ProviderException.Category.PROTOCOL, assertThrows(ProviderException.class,
                () -> provider.parseResponse("not-json", JSON.getNodeFactory().textNode("task"), 0)).category());
        assertEquals(ProviderException.Category.PROTOCOL, assertThrows(ProviderException.class,
                () -> provider.parseResponse("{\"id\":\"r\",\"output\":[{\"type\":\"unknown\"}]}",
                        JSON.getNodeFactory().textNode("task"), 0)).category());
        assertEquals(ProviderException.Category.PROTOCOL, assertThrows(ProviderException.class,
                () -> provider.parseResponse("{\"id\":\"r\",\"output\":[{\"type\":\"function_call\","
                                + "\"call_id\":\"c\",\"name\":\"read_file\",\"arguments\":\"not-json\"}]}",
                        JSON.getNodeFactory().textNode("task"), 0)).category());
        assertEquals(ProviderException.Category.PROTOCOL, assertThrows(ProviderException.class,
                () -> provider.parseResponse(finalResponse("done"), JSON.getNodeFactory().textNode("task"),
                        DeepSeekResponsesProvider.MAX_CURSOR_ROUNDS)).category());

        ArrayNode tooMany = JSON.createArrayNode();
        for (int i = 0; i <= DeepSeekResponsesProvider.MAX_CURSOR_ITEMS; i++) {
            tooMany.addObject().put("type", "message").put("role", "user").put("content", "x");
        }
        ProviderCursor oversized = new ProviderCursor("r", Map.of(
                DeepSeekResponsesProvider.CURSOR_REPLAY, JSON.writeValueAsString(tooMany),
                DeepSeekResponsesProvider.CURSOR_ROUNDS, "1"));
        ProviderRequest continuation = new ProviderRequest("system", "task", tools(), List.of(), oversized,
                new ProviderBudget(Duration.ofSeconds(1), 0));
        assertEquals(ProviderException.Category.PROTOCOL,
                assertThrows(ProviderException.class, () -> provider.buildRequest(continuation)).category());

        ArrayNode tooLarge = JSON.createArrayNode();
        tooLarge.addObject().put("type", "message").put("role", "user")
                .put("content", "x".repeat(DeepSeekResponsesProvider.MAX_CURSOR_BYTES));
        ProviderCursor oversizedBytes = new ProviderCursor("r", Map.of(
                DeepSeekResponsesProvider.CURSOR_REPLAY, JSON.writeValueAsString(tooLarge),
                DeepSeekResponsesProvider.CURSOR_ROUNDS, "1"));
        ProviderRequest byteLimited = new ProviderRequest("system", "task", tools(), List.of(), oversizedBytes,
                new ProviderBudget(Duration.ofSeconds(1), 0));
        assertEquals(ProviderException.Category.PROTOCOL,
                assertThrows(ProviderException.class, () -> provider.buildRequest(byteLimited)).category());
    }

    @Test
    void enforcesRequestTimeoutWithoutAdditionalAttempts() throws Exception {
        ArrayDeque<Response> responses = new ArrayDeque<>();
        responses.add(new Response(200, finalResponse("too late"), null, 250));
        Capture capture = start(responses);
        DeepSeekResponsesProvider provider = provider(baseUri(), TEST_KEY);
        ProviderRequest shortRequest = new ProviderRequest("system", "task", tools(), List.of(),
                ProviderCursor.empty(), new ProviderBudget(Duration.ofMillis(50), 0));

        ProviderException error = assertThrows(ProviderException.class,
                () -> provider.generate(shortRequest, new CancellationToken()));
        assertEquals(ProviderException.Category.TIMEOUT, error.category());
        assertEquals(1, capture.calls().get());
    }

    private DeepSeekResponsesProvider provider(URI baseUrl, String key) {
        return new DeepSeekResponsesProvider(HttpClient.newHttpClient(), baseUrl, key, "deepseek-test-model",
                new Redactor(List.of(key)));
    }

    private ProviderRequest request(int retries) {
        return new ProviderRequest("system", "task", tools(), List.of(), ProviderCursor.empty(),
                new ProviderBudget(Duration.ofSeconds(3), retries));
    }

    private List<ToolDefinition> tools() {
        return List.of(
                new ToolDefinition("read_file", "Read a file",
                        ToolSchemas.property(ToolSchemas.object(), "path", "string", true)),
                new ToolDefinition("search_code", "Search code",
                        ToolSchemas.property(ToolSchemas.object(), "query", "string", true)));
    }

    private Capture start(ArrayDeque<Response> responses) throws IOException {
        List<String> paths = new ArrayList<>();
        List<String> authorizations = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            paths.add(exchange.getRequestURI().getPath());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Response response = responses.removeFirst();
            if (response.delayMs() > 0) {
                try {
                    Thread.sleep(response.delayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (response.retryAfter() != null) {
                exchange.getResponseHeaders().set("Retry-After", response.retryAfter());
            }
            write(exchange, response);
        });
        server.start();
        return new Capture(paths, authorizations, bodies, calls);
    }

    private URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void write(HttpExchange exchange, Response response) throws IOException {
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static String finalResponse(String text) {
        return "{\"id\":\"resp_2\",\"output\":[{\"type\":\"message\",\"content\":["
                + "{\"type\":\"output_text\",\"text\":\"" + text + "\"}]}],"
                + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1,\"total_tokens\":2}}";
    }

    private record Response(int status, String body, String retryAfter, long delayMs) {
        private Response(int status, String body) {
            this(status, body, null, 0);
        }
    }

    private record Capture(List<String> paths, List<String> authorizations, List<String> bodies,
                           AtomicInteger calls) { }
}
