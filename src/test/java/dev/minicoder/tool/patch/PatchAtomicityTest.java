package dev.minicoder.tool.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicoder.support.TemporaryGitRepository;
import dev.minicoder.tool.ToolStatus;
import dev.minicoder.tool.ToolTestContext;
import dev.minicoder.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PatchAtomicityTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void preflightsEveryFileBeforeWritingAnyFile() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        Files.writeString(repo.resolve("second.txt"), "second\n");
        Workspace workspace = Workspace.open(repo);
        String patch = """
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,1 @@
                -hello
                +changed
                --- a/second.txt
                +++ b/second.txt
                @@ -1,1 +1,1 @@
                -wrong-context
                +changed
                """;
        var result = new ApplyPatchTool().execute(JSON.createObjectNode().put("patch", patch),
                ToolTestContext.create(workspace));
        assertEquals(ToolStatus.CONFLICT, result.status());
        assertEquals("hello\n", Files.readString(repo.resolve("README.md")));
        assertEquals("second\n", Files.readString(repo.resolve("second.txt")));
        assertEquals(0, workspace.revision());
    }

    @Test
    void rollsBackAlreadyCommittedFileWhenLaterCommitFails() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("rollback"));
        Files.writeString(repo.resolve("second.txt"), "second\n");
        Workspace workspace = Workspace.open(repo);
        String patch = """
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,1 @@
                -hello
                +first changed
                --- a/second.txt
                +++ b/second.txt
                @@ -1,1 +1,1 @@
                -second
                +second changed
                """;
        ApplyPatchTool tool = new ApplyPatchTool((target, committed) -> {
            if (committed == 1) throw new java.io.IOException("injected second-file commit failure");
        });
        var result = tool.execute(JSON.createObjectNode().put("patch", patch), ToolTestContext.create(workspace));
        assertEquals(ToolStatus.FAILED, result.status());
        assertEquals("hello\n", Files.readString(repo.resolve("README.md")));
        assertEquals("second\n", Files.readString(repo.resolve("second.txt")));
        assertEquals(0, workspace.revision());
    }

    @Test
    void appliesTwoValidFilesWithOneRevisionIncrement() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("multi-success"));
        Files.writeString(repo.resolve("second.txt"), "second\n");
        Workspace workspace = Workspace.open(repo);
        String patch = """
                --- a/README.md
                +++ b/README.md
                @@ -1,1 +1,1 @@
                -hello
                +first changed
                --- a/second.txt
                +++ b/second.txt
                @@ -1,1 +1,1 @@
                -second
                +second changed
                """;
        var result = new ApplyPatchTool().execute(JSON.createObjectNode().put("patch", patch),
                ToolTestContext.create(workspace));
        assertEquals(ToolStatus.OK, result.status());
        assertEquals("first changed\n", Files.readString(repo.resolve("README.md")));
        assertEquals("second changed\n", Files.readString(repo.resolve("second.txt")));
        assertEquals(1, workspace.revision());
    }
}
