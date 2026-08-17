package dev.minicodex.tool.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicodex.support.TemporaryGitRepository;
import dev.minicodex.tool.ToolStatus;
import dev.minicodex.tool.ToolTestContext;
import dev.minicodex.tool.shell.ProcessRunner;
import dev.minicodex.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ListFilesToolTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void boundsDirectoryFileAndSearchOutputs() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        for (int i = 0; i < 10; i++) Files.writeString(repo.resolve("file-" + i + ".txt"), "needle\nneedle\n");
        Workspace workspace = Workspace.open(repo);
        var context = ToolTestContext.create(workspace);

        var list = new ListFilesTool().execute(JSON.readTree("{\"path\":\".\",\"limit\":3}"), context);
        assertEquals(ToolStatus.OK, list.status());
        assertEquals(3, list.data().path("entries").size());
        assertTrue(list.truncated());
        assertTrue(list.data().path("hasMore").asBoolean());

        var search = new SearchCodeTool(new ProcessRunner()).execute(
                JSON.readTree("{\"query\":\"needle\",\"path\":\".\",\"limit\":2}"), context);
        assertEquals(ToolStatus.OK, search.status());
        assertEquals(2, search.data().path("matchesReturned").asInt());
        assertTrue(search.truncated());

        Path large = repo.resolve("large.txt");
        try (var channel = Files.newByteChannel(large, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(4L * 1024 * 1024);
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{1}));
        }
        assertEquals(ToolStatus.INVALID_INPUT, new ReadFileTool().execute(
                JSON.readTree("{\"path\":\"large.txt\"}"), context).status());
    }
}
