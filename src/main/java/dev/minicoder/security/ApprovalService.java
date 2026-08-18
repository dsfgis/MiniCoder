package dev.minicoder.security;

import java.util.List;

@FunctionalInterface
public interface ApprovalService {
    boolean approve(String executable, List<String> arguments, String reason);

    static ApprovalService denyAll() {
        return (executable, arguments, reason) -> false;
    }

    static ApprovalService allowAllForTests() {
        return (executable, arguments, reason) -> true;
    }
}

