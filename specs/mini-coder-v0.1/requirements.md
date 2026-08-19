# Mini Coder V0.1 需求规格

## 1. 背景与目标

本项目用于从零实现一个可运行、可观察、可验证的 Coding Agent Harness，以理解类似 Codex 的核心机制，而不是训练模型或在首版复制完整 Codex 产品。

V0.1 的目标是：用户通过 CLI 指定一个本地 Git 工作区和一项编码任务，Agent 能调用 LLM，自主查看和搜索代码、应用补丁、运行本地命令、检查差异，并在验证成功或达到明确停止条件后给出有证据的结果报告。

## 2. 已确认事实、假设与开放问题

### 2.1 已确认事实

- `FACT-001`：当前 `D:\study\code\agentcode` 已是实现完成并推送到 `origin/main` 的 Git 仓库；DeepSeek 接入后的最新已验证离线基线为 65 个测试。
- `FACT-002`：目标是先实现 Coding Agent Harness，而不是训练专用模型。
- `FACT-003`：V0.1 采用 Java 21，提供 CLI、LLM Provider 抽象、Agent Loop 和本地工具。
- `FACT-004`：V0.1 的核心工具范围为 `list_files`、`read_file`、`search_code`、`apply_patch`、`shell`、`git_diff`。
- `FACT-005`：V0.1 需要形成“读取/修改 → 验证 → 根据结果继续或停止 → 输出 diff 与结论”的闭环。
- `FACT-006`：用户于 2026-08-17 要求将产品名称统一为 `Mini Coder`。
- `FACT-007`：用户随后要求所有涉及产品名称的文档统一采用 `Mini Coder`，不再保留旧产品名或旧规格目录 slug。
- `FACT-008`：用户进一步要求代码中的相关命名也统一为 `Mini Coder`，因此此前拟保留内部 Java package 的默认方案不再适用。
- `FACT-009`：用户于 2026-08-18 要求增加 DeepSeek API key 支持；这会把 DeepSeek 提升为第二个真实 Provider，属于已批准规格范围之外的实质变更，必须先重新批准四份规格。
- `FACT-010`：用户于 2026-08-19 要求为代码增加中文注释，并将作者记录为 `Self David`、作者邮箱记录为 `dsfgis@gmail.com`。当前仓库共有 75 个 Java 文件（主代码 48、测试 27），没有文件包含 `@author`，仅 1 个文件检测到中文注释。

### 2.2 待确认假设

- `ASM-001`：项目采用 Maven 单模块结构，使用 Maven Wrapper；主要依赖为 Picocli、Jackson、SLF4J/Logback 和 JUnit 5。
- `ASM-002`：首个真实 Provider 使用 OpenAI Responses API；具体模型必须由 CLI、配置文件或环境变量提供，不硬编码“最新模型”。
- `ASM-003`：DeepSeek 使用官方 Responses API、独立 `DeepSeekResponsesProvider` 和 `DEEPSEEK_API_KEY`；模型仍由 `--model` 或 `DEEPSEEK_MODEL` 显式指定，不硬编码会变化的模型别名。
- `ASM-004`：Windows 11 是首要开发环境，但路径、进程和换行处理不得有意阻止 Linux/macOS 运行。
- `ASM-005`：目标工作区必须是现有 Git 仓库；V0.1 不负责自动建仓、创建提交、切分支或推送远端。
- `ASM-006`：测试验收使用一个受控的 Spring Boot 示例仓库，同时核心文件工具保持语言无关。
- `ASM-007`：V0.1 提供工作区边界、命令策略、审批、超时和输出限制，但不宣称能够安全执行恶意代码；强隔离的 OS/容器沙箱留到后续版本。
- `ASM-008`：一次 CLI 运行只处理一个用户任务，不恢复跨进程会话。
- `ASM-009`：本次“代码”默认指全部受版本控制的 `src/main/java/**/*.java` 与 `src/test/java/**/*.java`，不包含 `target/`、生成代码、依赖源码或非 Java 文件。
- `ASM-010`：每个 Java 文件的主要顶层类型使用中文 Javadoc 说明职责，并统一写入 `@author Self David (dsfgis@gmail.com)`；只在复杂控制流、安全边界或不直观算法处补充中文意图注释，不要求逐行或为显而易见的 getter/构造器添加注释。

### 2.3 开放问题（不阻塞文档草案）

- `OQ-001`：是否暴露 DeepSeek thinking/reasoning effort 专用 CLI 参数？默认答案：否；本次只接入 Provider、密钥、模型、Base URL、工具调用与续接，供应商专用推理参数留待后续需求。
- `OQ-002`：默认构建工具是否改用 Gradle？默认答案：否，采用 Maven Wrapper。
- `OQ-003`：高风险命令是“始终拒绝”还是“交互审批后允许”？默认答案：破坏性命令始终拒绝；可恢复但有外部副作用的命令需要审批。
- `OQ-004`：首版是否需要真正的 OS 级沙箱？默认答案：否，但发布说明必须明确安全边界。
- `OQ-005`：是否要求每个字段和方法都有中文注释？默认答案：否；强制覆盖每个 Java 文件的主要顶层类型，并对复杂/高风险逻辑补充注释，以避免机械注释掩盖真正的重要约束。

## 3. 功能需求

### REQ-001 CLI 启动与配置

系统必须提供单次任务 CLI，至少接收任务文本、工作区路径、Provider、模型、最大循环次数、总时限和可选验证命令。密钥只能从环境变量或受支持的安全配置源读取，不得作为必需的明文命令行参数。

- `AC-001` Given 合法工作区和完整配置，When 用户启动 CLI，Then 系统创建一次运行并显示唯一 `runId`。
- `AC-002` Given 缺少任务、模型、Provider 配置，或所选 Provider 必需的密钥，When 启动 CLI，Then 系统在执行任何工具前失败，并返回可操作的错误信息和非零退出码。
- `AC-003` Given `--help`，When 执行 CLI，Then 帮助文本列出参数、环境变量、安全边界和至少一个示例。

### REQ-002 工作区建立与基线

系统必须解析工作区的规范绝对路径，确认其为可访问的 Git 仓库，并在首次 LLM 调用前记录 Git 状态与差异基线。所有文件类工具的最终解析路径必须位于该工作区内。

- `AC-004` Given 不存在、不是目录或不是 Git 仓库的路径，When 启动任务，Then 系统拒绝运行且不调用 LLM。
- `AC-005` Given 包含 `..`、符号链接或 junction 的工具路径，When 解析后的真实路径越出工作区，Then 工具返回 `POLICY_DENIED` 且不访问目标。
- `AC-006` Given 工作区已有未提交修改，When 运行开始，Then 基线记录能够在最终报告中区分“运行前已有变化”和“本次运行新增变化”。

### REQ-003 LLM Provider 与工具调用协议

系统必须通过 `LlmProvider` 抽象发送指令、会话状态和工具定义，并返回类型化的文本、工具调用、调用标识、用量和 Provider 续接状态。OpenAI 实现必须使用 Responses API 支持的工具调用/续接语义，保留工具调用关联标识。

- `AC-007` Given Fake Provider 返回一个或多个工具调用，When Agent 处理响应，Then 每个调用的名称、参数和调用 ID 均无损传给对应工具，并将结果关联回原调用。
- `AC-008` Given Provider 返回最终文本且无工具调用，When Agent 处理响应，Then 进入完成判定而不是继续调用 Provider。
- `AC-009` Given Provider 超时、限流或服务端错误，When 未超过重试预算，Then 仅对可重试错误采用有上限的退避重试；超过预算后以明确错误停止。

### REQ-004 Agent Loop 与停止条件

系统必须实现确定性的 Agent Loop：调用 Provider、校验并执行工具、追加结果、再次调用 Provider，直到通过完成判定或触发停止条件。循环必须受最大迭代数、总时限、取消信号、重复调用和无进展检测约束。

- `AC-010` Given 可完成的脚本化 Fake Provider 场景，When 运行 Agent，Then 工具执行顺序与 Provider 返回顺序一致，并以 `SUCCEEDED` 或 `SUCCEEDED_WITH_WARNINGS` 结束。
- `AC-011` Given 循环达到最大迭代数或总时限，When 尚未完成，Then 系统停止后续调用，状态为 `LIMIT_REACHED`，并报告最后一步和未完成原因。
- `AC-012` Given 连续出现达到阈值的等价工具调用且没有新结果，When 检测到循环，Then 系统以 `NO_PROGRESS` 停止，而不是无限重试。

### REQ-005 工具注册、参数与结果契约

所有工具必须通过统一注册表暴露名称、用途、JSON Schema 参数和执行器；未知工具、非法 JSON 或不符合 Schema 的参数不得进入执行器。每个结果必须包含状态、简短摘要、结构化数据、是否截断和可诊断错误。

- `AC-013` Given 未注册工具或非法参数，When Agent 尝试执行，Then 返回结构化 `INVALID_TOOL_CALL`，不产生文件或进程副作用。
- `AC-014` Given 任一工具成功、失败或输出被截断，When 返回结果，Then结果字段符合统一 `ToolResult` 契约并可序列化回 Provider。

### REQ-006 只读文件与代码搜索工具

系统必须实现：受边界约束的目录枚举、按行读取 UTF-8 文本文件，以及基于 ripgrep 的代码搜索。工具必须支持合理的条目/行数/字节上限、忽略常见生成目录，并明确报告二进制文件、无匹配、编码错误、缺少 ripgrep 与输出截断。

- `AC-015` Given 工作区内目录，When 调用 `list_files`，Then 返回稳定排序的相对路径，且不返回工作区外条目。
- `AC-016` Given 合法文本文件和行范围，When 调用 `read_file`，Then 返回带行号内容；越界范围、二进制文件或超限有明确状态。
- `AC-017` Given 搜索表达式，When 调用 `search_code`，Then 返回文件、行号和匹配片段；无匹配与工具故障可区分。

### REQ-007 补丁修改工具

系统必须实现 `apply_patch`，接收 unified diff，在工作区边界内进行全量预检，并在进程正常运行的可控失败场景中提供事务式应用与回滚；不得把进程崩溃、断电或恶意文件系统下的多文件原子性列为 V0.1 保证。V0.1 不提供任意整文件覆写工具。

- `AC-018` Given 可应用且仅涉及工作区内文件的补丁，When 调用 `apply_patch`，Then 所有目标变更一次性生效并返回受影响文件列表。
- `AC-019` Given 上下文不匹配、路径越界、绝对路径、非法 diff、部分文件不可写或测试注入的可控提交失败，When 调用 `apply_patch` 且进程未异常终止，Then 整个操作失败且工作区内容保持调用前状态；若回滚本身失败，系统必须停止并准确报告可能不一致的文件。

### REQ-008 Shell 工具

系统必须通过参数化进程执行器在工作区目录运行命令，分别捕获 stdout、stderr、退出码和耗时，并强制每次命令超时、输出字节上限、子进程清理与命令策略判定。不得通过字符串拼接隐式调用额外 Shell；只有显式选择 PowerShell/cmd/bash 模式时才允许 Shell 语法。

- `AC-020` Given 允许的命令，When 调用 `shell`，Then 进程工作目录为目标工作区，并返回退出码、耗时、stdout/stderr 与截断标记。
- `AC-021` Given 超时命令，When 达到限制，Then 系统终止进程树并返回 `TIMEOUT`，Agent 可据此决定下一步。
- `AC-022` Given 被策略拒绝或需审批但未获批准的命令，When 调用 `shell`，Then 不创建进程并返回对应策略状态。

### REQ-009 Git 差异工具与变更归属

系统必须实现只读 `git_diff`，能够返回工作树状态、相对运行前基线的本次变化摘要、完整差异的受限输出，并识别可能与用户原有修改重叠的文件。工具不得自动提交、重置、清理或推送。

- `AC-023` Given Agent 修改一个文件，When 调用 `git_diff`，Then 返回该文件的 diff、统计信息和本次运行归属标记。
- `AC-024` Given 文件在运行前已有修改且运行中再次被改动，When 生成报告，Then 标记为 `OVERLAPS_PREEXISTING_CHANGE`，不得声称整份 diff 都由 Agent 创建。

### REQ-010 审批与命令策略

系统必须在工具执行前执行策略分类：只读/本地验证类命令可自动允许；破坏性、越界或无法可靠约束的命令拒绝；网络访问、发布、远端写入或其他外部副作用命令需要明确交互审批。非交互模式下，需审批的命令默认拒绝。

- `AC-025` Given 安全只读或本地验证命令，When 策略分类，Then 可在无需审批的情况下执行。
- `AC-026` Given 远端写入、发布或网络命令，When 用户未明确批准，Then 命令不执行；批准与否被记录但不记录敏感参数。
- `AC-027` Given 删除仓库、覆写 Git 历史、越出工作区或访问已知凭据位置的命令，When 策略分类，Then V0.1 始终拒绝，即使模型要求执行。

### REQ-011 验证闭环与完成判定

系统必须把验证证据作为成功判定的一部分。最后一次文件修改后，至少要有一个相关验证命令成功；若用户提供 `--verify-command`，该命令必须成功。模型文本不得单独决定成功状态。

- `AC-028` Given 最后一次修改后验证退出码为 0，且无未处理工具错误，When Provider 给出完成文本，Then运行可判定为 `SUCCEEDED`。
- `AC-029` Given 验证失败，When 尚有预算，Then失败结果返回 Agent 继续处理；预算耗尽后状态不得为成功。
- `AC-030` Given 没有可执行验证或验证发生在最后一次修改之前，When Provider 声称完成，Then 状态为 `SUCCEEDED_WITH_WARNINGS` 或失败，并明确标记“未充分验证”。

### REQ-012 最终报告与进程退出

每次运行必须输出人类可读总结，并可选输出机器可读 JSON。报告至少包含状态、原因、修改文件、与基线的关系、验证命令及结果、限制/警告、用量摘要和 `runId`。CLI 退出码必须稳定区分成功、带警告成功、用户取消、策略拒绝、配置错误、Provider 错误、工具错误和预算耗尽。

- `AC-031` Given 任一终止状态，When CLI 退出，Then 控制台与 JSON 报告包含相同的核心事实且不泄露密钥。
- `AC-032` Given 两种不同错误类别，When CLI 退出，Then 它们具有文档化且不同的退出码或结构化错误码。

## 4. 非功能需求

### REQ-013 安全与隐私

系统必须遵循最小权限原则；规范化所有路径；默认禁用工作区外文件访问；不得把 API 密钥、环境变量全集、凭据文件内容或明显的令牌写入日志、ToolResult 和最终报告。V0.1 的安全声明必须明确其不是恶意代码隔离环境。

- `AC-033` Given 日志、异常、命令参数或 Provider 响应中出现配置的密钥值，When 输出给终端或文件，Then 该值被一致替换为脱敏标记。
- `AC-034` Given README/帮助/报告模板，When 检查安全说明，Then 明确列出 V0.1 的边界和“不执行不可信仓库”的警告。

### REQ-014 可观察性

系统必须以 `runId`、迭代号和 `toolCallId` 关联结构化事件，记录状态转换、工具耗时、退出码、重试、审批和截断情况；默认不记录完整文件内容、完整提示词或秘密。

- `AC-035` Given 一次多迭代运行，When 查看日志，Then 可重建 Provider 调用、工具调用和状态转换的顺序。
- `AC-036` Given 输出超限或日志级别改变，When 运行，Then 截断事实和原始字节计数仍可见，且 DEBUG 也不输出配置的密钥。

### REQ-015 可测试性与可靠性

核心 Agent Loop、策略、路径边界和工具契约必须可在无网络、无真实 API 密钥条件下通过 Fake Provider 和临时 Git 仓库确定性测试。文件修改类测试必须验证失败原子性；命令测试不得依赖公网。

- `AC-037` Given 新检出的源码，When 执行 Maven 测试，Then 单元测试和集成测试无需真实 Provider 凭据即可运行。
- `AC-038` Given预置的成功、验证失败、无限循环、越界和补丁冲突场景，When 执行测试套件，Then 每个场景得到规定状态且工作区结果可重复。

### REQ-016 兼容性与扩展性

系统必须以 Java 21 构建；核心领域模型不得依赖某一 Provider 的 SDK 类型。新增 Provider 或工具时，不得修改 Agent Loop 的控制流。Windows 路径与 CRLF 必须纳入测试。

- `AC-039` Given Fake Provider 和 OpenAI Provider，When 运行同一 Agent Loop 合约测试，Then 两者通过相同的 Provider 接口边界。
- `AC-040` Given新增测试工具实现，When 注册到 ToolRegistry，Then Agent Loop 无需代码修改即可调用它。
- `AC-041` Given 包含空格、非 ASCII 字符或 CRLF 的 Windows 工作区路径，When 执行文件工具和受控命令，Then 路径与内容处理正确。

### REQ-017 产品命名与发行标识

产品的人类可读名称必须统一为 `Mini Coder`，公开 CLI usage name、Maven artifactId、可执行 JAR、分发 ZIP 和分发包内 JAR 必须统一使用机器标识 `mini-coder`。内部 Java package 与 Maven groupId 必须统一为 `dev.minicoder`。规范事实源目录必须统一为 `specs/mini-coder-v0.1/`，不继续生成或保留废弃命名的兼容制品。

- `AC-042` Given README、架构文档、变更记录、项目规则、Agent 指令、规格标题和 CLI 帮助，When 检索人类可读产品名称，Then 对外品牌只显示 `Mini Coder`，不在当前文档中保留旧品牌写法。
- `AC-043` Given 已打包的可执行 JAR，When 使用 `--help` 或 `--version` 启动，Then usage name 显示 `mini-coder`，版本文本显示 `Mini Coder 0.1.0`，示例命令使用 `mini-coder`。
- `AC-044` Given 执行 Maven package/verify，When 检查发布制品和 ZIP 内容，Then 生成 `mini-coder-0.1.0-SNAPSHOT-all.jar`、`mini-coder-0.1.0-SNAPSHOT-dist.zip`，且 ZIP 内可执行 JAR 名为 `mini-coder.jar`，不生成旧名兼容制品。
- `AC-045` Given 完成改名后的源码树，When 对全部受版本控制文件内容及路径执行不区分大小写的废弃产品名、slug 和 package 变体扫描，Then 命中数为 0。

### REQ-018 文档名称与路径迁移

所有当前 Markdown 文档的标题、正文、链接、示例、证据说明和目录路径必须统一使用 `Mini Coder` / `mini-coder`。规范事实源目录必须迁移到 `specs/mini-coder-v0.1/`，所有仓库内引用同步更新且可解析。历史事实不得伪造成新名称；旧文档原文由 Git 历史保留，当前证据文档使用不含废弃名称的“改名前历史证据”描述。

- `AC-046` Given 文档迁移完成，When 枚举 `specs/`，Then 只存在 `specs/mini-coder-v0.1/` 作为 V0.1 规格目录，四份事实源文件完整且旧目录不存在。
- `AC-047` Given 仓库内全部 Markdown 文档，When 校验相对链接、内联规格路径和 AGENTS/project rule 引用，Then 全部指向现存文件且没有废弃产品名、目录 slug 或 package 标识。
- `AC-048` Given 更新后的验证证据与 Git 历史，When 审阅改名前后记录，Then 当前文档命名一致，历史测试结果、哈希、日期和提交事实未被冒充为新制品证据，原始版本仍可从 Git 历史追溯。

### REQ-019 Java 命名空间与构建入口迁移

主代码和测试代码的目录、`package`、`import`、完全限定类名、Maven `groupId`、shade/assembly 入口类与测试 fixture 标识必须统一使用 `dev.minicoder` 或 `Mini Coder` 对应形式。迁移不得改变 Agent 行为、公开 CLI 参数、Provider/Tool 合约、安全策略或报告结构，也不得通过兼容桥接继续保留废弃 package。

- `AC-049` Given 主代码和测试代码，When 枚举 Java 文件路径并解析 `package`、`import` 和完全限定类名，Then 全部位于并引用 `dev.minicoder`，不存在废弃 package 目录或声明。
- `AC-050` Given Maven 构建配置和可执行 fat JAR，When 检查 `groupId`、shade `mainClass` 与 JAR manifest，Then 均指向 `dev.minicoder`，且 `java -jar ... --version` 正常退出 0。
- `AC-051` Given 完成迁移后的受版本控制树，When 扫描文件内容与路径中的废弃人类名、slug、camel 和 package 变体，Then 命中数为 0；`.git/` 历史对象和忽略的 `target/` 不属于当前树扫描范围。
- `AC-052` Given 迁移后的最终 revision，When 执行 Java 21 `clean verify`、连续两次离线 E2E 及打包 CLI 验收，Then 全部规定命令得到预期退出码，测试数量不减少，且无需真实 API key 或公网。

### REQ-020 DeepSeek API Provider 与密钥配置

系统必须把 `deepseek` 作为第二个真实 `LlmProvider` 提供给 CLI。DeepSeek 密钥只能从 `DEEPSEEK_API_KEY` 读取；模型按 `--model` 高于 `DEEPSEEK_MODEL` 的优先级选择；Base URL 按 `--base-url` 高于 `DEEPSEEK_BASE_URL`、再高于官方默认 `https://api.deepseek.com` 的优先级选择。DeepSeek Adapter 必须使用官方 Responses API，封装其无状态续接差异，不得把 DeepSeek 线协议或密钥泄漏到 AgentRuntime、领域模型、日志和报告。

- `AC-053` Given `--provider deepseek`、合法工作区、`DEEPSEEK_API_KEY` 和显式模型，When 启动 CLI，Then 系统选择 DeepSeek Adapter，使用 Bearer 认证，将默认 Base URL 解析为 `https://api.deepseek.com/responses`；自定义 Base URL 保留已有路径并只追加一个 `/responses` 段，不自动注入 `/v1`，且报告中的 Provider 标识为 `deepseek`。
- `AC-054` Given 选择 `deepseek` 但缺少/空白 `DEEPSEEK_API_KEY` 或模型，When 启动 CLI，Then 在创建 Provider 请求和执行任何工具前以 `CONFIG_ERROR` 失败；不得回退读取 `OPENAI_API_KEY`，错误、普通日志、DEBUG、ToolResult、终端和 JSON 中不得出现原始 DeepSeek 密钥。
- `AC-055` Given DeepSeek Responses API 返回最终文本、单个或多个 function call，When Agent 继续一轮或多轮工具交互，Then 调用 ID、名称、参数、响应顺序和用量无损映射；由于官方接口不支持 `previous_response_id`，Adapter 通过有界的 Provider 私有 cursor 回放必要的已支持 output items 与 tool results，AgentRuntime 无需修改控制流。
- `AC-056` Given DeepSeek 返回 400/422、401、402、429、5xx、超时、超限或畸形响应，When Adapter 处理错误，Then 仅 429、合格 5xx 和瞬时传输错误在总时限与重试预算内重试；配置/认证/余额/协议错误不重试，并返回已脱敏、供应商标识正确的结构化 `ProviderException`。
- `AC-057` Given 未设置任何真实 Provider 密钥且无公网，When 执行默认测试、DeepSeek 合约测试和离线 E2E，Then 全部可确定性运行；真实 DeepSeek smoke 只在显式 profile、凭据、模型和用户网络授权同时存在时运行，否则默认跳过且不阻塞离线发布。
- `AC-058` Given README、CLI `--help`、示例配置和发布包，When 用户查找 Provider 配置，Then 文档准确列出 `openai`、`deepseek`、`scripted`，分别说明密钥/模型/Base URL 环境变量、优先级、安全边界和至少一个不含真实密钥的 DeepSeek 示例。

### REQ-021 Java 中文注释与作者信息

系统的全部受版本控制 Java 主代码和测试代码必须具有可维护的中文源码说明。每个 Java 文件的主要顶层类型必须以中文 Javadoc 准确描述其职责、关键边界或测试目标，并统一记录作者 `Self David` 与作者邮箱 `dsfgis@gmail.com`。注释不得改变运行行为、公开契约、测试语义或构建产物，也不得用逐行翻译代码的机械注释替代设计意图。

- `AC-059` Given `src/main/java` 与 `src/test/java` 下的全部受版本控制 `.java` 文件，When 执行源码注释覆盖检查，Then 每个文件的主要顶层 `class`、`interface`、`record` 或 `enum` 前均存在至少一句含中文字符的 Javadoc，且包含且仅包含一条 `@author Self David (dsfgis@gmail.com)` 作者记录。
- `AC-060` Given Agent Loop、Provider Adapter、Workspace/PathResolver、ApplyPatch、ProcessRunner、CommandPolicy、Redactor、CompletionGate 等复杂或高风险实现，When 人工审阅注释，Then 注释说明职责、约束、原因或不变量，不复述单行语法，不包含过时承诺、密钥或未经验证的行为声明。
- `AC-061` Given 测试源码，When 审阅类级 Javadoc 与关键测试辅助逻辑，Then 中文说明能够识别该测试类覆盖的合同、边界或故障场景，同时保留现有测试名称、断言和执行语义。
- `AC-062` Given 注释与作者信息修改后的最终 revision，When 使用 Java 21 执行源码覆盖检查、`clean verify` 和连续两次离线 E2E，Then 全部退出 0，测试数不少于修改前 65，Git diff 除注释、规格/证据及注释覆盖测试外不包含产品行为变更，并且真实 Provider smoke 不被默认触发。

## 5. 范围

### 5.1 V0.1 范围内

- Java 21 CLI 应用与 Maven Wrapper。
- Provider 无关的 Agent Loop、OpenAI Responses API Provider、DeepSeek Responses API Provider、Fake Provider。
- 六个核心工具及统一 Tool Registry/Schema/Result 契约。
- 单任务内的消息/响应续接、工具结果反馈和停止条件。
- Git 基线、差异归属、验证闭环与最终报告。
- 最低可接受安全层：工作区约束、路径规范化、命令策略、交互审批、超时、输出限制和脱敏。
- 无网络的单元/集成测试，以及受控 Spring Boot fixture 的端到端验收方案。
- `Mini Coder` 对外品牌、`mini-coder` CLI usage name 与发行制品命名。
- 全部当前文档及规格目录的 `Mini Coder` 命名迁移与链接校验。
- Java 主代码/测试代码、Maven 坐标和可执行入口的 `dev.minicoder` 命名空间迁移。
- DeepSeek Provider 的 `DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL`、`DEEPSEEK_BASE_URL` 配置、无状态 Responses API 续接适配、离线合约测试和可选真实 smoke profile。
- 全部 Java 主代码与测试代码的中文顶层 Javadoc、复杂逻辑意图注释，以及统一的作者/邮箱记录。

### 5.2 V0.1 范围外

- 模型训练、微调或本地推理引擎。
- Claude/Qwen/Ollama 等其他真实 Provider，以及 DeepSeek 专用 thinking/reasoning effort CLI 控制。
- 多 Agent、Planner/Reviewer 分工和并行编排。
- RAG、向量数据库、长期记忆、跨进程会话恢复和自动上下文压缩。
- MCP、Skills、IDE 插件、GUI、Web 服务和云执行。
- Git commit/branch/worktree/push/PR 自动化。
- 完整 OS/容器沙箱、网络命名空间或恶意代码隔离承诺。
- 自动安装 Java、Git、ripgrep、Maven 或目标项目依赖。
- 对每个字段、简单方法或单行语句进行机械逐行注释；修改非 Java 源码的署名格式；为生成文件或第三方源码补注释。

## 6. 约束与依赖

- Java 21 JDK、Git 和 ripgrep 在本机可用；构建通过 Maven Wrapper 完成。
- 真实 OpenAI/DeepSeek 验收分别需要有效 API 凭据、可用模型权限和网络；这些不是离线测试的前提，两个 Provider 的密钥不得互相回退或混用。
- DeepSeek 官方 Responses API 当前使用 Bearer Auth 和 `https://api.deepseek.com`，且不支持 `previous_response_id`；Adapter 必须在私有、有界 cursor 中维护续接所需回放数据，不得依赖服务端会话状态。
- API、模型名与账户权限会变化，因此模型必须配置化，Provider 线协议必须封装在适配器内。
- V0.1 运行目标应是受信任或一次性测试仓库；命令策略不能替代 OS 级隔离。
- 多文件补丁的事务语义仅覆盖可测试的正常进程内失败；崩溃一致性和断电恢复不在 V0.1 范围内。
- 用户原有未提交修改必须保留；系统不得通过 reset/clean/checkout 等手段恢复工作区。
- 作者邮箱将随源码公开进入 Git 历史；该公开范围来自用户的明确要求。Java 源码和新增验证代码继续使用 UTF-8，不引入额外 Javadoc/格式化依赖。

## 7. 用户故事

- `US-001`：作为学习 Agent 架构的开发者，我想观察 LLM、工具和 Agent Loop 的每一步交互，以便理解 Harness 如何工作。
- `US-002`：作为 CLI 用户，我想给 Agent 一个本地 Git 仓库和修改任务，以便它能自主查找、修改和验证代码。
- `US-003`：作为谨慎的仓库所有者，我想限制 Agent 的文件与命令权限，以便降低误操作和外部副作用风险。
- `US-004`：作为维护者，我想用 Fake Provider 稳定复现循环和失败场景，以便无需消耗 API 配额即可测试系统。
- `US-005`：作为后续 Provider 开发者，我想通过稳定接口接入新模型，以便不重写 Agent Loop。
- `US-006`：作为 CLI 用户，我想看到统一的 `Mini Coder` 品牌和 `mini-coder` 发行标识，以便文档、帮助与下载制品不会出现名称混用。
- `US-007`：作为文档读者，我想让所有当前文档和规格路径都使用 `Mini Coder` 命名，以便导航、搜索和引用结果一致。
- `US-008`：作为 Java 维护者，我想让源码、测试、Maven 坐标和入口类统一使用 `dev.minicoder`，以便代码搜索与发布坐标不再混用旧命名。
- `US-009`：作为 DeepSeek API 用户，我想通过 `DEEPSEEK_API_KEY` 和 `--provider deepseek` 运行同一套 Coding Agent Loop，以便在不改动工具、安全策略和完成门的情况下选择第二个真实模型供应商。
- `US-010`：作为 Java 维护者，我想从中文源码注释中快速理解每个类型的职责与关键约束，并能看到统一作者联系方式，以便后续审阅和维护。

## 8. 验收总则

- 所有验收以可观察的 CLI 输出、JSON 报告、Git diff、日志或自动化测试结果为证据。
- Provider 的自然语言声明不是独立证据；文件状态、进程退出码与验证结果优先。
- 未解决的 `ASM-*`/`OQ-*` 在四份规格文档获批前均不得视为最终需求。
