package dev.minicodex.tool.git;

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

class GitDiffToolTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void reportsAgentAndOverlappingChanges() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        Files.writeString(repo.resolve("README.md"), "preexisting\n");
        Workspace workspace = Workspace.open(repo);
        Files.writeString(repo.resolve("README.md"), "agent overlap\n");
        Files.writeString(repo.resolve("new.txt"), "new\n");
        workspace.recordChanges(java.util.List.of("README.md", "new.txt"));

        var result = new GitDiffTool().execute(JSON.createObjectNode(), ToolTestContext.create(workspace));

        assertEquals(ToolStatus.OK, result.status());
        assertEquals("OVERLAPS_PREEXISTING_CHANGE",
                result.data().path("attribution").path("README.md").asText());
        assertEquals("AGENT_CREATED", result.data().path("attribution").path("new.txt").asText());
        assertTrue(result.data().path("diff").asText().contains("agent overlap"));
        assertTrue(result.data().path("diff").asText().contains("new.txt"));
        assertTrue(result.data().path("diff").asText().contains("+new"));
    }
}
