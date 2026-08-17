package dev.minicodex.tool.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicodex.support.TemporaryGitRepository;
import dev.minicodex.tool.ToolStatus;
import dev.minicodex.tool.ToolTestContext;
import dev.minicodex.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ApplyPatchToolTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void appliesUnifiedDiffAndIncrementsRevision() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        Workspace workspace = Workspace.open(repo);
        String patch = "--- a/README.md\n+++ b/README.md\n@@ -1,1 +1,2 @@\n hello\n+world\n";
        var input = JSON.createObjectNode().put("patch", patch);

        var result = new ApplyPatchTool().execute(input, ToolTestContext.create(workspace));

        assertEquals(ToolStatus.OK, result.status(), result.summary());
        assertEquals("hello\nworld\n", Files.readString(repo.resolve("README.md")));
        assertEquals(1, workspace.revision());
    }

    @Test
    void conflictAndEscapeLeaveWorkspaceUnchanged() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        Workspace workspace = Workspace.open(repo);
        ApplyPatchTool tool = new ApplyPatchTool();
        String original = Files.readString(repo.resolve("README.md"));

        String conflict = "--- a/README.md\n+++ b/README.md\n@@ -1,1 +1,1 @@\n-wrong\n+changed\n";
        assertEquals(ToolStatus.CONFLICT, tool.execute(JSON.createObjectNode().put("patch", conflict),
                ToolTestContext.create(workspace)).status());
        assertEquals(original, Files.readString(repo.resolve("README.md")));

        String escape = "--- a/../escape.txt\n+++ b/../escape.txt\n@@ -0,0 +1,1 @@\n+bad\n";
        assertEquals(ToolStatus.POLICY_DENIED, tool.execute(JSON.createObjectNode().put("patch", escape),
                ToolTestContext.create(workspace)).status());
        assertFalse(Files.exists(temp.resolve("escape.txt")));
    }
}

