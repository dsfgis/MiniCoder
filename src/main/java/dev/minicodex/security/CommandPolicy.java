package dev.minicodex.security;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandPolicy {
    private static final Set<String> DENIED_EXECUTABLES = Set.of(
            "rm", "rmdir", "del", "erase", "format", "diskpart", "shutdown", "reboot",
            "sudo", "runas", "scp", "ssh");
    private static final Set<String> NETWORK_EXECUTABLES = Set.of(
            "curl", "wget", "invoke-webrequest", "invoke-restmethod", "scp", "ssh");
    private static final Set<String> PACKAGE_EXECUTABLES = Set.of(
            "npm", "pnpm", "yarn", "pip", "pip3", "winget", "choco", "scoop");

    private final Path workspaceRoot;

    public CommandPolicy(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    public Decision classify(String executable, List<String> arguments) {
        String name = baseName(executable);
        List<String> args = arguments == null ? List.of() : arguments;
        String joined = String.join(" ", args).toLowerCase(Locale.ROOT);

        try {
            Path executablePath = Path.of(executable);
            if (executablePath.isAbsolute()
                    && !executablePath.toAbsolutePath().normalize().startsWith(workspaceRoot)) {
                return Decision.deny("Absolute executable outside the workspace is denied");
            }
        } catch (RuntimeException e) {
            return Decision.deny("Invalid executable path");
        }

        if (DENIED_EXECUTABLES.contains(name)) {
            return Decision.deny("Destructive or remote-control executable is denied in V0.1");
        }
        if (containsCredentialPath(joined) || containsWorkspaceEscape(args)) {
            return Decision.deny("Command references a credential location or escapes the workspace");
        }
        if (name.equals("git")) {
            return classifyGit(args);
        }
        if (name.equals("powershell") || name.equals("pwsh") || name.equals("cmd") || name.equals("bash")) {
            String script = joined;
            if (containsDestructiveScript(script)) {
                return Decision.deny("Destructive shell expression is denied in V0.1");
            }
            return Decision.approval("Explicit shell mode requires approval");
        }
        if (NETWORK_EXECUTABLES.contains(name)) {
            return Decision.approval("Network access requires approval");
        }
        if (PACKAGE_EXECUTABLES.contains(name)) {
            if (joined.contains("publish") || joined.contains("install") || joined.contains(" add ")) {
                return Decision.approval("Package installation or publishing requires approval");
            }
        }
        if (name.equals("docker") || name.equals("kubectl") || name.equals("terraform")) {
            return Decision.approval("External infrastructure or container changes require approval");
        }
        if (isKnownLocalBuild(name, args) || isReadOnlyExecutable(name)) {
            return Decision.allow("Known local read or verification command");
        }
        return Decision.approval("Command is not in the V0.1 automatic allow set");
    }

    public Decision classifyExplicitShell(String executable, List<String> arguments) {
        String expression = (executable + " " + String.join(" ", arguments == null ? List.of() : arguments))
                .toLowerCase(Locale.ROOT);
        if (containsDestructiveScript(expression) || containsCredentialPath(expression)
                || expression.contains("get-childitem env:") || expression.contains("printenv")
                || expression.contains("-encodedcommand")) {
            return Decision.deny("Destructive, credential, encoded, or environment-enumeration shell expression is denied");
        }
        return Decision.approval("Explicit shell mode requires approval");
    }

    private Decision classifyGit(List<String> args) {
        if (args.isEmpty()) {
            return Decision.allow("Git help/status invocation");
        }
        String verb = args.getFirst().toLowerCase(Locale.ROOT);
        Set<String> readOnly = Set.of("status", "diff", "log", "show", "rev-parse", "ls-files", "grep");
        if (readOnly.contains(verb)) {
            return Decision.allow("Read-only Git command");
        }
        Set<String> denied = Set.of("reset", "clean", "checkout", "switch", "rebase", "merge", "commit");
        if (denied.contains(verb)) {
            return Decision.deny("Git history or workspace mutation is denied in V0.1");
        }
        if (Set.of("push", "fetch", "pull", "clone").contains(verb)) {
            return Decision.approval("Remote Git access requires approval");
        }
        return Decision.approval("Unrecognized Git mutation requires approval");
    }

    private boolean containsWorkspaceEscape(List<String> args) {
        for (String arg : args) {
            if (arg == null) continue;
            String normalized = arg.replace('\\', '/');
            if (normalized.contains("../")) {
                return true;
            }
            try {
                Path path = Path.of(arg);
                if (path.isAbsolute() && !path.toAbsolutePath().normalize().startsWith(workspaceRoot)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }

    private static boolean containsCredentialPath(String input) {
        return input.contains("/.ssh") || input.contains("\\.ssh")
                || input.contains("/.aws") || input.contains("\\.aws")
                || input.contains("/.azure") || input.contains("\\.azure")
                || input.contains("credentials") || input.matches(".*(^|[ /\\\\])\\.env($|[ /\\\\]).*");
    }

    private static boolean containsDestructiveScript(String input) {
        return input.contains("remove-item") || input.contains("rm -") || input.contains("del ")
                || input.contains("rmdir") || input.contains("git reset") || input.contains("git clean")
                || input.contains("git checkout") || input.contains("git push") || input.contains("format ");
    }

    private static boolean isKnownLocalBuild(String name, List<String> args) {
        Set<String> build = new HashSet<>(Set.of("mvn", "mvn.cmd", "mvnw", "mvnw.cmd", "gradle", "gradlew",
                "gradlew.bat", "npm", "pnpm", "yarn", "pytest", "dotnet"));
        if (!build.contains(name)) return false;
        String joined = String.join(" ", args).toLowerCase(Locale.ROOT);
        if (joined.contains("publish") || joined.contains("deploy") || joined.contains("install")) return false;
        if (Set.of("npm", "pnpm", "yarn").contains(name)) {
            return args.isEmpty() || Set.of("test", "run", "lint", "check", "build").contains(args.getFirst().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    private static boolean isReadOnlyExecutable(String name) {
        return Set.of("rg", "grep", "findstr", "java", "javac", "where", "where.exe").contains(name);
    }

    private static String baseName(String executable) {
        String name = Path.of(executable).getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe")) name = name.substring(0, name.length() - 4);
        return name;
    }

    public record Decision(RiskClassification classification, String reason) {
        public static Decision allow(String reason) { return new Decision(RiskClassification.ALLOW, reason); }
        public static Decision approval(String reason) { return new Decision(RiskClassification.REQUIRE_APPROVAL, reason); }
        public static Decision deny(String reason) { return new Decision(RiskClassification.DENY, reason); }
    }
}
