package dev.minicoder.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RunConfigTest {
    @Test
    void rejectsInvalidBudgets() {
        assertThrows(IllegalArgumentException.class, () -> new RunConfig(
                Path.of("."), "task", "fake", "model", 0, Duration.ofSeconds(1),
                Optional.empty(), false, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new RunConfig(
                Path.of("."), "task", "fake", "model", 1, Duration.ZERO,
                Optional.empty(), false, Optional.empty()));
    }
}

