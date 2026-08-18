package dev.minicoder.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicoder.agent.CancellationToken;
import dev.minicoder.llm.LlmModels.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LlmProviderContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void scriptedProviderPreservesCallsAndCursor() throws Exception {
        ToolCall call = new ToolCall("call-1", "read_file", JSON.readTree("{\"path\":\"README.md\"}"));
        ProviderResponse expected = new ProviderResponse("resp-1", Optional.empty(), List.of(call),
                new ProviderCursor("resp-1", Map.of("mode", "test")), new Usage(10, 2, 12));
        ScriptedLlmProvider provider = new ScriptedLlmProvider(expected);
        ProviderRequest request = new ProviderRequest("system", "task", List.of(), List.of(),
                ProviderCursor.empty(), new ProviderBudget(Duration.ofSeconds(1), 0));

        ProviderResponse actual = provider.generate(request, new CancellationToken());

        assertEquals(expected, actual);
        assertEquals("call-1", actual.toolCalls().getFirst().callId());
        assertEquals("resp-1", actual.nextCursor().responseId());
        assertEquals(1, provider.requests().size());
    }

    @Test
    void cancellationStopsProviderBeforeConsumingScript() {
        ScriptedLlmProvider provider = new ScriptedLlmProvider(ProviderResponse.finalText("r", "done"));
        CancellationToken token = new CancellationToken();
        token.cancel();
        ProviderRequest request = new ProviderRequest("", "", List.of(), List.of(),
                ProviderCursor.empty(), new ProviderBudget(Duration.ofSeconds(1), 0));

        assertThrows(CancellationToken.CancellationException.class,
                () -> provider.generate(request, token));
        assertEquals(1, provider.remainingSteps());
    }
}

