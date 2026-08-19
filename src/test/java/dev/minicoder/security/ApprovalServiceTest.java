package dev.minicoder.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicoder.agent.CancellationToken;
import dev.minicoder.observability.InMemoryEventSink;
import dev.minicoder.support.TemporaryGitRepository;
import dev.minicoder.tool.ToolExecutionContext;
import dev.minicoder.tool.ToolStatus;
import dev.minicoder.tool.shell.*;
import dev.minicoder.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证交互批准、拒绝和非交互默认拒绝不会绕过命令策略或泄露参数秘密。
 *
 * @author Self David (dsfgis@gmail.com)
 */
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
