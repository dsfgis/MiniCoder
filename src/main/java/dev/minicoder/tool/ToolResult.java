package dev.minicoder.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 以统一结构返回工具状态、摘要、数据、截断事实、错误与耗时。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public record ToolResult(
        ToolStatus status,
        String summary,
        JsonNode data,
        boolean truncated,
        Optional<ToolError> error,
        Duration duration) {

    public ToolResult {
        Objects.requireNonNull(status, "status");
        summary = Objects.requireNonNullElse(summary, "");
        data = data == null ? JsonNodeFactory.instance.objectNode() : data;
        error = error == null ? Optional.empty() : error;
        duration = duration == null ? Duration.ZERO : duration;
    }

    public boolean isSuccess() {
        return status == ToolStatus.OK || status == ToolStatus.NO_MATCH;
    }

    public static ToolResult ok(String summary, JsonNode data, Duration duration) {
        return new ToolResult(ToolStatus.OK, summary, data, false, Optional.empty(), duration);
    }

    public static ToolResult failure(ToolStatus status, String code, String message, Duration duration) {
        return new ToolResult(status, message, JsonNodeFactory.instance.objectNode(), false,
                Optional.of(new ToolError(code, message)), duration);
    }

    public record ToolError(String code, String message) {
        public ToolError {
            code = Objects.requireNonNullElse(code, "UNKNOWN");
            message = Objects.requireNonNullElse(message, "");
        }
    }
}
