package dev.minicoder.tool.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.minicoder.agent.CancellationToken;
import dev.minicoder.tool.*;
import dev.minicoder.tool.shell.*;
import dev.minicoder.workspace.WorkspaceException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SearchCodeTool implements Tool {
    private final ProcessRunner processRunner;
    private final ToolDefinition definition;

    public SearchCodeTool(ProcessRunner processRunner) {
        this.processRunner = processRunner;
        var schema = ToolSchemas.object();
        ToolSchemas.property(schema, "query", "string", true);
        ToolSchemas.property(schema, "path", "string", false);
        ToolSchemas.property(schema, "glob", "string", false);
        ToolSchemas.property(schema, "limit", "integer", false);
        definition = new ToolDefinition("search_code", "Search workspace text with ripgrep.", schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input, ToolExecutionContext context) {
        Instant started = Instant.now();
        int limit = Math.clamp(input.path("limit").asInt(200), 1, 2_000);
        try {
            if (input.path("query").asText().isBlank()) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "EMPTY_QUERY", "Search query must not be blank",
                        Duration.between(started, Instant.now()));
            }
            String relative = input.path("path").asText(".");
            context.workspace().guard().resolveExisting(relative);
            List<String> args = new ArrayList<>(List.of("--line-number", "--column", "--color", "never",
                    "--max-count", Integer.toString(limit), "--glob", "!{.git,target,build,node_modules}/**"));
            if (input.hasNonNull("glob") && !input.path("glob").asText().isBlank()) {
                args.add("--glob");
                args.add(input.path("glob").asText());
            }
            args.add("--");
            args.add(input.path("query").asText());
            args.add(relative);
            ProcessResult result = processRunner.run(new CommandSpec("rg", args, Duration.ofSeconds(30),
                    256 * 1024, ShellMode.NONE), context.workspace().root(), context.cancellationToken());
            ToolStatus status = result.exitCode() == 0 ? ToolStatus.OK
                    : result.exitCode() == 1 ? ToolStatus.NO_MATCH : ToolStatus.FAILED;
            List<String> outputLines = result.stdout().lines().limit((long) limit + 1).toList();
            boolean lineTruncated = outputLines.size() > limit;
            String matches = String.join(System.lineSeparator(), outputLines.stream().limit(limit).toList());
            if (!matches.isEmpty()) matches += System.lineSeparator();
            var data = JsonNodeFactory.instance.objectNode();
            data.put("matches", matches);
            data.put("matchesReturned", Math.min(outputLines.size(), limit));
            data.put("exitCode", result.exitCode());
            data.put("stderr", result.stderr());
            data.put("stdoutBytes", result.stdoutBytes());
            return new ToolResult(status, status == ToolStatus.NO_MATCH ? "No matches" : "Search completed",
                    data, result.truncated() || lineTruncated, status == ToolStatus.FAILED
                    ? Optional.of(new ToolResult.ToolError("RG_FAILED", result.stderr())) : Optional.empty(),
                    Duration.between(started, Instant.now()));
        } catch (WorkspaceException e) {
            return ToolResult.failure(ToolStatus.POLICY_DENIED, "PATH_DENIED", e.getMessage(),
                    Duration.between(started, Instant.now()));
        } catch (IOException e) {
            return ToolResult.failure(ToolStatus.FAILED, "RG_UNAVAILABLE", e.getMessage(),
                    Duration.between(started, Instant.now()));
        }
    }
}
