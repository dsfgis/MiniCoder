package dev.minicodex.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface Tool {
    ToolDefinition definition();

    ToolResult execute(JsonNode input, ToolExecutionContext context);

    default boolean mayModifyWorkspace() {
        return false;
    }
}
