# Mini Coder V0.1

Mini Coder 是一个用于学习 Coding Agent Harness 的 Java 21 命令行项目。它实现单 Agent 循环、OpenAI Responses API 适配、六个本地工具、Git 基线归属、命令策略、验证完成门和证据报告；它不是模型训练项目，也不是完整 Codex 的复刻。

## 环境要求

- JDK 21（必须确认运行 Maven 的也是 JDK 21）
- Git
- ripgrep（`rg`）
- Windows PowerShell；项目主要验证入口为 `mvnw.cmd`
- 真实运行时需要 `OPENAI_API_KEY`，模型由 `--model` 或 `OPENAI_MODEL` 指定

检查环境：

```powershell
java -version
git --version
rg --version
```

若系统默认 Java 不是 21，可仅为当前 PowerShell 会话指定：

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21.0.11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## 构建与测试

```powershell
.\mvnw.cmd -q clean verify
.\mvnw.cmd -q -Poffline-e2e verify
```

默认测试和 `offline-e2e` 不访问公网，也不读取真实 API 凭据。离线 E2E 使用 `ScriptedLlmProvider`、临时 Git 仓库和已缓存依赖的真实 Spring Boot fixture，先以 `mvn.cmd -o test` 复现服务空指针，再应用补丁并用同一命令验证。

生成物：

- `target/mini-coder-0.1.0-SNAPSHOT-all.jar`：可执行 fat JAR
- `target/mini-coder-0.1.0-SNAPSHOT-dist.zip`：分发包

## 配置与运行

密钥只从环境变量读取，不接受明文命令行密钥参数：

```powershell
$env:OPENAI_API_KEY='<your-key>'
$env:OPENAI_MODEL='gpt-5'
java -jar target\mini-coder-0.1.0-SNAPSHOT-all.jar `
  --workspace D:\path\to\trusted-repo `
  --task "修复失败测试并验证" `
  --verify-command "mvn test" `
  --json-report D:\path\to\run-report.json
```

可用参数以 `--help` 为准。`OPENAI_BASE_URL` 可覆盖默认 API 根地址；若地址已以 `/v1` 结尾，适配器不会重复追加。

V0.1 只向模型暴露：`list_files`、`read_file`、`search_code`、`apply_patch`、`shell`、`git_diff`。多个工具调用严格按 Provider 返回顺序串行执行。

无需密钥的 CLI/报告离线示例（不修改工作区，预期退出码 10）：

```powershell
java -jar target\mini-coder-0.1.0-SNAPSHOT-all.jar --workspace . --task "inspect only" --provider scripted --non-interactive
```

## 成功判定

模型的最终文本只会触发 `CompletionGate`，不会直接决定成功：

- 文件修改会增加 workspace revision，并使旧验证失效。
- 最终 revision 上存在相关且退出码为 0 的验证，才可返回 `SUCCEEDED`。
- 没有验证或验证过期返回 `SUCCEEDED_WITH_WARNINGS`（退出码 10）。
- 最终 revision 的相关验证明确失败返回 `TOOL_ERROR`。
- 指定 `--verify-command` 后，其有序命令 token 必须由成功证据覆盖。

退出码：

| 状态 | 退出码 |
|---|---:|
| `SUCCEEDED` | 0 |
| `SUCCEEDED_WITH_WARNINGS` | 10 |
| `CANCELLED` | 11 |
| `CONFIG_ERROR` | 20 |
| `POLICY_BLOCKED` | 21 |
| `PROVIDER_ERROR` | 22 |
| `TOOL_ERROR` / `WORKSPACE_INCONSISTENT` | 30 |
| `LIMIT_REACHED` | 40 |
| `NO_PROGRESS` | 41 |

## 安全边界

V0.1 的 `WorkspaceGuard`、`CommandPolicy`、审批和脱敏是应用层防护，不是 OS 沙箱。只应在受信任仓库或隔离、可丢弃的副本中运行。

- 路径必须位于解析后的工作区根目录内；绝对路径、`..`、符号链接和 junction 越界会被拒绝。
- 本地只读/验证命令可自动允许；网络、安装、发布和外部副作用需要明确审批；破坏性操作、Git 历史改写、凭据访问、提权和工作区越界在 V0.1 拒绝。
- 不会自动运行 `git commit/reset/clean/checkout/push`。
- `shell` 默认使用 `ProcessBuilder(List<String>)`；只有模型显式选择 shell mode 时才解释 shell 语法，并仍需策略审批。
- API key、Bearer token 和常见密钥形式会在异常、工具输出和报告边界脱敏，但这不替代隔离环境。

## 可选真实 OpenAI Smoke Test

该测试默认跳过。只有用户明确授权联网并设置凭据后执行：

```powershell
$env:OPENAI_API_KEY='<your-key>'
$env:OPENAI_MODEL='gpt-5'
.\mvnw.cmd -q -Popenai-smoke verify
```

测试只进行固定的 `read_file` function-call/续接往返，工具结果来自内存 fixture，不读取或修改仓库。不要在日志或缺陷报告中粘贴密钥。

## 项目范围

V0.1 不支持 DeepSeek、多 Agent、RAG、MCP、长期记忆、GUI、云执行、自动 Git 写操作或强沙箱声明。范围或公共 CLI/报告合同变化时，必须先修改四份规格并重新确认。

开发与架构细节见 [ARCHITECTURE.md](ARCHITECTURE.md)，规格事实源位于 `specs/mini-codex-v0.1/`。
