package dev.minicoder.tool.shell;

import java.time.Duration;

/**
 * 保存进程退出码、双流输出、耗时、截断和进程树清理结果。
 *
 * @author Self David (dsfgis@gmail.com)
 */
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
