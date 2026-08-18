package dev.minicoder.tool.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.minicoder.tool.*;
import dev.minicoder.workspace.WorkspaceException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.StreamSupport;

public final class ListFilesTool implements Tool {
    private static final Set<String> IGNORED = Set.of(".git", "target", "build", "node_modules", ".idea", ".gradle");
    private static final int MAX_SCANNED_ENTRIES = 100_000;
    private final ToolDefinition definition;

    public ListFilesTool() {
        var schema = ToolSchemas.object();
        ToolSchemas.property(schema, "path", "string", true);
        ToolSchemas.property(schema, "maxDepth", "integer", false);
        ToolSchemas.property(schema, "limit", "integer", false);
        definition = new ToolDefinition("list_files", "List files below a workspace directory.", schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input, ToolExecutionContext context) {
        Instant started = Instant.now();
        int maxDepth = Math.clamp(input.path("maxDepth").asInt(4), 0, 20);
        int limit = Math.clamp(input.path("limit").asInt(500), 1, 5_000);
        try {
            Path directory = context.workspace().guard().resolveExisting(input.path("path").asText());
            if (!Files.isDirectory(directory)) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "NOT_DIRECTORY", "Path is not a directory",
                        Duration.between(started, Instant.now()));
            }
            PriorityQueue<String> smallest = new PriorityQueue<>(limit + 1, Comparator.reverseOrder());
            long[] totalEntries = {0};
            try (var stream = Files.walk(directory, maxDepth)) {
                stream.filter(path -> !path.equals(directory))
                        .filter(path -> StreamSupport.stream(path.spliterator(), false)
                                .noneMatch(segment -> IGNORED.contains(segment.toString())))
                        .map(path -> context.workspace().guard().relativize(path) + (Files.isDirectory(path) ? "/" : ""))
                        .limit((long) MAX_SCANNED_ENTRIES + 1)
                        .forEach(entry -> {
                            totalEntries[0]++;
                            if (smallest.size() < limit) {
                                smallest.add(entry);
                            } else if (entry.compareTo(smallest.peek()) < 0) {
                                smallest.poll();
                                smallest.add(entry);
                            }
                        });
            }
            boolean scanLimitReached = totalEntries[0] > MAX_SCANNED_ENTRIES;
            boolean truncated = totalEntries[0] > limit || scanLimitReached;
            java.util.List<String> selected = smallest.stream().sorted().toList();
            var data = JsonNodeFactory.instance.objectNode();
            var entries = data.putArray("entries");
            selected.forEach(entries::add);
            data.put("returnedEntries", selected.size());
            data.put("scannedEntries", Math.min(totalEntries[0], MAX_SCANNED_ENTRIES));
            data.put("scanLimitReached", scanLimitReached);
            data.put("hasMore", truncated);
            return new ToolResult(ToolStatus.OK, "Listed " + selected.size() + " entries", data, truncated,
                    java.util.Optional.empty(), Duration.between(started, Instant.now()));
        } catch (WorkspaceException e) {
            return ToolResult.failure(ToolStatus.POLICY_DENIED, "PATH_DENIED", e.getMessage(),
                    Duration.between(started, Instant.now()));
        } catch (IOException e) {
            return ToolResult.failure(ToolStatus.FAILED, "LIST_FAILED", e.getMessage(),
                    Duration.between(started, Instant.now()));
        }
    }
}
