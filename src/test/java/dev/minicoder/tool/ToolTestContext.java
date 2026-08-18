package dev.minicoder.tool;

import dev.minicoder.agent.CancellationToken;
import dev.minicoder.observability.EventSink;
import dev.minicoder.workspace.Workspace;

public final class ToolTestContext {
    private ToolTestContext() {}

    public static ToolExecutionContext create(Workspace workspace) {
        return new ToolExecutionContext(workspace, new CancellationToken(), EventSink.noop(), "test-run", 1);
    }
}

