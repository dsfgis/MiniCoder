# Mini Coder V0.1 验证清单

## 1. 使用说明

- 本清单描述“需要什么证据才能证明结果正确”，不替代 `tasks.md` 的实施步骤。
- 所有检查初始为未完成；只有实际执行验证并保存证据后才能勾选。
- 若需求、设计或任务发生实质变更，受影响检查应恢复为未完成，并重新确认四份文档。
- 命令以 Windows PowerShell 为基线；CI 使用等价命令时应在证据中记录环境与差异。

## 2. 文档就绪检查（实施前）

- [x] `CHECK-001` **事实、假设与开放问题已审阅**
  - **引用**：`requirements.md` 2；`design.md` 16；全部 `ASM-*`、`OQ-*`、`DD-OPEN-*`。
  - **关联任务**：实施前门禁。
  - **验证方法**：人工逐项审阅，并记录每个待确认假设/开放问题的“接受、修改或延期”决定。
  - **预期结果**：不存在被悄然当成事实的假设；所有会影响 V0.1 实现的默认选择都有明确决定。
  - **证据**：2026-08-19 用户明确确认当前四份规格文档，接受 `ASM-009`、`ASM-010`、`OQ-005` 和 `REQ-021` 默认决策，并授权按 `TASK-022`、`TASK-023` 实施；`FACT-010` 作为仓库源码统计事实已通过独立枚举复核。

- [x] `CHECK-002` **需求可测试性与逐级追踪完整**
  - **引用**：`REQ-001`–`REQ-021`；`AC-001`–`AC-062`；`design.md` 17；`tasks.md` 4。
  - **关联任务**：文档一致性检查。
  - **验证方法**：运行规格 ID 校验脚本/人工交叉表，确认每个 REQ 有 AC，每个 REQ 有设计映射，每个 REQ/AC 至少映射到一个 TASK 和一个 CHECK。
  - **预期结果**：无缺失 ID、重复 ID、孤立设计、孤立任务或孤立验收标准。
  - **证据**：2026-08-19 重新执行规格 ID、区间展开和逐级追踪检查：21 REQ、62 AC、23 TASK、38 CHECK；四类 ID 均连续且无重复；所有 REQ 均有设计和任务映射，`AC-001`–`AC-062` 均有任务与检查项覆盖。

- [x] `CHECK-003` **V0.1 安全边界与风险接受已确认**
  - **引用**：`REQ-008`、`REQ-010`、`REQ-013`；`AC-022`、`AC-025`–`AC-027`、`AC-034`；`design.md` 8、15.4。
  - **关联任务**：`TASK-008`、`TASK-009`、`TASK-016`。
  - **验证方法**：人工确认“基础策略不是 OS 沙箱”“仅在受信任/隔离副本运行”“破坏性命令始终拒绝”的表述与产品定位。
  - **预期结果**：用户明确接受 V0.1 不提供恶意代码隔离；否则规格先改为包含沙箱再实施。
  - **证据**：用户接受默认安全边界；README、CLI help 与规则文件审计见 `docs/verification-evidence.md`。

- [x] `CHECK-004` **四份文档整体批准**
  - **引用**：`requirements.md`、`design.md`、`tasks.md`、`check_list.md` 当前版本。
  - **关联任务**：`TASK-001` 的前置条件。
  - **验证方法**：用户明确确认四份当前文档，而不是只确认其中一份或原始想法。
  - **预期结果**：存在清晰、可追溯的整体批准；批准后才能修改产品代码、测试、依赖或配置。
  - **证据**：2026-08-19 用户明确使用“我确认当前四份规格文档”整体批准包含 `REQ-021`、`TASK-022`、`TASK-023` 和 `CHECK-036`–`CHECK-038` 的当前版本，并授权实施。

## 3. 构建、CLI 与配置

- [x] `CHECK-005` **Java 21 工程可重复构建且测试不依赖真实密钥**
  - **引用**：`REQ-015`、`REQ-016`、`REQ-020`；`AC-037`、`AC-057`；`design.md` 12、13。
  - **关联任务**：`TASK-001`、`TASK-014`、`TASK-016`、`TASK-021`。
  - **验证方法**：在未设置 Provider 密钥的干净检出中执行 `.\mvnw.cmd -q clean verify`。
  - **预期结果**：退出码 0；全部默认测试通过；构建日志不尝试真实 Provider 网络调用。
  - **证据**：2026-08-19 显式使用 Oracle JDK 21.0.11 执行最终 `clean verify`，退出 0；25 suites、65 tests、0 failure、0 error、4 skipped；默认跳过两项离线 E2E 场景和两个真实 Provider smoke，未使用真实密钥或公网。

- [x] `CHECK-006` **CLI 启动、runId、缺参失败与帮助信息正确**
  - **引用**：`REQ-001`、`REQ-020`；`AC-001`–`AC-003`、`AC-053`、`AC-054`、`AC-058`；`design.md` 5.1、5.4、10。
  - **关联任务**：`TASK-003`、`TASK-013`、`TASK-016`、`TASK-020`、`TASK-021`。
  - **验证方法**：运行 CLI 的合法 Fake Provider 示例、缺任务/模型/Provider/密钥组合以及 `--help`。
  - **预期结果**：合法运行在工具前显示唯一 runId；缺参在工具前以非零码失败；帮助列出三个 Provider、各自环境变量、安全边界和示例；密钥不接受明文 CLI 参数。
  - **证据**：`CliTest` 覆盖未知 Provider、DeepSeek 缺 key/模型和跨 Provider key 隔离；JDK 21 下 Unicode/空格目录的 fat JAR help/version 均退出 0，scripted 运行退出预期 10 并生成 JSON；help 列出三种 Provider、DeepSeek 三个环境变量、示例和非沙箱警告。

## 4. 工作区与路径边界

- [x] `CHECK-007` **无效工作区在 LLM 调用前被拒绝**
  - **引用**：`REQ-002`；`AC-004`；`design.md` 5.1、7.1。
  - **关联任务**：`TASK-004`、`TASK-014`。
  - **验证方法**：执行 `*Workspace*Test`，覆盖不存在路径、普通文件、非 Git 目录和不可访问目录。
  - **预期结果**：全部返回配置/工作区错误；Fake Provider 调用计数保持 0。
  - **证据**：`WorkspaceTest` 覆盖缺失路径、普通文件、非 Git 目录；CLI 配置失败发生在 Runtime/Provider 创建运行前。

- [x] `CHECK-008` **路径穿越、链接越界与 Windows 特殊路径被正确处理**
  - **引用**：`REQ-002`、`REQ-016`；`AC-005`、`AC-041`；`design.md` 7.1、13。
  - **关联任务**：`TASK-004`、`TASK-006`、`TASK-009`、`TASK-016`。
  - **验证方法**：运行 `*PathResolver*Test,*Workspace*Test`，并在含空格、中文、CRLF 的临时路径执行文件工具和安全命令。
  - **预期结果**：任何真实路径越界均为 `POLICY_DENIED`；合法 Windows 路径和内容无损处理。
  - **证据**：`PathResolverTest` junction 越界、Workspace/FileTools 中文空格 CRLF 测试与 `target\发布 验证` packaged CLI。

- [x] `CHECK-009` **运行前基线与重叠修改归属准确**
  - **引用**：`REQ-002`、`REQ-009`；`AC-006`、`AC-024`；`design.md` 7.2。
  - **关联任务**：`TASK-004`、`TASK-010`、`TASK-014`。
  - **验证方法**：在临时仓库预改文件，再由 Agent 修改相同/不同文件，执行 `*GitBaselineTest,*ChangeAttributionTest`。
  - **预期结果**：报告区分 PREEXISTING、AGENT_CREATED/MODIFIED 和 OVERLAPS_PREEXISTING_CHANGE，不把完整重叠 diff 归因给 Agent。
  - **证据**：`ChangeAttributionTest`/`GitDiffToolTest` 覆盖 PREEXISTING、AGENT_CREATED/MODIFIED、OVERLAPS。

## 5. Provider 与 Agent Loop

- [x] `CHECK-010` **Provider 工具调用与续接关联无损**
  - **引用**：`REQ-003`、`REQ-016`、`REQ-020`；`AC-007`、`AC-039`、`AC-055`；`design.md` 5.3、5.4、6。
  - **关联任务**：`TASK-002`、`TASK-011`、`TASK-012`、`TASK-015`、`TASK-020`。
  - **验证方法**：运行 `*LlmProviderContractTest,*OpenAiProviderContractTest,*DeepSeekProviderContractTest`，覆盖单/多工具调用、调用 ID、Provider cursor、DeepSeek 无状态回放和 ToolResult 回传。
  - **预期结果**：名称、参数、`callId`、顺序和续接状态无损；Fake/OpenAI/DeepSeek Provider 通过同一领域合约，AgentRuntime 无供应商分支。
  - **证据**：规定的 Provider 定向选择器退出 0；`DeepSeekProviderContractTest` 断言最终文本、两个有序 function call、call ID/参数/用量、两轮 tool result 回放，且 `AgentRuntime` 无 DeepSeek 类型引用。

- [x] `CHECK-011` **最终文本触发完成门且工具顺序确定**
  - **引用**：`REQ-003`、`REQ-004`；`AC-008`、`AC-010`；`design.md` 5.2、9.2。
  - **关联任务**：`TASK-012`、`TASK-014`。
  - **验证方法**：用 ScriptedLlmProvider 返回多个有序工具调用后再返回最终文本，执行 `*AgentRuntimeTest`。
  - **预期结果**：工具按响应顺序执行；最终文本不再触发 Provider 调用，但必须经过 CompletionGate；终态符合验证证据。
  - **证据**：`AgentRuntimeTest` 断言 callId 顺序与 CompletionGate；Spring E2E 事件顺序 c1→c4。

- [x] `CHECK-012` **Provider 瞬时错误有界重试、永久错误立即失败**
  - **引用**：`REQ-003`、`REQ-020`；`AC-009`、`AC-056`；`design.md` 10。
  - **关联任务**：`TASK-011`、`TASK-012`、`TASK-015`、`TASK-020`。
  - **验证方法**：分别模拟 OpenAI/DeepSeek 的 400/401/402/422/429/5xx、连接失败、总时限和畸形响应，运行对应 Provider contract tests。
  - **预期结果**：仅瞬时错误在预算内退避重试；配置/401/402/协议错误不重试；总时限不被绕过；错误使用正确供应商名称且输出已脱敏。
  - **证据**：DeepSeek mock 合同覆盖 400/401/402/403/422/429/500/503、请求超时、畸形 JSON/arguments 和未知 item；429/5xx 标记可重试，永久错误不重试，429→成功只重试一次，错误正文中的测试 key 被脱敏。

- [x] `CHECK-013` **迭代、总时限、取消与无进展均能停止循环**
  - **引用**：`REQ-004`；`AC-011`、`AC-012`；`design.md` 5.2、9、10。
  - **关联任务**：`TASK-012`、`TASK-014`。
  - **验证方法**：运行 `*RunStateMachineTest,*NoProgressTest,*AgentRuntimeTest` 的迭代上限、时间上限、取消和重复调用场景。
  - **预期结果**：分别得到 `LIMIT_REACHED`、`CANCELLED` 或 `NO_PROGRESS`；停止后没有新 Provider/工具调用；报告包含最后一步。
  - **证据**：`AgentRuntimeTest` 的 LIMIT_REACHED/CANCELLED/NO_PROGRESS 与脚本剩余步骤断言。

## 6. 工具契约与本地工具

- [x] `CHECK-014` **ToolRegistry 拒绝未知/非法调用且结果契约统一**
  - **引用**：`REQ-005`、`REQ-016`；`AC-013`、`AC-014`、`AC-040`；`design.md` 6。
  - **关联任务**：`TASK-002`、`TASK-005`。
  - **验证方法**：运行 `*ToolRegistryTest,*ToolSchemaTest,*ContractTest`，加入测试工具并传入未知名称、非法 JSON、缺失字段和截断结果。
  - **预期结果**：非法调用无执行器副作用；所有状态可序列化；新增工具不修改 AgentRuntime。
  - **证据**：`ToolRegistryTest`/Provider contract tests；未知、缺字段、额外字段均无执行器调用。

- [x] `CHECK-015` **List/Read/Search 的成功与边界场景完整**
  - **引用**：`REQ-006`；`AC-015`、`AC-016`、`AC-017`；`design.md` 6.1、7.1。
  - **关联任务**：`TASK-006`。
  - **验证方法**：运行 `.\mvnw.cmd -q -Dtest=*ListFilesToolTest,*ReadFileToolTest,*SearchCodeToolTest test`。
  - **预期结果**：路径稳定排序；读取带行号；搜索返回文件/行/片段；无匹配、二进制、编码、缺 rg、超限和越界状态可区分。
  - **证据**：规定的 TASK-006 选择器退出 0；覆盖上限、二进制、非法 UTF-8、无匹配、缺 rg 与越界。

- [x] `CHECK-016` **Apply Patch 成功原子、失败无部分修改**
  - **引用**：`REQ-007`；`AC-018`、`AC-019`；`design.md` 7.3、10。
  - **关联任务**：`TASK-007`、`TASK-014`。
  - **验证方法**：运行 `.\mvnw.cmd -q -Dtest=*ApplyPatchToolTest,*PatchAtomicityTest test`，比较调用前后文件哈希和 revision。
  - **预期结果**：合法多文件补丁一次性生效；任一非法/冲突/不可写/模拟提交失败场景所有哈希不变；成功时 revision 仅增加一次。
  - **证据**：`ApplyPatchToolTest`/`PatchAtomicityTest`；两文件成功仅一 revision，中途提交注入失败回滚内容不变。

- [x] `CHECK-017` **允许的 Shell 命令执行信息完整**
  - **引用**：`REQ-008`、`REQ-010`；`AC-020`、`AC-025`；`design.md` 8.1、8.2。
  - **关联任务**：`TASK-008`、`TASK-009`。
  - **验证方法**：在临时仓库执行受控的成功/非零退出命令和安全 Git/测试命令，运行 `*ProcessRunnerTest,*ShellToolTest`。
  - **预期结果**：工作目录固定为仓库根；参数不经隐式 Shell；stdout/stderr、退出码、耗时和截断字段准确；安全命令无需审批。
  - **证据**：`ProcessRunnerTest`/`ShellToolTest` 覆盖双流、非零退出、截断、根目录及参数化启动。

- [x] `CHECK-018` **Shell 超时清理且策略拒绝不启动进程**
  - **引用**：`REQ-008`、`REQ-010`；`AC-021`、`AC-022`、`AC-027`；`design.md` 8、10。
  - **关联任务**：`TASK-008`、`TASK-009`、`TASK-014`。
  - **验证方法**：运行长生命周期父子进程测试，以及删除仓库、覆写历史、越界、凭据访问和未批准命令测试。
  - **预期结果**：超时后父子进程均终止并返回 `TIMEOUT`；拒绝/未审批命令进程启动计数为 0；破坏性命令始终拒绝。
  - **证据**：父子 PowerShell 超时 `processTreeCleaned=true`；denied/explicit destructive shell 的 runner 调用计数保持不变。

- [x] `CHECK-019` **外部副作用命令必须显式审批**
  - **引用**：`REQ-010`；`AC-026`；`design.md` 8.2。
  - **关联任务**：`TASK-008`、`TASK-009`、`TASK-014`。
  - **验证方法**：模拟交互同意/拒绝与非交互模式，覆盖网络、push、publish 和安装命令；使用假的 ProcessRunner 防止真实外部写入。
  - **预期结果**：未批准/非交互均不执行；批准路径只执行测试替身；审批日志有原因但无秘密。
  - **证据**：`ApprovalServiceTest` 以 FakeRunner 覆盖批准/拒绝，记录 requested/resolved，拒绝路径零新增进程。

- [x] `CHECK-020` **Git Diff 只读且准确报告本次变化**
  - **引用**：`REQ-009`；`AC-023`、`AC-024`；`design.md` 7.2、14。
  - **关联任务**：`TASK-004`、`TASK-010`、`TASK-014`。
  - **验证方法**：运行 `*GitDiffToolTest,*ChangeAttributionTest`，审计执行过的 Git 子命令。
  - **预期结果**：diff、统计、未跟踪和归属正确；只出现允许的 Git 只读命令，不出现 commit/reset/clean/checkout/push。
  - **证据**：NUL status、tracked/untracked diff/stat/归属测试；产品源码 Git 调用审计仅含只读子命令。

## 7. 验证闭环与报告

- [x] `CHECK-021` **最后修改后的成功验证是完整成功必要条件**
  - **引用**：`REQ-011`；`AC-028`、`AC-029`、`AC-030`；`design.md` 9.2。
  - **关联任务**：`TASK-007`、`TASK-012`、`TASK-014`。
  - **验证方法**：运行 CompletionGate 场景：修改后成功、修改后失败再修复、验证后再次修改、无验证、用户指定验证失败。
  - **预期结果**：只有最后 revision 上相关验证成功才能 `SUCCEEDED`；失败且有预算会继续；缺失/过期验证不得被模型文本提升为完整成功。
  - **证据**：`CompletionGateTest` 与 `AgentRuntimeTest` 覆盖当前/过期/失败/指定命令及失败→修复→成功。

- [x] `CHECK-022` **终端/JSON 报告事实一致且错误码稳定**
  - **引用**：`REQ-012`；`AC-031`、`AC-032`；`design.md` 9、11.2。
  - **关联任务**：`TASK-003`、`TASK-010`、`TASK-013`、`TASK-016`。
  - **验证方法**：对成功、带警告、取消、策略拒绝、配置、Provider、工具和预算终态运行 CLI contract tests，对比终端解析结果、JSON 和退出码表。
  - **预期结果**：状态、原因、文件、验证、警告、用量和 runId 一致；错误类别拥有文档化稳定代码；报告不含秘密。
  - **证据**：`RunReportTest`、`CliTest` 与 packaged scripted report；状态、runId、退出码和用量一致。

- [x] `CHECK-023` **秘密脱敏与安全警告覆盖所有输出边界**
  - **引用**：`REQ-013`、`REQ-020`；`AC-033`、`AC-034`、`AC-054`；`design.md` 8.3、11。
  - **关联任务**：`TASK-003`、`TASK-008`、`TASK-011`、`TASK-016`、`TASK-020`、`TASK-021`。
  - **验证方法**：向配置、异常、命令参数和模拟 Provider 响应注入唯一测试秘密；扫描普通/DEBUG 日志、ToolResult、终端、JSON；人工审阅 README/`--help`。
  - **预期结果**：OpenAI/DeepSeek 原始秘密零出现，密钥不跨 Provider 读取，统一显示脱敏标记；文档明确没有 OS 沙箱且不得对不可信仓库运行。
  - **证据**：`RunConfigTest`/`CliTest`/`RedactorTest`/DeepSeek 错误合同均通过；扫描 Surefire、packaged help/error、scripted terminal/error 和 JSON 报告，两种唯一 `sk-...` 测试 token 的文件命中数为 0；help/README 保留非沙箱与可信仓库警告。

- [x] `CHECK-024` **结构化事件可还原顺序并记录截断事实**
  - **引用**：`REQ-014`；`AC-035`、`AC-036`；`design.md` 11.1。
  - **关联任务**：`TASK-008`、`TASK-009`、`TASK-013`、`TASK-014`。
  - **验证方法**：运行多迭代、大输出、Provider 重试和审批场景，按 runId/iteration/toolCallId 重放事件。
  - **预期结果**：调用和状态顺序唯一可还原；事件含耗时、退出码、重试、审批、截断及原始字节计数；DEBUG 仍无密钥。
  - **证据**：Runtime/E2E/Approval tests 覆盖 runId、iteration、toolCallId、retry、approval、exit、revision 和 truncation 事件。

## 8. 综合验收与发布

- [x] `CHECK-025` **离线故障矩阵确定且可重复**
  - **引用**：`REQ-015`；`AC-037`、`AC-038`；`design.md` 12.1、12.2。
  - **关联任务**：`TASK-002`、`TASK-014`。
  - **验证方法**：连续两次执行 `.\mvnw.cmd -q -Poffline-e2e verify`，覆盖成功、验证失败、循环、越界、补丁冲突、审批拒绝和预算耗尽。
  - **预期结果**：两次均退出 0；每个场景终态、事件顺序和最终文件哈希一致；无需公网和 API key。
  - **证据**：2026-08-19 在 DeepSeek 变更工作树连续两次执行 `.\mvnw.cmd -q -Poffline-e2e verify`，两次退出码均为 0，无真实 API key 或公网依赖；此前 namespace/文档迁移证据继续保留。

- [x] `CHECK-026` **Spring Boot 缺陷修复端到端闭环成立**
  - **引用**：`REQ-002`–`REQ-012`；`AC-006`、`AC-010`、`AC-018`、`AC-020`、`AC-023`、`AC-028`、`AC-031`；`design.md` 12.3。
  - **关联任务**：`TASK-014`。
  - **验证方法**：在固定 fixture 上用 ScriptedLlmProvider 执行“定位空指针 → 读取 → 补丁 → 测试 → diff → 完成”任务。
  - **预期结果**：初始测试失败；Agent 只修改预期文件；最后 revision 的测试退出码 0；最终状态 `SUCCEEDED`；报告含准确 diff 与验证证据。
  - **证据**：2026-08-19 两次 `offline-e2e` profile 均通过；固定 Spring Boot fixture 的定位、读取、patch、验证、diff、CompletionGate 闭环在加入 DeepSeek Provider 后重复成立。

- [ ] `CHECK-027` **真实 OpenAI Provider smoke（可选、非离线发布门禁）**
  - **引用**：`REQ-003`、`REQ-013`、`REQ-015`、`REQ-016`；`AC-007`–`AC-009`、`AC-033`、`AC-039`；`design.md` 5.3、12.3。
  - **关联任务**：`TASK-015`。
  - **验证方法**：在用户明确授权、设置密钥和模型的环境执行 `.\mvnw.cmd -q -Popenai-smoke verify`。
  - **预期结果**：至少一次真实工具调用和 `call_id` 续接成功；无密钥泄露；账户/网络阻塞与产品失败分开记录。
  - **证据**：N/A；本轮未提供或授权使用真实 OpenAI 凭据/网络执行。该项为可选且非离线发布门禁。

- [x] `CHECK-028` **发布包、文档与范围边界最终审计通过**
  - **引用**：`REQ-001`、`REQ-012`、`REQ-013`、`REQ-016`、`REQ-020`；`AC-003`、`AC-031`、`AC-032`、`AC-034`、`AC-041`、`AC-057`、`AC-058`；`design.md` 13、14。
  - **关联任务**：`TASK-016`、`TASK-021`。
  - **验证方法**：执行 `.\mvnw.cmd -q clean verify`，检查发布包内容与 `git status --short`，在含空格/中文路径运行 `--help` 和离线示例，人工审阅 README 与变更记录。
  - **预期结果**：发布包可启动；源码树只含预期文件；文档列出三个 Provider 的隔离配置、退出码、示例、安全边界和范围外能力；不声称支持其他真实 Provider、多 Agent、MCP 或强沙箱。
  - **证据**：最终 clean verify 与双 E2E 退出 0；fat JAR 含 DeepSeek Adapter、无 Spring classes，manifest 入口正确；ZIP 含 `mini-coder.jar`、README、ARCHITECTURE、CHANGELOG 和 Wrapper；10 个 Markdown、1 个相对链接、0 个缺失目标；制品哈希见 `docs/verification-evidence.md`。用户已有 `.gitignore` 改动被保留且未归入本任务实现。

- [x] `CHECK-029` **Mini Coder 产品改名完整且可发布**
  - **引用**：`REQ-017`；`AC-042`–`AC-045`；`design.md` 13.1、14。
  - **关联任务**：`TASK-017`–`TASK-019`。
  - **验证方法**：在 Java 21 环境执行 `.\mvnw.cmd -q clean verify`；运行新 fat JAR 的 `--help`、`--version` 与含空格/中文路径离线示例；检查 `target` 文件名和 ZIP 清单；定向扫描旧名并人工审阅允许保留项。
  - **预期结果**：测试和离线示例退出 0；公开品牌、CLI、制品、Java namespace 及当前文档统一为 `Mini Coder` / `mini-coder` / `dev.minicoder`；ZIP 内为 `mini-coder.jar`；受版本控制树不存在废弃命名。
  - **证据**：公开品牌、CLI、制品、Java namespace、Maven 坐标和当前文档统一；受版本控制工作树的废弃命名内容/路径扫描均为 0；最终构建、双 E2E 与 packaged CLI 均通过。

- [x] `CHECK-030` **全部文档名称、规格目录和链接迁移完成**
  - **引用**：`REQ-018`；`AC-046`–`AC-048`；`design.md` 13.2、14。
  - **关联任务**：`TASK-019`。
  - **验证方法**：枚举全部 Markdown 文件与 `specs/` 目录；扫描废弃品牌、目录 slug 和 package 变体；验证相对链接和事实源路径存在；执行最终 `.\mvnw.cmd -q clean verify`；检查文档 diff 与 Git 历史可追溯性。
  - **预期结果**：只存在 `specs/mini-coder-v0.1/` 事实源目录；当前文档与路径的废弃名称命中为 0；链接全部有效；证据事实未被改写为新制品结果；构建退出 0。
  - **证据**：四份事实源仅存在于 `specs/mini-coder-v0.1/`；当前内容/路径废弃命名命中均为 0；检查到的 1 个 Markdown 相对链接目标存在，缺失目标为 0；历史原文通过 commit `12ae40c` 追溯，当前文档未重标历史制品。

- [x] `CHECK-031` **Java namespace、Maven 坐标和入口类迁移完整**
  - **引用**：`REQ-019`；`AC-049`–`AC-052`；`design.md` 13.3、14。
  - **关联任务**：`TASK-018`、`TASK-019`。
  - **验证方法**：枚举主/测试 Java 路径和声明；检查 Maven effective coordinates、shade `mainClass`、fat JAR manifest；扫描受版本控制树废弃命名；执行最终 clean verify、两次 offline-e2e 和 packaged CLI 验收。
  - **预期结果**：源码、测试、Maven 与 manifest 只使用 `dev.minicoder`；废弃命名命中 0；53 个或更多测试无失败/错误；两次 E2E 退出 0；fat JAR `--help`/`--version` 可运行。
  - **证据**：主/测试源码仅位于 `dev/minicoder` 且声明/引用统一；POM effective coordinates 为 `dev.minicoder:mini-coder`，shade 与 manifest `Main-Class` 均为 `dev.minicoder.cli.Main`；最终 53 tests 无失败/错误，双 E2E 与 fat JAR CLI 通过，废弃命名命中 0；制品哈希见 `docs/verification-evidence.md`。

- [x] `CHECK-032` **DeepSeek 配置选择、密钥隔离与启动前失败正确**
  - **引用**：`REQ-001`、`REQ-013`、`REQ-020`；`AC-002`、`AC-033`、`AC-053`、`AC-054`；`design.md` 5.1、5.4、11。
  - **关联任务**：`TASK-020`、`TASK-021`。
  - **验证方法**：运行 `*RunConfigTest,*CliTest,*RedactorTest`，用独立测试值覆盖 `--provider deepseek`、CLI/环境优先级、缺密钥/模型、OpenAI key 已设置但 DeepSeek key 缺失、Base URL 规范化和所有输出边界；不得输出真实环境变量值。
  - **预期结果**：只读取 `DEEPSEEK_API_KEY`；模型/Base URL 优先级符合矩阵；缺配置在 HTTP/工具前 `CONFIG_ERROR`；help 列出三个 Provider；测试密钥在普通/DEBUG/异常/终端/JSON 中命中 0。
  - **证据**：规定定向测试退出 0；`ProviderConfig` 断言 CLI > DeepSeek 环境变量 > 默认 Base URL，缺 DeepSeek key/模型时在工作区/HTTP 前 `CONFIG_ERROR`，设置 OpenAI key 也不回退；配置字符串和全部已验收输出无测试 key；packaged help 结果通过。

- [x] `CHECK-033` **DeepSeek Responses 工具调用与无状态多轮回放无损**
  - **引用**：`REQ-003`、`REQ-004`、`REQ-016`、`REQ-020`；`AC-007`、`AC-010`、`AC-039`、`AC-055`；`design.md` 5.2、5.4、6。
  - **关联任务**：`TASK-020`。
  - **验证方法**：使用本地 mock HTTP server 执行最终文本、单/多 function call、两轮以上 tool result 续接、reasoning/message/function_call item 回放、非法 arguments、cursor 字节/item/轮数上限；比对每轮 JSON 和领域对象，不访问公网。
  - **预期结果**：默认 Base URL 精确解析为 `https://api.deepseek.com/responses`；自定义 Base URL 保留已有路径且只追加一个 `/responses`，不自动注入 `/v1`，已包含 `/responses` 的 endpoint 输入被配置校验拒绝；Bearer header 使用测试 key；不发送无效 `previous_response_id` 续接；必要 output items 与 tool results 按序回放；call ID/名称/参数/用量无损；多个工具仍由 AgentRuntime 串行执行；超限或无法保真时明确 `PROTOCOL` 失败。
  - **证据**：`DeepSeekProviderContractTest` 7 个测试通过；断言默认/custom/编码/规范化 endpoint、Bearer、无 `previous_response_id`、message/reasoning/function calls/tool results 顺序、arguments/用量和 30 轮/512 items/1 MiB cursor 上限；非法 endpoint、arguments、未知 item 和超限均为 `PROTOCOL`。

- [x] `CHECK-034` **DeepSeek 错误矩阵、离线回归和发布文档通过**
  - **引用**：`REQ-012`–`REQ-016`、`REQ-020`；`AC-031`–`AC-039`、`AC-056`–`AC-058`；`design.md` 10–14。
  - **关联任务**：`TASK-020`、`TASK-021`。
  - **验证方法**：模拟 400/401/402/422/429/5xx、超时、超限和畸形响应；执行最终 `.\mvnw.cmd -q clean verify`、连续两次 `.\mvnw.cmd -q -Poffline-e2e verify`、packaged help/scripted CLI、文档链接/配置名/密钥扫描和发布包审计。
  - **预期结果**：错误分类、重试次数、总时限和供应商文案正确且已脱敏；默认与 E2E 无真实密钥/公网；测试数不少于 53；README/help/ARCHITECTURE/CHANGELOG/规则文件准确说明三个 Provider、配置优先级、可选 smoke 和安全边界。
  - **证据**：错误矩阵和定向测试退出 0；最终 clean verify 为 65 tests、0 failure/error；双 E2E 退出 0；packaged help/version 为 0、scripted 为预期 10；文档/规则/链接/Provider 名称/环境变量/秘密/制品审计通过，SHA-256 见 `docs/verification-evidence.md`。

- [x] `CHECK-035` **真实 DeepSeek Provider smoke（可选、非离线发布门禁）**
  - **引用**：`REQ-003`、`REQ-013`、`REQ-015`、`REQ-020`；`AC-053`、`AC-055`、`AC-057`；`design.md` 5.4、12.3。
  - **关联任务**：`TASK-021`。
  - **验证方法**：仅在用户另行明确授权、设置 `DEEPSEEK_API_KEY`、模型且允许公网时，在一次性临时 Git 仓库执行 `.\mvnw.cmd -q -Pdeepseek-smoke verify`。
  - **预期结果**：至少完成一次真实 DeepSeek Responses function call 与 tool result 续接；无密钥泄露；账户余额、权限、模型或网络阻塞与产品失败分开记录。
  - **证据**：2026-08-19 用户提供 DeepSeek 凭据并明确要求立即接入，构成本次真实公网 smoke 授权。JDK 21 下以进程级 `DEEPSEEK_MODEL=deepseek-v4-flash` 执行 `.\mvnw.cmd -q -Pdeepseek-smoke verify` 退出 0；`DeepSeekSmokeTest` 为 1 test、0 failure、0 error、0 skipped，完成一次真实 Responses function call 和 tool result 续接。测试仅使用内存 fixture，未执行真实工作区工具；扫描全部 Surefire 报告，用户环境中的 DeepSeek key 原文命中文件数为 0。

- [x] `CHECK-036` **全部 Java 文件的中文 Javadoc 与作者记录覆盖完整**
  - **引用**：`REQ-021`；`AC-059`、`AC-061`；`design.md` 13.4。
  - **关联任务**：`TASK-022`、`TASK-023`。
  - **验证方法**：运行 `SourceDocumentationTest` 并用独立只读 PowerShell/`rg` 复核 `src/main/java`、`src/test/java` 下全部受版本控制 Java 文件；逐文件验证主要顶层类型前存在含中文字符的 Javadoc，且精确作者记录 `@author Self David (dsfgis@gmail.com)` 出现一次。
  - **预期结果**：主代码、测试代码和新增覆盖测试自身均 100% 通过；无缺失、重复、拼写差异或写在非 Javadoc 注释中的作者记录。
  - **证据**：独立枚举得到主代码 48 个、测试代码 28 个、合计 76 个 Java 文件；76/76 均在主要顶层类型前具有中文 Javadoc，精确作者记录均恰好出现一次。Java 21 下 `\.\mvnw.cmd -q -Dtest=SourceDocumentationTest test` 退出 0，动态覆盖测试自身也在扫描范围内。

- [x] `CHECK-037` **中文注释具有维护价值且不泄露秘密**
  - **引用**：`REQ-013`、`REQ-021`；`AC-033`、`AC-060`、`AC-061`；`design.md` 11、13.4、15.6。
  - **关联任务**：`TASK-022`、`TASK-023`。
  - **验证方法**：人工审阅全部类型级 Javadoc，并重点审阅 AgentRuntime/CompletionGate、Provider Adapter、Workspace/PathResolver、ApplyPatch、ProcessRunner、CommandPolicy、Redactor 和复杂测试 fixture；扫描常见 API key/Bearer 模式。
  - **预期结果**：注释针对具体职责、边界、原因或不变量；不逐行复述代码、不作未经验证的行为承诺、不包含密钥或凭据；作者邮箱只以批准的作者元数据形式出现。
  - **证据**：人工审阅全部类型级 Javadoc，并重点复核 AgentRuntime/CompletionGate、Provider Adapter、WorkspaceGuard、ApplyPatch、ProcessRunner、CommandPolicy、Redactor 及复杂测试 fixture；共识别 25 处中文意图注释，说明边界、原因或不变量而非逐行复述。扫描全部 Java 源码和 Surefire 报告，用户 DeepSeek key 原文命中文件数为 0；批准的作者邮箱仅作为作者元数据出现。

- [x] `CHECK-038` **注释变更不改变行为且离线回归通过**
  - **引用**：`REQ-015`、`REQ-016`、`REQ-021`；`AC-037`–`AC-041`、`AC-062`；`design.md` 12–14。
  - **关联任务**：`TASK-022`、`TASK-023`。
  - **验证方法**：在 Java 21 环境执行 `SourceDocumentationTest`、`.\mvnw.cmd -q clean verify` 和连续两次 `.\mvnw.cmd -q -Poffline-e2e verify`；审阅 `git diff --word-diff`/普通 diff，确认除注释、规格/证据和源码覆盖测试外无行为变化。
  - **预期结果**：全部命令退出 0；测试数不少于修改前 65；不访问真实 Provider 或公网；产品 Java 可执行语句、公开契约、依赖和构建配置保持不变；用户已有 `.gitignore` 修改被保留。
  - **证据**：Oracle JDK 21.0.11 下 `\.\mvnw.cmd -q clean verify` 退出 0，26 个 Surefire suite 共 66 tests、0 failure、0 error、2 skipped；连续两次 `\.\mvnw.cmd -q -Poffline-e2e verify` 均退出 0。75 个既有受版本控制 Java 文件去除注释与空行后相对 HEAD 的非注释差异为 0；新增文件仅为 `SourceDocumentationTest`。依赖、构建配置和公开契约未改，用户已有 `.gitignore` 修改保持原样。

## 9. 验收标准到检查项覆盖表

| 验收标准 | 检查项 |
|---|---|
| `AC-001`–`AC-003` | `CHECK-006` |
| `AC-004` | `CHECK-007` |
| `AC-005` | `CHECK-008` |
| `AC-006` | `CHECK-009`、`CHECK-026` |
| `AC-007` | `CHECK-010`、`CHECK-027` |
| `AC-008` | `CHECK-011` |
| `AC-009` | `CHECK-012`、`CHECK-027` |
| `AC-010` | `CHECK-011`、`CHECK-026` |
| `AC-011`–`AC-012` | `CHECK-013` |
| `AC-013`–`AC-014` | `CHECK-014` |
| `AC-015`–`AC-017` | `CHECK-015` |
| `AC-018`–`AC-019` | `CHECK-016` |
| `AC-020` | `CHECK-017`、`CHECK-026` |
| `AC-021`–`AC-022` | `CHECK-018` |
| `AC-023` | `CHECK-020`、`CHECK-026` |
| `AC-024` | `CHECK-009`、`CHECK-020` |
| `AC-025` | `CHECK-017` |
| `AC-026` | `CHECK-019` |
| `AC-027` | `CHECK-018` |
| `AC-028`–`AC-030` | `CHECK-021`、`CHECK-026` |
| `AC-031`–`AC-032` | `CHECK-022`、`CHECK-028` |
| `AC-033` | `CHECK-023`、`CHECK-027` |
| `AC-034` | `CHECK-003`、`CHECK-023`、`CHECK-028` |
| `AC-035`–`AC-036` | `CHECK-024` |
| `AC-037`–`AC-038` | `CHECK-005`、`CHECK-025` |
| `AC-039` | `CHECK-010`、`CHECK-027` |
| `AC-040` | `CHECK-014` |
| `AC-041` | `CHECK-008`、`CHECK-028` |
| `AC-042`–`AC-045` | `CHECK-029` |
| `AC-046`–`AC-048` | `CHECK-030` |
| `AC-049`–`AC-052` | `CHECK-031` |
| `AC-053`–`AC-054` | `CHECK-006`、`CHECK-023`、`CHECK-032` |
| `AC-055` | `CHECK-010`、`CHECK-033`、`CHECK-035` |
| `AC-056` | `CHECK-012`、`CHECK-034` |
| `AC-057` | `CHECK-005`、`CHECK-028`、`CHECK-034`、`CHECK-035` |
| `AC-058` | `CHECK-006`、`CHECK-028`、`CHECK-034` |
| `AC-059` | `CHECK-036` |
| `AC-060`–`AC-061` | `CHECK-036`、`CHECK-037` |
| `AC-062` | `CHECK-038` |

当前覆盖结论：`AC-001` 至 `AC-062` 均映射到至少一个检查项。`CHECK-001`–`CHECK-026`、`CHECK-028`–`CHECK-038` 均有实际证据并已闭环；仅 `CHECK-027` 可选 OpenAI smoke 保持 N/A，不阻塞离线发布。
