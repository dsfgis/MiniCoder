package dev.minicoder.workspace;

/**
 * 表示工作区无效、Git 基线读取失败或路径策略无法继续等错误。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public final class WorkspaceException extends RuntimeException {
    public WorkspaceException(String message) {
        super(message);
    }

    public WorkspaceException(String message, Throwable cause) {
        super(message, cause);
    }
}
