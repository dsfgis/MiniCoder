package dev.minicodex.agent;

import java.util.Objects;

public record VerificationEvidence(String command, int exitCode, long workspaceRevision, long durationMs) {
    public VerificationEvidence {
        command = Objects.requireNonNullElse(command, "");
    }

    public boolean passed() {
        return exitCode == 0;
    }
}

