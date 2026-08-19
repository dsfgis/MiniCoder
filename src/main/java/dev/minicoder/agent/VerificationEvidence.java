package dev.minicoder.agent;

import java.util.Objects;

/**
 * 记录验证命令在特定工作区 revision 上的退出码与耗时，供完成门判断证据是否有效。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public record VerificationEvidence(String command, int exitCode, long workspaceRevision, long durationMs) {
    public VerificationEvidence {
        command = Objects.requireNonNullElse(command, "");
    }

    public boolean passed() {
        return exitCode == 0;
    }
}
