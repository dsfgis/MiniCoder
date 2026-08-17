package dev.minicodex.tool.shell;

import java.time.Duration;

public record ProcessResult(
        int exitCode,
        String stdout,
        String stderr,
        Duration duration,
        boolean timedOut,
        boolean truncated,
        long stdoutBytes,
        long stderrBytes,
        boolean processTreeCleaned) {
}

