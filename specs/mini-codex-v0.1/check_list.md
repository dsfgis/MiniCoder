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
  - **证据**：用户于 2026-08-17 明确确认当前四份规格并接受 `REQ-017` 默认改名决策；内部 package/规格目录保留且旧名制品不兼容的边界已获批。

- [x] `CHECK-002` **需求可测试性与逐级追踪完整**
  - **引用**：`REQ-001`–`REQ-017`；`AC-001`–`AC-045`；`design.md` 17；`tasks.md` 4。
  - **关联任务**：文档一致性检查。
  - **验证方法**：运行规格 ID 校验脚本/人工交叉表，确认每个 REQ 有 AC，每个 REQ 有设计映射，每个 REQ/AC 至少映射到一个 TASK 和一个 CHECK。
  - **预期结果**：无缺失 ID、重复 ID、孤立设计、孤立任务或孤立验收标准。
  - **证据**：只读规格检查为 17 REQ、45 AC、17 TASK、29 CHECK；ID 无重复，17 个需求均有设计与任务映射，`AC-001`–`AC-045` 均有检查项覆盖；详见证据文档。

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
  - **证据**：用户先批准原四份规格；`REQ-017` 更新后再次明确确认当前四份规格、接受其默认决策并授权实施 `TASK-017`。

## 3. 构建、CLI 与配置

- [x] `CHECK-005` **Java 21 工程可重复构建且测试不依赖真实密钥**
  - **引用**：`REQ-015`、`REQ-016`；`AC-037`；`design.md` 12、13。
  - **关联任务**：`TASK-001`、`TASK-014`、`TASK-016`。
  - **验证方法**：在未设置 Provider 密钥的干净检出中执行 `.\mvnw.cmd -q clean verify`。
  - **预期结果**：退出码 0；全部默认测试通过；构建日志不尝试真实 Provider 网络调用。
  - **证据**：JDK 21.0.11；改名后 `mvnw.cmd -q clean verify` 退出 0；新增 CLI 品牌测试后总测试数为 53，默认构建仍不调用真实 Provider。

- [x] `CHECK-006` **CLI 启动、runId、缺参失败与帮助信息正确**
  - **引用**：`REQ-001`；`AC-001`、`AC-002`、`AC-003`；`design.md` 5.1、10。
  - **关联任务**：`TASK-003`、`TASK-013`、`TASK-016`。
  - **验证方法**：运行 CLI 的合法 Fake Provider 示例、缺任务/模型/Provider/密钥组合以及 `--help`。
  - **预期结果**：合法运行在工具前显示唯一 runId；缺参在工具前以非零码失败；帮助列出参数、环境变量、安全边界和示例。
  - **证据**：`CliTest` 与 packaged scripted/help/version 结果；`Usage: mini-coder` 与 `Mini Coder 0.1.0` 已断言，进程退出 10/0，runId 与 JSON 一致；见证据文档。

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
  - **引用**：`REQ-003`、`REQ-016`；`AC-007`、`AC-039`；`design.md` 5.3、6。
  - **关联任务**：`TASK-002`、`TASK-011`、`TASK-012`、`TASK-015`。
  - **验证方法**：运行 `*ProviderContractTest,*OpenAiWireMockTest`，覆盖单/多工具调用、调用 ID、Provider cursor 和 ToolResult 回传。
  - **预期结果**：名称、参数、`callId` 和续接状态无损；Fake/OpenAI Provider 通过同一合约。
  - **证据**：`LlmProviderContractTest`、`OpenAiProviderContractTest`、`AgentRuntimeTest`；规定选择器退出 0。

- [x] `CHECK-011` **最终文本触发完成门且工具顺序确定**
  - **引用**：`REQ-003`、`REQ-004`；`AC-008`、`AC-010`；`design.md` 5.2、9.2。
  - **关联任务**：`TASK-012`、`TASK-014`。
  - **验证方法**：用 ScriptedLlmProvider 返回多个有序工具调用后再返回最终文本，执行 `*AgentRuntimeTest`。
  - **预期结果**：工具按响应顺序执行；最终文本不再触发 Provider 调用，但必须经过 CompletionGate；终态符合验证证据。
  - **证据**：`AgentRuntimeTest` 断言 callId 顺序与 CompletionGate；Spring E2E 事件顺序 c1→c4。

- [x] `CHECK-012` **Provider 瞬时错误有界重试、永久错误立即失败**
  - **引用**：`REQ-003`；`AC-009`；`design.md` 10。
  - **关联任务**：`TASK-011`、`TASK-012`、`TASK-015`。
  - **验证方法**：模拟 429、5xx、连接失败、401 和畸形响应，运行 `*OpenAiProvider*Test`。
  - **预期结果**：仅瞬时错误在预算内退避重试；401/协议错误不重试；总时限不被绕过；输出已脱敏。
  - **证据**：OpenAI contract tests 覆盖 429/500 重试、Retry-After 总时限、401 不重试、畸形响应与脱敏。

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
  - **引用**：`REQ-013`；`AC-033`、`AC-034`；`design.md` 8.3、11。
  - **关联任务**：`TASK-003`、`TASK-008`、`TASK-011`、`TASK-016`。
  - **验证方法**：向配置、异常、命令参数和模拟 Provider 响应注入唯一测试秘密；扫描普通/DEBUG 日志、ToolResult、终端、JSON；人工审阅 README/`--help`。
  - **预期结果**：原始秘密零出现，统一显示脱敏标记；文档明确没有 OS 沙箱且不得对不可信仓库运行。
  - **证据**：`RedactorTest`、OpenAI error test、RunReport secret injection；README/help 安全警告人工审阅。

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
  - **证据**：`TASK-017` 后连续两次 `mvnw.cmd -q -Poffline-e2e verify` 均退出 0；完整文件内容与终态断言固定。

- [x] `CHECK-026` **Spring Boot 缺陷修复端到端闭环成立**
  - **引用**：`REQ-002`–`REQ-012`；`AC-006`、`AC-010`、`AC-018`、`AC-020`、`AC-023`、`AC-028`、`AC-031`；`design.md` 12.3。
  - **关联任务**：`TASK-014`。
  - **验证方法**：在固定 fixture 上用 ScriptedLlmProvider 执行“定位空指针 → 读取 → 补丁 → 测试 → diff → 完成”任务。
  - **预期结果**：初始测试失败；Agent 只修改预期文件；最后 revision 的测试退出码 0；最终状态 `SUCCEEDED`；报告含准确 diff 与验证证据。
  - **证据**：真实 Spring Boot 3.3.2 fixture 以 `mvn.cmd -o -q test` 前失败后成功；最终 SUCCEEDED/diff/归属/revision 均断言。

- [ ] `CHECK-027` **真实 OpenAI Provider smoke（可选、非离线发布门禁）**
  - **引用**：`REQ-003`、`REQ-013`、`REQ-015`、`REQ-016`；`AC-007`–`AC-009`、`AC-033`、`AC-039`；`design.md` 5.3、12.3。
  - **关联任务**：`TASK-015`。
  - **验证方法**：在用户明确授权、设置密钥和模型的环境执行 `.\mvnw.cmd -q -Popenai-smoke verify`。
  - **预期结果**：至少一次真实工具调用和 `call_id` 续接成功；无密钥泄露；账户/网络阻塞与产品失败分开记录。
  - **证据**：N/A；本轮未提供或授权使用真实 OpenAI 凭据/网络执行。该项为可选且非离线发布门禁。

- [x] `CHECK-028` **发布包、文档与范围边界最终审计通过**
  - **引用**：`REQ-001`、`REQ-012`、`REQ-013`、`REQ-016`；`AC-003`、`AC-031`、`AC-032`、`AC-034`、`AC-041`；`design.md` 13、14。
  - **关联任务**：`TASK-016`。
  - **验证方法**：执行 `.\mvnw.cmd -q clean verify`，检查发布包内容与 `git status --short`，在含空格/中文路径运行 `--help` 和离线示例，人工审阅 README 与变更记录。
  - **预期结果**：发布包可启动；源码树只含预期文件；文档列出依赖、配置、退出码、示例、安全边界和范围外能力；没有声称支持 DeepSeek、多 Agent、MCP 或强沙箱。
  - **证据**：改名后 clean verify、SHA-256、ZIP 内容、Spring 类零打包、Unicode/space 目录 help/version/scripted CLI；详见证据文档。

- [x] `CHECK-029` **Mini Coder 产品改名完整且可发布**
  - **引用**：`REQ-017`；`AC-042`–`AC-045`；`design.md` 13.1、14。
  - **关联任务**：`TASK-017`。
  - **验证方法**：在 Java 21 环境执行 `.\mvnw.cmd -q clean verify`；运行新 fat JAR 的 `--help`、`--version` 与含空格/中文路径离线示例；检查 `target` 文件名和 ZIP 清单；定向扫描旧名并人工审阅允许保留项。
  - **预期结果**：测试和离线示例退出 0；公开品牌、CLI 与制品统一为 `Mini Coder` / `mini-coder`；ZIP 内为 `mini-coder.jar`；不存在旧名兼容制品；只在改名说明、内部 package/规格路径和历史证据中保留有用途的旧标识。
  - **证据**：CLI 局部测试、最终 clean verify 与两次 offline-e2e 均退出 0；新 fat JAR/ZIP 哈希分别为 `0D74190AD6DC6707503BEC93D9FEFCC921A96BB852858EE13F183D6856E530C6`、`8D95803A7F2DFED115D2C2C4EB2668FFB40E5C2192FC1A46FB879684A259AC20`；ZIP 内为 `mini-coder.jar`，旧制品为 0；help/version/Unicode scripted CLI 与旧名允许清单审计均通过，详见证据文档。

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

当前覆盖结论：`AC-001` 至 `AC-045` 均映射到至少一个已完成的离线检查项；`CHECK-001`、`CHECK-002` 与 `CHECK-029` 已由重新批准、追踪检查及实际发布验证闭合。`CHECK-027` 仍为可选且本轮未授权执行。
