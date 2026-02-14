# Developer Setup

## Table of Contents

- [Prerequisites](#prerequisites)
- [Building](#building)
- [Validation pipeline](#validation-pipeline)
- [Testing](#testing)
- [Git hooks](#git-hooks)
- [Documentation](#documentation)


## Prerequisites

- **Java 17+**: Install via [SDKMAN](https://sdkman.io/) or `brew install openjdk@17`
- **Maven**: Provided by the Maven Wrapper (`./mvnw`), no separate install needed
- **Python 3.12+**: Required for MkDocs documentation builds and mapping doc generation

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
