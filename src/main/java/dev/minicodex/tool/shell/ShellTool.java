package dev.minicodex.tool.shell;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.minicodex.security.*;
import dev.minicodex.tool.*;
import dev.minicodex.observability.RunEvent;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ShellTool implements Tool {
    private final ProcessRunner runner;
    private final CommandPolicy policy;
    private final ApprovalService approvals;
    private final Redactor redactor;
    private final ToolDefinition definition;

    public ShellTool(ProcessRunner runner, CommandPolicy policy, ApprovalService approvals, Redactor redactor) {
        this.runner = runner;
        this.policy = policy;
        this.approvals = approvals;
        this.redactor = redactor;
        var schema = ToolSchemas.object();
        ToolSchemas.property(schema, "executable", "string", true);
        ToolSchemas.property(schema, "args", "array", false);
        ToolSchemas.property(schema, "timeoutMs", "integer", false);
        ToolSchemas.property(schema, "shellMode", "string", false);
        definition = new ToolDefinition("shell", "Run a policy-controlled local command in the workspace.", schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override public boolean mayModifyWorkspace() { return true; }

    @Override
    public ToolResult execute(JsonNode input, ToolExecutionContext context) {
        Instant started = Instant.now();
        String executable = input.path("executable").asText();
        List<String> args = new ArrayList<>();
        input.path("args").forEach(node -> args.add(node.asText()));
        ShellMode mode;
        try {
            mode = ShellMode.valueOf(input.path("shellMode").asText("NONE").toUpperCase());
        } catch (IllegalArgumentException e) {
            return ToolResult.failure(ToolStatus.INVALID_INPUT, "INVALID_SHELL_MODE", "Unknown shellMode",
                    Duration.between(started, Instant.now()));
        }
        CommandPolicy.Decision decision = mode == ShellMode.NONE
                ? policy.classify(executable, args) : policy.classifyExplicitShell(executable, args);
        if (decision.classification() == RiskClassification.DENY) {
            return ToolResult.failure(ToolStatus.POLICY_DENIED, "COMMAND_DENIED", decision.reason(),
                    Duration.between(started, Instant.now()));
        }
        if (decision.classification() == RiskClassification.REQUIRE_APPROVAL
                ) {
            context.eventSink().emit(RunEvent.of(context.runId(), context.iteration(), "approval_requested",
                    "WAITING", null, null, java.util.Map.of("reason", decision.reason())));
            boolean approved = approvals.approve(executable, args, decision.reason());
            context.eventSink().emit(RunEvent.of(context.runId(), context.iteration(), "approval_resolved",
                    approved ? "APPROVED" : "DENIED", null, null, java.util.Map.of()));
            if (!approved) {
                return ToolResult.failure(ToolStatus.APPROVAL_DENIED, "APPROVAL_DENIED", decision.reason(),
                        Duration.between(started, Instant.now()));
            }
        }
        try {
            long requestedTimeoutMs = Math.clamp(input.path("timeoutMs").asLong(60_000), 100, 600_000);
            long remainingMs = Math.max(1, context.remainingBudget().toMillis());
            long timeoutMs = Math.min(requestedTimeoutMs, remainingMs);
            ProcessResult result = runner.run(new CommandSpec(executable, args, Duration.ofMillis(timeoutMs),
                    256 * 1024, mode), context.workspace().root(), context.cancellationToken());
            var data = JsonNodeFactory.instance.objectNode();
            data.put("executable", executable);
            var argsNode = data.putArray("args");
            args.forEach(argsNode::add);
            data.put("exitCode", result.exitCode());
            data.put("stdout", redactor.redact(result.stdout()));
            data.put("stderr", redactor.redact(result.stderr()));
            data.put("durationMs", result.duration().toMillis());
            data.put("timedOut", result.timedOut());
            data.put("stdoutBytes", result.stdoutBytes());
            data.put("stderrBytes", result.stderrBytes());
            data.put("processTreeCleaned", result.processTreeCleaned());
            ToolStatus status = result.timedOut() ? ToolStatus.TIMEOUT : ToolStatus.OK;
            return new ToolResult(status, result.timedOut() ? "Command timed out" : "Command exited " + result.exitCode(),
                    data, result.truncated(), Optional.empty(), Duration.between(started, Instant.now()));
        } catch (IOException e) {
            return ToolResult.failure(ToolStatus.FAILED, "PROCESS_FAILED", redactor.redact(e.getMessage()),
                    Duration.between(started, Instant.now()));
        }
    }
}
