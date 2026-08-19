package dev.minicoder.cli;

import dev.minicoder.agent.*;
import dev.minicoder.config.*;
import dev.minicoder.llm.LlmProvider;
import dev.minicoder.llm.ScriptedLlmProvider;
import dev.minicoder.llm.LlmModels.ProviderResponse;
import dev.minicoder.llm.deepseek.DeepSeekResponsesProvider;
import dev.minicoder.llm.openai.OpenAiResponsesProvider;
import dev.minicoder.observability.InMemoryEventSink;
import dev.minicoder.report.RunReport;
import dev.minicoder.security.*;
import dev.minicoder.tool.ToolRegistry;
import dev.minicoder.tool.file.*;
import dev.minicoder.tool.git.GitDiffTool;
import dev.minicoder.tool.patch.ApplyPatchTool;
import dev.minicoder.tool.shell.*;
import dev.minicoder.workspace.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Function;

@Command(name = "mini-coder", mixinStandardHelpOptions = true, version = "Mini Coder 0.1.0",
        description = "Run one evidence-driven coding-agent task in a trusted local Git workspace.%n"
                + "V0.1 policy controls are not an OS sandbox; use only trusted repositories or disposable copies.",
        footer = "%nEnvironment:%n"
                + "  OpenAI: OPENAI_API_KEY, OPENAI_MODEL, OPENAI_BASE_URL%n"
                + "  DeepSeek: DEEPSEEK_API_KEY, DEEPSEEK_MODEL, DEEPSEEK_BASE_URL%n"
                + "  API keys are read only from the selected Provider's environment variable.%n"
                + "%nExamples:%n"
                + "  mini-coder --workspace . --task \"Fix the failing test\" --model gpt-5%n"
                + "  mini-coder --workspace . --task \"Fix the failing test\" --provider deepseek --model <model>")
public final class Main implements Callable<Integer> {
    private final Function<String, String> environment;

    public Main() {
        this(System::getenv);
    }

    Main(Map<String, String> environment) {
        Map<String, String> snapshot = Map.copyOf(environment);
        this.environment = snapshot::get;
    }

    private Main(Function<String, String> environment) {
        this.environment = environment;
    }

    @Spec
    private CommandSpec commandSpec;
    @Option(names = "--workspace", required = true, description = "Existing local Git workspace")
    private Path workspace;
    @Option(names = "--task", required = true, description = "Coding task")
    private String task;
    @Option(names = "--provider", defaultValue = "openai", description = "Provider: openai, deepseek, or scripted")
    private String provider;
    @Option(names = "--model", description = "Model; falls back to the selected Provider's model environment variable")
    private String model;
    @Option(names = "--base-url", description = "API base URL; falls back to the selected Provider's Base URL environment variable")
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
        return configured(new Main());
    }

    static CommandLine commandLine(Map<String, String> environment) {
        return configured(new Main(environment));
    }

    private static CommandLine configured(Main command) {
        return new CommandLine(command).setParameterExceptionHandler((error, args) -> {
            error.getCommandLine().getErr().println("CONFIG_ERROR: " + error.getMessage());
            error.getCommandLine().usage(error.getCommandLine().getErr());
            return 20;
        });
    }

    @Override
    public Integer call() {
        try {
            ProviderConfig selected = ProviderConfig.resolve(provider, model, baseUrl, environment);
            List<String> missing = DependencyPreflight.check("git", "rg");
            if (!missing.isEmpty()) throw new ConfigException("Missing required executables: " + missing);

            RunConfig config = new RunConfig(workspace, task, selected.provider(), selected.model(),
                    maxIterations, Duration.ofSeconds(maxDurationSeconds), Optional.ofNullable(verifyCommand),
                    !nonInteractive, Optional.ofNullable(jsonReport));
            Workspace opened = Workspace.open(config.workspace());
            Redactor redactor = new Redactor(selected.provider().equals(ProviderConfig.SCRIPTED)
                    ? List.of() : List.of(selected.apiKey()));
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
            HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
            LlmProvider llm = switch (selected.provider()) {
                case ProviderConfig.SCRIPTED -> new ScriptedLlmProvider(ProviderResponse.finalText("scripted-final",
                        "Offline scripted Provider completed without changing the workspace."));
                case ProviderConfig.OPENAI -> new OpenAiResponsesProvider(httpClient, selected.baseUrl(),
                        selected.apiKey(), selected.model(), redactor);
                case ProviderConfig.DEEPSEEK -> new DeepSeekResponsesProvider(httpClient, selected.baseUrl(),
                        selected.apiKey(), selected.model(), redactor);
                default -> throw new ConfigException("Unsupported Provider: " + selected.provider());
            };
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

}
