# Mini Coder V0.1 架构

## 运行链路

```text
CLI / RunConfig
      |
      v
Workspace.open -> GitBaseline + WorkspaceGuard
      |
      v
AgentRuntime <-> LlmProvider (OpenAI or scripted test double)
      |
      v
ToolRegistry -> six Provider-neutral tools
      |
      +-> file/search/patch
      +-> CommandPolicy -> ApprovalService -> ProcessRunner
      +-> read-only Git diff + attribution
      |
      v
CompletionGate -> immutable RunOutcome -> console/JSON RunReport
```

`AgentRuntime` 只依赖 Provider-neutral 请求、响应、工具调用和结果。OpenAI JSON 与 HTTP 类型封装在 `llm/openai`；核心包不会根据具体工具名实现工具行为分支，工具发现和校验统一经过 `ToolRegistry`。

## 不变量

1. 所有文件目标先经 `WorkspaceGuard` 解析真实路径并验证根边界。
2. `apply_patch` 先解析并对所有文件完成内存预检，再在同一文件系统暂存和替换；可控失败会回滚。
3. 每次成功补丁只增加一次 revision，并记录 Agent 实际触碰路径。
4. Git 归属区分 `PREEXISTING`、`AGENT_CREATED`、`AGENT_MODIFIED`、`OVERLAPS_PREEXISTING_CHANGE` 和 `UNKNOWN`。
5. `shell` 在进程创建前完成策略和审批；非零退出是可观察结果，超时/取消会清理进程树。
6. 多工具调用按响应顺序串行执行；循环受迭代数、总时限、Provider 重试和无进展阈值约束。
7. 完成状态由当前 revision 的验证证据校准；Provider 文本不能覆盖进程退出码、Git 事实或策略结果。
8. 终端和 JSON 从同一个 `RunOutcome` 渲染，报告前统一脱敏。

## 测试层次

- 单元/合同：配置、Provider-neutral 合同、工具 Schema、路径、补丁、策略、进程、Git、Provider wire、Runtime、CompletionGate、报告与 CLI。
- 离线 E2E：真实六工具 + Scripted Provider + 临时 Git 仓库，覆盖成功修复、越界、补丁冲突、审批拒绝和预算耗尽。
- 真实 smoke：显式 profile 与凭据门控，默认跳过且不属于离线发布阻塞项。

安全设计和限制以 `README.md` 与 `project_rule.md` 为准；详细需求、设计、任务和验收以 `specs/mini-codex-v0.1/` 为事实源。
