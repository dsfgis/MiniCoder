package dev.minicoder.tool.shell;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 描述待执行程序、参数、工作目录、超时、输出上限和显式 Shell 模式。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public record CommandSpec(
        String executable,
        List<String> arguments,
        Duration timeout,
        int maxOutputBytes,
        ShellMode shellMode) {
    public CommandSpec {
        if (executable == null || executable.isBlank()) {
            throw new IllegalArgumentException("executable must not be blank");
        }
        arguments = List.copyOf(Objects.requireNonNullElse(arguments, List.of()));
        timeout = Objects.requireNonNullElse(timeout, Duration.ofSeconds(60));
        if (timeout.isZero() || timeout.isNegative() || maxOutputBytes < 1) {
            throw new IllegalArgumentException("Invalid process limits");
        }
        shellMode = shellMode == null ? ShellMode.NONE : shellMode;
    }
}
