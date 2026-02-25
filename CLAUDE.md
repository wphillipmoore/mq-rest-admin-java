# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- include: docs/standards-and-conventions.md -->
<!-- include: docs/repository-standards.md -->

## Auto-memory policy

**Do NOT use MEMORY.md.** Claude Code's auto-memory feature stores behavioral
rules outside of version control, making them invisible to code review,
inconsistent across repos, and unreliable across sessions. All behavioral rules,
conventions, and workflow instructions belong in managed, version-controlled
documentation (CLAUDE.md, AGENTS.md, skills, or docs/).

If you identify a pattern, convention, or rule worth preserving:

1. **Stop.** Do not write to MEMORY.md.
2. **Discuss with the user** what you want to capture and why.
3. **Together, decide** the correct managed location (CLAUDE.md, a skill file,
   standards docs, or a new issue to track the gap).

This policy exists because MEMORY.md is per-directory and per-machine — it
creates divergent agent behavior across the multi-repo environment this project
operates in. Consistency requires all guidance to live in shared, reviewable
documentation.

## Shell command policy

**Do NOT use heredocs** (`<<EOF` / `<<'EOF'`) for multi-line arguments to CLI
tools such as `gh`, `git commit`, or `curl`. Heredocs routinely fail due to
shell escaping issues with apostrophes, backticks, and special characters.
Always write multi-line content to a temporary file and pass it via `--body-file`
or `--file` instead.

## Project Overview

Java wrapper for the IBM MQ administrative REST API, ported from `pymqrest` (Python). Provides method-per-command API (`displayQueue()`, `defineQlocal()`, etc.) with attribute mapping between snake_case and MQSC parameter names.

**Build coordinates**: `io.github.wphillipmoore:mq-rest-admin:1.1.1`

**Java package**: `io.github.wphillipmoore.mq.rest.admin`

**Canonical Standards**: https://github.com/wphillipmoore/standards-and-conventions (local: `../standards-and-conventions`)

**Reference implementation**: `../mq-rest-admin-python`

## Development Commands

### Environment

- **Java**: 17+ (install via `brew install openjdk@17` or SDKMAN)
- **Maven**: Provided by Maven Wrapper (`./mvnw`), no separate install needed
- **Standard tooling**: `cd ../standard-tooling && uv sync && export PATH="../standard-tooling/.venv/bin:../standard-tooling/scripts/bin:$PATH"`

### Three-Tier CI Model

Testing is split across three tiers with increasing scope and cost:

**Tier 1 — Local pre-commit (seconds):** Fast smoke tests in a single
container. Run before every commit. No MQ, no matrix.

```bash
./scripts/dev/test.sh        # Full verify pipeline in dev-java:21
./scripts/dev/lint.sh        # Spotless + Checkstyle in dev-java:21
./scripts/dev/audit.sh       # Dependency + license audit in dev-java:21
```

**Tier 2 — Push CI (~3-5 min):** Triggers automatically on push to
`feature/**`, `bugfix/**`, `hotfix/**`, `chore/**`. Single Java version
(21), includes integration tests, no security scanners or release gates.
Workflow: `.github/workflows/ci-push.yml` (calls `ci.yml`).

**Tier 3 — PR CI (~8-10 min):** Triggers on `pull_request`. Full Java
matrix (17, 21, 25-ea), all integration tests, security scanners (CodeQL,
Trivy, Semgrep), standards compliance, and release gates. Workflow:
`.github/workflows/ci.yml`.

### Build and Validate

```bash
st-validate-local               # Canonical validation (runs full pipeline below)
./mvnw compile              # Compile sources
./mvnw verify               # Full pipeline: Spotless → Checkstyle → compile → unit tests →
                            # integration tests → JaCoCo (100% line+branch) → SpotBugs → PMD
./mvnw spotless:apply       # Auto-format code (run before committing)
```

### Docker-First Testing

All tests can run inside containers — Docker is the only host prerequisite.
The `dev-java:21` image is built from `../standard-tooling/docker/java/`.

```bash
# Build the dev image (one-time, from standard-tooling)
cd ../standard-tooling && docker/build.sh

# Run full verify pipeline in container
./scripts/dev/test.sh

# Run lint checks in container
./scripts/dev/lint.sh

# Run dependency audit in container
./scripts/dev/audit.sh
```

Environment overrides:

- `DOCKER_DEV_IMAGE` — override the container image (default: `dev-java:21`)
- `DOCKER_TEST_CMD` — override the test command

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

## Git Conventions

### Pre-flight Checklist

1. Check current branch: `git status -sb`
2. If on `develop` or `main`, create a `feature/*`, `bugfix/*`, or `hotfix/*` branch first
3. Enable git hooks: `git config core.hooksPath ../standard-tooling/scripts/lib/git-hooks`

### Branching Model (`library-release`)

- **`develop`**: Integration branch — PRs target here (squash merge)
- **`main`**: Release branch — release PRs merge here (regular merge)
- Allowed branch prefixes: `feature/*`, `bugfix/*`, `hotfix/*`

### Commit Message Rules (enforced by hooks)

Conventional Commits format required:

```
<type>(optional-scope): <description>

Co-Authored-By: wphillipmoore-claude <255925739+wphillipmoore-claude@users.noreply.github.com>
```

Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

The co-author hook validates trailers against approved identities in `docs/repository-standards.md`. Only use the approved co-author lines listed there.

## Commit and PR Scripts

**NEVER use raw `git commit`** — always use `st-commit`.
**NEVER use raw `gh pr create`** — always use `st-submit-pr`.

### Committing

```bash
st-commit --type feat --scope session --message "add timeout option" --agent claude
st-commit --type fix --message "correct null handling in mapper" --agent claude
```

- `--type` (required): `feat|fix|docs|style|refactor|test|chore|ci|build`
- `--message` (required): commit description
- `--agent` (required): `claude` or `codex` — resolves the correct `Co-Authored-By` identity
- `--scope` (optional): conventional commit scope
- `--body` (optional): detailed commit body

### Submitting PRs

```bash
st-submit-pr --issue 42 --summary "Add timeout option to session builder"
st-submit-pr --issue 42 --linkage Ref --summary "Update docs" --docs-only
```

- `--issue` (required): GitHub issue number (just the number)
- `--summary` (required): one-line PR summary
- `--linkage` (optional, default: `Fixes`): `Fixes|Closes|Resolves|Ref`
- `--title` (optional): PR title (default: most recent commit subject)
- `--notes` (optional): additional notes
- `--docs-only` (optional): applies docs-only testing exception
- `--dry-run` (optional): print generated PR without executing

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

```
MqRestException (sealed, RuntimeException)
├── MqRestTransportException   (network/connection)
├── MqRestResponseException    (malformed JSON)
├── MqRestAuthException        (auth failures)
├── MqRestCommandException     (MQSC errors)
└── MqRestTimeoutException     (polling timeout)

MappingException (separate, data-transformation errors)
```

### Package Layout

```
.admin          — MqRestSession (Builder pattern), MqRestTransport, HttpClientTransport, TransportResponse
.admin.auth     — Credentials (sealed), BasicAuth, LtpaAuth, CertificateAuth
.admin.exception — Sealed exception hierarchy
.admin.mapping  — AttributeMapper, MappingData, MappingIssue, MappingDirection, MappingOverrideMode
.admin.sync     — SyncConfig, SyncResult, SyncOperation
.admin.ensure   — EnsureResult, EnsureAction
```

## Documentation and Standards

- `AGENTS.md` — Generic AI agent instructions with include directives
- `docs/repository-standards.md` — Project-specific standards (branching, co-authors, naming)
- `docs/standards-and-conventions.md` — Canonical standards reference
