package dev.minicodex.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicodex.agent.CancellationToken;
import dev.minicodex.observability.InMemoryEventSink;
import dev.minicodex.support.TemporaryGitRepository;
import dev.minicodex.tool.ToolExecutionContext;
import dev.minicodex.tool.ToolStatus;
import dev.minicodex.tool.shell.*;
import dev.minicodex.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void approvedExternalCommandUsesTestDoubleAndDenialStartsNoProcess() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        Workspace workspace = Workspace.open(repo);
        FakeRunner runner = new FakeRunner();
        InMemoryEventSink events = new InMemoryEventSink();
        ToolExecutionContext context = new ToolExecutionContext(workspace, new CancellationToken(), events,
                "approval-run", 1);
        var input = JSON.readTree("{\"executable\":\"curl\",\"args\":[\"https://example.invalid\"]}");

        ShellTool allowed = new ShellTool(runner, new CommandPolicy(repo), ApprovalService.allowAllForTests(),
                new Redactor(List.of()));
        assertEquals(ToolStatus.OK, allowed.execute(input, context).status());
        assertEquals(1, runner.calls);
        assertEquals(List.of("approval_requested", "approval_resolved"), events.events().stream()
                .map(event -> event.type()).toList());

        ShellTool denied = new ShellTool(runner, new CommandPolicy(repo), ApprovalService.denyAll(),
                new Redactor(List.of()));
        assertEquals(ToolStatus.APPROVAL_DENIED, denied.execute(input, context).status());
        assertEquals(1, runner.calls);
    }

    private static final class FakeRunner extends ProcessRunner {
        int calls;
        @Override
        public ProcessResult run(CommandSpec spec, Path workingDirectory, CancellationToken token) {
            calls++;
            return new ProcessResult(0, "fake", "", Duration.ofMillis(1), false, false,
                    4, 0, true);
        }
    }
}
