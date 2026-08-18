package dev.minicoder.cli;

import dev.minicoder.agent.RunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.minicoder.support.TemporaryGitRepository;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliTest {
    @TempDir Path temp;
    @Test
    void helpDocumentsSafetyBoundaryAndExample() {
        Capture capture = execute("--help");
        assertEquals(0, capture.exitCode());
        assertTrue(capture.out().contains("not an OS sandbox"));
        assertTrue(capture.out().contains("Usage: mini-coder"));
        assertTrue(capture.out().contains("Example:"));
        assertTrue(capture.out().contains("mini-coder --workspace"));
        assertTrue(capture.out().contains("--workspace"));
        assertTrue(capture.out().contains("--verify-command"));
        assertTrue(capture.out().contains("OPENAI_API_KEY"));
    }

    @Test
    void versionUsesMiniCoderBrand() {
        Capture capture = execute("--version");
        assertEquals(0, capture.exitCode());
        assertEquals("Mini Coder 0.1.0", capture.out().trim());
    }

    @Test
    void parseAndConfigurationFailuresUseStableConfigurationExitCode() {
        Capture missing = execute();
        assertEquals(20, missing.exitCode());
        assertTrue(missing.err().contains("CONFIG_ERROR"));

        Capture provider = execute("--workspace", ".", "--task", "x", "--provider", "unknown",
                "--model", "test");
        assertEquals(20, provider.exitCode());
        assertTrue(provider.err().contains("supports openai"));
    }

    @Test
    void terminalStatusesHaveDocumentedExitCodes() {
        assertEquals(0, Main.exitCode(RunStatus.SUCCEEDED));
        assertEquals(10, Main.exitCode(RunStatus.SUCCEEDED_WITH_WARNINGS));
        assertEquals(11, Main.exitCode(RunStatus.CANCELLED));
        assertEquals(20, Main.exitCode(RunStatus.CONFIG_ERROR));
        assertEquals(21, Main.exitCode(RunStatus.POLICY_BLOCKED));
        assertEquals(22, Main.exitCode(RunStatus.PROVIDER_ERROR));
        assertEquals(30, Main.exitCode(RunStatus.TOOL_ERROR));
        assertEquals(40, Main.exitCode(RunStatus.LIMIT_REACHED));
        assertEquals(41, Main.exitCode(RunStatus.NO_PROGRESS));
    }

    @Test
    void offlineScriptedProviderStartsDisplaysRunIdAndNeedsNoCredential() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("CLI 示例 中文"));
        Capture capture = execute("--workspace", repo.toString(), "--task", "inspect only",
                "--provider", "scripted", "--non-interactive");
        assertEquals(10, capture.exitCode());
        assertTrue(capture.out().contains("runId:"));
        assertTrue(capture.out().contains("SUCCEEDED_WITH_WARNINGS"));
        assertFalse(capture.err().contains("OPENAI_API_KEY"));
    }

    private Capture execute(String... args) {
        CommandLine command = Main.commandLine();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        command.setOut(new PrintWriter(out, true, StandardCharsets.UTF_8));
        command.setErr(new PrintWriter(err, true, StandardCharsets.UTF_8));
        int exit = command.execute(args);
        return new Capture(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Capture(int exitCode, String out, String err) {}
}
