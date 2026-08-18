package dev.minicoder.agent;

import dev.minicoder.config.RunConfig;

import java.util.Comparator;
import java.util.List;

public final class CompletionGate {
    public Decision evaluate(RunConfig config, long workspaceRevision, List<VerificationEvidence> evidence) {
        if (workspaceRevision == 0) {
            return new Decision(RunStatus.SUCCEEDED_WITH_WARNINGS,
                    "No workspace modification was observed; the coding task is not fully verified");
        }
        List<VerificationEvidence> currentRevision = evidence.stream()
                .filter(item -> item.workspaceRevision() == workspaceRevision)
                .filter(item -> config.verifyCommand().map(required -> commandMatches(item.command(), required))
                        .orElse(true))
                .toList();
        if (!currentRevision.isEmpty() && currentRevision.stream().noneMatch(VerificationEvidence::passed)) {
            return new Decision(RunStatus.TOOL_ERROR,
                    "Relevant verification failed at the final workspace revision");
        }
        VerificationEvidence current = evidence.stream()
                .filter(item -> item.workspaceRevision() == workspaceRevision)
                .filter(VerificationEvidence::passed)
                .filter(item -> config.verifyCommand().map(required -> commandMatches(item.command(), required))
                        .orElse(true))
                .max(Comparator.comparingLong(VerificationEvidence::durationMs))
                .orElse(null);
        if (current == null) {
            return new Decision(RunStatus.SUCCEEDED_WITH_WARNINGS,
                    "The final workspace revision has no successful relevant verification evidence");
        }
        return new Decision(RunStatus.SUCCEEDED, "Final workspace revision is verified by: " + current.command());
    }

    static boolean commandMatches(String actual, String required) {
        String[] actualTokens = normalize(actual).split(" ");
        String[] requiredTokens = normalize(required).split(" ");
        int requiredIndex = 0;
        for (String token : actualTokens) {
            if (requiredIndex < requiredTokens.length && token.equals(requiredTokens[requiredIndex])) {
                requiredIndex++;
            }
        }
        return requiredIndex == requiredTokens.length;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("\\s+", " ").strip();
    }

    public record Decision(RunStatus status, String reason) {}
}
