package dev.minicodex.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

public final class WorkspaceGuard {
    private final Path root;

    public WorkspaceGuard(Path root) {
        Objects.requireNonNull(root, "root");
        try {
            this.root = root.toRealPath();
        } catch (IOException e) {
            throw new WorkspaceException("Unable to resolve workspace root: " + root, e);
        }
    }

    public Path root() {
        return root;
    }

    public Path resolveExisting(String relativePath) {
        Path candidate = resolveLexically(relativePath);
        try {
            Path real = candidate.toRealPath();
            ensureInside(real);
            return real;
        } catch (IOException e) {
            throw new WorkspaceException("Path does not exist or cannot be resolved: " + relativePath, e);
        }
    }

    public Path resolveForWrite(String relativePath) {
        Path candidate = resolveLexically(relativePath);
        Path existing = candidate;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new WorkspaceException("No existing parent for path: " + relativePath);
        }
        try {
            Path realParent = existing.toRealPath();
            ensureInside(realParent);
            Path suffix = existing.relativize(candidate);
            Path resolved = realParent.resolve(suffix).normalize();
            ensureInside(resolved);
            return resolved;
        } catch (IOException e) {
            throw new WorkspaceException("Unable to resolve target path: " + relativePath, e);
        }
    }

    public String relativize(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        ensureInside(absolute);
        return root.relativize(absolute).toString().replace('\\', '/');
    }

    private Path resolveLexically(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new WorkspaceException("Path must not be blank");
        }
        Path supplied = Path.of(relativePath);
        if (supplied.isAbsolute()) {
            throw new WorkspaceException("Absolute paths are not allowed: " + relativePath);
        }
        Path candidate = root.resolve(supplied).normalize();
        ensureInside(candidate);
        return candidate;
    }

    private void ensureInside(Path path) {
        if (!path.normalize().startsWith(root)) {
            throw new WorkspaceException("Path escapes workspace: " + path);
        }
    }
}
