package dev.minicoder.tool;

import dev.minicoder.agent.CancellationToken;
import dev.minicoder.observability.EventSink;
import dev.minicoder.workspace.Workspace;

/**
 * 为工具测试装配临时工作区、取消令牌、事件收集器和安全依赖。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class ToolTestContext {
    private ToolTestContext() {}

    public static ToolExecutionContext create(Workspace workspace) {
        return new ToolExecutionContext(workspace, new CancellationToken(), EventSink.noop(), "test-run", 1);
    }
}
