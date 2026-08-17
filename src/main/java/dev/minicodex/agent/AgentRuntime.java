package dev.minicodex.agent;

import dev.minicodex.config.RunConfig;
import dev.minicodex.llm.*;
import dev.minicodex.llm.LlmModels.*;
import dev.minicodex.observability.*;
import dev.minicodex.tool.*;
import dev.minicodex.workspace.Workspace;

import java.time.Duration;
import java.time.Instant;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AgentRuntime {
    public static final String SYSTEM_INSTRUCTIONS = """
            You are a local coding agent. Inspect before editing. Use only provided tools.
            Keep all work inside the workspace. Prefer apply_patch over shell-based writes.
            Run relevant verification after the last edit, inspect git_diff, then provide a concise evidence-based final answer.
            Do not claim success when verification is missing or failed.
            """;

    private final LlmProvider provider;
    private final ToolRegistry tools;
    private final CompletionGate completionGate;
    private final EventSink eventSink;

    public AgentRuntime(LlmProvider provider, ToolRegistry tools, CompletionGate completionGate, EventSink eventSink) {
        this.provider = provider;
        this.tools = tools;
        this.completionGate = completionGate;
        this.eventSink = eventSink == null ? EventSink.noop() : eventSink;
    }

    public RunOutcome run(RunConfig config, Workspace workspace, CancellationToken token) {
        return run(config, workspace, token, UUID.randomUUID().toString());
    }

    public RunOutcome run(RunConfig config, Workspace workspace, CancellationToken token, String runId) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        Instant started = Instant.now();
        ProviderCursor cursor = ProviderCursor.empty();
        List<ToolExchange> pendingResults = new ArrayList<>();
        List<VerificationEvidence> evidence = new ArrayList<>();
        Usage usage = Usage.ZERO;
        String lastFingerprint = "";
        int repeated = 0;
        Map<String, ToolStatus> unresolvedToolFailures = new LinkedHashMap<>();
        int iteration = 0;
        List<RunEvent> localEvents = new ArrayList<>();
        emit(localEvents, RunEvent.of(runId, 0, "run_started", RunStatus.RUNNING.name(), null, null,
                Map.of("workspace", workspace.root().toString())));
        try {
            for (iteration = 1; iteration <= config.maxIterations(); iteration++) {
                token.throwIfCancelled();
                Duration elapsed = Duration.between(started, Instant.now());
                Duration remaining = config.maxDuration().minus(elapsed);
                if (remaining.isZero() || remaining.isNegative()) {
                    return outcome(runId, RunStatus.LIMIT_REACHED, "Total run deadline reached", "", iteration - 1,
                            workspace, evidence, usage, started, localEvents);
                }
                emit(localEvents, RunEvent.of(runId, iteration, "provider_started", "RUNNING", cursor.responseId(),
                        null, Map.of()));
                String requestCursorId = cursor.responseId();
                int requestIteration = iteration;
                ProviderResponse response = provider.generate(new ProviderRequest(SYSTEM_INSTRUCTIONS, config.task(),
                        tools.definitions(), pendingResults, cursor,
                        new ProviderBudget(remaining.compareTo(Duration.ofSeconds(90)) < 0 ? remaining : Duration.ofSeconds(90), 2),
                        (completedAttempt, category, backoffMs) -> emit(localEvents,
                                RunEvent.of(runId, requestIteration, "provider_retried", "RETRYING", requestCursorId,
                                        null, Map.of("completedAttempt", completedAttempt, "category", category,
                                                "backoffMs", backoffMs)))), token);
                usage = usage.plus(response.usage());
                if (deadlineExceeded(started, config.maxDuration())) {
                    return outcome(runId, RunStatus.LIMIT_REACHED, "Total run deadline reached", "", iteration,
                            workspace, evidence, usage, started, localEvents);
                }
                cursor = response.nextCursor();
                pendingResults = new ArrayList<>();
                if (response.toolCalls().size() > 32) {
                    throw new ProviderException(ProviderException.Category.PROTOCOL, false, 0,
                            "Provider response exceeds the 32 tool-call limit");
                }
                emit(localEvents, RunEvent.of(runId, iteration, "provider_completed", "OK", response.responseId(),
                        null, Map.of("toolCalls", response.toolCalls().size())));

                if (!response.toolCalls().isEmpty()) {
                    for (ToolCall call : response.toolCalls()) {
                        token.throwIfCancelled();
                        if (deadlineExceeded(started, config.maxDuration())) {
                            return outcome(runId, RunStatus.LIMIT_REACHED, "Total run deadline reached", "", iteration,
                                    workspace, evidence, usage, started, localEvents);
                        }
                        EventSink toolEventSink = event -> {
                            localEvents.add(event);
                            eventSink.emit(event);
                        };
                        ToolExecutionContext context = new ToolExecutionContext(workspace, token, toolEventSink,
                                runId, iteration, config.maxDuration().minus(Duration.between(started, Instant.now())));
                        emit(localEvents, RunEvent.of(runId, iteration, "tool_started", "RUNNING", response.responseId(),
                                call.callId(), Map.of("tool", call.name())));
                        long revisionBefore = workspace.revision();
                        Optional<Tool> selectedTool = tools.find(call.name());
                        boolean mayModify = selectedTool.map(Tool::mayModifyWorkspace).orElse(false);
                        Map<String, String> contentBefore = mayModify
                                ? workspace.snapshotChangedContent() : Map.of();
                        ToolResult result = tools.execute(call, context);
                        if (mayModify && workspace.revision() == revisionBefore) {
                            workspace.recordExternalChanges(contentBefore);
                        }
                        emit(localEvents, RunEvent.of(runId, iteration, "tool_validated",
                                result.status() == ToolStatus.INVALID_TOOL_CALL ? "FAILED" : "OK",
                                response.responseId(), call.callId(), Map.of("tool", call.name())));
                        pendingResults.add(new ToolExchange(call.callId(), call.name(), result));
                        if (result.isSuccess()) {
                            unresolvedToolFailures.remove(call.name());
                        } else if (!result.isSuccess()) {
                            unresolvedToolFailures.put(call.name(), result.status());
                        }
                        boolean verificationRecorded = recordVerification(config, call, result,
                                workspace.revision(), evidence);
                        if (workspace.revision() != revisionBefore) {
                            emit(localEvents, RunEvent.of(runId, iteration, "workspace_changed", "OK",
                                    response.responseId(), call.callId(), Map.of("workspaceRevision", workspace.revision())));
                        }
                        if (verificationRecorded) {
                            emit(localEvents, RunEvent.of(runId, iteration, "verification_recorded",
                                    result.data().path("exitCode").asInt(-1) == 0 ? "PASSED" : "FAILED",
                                    response.responseId(), call.callId(), Map.of(
                                            "workspaceRevision", workspace.revision(),
                                            "exitCode", result.data().path("exitCode").asInt(-1))));
                        }
                        if (result.truncated()) {
                            emit(localEvents, RunEvent.of(runId, iteration, "output_truncated", "WARNING",
                                    response.responseId(), call.callId(), Map.of("tool", call.name())));
                        }
                        Map<String, Object> completionMetadata = new LinkedHashMap<>();
                        completionMetadata.put("tool", call.name());
                        completionMetadata.put("durationMs", result.duration().toMillis());
                        completionMetadata.put("truncated", result.truncated());
                        if (result.data().has("exitCode")) {
                            completionMetadata.put("exitCode", result.data().path("exitCode").asInt());
                        }
                        emit(localEvents, RunEvent.of(runId, iteration, "tool_completed", result.status().name(),
                                response.responseId(), call.callId(), completionMetadata));
                        String fingerprint = call.name() + "|" + call.arguments() + "|" + result.status() + "|"
                                + result.summary() + "|" + Integer.toHexString(result.data().toString().hashCode())
                                + "|" + workspace.revision();
                        if (fingerprint.equals(lastFingerprint)) repeated++; else repeated = 0;
                        lastFingerprint = fingerprint;
                        if (repeated >= 2) {
                            return outcome(runId, RunStatus.NO_PROGRESS, "Equivalent tool call repeated without progress",
                                    "", iteration, workspace, evidence, usage, started, localEvents);
                        }
                        if (result.status() == ToolStatus.WORKSPACE_INCONSISTENT) {
                            return outcome(runId, RunStatus.WORKSPACE_INCONSISTENT, result.summary(), "", iteration,
                                    workspace, evidence, usage, started, localEvents);
                        }
                    }
                    continue;
                }

                if (response.finalText().isPresent()) {
                    if (unresolvedToolFailures.values().stream().anyMatch(status -> status == ToolStatus.POLICY_DENIED
                            || status == ToolStatus.APPROVAL_DENIED)) {
                        return outcome(runId, RunStatus.POLICY_BLOCKED,
                                "The requested action remains blocked by command policy or approval",
                                response.finalText().get(), iteration, workspace, evidence, usage, started, localEvents);
                    }
                    if (!unresolvedToolFailures.isEmpty()) {
                        return outcome(runId, RunStatus.TOOL_ERROR,
                                "Tool failures were not resolved: " + unresolvedToolFailures,
                                response.finalText().get(), iteration, workspace, evidence, usage, started, localEvents);
                    }
                    CompletionGate.Decision decision = completionGate.evaluate(config, workspace.revision(), evidence);
                    return outcome(runId, decision.status(), decision.reason(), response.finalText().get(), iteration,
                            workspace, evidence, usage, started, localEvents);
                }
            }
            return outcome(runId, RunStatus.LIMIT_REACHED, "Maximum iteration count reached", "", iteration - 1,
                    workspace, evidence, usage, started, localEvents);
        } catch (CancellationToken.CancellationException e) {
            return outcome(runId, RunStatus.CANCELLED, e.getMessage(), "", iteration, workspace, evidence, usage,
                    started, localEvents);
        } catch (ProviderException e) {
            return outcome(runId, RunStatus.PROVIDER_ERROR, e.category() + ": " + e.getMessage(), "", iteration,
                    workspace, evidence, usage, started, localEvents);
        } catch (RuntimeException e) {
            return outcome(runId, RunStatus.TOOL_ERROR, e.getClass().getSimpleName() + ": " + e.getMessage(), "",
                    iteration, workspace, evidence, usage, started, localEvents);
        }
    }

    private static boolean recordVerification(RunConfig config, ToolCall call, ToolResult result, long revision,
                                           List<VerificationEvidence> evidence) {
        if (!call.name().equals("shell") || !result.data().has("exitCode")) return false;
        String executable = result.data().path("executable").asText();
        List<String> args = new ArrayList<>();
        result.data().path("args").forEach(node -> args.add(node.asText()));
        String command = (executable + " " + String.join(" ", args)).strip();
        if (!isVerificationCommand(config, command, executable, args)) return false;
        evidence.add(new VerificationEvidence(command, result.data().path("exitCode").asInt(-1), revision,
                result.data().path("durationMs").asLong()));
        return true;
    }

    private static boolean isVerificationCommand(RunConfig config, String command, String executable,
                                                  List<String> args) {
        if (config.verifyCommand().map(required -> CompletionGate.commandMatches(command, required)).orElse(false)) {
            return true;
        }
        String name = executable.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        String argumentText = String.join(" ", args).toLowerCase(Locale.ROOT);
        if (Set.of("pytest", "ctest", "javac", "go", "cargo", "dotnet", "mvn", "mvn.cmd", "mvnw", "mvnw.cmd",
                "gradle", "gradlew", "gradlew.bat", "npm", "pnpm", "yarn", "make").contains(name)) {
            return name.equals("pytest") || name.equals("ctest") || name.equals("javac")
                    || Arrays.stream(argumentText.split("\\s+"))
                    .anyMatch(Set.of("test", "check", "verify", "package", "build", "compile", "lint")::contains);
        }
        return false;
    }

    private static boolean deadlineExceeded(Instant started, Duration maximum) {
        return Duration.between(started, Instant.now()).compareTo(maximum) >= 0;
    }

    private RunOutcome outcome(String runId, RunStatus status, String reason, String finalText, int iterations,
                               Workspace workspace, List<VerificationEvidence> evidence, Usage usage, Instant started,
                               List<RunEvent> localEvents) {
        emit(localEvents, RunEvent.of(runId, iterations, "run_stopped", status.name(), null, null,
                Map.of("reason", reason)));
        Workspace.CommandResult statusResult = Workspace.git(workspace.root(), "status", "--porcelain=v1", "-z",
                "--untracked-files=all");
        List<Workspace.StatusEntry> statusEntries = Workspace.statusEntries(statusResult.stdout());
        String gitStatus = Workspace.renderStatus(statusEntries);
        List<String> changedFiles = statusEntries.stream().map(Workspace.StatusEntry::path).toList();
        Map<String, String> attribution = new TreeMap<>();
        for (Workspace.StatusEntry entry : statusEntries) {
            attribution.put(entry.path(), workspace.attribution(entry.path()).name());
        }
        ByteArrayOutputStream diffBytes = new ByteArrayOutputStream(64 * 1024);
        ByteArrayOutputStream statBytes = new ByteArrayOutputStream(16 * 1024);
        appendBounded(diffBytes, Workspace.git(workspace.root(), 512 * 1024,
                "diff", "--no-ext-diff").stdout(), 512 * 1024);
        appendBounded(statBytes, Workspace.git(workspace.root(), 128 * 1024,
                "diff", "--stat", "--no-ext-diff").stdout(), 128 * 1024);
        int untracked = 0;
        for (Workspace.StatusEntry entry : statusEntries) {
            if (!entry.status().equals("??") || !workspace.wasChangedByAgent(entry.path()) || untracked++ >= 20) {
                continue;
            }
            appendBounded(diffBytes, Workspace.git(workspace.root(), 512 * 1024,
                    "diff", "--no-index", "--", "NUL", entry.path()).stdout(), 512 * 1024);
            appendBounded(statBytes, Workspace.git(workspace.root(), 64 * 1024,
                    "diff", "--no-index", "--stat", "--", "NUL", entry.path()).stdout(), 128 * 1024);
        }
        String gitStat = statBytes.toString(StandardCharsets.UTF_8);
        String gitDiff = diffBytes.toString(StandardCharsets.UTF_8);
        return new RunOutcome(runId, status, reason, finalText, iterations, workspace.revision(), evidence,
                changedFiles, attribution, gitStatus, gitStat, gitDiff, usage,
                Duration.between(started, Instant.now()), localEvents);
    }

    private void emit(List<RunEvent> local, RunEvent event) {
        local.add(event);
        eventSink.emit(event);
    }

    private static void appendBounded(ByteArrayOutputStream target, String value, int limit) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int remaining = Math.max(0, limit - target.size());
        target.write(bytes, 0, Math.min(remaining, bytes.length));
    }
}
