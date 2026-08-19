package dev.minicoder.tool;

import dev.minicoder.agent.CancellationToken;
import dev.minicoder.observability.EventSink;
import dev.minicoder.workspace.Workspace;

import java.util.Objects;
import java.time.Duration;

/**
 * 向工具提供受控工作区、安全服务、事件出口与当前运行关联信息。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public record ToolExecutionContext(
        Workspace workspace,
        CancellationToken cancellationToken,
        EventSink eventSink,
        String runId,
        int iteration,
        Duration remainingBudget) {
    public ToolExecutionContext {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        eventSink = eventSink == null ? EventSink.noop() : eventSink;
        runId = Objects.requireNonNullElse(runId, "");
        remainingBudget = remainingBudget == null ? Duration.ofMinutes(10) : remainingBudget;
        if (remainingBudget.isZero() || remainingBudget.isNegative()) {
            remainingBudget = Duration.ofMillis(1);
        }
    }

    public ToolExecutionContext(Workspace workspace, CancellationToken cancellationToken, EventSink eventSink,
                                String runId, int iteration) {
        this(workspace, cancellationToken, eventSink, runId, iteration, Duration.ofMinutes(10));
    }
}
