package dev.minicodex.llm;

import com.fasterxml.jackson.databind.JsonNode;
import dev.minicodex.tool.ToolDefinition;
import dev.minicodex.tool.ToolResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class LlmModels {
    private LlmModels() {}

    public record ProviderRequest(
            String systemInstructions,
            String userTask,
            List<ToolDefinition> tools,
            List<ToolExchange> newToolResults,
            ProviderCursor cursor,
            ProviderBudget budget,
            ProviderTelemetry telemetry) {
        public ProviderRequest {
            systemInstructions = Objects.requireNonNullElse(systemInstructions, "");
            userTask = Objects.requireNonNullElse(userTask, "");
            tools = List.copyOf(Objects.requireNonNullElse(tools, List.of()));
            newToolResults = List.copyOf(Objects.requireNonNullElse(newToolResults, List.of()));
            cursor = cursor == null ? ProviderCursor.empty() : cursor;
            budget = budget == null ? new ProviderBudget(Duration.ofSeconds(60), 0) : budget;
            telemetry = telemetry == null ? ProviderTelemetry.noop() : telemetry;
        }

        public ProviderRequest(String systemInstructions, String userTask, List<ToolDefinition> tools,
                               List<ToolExchange> newToolResults, ProviderCursor cursor, ProviderBudget budget) {
            this(systemInstructions, userTask, tools, newToolResults, cursor, budget, ProviderTelemetry.noop());
        }
    }

    public record ProviderResponse(
            String responseId,
            Optional<String> finalText,
            List<ToolCall> toolCalls,
            ProviderCursor nextCursor,
            Usage usage) {
        public ProviderResponse {
            responseId = Objects.requireNonNullElse(responseId, "");
            finalText = finalText == null ? Optional.empty() : finalText.filter(s -> !s.isBlank());
            toolCalls = List.copyOf(Objects.requireNonNullElse(toolCalls, List.of()));
            nextCursor = nextCursor == null ? ProviderCursor.empty() : nextCursor;
            usage = usage == null ? Usage.ZERO : usage;
        }

        public static ProviderResponse finalText(String responseId, String text) {
            return new ProviderResponse(responseId, Optional.ofNullable(text), List.of(),
                    new ProviderCursor(responseId, Map.of()), Usage.ZERO);
        }

        public static ProviderResponse tools(String responseId, List<ToolCall> calls) {
            return new ProviderResponse(responseId, Optional.empty(), calls,
                    new ProviderCursor(responseId, Map.of()), Usage.ZERO);
        }
    }

    public record ProviderCursor(String responseId, Map<String, String> opaque) {
        public ProviderCursor {
            responseId = Objects.requireNonNullElse(responseId, "");
            opaque = Map.copyOf(Objects.requireNonNullElse(opaque, Map.of()));
        }

        public static ProviderCursor empty() {
            return new ProviderCursor("", Map.of());
        }

        public boolean isEmpty() {
            return responseId.isBlank() && opaque.isEmpty();
        }
    }

    public record ToolCall(String callId, String name, JsonNode arguments) {
        public ToolCall {
            callId = requireText(callId, "callId");
            name = requireText(name, "name");
            Objects.requireNonNull(arguments, "arguments");
        }
    }

    public record ToolExchange(String callId, String toolName, ToolResult result) {
        public ToolExchange {
            callId = requireText(callId, "callId");
            toolName = requireText(toolName, "toolName");
            Objects.requireNonNull(result, "result");
        }
    }

    public record ProviderBudget(Duration timeout, int retriesRemaining) {
        public ProviderBudget {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative() || timeout.isZero() || retriesRemaining < 0) {
                throw new IllegalArgumentException("Invalid provider budget");
            }
        }
    }

    @FunctionalInterface
    public interface ProviderTelemetry {
        void retrying(int completedAttempt, String category, long backoffMs);

        static ProviderTelemetry noop() {
            return (completedAttempt, category, backoffMs) -> { };
        }
    }

    public record Usage(long inputTokens, long outputTokens, long totalTokens) {
        public static final Usage ZERO = new Usage(0, 0, 0);

        public Usage plus(Usage other) {
            return new Usage(inputTokens + other.inputTokens,
                    outputTokens + other.outputTokens,
                    totalTokens + other.totalTokens);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
