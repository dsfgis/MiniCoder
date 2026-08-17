package dev.minicodex.security;

import java.io.Console;
import java.util.List;

public final class ConsoleApprovalService implements ApprovalService {
    private final Redactor redactor;
    private final boolean interactive;

    public ConsoleApprovalService(Redactor redactor, boolean interactive) {
        this.redactor = redactor;
        this.interactive = interactive;
    }

    @Override
    public boolean approve(String executable, List<String> arguments, String reason) {
        if (!interactive) return false;
        Console console = System.console();
        if (console == null) return false;
        String command = redactor.redact(executable + " " + String.join(" ", arguments));
        String answer = console.readLine("Approval required (%s)%nCommand: %s%nAllow once? [y/N] ", reason, command);
        return answer != null && (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes"));
    }
}

