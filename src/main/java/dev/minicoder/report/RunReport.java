package dev.minicoder.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.minicoder.agent.RunOutcome;
import dev.minicoder.agent.VerificationEvidence;
import dev.minicoder.security.Redactor;

import java.util.Objects;
import java.util.List;

/**
 * 从同一份运行事实生成终端与 JSON 报告，保证状态、证据和用量表达一致。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class RunReport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RunOutcome outcome;
    private final String provider;
    private final String model;
    private final Redactor redactor;

    public RunReport(RunOutcome outcome, String provider, String model, Redactor redactor) {
        this.outcome = Objects.requireNonNull(outcome);
        this.provider = Objects.requireNonNullElse(provider, "");
        this.model = Objects.requireNonNullElse(model, "");
        this.redactor = Objects.requireNonNull(redactor);
    }

    public String renderConsole() {
        StringBuilder output = new StringBuilder();
        output.append("runId: ").append(outcome.runId()).append('\n');
        output.append("status: ").append(outcome.status()).append('\n');
        output.append("exitCode: ").append(outcome.status().exitCode()).append('\n');
        output.append("reason: ").append(redactor.redact(outcome.reason())).append('\n');
        output.append("provider/model: ").append(redactor.redact(provider)).append('/')
                .append(redactor.redact(model)).append('\n');
        output.append("iterations: ").append(outcome.iterations()).append('\n');
        output.append("workspaceRevision: ").append(outcome.workspaceRevision()).append('\n');
        output.append("changedFiles: ").append(redactor.redact(outcome.changedFiles().toString())).append('\n');
        output.append("changeAttribution: ")
                .append(redactor.redact(outcome.changeAttribution().toString())).append('\n');
        if (!outcome.verification().isEmpty()) {
            output.append("verification:\n");
            for (VerificationEvidence evidence : outcome.verification()) {
                output.append("  - ").append(redactor.redact(evidence.command()))
                        .append(" => exit ").append(evidence.exitCode())
                        .append(" @ revision ").append(evidence.workspaceRevision()).append('\n');
            }
        }
        output.append("usage: input=").append(outcome.usage().inputTokens())
                .append(", output=").append(outcome.usage().outputTokens())
                .append(", total=").append(outcome.usage().totalTokens()).append('\n');
        output.append("durationMs: ").append(outcome.duration().toMillis()).append('\n');
        if (!outcome.finalText().isBlank()) {
            output.append("\n").append(redactor.redact(outcome.finalText())).append('\n');
        }
        if (!outcome.gitStat().isBlank()) {
            output.append("\ngit stat:\n").append(redactor.redact(outcome.gitStat()));
        }
        if (!outcome.gitDiff().isBlank()) {
            output.append("\ngit diff:\n").append(redactor.redact(outcome.gitDiff()));
        }
        return output.toString();
    }

    public String renderJson() {
        ObjectNode root = JSON.createObjectNode();
        root.put("runId", outcome.runId());
        root.put("status", outcome.status().name());
        root.put("exitCode", outcome.status().exitCode());
        root.put("reason", redactor.redact(outcome.reason()));
        root.put("provider", redactor.redact(provider));
        root.put("model", redactor.redact(model));
        root.put("iterations", outcome.iterations());
        root.put("workspaceRevision", outcome.workspaceRevision());
        root.put("durationMs", outcome.duration().toMillis());
        root.put("finalText", redactor.redact(outcome.finalText()));
        root.put("gitStatus", redactor.redact(outcome.gitStatus()));
        root.put("gitStat", redactor.redact(outcome.gitStat()));
        root.put("gitDiff", redactor.redact(outcome.gitDiff()));
        root.set("changedFiles", redactedValue(outcome.changedFiles()));
        root.set("changeAttribution", redactedValue(outcome.changeAttribution()));
        ObjectNode usage = root.putObject("usage");
        usage.put("inputTokens", outcome.usage().inputTokens());
        usage.put("outputTokens", outcome.usage().outputTokens());
        usage.put("totalTokens", outcome.usage().totalTokens());
        ArrayNode verification = root.putArray("verification");
        for (VerificationEvidence item : outcome.verification()) {
            ObjectNode node = verification.addObject();
            node.put("command", redactor.redact(item.command()));
            node.put("exitCode", item.exitCode());
            node.put("workspaceRevision", item.workspaceRevision());
            node.put("durationMs", item.durationMs());
        }
        ArrayNode events = root.putArray("events");
        outcome.events().forEach(event -> {
            ObjectNode node = events.addObject();
            node.put("timestamp", event.timestamp().toString());
            node.put("iteration", event.iteration());
            node.put("type", event.type());
            node.put("status", event.status());
            event.responseId().ifPresent(value -> node.put("responseId", value));
            event.toolCallId().ifPresent(value -> node.put("toolCallId", value));
            node.set("metadata", redactedValue(event.metadata()));
        });
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to render JSON report", e);
        }
    }

    private JsonNode redactedValue(Object value) {
        try {
            return JSON.readTree(redactor.redact(JSON.writeValueAsString(value)));
        } catch (JsonProcessingException e) {
            return JSON.valueToTree(List.of("[unavailable]"));
        }
    }
}
