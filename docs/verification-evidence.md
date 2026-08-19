# Mini Coder V0.1 Verification Evidence

验证日期：2026-08-17 至 2026-08-19（Asia/Shanghai）
环境：Windows 11 amd64；Oracle JDK 21.0.11；Maven Wrapper 3.9.14；Git 2.45.1；ripgrep 15.2.0。

## Approval and traceability

- 用户先明确确认原四份规格并授权按 `TASK-001`–`TASK-016` 实施；产品改名规格更新后，又明确确认 `REQ-017` 并授权 `TASK-017`；2026-08-18 再次明确确认 `REQ-018`、`REQ-019` 并授权 `TASK-018`、`TASK-019`；2026-08-19 明确确认包含 `REQ-020` 的当前四份规格，接受 `ASM-003`、`OQ-001` 默认决策并授权 `TASK-020`、`TASK-021`；同日再次明确确认当前四份规格，接受 `ASM-009`、`ASM-010`、`OQ-005` 和 `REQ-021` 默认决策并授权 `TASK-022`、`TASK-023`。
- 最新规格 ID 只读检查结果：`requirements.md` 包含 21 REQ/62 AC，`design.md` 映射 21 REQ，`tasks.md` 包含 23 TASK 并覆盖 21 REQ/62 AC，`check_list.md` 包含 38 CHECK 并覆盖 62 AC；ID 无重复，需求到设计/任务/检查映射无缺失。
- `project_rule.md`、`AGENTS.md`、README 和 CLI help 均声明：策略控制不是 OS sandbox，只能用于受信任仓库或隔离副本；V0.1 支持 OpenAI、DeepSeek 和 scripted Provider，其他真实 Provider、DeepSeek 专用推理参数、多 Agent、RAG、MCP、自动 Git 写入和强沙箱仍在范围外。

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

## Pre-rename history

初始实现和第一次品牌迁移的原始制品名、哈希、CLI runId、状态及当时工作树记录保留在 Git commit `12ae40c` 的本证据文档中。当前文档不将这些历史制品重新标注为现行名称；它们仅通过 Git 历史追溯，不代表当前发布制品。

## TASK-017 Mini Coder rename evidence

- 局部 CLI 测试：`.\mvnw.cmd -q -Dtest=CliTest test`，退出码 0；新增断言覆盖 `Usage: mini-coder`、示例命令和 `Mini Coder 0.1.0` 版本文本。
- 改名后 `.\mvnw.cmd -q clean verify` 明确退出 0；随后两次 `.\mvnw.cmd -q -Poffline-e2e verify` 均退出 0；证据文档更新后再次执行最终 `.\mvnw.cmd -q clean verify`，仍明确退出 0。最终默认 Surefire 报告为 23 suites、53 tests、0 failure、0 error、3 skipped；启用离线 E2E 时为 1 skipped（仅真实 OpenAI smoke）。
- `TASK-017` 所在 revision 的发布制品：
  - `target/mini-coder-0.1.0-SNAPSHOT-all.jar`，SHA-256 `0D74190AD6DC6707503BEC93D9FEFCC921A96BB852858EE13F183D6856E530C6`。
  - `target/mini-coder-0.1.0-SNAPSHOT-dist.zip`，SHA-256 `8D95803A7F2DFED115D2C2C4EB2668FFB40E5C2192FC1A46FB879684A259AC20`。
- ZIP 根目录为 `mini-coder/`，其中可执行 JAR 为 `mini-coder.jar`；干净构建后的旧名发布制品数量为 0；fat JAR 中 `org/springframework/` 类数量为 0。
- 在 `target\发布 验证` 目录运行 `mini-coder.jar --help` 和 `--version` 均退出 0；帮助首行为 `Usage: mini-coder`，示例使用 `mini-coder`，版本输出为 `Mini Coder 0.1.0`。
- 同一 Unicode/空格目录使用最终构建制品运行 scripted CLI，进程与 JSON 均报告 `SUCCEEDED_WITH_WARNINGS`/10，runId 为 `e03f8032-c085-419f-893d-1d5cd077c22f`，workspace revision 为 0。
- `TASK-017` 所在 revision 的定向扫描允许当时尚未迁移的内部 namespace 与规格目录；其原始命中和判断保留在 Git 历史。当前迁移使用 `REQ-018`、`REQ-019` 的零例外标准重新验收。

## TASK-018 and TASK-019 namespace/document migration evidence

- 用户于 2026-08-18 明确确认包含 `REQ-018`、`REQ-019` 的四份当前规格，接受默认决策，并授权实施、提交和推送 `origin/main`。
- Java 目录、全部 `package`/`import`/完全限定类名已迁移为 `dev.minicoder`；POM 坐标为 `dev.minicoder:mini-coder`，shade `mainClass` 与 fat JAR manifest `Main-Class` 均为 `dev.minicoder.cli.Main`。
- 规格事实源仅存在于 `specs/mini-coder-v0.1/`；废弃命名的当前内容扫描和路径扫描均为 0；4/4 事实源文件存在；检查到 1 个 Markdown 相对链接，缺失目标为 0。
- 规格一致性检查为 19 REQ、52 AC、19 TASK、31 CHECK，定义无重复；展开 ID 区间后，需求到设计、需求/验收到任务、验收到检查均无缺失。
- Java 21 `.\mvnw.cmd -q clean verify` 退出 0：23 Surefire suites、53 tests、0 failure、0 error、3 skipped；连续两次 `.\mvnw.cmd -q -Poffline-e2e verify` 均退出 0，无真实 API key 或公网依赖。
- ZIP 根目录为 `mini-coder/`，包含 `mini-coder.jar`、README、ARCHITECTURE、CHANGELOG 与 Maven Wrapper；fat JAR 中 `org/springframework/` class 数为 0。
- 最终发布制品：
  - `target/mini-coder-0.1.0-SNAPSHOT-all.jar`，SHA-256 `E205F3D7FF87E00E9DE8150B8C0479FCDBE4BD4A117AF124C95E9C09C6FE68AE`。
  - `target/mini-coder-0.1.0-SNAPSHOT-dist.zip`，SHA-256 `D9D3401541E661C2711A24E5C01FFA5916FFDC922B5A73959F6EAE29D9EDD6BF`。
- 在 `target\发布 验证` 中运行 fat JAR `--help`、`--version` 均退出 0；帮助首行为 `Usage: mini-coder`，版本为 `Mini Coder 0.1.0`。
- 同一 Unicode/空格目录使用最终制品运行 scripted CLI，进程与 JSON 均为 `SUCCEEDED_WITH_WARNINGS`/10，runId `e5c4ad60-999d-4d75-96be-23c09c736c8f`，workspace revision 为 0；该警告准确反映本次只读 scripted 任务未修改工作区。

## TASK-020 and TASK-021 DeepSeek Provider evidence

- 用户于 2026-08-19 明确批准包含 `REQ-020` 的当前四份规格，接受 `ASM-003`、`OQ-001` 默认决策并授权 `TASK-020`、`TASK-021`。成功验证命令均在当前 PowerShell 会话显式使用 `D:\Program Files\Java\jdk-21.0.11`；系统默认 Java 8 不作为项目验证环境。
- 新增 `ProviderConfig` 和独立 `llm/deepseek/DeepSeekResponsesProvider`；`AgentRuntime`、`ToolRegistry`、`CompletionGate` 没有 DeepSeek wire 类型或供应商控制流分支。DeepSeek key 只读取 `DEEPSEEK_API_KEY`，模型/Base URL 按批准优先级解析。
- 定向命令 `.\mvnw.cmd -q -Dtest='*RunConfigTest,*CliTest,*DeepSeekProviderContractTest,*RedactorTest,*LlmProviderContractTest' test` 退出 0。`DeepSeekProviderContractTest` 的 7 个测试覆盖默认/custom endpoint、Bearer、最终文本、两个有序 function call、无状态两轮回放、用量、400/401/402/403/422/429/500/503、一次有界重试、超时、畸形响应以及 30 轮/512 items/1 MiB cursor 上限。
- 最终 `.\mvnw.cmd -q clean verify` 退出 0：25 Surefire suites、65 tests、0 failure、0 error、4 skipped。连续两次 `.\mvnw.cmd -q -Poffline-e2e verify` 均退出 0，无真实 Provider 密钥或公网依赖。
- JDK 21 下 Unicode/空格目录中的 fat JAR `--help`、`--version` 均退出 0；帮助列出 `openai`、`deepseek`、`scripted`、DeepSeek 三个环境变量、示例和非沙箱警告。scripted CLI 退出预期 10 并生成 JSON 报告。
- 扫描 Surefire、packaged help/error、scripted terminal/error 和 JSON 报告，两种唯一 `sk-...` 测试 token 的输出文件命中数为 0。DeepSeek 主源码中的 `previous_response_id` 和核心领域包中的 DeepSeek Adapter 类型引用均为 0。
- 10 个 Markdown 文件只包含 1 个相对链接且目标存在；旧 DeepSeek 范围表述命中 0。fat JAR manifest 为 `dev.minicoder.cli.Main`，含 1 个 DeepSeek Adapter class、0 个 Spring class；ZIP 含 `mini-coder.jar`、README、ARCHITECTURE、CHANGELOG 和 Maven Wrapper。
- 最终发布制品：
  - `target/mini-coder-0.1.0-SNAPSHOT-all.jar`，SHA-256 `0FF07F82413A58C07493E8033CD5B987F6B2218375F19461371BE079B72CAD3A`。
  - `target/mini-coder-0.1.0-SNAPSHOT-dist.zip`，SHA-256 `618E9FEB556D95DC0E9CA9B61E8578D09DCCABB96B5B84FD592B8FA5D9A743FF`。
- `deepseek-smoke` profile/test 默认跳过。2026-08-19 用户提供 DeepSeek 凭据并明确要求立即接入后，以进程级 `DEEPSEEK_MODEL=deepseek-v4-flash` 执行 `.\mvnw.cmd -q -Pdeepseek-smoke verify`，退出 0；`DeepSeekSmokeTest` 为 1 test、0 failure、0 error、0 skipped，真实完成一次 Responses function call 与 tool result 续接。测试只使用内存 fixture，未执行真实工作区工具；全部 Surefire 报告对用户环境中 DeepSeek key 原文的文件命中数为 0。`CHECK-035` 已通过。
- `.gitignore` 中 `.ai-code-tracker.json` 是实施开始前出现的用户改动；本任务完整保留，未将其计入 DeepSeek 实现。

## TASK-022 and TASK-023 Chinese source documentation evidence

- 用户于 2026-08-19 明确批准包含 `REQ-021` 的当前四份规格，接受 `ASM-009`、`ASM-010`、`OQ-005` 默认决策并授权 `TASK-022`、`TASK-023`。
- 独立源码枚举得到 `src/main/java` 48 个、`src/test/java` 28 个、合计 76 个 Java 文件。76/76 个文件的主要顶层类型前均有中文 Javadoc，且精确作者记录 `@author Self David (dsfgis@gmail.com)` 每文件恰好一条；主代码局部扫描为 48/48、零缺失、零重复、零顺序错误。
- 新增离线 `SourceDocumentationTest`，动态扫描主/测试源码根，校验中文 Javadoc、作者格式/唯一性和注释与主要顶层声明的直接关联。Java 21 下 `\.\mvnw.cmd -q -Dtest=SourceDocumentationTest test` 退出 0，`\.\mvnw.cmd -q -DskipTests compile` 退出 0。
- 重点审阅 AgentRuntime/CompletionGate、OpenAI/DeepSeek Adapter、WorkspaceGuard、ApplyPatch、ProcessRunner、CommandPolicy、Redactor 及复杂测试 fixture，共有 25 处中文意图注释，用于说明边界、原因或不变量。扫描全部 Java 源码与 Surefire 报告，用户 DeepSeek key 原文命中文件数为 0；作者邮箱仅以批准的作者元数据出现。
- 75 个既有受版本控制 Java 文件相对 HEAD 去除块注释、行注释与空行后，非注释差异文件数为 0；新增 Java 文件仅为文档覆盖测试。依赖、构建配置、公开契约和产品执行逻辑均未改变，实施前已有的 `.gitignore` 用户改动保持原样。
- Oracle JDK 21.0.11 下最终 `\.\mvnw.cmd -q clean verify` 退出 0：26 个 Surefire suite、66 tests、0 failure、0 error、2 skipped；连续两次 `\.\mvnw.cmd -q -Poffline-e2e verify` 均退出 0。`git diff --check` 退出 0。
- 本次注释实施后的发布制品：
  - `target/mini-coder-0.1.0-SNAPSHOT-all.jar`，SHA-256 `4C8589998106339DB30953274011F69B62B77DE55DF94FFF3B147404914266E0`。
  - `target/mini-coder-0.1.0-SNAPSHOT-dist.zip`，SHA-256 `DC77E9E992F6F89446A61467BF826534AB613CCAB1DD707988ADA05B94417A20`。

## Checklist evidence map

| CHECK | Evidence |
|---|---|
| 001–004 | 五轮规格阶段批准；最新 `ASM-009`/`ASM-010`/`OQ-005`/`REQ-021` 默认决策接受；21 REQ/62 AC 追踪与安全边界审阅 |
| 005–006 | 65-test final clean verify；三 Provider packaged help；scripted CLI/JSON；CLI/config tests |
| 007–009 | Workspace/PathResolver/ChangeAttribution tests |
| 010–013 | Provider contract/OpenAI/DeepSeek adapters、错误矩阵、AgentRuntime/CompletionGate tests |
| 014–020 | ToolRegistry、file、patch、policy、approval、process、Git tests |
| 021–024 | revision verification matrix、RunReport/CLI、OpenAI/DeepSeek Redactor、retry/approval/truncation events |
| 025–026 | DeepSeek 变更后连续两次 offline-e2e；真实 Spring Boot fixture 闭环 |
| 027 | N/A：本轮未使用真实 OpenAI 凭据，未获得该可选网络 smoke 的明确执行授权 |
| 028 | 65-test final clean verify、artifact hash/content、Unicode/space path packaged CLI、三 Provider 文档/范围审计 |
| 029 | Mini Coder CLI/版本测试、新名 JAR/ZIP/包内 JAR、旧制品为 0、旧名审计与 Unicode/space path packaged CLI |
| 030 | 规格目录 4/4、文档/路径零残留、Markdown 相对链接 1/1、Git 历史保真审阅 |
| 031 | `dev.minicoder` 路径/声明、Maven 坐标、shade/manifest 入口、53 tests、双 E2E、packaged CLI 与制品哈希 |
| 032 | DeepSeek 配置优先级、key 隔离、启动前失败、packaged help 与输出秘密扫描 |
| 033 | DeepSeek endpoint/Bearer、无状态两轮回放、顺序/用量、cursor 三类上限与协议失败 |
| 034 | DeepSeek 错误矩阵、65 tests、双 E2E、packaged CLI、文档/链接/制品审计 |
| 035 | 获得单独授权后的真实 DeepSeek V4 Flash Responses function call/续接，1 test、0 failure/error/skip，Surefire 中真实 key 原文命中 0 |
| 036–038 | 76/76 Java 中文 Javadoc 与唯一作者记录；25 处意图注释；SourceDocumentationTest、66-test clean verify、双 offline-e2e、非注释差异与秘密扫描 |
