package dev.minicoder.workspace;

import dev.minicoder.support.TemporaryGitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangeAttributionTest {
    @TempDir Path temp;

    @Test
    void distinguishesPreexistingCreatedModifiedAndOverlap() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        Files.writeString(repo.resolve("tracked.txt"), "clean\n");
        TemporaryGitRepository.git(repo, "add", "tracked.txt");
        TemporaryGitRepository.git(repo, "commit", "-m", "add tracked file");
        Files.writeString(repo.resolve("README.md"), "user change\n");
        Files.writeString(repo.resolve("user.txt"), "user\n");
        Workspace workspace = Workspace.open(repo);
        assertEquals(ChangeAttribution.PREEXISTING, workspace.attribution("README.md"));
        assertEquals(ChangeAttribution.PREEXISTING, workspace.attribution("user.txt"));

        Files.writeString(repo.resolve("README.md"), "agent overlap\n");
        Files.writeString(repo.resolve("tracked.txt"), "agent modified\n");
        Files.writeString(repo.resolve("new.txt"), "agent new\n");
        workspace.recordChanges(List.of("README.md", "tracked.txt", "new.txt"));
        assertEquals(ChangeAttribution.OVERLAPS_PREEXISTING_CHANGE, workspace.attribution("README.md"));
        assertEquals(ChangeAttribution.AGENT_MODIFIED, workspace.attribution("tracked.txt"));
        assertEquals(ChangeAttribution.AGENT_CREATED, workspace.attribution("new.txt"));
    }
}
