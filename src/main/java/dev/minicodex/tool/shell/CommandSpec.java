package dev.minicodex.tool.shell;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

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

