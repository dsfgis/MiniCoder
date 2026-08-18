package dev.minicoder.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.minicoder.config.RunConfig;
import dev.minicoder.llm.LlmModels.*;
import dev.minicoder.llm.ProviderException;
import dev.minicoder.llm.ScriptedLlmProvider;
import dev.minicoder.observability.InMemoryEventSink;
import dev.minicoder.support.TemporaryGitRepository;
import dev.minicoder.tool.*;
import dev.minicoder.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AgentRuntimeTest {
    @TempDir Path temp;

    @Test
    void executesMultipleCallsInOrderAndCompletesOnlyAfterCurrentRevisionVerification() throws Exception {
        Workspace workspace = Workspace.open(TemporaryGitRepository.create(temp.resolve("repo")));
        List<String> order = new ArrayList<>();
        ToolRegistry registry = new ToolRegistry()
                .register(tool("read_file", order, context -> result("read_file", 0)))
                .register(tool("apply_patch", order, context -> {
                    try {
                        Files.writeString(context.workspace().root().resolve("README.md"), "fixed\n",
                                StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    context.workspace().recordChanges(List.of("README.md"));
                    return result("apply_patch", 0);
                }))
                .register(tool("shell", order, context -> shellResult(0)));
        ScriptedLlmProvider provider = new ScriptedLlmProvider(
                ProviderResponse.tools("r1", List.of(call("c1", "read_file"), call("c2", "apply_patch"))),
                ProviderResponse.tools("r2", List.of(call("c3", "shell"))),
                ProviderResponse.finalText("r3", "Fixed and verified"));

        RunOutcome outcome = runtime(provider, registry).run(config(6, Duration.ofSeconds(10)), workspace,
                new CancellationToken());

        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(List.of("read_file", "apply_patch", "shell"), order);
        assertEquals(1, outcome.workspaceRevision());
        assertEquals(1, outcome.verification().size());
        assertTrue(outcome.gitDiff().contains("fixed"));
        assertEquals("AGENT_MODIFIED", outcome.changeAttribution().get("README.md"));
        assertEquals(List.of("c1", "c2"), provider.requests().get(1).newToolResults().stream()
                .map(ToolExchange::callId).toList());
        assertTrue(outcome.events().stream().anyMatch(event -> event.type().equals("run_stopped")));
    }

    @Test
    void verificationBecomesStaleAfterAnotherEdit() throws Exception {
        Workspace workspace = Workspace.open(TemporaryGitRepository.create(temp.resolve("stale")));
        ToolRegistry registry = new ToolRegistry()
                .register(revisionTool("apply_patch"))
                .register(tool("shell", new ArrayList<>(), context -> shellResult(0)));
        ScriptedLlmProvider provider = new ScriptedLlmProvider(
                ProviderResponse.tools("r1", List.of(call("p1", "apply_patch"))),
                ProviderResponse.tools("r2", List.of(call("v1", "shell"))),
                ProviderResponse.tools("r3", List.of(call("p2", "apply_patch"))),
                ProviderResponse.finalText("r4", "done"));

        RunOutcome outcome = runtime(provider, registry).run(config(6, Duration.ofSeconds(10)), workspace,
                new CancellationToken());
        assertEquals(RunStatus.SUCCEEDED_WITH_WARNINGS, outcome.status());
        assertEquals(2, outcome.workspaceRevision());
        assertEquals(1, outcome.verification().getFirst().workspaceRevision());
    }

    @Test
    void ordinaryGitStatusIsNotVerificationEvidence() throws Exception {
        Workspace workspace = Workspace.open(TemporaryGitRepository.create(temp.resolve("status")));
        ToolRegistry registry = new ToolRegistry()
                .register(revisionTool("apply_patch"))
                .register(tool("shell", new ArrayList<>(), context -> shellResult("git", List.of("status"), 0)));
        ScriptedLlmProvider provider = new ScriptedLlmProvider(
                ProviderResponse.tools("r1", List.of(call("p1", "apply_patch"))),
                ProviderResponse.tools("r2", List.of(call("s1", "shell"))),
                ProviderResponse.finalText("r3", "done"));
        RunOutcome outcome = runtime(provider, registry).run(config(5, Duration.ofSeconds(10)), workspace,
                new CancellationToken());
        assertTrue(outcome.verification().isEmpty());
        assertEquals(RunStatus.SUCCEEDED_WITH_WARNINGS, outcome.status());
    }

    @Test
    void stopsRepeatedEquivalentCallsAsNoProgress() throws Exception {
        Workspace workspace = Workspace.open(TemporaryGitRepository.create(temp.resolve("loop")));
        ToolRegistry registry = new ToolRegistry().register(tool("read_file", new ArrayList<>(),
                context -> result("same", 0)));
        ScriptedLlmProvider provider = new ScriptedLlmProvider(
                ProviderResponse.tools("r1", List.of(call("c1", "read_file"))),
                ProviderResponse.tools("r2", List.of(call("c2", "read_file"))),
                ProviderResponse.tools("r3", List.of(call("c3", "read_file"))),
                ProviderResponse.finalText("r4", "should not happen"));
        RunOutcome outcome = runtime(provider, registry).run(config(8, Duration.ofSeconds(10)), workspace,
                new CancellationToken());
        assertEquals(RunStatus.NO_PROGRESS, outcome.status());
        assertEquals(1, provider.remainingSteps());
    }

    @Test
    void mapsLimitsCancellationAndProviderErrors() throws Exception {
        Workspace workspace = Workspace.open(TemporaryGitRepository.create(temp.resolve("limits")));
        ToolRegistry registry = new ToolRegistry().register(tool("read_file", new ArrayList<>(),
                context -> result("read", 0)));
        ScriptedLlmProvider iterationProvider = new ScriptedLlmProvider(
                ProviderResponse.tools("r1", List.of(call("c1", "read_file"))),
                ProviderResponse.tools("r2", List.of(callWithValue("c2", "read_file", 2))));
        assertEquals(RunStatus.LIMIT_REACHED,
                runtime(iterationProvider, registry).run(config(2, Duration.ofSeconds(10)), workspace,
                        new CancellationToken()).status());

        var deadlineProvider = (dev.minicoder.llm.LlmProvider) (request, token) -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ProviderResponse.finalText("r", "done");
        };
        assertEquals(RunStatus.LIMIT_REACHED,
                new AgentRuntime(deadlineProvider, registry, new CompletionGate(), new InMemoryEventSink())
                        .run(config(1, Duration.ofMillis(1)), workspace,
                        new CancellationToken()).status());

        CancellationToken cancelled = new CancellationToken();
        cancelled.cancel();
        assertEquals(RunStatus.CANCELLED,
                runtime(new ScriptedLlmProvider(ProviderResponse.finalText("r", "done")), registry)
                        .run(config(1, Duration.ofSeconds(2)), workspace, cancelled).status());

        ProviderException failure = new ProviderException(ProviderException.Category.AUTHENTICATION, false, 401,
                "bad credential");
        assertEquals(RunStatus.PROVIDER_ERROR,
                runtime(new ScriptedLlmProvider(failure), registry)
                        .run(config(1, Duration.ofSeconds(2)), workspace, new CancellationToken()).status());
    }

    @Test
    void recordsProviderRetryTelemetryWithRunContext() throws Exception {
        Workspace workspace = Workspace.open(TemporaryGitRepository.create(temp.resolve("retry-events")));
        dev.minicoder.llm.LlmProvider provider = (request, token) -> {
            request.telemetry().retrying(1, "RATE_LIMIT", 125);
            return ProviderResponse.finalText("r1", "done");
        };
        RunOutcome outcome = new AgentRuntime(provider, new ToolRegistry(), new CompletionGate(),
                new InMemoryEventSink()).run(config(2, Duration.ofSeconds(5)), workspace,
                new CancellationToken(), "retry-run");
        var event = outcome.events().stream().filter(item -> item.type().equals("provider_retried"))
                .findFirst().orElseThrow();
        assertEquals("retry-run", event.runId());
        assertEquals(1, event.iteration());
        assertEquals(125L, event.metadata().get("backoffMs"));
    }

    @Test
    void failedVerificationCanBeFollowedByRepairAndSuccessfulFinalRevisionVerification() throws Exception {
        Workspace workspace = Workspace.open(TemporaryGitRepository.create(temp.resolve("repair-after-failure")));
        ToolRegistry registry = new ToolRegistry()
                .register(revisionTool("apply_patch"))
                .register(new Tool() {
                    int calls;
                    private final ToolDefinition definition = new ToolDefinition("shell", "test shell",
                            ToolSchemas.property(ToolSchemas.object(), "value", "integer", false));
                    @Override public ToolDefinition definition() { return definition; }
                    @Override public ToolResult execute(JsonNode input, ToolExecutionContext context) {
                        return shellResult(calls++ == 0 ? 1 : 0);
                    }
                });
        ScriptedLlmProvider provider = new ScriptedLlmProvider(
                ProviderResponse.tools("r1", List.of(call("p1", "apply_patch"))),
                ProviderResponse.tools("r2", List.of(call("v1", "shell"))),
                ProviderResponse.tools("r3", List.of(callWithValue("p2", "apply_patch", 2))),
                ProviderResponse.tools("r4", List.of(callWithValue("v2", "shell", 2))),
                ProviderResponse.finalText("r5", "fixed"));
        RunOutcome outcome = runtime(provider, registry).run(config(7, Duration.ofSeconds(15)), workspace,
                new CancellationToken());
        assertEquals(RunStatus.SUCCEEDED, outcome.status());
        assertEquals(List.of(1, 0), outcome.verification().stream().map(VerificationEvidence::exitCode).toList());
        assertEquals(2, outcome.verification().getLast().workspaceRevision());
    }

    @Test
    void recordsOutputTruncationEvent() throws Exception {
        Workspace workspace = Workspace.open(TemporaryGitRepository.create(temp.resolve("truncated-event")));
        Tool truncated = new Tool() {
            private final ToolDefinition definition = new ToolDefinition("read_file", "read",
                    ToolSchemas.property(ToolSchemas.object(), "value", "integer", false));
            @Override public ToolDefinition definition() { return definition; }
            @Override public ToolResult execute(JsonNode input, ToolExecutionContext context) {
                return new ToolResult(ToolStatus.OK, "bounded", JsonNodeFactory.instance.objectNode(), true,
                        Optional.empty(), Duration.ofMillis(1));
            }
        };
        ScriptedLlmProvider provider = new ScriptedLlmProvider(
                ProviderResponse.tools("r1", List.of(call("c1", "read_file"))),
                ProviderResponse.finalText("r2", "done"));
        RunOutcome outcome = runtime(provider, new ToolRegistry().register(truncated))
                .run(config(3, Duration.ofSeconds(5)), workspace, new CancellationToken());
        assertTrue(outcome.events().stream().anyMatch(event -> event.type().equals("output_truncated")));
    }

    private AgentRuntime runtime(ScriptedLlmProvider provider, ToolRegistry registry) {
        return new AgentRuntime(provider, registry, new CompletionGate(), new InMemoryEventSink());
    }

    private RunConfig config(int iterations, Duration duration) {
        return new RunConfig(temp, "fix", "fake", "scripted", iterations, duration, Optional.empty(), false,
                Optional.empty());
    }

    private Tool revisionTool(String name) {
        return tool(name, new ArrayList<>(), context -> {
            context.workspace().incrementRevision();
            return result(name, 0);
        });
    }

    private Tool tool(String name, List<String> order, ToolAction action) {
        return new Tool() {
            private final ToolDefinition definition = new ToolDefinition(name, "test " + name,
                    ToolSchemas.property(ToolSchemas.object(), "value", "integer", false));
            @Override public ToolDefinition definition() { return definition; }
            @Override public ToolResult execute(JsonNode input, ToolExecutionContext context) {
                order.add(name);
                return action.execute(context);
            }
        };
    }

    private ToolCall call(String id, String name) {
        return callWithValue(id, name, 1);
    }

    private ToolCall callWithValue(String id, String name, int value) {
        return new ToolCall(id, name, JsonNodeFactory.instance.objectNode().put("value", value));
    }

    private ToolResult result(String summary, int exit) {
        return new ToolResult(ToolStatus.OK, summary, JsonNodeFactory.instance.objectNode(), false,
                Optional.empty(), Duration.ofMillis(1));
    }

    private ToolResult shellResult(int exitCode) {
        return shellResult("mvn", List.of("test"), exitCode);
    }

    private ToolResult shellResult(String executable, List<String> args, int exitCode) {
        var data = JsonNodeFactory.instance.objectNode();
        data.put("executable", executable);
        var argsNode = data.putArray("args");
        args.forEach(argsNode::add);
        data.put("exitCode", exitCode);
        data.put("durationMs", 5);
        return new ToolResult(ToolStatus.OK, "command", data, false, Optional.empty(), Duration.ofMillis(5));
    }

    @FunctionalInterface
    private interface ToolAction {
        ToolResult execute(ToolExecutionContext context);
    }
}
