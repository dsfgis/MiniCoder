package dev.minicoder.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 定义所有产品工具的元数据与执行契约，具体实现必须通过 ToolRegistry 注册。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public interface Tool {
    ToolDefinition definition();

    ToolResult execute(JsonNode input, ToolExecutionContext context);

    default boolean mayModifyWorkspace() {
        return false;
    }
}
