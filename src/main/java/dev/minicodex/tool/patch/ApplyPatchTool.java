package dev.minicodex.tool.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.minicodex.tool.*;
import dev.minicodex.workspace.WorkspaceException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.CharacterCodingException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApplyPatchTool implements Tool {
    private static final Pattern HUNK = Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*");
    private static final int MAX_PATCH_BYTES = 2 * 1024 * 1024;
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_TOTAL_BYTES = 16 * 1024 * 1024;
    private static final int MAX_FILES = 100;
    private final ToolDefinition definition;
    private final CommitFaultInjector faultInjector;

    public ApplyPatchTool() {
        this((path, committedFiles) -> { });
    }

    public ApplyPatchTool(CommitFaultInjector faultInjector) {
        this.faultInjector = Objects.requireNonNull(faultInjector);
        var schema = ToolSchemas.object();
        ToolSchemas.property(schema, "patch", "string", true);
        definition = new ToolDefinition("apply_patch", "Apply a preflighted unified diff inside the workspace.", schema);
    }

    @Override public ToolDefinition definition() { return definition; }

    @Override public boolean mayModifyWorkspace() { return true; }

    @Override
    public ToolResult execute(JsonNode input, ToolExecutionContext context) {
        Instant started = Instant.now();
        try {
            String patchText = input.path("patch").asText();
            if (patchText.getBytes(StandardCharsets.UTF_8).length > MAX_PATCH_BYTES) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "PATCH_TOO_LARGE",
                        "Patch exceeds the 2 MiB limit", Duration.between(started, Instant.now()));
            }
            List<FilePatch> patches = parse(patchText);
            if (patches.isEmpty()) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "EMPTY_PATCH", "Patch contains no file changes",
                        Duration.between(started, Instant.now()));
            }
            Map<Path, byte[]> before = new LinkedHashMap<>();
            Map<Path, byte[]> after = new LinkedHashMap<>();
            List<String> affected = new ArrayList<>();
            if (patches.size() > MAX_FILES) {
                return ToolResult.failure(ToolStatus.INVALID_INPUT, "TOO_MANY_FILES",
                        "Patch exceeds the 100-file limit", Duration.between(started, Instant.now()));
            }
            long totalBytes = 0;
            Set<Path> uniqueTargets = new HashSet<>();
            for (FilePatch patch : patches) {
                Path target = context.workspace().guard().resolveForWrite(patch.path());
                if (!uniqueTargets.add(target)) {
                    throw new IllegalArgumentException("Patch contains duplicate target: " + patch.path());
                }
                byte[] original = null;
                if (Files.exists(target)) {
                    long size = Files.size(target);
                    if (size > MAX_FILE_BYTES) throw new IllegalArgumentException("Target file exceeds 4 MiB: " + patch.path());
                    try (var stream = Files.newInputStream(target)) {
                        original = stream.readNBytes(MAX_FILE_BYTES + 1);
                    }
                    if (original.length > MAX_FILE_BYTES) {
                        throw new IllegalArgumentException("Target file exceeds 4 MiB: " + patch.path());
                    }
                }
                String source = original == null ? "" : decodeUtf8(original, patch.path());
                String updated = applyHunks(source, patch.hunks());
                byte[] updatedBytes = updated.getBytes(StandardCharsets.UTF_8);
                if (updatedBytes.length > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("Patched file exceeds 4 MiB: " + patch.path());
                }
                totalBytes += (original == null ? 0 : original.length) + updatedBytes.length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("Patch working set exceeds 16 MiB");
                }
                before.put(target, original);
                after.put(target, updatedBytes);
                affected.add(context.workspace().guard().relativize(target));
            }
            commit(after, before, faultInjector);
            long revision = context.workspace().recordChanges(affected);
            var data = JsonNodeFactory.instance.objectNode();
            var files = data.putArray("files");
            affected.forEach(files::add);
            data.put("workspaceRevision", revision);
            return ToolResult.ok("Applied patch to " + affected.size() + " file(s)", data,
                    Duration.between(started, Instant.now()));
        } catch (PatchConflictException e) {
            return ToolResult.failure(ToolStatus.CONFLICT, "PATCH_CONFLICT", e.getMessage(),
                    Duration.between(started, Instant.now()));
        } catch (WorkspaceException e) {
            return ToolResult.failure(ToolStatus.POLICY_DENIED, "PATH_DENIED", e.getMessage(),
                    Duration.between(started, Instant.now()));
        } catch (RollbackException e) {
            return ToolResult.failure(ToolStatus.WORKSPACE_INCONSISTENT, "ROLLBACK_FAILED", e.getMessage(),
                    Duration.between(started, Instant.now()));
        } catch (IOException | IllegalArgumentException e) {
            return ToolResult.failure(ToolStatus.FAILED, "PATCH_FAILED", e.getMessage(),
                    Duration.between(started, Instant.now()));
        }
    }

    private static List<FilePatch> parse(String patch) {
        if (patch == null || patch.isBlank()) throw new IllegalArgumentException("Patch must not be blank");
        List<String> lines = Arrays.asList(patch.replace("\r\n", "\n").split("\n", -1));
        List<FilePatch> files = new ArrayList<>();
        int index = 0;
        while (index < lines.size()) {
            if (!lines.get(index).startsWith("--- ")) { index++; continue; }
            String oldPath = pathToken(lines.get(index++).substring(4));
            if (index >= lines.size() || !lines.get(index).startsWith("+++ ")) {
                throw new IllegalArgumentException("Missing +++ file header");
            }
            String newPath = pathToken(lines.get(index++).substring(4));
            if ("/dev/null".equals(newPath)) {
                throw new IllegalArgumentException("File deletion is not supported by V0.1 apply_patch");
            }
            String path = stripPrefix(newPath);
            if (path.isBlank() || Path.of(path).isAbsolute()) {
                throw new IllegalArgumentException("Invalid patch path: " + path);
            }
            List<Hunk> hunks = new ArrayList<>();
            while (index < lines.size() && !lines.get(index).startsWith("--- ")) {
                Matcher matcher = HUNK.matcher(lines.get(index));
                if (!matcher.matches()) { index++; continue; }
                int oldStart = Integer.parseInt(matcher.group(1));
                index++;
                List<String> hunkLines = new ArrayList<>();
                while (index < lines.size() && !lines.get(index).startsWith("@@ ")
                        && !lines.get(index).startsWith("--- ")) {
                    String line = lines.get(index++);
                    if (line.startsWith("\\ No newline")) continue;
                    if (line.isEmpty()) break;
                    char prefix = line.charAt(0);
                    if (prefix != ' ' && prefix != '+' && prefix != '-') break;
                    hunkLines.add(line);
                }
                hunks.add(new Hunk(oldStart, hunkLines));
            }
            if (hunks.isEmpty()) throw new IllegalArgumentException("Patch has no hunks for " + path);
            files.add(new FilePatch(path, hunks));
        }
        return files;
    }

    private static String applyHunks(String source, List<Hunk> hunks) {
        boolean crlf = source.contains("\r\n");
        String normalized = source.replace("\r\n", "\n");
        boolean trailingNewline = normalized.endsWith("\n");
        List<String> original = new ArrayList<>(Arrays.asList(normalized.split("\n", -1)));
        if (trailingNewline && !original.isEmpty()) original.removeLast();
        List<String> output = new ArrayList<>();
        int cursor = 0;
        for (Hunk hunk : hunks) {
            int target = Math.max(0, hunk.oldStart() - 1);
            if (target < cursor || target > original.size()) {
                throw new PatchConflictException("Hunk position is invalid at old line " + hunk.oldStart());
            }
            output.addAll(original.subList(cursor, target));
            cursor = target;
            for (String line : hunk.lines()) {
                char prefix = line.charAt(0);
                String content = line.substring(1);
                if (prefix == ' ' || prefix == '-') {
                    if (cursor >= original.size() || !original.get(cursor).equals(content)) {
                        throw new PatchConflictException("Patch context mismatch near old line " + (cursor + 1));
                    }
                    if (prefix == ' ') output.add(content);
                    cursor++;
                } else if (prefix == '+') {
                    output.add(content);
                }
            }
        }
        output.addAll(original.subList(cursor, original.size()));
        String separator = crlf ? "\r\n" : "\n";
        String joined = String.join(separator, output);
        if (trailingNewline || source.isEmpty()) joined += separator;
        return joined;
    }

    private static void commit(Map<Path, byte[]> after, Map<Path, byte[]> before,
                               CommitFaultInjector faultInjector) throws IOException {
        Map<Path, Path> staged = new LinkedHashMap<>();
        List<Path> committed = new ArrayList<>();
        try {
            for (Map.Entry<Path, byte[]> entry : after.entrySet()) {
                Path parent = entry.getKey().getParent();
                Files.createDirectories(parent);
                Path temp = Files.createTempFile(parent, ".mini-coder-", ".tmp");
                Files.write(temp, entry.getValue());
                staged.put(entry.getKey(), temp);
            }
            for (Map.Entry<Path, Path> entry : staged.entrySet()) {
                faultInjector.beforeMove(entry.getKey(), committed.size());
                moveReplace(entry.getValue(), entry.getKey());
                committed.add(entry.getKey());
            }
        } catch (IOException failure) {
            List<String> rollbackFailures = new ArrayList<>();
            for (Path path : committed.reversed()) {
                try {
                    byte[] original = before.get(path);
                    if (original == null) Files.deleteIfExists(path);
                    else Files.write(path, original);
                } catch (IOException rollback) {
                    rollbackFailures.add(path.toString());
                }
            }
            if (!rollbackFailures.isEmpty()) {
                throw new RollbackException("Rollback failed for: " + String.join(", ", rollbackFailures), failure);
            }
            throw failure;
        } finally {
            for (Path temp : staged.values()) Files.deleteIfExists(temp);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String decodeUtf8(byte[] bytes, String path) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Target is not valid UTF-8: " + path, e);
        }
    }

    private static String pathToken(String value) {
        int tab = value.indexOf('\t');
        return (tab >= 0 ? value.substring(0, tab) : value).strip();
    }

    private static String stripPrefix(String path) {
        return path.startsWith("a/") || path.startsWith("b/") ? path.substring(2) : path;
    }

    private record FilePatch(String path, List<Hunk> hunks) {}
    private record Hunk(int oldStart, List<String> lines) {}
    @FunctionalInterface
    public interface CommitFaultInjector {
        void beforeMove(Path target, int committedFiles) throws IOException;
    }
    private static final class PatchConflictException extends RuntimeException {
        private PatchConflictException(String message) { super(message); }
    }
    private static final class RollbackException extends IOException {
        private RollbackException(String message, Throwable cause) { super(message, cause); }
    }
}
