package dev.minicoder.tool;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
