package dev.minicodex.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicodex.agent.CancellationToken;
import dev.minicodex.llm.LlmModels.ToolCall;
import dev.minicodex.observability.EventSink;
import dev.minicodex.support.TemporaryGitRepository;
import dev.minicodex.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void rejectsUnknownAndInvalidCallsWithoutExecution() throws Exception {
        Workspace workspace = Workspace.open(TemporaryGitRepository.create(temp.resolve("repo")));
        ToolExecutionContext context = new ToolExecutionContext(workspace, new CancellationToken(),
                EventSink.noop(), "run", 1);
        ToolRegistry registry = new ToolRegistry();
        ToolResult unknown = registry.execute(new ToolCall("1", "missing", JSON.createObjectNode()), context);
        assertEquals(ToolStatus.INVALID_TOOL_CALL, unknown.status());

        int[] calls = {0};
        registry.register(new Tool() {
            @Override public ToolDefinition definition() {
                return new ToolDefinition("demo", "demo tool", schema());
            }
            @Override public ToolResult execute(JsonNode input, ToolExecutionContext ignored) {
                calls[0]++;
                return ToolResult.ok("ok", JSON.createObjectNode(), Duration.ZERO);
            }
        });
        ToolResult invalid = registry.execute(new ToolCall("2", "demo", JSON.createObjectNode()), context);
        assertEquals(ToolStatus.INVALID_TOOL_CALL, invalid.status());
        ToolResult extra = registry.execute(new ToolCall("3", "demo",
                JSON.createObjectNode().put("value", "ok").put("unexpected", true)), context);
        assertEquals(ToolStatus.INVALID_TOOL_CALL, extra.status());
        assertEquals(0, calls[0]);
    }

    @Test
    void rejectsDuplicateToolNames() {
        Tool tool = new StubTool();
        ToolRegistry registry = new ToolRegistry().register(tool);
        assertThrows(IllegalArgumentException.class, () -> registry.register(tool));
    }

    private static JsonNode schema() {
        var schema = JSON.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", JSON.createObjectNode()
                .set("value", JSON.createObjectNode().put("type", "string")));
        schema.set("required", JSON.createArrayNode().add("value"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static final class StubTool implements Tool {
        @Override public ToolDefinition definition() { return new ToolDefinition("stub", "stub tool", schema()); }
        @Override public ToolResult execute(JsonNode input, ToolExecutionContext context) {
            return ToolResult.ok("ok", JSON.createObjectNode(), Duration.ZERO);
        }
    }
}
