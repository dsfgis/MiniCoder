package dev.minicoder.agent;

import dev.minicoder.llm.LlmModels.Usage;
import dev.minicoder.observability.RunEvent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 汇总一次 Agent 运行的终态、说明、工具结果、验证证据和用量信息。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public record RunOutcome(
        String runId,
        RunStatus status,
        String reason,
        String finalText,
        int iterations,
        long workspaceRevision,
        List<VerificationEvidence> verification,
        List<String> changedFiles,
        Map<String, String> changeAttribution,
        String gitStatus,
        String gitStat,
        String gitDiff,
        Usage usage,
        Duration duration,
        List<RunEvent> events) {
    public RunOutcome {
        runId = Objects.requireNonNullElse(runId, "");
        Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNullElse(reason, "");
        finalText = Objects.requireNonNullElse(finalText, "");
        verification = List.copyOf(Objects.requireNonNullElse(verification, List.of()));
        changedFiles = List.copyOf(Objects.requireNonNullElse(changedFiles, List.of()));
        changeAttribution = Map.copyOf(Objects.requireNonNullElse(changeAttribution, Map.of()));
        gitStatus = Objects.requireNonNullElse(gitStatus, "");
        gitStat = Objects.requireNonNullElse(gitStat, "");
        gitDiff = Objects.requireNonNullElse(gitDiff, "");
        usage = usage == null ? Usage.ZERO : usage;
        duration = duration == null ? Duration.ZERO : duration;
        events = List.copyOf(Objects.requireNonNullElse(events, List.of()));
    }
}
