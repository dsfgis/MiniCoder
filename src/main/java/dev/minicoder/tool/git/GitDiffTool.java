package dev.minicoder.tool.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.minicoder.tool.*;
import dev.minicoder.workspace.Workspace;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 只读汇总 Git 状态、差异和运行前基线归属，绝不执行仓库写操作。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class GitDiffTool implements Tool {
    private final ToolDefinition definition;

    public GitDiffTool() {
        var schema = ToolSchemas.object();
        ToolSchemas.property(schema, "maxBytes", "integer", false);
        definition = new ToolDefinition("git_diff", "Read Git status, diff, statistics, and baseline attribution.", schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input, ToolExecutionContext context) {
        Instant started = Instant.now();
        int maxBytes = Math.clamp(input.path("maxBytes").asInt(512 * 1024), 1024, 2 * 1024 * 1024);
        Workspace.CommandResult status = Workspace.git(context.workspace().root(), maxBytes + 1,
                "status", "--porcelain=v1", "-z", "--untracked-files=all");
        List<Workspace.StatusEntry> entries = Workspace.statusEntries(status.stdout());
        Workspace.CommandResult diff = Workspace.git(context.workspace().root(), maxBytes + 1,
                "diff", "--no-ext-diff", "--binary");
        Workspace.CommandResult staged = Workspace.git(context.workspace().root(), maxBytes + 1,
                "diff", "--cached", "--no-ext-diff", "--binary");
        Workspace.CommandResult stat = Workspace.git(context.workspace().root(), 256 * 1024,
                "diff", "--stat", "--no-ext-diff");

        ByteArrayOutputStream combinedDiff = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        ByteArrayOutputStream combinedStat = new ByteArrayOutputStream(16 * 1024);
        boolean truncated = append(combinedDiff, diff.stdout(), maxBytes) || diff.truncated() || status.truncated()
                || staged.truncated() || stat.truncated();
        append(combinedStat, stat.stdout(), 256 * 1024);
        long originalDiffBytes = diff.stdoutBytes();
        int untrackedProcessed = 0;
        Map<String, String> attribution = new TreeMap<>();
        for (Workspace.StatusEntry entry : entries) {
            attribution.put(entry.path(), context.workspace().attribution(entry.path()).name());
            if (!entry.status().equals("??")) continue;
            if (untrackedProcessed++ >= 20) {
                truncated = true;
                continue;
            }
            Workspace.CommandResult untracked = Workspace.git(context.workspace().root(), maxBytes + 1,
                    "diff", "--no-index", "--binary", "--", "NUL", entry.path());
            originalDiffBytes += untracked.stdoutBytes();
            truncated |= append(combinedDiff, untracked.stdout(), maxBytes) || untracked.truncated();
            Workspace.CommandResult untrackedStat = Workspace.git(context.workspace().root(), 64 * 1024,
                    "diff", "--no-index", "--stat", "--", "NUL", entry.path());
            truncated |= append(combinedStat, untrackedStat.stdout(), 256 * 1024) || untrackedStat.truncated();
        }
        var data = JsonNodeFactory.instance.objectNode();
        data.put("status", Workspace.renderStatus(entries));
        data.put("diff", combinedDiff.toString(StandardCharsets.UTF_8));
        data.put("stagedDiff", staged.stdout());
        data.put("stat", combinedStat.toString(StandardCharsets.UTF_8));
        data.put("originalDiffBytes", originalDiffBytes);
        data.put("gitOutputTruncated", diff.truncated() || status.truncated() || staged.truncated() || stat.truncated());
        var attributionNode = JsonNodeFactory.instance.objectNode();
        attribution.forEach(attributionNode::put);
        data.set("attribution", attributionNode);
        data.put("workspaceRevision", context.workspace().revision());
        return new ToolResult(ToolStatus.OK, "Git diff contains " + attribution.size() + " changed path(s)",
                data, truncated, Optional.empty(), Duration.between(started, Instant.now()));
    }

    private static boolean append(ByteArrayOutputStream target, String value, int limit) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int remaining = Math.max(0, limit - target.size());
        target.write(bytes, 0, Math.min(remaining, bytes.length));
        return bytes.length > remaining;
    }
}
