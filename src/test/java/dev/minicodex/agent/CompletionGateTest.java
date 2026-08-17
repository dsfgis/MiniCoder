package dev.minicodex.agent;

import dev.minicodex.config.RunConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompletionGateTest {
    private final CompletionGate gate = new CompletionGate();

    @Test
    void requiresSuccessfulEvidenceAtFinalRevision() {
        RunConfig config = config(Optional.empty());
        assertEquals(RunStatus.SUCCEEDED,
                gate.evaluate(config, 2, List.of(new VerificationEvidence("mvn test", 0, 2, 10))).status());
        assertEquals(RunStatus.SUCCEEDED_WITH_WARNINGS,
                gate.evaluate(config, 2, List.of(new VerificationEvidence("mvn test", 0, 1, 10))).status());
        assertEquals(RunStatus.TOOL_ERROR,
                gate.evaluate(config, 2, List.of(new VerificationEvidence("mvn test", 1, 2, 10))).status());
    }

    @Test
    void enforcesUserSpecifiedVerificationFingerprint() {
        RunConfig config = config(Optional.of("mvn verify"));
        assertEquals(RunStatus.SUCCEEDED_WITH_WARNINGS,
                gate.evaluate(config, 1, List.of(new VerificationEvidence("mvn test", 0, 1, 10))).status());
        assertEquals(RunStatus.SUCCEEDED,
                gate.evaluate(config, 1, List.of(new VerificationEvidence("mvn -q verify", 0, 1, 10))).status());
    }

    @Test
    void noModificationCannotBecomeFullSuccess() {
        assertEquals(RunStatus.SUCCEEDED_WITH_WARNINGS, gate.evaluate(config(Optional.empty()), 0, List.of()).status());
    }

    @Test
    void failedCurrentRevisionVerificationIsAnErrorNotWarningSuccess() {
        assertEquals(RunStatus.TOOL_ERROR, gate.evaluate(config(Optional.empty()), 1,
                List.of(new VerificationEvidence("mvn test", 1, 1, 10))).status());
    }

    private RunConfig config(Optional<String> verify) {
        return new RunConfig(Path.of("."), "task", "fake", "scripted", 5, Duration.ofSeconds(10),
                verify, false, Optional.empty());
    }
}
