package dev.minicoder.workspace;

import dev.minicoder.support.TemporaryGitRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PathResolverTest {
    @TempDir Path temp;

    @Test
    void rejectsJunctionThatResolvesOutsideWorkspace() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("windows"));
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        Path outside = Files.createDirectories(temp.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Path link = repo.resolve("outside-link");
        Process process = new ProcessBuilder("cmd", "/d", "/c", "mklink", "/J",
                link.toString(), outside.toString()).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        Assumptions.assumeTrue(process.waitFor() == 0, "junction creation is unavailable");

        try {
            Workspace workspace = Workspace.open(repo);
            assertThrows(WorkspaceException.class,
                    () -> workspace.guard().resolveExisting("outside-link/secret.txt"));
            assertThrows(WorkspaceException.class,
                    () -> workspace.guard().resolveForWrite("outside-link/new.txt"));
        } finally {
            Files.deleteIfExists(link);
        }
    }
}
