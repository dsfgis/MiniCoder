package dev.minicoder.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * 保存工具的稳定名称、用途说明和供 Provider 使用的 JSON Schema。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public record ToolDefinition(String name, String description, JsonNode parametersSchema) {
    public ToolDefinition {
        if (name == null || !name.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid tool name: " + name);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool description must not be blank");
        }
        Objects.requireNonNull(parametersSchema, "parametersSchema");
    }
}
