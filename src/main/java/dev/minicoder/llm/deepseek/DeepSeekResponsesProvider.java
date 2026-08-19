package dev.minicoder.llm.deepseek;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.minicoder.agent.CancellationToken;
import dev.minicoder.llm.LlmModels.ProviderBudget;
import dev.minicoder.llm.LlmModels.ProviderCursor;
import dev.minicoder.llm.LlmModels.ProviderRequest;
import dev.minicoder.llm.LlmModels.ProviderResponse;
import dev.minicoder.llm.LlmModels.ProviderTelemetry;
import dev.minicoder.llm.LlmModels.ToolCall;
import dev.minicoder.llm.LlmModels.ToolExchange;
import dev.minicoder.llm.LlmModels.Usage;
import dev.minicoder.llm.LlmProvider;
import dev.minicoder.llm.ProviderException;
import dev.minicoder.security.Redactor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 适配 DeepSeek Responses API，并以私有有界回放游标维持无状态工具调用续接。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class DeepSeekResponsesProvider implements LlmProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TOOL_CALLS = 32;
    static final int MAX_CURSOR_BYTES = 1024 * 1024;
    static final int MAX_CURSOR_ITEMS = 512;
    static final int MAX_CURSOR_ROUNDS = 30;
    static final String CURSOR_REPLAY = "deepseek.replay";
    static final String CURSOR_ROUNDS = "deepseek.rounds";

    private final HttpClient client;
    private final URI responsesEndpoint;
    private final String apiKey;
    private final String model;
    private final Redactor redactor;

    public DeepSeekResponsesProvider(HttpClient client, URI baseUrl, String apiKey, String model,
                                     Redactor redactor) {
        if (client == null) throw new IllegalArgumentException("HTTP client is required");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("DeepSeek API key is required");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("DeepSeek model is required");
        if (redactor == null) throw new IllegalArgumentException("Redactor is required");
        this.client = client;
        this.responsesEndpoint = responsesEndpoint(baseUrl);
        this.apiKey = apiKey;
        this.model = model.strip();
        this.redactor = redactor;
    }

    @Override
    public ProviderResponse generate(ProviderRequest request, CancellationToken token) throws ProviderException {
        ObjectNode requestBody = buildRequest(request);
        int previousRounds = cursorRounds(request.cursor());
        int attempts = 0;
        ProviderException last = null;
        Instant deadline = Instant.now().plus(request.budget().timeout());
        while (attempts <= request.budget().retriesRemaining()) {
            token.throwIfCancelled();
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isZero() || remaining.isNegative()) {
                throw new ProviderException(ProviderException.Category.TIMEOUT, false, 0,
                        "DeepSeek request exhausted its total deadline");
            }
            attempts++;
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder(responsesEndpoint)
                        .timeout(remaining)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                        .build();
                HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                String body = readBounded(response.body());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseResponse(body, requestBody.path("input"), previousRounds);
                }
                last = classifyHttp(response.statusCode(), body);
                if (!last.retryable() || attempts > request.budget().retriesRemaining()) throw last;
                backoff(attempts, retryAfterMillis(response), deadline, request.telemetry(),
                        last.category().name(), token);
            } catch (java.net.http.HttpTimeoutException e) {
                last = new ProviderException(ProviderException.Category.TIMEOUT, true, 0,
                        "DeepSeek request timed out", e);
                if (attempts > request.budget().retriesRemaining()) throw last;
                backoff(attempts, 0, deadline, request.telemetry(), last.category().name(), token);
            } catch (IOException e) {
                last = new ProviderException(ProviderException.Category.TRANSIENT, true, 0,
                        redactor.redact("DeepSeek transport error: " + e.getMessage()), e);
                if (attempts > request.budget().retriesRemaining()) throw last;
                backoff(attempts, 0, deadline, request.telemetry(), last.category().name(), token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProviderException(ProviderException.Category.TRANSIENT, false, 0,
                        "DeepSeek request interrupted", e);
            }
        }
        throw last == null ? new ProviderException(ProviderException.Category.TRANSIENT, false, 0,
                "DeepSeek request failed") : last;
    }

    ObjectNode buildRequest(ProviderRequest request) throws ProviderException {
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
            tool.put("strict", false);
        });

        // DeepSeek 不接受 previous_response_id，后续轮次必须回放受支持的 output items 与工具结果。
        if (request.cursor().isEmpty()) {
            if (!request.newToolResults().isEmpty()) {
                throw protocol("DeepSeek tool results require a replay cursor", null);
            }
            root.put("input", request.userTask());
        } else {
            ArrayNode input = replayFrom(request.cursor());
            for (ToolExchange exchange : request.newToolResults()) {
                ObjectNode item = input.addObject();
                item.put("type", "function_call_output");
                item.put("call_id", exchange.callId());
                item.put("output", serializeToolResult(exchange));
            }
            validateReplay(input);
            root.set("input", input);
        }
        return root;
    }

    ProviderResponse parseResponse(String body, JsonNode requestInput, int previousRounds) throws ProviderException {
        try {
            JsonNode root = JSON.readTree(body);
            if (root == null || !root.isObject()) throw protocol("Response root must be an object", null);
            String id = root.path("id").asText();
            if (id.isBlank()) throw protocol("Response is missing id", null);
            JsonNode output = root.path("output");
            if (!output.isArray()) throw protocol("Response output must be an array", null);

            List<ToolCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            // 私有 cursor 只保存续接所需的受支持项，并在序列化前同时限制轮数、项数和字节数。
            ArrayNode replay = requestReplay(requestInput);
            for (JsonNode item : output) {
                String type = item.path("type").asText();
                switch (type) {
                    case "function_call" -> {
                        String arguments = item.path("arguments").isTextual()
                                ? item.path("arguments").asText() : item.path("arguments").toString();
                        calls.add(new ToolCall(item.path("call_id").asText(), item.path("name").asText(),
                                JSON.readTree(arguments)));
                        if (calls.size() > MAX_TOOL_CALLS) {
                            throw protocol("Response exceeds the 32 tool-call limit", null);
                        }
                    }
                    case "message" -> {
                        JsonNode content = item.path("content");
                        if (!content.isArray()) throw protocol("Message content must be an array", null);
                        for (JsonNode part : content) {
                            if (part.path("type").asText().equals("output_text")) {
                                if (!text.isEmpty()) text.append('\n');
                                text.append(part.path("text").asText());
                            }
                        }
                    }
                    case "reasoning" -> { }
                    default -> throw protocol("Unsupported DeepSeek output item type: " + type, null);
                }
                replay.add(item.deepCopy());
            }

            Optional<String> finalText = text.isEmpty() ? Optional.empty() : Optional.of(text.toString());
            if (calls.isEmpty() && finalText.isEmpty()) {
                throw protocol("Response has no text or tool calls", null);
            }
            int rounds = previousRounds + 1;
            if (rounds > MAX_CURSOR_ROUNDS) throw protocol("DeepSeek replay cursor exceeds the 30-round limit", null);
            validateReplay(replay);
            String replayJson = JSON.writeValueAsString(replay);
            ProviderCursor cursor = new ProviderCursor(id, Map.of(
                    CURSOR_REPLAY, replayJson,
                    CURSOR_ROUNDS, Integer.toString(rounds)));
            JsonNode usageNode = root.path("usage");
            Usage usage = new Usage(usageNode.path("input_tokens").asLong(),
                    usageNode.path("output_tokens").asLong(), usageNode.path("total_tokens").asLong());
            return new ProviderResponse(id, finalText, calls, cursor, usage);
        } catch (ProviderException e) {
            throw e;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw protocol(redactor.redact("Invalid DeepSeek response: " + e.getMessage()), e);
        }
    }

    static URI responsesEndpoint(URI baseUrl) {
        if (baseUrl == null) {
            throw new IllegalArgumentException("DeepSeek Base URL is required");
        }
        URI normalized = baseUrl.normalize();
        if (!normalized.isAbsolute()
                || !(normalized.getScheme().equalsIgnoreCase("http") || normalized.getScheme().equalsIgnoreCase("https"))
                || normalized.getRawAuthority() == null || normalized.getRawAuthority().isBlank()
                || normalized.getRawUserInfo() != null || normalized.getRawQuery() != null
                || normalized.getRawFragment() != null) {
            throw new IllegalArgumentException("DeepSeek Base URL must be an absolute HTTP(S) URL without credentials, query, or fragment");
        }
        String path = Optional.ofNullable(normalized.getRawPath()).orElse("").replaceAll("/+$", "");
        if (path.endsWith("/responses")) {
            throw new IllegalArgumentException("DeepSeek Base URL must not include the /responses endpoint");
        }
        String endpointPath = path + "/responses";
        return normalized.resolve(URI.create(endpointPath));
    }

    private ArrayNode replayFrom(ProviderCursor cursor) throws ProviderException {
        String raw = cursor.opaque().get(CURSOR_REPLAY);
        if (raw == null || raw.isBlank()) throw protocol("DeepSeek replay cursor is missing history", null);
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_CURSOR_BYTES) {
            throw protocol("DeepSeek replay cursor exceeds the 1 MiB limit", null);
        }
        try {
            JsonNode parsed = JSON.readTree(raw);
            if (!parsed.isArray()) throw protocol("DeepSeek replay cursor history must be an array", null);
            ArrayNode replay = (ArrayNode) parsed.deepCopy();
            validateReplay(replay);
            cursorRounds(cursor);
            return replay;
        } catch (JsonProcessingException e) {
            throw protocol("Invalid DeepSeek replay cursor", e);
        }
    }

    private static int cursorRounds(ProviderCursor cursor) throws ProviderException {
        if (cursor == null || cursor.isEmpty()) return 0;
        String raw = cursor.opaque().get(CURSOR_ROUNDS);
        try {
            int rounds = Integer.parseInt(raw);
            if (rounds < 1 || rounds > MAX_CURSOR_ROUNDS) throw new NumberFormatException();
            return rounds;
        } catch (NumberFormatException | NullPointerException e) {
            throw protocol("Invalid DeepSeek replay cursor round count", e);
        }
    }

    private static ArrayNode requestReplay(JsonNode requestInput) throws ProviderException {
        ArrayNode replay = JSON.createArrayNode();
        if (requestInput.isTextual()) {
            ObjectNode initial = replay.addObject();
            initial.put("type", "message");
            initial.put("role", "user");
            initial.put("content", requestInput.asText());
        } else if (requestInput.isArray()) {
            requestInput.forEach(item -> replay.add(item.deepCopy()));
        } else {
            throw protocol("DeepSeek request input cannot be replayed", null);
        }
        return replay;
    }

    private static void validateReplay(ArrayNode replay) throws ProviderException {
        if (replay.size() > MAX_CURSOR_ITEMS) {
            throw protocol("DeepSeek replay cursor exceeds the 512-item limit", null);
        }
        try {
            if (JSON.writeValueAsBytes(replay).length > MAX_CURSOR_BYTES) {
                throw protocol("DeepSeek replay cursor exceeds the 1 MiB limit", null);
            }
        } catch (JsonProcessingException e) {
            throw protocol("Unable to serialize DeepSeek replay cursor", e);
        }
    }

    private static String serializeToolResult(ToolExchange exchange) throws ProviderException {
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
            return JSON.writeValueAsString(output);
        } catch (JsonProcessingException e) {
            throw protocol("Unable to serialize DeepSeek tool result", e);
        }
    }

    private ProviderException classifyHttp(int status, String body) {
        String safe = redactor.redact(body == null ? "" : body);
        if (safe.length() > 2_000) safe = safe.substring(0, 2_000) + "...[truncated]";
        return switch (status) {
            case 401 -> new ProviderException(ProviderException.Category.AUTHENTICATION, false, status,
                    "DeepSeek authentication failed: " + safe);
            case 402 -> new ProviderException(ProviderException.Category.INSUFFICIENT_BALANCE, false, status,
                    "DeepSeek account balance is insufficient: " + safe);
            case 403 -> new ProviderException(ProviderException.Category.AUTHORIZATION, false, status,
                    "DeepSeek authorization failed: " + safe);
            case 429 -> new ProviderException(ProviderException.Category.RATE_LIMIT, true, status,
                    "DeepSeek rate limit: " + safe);
            default -> new ProviderException(status >= 500 ? ProviderException.Category.TRANSIENT
                    : ProviderException.Category.PROTOCOL, status >= 500, status,
                    "DeepSeek HTTP " + status + ": " + safe);
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
                        "DeepSeek response exceeds the 4 MiB limit");
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
        long exponential = Math.min(2_000, 100L * (1L << Math.min(attempts - 1, 4)))
                + ThreadLocalRandom.current().nextLong(25, 100);
        long millis = Math.max(exponential, Math.min(retryAfterMillis, 10_000));
        long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
        if (remainingMillis <= millis) {
            throw new ProviderException(ProviderException.Category.TIMEOUT, false, 0,
                    "DeepSeek retry backoff would exceed the total deadline");
        }
        telemetry.retrying(attempts, category, millis);
        try {
            Thread.sleep(millis);
            token.throwIfCancelled();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(ProviderException.Category.TRANSIENT, false, 0,
                    "DeepSeek retry interrupted", e);
        }
    }
}
