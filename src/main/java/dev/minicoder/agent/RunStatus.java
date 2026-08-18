package dev.minicoder.agent;

public enum RunStatus {
    INITIALIZING,
    RUNNING,
    WAITING_APPROVAL,
    VERIFYING,
    SUCCEEDED,
    SUCCEEDED_WITH_WARNINGS,
    CANCELLED,
    POLICY_BLOCKED,
    CONFIG_ERROR,
    PROVIDER_ERROR,
    TOOL_ERROR,
    LIMIT_REACHED,
    NO_PROGRESS,
    WORKSPACE_INCONSISTENT;

    public int exitCode() {
        return switch (this) {
            case SUCCEEDED -> 0;
            case SUCCEEDED_WITH_WARNINGS -> 10;
            case CANCELLED -> 11;
            case CONFIG_ERROR -> 20;
            case POLICY_BLOCKED -> 21;
            case PROVIDER_ERROR -> 22;
            case TOOL_ERROR, WORKSPACE_INCONSISTENT -> 30;
            case LIMIT_REACHED -> 40;
            case NO_PROGRESS -> 41;
            default -> 30;
        };
    }
}
