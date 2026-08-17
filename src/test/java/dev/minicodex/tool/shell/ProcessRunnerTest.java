package dev.minicodex.tool.shell;

import dev.minicodex.agent.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        ProcessResult result = new ProcessRunner().run(new CommandSpec("powershell",
                List.of("-NoProfile", "-Command",
                        "$child = Start-Process powershell -WindowStyle Hidden -PassThru -ArgumentList '-NoProfile','-Command','Start-Sleep -Seconds 10'; "
                                + "Write-Output $child.Id; Start-Sleep -Seconds 10"),
                Duration.ofMillis(250), 1024, ShellMode.NONE), temp, new CancellationToken());
        assertTrue(result.timedOut());
        assertTrue(result.processTreeCleaned());
    }
}
