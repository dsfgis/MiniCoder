package dev.minicoder.tool;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 提供工具参数 JSON Schema 的构造与严格校验辅助方法。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class ToolSchemas {
    private ToolSchemas() {}

    public static ObjectNode object() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.set("properties", JsonNodeFactory.instance.objectNode());
        schema.set("required", JsonNodeFactory.instance.arrayNode());
        schema.put("additionalProperties", false);
        return schema;
    }

    public static ObjectNode property(ObjectNode schema, String name, String type, boolean required) {
        ObjectNode property = JsonNodeFactory.instance.objectNode().put("type", type);
        if (type.equals("array")) {
            property.set("items", JsonNodeFactory.instance.objectNode().put("type", "string"));
        }
        ((ObjectNode) schema.get("properties")).set(name, property);
        if (required) ((ArrayNode) schema.get("required")).add(name);
        return schema;
    }
}
