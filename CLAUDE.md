# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- include: docs/standards-and-conventions.md -->
<!-- include: docs/repository-standards.md -->

## Auto-memory policy

**Do NOT use MEMORY.md.** Never write to MEMORY.md or any file under the
memory directory. All behavioral rules, conventions, and workflow instructions
belong in managed, version-controlled documentation (CLAUDE.md, AGENTS.md,
skills, or docs/). If you want to persist something, tell the human what you
would save and let them decide where it belongs.

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
- **Standard tooling**: CLI tools (`st-commit`, `st-validate-local`, etc.) are pre-installed in the dev container images

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
