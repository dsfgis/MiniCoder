package dev.minicodex.tool.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.minicodex.tool.*;
import dev.minicodex.workspace.WorkspaceException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class ReadFileTool implements Tool {
    private static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_LINES = 2_000;
    private final ToolDefinition definition;

    public ReadFileTool() {
        var schema = ToolSchemas.object();
        ToolSchemas.property(schema, "path", "string", true);
        ToolSchemas.property(schema, "startLine", "integer", false);
        ToolSchemas.property(schema, "endLine", "integer", false);
        definition = new ToolDefinition("read_file", "Read a bounded UTF-8 text file with line numbers.", schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override
    public ToolResult execute(JsonNode input, ToolExecutionContext context) {
        Instant started = Instant.now();
        int startLine = Math.max(1, input.path("startLine").asInt(1));
        int requestedEnd = Math.max(startLine, input.path("endLine").asInt(startLine + 199));
        int endLine = Math.min(requestedEnd, startLine + MAX_LINES - 1);
        try {
            Path file = context.workspace().guard().resolveExisting(input.path("path").asText());
            if (!Files.isRegularFile(file)) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "NOT_FILE", "Path is not a regular file",
                        Duration.between(started, Instant.now()));
            }
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "FILE_TOO_LARGE",
                        "File exceeds the 4 MiB read limit", Duration.between(started, Instant.now()));
            }
            byte[] bytes;
            try (var stream = Files.newInputStream(file)) {
                bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
            }
            if (bytes.length > MAX_FILE_BYTES) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "FILE_TOO_LARGE",
                        "File exceeds the 4 MiB read limit", Duration.between(started, Instant.now()));
            }
            if (containsNul(bytes)) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "BINARY_FILE", "Binary files are not supported",
                        Duration.between(started, Instant.now()));
            }
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "INVALID_UTF8", "File is not valid UTF-8",
                        Duration.between(started, Instant.now()));
            }
            String[] lines = text.split("\\R", -1);
            if (startLine > lines.length) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "LINE_RANGE", "startLine exceeds file length",
                        Duration.between(started, Instant.now()));
            }
            StringBuilder selected = new StringBuilder();
            int selectedBytes = 0;
            int last = Math.min(endLine, lines.length);
            boolean byteTruncated = false;
            for (int number = startLine; number <= last; number++) {
                String rendered = "%6d | %s%n".formatted(number, lines[number - 1]);
                int renderedBytes = rendered.getBytes(StandardCharsets.UTF_8).length;
                if (selectedBytes + renderedBytes > MAX_BYTES) {
                    byteTruncated = true;
                    break;
                }
                selected.append(rendered);
                selectedBytes += renderedBytes;
            }
            boolean truncated = byteTruncated || requestedEnd > endLine || last < lines.length && last < requestedEnd;
            var data = JsonNodeFactory.instance.objectNode();
            data.put("path", context.workspace().guard().relativize(file));
            data.put("content", selected.toString());
            data.put("startLine", startLine);
            data.put("endLine", last);
            data.put("totalLines", lines.length);
            data.put("originalBytes", size);
            return new ToolResult(ToolStatus.OK, "Read lines " + startLine + "-" + last, data, truncated,
                    Optional.empty(), Duration.between(started, Instant.now()));
        } catch (WorkspaceException e) {
            return ToolResult.failure(ToolStatus.POLICY_DENIED, "PATH_DENIED", e.getMessage(),
                    Duration.between(started, Instant.now()));
        } catch (IOException e) {
            return ToolResult.failure(ToolStatus.FAILED, "READ_FAILED", e.getMessage(),
                    Duration.between(started, Instant.now()));
        }
    }

    private static boolean containsNul(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return true;
        return false;
    }
}
