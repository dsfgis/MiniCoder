package dev.minicoder.llm.openai;

import dev.minicoder.agent.CancellationToken;
import dev.minicoder.llm.LlmModels.*;
import dev.minicoder.security.Redactor;
import dev.minicoder.tool.ToolDefinition;
import dev.minicoder.tool.ToolResult;
import dev.minicoder.tool.ToolSchemas;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在显式授权、凭据和网络均可用时验证真实 OpenAI 工具调用续接。
 *
 * @author Self David (dsfgis@gmail.com)
 */
@EnabledIfSystemProperty(named = "mini.codex.openai.smoke", matches = "true")
class OpenAiSmokeTest {
    @Test
    void performsExplicitlyEnabledReadOnlyProviderRoundTrip() throws Exception {
        String key = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "OPENAI_API_KEY is required for smoke test");
        Assumptions.assumeTrue(model != null && !model.isBlank(), "OPENAI_MODEL is required for smoke test");
        String base = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com");
        OpenAiResponsesProvider provider = new OpenAiResponsesProvider(HttpClient.newHttpClient(), URI.create(base),
                key, model, new Redactor(List.of(key)));
        ToolDefinition readFile = new ToolDefinition("read_file", "Read the fixed smoke-test file",
                ToolSchemas.property(ToolSchemas.object(), "path", "string", true));
        ProviderResponse response = provider.generate(new ProviderRequest(
                "Call read_file exactly once with path README.md. After its result, reply briefly.",
                "Read the fixed smoke-test README.", List.of(readFile), List.of(), ProviderCursor.empty(),
                new ProviderBudget(Duration.ofSeconds(45), 1)), new CancellationToken());
        assertTrue(!response.toolCalls().isEmpty(), "real Provider must return a tool call");
        ToolCall call = response.toolCalls().getFirst();
        ToolResult result = ToolResult.ok("read", new com.fasterxml.jackson.databind.ObjectMapper()
                .createObjectNode().put("content", "Mini Coder smoke fixture"), Duration.ZERO);
        ProviderResponse completed = provider.generate(new ProviderRequest(
                "Use the supplied tool result and reply briefly.", "Read the fixed smoke-test README.",
                List.of(readFile), List.of(new ToolExchange(call.callId(), call.name(), result)),
                response.nextCursor(), new ProviderBudget(Duration.ofSeconds(45), 1)), new CancellationToken());
        assertTrue(completed.finalText().isPresent());
    }
}
