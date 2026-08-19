package dev.minicoder.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicoder.agent.RunOutcome;
import dev.minicoder.agent.RunStatus;
import dev.minicoder.agent.VerificationEvidence;
import dev.minicoder.llm.LlmModels.Usage;
import dev.minicoder.observability.RunEvent;
import dev.minicoder.security.Redactor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证终端和 JSON 报告共享同一运行事实并保持稳定字段与秘密脱敏。
 *
 * @author Self David (dsfgis@gmail.com)
 */
class RunReportTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void consoleAndJsonShareCoreFactsAndRedactAllPayloads() throws Exception {
        String secret = "sk-super-secret-value";
        RunEvent event = new RunEvent(Instant.parse("2026-08-17T00:00:00Z"), "run-1", 1,
                "tool_completed", "OK", Optional.of("resp-1"), Optional.of("call-1"),
                Map.of("header", "Authorization: Bearer " + secret));
        RunOutcome outcome = new RunOutcome("run-1", RunStatus.SUCCEEDED, "verified " + secret,
                "done " + secret, 2, 1, List.of(new VerificationEvidence("mvn test " + secret, 0, 1, 12)),
                List.of("src/" + secret + ".java"), Map.of("src/" + secret + ".java", "AGENT_MODIFIED"),
                " M src/" + secret + ".java", "1 file", "-" + secret, new Usage(2, 3, 5),
                Duration.ofMillis(20), List.of(event));
        RunReport report = new RunReport(outcome, "openai", "gpt-test", new Redactor(List.of(secret)));

        String console = report.renderConsole();
        String json = report.renderJson();
        assertFalse(console.contains(secret));
        assertFalse(json.contains(secret));
        assertTrue(console.contains("runId: run-1"));
        assertEquals("run-1", JSON.readTree(json).path("runId").asText());
        assertEquals("SUCCEEDED", JSON.readTree(json).path("status").asText());
        assertEquals(5, JSON.readTree(json).path("usage").path("totalTokens").asLong());
        assertEquals("[REDACTED]", JSON.readTree(json).path("events").get(0)
                .path("metadata").path("header").asText().substring("Authorization: Bearer ".length()));
    }
}
