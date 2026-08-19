package dev.minicoder.workspace;

import dev.minicoder.support.TemporaryGitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证工作区打开、Git 仓库校验、中文空格路径、CRLF 和 baseline 捕获。
 *
 * @author Self David (dsfgis@gmail.com)
 */
class WorkspaceTest {
    @TempDir Path temp;

    @Test
    void rejectsNonGitDirectory() {
        assertThrows(WorkspaceException.class, () -> Workspace.open(temp));
    }

    @Test
    void rejectsMissingPathAndRegularFile() throws Exception {
        assertThrows(WorkspaceException.class, () -> Workspace.open(temp.resolve("missing")));
        Path file = Files.writeString(temp.resolve("file.txt"), "not a workspace");
        assertThrows(WorkspaceException.class, () -> Workspace.open(file));
    }

    @Test
    void capturesPreexistingChangesAndRevision() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo 中文"));
        Files.writeString(repo.resolve("README.md"), "changed\r\n");

        Workspace workspace = Workspace.open(repo);

        assertTrue(workspace.baseline().changedPaths().contains("README.md"));
        assertEquals(ChangeAttribution.PREEXISTING,
                workspace.attribution("README.md"));
        assertEquals(1, workspace.recordChanges(java.util.List.of("README.md")));
        assertEquals(ChangeAttribution.OVERLAPS_PREEXISTING_CHANGE,
                workspace.attribution("README.md"));
    }

    @Test
    void rejectsAbsoluteAndTraversalPaths() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        Workspace workspace = Workspace.open(repo);

        assertThrows(WorkspaceException.class,
                () -> workspace.guard().resolveExisting(repo.resolve("README.md").toString()));
        assertThrows(WorkspaceException.class,
                () -> workspace.guard().resolveForWrite("../escape.txt"));
    }

    @Test
    void parsesNulDelimitedStatusWithoutLosingSpacesOrChinese() {
        var entries = Workspace.statusEntries("?? dir with space/中文.txt\0R  new name.txt\0old name.txt\0");
        assertEquals("dir with space/中文.txt", entries.get(0).path());
        assertEquals("new name.txt", entries.get(1).path());
        assertEquals("old name.txt", entries.get(1).originalPath());
    }
}
