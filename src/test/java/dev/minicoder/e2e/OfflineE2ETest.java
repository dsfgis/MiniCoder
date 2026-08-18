package dev.minicoder.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.minicoder.agent.*;
import dev.minicoder.config.RunConfig;
import dev.minicoder.llm.LlmModels.*;
import dev.minicoder.llm.ScriptedLlmProvider;
import dev.minicoder.observability.InMemoryEventSink;
import dev.minicoder.security.*;
import dev.minicoder.support.TemporaryGitRepository;
import dev.minicoder.tool.ToolRegistry;
import dev.minicoder.tool.file.*;
import dev.minicoder.tool.git.GitDiffTool;
import dev.minicoder.tool.patch.ApplyPatchTool;
import dev.minicoder.tool.shell.*;
import dev.minicoder.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "mini.codex.e2e", matches = "true")
class OfflineE2ETest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void repairsNullHandlingRunsFinalRevisionVerificationAndReportsDiff() throws Exception {
        Path repo = fixture(temp.resolve("Spring Fixture 中文"));
        ProcessResult before = new ProcessRunner().run(new CommandSpec("mvn.cmd", List.of("-o", "-q", "test"),
                Duration.ofSeconds(60), 64 * 1024, ShellMode.NONE), repo, new CancellationToken());
        assertNotEquals(0, before.exitCode(), "fixture must reproduce the defect before the agent runs");

        String patch = """
                --- a/src/main/java/fixture/UserService.java
                +++ b/src/main/java/fixture/UserService.java
                @@ -7,3 +7,6 @@
                     String display(User user) {
                -        return user.name().trim();
                +        if (user == null || user.name() == null || user.name().isBlank()) {
                +            return "anonymous";
                +        }
                +        return user.name().trim();
                     }
                """;
        ScriptedLlmProvider provider = new ScriptedLlmProvider(
                ProviderResponse.tools("r1", List.of(call("c1", "read_file",
                        "{\"path\":\"src/main/java/fixture/UserService.java\"}"))),
                ProviderResponse.tools("r2", List.of(call("c2", "apply_patch",
                        JSON.createObjectNode().put("patch", patch)))),
                ProviderResponse.tools("r3", List.of(call("c3", "shell",
                        "{\"executable\":\"mvn.cmd\",\"args\":[\"-o\",\"-q\",\"test\"],\"timeoutMs\":60000}"))),
                ProviderResponse.tools("r4", List.of(call("c4", "git_diff", "{}"))),
                ProviderResponse.finalText("r5", "Null handling fixed and verified."));
        Workspace workspace = Workspace.open(repo);
        RunOutcome outcome = runtime(provider, tools(workspace)).run(config(repo, 10), workspace,
                new CancellationToken(), "e2e-success");

        assertEquals(RunStatus.SUCCEEDED, outcome.status(),
                () -> outcome.reason() + " | verification=" + outcome.verification()
                        + " | requests=" + provider.requests().stream()
                        .map(request -> request.newToolResults().toString()).toList());
        assertEquals(List.of("src/main/java/fixture/UserService.java"), outcome.changedFiles());
        assertEquals("AGENT_MODIFIED", outcome.changeAttribution().get("src/main/java/fixture/UserService.java"));
        assertTrue(outcome.gitDiff().contains("anonymous"));
        assertEquals(0, outcome.verification().getLast().exitCode());
        assertEquals(outcome.workspaceRevision(), outcome.verification().getLast().workspaceRevision());
        assertTrue(outcome.events().stream().filter(event -> event.type().equals("tool_started"))
                .map(event -> event.toolCallId().orElseThrow()).toList()
                .equals(List.of("c1", "c2", "c3", "c4")));
        assertEquals("""
                package fixture;

                import org.springframework.stereotype.Service;

                @Service
                public class UserService {
                    String display(User user) {
                        if (user == null || user.name() == null || user.name().isBlank()) {
                            return "anonymous";
                        }
                        return user.name().trim();
                    }

                    record User(String name) {}
                }
                """, Files.readString(repo.resolve("src/main/java/fixture/UserService.java")));
    }

    @Test
    void classifiesEscapeConflictApprovalAndBudgetFailuresWithoutSideEffects() throws Exception {
        assertEquals(RunStatus.POLICY_BLOCKED, runFailureScenario("escape", call("p1", "apply_patch",
                JSON.createObjectNode().put("patch", """
                        --- a/README.md
                        +++ b/../escape.txt
                        @@ -0,0 +1,1 @@
                        +escape
                        """))).status());

        assertEquals(RunStatus.TOOL_ERROR, runFailureScenario("conflict", call("p2", "apply_patch",
                JSON.createObjectNode().put("patch", """
                        --- a/README.md
                        +++ b/README.md
                        @@ -1,1 +1,1 @@
                        -does-not-match
                        +changed
                        """))).status());

        assertEquals(RunStatus.POLICY_BLOCKED, runFailureScenario("approval", call("s1", "shell",
                "{\"executable\":\"curl\",\"args\":[\"https://example.invalid\"]}")).status());

        Path repo = fixture(temp.resolve("budget"));
        Workspace workspace = Workspace.open(repo);
        ScriptedLlmProvider looping = new ScriptedLlmProvider(
                ProviderResponse.tools("r1", List.of(call("a", "read_file", "{\"path\":\"README.md\"}"))),
                ProviderResponse.tools("r2", List.of(call("b", "list_files", "{}"))));
        assertEquals(RunStatus.LIMIT_REACHED,
                runtime(looping, tools(workspace)).run(config(repo, 2), workspace, new CancellationToken()).status());
    }

    private RunOutcome runFailureScenario(String name, ToolCall toolCall) throws Exception {
        Path repo = fixture(temp.resolve(name));
        Workspace workspace = Workspace.open(repo);
        ScriptedLlmProvider provider = new ScriptedLlmProvider(
                ProviderResponse.tools("r1", List.of(toolCall)),
                ProviderResponse.finalText("r2", "cannot continue"));
        return runtime(provider, tools(workspace)).run(config(repo, 3), workspace, new CancellationToken());
    }

    private AgentRuntime runtime(ScriptedLlmProvider provider, ToolRegistry registry) {
        return new AgentRuntime(provider, registry, new CompletionGate(), new InMemoryEventSink());
    }

    private ToolRegistry tools(Workspace workspace) {
        ProcessRunner runner = new ProcessRunner();
        Redactor redactor = new Redactor(List.of("test-secret"));
        return new ToolRegistry()
                .register(new ListFilesTool())
                .register(new ReadFileTool())
                .register(new SearchCodeTool(runner))
                .register(new ApplyPatchTool())
                .register(new ShellTool(runner, new CommandPolicy(workspace.root()), ApprovalService.denyAll(), redactor))
                .register(new GitDiffTool());
    }

    private RunConfig config(Path repo, int iterations) {
        return new RunConfig(repo, "Fix the null handling defect", "fake", "scripted", iterations,
                Duration.ofSeconds(120), Optional.of("mvn.cmd -o -q test"), false, Optional.empty());
    }

    private ToolCall call(String id, String name, String json) throws Exception {
        return call(id, name, JSON.readTree(json));
    }

    private ToolCall call(String id, String name, com.fasterxml.jackson.databind.JsonNode arguments) {
        return new ToolCall(id, name, arguments);
    }

    private Path fixture(Path repo) throws Exception {
        TemporaryGitRepository.create(repo);
        Files.createDirectories(repo.resolve("src/main/java/fixture"));
        Files.createDirectories(repo.resolve("src/test/java/fixture"));
        Files.writeString(repo.resolve(".gitignore"), "target/\n", StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>fixture</groupId><artifactId>spring-null-fixture</artifactId><version>1.0</version>
                  <properties>
                    <maven.compiler.release>21</maven.compiler.release>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                  </properties>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId>
                      <version>3.3.2</version>
                    </dependency>
                    <dependency>
                      <groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId>
                      <version>5.10.3</version><scope>test</scope>
                    </dependency>
                  </dependencies>
                  <build><plugins>
                    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version></plugin>
                    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.2.5</version></plugin>
                  </plugins></build>
                </project>
                """, StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("src/main/java/fixture/DemoApplication.java"), """
                package fixture;

                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class DemoApplication {}
                """, StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("src/main/java/fixture/UserService.java"), """
                package fixture;

                import org.springframework.stereotype.Service;

                @Service
                public class UserService {
                    String display(User user) {
                        return user.name().trim();
                    }

                    record User(String name) {}
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("src/test/java/fixture/UserServiceTest.java"), """
                package fixture;

                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;

                class UserServiceTest {
                    @Test void nullUserIsAnonymous() {
                        assertEquals("anonymous", new UserService().display(null));
                    }
                }
                """, StandardCharsets.UTF_8);
        TemporaryGitRepository.git(repo, "add", ".gitignore", "pom.xml", "src");
        TemporaryGitRepository.git(repo, "commit", "-m", "add controlled Spring service fixture");
        return repo;
    }
}
