package dev.minicoder.tool.shell;

import dev.minicoder.agent.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证进程双流捕获、非零退出、输出截断、超时和父子进程树清理。
 *
 * @author Self David (dsfgis@gmail.com)
 */
class ProcessRunnerTest {
    @TempDir Path temp;

    @Test
    void capturesStreamsExitAndTruncation() throws Exception {
        ProcessResult result = new ProcessRunner().run(new CommandSpec("powershell",
                List.of("-NoProfile", "-Command", "$host.UI.WriteErrorLine('err'); '1234567890'; exit 7"),
                Duration.ofSeconds(10), 5, ShellMode.NONE), temp, new CancellationToken());
        assertEquals(7, result.exitCode());
        assertTrue(result.stdout().startsWith("12345"));
        assertTrue(result.stderr().contains("err") || result.stderrBytes() > 0);
        assertTrue(result.truncated());
    }

    @Test
    void timesOutAndCleansProcess() throws Exception {
        // 子 PowerShell 继续休眠可证明超时处理清理的是整棵进程树，而不只是直接父进程。
        ProcessResult result = new ProcessRunner().run(new CommandSpec("powershell",
                List.of("-NoProfile", "-Command",
                        "$child = Start-Process powershell -WindowStyle Hidden -PassThru -ArgumentList '-NoProfile','-Command','Start-Sleep -Seconds 10'; "
                                + "Write-Output $child.Id; Start-Sleep -Seconds 10"),
                Duration.ofMillis(250), 1024, ShellMode.NONE), temp, new CancellationToken());
        assertTrue(result.timedOut());
        assertTrue(result.processTreeCleaned());
    }
}
