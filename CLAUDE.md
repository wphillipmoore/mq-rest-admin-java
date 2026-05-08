# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Standards reference**: <https://github.com/wphillipmoore/standards-and-conventions>
— active standards documentation lives in the standard-tooling repository under `docs/`.
Repository profile: `standard-tooling.toml`.

## Memory management

Memory is allowed with human approval. The authoritative policy is in
the user's global `~/.claude/CLAUDE.md` — agents must propose memory
writes and suggest a destination (repo memory, global CLAUDE.md, or
plugin/skill issue) before writing. See that file for the full
workflow.

Available skills:
- `/standard-tooling:memory-init` — set up or update the policy header
  in a project's `MEMORY.md`.
- `/standard-tooling:memory-audit` — structured collaborative review
  of memory files.

## Parallel AI agent development

This repository supports running multiple Claude Code agents in parallel via
git worktrees. The convention keeps parallel agents' working trees isolated
while preserving shared project memory (which Claude Code derives from the
session's starting CWD).

**Canonical spec:**
[`standard-tooling/docs/specs/worktree-convention.md`](https://github.com/wphillipmoore/standard-tooling/blob/develop/docs/specs/worktree-convention.md)
— full rationale, trust model, failure modes, and memory-path implications.
The canonical text lives in `standard-tooling`; this section is the local
on-ramp.

### Structure

```text
~/dev/github/mq-rest-admin-java/          ← sessions ALWAYS start here
  .git/
  CLAUDE.md, src/, …                      ← main worktree (usually `develop`)
  .worktrees/                             ← container for parallel worktrees
    issue-261-adopt-worktree-convention/  ← worktree on feature/261-...
    …
```

### Rules

1. **Sessions always start at the project root.**
   `cd ~/dev/github/mq-rest-admin-java && claude` — never from inside
   `.worktrees/<name>/`. This keeps the memory-path slug stable and shared.
2. **Each parallel agent is assigned exactly one worktree.** The session
   prompt names the worktree (see Agent prompt contract below).
   - For Read / Edit / Write tools: use the worktree's absolute path.
   - For Bash commands that touch files: `cd` into the worktree first,
     or use absolute paths.
3. **The main worktree is read-only.** All edits flow through a worktree
   on a feature branch — the logical endpoint of the standing
   "no direct commits to `develop`" policy.
4. **One worktree per issue.** Don't stack in-flight issues. When a
   branch lands, remove the worktree before starting the next.
5. **Naming: `issue-<N>-<short-slug>`.** `<N>` is the GitHub issue
   number; `<short-slug>` is 2–4 kebab-case tokens.

### Agent prompt contract

When launching a parallel-agent session, use this template (fill in the
placeholders):

```text
You are working on issue #<N>: <issue title>.

Your worktree is: /Users/pmoore/dev/github/mq-rest-admin-java/.worktrees/issue-<N>-<slug>/
Your branch is:   feature/<N>-<slug>

Rules for this session:
- Do all git operations from inside your worktree:
    cd <absolute-worktree-path> && git <command>
- For Read / Edit / Write tools, use the absolute worktree path.
- For Bash commands that touch files, cd into the worktree first
  or use absolute paths.
- Do not edit files at the project root. The main worktree is
  read-only — all changes flow through your worktree on your
  feature branch.
```

All fields are required.

## Project Overview

Java wrapper for the IBM MQ administrative REST API, ported from `pymqrest` (Python). Provides method-per-command API (`displayQueue()`, `defineQlocal()`, etc.) with attribute mapping between snake_case and MQSC parameter names.

**Build coordinates**: `io.github.wphillipmoore:mq-rest-admin:1.1.1`

**Java package**: `io.github.wphillipmoore.mq.rest.admin`

**Canonical Standards**: <https://github.com/wphillipmoore/standards-and-conventions> (local: `../standards-and-conventions`)

**Reference implementation**: `../mq-rest-admin-python`

## Development Commands

### Environment

- **Java**: 17+ (install via `brew install openjdk@17` or SDKMAN)
- **Maven**: Provided by Maven Wrapper (`./mvnw`), no separate install needed
- **Git hooks**: `git config core.hooksPath ../standard-tooling/scripts/lib/git-hooks`
- **Standard tooling**: CLI tools (`st-commit`, `st-validate`, etc.) are pre-installed in the dev container images

### CI

PR CI triggers on `pull_request`. Runs common quality checks (yamllint,
markdownlint, shellcheck, actionlint), full Java matrix (17, 21, 25-ea)
for unit tests, integration tests with MQ containers, security scanners
(CodeQL, Trivy, Semgrep), standards compliance, and release gates.
Workflow: `.github/workflows/ci.yml`.

### Validation

```bash
st-docker-run -- st-validate   # Full validation (runs in dev container)
```

### Build and Validate

```bash
./mvnw compile              # Compile sources
./mvnw verify               # Full pipeline: Spotless → Checkstyle → compile → unit tests →
                            # integration tests → JaCoCo (100% line+branch) → SpotBugs → PMD
./mvnw spotless:apply       # Auto-format code (run before committing)
```

### Testing

```bash
./mvnw test                                        # All unit tests (*Test.java)
./mvnw test -Dtest=MqRestSessionTest               # Single test class
./mvnw test -Dtest="MqRestSessionTest#testMethod"  # Single test method
./mvnw verify                                      # Unit + integration tests (*IT.java)
```

- **Framework**: JUnit 5 (Jupiter)
- **Mocking**: Mockito 5 with `@ExtendWith(MockitoExtension.class)`
- **Assertions**: AssertJ (`assertThat(x).isEqualTo(y)`)
- **Coverage**: JaCoCo — 100% line and branch coverage enforced at BUNDLE level

### Individual Lint Tools

```bash
./mvnw spotless:check      # Check formatting (google-java-format, 2-space indent)
./mvnw checkstyle:check    # Check style (google_checks.xml)
./mvnw spotbugs:check      # Bug patterns (effort=Max, threshold=Low)
./mvnw pmd:check           # Code smell detection
```

### Local MQ Container

The MQ development environment is owned by the
[mq-rest-admin-dev-environment](https://github.com/wphillipmoore/mq-rest-admin-dev-environment)
repository. Clone it as a sibling directory before running lifecycle
scripts:

```bash
# Prerequisite (one-time)
git clone https://github.com/wphillipmoore/mq-rest-admin-dev-environment.git ../mq-rest-admin-dev-environment

# Start the containerized MQ queue managers
./scripts/dev/mq_start.sh

# Seed deterministic test objects (DEV.* prefix)
./scripts/dev/mq_seed.sh

# Verify REST-based MQSC responses
./scripts/dev/mq_verify.sh

# Stop the queue managers
./scripts/dev/mq_stop.sh

# Reset to clean state (removes data volumes)
./scripts/dev/mq_reset.sh
```

The lifecycle scripts are thin wrappers that delegate to
`../mq-rest-admin-dev-environment`. Override the path with `MQ_DEV_ENV_PATH`.

Integration tests are gated by the `MQ_REST_ADMIN_RUN_INTEGRATION`
environment variable. When unset, integration tests are skipped. For local
runs:

```bash
./scripts/dev/mq_start.sh
./scripts/dev/mq_seed.sh
export MQ_REST_ADMIN_RUN_INTEGRATION=true
./mvnw verify    # Unit + integration tests
```

Container details:

- Queue managers: `QM1` and `QM2`
- QM1 ports: `1424` (MQ listener), `9453` (REST API)
- QM2 ports: `1425` (MQ listener), `9454` (REST API)
- Admin credentials: `mqadmin` / `mqadmin`
- Object prefix: `DEV.*`

## Architecture

Direct port of pymqrest's architecture, adapted to Java idioms.

### Key Design Decisions

- **Zero runtime deps** beyond Gson (~280KB) — HTTP via `java.net.http.HttpClient`
- **Sealed types**: `MqRestException` hierarchy (unchecked) and `Credentials` interface
- **Nullability**: JSpecify `@Nullable` annotations; Error Prone + NullAway enforced on JDK 21+ (auto-activated `error-prone` profile)
- **Dynamic attributes**: `Map<String, Object>` for MQ attributes; Java records for fixed-schema types (`TransportResponse`, `SyncConfig`, `SyncResult`, `EnsureResult`, `MappingIssue`)
- **Testability**: `MqRestTransport` interface enables Mockito-based testing without HTTP

### Attribute Mapping Pipeline

3-layer pipeline (key map → value map → key-value map) with request/response directions. Strict mode throws `MappingException`; permissive mode records `MappingIssue` entries. Mapping data in `src/main/resources/.../mapping/mapping-data.json`.

### Exception Hierarchy

```text
MqRestException (sealed, RuntimeException)
├── MqRestTransportException   (network/connection)
├── MqRestResponseException    (malformed JSON)
├── MqRestAuthException        (auth failures)
├── MqRestCommandException     (MQSC errors)
└── MqRestTimeoutException     (polling timeout)

MappingException (separate, data-transformation errors)
```

### Package Layout

```text
.admin          — MqRestSession (Builder pattern), MqRestTransport, HttpClientTransport, TransportResponse
.admin.auth     — Credentials (sealed), BasicAuth, LtpaAuth, CertificateAuth
.admin.exception — Sealed exception hierarchy
.admin.mapping  — AttributeMapper, MappingData, MappingIssue, MappingDirection, MappingOverrideMode
.admin.sync     — SyncConfig, SyncResult, SyncOperation
.admin.ensure   — EnsureResult, EnsureAction
```
