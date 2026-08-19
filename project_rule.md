# Mini Coder V0.1 Project Rules

## 1. Purpose

This document consolidates implementation rules derived from the current project specifications. It is an operational guide, not an independent source of requirements. The current four-document set, including `REQ-018` through `REQ-021`, was explicitly approved by the user on 2026-08-19.

Canonical specification documents:

- `specs/mini-coder-v0.1/requirements.md`
- `specs/mini-coder-v0.1/design.md`
- `specs/mini-coder-v0.1/tasks.md`
- `specs/mini-coder-v0.1/check_list.md`

If this document conflicts with a canonical spec, the canonical spec wins. Do not resolve material conflicts by guessing; update the specs and obtain approval.

## 2. Decision status

The following defaults from the spec were accepted with the four-document approvals completed through 2026-08-19:

- Java 21.
- Maven single-module project with Maven Wrapper.
- Picocli for CLI, Jackson for JSON, SLF4J/Logback for logging, JUnit 5 for tests.
- OpenAI Responses API as the first real Provider; model name is runtime configuration.
- `ScriptedLlmProvider` as the offline deterministic Provider.
- DeepSeek is the second real Provider, uses its own Responses Adapter and only reads `DEEPSEEK_API_KEY`; model aliases remain runtime configuration.
- DeepSeek-specific thinking/reasoning effort CLI controls remain deferred; the Adapter uses bounded stateless replay because `previous_response_id` is unavailable.
- Windows 11 is the primary development environment; deliberate cross-platform blockers are prohibited.
- The target workspace must already be a Git repository.
- V0.1 provides policy controls but not OS/container isolation.
- The public product name is `Mini Coder`; the CLI and release artifact identifier is `mini-coder`; the Java namespace and Maven `groupId` are `dev.minicoder`; the canonical specification directory is `specs/mini-coder-v0.1/`.
- Every tracked Java source under `src/main/java` and `src/test/java` has targeted Chinese type-level Javadoc and exactly one `@author Self David (dsfgis@gmail.com)` record; complex or high-risk logic receives concise Chinese intent comments instead of line-by-line narration.

Any still-deferred `ASM-*`, `OQ-*`, and `DD-OPEN-*` entry must remain visible. A future implementation choice must not silently close it.

## 3. Scope guard

### 3.1 Required V0.1 capability

- One CLI run handles one coding task in one local Git workspace.
- One Provider-neutral Agent loop coordinates model calls and local tool calls.
- Product tools are limited to:
  - `list_files`
  - `read_file`
  - `search_code`
  - `apply_patch`
  - `shell`
  - `git_diff`
- The system records a Git baseline before the first model call.
- The system loops through observation, modification, verification, and completion assessment.
- Final output includes status, reason, changed files, baseline attribution, verification evidence, warnings, usage summary, and `runId`.

### 3.2 Explicitly out of scope

- Model training, fine-tuning, or local inference engines.
- Other real Providers beyond OpenAI and DeepSeek.
- DeepSeek-specific thinking/reasoning effort CLI controls.
- Multi-agent orchestration, Planner/Reviewer agents, and parallel agent execution.
- RAG, vector databases, long-term memory, cross-process session recovery, and automatic context compaction.
- MCP, Skills, IDE plugins, GUI, web service, and cloud execution.
- Automatic Git commits, branches, worktrees, pushes, pull requests, resets, or cleans.
- Dependency auto-installation and package publishing.
- Claims of safe malicious-code execution or complete OS/container sandboxing.

Any request that adds an out-of-scope capability is a requirement change, not a small implementation detail.

## 4. Required package boundaries

Use the design's package responsibilities:

```text
cli/            command entry and argument parsing
config/         configuration, secret wrappers, dependency preflight
agent/          AgentRuntime, state machine, budgets, CompletionGate
llm/            Provider-neutral contracts and concrete adapters
tool/           Tool contracts, registry, schema validation, implementations
workspace/      canonical root, path guard, Git baseline
security/       command classification, approval, redaction
report/         shared RunReport, console and JSON renderers
observability/  structured RunEvent and sinks
```

Dependency direction rules:

- `agent` may depend on domain interfaces but not concrete Provider or tool classes.
- Concrete tools may depend on workspace/security/process services.
- Provider adapters may depend on HTTP/JSON libraries but may not leak adapter-specific types into core packages.
- CLI assembles dependencies; core packages must not reach back into CLI.
- Reporting consumes immutable run facts; it must not re-run tools to reconstruct results.

## 5. Domain and interface rules

- Prefer immutable records/value objects for requests, responses, events, evidence, and reports.
- Use explicit status/error enums instead of parsing human-readable messages.
- Preserve Provider response IDs, tool-call IDs, and opaque continuation state without loss.
- `ProviderCursor` is opaque outside the concrete Provider adapter.
- `ToolDefinition` includes stable name, concise purpose, and JSON Schema.
- Validate tool name and arguments before entering a tool executor.
- Every `ToolResult` includes status, summary, structured data, truncation state, duration, and an optional structured error.
- Unknown tools and invalid arguments return `INVALID_TOOL_CALL` without side effects.
- New tools are added through registration, never through `if/switch` branches in `AgentRuntime`.

## 6. Agent loop rules

The required loop is:

1. Build a Provider request from the task, tool definitions, new tool results, and continuation state.
2. Call the Provider within retry, time, and cancellation budgets.
3. If tool calls are returned, validate and execute them serially in response order.
4. Associate every result with its original `toolCallId` and continue the Provider conversation.
5. Record state transitions, progress fingerprints, workspace revisions, and verification evidence.
6. If final text is returned, evaluate it through `CompletionGate`.
7. Stop only on an evidence-calibrated terminal state or an explicit limit/error/cancellation condition.

The loop must enforce:

- maximum iterations;
- total elapsed-time limit;
- Provider retry budget with bounded backoff and jitter;
- cancellation;
- repeated equivalent call detection;
- no-progress detection;
- no retries that extend beyond the total run deadline.

The model can propose actions and explanations. It cannot override policy, tool results, Git facts, exit codes, or CompletionGate.

## 7. Workspace and filesystem rules

- Convert the configured workspace to a canonical absolute real path before any Provider call.
- Reject nonexistent paths, non-directories, inaccessible paths, and non-Git repositories.
- Resolve every tool path through one shared `PathResolver`/`WorkspaceGuard`.
- Reject absolute paths supplied to relative-path tools.
- Normalize `.` and `..`, resolve existing parent links, and check the final real location using filesystem semantics rather than string-prefix comparison.
- Apply the same boundary checks to read, list, search, patch, process working directory, Git, temporary, and recovery paths.
- Account for Windows drive letters, case behavior, junctions, spaces, Unicode names, and CRLF.
- Put explicit byte/line/item/depth limits on file and search results.
- Distinguish no match, not found, binary content, decoding failure, policy denial, and truncation.

## 8. Patch rules

- `apply_patch` accepts unified diff only; do not add an arbitrary whole-file overwrite product tool.
- Reject absolute paths, workspace escapes, malformed diffs, and invalid target types before modification.
- Preflight all target files and all hunks before committing any change.
- Compute intended contents in memory, then use same-filesystem temporary files and controlled replacement.
- In normal in-process failure scenarios, roll back all already-applied targets.
- A rollback failure is terminal `WORKSPACE_INCONSISTENT`; report exact potentially inconsistent files and stop.
- Do not claim cross-file atomicity across power loss, process crashes, or malicious filesystem behavior.
- Increment `workspaceRevision` once for a successful logical patch and invalidate verification from earlier revisions.

## 9. Process and command rules

- Default execution uses `ProcessBuilder(List<String>)` with the workspace root as the working directory.
- Do not compose commands by concatenating user/model strings.
- `shellMode=none` is the default. Pipes, redirection, substitution, and other shell syntax require an explicit supported shell mode and policy approval.
- Consume stdout and stderr concurrently to prevent deadlock.
- Enforce per-command timeout and bounded output. Preserve the configured head/tail window plus original byte counts and a truncation flag.
- On timeout/cancellation, terminate the process and its descendants; report whether cleanup succeeded.
- A nonzero command exit is normally an observation for the Agent, not automatically an infrastructure failure.

Command-risk rules:

| Classification | Required behavior |
|---|---|
| `ALLOW` | Run only known local read/verification commands within the workspace. |
| `REQUIRE_APPROVAL` | Show a redacted preview and reason; run only after explicit approval. Non-interactive mode denies it. |
| `DENY` | Never start the process in V0.1. |

Always deny recursive broad deletion, workspace escape, Git history rewriting, credential-directory access, privilege escalation, and policy bypass. Require approval for network access, remote writes, publishing, installation, container lifecycle changes, and unclear external side effects.

## 10. Git rules

- Before the first Provider request capture HEAD (if present), porcelain status, staged/unstaged diff, and untracked-file metadata required for attribution.
- Distinguish `PREEXISTING`, `AGENT_CREATED`, `AGENT_MODIFIED`, `OVERLAPS_PREEXISTING_CHANGE`, and `UNKNOWN`.
- Never claim the whole final diff belongs to the Agent when it overlaps a pre-existing change.
- `git_diff` is read-only and output-bounded.
- Do not automatically run commit, reset, clean, checkout, rebase, merge, push, or branch/worktree mutation.
- Never discard or overwrite user changes as a recovery shortcut.

## 11. Provider rules

- The real V0.1 adapters target the OpenAI and DeepSeek Responses APIs, but model and endpoint settings remain configuration.
- OpenAI and DeepSeek use dedicated API key, model, and Base URL environment variables; never fall back to another Provider's key.
- DeepSeek uses a private replay cursor instead of `previous_response_id`; bound it to 30 rounds, 512 items, and 1 MiB, and do not expose reasoning contents in logs or reports.
- Keep wire-schema mapping, function-call mapping, continuation handling, usage parsing, and Provider error classification inside the adapter.
- Preserve `call_id`/response association across turns.
- Retry only classified transient failures such as eligible rate limits, selected 5xx responses, and transient transport errors.
- Treat authentication, authorization, incompatible schema, and malformed responses as non-retryable unless evidence says otherwise.
- Bound retries by count and total run deadline.
- Unit/contract tests use mocked HTTP fixtures; default CI must not use a real key or network.
- Real Provider smoke tests are opt-in and must use an explicitly provided model and disposable workspace.

## 12. Security and privacy rules

- Secrets must come from environment variables or an approved secure source, not a required plaintext CLI argument.
- Never log complete environment variables, headers, prompts containing secrets, credential-file contents, or raw Provider failures without redaction.
- Apply one `Redactor` at terminal, structured log, exception, ToolResult, and report boundaries.
- At minimum, redact exact configured secret values and common bearer/API-key forms.
- Approval records may contain the redacted command, classification, reason, decision, and time; they must not retain raw secrets.
- Keep the V0.1 warning visible in README and CLI help: policy controls reduce accidental harm but do not isolate untrusted build scripts or child processes.

## 13. Completion and reporting rules

- Provider final text is a completion proposal only.
- After any file modification, complete success requires relevant verification with exit code 0 at the current final workspace revision.
- If `--verify-command` is supplied, its current-revision success is mandatory for `SUCCEEDED`.
- Verification failure with remaining budget is returned to the Agent for another attempt.
- Missing, failed, or stale verification must produce warning/failure accurately; never upgrade it to success for presentation.
- Console and JSON outputs must render the same immutable `RunReport` facts.
- Stable statuses/error codes and exit codes must distinguish success, warning success, cancellation, policy block, configuration error, Provider error, tool error, no progress, inconsistent workspace, and limit reached.

## 14. Observability rules

Every structured event includes applicable correlation fields:

- `runId`
- iteration number
- response ID
- `toolCallId`
- event type and status
- duration
- retry/approval/truncation metadata

The event stream must be sufficient to reconstruct Provider calls, tool calls, approvals, workspace revisions, verification, and terminal-state order. Default logs must not include full file content, full prompts, or secrets. DEBUG mode does not relax redaction.

## 15. Testing rules

### 15.1 Default test properties

- No real API credentials.
- No public network dependency.
- No mutation outside temporary test roots.
- No destructive commands against the developer workspace.
- Deterministic Scripted Provider scenarios.
- Temporary Git repositories with explicit fixtures.

### 15.2 Required scenario families

- CLI success, help, and missing configuration.
- Invalid/non-Git workspace and path/link escape.
- Provider final answer, single/multiple tools, continuation, transient/permanent failures.
- Iteration/time/cancellation/no-progress limits.
- Tool schema rejection and uniform results.
- List/read/search success, no match, encoding, binary, missing `rg`, and truncation.
- Patch success, conflict, multi-file controlled failure, rollback failure, and revision invalidation.
- Process stdout/stderr, nonzero exit, timeout, output truncation, and process-tree cleanup.
- Command allow/approval/deny and non-interactive rejection.
- Git attribution with clean, pre-existing, and overlapping changes.
- Verification success, failure/retry, missing evidence, and stale evidence.
- Redaction across normal and DEBUG outputs.
- Windows spaces, Unicode paths, junctions, and CRLF.

### 15.3 Required verification commands

Use the exact commands in each `TASK-*`. Project-wide gates are expected to include:

```powershell
.\mvnw.cmd -q clean verify
.\mvnw.cmd -q -Poffline-e2e verify
```

The real Provider command is optional and requires explicit authorization:

```powershell
.\mvnw.cmd -q -Popenai-smoke verify
```

Do not run the smoke profile merely to make default CI pass.

## 16. Evidence and checklist rules

- `tasks.md` says what to build; `check_list.md` says what evidence proves it.
- Do not check off a `CHECK-*` item before its method has actually run and its observable result matches.
- Evidence must name the environment, command, exit code, relevant log/report/diff/hash, and date when useful.
- A skipped optional check must be marked `N/A` with a reason, not falsely checked as passed.
- Before release, verify all `AC-001` through `AC-062` remain covered by tasks and checks.

## 17. Change control

Treat any of the following as a material specification change:

- adding/removing a product tool;
- changing Provider scope or conversation semantics;
- changing success/completion criteria;
- weakening path, command, approval, redaction, or Git protections;
- adding persistent state, network services, Git writes, multi-agent behavior, MCP, memory, or sandbox claims;
- changing public CLI/report/error contracts;
- changing the approved build/test strategy.

For a material change:

1. stop affected implementation;
2. revise `requirements.md` first;
3. revise `design.md`, then `tasks.md`, then `check_list.md`;
4. rerun ID and traceability checks;
5. request approval for all four current documents;
6. resume only after approval.

## 18. Definition of done

A task is done only when:

- its deliverable exists and stays within approved scope;
- its local verification passes;
- relevant error and boundary cases are covered;
- no secret or unrelated user change is introduced;
- evidence is recorded in applicable checks;
- dependent documentation is updated when behavior changes.

V0.1 is ready only when the offline full build and E2E suite pass, reports are evidence-calibrated, release documentation states the safety boundary accurately, and all required checklist items contain real evidence.
