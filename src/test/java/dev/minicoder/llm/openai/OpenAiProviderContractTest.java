package dev.minicoder.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.minicoder.agent.CancellationToken;
import dev.minicoder.llm.LlmModels.*;
import dev.minicoder.llm.ProviderException;
import dev.minicoder.security.Redactor;
import dev.minicoder.tool.ToolDefinition;
import dev.minicoder.tool.ToolResult;
import dev.minicoder.tool.ToolSchemas;
import dev.minicoder.tool.ToolStatus;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过本地 HTTP fixture 验证 OpenAI Responses 请求映射、续接、重试和错误脱敏。
 *
 * @author Self David (dsfgis@gmail.com)
 */
class OpenAiProviderContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void parsesToolCallsAndSerializesContinuationWithoutNullMapFailure() throws Exception {
        OpenAiResponsesProvider provider = provider("secret-value");
        ProviderResponse parsed = provider.parseResponse("""
                {"id":"resp_1","output":[{"type":"function_call","call_id":"call_1",
                "name":"read_file","arguments":"{\\"path\\":\\"README.md\\"}"}],
                "usage":{"input_tokens":3,"output_tokens":4,"total_tokens":7}}
                """);
        assertEquals("call_1", parsed.toolCalls().getFirst().callId());
        assertEquals("README.md", parsed.toolCalls().getFirst().arguments().path("path").asText());
        assertEquals(7, parsed.usage().totalTokens());

        ToolResult result = ToolResult.ok("read", JSON.createObjectNode().put("content", "hello"), Duration.ZERO);
        ProviderRequest continuation = new ProviderRequest("system", "task", List.of(tool()),
                List.of(new ToolExchange("call_1", "read_file", result)),
                new ProviderCursor("resp_1", Map.of()), new ProviderBudget(Duration.ofSeconds(2), 0));
        JsonNode wire = provider.buildRequest(continuation);
        assertEquals("resp_1", wire.path("previous_response_id").asText());
        JsonNode output = JSON.readTree(wire.path("input").get(0).path("output").asText());
        assertTrue(output.path("error").isNull());
        assertEquals("hello", output.path("data").path("content").asText());
    }

    @Test
    void retries429ThenReturnsFinalTextAndSendsBearerHeader() throws Exception {
        var responses = new ArrayDeque<Response>();
        responses.add(new Response(429, "{\"error\":\"slow down\"}"));
        responses.add(new Response(200, finalResponse("done")));
        List<String> authorizations = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        server = start(responses, authorizations, calls);

        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(HttpClient.newHttpClient(), baseUri(),
                "secret-value", "gpt-test", new Redactor(List.of("secret-value")));
        List<String> retries = new ArrayList<>();
        ProviderRequest request = new ProviderRequest("system", "task", List.of(tool()), List.of(),
                ProviderCursor.empty(), new ProviderBudget(Duration.ofSeconds(3), 1),
                (attempt, category, delay) -> retries.add(attempt + ":" + category + ":" + delay));
        ProviderResponse result = provider.generate(request, new CancellationToken());

        assertEquals("done", result.finalText().orElseThrow());
        assertEquals(2, calls.get());
        assertEquals(List.of("Bearer secret-value", "Bearer secret-value"), authorizations);
        assertEquals(1, retries.size());
        assertTrue(retries.getFirst().contains("RATE_LIMIT"));
    }

    @Test
    void doesNotRetryAuthenticationFailureAndRedactsResponseSecret() throws Exception {
        var responses = new ArrayDeque<Response>();
        responses.add(new Response(401, "{\"error\":\"secret-value\"}"));
        AtomicInteger calls = new AtomicInteger();
        server = start(responses, new ArrayList<>(), calls);
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(HttpClient.newHttpClient(), baseUri(),
                "secret-value", "gpt-test", new Redactor(List.of("secret-value")));

        ProviderException error = assertThrows(ProviderException.class,
                () -> provider.generate(request(3), new CancellationToken()));
        assertEquals(ProviderException.Category.AUTHENTICATION, error.category());
        assertFalse(error.retryable());
        assertEquals(1, calls.get());
        assertFalse(error.getMessage().contains("secret-value"));
        assertTrue(error.getMessage().contains("[REDACTED]"));
    }

    @Test
    void retriesServerFailureButDoesNotLetRetryAfterBypassTotalDeadline() throws Exception {
        var serverFailure = new ArrayDeque<Response>();
        serverFailure.add(new Response(500, "{\"error\":\"temporary\"}"));
        serverFailure.add(new Response(200, finalResponse("recovered")));
        AtomicInteger recoveredCalls = new AtomicInteger();
        server = start(serverFailure, new ArrayList<>(), recoveredCalls);
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(HttpClient.newHttpClient(), baseUri(),
                "secret-value", "gpt-test", new Redactor(List.of("secret-value")));
        assertEquals("recovered", provider.generate(request(1), new CancellationToken()).finalText().orElseThrow());
        assertEquals(2, recoveredCalls.get());
        server.stop(0);

        var rateLimited = new ArrayDeque<Response>();
        rateLimited.add(new Response(429, "{\"error\":\"wait\"}", "5"));
        AtomicInteger limitedCalls = new AtomicInteger();
        server = start(rateLimited, new ArrayList<>(), limitedCalls);
        OpenAiResponsesProvider limited = new OpenAiResponsesProvider(HttpClient.newHttpClient(), baseUri(),
                "secret-value", "gpt-test", new Redactor(List.of("secret-value")));
        ProviderRequest shortBudget = new ProviderRequest("system", "task", List.of(tool()), List.of(),
                ProviderCursor.empty(), new ProviderBudget(Duration.ofMillis(100), 3));
        ProviderException error = assertThrows(ProviderException.class,
                () -> limited.generate(shortBudget, new CancellationToken()));
        assertEquals(ProviderException.Category.TIMEOUT, error.category());
        assertEquals(1, limitedCalls.get());
    }

    @Test
    void rejectsMalformedOrEmptyProtocolResponses() {
        OpenAiResponsesProvider provider = provider("secret-value");
        assertEquals(ProviderException.Category.PROTOCOL,
                assertThrows(ProviderException.class, () -> provider.parseResponse("not-json")).category());
        assertEquals(ProviderException.Category.PROTOCOL,
                assertThrows(ProviderException.class,
                        () -> provider.parseResponse("{\"id\":\"resp_empty\",\"output\":[]}" )).category());
    }

    private OpenAiResponsesProvider provider(String key) {
        return new OpenAiResponsesProvider(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:9"), key,
                "gpt-test", new Redactor(List.of(key)));
    }

    private ProviderRequest request(int retries) {
        return new ProviderRequest("system", "task", List.of(tool()), List.of(), ProviderCursor.empty(),
                new ProviderBudget(Duration.ofSeconds(3), retries));
    }

    private ToolDefinition tool() {
        return new ToolDefinition("read_file", "Read a file", ToolSchemas.property(ToolSchemas.object(),
                "path", "string", true));
    }

    private HttpServer start(ArrayDeque<Response> responses, List<String> authorizations, AtomicInteger calls)
            throws IOException {
        HttpServer local = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        local.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet();
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            Response response = responses.removeFirst();
            if (response.retryAfter() != null) {
                exchange.getResponseHeaders().set("Retry-After", response.retryAfter());
            }
            write(exchange, response);
        });
        local.start();
        return local;
    }

    private URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static void write(HttpExchange exchange, Response response) throws IOException {
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String finalResponse(String text) {
        return "{\"id\":\"resp_2\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\""
                + text + "\"}]}],\"usage\":{\"input_tokens\":1,\"output_tokens\":1,\"total_tokens\":2}}";
    }

    private record Response(int status, String body, String retryAfter) {
        private Response(int status, String body) { this(status, body, null); }
    }
}
