package dev.minicoder.llm.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicoder.agent.CancellationToken;
import dev.minicoder.llm.LlmModels.ProviderBudget;
import dev.minicoder.llm.LlmModels.ProviderCursor;
import dev.minicoder.llm.LlmModels.ProviderRequest;
import dev.minicoder.llm.LlmModels.ProviderResponse;
import dev.minicoder.llm.LlmModels.ToolCall;
import dev.minicoder.llm.LlmModels.ToolExchange;
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
 * 在显式授权、凭据和网络均可用时验证真实 DeepSeek function call 与结果续接。
 *
 * @author Self David (dsfgis@gmail.com)
 */
@EnabledIfSystemProperty(named = "mini.coder.deepseek.smoke", matches = "true")
class DeepSeekSmokeTest {
    @Test
    void performsExplicitlyEnabledReadOnlyProviderRoundTrip() throws Exception {
        String key = System.getenv("DEEPSEEK_API_KEY");
        String model = System.getenv("DEEPSEEK_MODEL");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "DEEPSEEK_API_KEY is required for smoke test");
        Assumptions.assumeTrue(model != null && !model.isBlank(), "DEEPSEEK_MODEL is required for smoke test");
        String base = System.getenv().getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com");
        DeepSeekResponsesProvider provider = new DeepSeekResponsesProvider(HttpClient.newHttpClient(),
                URI.create(base), key, model, new Redactor(List.of(key)));
        ToolDefinition readFile = new ToolDefinition("read_file", "Read the fixed smoke-test file",
                ToolSchemas.property(ToolSchemas.object(), "path", "string", true));
        ProviderResponse response = provider.generate(new ProviderRequest(
                "Call read_file exactly once with path README.md. After its result, reply briefly.",
                "Read the fixed smoke-test README.", List.of(readFile), List.of(), ProviderCursor.empty(),
                new ProviderBudget(Duration.ofSeconds(60), 1)), new CancellationToken());
        assertTrue(!response.toolCalls().isEmpty(), "real DeepSeek Provider must return a tool call");
        ToolCall call = response.toolCalls().getFirst();
        ToolResult result = ToolResult.ok("read", new ObjectMapper().createObjectNode()
                .put("content", "Mini Coder smoke fixture"), Duration.ZERO);
        ProviderResponse completed = provider.generate(new ProviderRequest(
                "Use the supplied tool result and reply briefly.", "Read the fixed smoke-test README.",
                List.of(readFile), List.of(new ToolExchange(call.callId(), call.name(), result)),
                response.nextCursor(), new ProviderBudget(Duration.ofSeconds(60), 1)), new CancellationToken());
        assertTrue(completed.finalText().isPresent());
    }
}
