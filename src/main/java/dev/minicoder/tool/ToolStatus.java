package dev.minicoder.tool;

/**
 * 定义工具调用可观察的成功、输入、策略、超时、冲突和失败状态。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public enum ToolStatus {
    OK,
    INVALID_INPUT,
    INVALID_TOOL_CALL,
    NOT_FOUND,
    NO_MATCH,
    POLICY_DENIED,
    APPROVAL_REQUIRED,
    APPROVAL_DENIED,
    TIMEOUT,
    CONFLICT,
    FAILED,
    WORKSPACE_INCONSISTENT
}
