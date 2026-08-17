# Mini Coder V0.1 Verification Evidence

验证日期：2026-08-17（Asia/Shanghai）  
环境：Windows 11 amd64；Oracle JDK 21.0.11；Maven Wrapper 3.9.14；Git 2.45.1；ripgrep 15.2.0。

## Approval and traceability

- 用户先明确确认原四份规格并授权按 `TASK-001`–`TASK-016` 实施；产品改名规格更新后，又明确确认当前四份文档、接受 `REQ-017` 默认决策，并授权按 `TASK-017` 实施及将验证后的代码推送到指定 GitHub `main` 分支。
- 最新规格 ID 只读检查结果：`requirements.md` 包含 17 REQ/45 AC，`design.md` 映射 17 REQ，`tasks.md` 包含 17 TASK 并覆盖 17 REQ/45 AC，`check_list.md` 包含 29 CHECK 并覆盖 45 AC；ID 无重复，需求到设计/任务映射无缺失。
- `project_rule.md`、`AGENTS.md`、README 和 CLI help 均声明：策略控制不是 OS sandbox，只能用于受信任仓库或隔离副本；DeepSeek、多 Agent、RAG、MCP、自动 Git 写入和强沙箱不在 V0.1 范围。

## Task-level commands

以下命令均在 `D:\study\code\agentcode` 执行，`JAVA_HOME=D:\Program Files\Java\jdk-21.0.11`，退出码均为 0：

```powershell
.\mvnw.cmd -v
.\mvnw.cmd -q -Dtest='*ContractTest,*ScriptedLlmProviderTest,*Config*Test,*DependencyPreflightTest,*Workspace*Test,*PathResolver*Test,*GitBaselineTest,*ToolRegistryTest,*ToolSchemaTest' test
.\mvnw.cmd -q -Dtest='*ListFilesToolTest,*ReadFileToolTest,*SearchCodeToolTest' test
.\mvnw.cmd -q -Dtest='*ApplyPatchToolTest,*PatchAtomicityTest,*CommandPolicyTest,*ApprovalServiceTest,*RedactorTest,*ProcessRunnerTest,*ShellToolTest,*GitDiffToolTest,*ChangeAttributionTest' test
.\mvnw.cmd -q -Dtest='*OpenAiProviderContractTest,*OpenAiWireMockTest,*AgentRuntimeTest,*RunStateMachineTest,*CompletionGateTest,*NoProgressTest,*Cli*Test,*RunReportTest,*RunEventTest' test
```

这些测试覆盖：无效/中文/空格/CRLF 工作区、junction 越界、NUL porcelain 路径、读/列举/搜索上限、二进制/非法 UTF-8/缺失 rg、多文件 patch 成功与注入式中途提交回滚、命令分类与审批替身、父子进程超时清理、未跟踪文件 diff、归属、Provider 429/500/401/协议错误/总时限、顺序工具调用、取消/迭代/无进展、验证失效与失败后修复、重试/审批/截断事件、CLI 和报告脱敏。

## Offline E2E and release build

下列命令连续执行两次，均退出 0：

```powershell
.\mvnw.cmd -q -Poffline-e2e verify
.\mvnw.cmd -q -Poffline-e2e verify
```

E2E 使用真实 test-scope Spring Boot 3.3.2 fixture；临时仓库先以 `mvn.cmd -o -q test` 稳定复现空指针，再由 Scripted Provider 驱动 `read_file -> apply_patch -> shell -> git_diff -> final text`。断言最终状态 `SUCCEEDED`、只修改 `src/main/java/fixture/UserService.java`、最终 revision 验证退出码 0、准确 diff/归属和完整固定源文件；越界、冲突、审批拒绝及预算耗尽分别得到规定终态。fixture 内部 Maven 强制 `-o`。

最终发布命令：

```powershell
.\mvnw.cmd -q clean verify
```

改名前结果：23 个 Surefire suite，52 tests，0 failure，0 error，3 skipped。默认跳过的是 2 个 profile-gated E2E test 和 1 个真实 OpenAI smoke test；默认构建未调用真实 Provider。

## Pre-rename artifact history

- `target/mini-codex-0.1.0-SNAPSHOT-all.jar`
  - SHA-256: `E27DF65F4CCCBF9925C8A790AFA2EC5AF2CB1DEFD721F17AF111250B94550A13`
- `target/mini-codex-0.1.0-SNAPSHOT-dist.zip`
  - SHA-256: `46F10E560219198B92693EDE1F806126FDFC819D0E54AAFAB78B8CBE6905BC91`
- ZIP 已审计包含 `mini-codex.jar`、README、ARCHITECTURE、CHANGELOG、Maven Wrapper；fat JAR 中 `org/springframework/` 类数量为 0。
- 在 `target\发布 验证` 工作目录运行打包 JAR `--help`：进程退出码 0，包含参数、环境变量、示例和非 OS sandbox 警告。
- 同一目录运行 packaged `--provider scripted --non-interactive --json-report scripted-report.json`：进程退出码 10；控制台与 JSON 均为 `SUCCEEDED_WITH_WARNINGS`/10，runId 一致，不要求 `OPENAI_API_KEY`，workspace revision 为 0。
- `git status --short` 只显示本次从空仓库创建的预期未跟踪项目文件；未执行 commit、reset、clean、checkout 或 push。

以上条目是 `REQ-017` 实施前的历史证据，仅用于保留证据链，不代表当前发布制品。

## TASK-017 Mini Coder rename evidence

- 局部 CLI 测试：`.\mvnw.cmd -q -Dtest=CliTest test`，退出码 0；新增断言覆盖 `Usage: mini-coder`、示例命令和 `Mini Coder 0.1.0` 版本文本。
- 改名后 `.\mvnw.cmd -q clean verify` 明确退出 0；随后两次 `.\mvnw.cmd -q -Poffline-e2e verify` 均退出 0；证据文档更新后再次执行最终 `.\mvnw.cmd -q clean verify`，仍明确退出 0。最终默认 Surefire 报告为 23 suites、53 tests、0 failure、0 error、3 skipped；启用离线 E2E 时为 1 skipped（仅真实 OpenAI smoke）。
- 当前发布制品：
  - `target/mini-coder-0.1.0-SNAPSHOT-all.jar`，SHA-256 `0D74190AD6DC6707503BEC93D9FEFCC921A96BB852858EE13F183D6856E530C6`。
  - `target/mini-coder-0.1.0-SNAPSHOT-dist.zip`，SHA-256 `8D95803A7F2DFED115D2C2C4EB2668FFB40E5C2192FC1A46FB879684A259AC20`。
- ZIP 根目录为 `mini-coder/`，其中可执行 JAR 为 `mini-coder.jar`；干净构建后的旧名发布制品数量为 0；fat JAR 中 `org/springframework/` 类数量为 0。
- 在 `target\发布 验证` 目录运行 `mini-coder.jar --help` 和 `--version` 均退出 0；帮助首行为 `Usage: mini-coder`，示例使用 `mini-coder`，版本输出为 `Mini Coder 0.1.0`。
- 同一 Unicode/空格目录使用最终构建制品运行 scripted CLI，进程与 JSON 均报告 `SUCCEEDED_WITH_WARNINGS`/10，runId 为 `e03f8032-c085-419f-893d-1d5cd077c22f`，workspace revision 为 0。
- 旧名定向扫描结果仅包含批准保留的 `dev.minicodex` package、`specs/mini-codex-v0.1/` 目录、`REQ-017` 改名说明和本节历史证据；没有旧对外品牌、CLI usage name 或当前发布制品引用。

## Checklist evidence map

| CHECK | Evidence |
|---|---|
| 001–004 | 两次四文档整体批准、默认决策接受、17 REQ/45 AC 最新规格追踪与安全边界审阅 |
| 005–006 | final clean verify；packaged help/scripted CLI；CLI tests |
| 007–009 | Workspace/PathResolver/ChangeAttribution tests |
| 010–013 | Provider contract/OpenAI adapter/AgentRuntime/CompletionGate tests |
| 014–020 | ToolRegistry、file、patch、policy、approval、process、Git tests |
| 021–024 | revision verification matrix、RunReport/CLI、Redactor、retry/approval/truncation events |
| 025–026 | 改名前及 `TASK-017` 后各两次 offline-e2e；真实 Spring Boot fixture 闭环 |
| 027 | N/A：本轮未使用真实 OpenAI 凭据，未获得该可选网络 smoke 的明确执行授权 |
| 028 | final clean verify、artifact hash/content、Unicode/space path packaged CLI、文档/范围审计 |
| 029 | Mini Coder CLI/版本测试、新名 JAR/ZIP/包内 JAR、旧制品为 0、旧名审计与 Unicode/space path packaged CLI |
