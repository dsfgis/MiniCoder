package dev.minicodex.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.minicodex.llm.LlmModels.ToolCall;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        String name = tool.definition().name();
        if (tools.putIfAbsent(name, tool) != null) {
            throw new IllegalArgumentException("Duplicate tool: " + name);
        }
        return this;
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(Tool::definition).toList();
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public ToolResult execute(ToolCall call, ToolExecutionContext context) {
        Instant started = Instant.now();
        Tool tool = tools.get(call.name());
        if (tool == null) {
            return ToolResult.failure(ToolStatus.INVALID_TOOL_CALL, "UNKNOWN_TOOL",
                    "Unknown tool: " + call.name(), Duration.between(started, Instant.now()));
        }
        List<String> errors = validate(tool.definition().parametersSchema(), call.arguments());
        if (!errors.isEmpty()) {
            var data = JsonNodeFactory.instance.objectNode();
            data.putPOJO("validationErrors", errors);
            return new ToolResult(ToolStatus.INVALID_TOOL_CALL, "Invalid arguments", data, false,
                    Optional.of(new ToolResult.ToolError("INVALID_ARGUMENTS", String.join("; ", errors))),
                    Duration.between(started, Instant.now()));
        }
        context.cancellationToken().throwIfCancelled();
        return tool.execute(call.arguments(), context);
    }

    static List<String> validate(JsonNode schema, JsonNode input) {
        List<String> errors = new ArrayList<>();
        if (!input.isObject()) {
            errors.add("arguments must be an object");
            return errors;
        }
        if (schema.path("type").asText("object").equals("object")) {
            for (JsonNode required : schema.path("required")) {
                String field = required.asText();
                if (!input.has(field) || input.get(field).isNull()) {
                    errors.add("missing required field: " + field);
                }
            }
            schema.path("properties").fields().forEachRemaining(entry -> {
                if (!input.has(entry.getKey())) {
                    return;
                }
                String type = entry.getValue().path("type").asText();
                JsonNode value = input.get(entry.getKey());
                boolean valid = switch (type) {
                    case "string" -> value.isTextual();
                    case "integer" -> value.isIntegralNumber();
                    case "number" -> value.isNumber();
                    case "boolean" -> value.isBoolean();
                    case "array" -> value.isArray();
                    case "object" -> value.isObject();
                    default -> true;
                };
                if (!valid) {
                    errors.add("field " + entry.getKey() + " must be " + type);
                }
            });
            if (schema.path("additionalProperties").isBoolean()
                    && !schema.path("additionalProperties").asBoolean()) {
                input.fieldNames().forEachRemaining(field -> {
                    if (!schema.path("properties").has(field)) {
                        errors.add("unknown field: " + field);
                    }
                });
            }
        }
        return errors;
    }
}
