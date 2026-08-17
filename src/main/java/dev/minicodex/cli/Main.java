package dev.minicodex.cli;

import dev.minicodex.agent.*;
import dev.minicodex.config.*;
import dev.minicodex.llm.LlmProvider;
import dev.minicodex.llm.ScriptedLlmProvider;
import dev.minicodex.llm.LlmModels.ProviderResponse;
import dev.minicodex.llm.openai.OpenAiResponsesProvider;
import dev.minicodex.observability.InMemoryEventSink;
import dev.minicodex.report.RunReport;
import dev.minicodex.security.*;
import dev.minicodex.tool.ToolRegistry;
import dev.minicodex.tool.file.*;
import dev.minicodex.tool.git.GitDiffTool;
import dev.minicodex.tool.patch.ApplyPatchTool;
import dev.minicodex.tool.shell.*;
import dev.minicodex.workspace.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

@Command(name = "mini-coder", mixinStandardHelpOptions = true, version = "Mini Coder 0.1.0",
        description = "Run one evidence-driven coding-agent task in a trusted local Git workspace.%n"
                + "V0.1 policy controls are not an OS sandbox; use only trusted repositories or disposable copies.",
        footer = "%nEnvironment:%n  OPENAI_API_KEY (required for openai), OPENAI_MODEL, OPENAI_BASE_URL%n"
                + "%nExample:%n  mini-coder --workspace . --task \"Fix the failing test\" --model gpt-5")
public final class Main implements Callable<Integer> {
    @Spec
    private CommandSpec commandSpec;
    @Option(names = "--workspace", required = true, description = "Existing local Git workspace")
    private Path workspace;
    @Option(names = "--task", required = true, description = "Coding task")
    private String task;
    @Option(names = "--provider", defaultValue = "openai", description = "Provider: openai or scripted")
    private String provider;
    @Option(names = "--model", description = "Model; falls back to OPENAI_MODEL")
    private String model;
    @Option(names = "--base-url", description = "API base URL; falls back to OPENAI_BASE_URL")
    private String baseUrl;
    @Option(names = "--max-iterations", defaultValue = "30")
    private int maxIterations;
    @Option(names = "--max-duration-seconds", defaultValue = "900")
    private long maxDurationSeconds;
    @Option(names = "--verify-command", description = "Required verification command fingerprint")
    private String verifyCommand;
    @Option(names = "--non-interactive", description = "Deny commands that require approval")
    private boolean nonInteractive;
    @Option(names = "--json-report", description = "Optional JSON report path")
    private Path jsonReport;

    public static void main(String[] args) {
        int exit = commandLine().execute(args);
        System.exit(exit);
    }

    static CommandLine commandLine() {
        return new CommandLine(new Main()).setParameterExceptionHandler((error, args) -> {
            error.getCommandLine().getErr().println("CONFIG_ERROR: " + error.getMessage());
            error.getCommandLine().usage(error.getCommandLine().getErr());
            return 20;
        });
    }

    @Override
    public Integer call() {
        try {
            if (!provider.equalsIgnoreCase("openai") && !provider.equalsIgnoreCase("scripted")) {
                throw new ConfigException("V0.1 supports openai and the offline scripted Provider");
            }
            boolean scripted = provider.equalsIgnoreCase("scripted");
            String selectedModel = scripted ? firstNonBlank(model, "scripted-v0.1")
                    : firstNonBlank(model, System.getenv("OPENAI_MODEL"));
            String apiKey = System.getenv("OPENAI_API_KEY");
            String selectedBaseUrl = firstNonBlank(baseUrl, System.getenv("OPENAI_BASE_URL"), "https://api.openai.com");
            if (selectedModel == null) throw new ConfigException("Missing model: use --model or OPENAI_MODEL");
            if (!scripted && (apiKey == null || apiKey.isBlank())) throw new ConfigException("Missing OPENAI_API_KEY");
            List<String> missing = DependencyPreflight.check("git", "rg");
            if (!missing.isEmpty()) throw new ConfigException("Missing required executables: " + missing);

            RunConfig config = new RunConfig(workspace, task, provider.toLowerCase(), selectedModel,
                    maxIterations, Duration.ofSeconds(maxDurationSeconds), Optional.ofNullable(verifyCommand),
                    !nonInteractive, Optional.ofNullable(jsonReport));
            Workspace opened = Workspace.open(config.workspace());
            Redactor redactor = new Redactor(scripted ? List.of() : List.of(apiKey));
            ProcessRunner runner = new ProcessRunner();
            CommandPolicy policy = new CommandPolicy(opened.root());
            ApprovalService approvals = new ConsoleApprovalService(redactor, config.interactive());
            ToolRegistry tools = new ToolRegistry()
                    .register(new ListFilesTool())
                    .register(new ReadFileTool())
                    .register(new SearchCodeTool(runner))
                    .register(new ApplyPatchTool())
                    .register(new ShellTool(runner, policy, approvals, redactor))
                    .register(new GitDiffTool());
            LlmProvider llm = scripted
                    ? new ScriptedLlmProvider(ProviderResponse.finalText("scripted-final",
                            "Offline scripted Provider completed without changing the workspace."))
                    : new OpenAiResponsesProvider(HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20)).build(), URI.create(selectedBaseUrl), apiKey,
                    selectedModel, redactor);
            CancellationToken token = new CancellationToken();
            Runtime.getRuntime().addShutdownHook(new Thread(token::cancel, "mini-coder-cancel"));
            InMemoryEventSink events = new InMemoryEventSink();
            String runId = UUID.randomUUID().toString();
            commandSpec.commandLine().getOut().println("runId: " + runId);
            RunOutcome outcome = new AgentRuntime(llm, tools, new CompletionGate(), events)
                    .run(config, opened, token, runId);
            RunReport report = new RunReport(outcome, config.provider(), config.model(), redactor);
            commandSpec.commandLine().getOut().print(report.renderConsole());
            commandSpec.commandLine().getOut().flush();
            if (config.jsonReport().isPresent()) {
                Path reportPath = config.jsonReport().get().toAbsolutePath().normalize();
                if (reportPath.getParent() != null) Files.createDirectories(reportPath.getParent());
                Files.writeString(reportPath, report.renderJson(), StandardCharsets.UTF_8);
            }
            return exitCode(outcome.status());
        } catch (ConfigException | IllegalArgumentException | WorkspaceException e) {
            commandSpec.commandLine().getErr().println("CONFIG_ERROR: " + e.getMessage());
            return 20;
        } catch (Exception e) {
            commandSpec.commandLine().getErr().println(
                    "TOOL_ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return 30;
        }
    }

    static int exitCode(RunStatus status) {
        return status.exitCode();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.strip();
        return null;
    }
}
