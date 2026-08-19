# Mini Coder V0.1 Agent Instructions

## Instruction scope

These instructions apply to the entire repository.

Before doing repository work, read the following files in order:

1. `project_rule.md`
2. `specs/mini-coder-v0.1/requirements.md`
3. `specs/mini-coder-v0.1/design.md`
4. `specs/mini-coder-v0.1/tasks.md`
5. `specs/mini-coder-v0.1/check_list.md`

The four files under `specs/mini-coder-v0.1/` are the source of truth. `project_rule.md` and this file summarize them and must not silently expand or contradict their scope.

## Current phase and approval gate

- The four Mini Coder V0.1 spec documents, including `REQ-017` through `REQ-020`, and their default decisions were explicitly approved by the user on 2026-08-19; the repository is in implementation/verification phase.
- Do not create or modify product source, tests, schemas, dependencies, generated code, build/deployment configuration, or infrastructure until the user explicitly approves the current versions of all four spec documents.
- Creating or refining documentation is allowed before approval, but material changes to requirements, design, tasks, or verification invalidate any earlier approval.
- If implementation reveals a material mismatch, stop, revise the affected spec documents, run the traceability review again, and obtain renewed approval.

## Working method

1. Check the request for incorrect assumptions, missing evidence, scope expansion, and conflicts with the specs.
2. Distinguish confirmed facts from assumptions and unresolved decisions. Never turn `ASM-*`, `OQ-*`, or `DD-OPEN-*` into settled requirements without approval.
3. After approval, implement in `TASK-001` through `TASK-021` order unless a task explicitly permits parallel work and all dependencies are satisfied.
4. Keep changes small and independently verifiable. Run each task's stated verification before starting a dependent task.
5. Update task/check status only from actual evidence. Never mark a checkbox complete because code appears correct.
6. Preserve user changes and report blockers or newly discovered requirement changes instead of working around them silently.

## Non-negotiable architecture rules

- Target Java 21 and the approved Maven single-module layout.
- Keep `AgentRuntime` independent of Provider wire formats and concrete tool implementations.
- Keep Provider SDK/JSON types inside Provider adapters; core domain models must remain Provider-neutral.
- Keep OpenAI and DeepSeek credentials/configuration isolated; DeepSeek continuation state stays private, bounded, and must not add Provider branches to `AgentRuntime`.
- Register tools through `ToolRegistry`; do not add tool-specific branches to the Agent loop.
- V0.1 exposes only `list_files`, `read_file`, `search_code`, `apply_patch`, `shell`, and `git_diff` as product tools.
- Execute multiple tool calls serially in Provider response order in V0.1.
- A model's final text triggers `CompletionGate`; it is not proof of success.
- Any successful file modification increments the workspace revision and invalidates older verification evidence.

## Safety rules

- Resolve and verify real paths before every filesystem action. Reject paths that escape the canonical workspace root through absolute paths, `..`, symlinks, or junctions.
- Use `ProcessBuilder(List<String>)` by default. Do not concatenate command strings or invoke a shell implicitly.
- Apply command policy before process creation:
  - local read/verification commands may be allowed;
  - network access, remote writes, publishing, installation, and other external side effects require explicit approval;
  - destructive operations, Git history rewriting, workspace escape, credential access, and privilege escalation are denied in V0.1.
- Never expose API keys, complete environment dumps, credential-file contents, or bearer tokens in logs, errors, ToolResults, fixtures, or reports.
- Do not claim that command policy is an OS sandbox. V0.1 must be described as suitable only for trusted repositories or isolated disposable copies.
- Do not automatically run `git commit`, `git reset`, `git clean`, `git checkout`, `git push`, publish commands, or destructive recovery commands.

## Testing and evidence

- Default tests must run without network access or real API credentials.
- Use `ScriptedLlmProvider`, mocked HTTP responses, and temporary Git repositories for deterministic tests.
- Use the Windows verification commands documented in `tasks.md`, primarily `.\mvnw.cmd`.
- Cover success, OpenAI/DeepSeek Provider failures, DeepSeek stateless replay limits, validation failures, time/iteration limits, no-progress loops, path escape, patch conflicts, approval denial, output truncation, process-tree timeout cleanup, and pre-existing Git changes.
- Full success after a code change requires relevant verification with exit code 0 after the final workspace revision. Otherwise report warning/failure accurately.
- Record commands, exit codes, logs, diffs, hashes, or report artifacts in the matching `CHECK-*` evidence field before checking it off.

## Code review rules

- Flag any behavior that bypasses `WorkspaceGuard`, `ToolRegistry`, `CommandPolicy`, `Redactor`, or `CompletionGate`.
- Flag Provider-specific types leaking into `agent`, `tool`, `workspace`, `security`, or `report` domain packages.
- Flag unbounded file, search, process, Provider, log, or diff output.
- Flag retries without explicit budgets or retries that bypass the total run deadline.
- Flag claims of success that are not supported by current-revision verification evidence.
- Flag new V0.1 features involving other real Providers, DeepSeek-specific reasoning controls, multi-agent orchestration, RAG, MCP, memory, GUI, Git writes, cloud execution, or strong sandbox claims unless the specs were revised and re-approved.

## Communication

- Use Chinese for project documentation and user-facing summaries unless the user requests another language.
- Use English for Java identifiers, protocol fields, status codes, filenames required by tools, and code-level terminology where translation would reduce precision.
- Lead with the outcome, cite concrete files/tests/evidence, and state unverified assumptions explicitly.
