package dev.minicoder.security;

import java.util.List;

/**
 * 在命令具有外部副作用时请求用户决策，并返回可审计的批准结果。
 *
 * @author Self David (dsfgis@gmail.com)
 */
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
