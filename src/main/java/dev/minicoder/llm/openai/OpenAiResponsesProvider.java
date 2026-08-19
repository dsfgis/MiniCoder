package dev.minicoder.llm.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.minicoder.agent.CancellationToken;
import dev.minicoder.llm.*;
import dev.minicoder.llm.LlmModels.*;
import dev.minicoder.security.Redactor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 将领域请求映射到 OpenAI Responses API，并封装工具调用续接、重试和错误脱敏。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class OpenAiResponsesProvider implements LlmProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TOOL_CALLS = 32;
    private final HttpClient client;
    private final URI responsesEndpoint;
    private final String apiKey;
    private final String model;
    private final Redactor redactor;

    public OpenAiResponsesProvider(HttpClient client, URI baseUrl, String apiKey, String model, Redactor redactor) {
        this.client = client;
        String base = baseUrl.toString().replaceAll("/+$", "");
        this.responsesEndpoint = URI.create(base.endsWith("/v1") ? base + "/responses" : base + "/v1/responses");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("OpenAI API key is required");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("OpenAI model is required");
        this.apiKey = apiKey;
        this.model = model;
        this.redactor = redactor;
    }

    @Override
    public ProviderResponse generate(ProviderRequest request, CancellationToken token) throws ProviderException {
        int attempts = 0;
        ProviderException last = null;
        Instant deadline = Instant.now().plus(request.budget().timeout());
        while (attempts <= request.budget().retriesRemaining()) {
            token.throwIfCancelled();
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isZero() || remaining.isNegative()) {
                throw new ProviderException(ProviderException.Category.TIMEOUT, false, 0,
                        "OpenAI request exhausted its total deadline");
            }
            attempts++;
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder(responsesEndpoint)
                        .timeout(remaining)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(buildRequest(request).toString()))
                        .build();
                HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                String body = readBounded(response.body());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseResponse(body);
                }
                last = classifyHttp(response.statusCode(), body);
                if (!last.retryable() || attempts > request.budget().retriesRemaining()) throw last;
                backoff(attempts, retryAfterMillis(response), deadline, request.telemetry(),
                        last.category().name(), token);
            } catch (java.net.http.HttpTimeoutException e) {
                last = new ProviderException(ProviderException.Category.TIMEOUT, true, 0,
                        "OpenAI request timed out", e);
                if (attempts > request.budget().retriesRemaining()) throw last;
                backoff(attempts, 0, deadline, request.telemetry(), last.category().name(), token);
            } catch (IOException e) {
                last = new ProviderException(ProviderException.Category.TRANSIENT, true, 0,
                        redactor.redact("OpenAI transport error: " + e.getMessage()), e);
                if (attempts > request.budget().retriesRemaining()) throw last;
                backoff(attempts, 0, deadline, request.telemetry(), last.category().name(), token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProviderException(ProviderException.Category.TRANSIENT, false, 0,
                        "OpenAI request interrupted", e);
            }
        }
        throw last == null ? new ProviderException(ProviderException.Category.TRANSIENT, false, 0,
                "OpenAI request failed") : last;
    }

    ObjectNode buildRequest(ProviderRequest request) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", model);
        root.put("instructions", request.systemInstructions());
        ArrayNode tools = root.putArray("tools");
        request.tools().forEach(definition -> {
            ObjectNode tool = tools.addObject();
            tool.put("type", "function");
            tool.put("name", definition.name());
            tool.put("description", definition.description());
            tool.set("parameters", definition.parametersSchema());
            // 核心 Schema 允许省略可选字段；真正的严格参数校验仍由本地 ToolRegistry 统一执行。
            tool.put("strict", false);
        });
        // OpenAI 由服务端 response id 续接，因此无需像 DeepSeek 一样在本地回放完整响应项。
        if (!request.cursor().responseId().isBlank()) {
            root.put("previous_response_id", request.cursor().responseId());
        }
        if (request.newToolResults().isEmpty()) {
            root.put("input", request.userTask());
        } else {
            ArrayNode input = root.putArray("input");
            request.newToolResults().forEach(exchange -> {
                ObjectNode item = input.addObject();
                item.put("type", "function_call_output");
                item.put("call_id", exchange.callId());
                try {
                    ObjectNode output = JSON.createObjectNode();
                    output.put("status", exchange.result().status().name());
                    output.put("summary", exchange.result().summary());
                    output.set("data", exchange.result().data());
                    output.put("truncated", exchange.result().truncated());
                    exchange.result().error().ifPresentOrElse(error -> {
                        ObjectNode errorNode = output.putObject("error");
                        errorNode.put("code", error.code());
                        errorNode.put("message", error.message());
                    }, () -> output.putNull("error"));
                    item.put("output", JSON.writeValueAsString(output));
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Unable to serialize tool result", e);
                }
            });
        }
        return root;
    }

    ProviderResponse parseResponse(String body) throws ProviderException {
        try {
            JsonNode root = JSON.readTree(body);
            String id = root.path("id").asText();
            if (id.isBlank()) throw protocol("Response is missing id", null);
            List<ToolCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (JsonNode item : root.path("output")) {
                String type = item.path("type").asText();
                if (type.equals("function_call")) {
                    String arguments = item.path("arguments").isTextual()
                            ? item.path("arguments").asText() : item.path("arguments").toString();
                    calls.add(new ToolCall(item.path("call_id").asText(), item.path("name").asText(),
                            JSON.readTree(arguments)));
                    if (calls.size() > MAX_TOOL_CALLS) {
                        throw protocol("Response exceeds the 32 tool-call limit", null);
                    }
                } else if (type.equals("message")) {
                    for (JsonNode content : item.path("content")) {
                        if (content.path("type").asText().equals("output_text")) {
                            if (!text.isEmpty()) text.append('\n');
                            text.append(content.path("text").asText());
                        }
                    }
                }
            }
            JsonNode usageNode = root.path("usage");
            Usage usage = new Usage(usageNode.path("input_tokens").asLong(),
                    usageNode.path("output_tokens").asLong(), usageNode.path("total_tokens").asLong());
            Optional<String> finalText = text.isEmpty() ? Optional.empty() : Optional.of(text.toString());
            if (calls.isEmpty() && finalText.isEmpty()) throw protocol("Response has no text or tool calls", null);
            return new ProviderResponse(id, finalText, calls, new ProviderCursor(id, Map.of()), usage);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw protocol(redactor.redact("Invalid OpenAI response: " + e.getMessage()), e);
        }
    }

    private ProviderException classifyHttp(int status, String body) {
        String safe = redactor.redact(body == null ? "" : body);
        if (safe.length() > 2_000) safe = safe.substring(0, 2_000) + "...[truncated]";
        return switch (status) {
            case 401 -> new ProviderException(ProviderException.Category.AUTHENTICATION, false, status,
                    "OpenAI authentication failed: " + safe);
            case 403 -> new ProviderException(ProviderException.Category.AUTHORIZATION, false, status,
                    "OpenAI authorization failed: " + safe);
            case 429 -> new ProviderException(ProviderException.Category.RATE_LIMIT, true, status,
                    "OpenAI rate limit: " + safe);
            default -> new ProviderException(status >= 500 ? ProviderException.Category.TRANSIENT
                    : ProviderException.Category.PROTOCOL, status >= 500, status,
                    "OpenAI HTTP " + status + ": " + safe);
        };
    }

    private static ProviderException protocol(String message, Throwable cause) {
        return new ProviderException(ProviderException.Category.PROTOCOL, false, 0, message, cause);
    }

    private static String readBounded(InputStream input) throws IOException, ProviderException {
        try (input) {
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new ProviderException(ProviderException.Category.PROTOCOL, false, 0,
                        "OpenAI response exceeds the 4 MiB limit");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static long retryAfterMillis(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").map(value -> {
            try {
                return Math.max(0, Long.parseLong(value.strip()) * 1_000L);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }).orElse(0L);
    }

    private static void backoff(int attempts, long retryAfterMillis, Instant deadline,
                                ProviderTelemetry telemetry, String category,
                                CancellationToken token) throws ProviderException {
        // 退避时间同时受 Retry-After 上限和总 deadline 约束，重试不能延长整次运行预算。
        long exponential = Math.min(2_000, 100L * (1L << Math.min(attempts - 1, 4)))
                + ThreadLocalRandom.current().nextLong(25, 100);
        long millis = Math.max(exponential, Math.min(retryAfterMillis, 10_000));
        long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
        if (remainingMillis <= millis) {
            throw new ProviderException(ProviderException.Category.TIMEOUT, false, 0,
                    "OpenAI retry backoff would exceed the total deadline");
        }
        telemetry.retrying(attempts, category, millis);
        try {
            Thread.sleep(millis);
            token.throwIfCancelled();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(ProviderException.Category.TRANSIENT, false, 0,
                    "Retry interrupted", e);
        }
    }
}
