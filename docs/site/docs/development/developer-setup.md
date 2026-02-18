# Developer Setup

This guide covers everything needed to develop and test mq-rest-admin
locally.

## Prerequisites

| Tool | Version | Purpose |
| --- | --- | --- |
| Java | 17+ | Runtime (install via SDKMAN or `brew install openjdk@17`) |
| Maven | 3.9+ | Provided by Maven Wrapper (`./mvnw`) |
| Python | 3.12+ | MkDocs documentation builds and mapping doc generation |
| Docker | Latest | Local MQ containers (integration tests) |
| `markdownlint` | Latest | Docs validation |

## Required repositories

mq-rest-admin depends on two sibling repositories:

| Repository | Purpose |
| --- | --- |
| [mq-rest-admin-java](https://github.com/wphillipmoore/mq-rest-admin-java) | This project |
| [standards-and-conventions](https://github.com/wphillipmoore/standards-and-conventions) | Canonical project standards (referenced by `AGENTS.md` and git hooks) |
| [mq-rest-admin-dev-environment](https://github.com/wphillipmoore/mq-rest-admin-dev-environment) | Dockerized MQ test infrastructure (local and CI) |
| [mq-rest-admin-common](https://github.com/wphillipmoore/mq-rest-admin-common) | Shared documentation fragments |

## Recommended directory layout

Clone all repositories as siblings:

```text
~/dev/github/
├── mq-rest-admin-java/
├── mq-rest-admin-common/
├── standards-and-conventions/
└── mq-rest-admin-dev-environment/
```

```bash
cd ~/dev/github
git clone https://github.com/wphillipmoore/mq-rest-admin-java.git
git clone https://github.com/wphillipmoore/mq-rest-admin-common.git
git clone https://github.com/wphillipmoore/standards-and-conventions.git
git clone https://github.com/wphillipmoore/mq-rest-admin-dev-environment.git
```

## Initial setup

```bash
cd mq-rest-admin-java

# Compile and run all quality checks
./mvnw verify

# Enable repository git hooks
git config core.hooksPath scripts/git-hooks
```

## Building

```bash
./mvnw compile          # Compile sources
./mvnw clean            # Remove target/
./mvnw clean compile    # Clean rebuild
./mvnw package          # Compile and package JAR
```

## Validation pipeline

The full validation pipeline runs all quality checks in sequence:

```bash
./mvnw verify
```

This runs: formatting check (Spotless) → style check (Checkstyle) → compile →
unit tests (Surefire) → integration tests (Failsafe) → coverage enforcement
(JaCoCo 100% line + branch) → bug analysis (SpotBugs) → code smell detection
(PMD).

Individual tools can be run standalone:

```bash
./mvnw spotless:check      # Check formatting
./mvnw spotless:apply      # Auto-format code
./mvnw checkstyle:check    # Check style rules
./mvnw spotbugs:check      # Check for bug patterns
./mvnw pmd:check           # Check for code smells
```

## Testing

```bash
./mvnw test                # Unit tests only (*Test.java)
./mvnw verify              # Unit + integration tests (*IT.java)
```

- **Framework**: JUnit 5 (Jupiter)
- **Mocking**: Mockito 5 with `@ExtendWith(MockitoExtension.class)`
- **Assertions**: AssertJ (`assertThat(x).isEqualTo(y)`)
- **Coverage**: JaCoCo — 100% line and branch coverage enforced

## Running integration tests

Integration tests require running MQ containers. Start the containers,
seed test objects, then run the tests:

```bash
# Start both queue managers
scripts/dev/mq_start.sh

# Seed deterministic test objects
scripts/dev/mq_seed.sh

# Run integration tests
MQ_REST_ADMIN_RUN_INTEGRATION=1 ./mvnw verify

# Stop MQ when done
scripts/dev/mq_stop.sh
```

See [local MQ container](local-mq-container.md) for full container configuration,
credentials, gateway routing, and troubleshooting.

## Git hooks

Enable repository git hooks before committing:

```bash
git config core.hooksPath scripts/git-hooks
```

The hooks enforce:

- **pre-commit**: Branch naming conventions and protected branch rules
- **commit-msg**: Conventional Commits format and co-author trailer validation

## Documentation

### Local setup

```bash
# Set up shared fragments symlink
scripts/dev/docs-setup.sh

# Install MkDocs
pip install mkdocs-material

# Generate mapping docs
python scripts/dev/generate_mapping_docs.py

# Build the documentation site
mkdocs build -f docs/site/mkdocs.yml

# Serve locally with live reload
mkdocs serve -f docs/site/mkdocs.yml
```

### Javadoc

```bash
./mvnw javadoc:javadoc
```

Output is generated to `docs/site/site/javadoc/`.

## CI pipeline overview

CI runs on every pull request and enforces the same gates as local
validation. The pipeline includes:

- **Unit tests** on Java 17, 21, and 25-ea
- **Integration tests** against real MQ queue managers via the shared
  `wphillipmoore/mq-rest-admin-dev-environment/.github/actions/setup-mq` action
- **Standards compliance** (Spotless, Checkstyle, SpotBugs, PMD, markdown
  lint, commit messages, repository profile)
- **Dependency audit** (`dependency-check`)
- **Release gates** (version checks, changelog validation) for PRs
  targeting `main`
