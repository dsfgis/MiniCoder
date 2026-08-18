package dev.minicoder.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class Workspace {
    private static final int MAX_GIT_OUTPUT_BYTES = 8 * 1024 * 1024;
    private final WorkspaceGuard guard;
    private final GitBaseline baseline;
    private final AtomicLong revision = new AtomicLong();
    private final Set<String> agentChangedPaths = ConcurrentHashMap.newKeySet();

    private Workspace(WorkspaceGuard guard, GitBaseline baseline) {
        this.guard = guard;
        this.baseline = baseline;
    }

    public static Workspace open(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            throw new WorkspaceException("Workspace is not a directory: " + root);
        }
        WorkspaceGuard guard = new WorkspaceGuard(root);
        CommandResult inside = git(guard.root(), "rev-parse", "--is-inside-work-tree");
        if (inside.exitCode() != 0 || !inside.stdout().strip().equals("true")) {
            throw new WorkspaceException("Workspace is not a Git repository: " + root);
        }
        String head = git(guard.root(), "rev-parse", "--verify", "HEAD").stdout().strip();
        CommandResult statusResult = git(guard.root(), "status", "--porcelain=v1", "-z", "--untracked-files=all");
        if (statusResult.truncated()) {
            throw new WorkspaceException("Git status exceeds the workspace baseline limit");
        }
        String status = statusResult.stdout();
        String diff = git(guard.root(), "diff", "--no-ext-diff", "--binary").stdout();
        String staged = git(guard.root(), "diff", "--cached", "--no-ext-diff", "--binary").stdout();
        Set<String> changed = new HashSet<>();
        statusEntries(status).forEach(entry -> changed.add(entry.path()));
        Map<String, String> hashes = new HashMap<>();
        for (String relative : changed) {
            try {
                Path path = guard.resolveExisting(relative);
                if (Files.isRegularFile(path)) {
                    hashes.put(relative, sha256(path));
                }
            } catch (RuntimeException | IOException ignored) {
                hashes.put(relative, "<missing>");
            }
        }
        return new Workspace(guard, new GitBaseline(head, status, diff, staged, changed, hashes));
    }

    public WorkspaceGuard guard() {
        return guard;
    }

    public Path root() {
        return guard.root();
    }

    public GitBaseline baseline() {
        return baseline;
    }

    public long revision() {
        return revision.get();
    }

    public long incrementRevision() {
        return revision.incrementAndGet();
    }

    public long recordChanges(Iterable<String> relativePaths) {
        for (String path : relativePaths) agentChangedPaths.add(path.replace('\\', '/'));
        return revision.incrementAndGet();
    }

    public ChangeAttribution attribution(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        boolean preexisting = baseline.changedPaths().contains(normalized);
        boolean agentChanged = agentChangedPaths.contains(normalized);
        if (preexisting && agentChanged) {
            return ChangeAttribution.OVERLAPS_PREEXISTING_CHANGE;
        }
        if (preexisting) return ChangeAttribution.PREEXISTING;
        if (!agentChanged) return ChangeAttribution.UNKNOWN;
        return git(root(), "ls-files", "--error-unmatch", "--", normalized).exitCode() == 0
                ? ChangeAttribution.AGENT_MODIFIED : ChangeAttribution.AGENT_CREATED;
    }

    public boolean wasChangedByAgent(String relativePath) {
        return agentChangedPaths.contains(relativePath.replace('\\', '/'));
    }

    public Map<String, String> snapshotChangedContent() {
        CommandResult status = git(root(), "status", "--porcelain=v1", "-z", "--untracked-files=all");
        if (status.truncated()) throw new WorkspaceException("Git status exceeds the change-detection limit");
        Map<String, String> snapshot = new HashMap<>();
        for (StatusEntry entry : statusEntries(status.stdout())) {
            try {
                Path path = guard.resolveForWrite(entry.path());
                snapshot.put(entry.path(), Files.isRegularFile(path) ? sha256(path) : "<missing>");
            } catch (IOException | RuntimeException e) {
                snapshot.put(entry.path(), "<unavailable>");
            }
        }
        return Map.copyOf(snapshot);
    }

    public long recordExternalChanges(Map<String, String> before) {
        Map<String, String> after = snapshotChangedContent();
        Set<String> paths = new HashSet<>(before.keySet());
        paths.addAll(after.keySet());
        java.util.List<String> changed = paths.stream()
                .filter(path -> !Objects.equals(before.get(path), after.get(path)))
                .sorted().toList();
        return changed.isEmpty() ? revision() : recordChanges(changed);
    }

    public static CommandResult git(Path root, String... args) {
        return git(root, MAX_GIT_OUTPUT_BYTES, args);
    }

    public static CommandResult git(Path root, int maxOutputBytes, String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        try {
            Process process = new ProcessBuilder(command).directory(root.toFile()).start();
            BoundedCapture out = new BoundedCapture(maxOutputBytes);
            BoundedCapture err = new BoundedCapture(Math.min(maxOutputBytes, 1024 * 1024));
            Thread stdout = Thread.startVirtualThread(() -> transfer(process.getInputStream(), out));
            Thread stderr = Thread.startVirtualThread(() -> transfer(process.getErrorStream(), err));
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new WorkspaceException("Git command timed out");
            }
            int exit = process.exitValue();
            stdout.join();
            stderr.join();
            return new CommandResult(exit, out.text(), err.text(), out.truncated() || err.truncated(),
                    out.totalBytes(), err.totalBytes());
        } catch (IOException e) {
            throw new WorkspaceException("Unable to execute Git", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkspaceException("Git command interrupted", e);
        }
    }

    private static void transfer(InputStream input, BoundedCapture output) {
        try (input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, read);
        } catch (IOException ignored) {
        }
    }

    public static java.util.List<StatusEntry> statusEntries(String porcelainZ) {
        java.util.List<StatusEntry> entries = new java.util.ArrayList<>();
        java.util.List<String> records = new java.util.ArrayList<>();
        int start = 0;
        for (int index = 0; index <= porcelainZ.length(); index++) {
            if (index == porcelainZ.length() || porcelainZ.charAt(index) == '\0') {
                records.add(porcelainZ.substring(start, index));
                start = index + 1;
            }
        }
        for (int index = 0; index < records.size(); index++) {
            String record = records.get(index);
            if (record.length() < 4) continue;
            String status = record.substring(0, 2);
            String path = record.substring(3).replace('\\', '/');
            String original = "";
            if ((status.indexOf('R') >= 0 || status.indexOf('C') >= 0) && index + 1 < records.size()) {
                original = records.get(++index).replace('\\', '/');
            }
            entries.add(new StatusEntry(status, path, original));
        }
        return java.util.List.copyOf(entries);
    }

    public static String renderStatus(java.util.List<StatusEntry> entries) {
        StringBuilder output = new StringBuilder();
        for (StatusEntry entry : entries) {
            output.append(entry.status()).append(' ').append(entry.path());
            if (!entry.originalPath().isBlank()) output.append(" <- ").append(entry.originalPath());
            output.append('\n');
        }
        return output.toString();
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record CommandResult(int exitCode, String stdout, String stderr, boolean truncated,
                                long stdoutBytes, long stderrBytes) {}

    public record StatusEntry(String status, String path, String originalPath) {}

    private static final class BoundedCapture {
        private final int limit;
        private final ByteArrayOutputStream kept = new ByteArrayOutputStream();
        private long totalBytes;

        private BoundedCapture(int limit) { this.limit = Math.max(1, limit); }
        private void write(byte[] bytes, int length) {
            totalBytes += length;
            int remaining = limit - kept.size();
            if (remaining > 0) kept.write(bytes, 0, Math.min(remaining, length));
        }
        private String text() { return kept.toString(StandardCharsets.UTF_8); }
        private boolean truncated() { return totalBytes > limit; }
        private long totalBytes() { return totalBytes; }
    }
}
