# Mini Coder V0.1 实施任务

## 1. 执行规则

- 本文任务只有在 `requirements.md`、`design.md`、`tasks.md`、`check_list.md` 的当前版本被明确批准后才能执行。
- 默认按任务编号推进；标为可并行的任务仍必须满足其依赖，且不得并行修改同一文件。
- 每个任务必须先执行其局部验证，再进入依赖它的任务。
- 任务完成不等于验收通过；最终证据统一记录到 `check_list.md`。

## 2. 任务清单

### TASK-001 建立 Java 21 工程骨架

- **交付物**：初始化 Git 仓库（若用户批准在当前目录建仓）、Maven Wrapper、`pom.xml`、标准源码/测试目录、基础包、格式/编译配置和 `.gitignore`。
- **需求引用**：`REQ-001`、`REQ-015`、`REQ-016`；`AC-037`、`AC-041`。
- **设计引用**：3.2、12、13、14。
- **依赖**：四份规格文档获批。
- **验证方法**：执行 `java -version` 和 `.\mvnw.cmd -q -DskipTests package`。
- **预期结果**：JDK 主版本为 21；空骨架成功打包，退出码为 0；无源码生成到版本控制范围外的意外位置。
- **并行性**：否，所有实现任务的基础。

### TASK-002 定义核心领域契约与 Fake Provider

- **交付物**：`RunConfig`、`RunContext`、状态/错误枚举、`LlmProvider`、Provider 请求/响应/游标、`Tool`、`ToolDefinition`、`ToolCall`、`ToolResult`、取消令牌，以及 `ScriptedLlmProvider` 测试实现。
- **需求引用**：`REQ-003`、`REQ-004`、`REQ-005`、`REQ-015`、`REQ-016`；`AC-007`、`AC-008`、`AC-014`、`AC-039`、`AC-040`。
- **设计引用**：4、6、9.1、12.1。
- **依赖**：`TASK-001`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*ContractTest,*ScriptedLlmProviderTest test`。
- **预期结果**：领域对象可稳定序列化；Fake Provider 能脚本化返回文本/多工具调用/错误；测试全部通过且不需要网络或 API key。
- **并行性**：否，后续模块共享这些契约。

### TASK-003 实现配置加载与依赖预检

- **交付物**：CLI/环境/配置文件优先级、密钥安全读取、参数约束、`git`/`rg` 可用性预检和结构化配置错误。
- **需求引用**：`REQ-001`、`REQ-006`、`REQ-012`、`REQ-013`；`AC-002`、`AC-017`、`AC-032`、`AC-033`。
- **设计引用**：5.1、6、10、11.2。
- **依赖**：`TASK-002`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*Config*Test,*DependencyPreflightTest test`。
- **预期结果**：缺失配置在任何 Provider/工具执行前失败；优先级符合设计；异常和断言输出不含测试密钥。
- **并行性**：可与 `TASK-004` 在不同文件上并行。

### TASK-004 实现工作区边界与 Git 基线

- **交付物**：`Workspace`、`PathResolver`、`WorkspaceGuard`、Git 仓库校验、启动基线捕获和变更归属领域模型。
- **需求引用**：`REQ-002`、`REQ-009`、`REQ-013`、`REQ-016`；`AC-004`、`AC-005`、`AC-006`、`AC-024`、`AC-041`。
- **设计引用**：5.1、7.1、7.2、13。
- **依赖**：`TASK-002`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*Workspace*Test,*PathResolver*Test,*GitBaselineTest test`。
- **预期结果**：临时 Git 仓库基线可复现；`..`、绝对路径、符号链接/junction 越界被拒绝；预有修改与本次重叠被区分；空格、中文和 CRLF 场景通过。
- **并行性**：可与 `TASK-003` 并行。

### TASK-005 实现 ToolRegistry 与参数校验

- **交付物**：不可变工具注册表、名称唯一性校验、JSON Schema 生成/校验、未知工具/非法参数处理和统一执行上下文。
- **需求引用**：`REQ-005`、`REQ-016`；`AC-013`、`AC-014`、`AC-040`。
- **设计引用**：4、5.2、6。
- **依赖**：`TASK-002`、`TASK-004`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*ToolRegistryTest,*ToolSchemaTest test`。
- **预期结果**：未知/重复工具和非法参数不会进入执行器；测试工具注册后无需修改 Agent Loop 契约。
- **并行性**：否，是具体工具的入口。

### TASK-006 实现只读文件与搜索工具

- **交付物**：`list_files`、`read_file`、`search_code`，包括稳定排序、行/条目/字节限制、常见生成目录忽略、编码/二进制处理和 ripgrep 结果解析。
- **需求引用**：`REQ-006`、`REQ-013`、`REQ-016`；`AC-015`、`AC-016`、`AC-017`、`AC-041`。
- **设计引用**：6.1、7.1、13。
- **依赖**：`TASK-004`、`TASK-005`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*ListFilesToolTest,*ReadFileToolTest,*SearchCodeToolTest test`。
- **预期结果**：正常、无匹配、二进制、编码错误、输出截断、缺少 rg 和越界案例均返回规定状态；所有测试通过。
- **并行性**：可与 `TASK-007`、`TASK-008` 在不同文件上并行。

### TASK-007 实现原子 Apply Patch

- **交付物**：unified diff 解析、全量预检、内存变换、同文件系统临时文件提交、失败回滚、revision 更新和变更文件摘要。
- **需求引用**：`REQ-007`、`REQ-011`、`REQ-013`；`AC-018`、`AC-019`、`AC-028`、`AC-030`。
- **设计引用**：5.2、7.3、9.2、10。
- **依赖**：`TASK-004`、`TASK-005`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*ApplyPatchToolTest,*PatchAtomicityTest test`。
- **预期结果**：单/多文件补丁成功；上下文冲突、非法路径、不可写目标和模拟中途失败均不留下部分修改；成功修改使 revision 增加。
- **并行性**：可与 `TASK-006`、`TASK-008` 并行。

### TASK-008 实现安全策略、审批与脱敏

- **交付物**：`RiskClassifier`、`CommandPolicy`、规则集、交互/非交互 `ApprovalService`、`Redactor` 和表驱动安全测试。
- **需求引用**：`REQ-010`、`REQ-013`、`REQ-014`；`AC-025`、`AC-026`、`AC-027`、`AC-033`、`AC-036`。
- **设计引用**：8.2、8.3、11。
- **依赖**：`TASK-002`、`TASK-004`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*CommandPolicyTest,*ApprovalServiceTest,*RedactorTest test`。
- **预期结果**：允许/审批/拒绝矩阵符合规格；非交互审批默认拒绝；已配置密钥在普通与 DEBUG 输出中均被遮蔽。
- **并行性**：可与 `TASK-006`、`TASK-007` 并行；`TASK-009` 依赖它。

### TASK-009 实现 ProcessRunner 与 Shell 工具

- **交付物**：参数数组进程执行、显式 Shell 模式、工作目录固定、并发消费 stdout/stderr、输出窗口、超时/取消、后代进程清理，以及接入策略/审批的 `shell` 工具。
- **需求引用**：`REQ-008`、`REQ-010`、`REQ-014`、`REQ-016`；`AC-020`、`AC-021`、`AC-022`、`AC-025`、`AC-026`、`AC-027`、`AC-036`、`AC-041`。
- **设计引用**：6.1、8.1、8.2、10、13。
- **依赖**：`TASK-005`、`TASK-008`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*ProcessRunnerTest,*ShellToolTest test`。
- **预期结果**：退出码、双流、耗时和截断正确；超时测试确认进程树被清理；被拒命令零进程启动；带空格/中文路径通过。
- **并行性**：否。

### TASK-010 实现 Git Diff 与变更归属工具

- **交付物**：只读 Git 命令封装、工作树/暂存区/未跟踪状态解析、相对基线的变更归属、受限 diff/统计输出和 `git_diff` 工具。
- **需求引用**：`REQ-009`、`REQ-012`、`REQ-013`；`AC-023`、`AC-024`、`AC-031`。
- **设计引用**：6.1、7.2、11.2、14。
- **依赖**：`TASK-004`、`TASK-005`、`TASK-009`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*GitDiffToolTest,*ChangeAttributionTest test`。
- **预期结果**：Agent 新改、用户预有修改和重叠修改被正确标记；工具从不运行 Git 写操作；大 diff 有截断与原始大小信息。
- **并行性**：可与 `TASK-011` 并行。

### TASK-011 实现 OpenAI Responses API Provider

- **交付物**：基于 Java HTTP/JSON 边界的 OpenAI Adapter、请求/响应映射、function tool schema、`call_id` 关联、Provider cursor、超时、限流/5xx 有界退避、用量解析和错误脱敏。
- **需求引用**：`REQ-003`、`REQ-013`、`REQ-016`；`AC-007`、`AC-008`、`AC-009`、`AC-033`、`AC-039`。
- **设计引用**：5.3、6、10、13、15.1、15.2。
- **依赖**：`TASK-002`、`TASK-003`、`TASK-005`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*OpenAiProviderContractTest,*OpenAiWireMockTest test`。
- **预期结果**：录制/模拟的最终文本、单/多工具调用、续接、429/5xx、401 和畸形响应全部映射为规定领域结果；不访问公网、不需要真实密钥。
- **并行性**：可与 `TASK-010` 并行。

### TASK-012 实现 Agent Runtime、无进展检测与完成门

- **交付物**：Agent 状态机、迭代/时间/重试预算、顺序工具执行、工具结果回传、取消、无进展指纹、workspace revision、验证证据和 `CompletionGate`。
- **需求引用**：`REQ-003`、`REQ-004`、`REQ-011`；`AC-007`、`AC-008`、`AC-009`、`AC-010`、`AC-011`、`AC-012`、`AC-028`、`AC-029`、`AC-030`。
- **设计引用**：5.2、5.3、9、10。
- **依赖**：`TASK-006` 至 `TASK-011`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*AgentRuntimeTest,*RunStateMachineTest,*CompletionGateTest,*NoProgressTest test`。
- **预期结果**：Fake Provider 的成功、失败后修复、多调用顺序、最终文本、迭代上限、总超时、取消、重复调用和验证失效场景全部得到规定状态。
- **并行性**：否，是主链路汇合点。

### TASK-013 实现 CLI、事件与最终报告

- **交付物**：Picocli 命令、`--help`、runId 展示、结构化事件、终端/JSON 共用 `RunReport`、稳定退出码和 Ctrl+C 取消接线。
- **需求引用**：`REQ-001`、`REQ-012`、`REQ-014`；`AC-001`、`AC-003`、`AC-031`、`AC-032`、`AC-035`、`AC-036`。
- **设计引用**：5.1、9、10、11。
- **依赖**：`TASK-003`、`TASK-010`、`TASK-012`。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest=*Cli*Test,*RunReportTest,*RunEventTest test`，并执行 `.\mvnw.cmd -q -DskipTests package` 后运行生成的 CLI `--help`。
- **预期结果**：所有终止状态映射为稳定退出码；终端/JSON 核心字段一致；事件可重放调用顺序；帮助含安全警告和示例。
- **并行性**：否。

### TASK-014 建立离线端到端与故障场景验收

- **交付物**：受控 Spring Boot fixture、临时仓库工厂和由 ScriptedLlmProvider 驱动的成功修复、验证失败后重试、无进展、越界、补丁冲突、审批拒绝与预算耗尽端到端测试。
- **需求引用**：`REQ-002` 至 `REQ-015`；重点 `AC-006`、`AC-010` 至 `AC-012`、`AC-018` 至 `AC-030`、`AC-035`、`AC-037`、`AC-038`。
- **设计引用**：12.2、12.3。
- **依赖**：`TASK-013`。
- **验证方法**：执行 `.\mvnw.cmd -q -Poffline-e2e verify`。
- **预期结果**：无需公网/API key，全部场景通过；成功场景最后一次修改后的验证退出码为 0，失败场景不产生虚假成功，工作区结果可重复。
- **并行性**：否。

### TASK-015 增加可选真实 Provider Smoke Test

- **交付物**：默认跳过、显式启用的 OpenAI smoke profile；只在一次性 fixture 上执行低风险 read-only 或最小补丁任务；费用/权限/网络失败与产品失败分开报告。
- **需求引用**：`REQ-003`、`REQ-013`、`REQ-015`；`AC-007` 至 `AC-009`、`AC-033`、`AC-039`。
- **设计引用**：5.3、12.3、13。
- **依赖**：`TASK-014`；需要用户提供授权环境和凭据。
- **验证方法**：在显式设置 Provider 凭据和模型后执行 `.\mvnw.cmd -q -Popenai-smoke verify`。
- **预期结果**：至少完成一次 Responses API 工具调用续接；报告可审计且不含密钥。没有网络/权限时明确标记为环境阻塞，而非离线测试失败。
- **并行性**：否；可选、需外部条件。

### TASK-016 完成文档、打包与兼容性验证

- **交付物**：README、架构说明、CLI 示例、Provider 配置、安全边界、退出码、开发/测试指南、可执行 JAR/分发包和变更记录。
- **需求引用**：`REQ-001`、`REQ-012`、`REQ-013`、`REQ-016`；`AC-003`、`AC-031`、`AC-032`、`AC-034`、`AC-041`。
- **设计引用**：8.3、11.2、13、14。
- **依赖**：`TASK-014`；真实 smoke 结果可作为附加证据但不阻塞离线发布。
- **验证方法**：执行 `.\mvnw.cmd -q clean verify`，检查 `git status --short` 和发布包，再在含空格/中文路径的 Windows 临时目录运行 `--help` 与离线示例。
- **预期结果**：全量离线构建/测试通过；发布包可启动；文档明确“不执行不可信仓库”和无 OS 沙箱承诺；只包含预期文件。
- **并行性**：否，最终收口。

### TASK-017 将产品与发行标识改为 Mini Coder

- **状态**：已完成；实现、离线测试、发布制品、旧名扫描和证据记录均通过，详见 `CHECK-029` 与 `docs/verification-evidence.md`。
- **交付物**：将人类可读品牌更新为 `Mini Coder`；将 Picocli usage name、Maven artifactId、fat JAR、分发 ZIP 和 ZIP 内 JAR 更新为 `mini-coder`；更新 CLI/打包测试、README、架构说明、变更记录、项目规则、Agent 指令和验证证据。Java namespace 与规格目录的进一步迁移由 `TASK-018`、`TASK-019` 完成。
- **需求引用**：`REQ-017`；`AC-042`–`AC-045`。
- **设计引用**：13.1、14。
- **依赖**：`TASK-016`；四份包含 `REQ-017` 的当前规格文档重新获批。
- **验证方法**：在 Java 21 环境执行 `.\mvnw.cmd -q clean verify`；分别运行新 fat JAR 的 `--help` 与 `--version`；检查新 JAR/ZIP 文件名及 ZIP 清单；执行旧名定向扫描并人工审阅允许保留项；在含空格/中文路径运行新名制品的离线示例。
- **预期结果**：全量离线测试退出 0；CLI、文档和新制品统一使用 `Mini Coder` / `mini-coder`；ZIP 内含 `mini-coder.jar`；干净构建不产生废弃制品。`TASK-017` 当时未迁移 Java namespace，后续由 `TASK-018`、`TASK-019` 完成；历史证据不被伪造或改写。
- **并行性**：否；同时修改构建、CLI 和交付文档，需串行完成并在最终 workspace revision 重跑发布门禁。

### TASK-018 迁移 Java 命名空间与构建入口

- **状态**：已完成；源码/测试路径、声明、Maven 坐标和入口类已迁移，Java 21 全量构建及 53-test 门禁通过，详见 `CHECK-031` 与 `docs/verification-evidence.md`。
- **交付物**：将主代码和测试代码目录迁移到 `src/main/java/dev/minicoder/`、`src/test/java/dev/minicoder/`；统一全部 Java `package`、`import`、完全限定类名和 fixture 命名；更新 Maven `groupId`、shade `mainClass` 与相关构建入口为 `dev.minicoder`；不保留兼容 package。
- **需求引用**：`REQ-017`、`REQ-019`；`AC-045`、`AC-049`–`AC-052`。
- **设计引用**：3.2、6、12、13.1、13.3、14。
- **依赖**：`TASK-017`；四份包含 `REQ-018`、`REQ-019` 的当前规格文档重新获批。
- **验证方法**：枚举 Java 文件路径并扫描 `package`/`import`/完全限定类名；执行 `.\mvnw.cmd -q clean verify`；检查 Maven effective coordinates、fat JAR manifest 和 `java -jar ... --version`；确认测试数不少于迁移前 53。
- **预期结果**：主代码、测试、Maven 坐标和入口类全部使用 `dev.minicoder`；废弃 package 内容/路径为 0；全量构建退出 0，53 个测试无失败/错误，fat JAR 可启动。
- **并行性**：否；源码目录、声明和构建入口必须作为一次一致迁移完成。

### TASK-019 统一全部文档名称并迁移规格目录

- **状态**：已完成；规格目录、全部文档引用和历史证据表述已迁移，零残留、链接、规格追踪、最终构建、双 E2E 与 packaged CLI 门禁均通过，详见 `CHECK-029`–`CHECK-031` 与 `docs/verification-evidence.md`。
- **交付物**：将四份规格移动到 `specs/mini-coder-v0.1/`；更新 `AGENTS.md`、`project_rule.md`、README、ARCHITECTURE、CHANGELOG、验证证据和规格内所有路径/名称引用；从当前文档删除废弃品牌、slug、package 与历史制品名，同时保留可从 Git 历史追溯的真实证据。
- **需求引用**：`REQ-017`、`REQ-018`、`REQ-019`；`AC-042`、`AC-045`–`AC-048`、`AC-051`、`AC-052`。
- **设计引用**：13.1–13.3、14。
- **依赖**：`TASK-018` 完成且局部构建通过。
- **验证方法**：枚举全部受版本控制内容、路径和 `specs/`；对废弃人类名、slug、camel 和 package 变体执行不区分大小写扫描；验证所有 Markdown 相对链接和四份事实源路径存在；在 Java 21 环境执行最终 `.\mvnw.cmd -q clean verify` 和连续两次 `.\mvnw.cmd -q -Poffline-e2e verify`；验收 fat JAR manifest、`--help`、`--version` 和 scripted CLI；审阅 Git diff。
- **预期结果**：四份事实源只存在于 `specs/mini-coder-v0.1/`；受版本控制树中的废弃命名命中数为 0；所有链接目标存在；最终全量构建与两次离线 E2E 退出 0、测试数不减少、发布制品可启动；历史事实仅由 Git 历史追溯。
- **并行性**：否；依赖代码 namespace 完成，并作为最终发布门禁收口。

### TASK-020 实现 DeepSeek 配置与 Responses API Adapter

- **状态**：已完成；独立配置解析器、DeepSeek Adapter、无状态有界 cursor、错误分类和离线合同测试均已实现，规定定向测试退出 0。
- **交付物**：CLI/配置层支持 `--provider deepseek`、`DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL`、`DEEPSEEK_BASE_URL` 及规定优先级；新增 `llm/deepseek/DeepSeekResponsesProvider`，将默认 Base URL 解析为 `https://api.deepseek.com/responses`，对自定义 Base URL 只追加 `/responses` 而不自动注入 `/v1`，并实现 Bearer Auth、请求/响应映射、有界无状态回放 cursor、function call/tool result 关联、用量、错误分类、重试与脱敏；不新增第三方 Provider SDK。
- **需求引用**：`REQ-001`、`REQ-003`、`REQ-013`、`REQ-015`、`REQ-016`、`REQ-020`；`AC-002`、`AC-007`–`AC-009`、`AC-033`、`AC-037`、`AC-039`、`AC-053`–`AC-057`。
- **设计引用**：3.2、4、5.1、5.4、6、10–13、15.2。
- **依赖**：`TASK-019`；包含 `REQ-020` 的四份当前规格文档重新获批。
- **验证方法**：执行 `.\mvnw.cmd -q -Dtest='*RunConfigTest,*CliTest,*DeepSeekProviderContractTest,*RedactorTest,*LlmProviderContractTest' test`；使用本地 mock HTTP server 断言端点、Authorization header（只比较测试值，不输出）、请求体、无状态多轮回放、响应顺序、用量、cursor 上限以及 400/401/402/422/429/5xx/超时/畸形响应。
- **预期结果**：选择 DeepSeek 时只读取 DeepSeek 专用配置；缺密钥/模型在网络和工具前失败；最终文本、单/多工具调用及多轮续接通过同一领域契约；仅允许错误有界重试；测试输出与报告扫描不到测试密钥；AgentRuntime、ToolRegistry 和 CompletionGate 无 Provider 分支修改。
- **并行性**：否；配置、Adapter 与安全测试需作为一个可验证增量完成。

### TASK-021 完成 DeepSeek CLI 文档、可选 Smoke 与回归发布门禁

- **状态**：已完成；CLI/help、README、架构、规则、变更记录、验证证据和默认禁用 smoke profile 已更新，最终 clean verify、双离线 E2E、packaged CLI、ZIP、链接与秘密扫描均通过；2026-08-19 获得单独授权后，真实 DeepSeek smoke 也已通过。
- **交付物**：更新 CLI help、README、ARCHITECTURE、CHANGELOG、`project_rule.md`、`AGENTS.md` 和验证证据；新增默认禁用的 `deepseek-smoke` Maven profile/test，要求显式 `DEEPSEEK_API_KEY`、模型和用户网络授权；完成发布包与回滚说明。
- **需求引用**：`REQ-001`、`REQ-012`–`REQ-016`、`REQ-020`；`AC-003`、`AC-031`–`AC-039`、`AC-053`–`AC-058`。
- **设计引用**：5.4、8.3、10–14。
- **依赖**：`TASK-020` 局部测试通过。
- **验证方法**：运行 `.\mvnw.cmd -q clean verify`、连续两次 `.\mvnw.cmd -q -Poffline-e2e verify`、打包 JAR `--help` 和 scripted CLI；扫描真实/测试密钥、Provider 名称、环境变量和文档链接；在用户另行明确授权且凭据/模型/网络可用时运行 `.\mvnw.cmd -q -Pdeepseek-smoke verify`，否则记录 N/A，不把可选 smoke 当作离线发布失败。
- **预期结果**：默认构建、双 E2E 和 packaged CLI 退出符合预期且测试数不少于实施前 53；不需要真实密钥或公网；help/README 准确列出三个 Provider 和隔离的配置优先级；可选 smoke 不泄密并能完成至少一次 DeepSeek Responses function call 续接，或以外部环境阻塞准确记录。
- **并行性**：否；依赖 Adapter 完成并作为发布收口。

## 3. 推荐增量里程碑

| 里程碑 | 包含任务 | 可演示结果 |
|---|---|---|
| M1 契约与边界 | `TASK-001`–`TASK-005` | 无网络下验证 Provider/Tool/Workspace 合约 |
| M2 本地工具 | `TASK-006`–`TASK-010` | 在临时 Git 仓库安全读取、搜索、补丁、命令与 diff |
| M3 Agent 闭环 | `TASK-011`–`TASK-013` | Fake/OpenAI Adapter 共用 Agent Loop，生成证据报告 |
| M4 验收交付 | `TASK-014`–`TASK-016` | 离线缺陷修复 E2E、可选真实 smoke、可发布包 |
| M5 产品改名 | `TASK-017` | `Mini Coder` 品牌、`mini-coder` CLI 与新名发布制品 |
| M6 代码命名迁移 | `TASK-018` | Java namespace、Maven 坐标与入口类统一为 `dev.minicoder` |
| M7 文档与发布收口 | `TASK-019` | 全部文档统一命名、规格目录/链接迁移、最终离线门禁通过 |
| M8 DeepSeek Provider | `TASK-020`–`TASK-021` | DeepSeek 密钥/模型配置、Responses API 工具调用、离线合约和可选真实 smoke |

## 4. 需求与验收标准覆盖检查

| 需求 | 验收标准 | 覆盖任务 |
|---|---|---|
| `REQ-001` | `AC-001`–`AC-003` | `TASK-001`、`TASK-003`、`TASK-013`、`TASK-016` |
| `REQ-002` | `AC-004`–`AC-006` | `TASK-004`、`TASK-014` |
| `REQ-003` | `AC-007`–`AC-009` | `TASK-002`、`TASK-011`、`TASK-012`、`TASK-015` |
| `REQ-004` | `AC-010`–`AC-012` | `TASK-002`、`TASK-012`、`TASK-014` |
| `REQ-005` | `AC-013`–`AC-014` | `TASK-002`、`TASK-005` |
| `REQ-006` | `AC-015`–`AC-017` | `TASK-003`、`TASK-006` |
| `REQ-007` | `AC-018`–`AC-019` | `TASK-007`、`TASK-014` |
| `REQ-008` | `AC-020`–`AC-022` | `TASK-009`、`TASK-014` |
| `REQ-009` | `AC-023`–`AC-024` | `TASK-004`、`TASK-010`、`TASK-014` |
| `REQ-010` | `AC-025`–`AC-027` | `TASK-008`、`TASK-009`、`TASK-014` |
| `REQ-011` | `AC-028`–`AC-030` | `TASK-007`、`TASK-012`、`TASK-014` |
| `REQ-012` | `AC-031`–`AC-032` | `TASK-003`、`TASK-010`、`TASK-013`、`TASK-016` |
| `REQ-013` | `AC-033`–`AC-034` | `TASK-003`、`TASK-008`、`TASK-011`、`TASK-015`、`TASK-016` |
| `REQ-014` | `AC-035`–`AC-036` | `TASK-008`、`TASK-009`、`TASK-013`、`TASK-014` |
| `REQ-015` | `AC-037`–`AC-038` | `TASK-001`、`TASK-002`、`TASK-014`、`TASK-015` |
| `REQ-016` | `AC-039`–`AC-041` | `TASK-001`、`TASK-002`、`TASK-004`–`TASK-006`、`TASK-009`、`TASK-011`、`TASK-016` |
| `REQ-017` | `AC-042`–`AC-045` | `TASK-017`–`TASK-019` |
| `REQ-018` | `AC-046`–`AC-048` | `TASK-019` |
| `REQ-019` | `AC-049`–`AC-052` | `TASK-018`、`TASK-019` |
| `REQ-020` | `AC-053`–`AC-058` | `TASK-020`、`TASK-021` |

覆盖结论：当前 20 个需求和 58 个验收标准均至少映射到一个实施任务；DeepSeek 只扩展 Provider/配置/测试/文档边界，没有任务引入 V0.1 范围外的多 Agent、RAG、MCP、长期记忆、Git 写入、其他真实 Provider 或强沙箱承诺。
