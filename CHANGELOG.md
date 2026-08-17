# Changelog

## 0.1.0 - 2026-08-17

- 将产品品牌统一为 `Mini Coder`，CLI usage name 与发布制品统一为 `mini-coder`；内部 `dev.minicodex` package 和既有规格目录保持不变。
- 建立 Java 21 / Maven 单模块项目与可执行 fat JAR。
- 实现 Provider-neutral Agent Loop、Scripted test Provider 和 OpenAI Responses API adapter。
- 实现六个 V0.1 工具、工作区路径边界、事务式 patch、命令策略/审批、进程限制和只读 Git 归属。
- 实现 revision-aware CompletionGate、结构化事件、稳定退出码及终端/JSON 报告。
- 增加无网络默认测试、离线 E2E 与默认跳过的真实 OpenAI smoke profile。

本版本不包含 DeepSeek、多 Agent、RAG、MCP、长期记忆、GUI、云执行、自动 Git 写操作或 OS 级沙箱。
