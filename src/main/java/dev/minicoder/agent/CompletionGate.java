package dev.minicoder.agent;

import dev.minicoder.config.RunConfig;

import java.util.Comparator;
import java.util.List;

/**
 * 根据当前工作区 revision 和验证证据校准最终状态，防止模型文本越过成功门禁。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class CompletionGate {
    public Decision evaluate(RunConfig config, long workspaceRevision, List<VerificationEvidence> evidence) {
        if (workspaceRevision == 0) {
            return new Decision(RunStatus.SUCCEEDED_WITH_WARNINGS,
                    "No workspace modification was observed; the coding task is not fully verified");
        }
        // 只接受最终 revision 的相关证据；任何后续文件变化都会使旧验证自动失效。
        List<VerificationEvidence> currentRevision = evidence.stream()
                .filter(item -> item.workspaceRevision() == workspaceRevision)
                .filter(item -> config.verifyCommand().map(required -> commandMatches(item.command(), required))
                        .orElse(true))
                .toList();
        if (!currentRevision.isEmpty() && currentRevision.stream().noneMatch(VerificationEvidence::passed)) {
            return new Decision(RunStatus.TOOL_ERROR,
                    "Relevant verification failed at the final workspace revision");
        }
        VerificationEvidence current = evidence.stream()
                .filter(item -> item.workspaceRevision() == workspaceRevision)
                .filter(VerificationEvidence::passed)
                .filter(item -> config.verifyCommand().map(required -> commandMatches(item.command(), required))
                        .orElse(true))
                .max(Comparator.comparingLong(VerificationEvidence::durationMs))
                .orElse(null);
        if (current == null) {
            return new Decision(RunStatus.SUCCEEDED_WITH_WARNINGS,
                    "The final workspace revision has no successful relevant verification evidence");
        }
        return new Decision(RunStatus.SUCCEEDED, "Final workspace revision is verified by: " + current.command());
    }

    static boolean commandMatches(String actual, String required) {
        // 使用有序子序列匹配，允许调用方添加安全参数，但不能打乱用户要求的验证命令语义。
        String[] actualTokens = normalize(actual).split(" ");
        String[] requiredTokens = normalize(required).split(" ");
        int requiredIndex = 0;
        for (String token : actualTokens) {
            if (requiredIndex < requiredTokens.length && token.equals(requiredTokens[requiredIndex])) {
                requiredIndex++;
            }
        }
        return requiredIndex == requiredTokens.length;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("\\s+", " ").strip();
    }

    public record Decision(RunStatus status, String reason) {}
}
