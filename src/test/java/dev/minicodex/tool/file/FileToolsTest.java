package dev.minicodex.tool.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicodex.support.TemporaryGitRepository;
import dev.minicodex.tool.ToolStatus;
import dev.minicodex.tool.ToolTestContext;
import dev.minicodex.tool.shell.ProcessRunner;
import dev.minicodex.tool.shell.CommandSpec;
import dev.minicodex.tool.shell.ProcessResult;
import dev.minicodex.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileToolsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void listsReadsAndSearchesWorkspaceText() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo 中文"));
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/Example.java"), "class Example { String needle = \"值\"; }\r\n");
        Workspace workspace = Workspace.open(repo);
        var context = ToolTestContext.create(workspace);

        var listed = new ListFilesTool().execute(JSON.readTree("{\"path\":\".\",\"maxDepth\":3}"), context);
        assertEquals(ToolStatus.OK, listed.status());
        assertTrue(listed.data().path("entries").toString().contains("src/Example.java"));

        var read = new ReadFileTool().execute(JSON.readTree("{\"path\":\"src/Example.java\",\"startLine\":1,\"endLine\":5}"), context);
        assertEquals(ToolStatus.OK, read.status());
        assertTrue(read.data().path("content").asText().contains("1 | class Example"));

        var search = new SearchCodeTool(new ProcessRunner()).execute(
                JSON.readTree("{\"query\":\"needle\",\"path\":\"src\"}"), context);
        assertEquals(ToolStatus.OK, search.status());
        assertTrue(search.data().path("matches").asText().contains("Example.java"));
    }

    @Test
    void rejectsBinaryAndEscapingPathsAndDistinguishesNoMatch() throws Exception {
        Path repo = TemporaryGitRepository.create(temp.resolve("repo"));
        Files.write(repo.resolve("binary.bin"), new byte[]{1, 0, 2});
        Workspace workspace = Workspace.open(repo);
        var context = ToolTestContext.create(workspace);

        assertEquals(ToolStatus.INVALID_INPUT, new ReadFileTool().execute(
                JSON.readTree("{\"path\":\"binary.bin\"}"), context).status());
        assertEquals(ToolStatus.POLICY_DENIED, new ListFilesTool().execute(
                JSON.readTree("{\"path\":\"../\"}"), context).status());
        assertEquals(ToolStatus.NO_MATCH, new SearchCodeTool(new ProcessRunner()).execute(
                JSON.readTree("{\"query\":\"definitely_absent\",\"path\":\".\"}"), context).status());

        Files.write(repo.resolve("invalid-utf8.txt"), new byte[]{(byte) 0xC3, 0x28});
        assertEquals(ToolStatus.INVALID_INPUT, new ReadFileTool().execute(
                JSON.readTree("{\"path\":\"invalid-utf8.txt\"}"), context).status());

        ProcessRunner missingRg = new ProcessRunner() {
            @Override public ProcessResult run(CommandSpec spec, Path workingDirectory,
                                               dev.minicodex.agent.CancellationToken token) throws java.io.IOException {
                throw new java.io.IOException("rg executable not found");
            }
        };
        assertEquals(ToolStatus.FAILED, new SearchCodeTool(missingRg).execute(
                JSON.readTree("{\"query\":\"anything\",\"path\":\".\"}"), context).status());
    }
}
