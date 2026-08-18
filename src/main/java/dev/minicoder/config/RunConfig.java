package dev.minicoder.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record RunConfig(
        Path workspace,
        String task,
        String provider,
        String model,
        int maxIterations,
        Duration maxDuration,
        Optional<String> verifyCommand,
        boolean interactive,
        Optional<Path> jsonReport) {

    public RunConfig {
        Objects.requireNonNull(workspace, "workspace");
        task = requireText(task, "task");
        if (task.length() > 100_000) throw new IllegalArgumentException("task exceeds 100000 characters");
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        if (provider.length() > 50 || model.length() > 200) {
            throw new IllegalArgumentException("provider or model identifier is too long");
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be positive");
        }
        Objects.requireNonNull(maxDuration, "maxDuration");
        if (maxDuration.isZero() || maxDuration.isNegative()) {
            throw new IllegalArgumentException("maxDuration must be positive");
        }
        verifyCommand = verifyCommand == null ? Optional.empty() : verifyCommand.filter(s -> !s.isBlank());
        jsonReport = jsonReport == null ? Optional.empty() : jsonReport;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
