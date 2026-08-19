# Changelog

## 0.1.0 - 2026-08-19

- 将产品品牌、CLI usage name 与发布制品统一为 `Mini Coder` / `mini-coder`，并将 Java namespace、Maven `groupId`、入口类和规格目录统一迁移到 `dev.minicoder` 与 `specs/mini-coder-v0.1/`。
- 建立 Java 21 / Maven 单模块项目与可执行 fat JAR。
- 实现 Provider-neutral Agent Loop、Scripted test Provider 和 OpenAI Responses API adapter。
- 实现六个 V0.1 工具、工作区路径边界、事务式 patch、命令策略/审批、进程限制和只读 Git 归属。
- 实现 revision-aware CompletionGate、结构化事件、稳定退出码及终端/JSON 报告。
- 增加无网络默认测试、离线 E2E 与默认跳过的真实 OpenAI smoke profile。
- 增加 DeepSeek Responses API Adapter、`DEEPSEEK_API_KEY`/模型/Base URL 隔离配置、无状态工具调用回放、离线合同测试与默认跳过的真实 DeepSeek smoke profile。

本版本不包含其他真实 Provider、DeepSeek 专用推理参数、多 Agent、RAG、MCP、长期记忆、GUI、云执行、自动 Git 写操作或 OS 级沙箱。
