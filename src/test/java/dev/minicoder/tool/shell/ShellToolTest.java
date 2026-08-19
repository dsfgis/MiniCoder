package dev.minicoder.tool.shell;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicoder.security.*;
import dev.minicoder.support.TemporaryGitRepository;
import dev.minicoder.tool.ToolStatus;
import dev.minicoder.tool.ToolTestContext;
import dev.minicoder.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 ShellTool 的参数化执行、策略裁决、审批路径和统一结果映射。
 *
 * @author Self David (dsfgis@gmail.com)
 */
class ShellToolTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void allowsReadOnlyAndBlocksDeniedCommandBeforeStart() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        Workspace workspace = Workspace.open(repo);
        CountingRunner runner = new CountingRunner();
        ShellTool tool = new ShellTool(runner, new CommandPolicy(workspace.root()),
                ApprovalService.denyAll(), new Redactor(List.of()));

        var ok = tool.execute(JSON.readTree("{\"executable\":\"git\",\"args\":[\"status\",\"--short\"]}"),
                ToolTestContext.create(workspace));
        assertEquals(ToolStatus.OK, ok.status());
        assertEquals(1, runner.calls);

        var denied = tool.execute(JSON.readTree("{\"executable\":\"rm\",\"args\":[\"-rf\",\".\"]}"),
                ToolTestContext.create(workspace));
        assertEquals(ToolStatus.POLICY_DENIED, denied.status());
        assertEquals(1, runner.calls);

        var shellDenied = tool.execute(JSON.readTree("""
                        {"executable":"Write-Output","args":["ok; Remove-Item file"],"shellMode":"POWERSHELL"}
                        """), ToolTestContext.create(workspace));
        assertEquals(ToolStatus.POLICY_DENIED, shellDenied.status());
        assertEquals(1, runner.calls);
    }

    private static final class CountingRunner extends ProcessRunner {
        int calls;
        @Override public ProcessResult run(CommandSpec spec, Path workingDirectory,
                                           dev.minicoder.agent.CancellationToken token) throws java.io.IOException {
            calls++;
            return super.run(spec, workingDirectory, token);
        }
    }
}
