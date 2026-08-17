package dev.minicodex.observability;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record RunEvent(
        Instant timestamp,
        String runId,
        int iteration,
        String type,
        String status,
        Optional<String> responseId,
        Optional<String> toolCallId,
        Map<String, Object> metadata) {
    public RunEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        runId = Objects.requireNonNullElse(runId, "");
        type = Objects.requireNonNullElse(type, "unknown");
        status = Objects.requireNonNullElse(status, "");
        responseId = responseId == null ? Optional.empty() : responseId;
        toolCallId = toolCallId == null ? Optional.empty() : toolCallId;
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
    }

    public static RunEvent of(String runId, int iteration, String type, String status,
                              String responseId, String toolCallId, Map<String, Object> metadata) {
        return new RunEvent(Instant.now(), runId, iteration, type, status,
                Optional.ofNullable(responseId), Optional.ofNullable(toolCallId), metadata);
    }
}

