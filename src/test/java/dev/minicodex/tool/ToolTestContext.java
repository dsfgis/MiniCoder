package dev.minicodex.tool;

import dev.minicodex.agent.CancellationToken;
import dev.minicodex.observability.EventSink;
import dev.minicodex.workspace.Workspace;

public final class ToolTestContext {
    private ToolTestContext() {}

    public static ToolExecutionContext create(Workspace workspace) {
        return new ToolExecutionContext(workspace, new CancellationToken(), EventSink.noop(), "test-run", 1);
    }
}

