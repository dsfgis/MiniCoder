package dev.minicodex.workspace;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record GitBaseline(
        String head,
        String porcelainStatus,
        String unstagedDiff,
        String stagedDiff,
        Set<String> changedPaths,
        Map<String, String> contentHashes) {
    public GitBaseline {
        head = Objects.requireNonNullElse(head, "");
        porcelainStatus = Objects.requireNonNullElse(porcelainStatus, "");
        unstagedDiff = Objects.requireNonNullElse(unstagedDiff, "");
        stagedDiff = Objects.requireNonNullElse(stagedDiff, "");
        changedPaths = Set.copyOf(Objects.requireNonNullElse(changedPaths, Set.of()));
        contentHashes = Map.copyOf(Objects.requireNonNullElse(contentHashes, Map.of()));
    }
}

