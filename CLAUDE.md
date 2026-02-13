# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Documentation Strategy

This repository uses two complementary approaches for AI agent guidance:

- **AGENTS.md**: Generic AI agent instructions using include directives to force documentation indexing. Contains canonical standards references, shared skills loading, and user override support.
- **CLAUDE.md** (this file): Claude Code-specific guidance with prescriptive commands, architecture details, and development workflows optimized for `/init`.

### Integration Approach

**For Claude Code** (`/init` command):
1. Read CLAUDE.md (this file) first for optimized quick-start guidance
2. Process include directives to load repository standards
3. Reference AGENTS.md for shared skills and canonical standards location
4. Apply layered standards: canonical → project-specific → user overrides

**For other AI agents** (Codex, generic LLMs):
1. Read AGENTS.md first as the primary entry point
2. Process include directives to load all referenced documentation
3. Resolve canonical standards repo path (local or GitHub)
4. Load shared skills from standards repo
5. Apply user overrides from `~/AGENTS.md` if present

**Key differences**:
- **CLAUDE.md**: Prescriptive, command-focused, optimized for `/init`
- **AGENTS.md**: Declarative, include-directive-driven, forces full documentation indexing

Both files share the same underlying standards via include directives, ensuring consistency across all AI agents working in this repository.

### Best Practices for Dual-File Approach

**What goes in AGENTS.md**:
- Include directives for documentation indexing
- Canonical standards repository references
- Shared skills loading instructions
- User override mechanisms
- Minimal, declarative content

**What goes in CLAUDE.md**:
- Claude Code-specific quick-start commands
- Detailed architecture and design patterns
- Implementation notes and common workflows
- Integration guidance between the two files
- More verbose, prescriptive content

**What goes in neither (use includes instead)**:
- Repository standards (keep in `docs/repository-standards.md`)
- Canonical standards (reference external repo)
- Project-specific conventions (keep in referenced docs)

**Maintenance strategy**:
- Update standards in source files, not in AGENTS.md or CLAUDE.md
- Use include directives to pull in shared content
- Keep AGENTS.md minimal and CLAUDE.md focused on Claude Code workflows
- Test both entry points when updating documentation structure

<!-- include: docs/standards-and-conventions.md -->
<!-- include: docs/repository-standards.md -->

## Project Overview

This is a Java port of `pymqrest`, providing a Java wrapper for the IBM MQ administrative REST API. The project will provide a Java mapping layer for MQ REST API attribute translations and command metadata.

**Project name**: mq-rest-admin

**Status**: Pre-alpha (initial setup)

**Build coordinates**: `io.github.wphillipmoore:mq-rest-admin:0.1.0-SNAPSHOT`

**Java package**: `io.github.wphillipmoore.mq.rest.admin`

**Canonical Standards**: This repository follows standards at https://github.com/wphillipmoore/standards-and-conventions (local path: `../standards-and-conventions` if available)

## Development Commands

### Environment Setup

- **Java**: 17+ (install via `brew install openjdk@17` or SDKMAN)
- **Maven**: Provided by Maven Wrapper (`./mvnw`), no separate install needed

### Build

```bash
./mvnw compile          # Compile sources
./mvnw clean            # Remove target/
./mvnw clean compile    # Clean rebuild
./mvnw package          # Compile and package JAR
```

### Validation

```bash
./mvnw verify              # Run entire validation pipeline (single command)
```

The `verify` lifecycle runs in order: formatting check (Spotless) → style check
(Checkstyle) → compile → unit tests (Surefire) → integration tests (Failsafe) →
coverage enforcement (JaCoCo 100% line + branch) → bug analysis (SpotBugs) →
code smell detection (PMD).

Individual tools can also be run standalone:

```bash
./mvnw spotless:check      # Check formatting
./mvnw spotless:apply      # Auto-format code
./mvnw checkstyle:check    # Check style rules
./mvnw spotbugs:check      # Check for bug patterns
./mvnw pmd:check           # Check for code smells
```

### Testing

```bash
./mvnw test                # Unit tests only (*Test.java)
./mvnw verify              # Unit + integration tests (*IT.java)
```

- **Framework**: JUnit 5 (Jupiter)
- **Mocking**: Mockito 5 with `@ExtendWith(MockitoExtension.class)`
- **Assertions**: AssertJ (`assertThat(x).isEqualTo(y)`)
- **Coverage**: JaCoCo — 100% line and branch coverage enforced at BUNDLE level

### Linting and Formatting

- **Formatter**: Spotless + google-java-format (2-space indent, opinionated)
- **Style**: Checkstyle with `google_checks.xml` (bundled)
- **Bug analysis**: SpotBugs (`effort=Max`, `threshold=Low`)
- **Code smells**: PMD (default ruleset)

Run `./mvnw spotless:apply` to auto-format before committing.

## Architecture

Direct port of `pymqrest`'s architecture, adapted to Java idioms.

### Technology Stack

- **HTTP client**: `java.net.http.HttpClient` (JDK built-in, zero runtime dependencies)
- **JSON library**: Gson (`com.google.code.gson:gson`, single JAR, zero transitive deps)
- **Runtime dependencies**: 1 (Gson ~280KB)

### API Surface

- **Style**: Method-per-command mirroring pymqrest (`displayQueue()`, `defineQlocal()`, etc.)
- **Parameters/results**: `Map<String, Object>` for MQ attributes (dynamic attribute sets, permissive mode)
- **Fixed-schema types**: Java records for `TransportResponse`, `SyncConfig`, `SyncResult`, `EnsureResult`, `MappingIssue`, credential types
- **Credentials**: Sealed interface (`Credentials permits BasicAuth, LtpaAuth, CertificateAuth`)
- **Session**: Single `MqRestSession` class (no mixin decomposition)

### Transport Layer

- `MqRestTransport` interface (mirrors pymqrest's Protocol) — enables Mockito-based testing
- `HttpClientTransport` implementation behind that interface
- Supports TLS/mTLS via `SSLContext`, timeouts via `Duration`, custom headers

### Attribute Mapping

- Direct port of pymqrest's 3-layer pipeline: key map, value map, key-value map
- Two directions: request and response
- Strict/permissive modes with `MappingIssue` tracking
- Mapping data stored as JSON resource file in `src/main/resources/` (loaded by Gson)
- Override mechanism with merge/replace modes

### Exception Hierarchy

Unchecked (`RuntimeException`), sealed:

```
MqRestException (sealed, extends RuntimeException)
├── MqRestTransportException   (network/connection failures)
├── MqRestResponseException    (malformed JSON, unexpected structure)
├── MqRestAuthException        (authentication/authorization failures)
├── MqRestCommandException     (MQSC command returned error codes)
└── MqRestTimeoutException     (polling timeout exceeded)

MappingException (separate hierarchy, data-transformation errors)
```

### Package Structure

```
io.github.wphillipmoore.mq.rest.admin
    MqRestSession, MqRestTransport, HttpClientTransport, TransportResponse
io.github.wphillipmoore.mq.rest.admin.auth
    Credentials, BasicAuth, LtpaAuth, CertificateAuth
io.github.wphillipmoore.mq.rest.admin.exception
    MqRestException, MqRest{Transport,Response,Auth,Command,Timeout}Exception
io.github.wphillipmoore.mq.rest.admin.mapping
    AttributeMapper, MappingData, MappingIssue, MappingException, MappingOverrideMode
io.github.wphillipmoore.mq.rest.admin.sync
    SyncConfig, SyncResult, SyncOperation
io.github.wphillipmoore.mq.rest.admin.ensure
    EnsureResult, EnsureAction
```

## Repository Standards Quick Reference

The include directives at the top of this file load the full repository standards. Key highlights for quick reference:

**Pre-flight Checklist**:
- Check current branch: `git status -sb`
- If on `develop`, create `feature/*` branch or get explicit approval
- Enable git hooks: `git config core.hooksPath scripts/git-hooks`

See `docs/repository-standards.md` for complete details.

## Documentation Indexing Strategy

This repository uses `<!-- include: path/to/file.md -->` directives to force documentation indexing. When you encounter these directives:

1. **Read the referenced files** to understand the full context
2. **Apply layered standards** in order:
   - Canonical standards (from `standards-and-conventions` repo)
   - Project-specific standards (`docs/repository-standards.md`)
   - User overrides (`~/AGENTS.md` if present)
3. **Load shared skills** from `<standards-repo-path>/skills/**/SKILL.md`

The include directives appear in:
- `AGENTS.md` - Includes repository standards and conventions
- `CLAUDE.md` - Includes same standards for Claude Code
- `docs/standards-and-conventions.md` - Includes canonical standards reference

This approach ensures all AI agents (Codex, Claude, etc.) have access to the same foundational documentation.

## Documentation Structure

- `README.md` - Project overview and quick start
- `AGENTS.md` - Generic AI agent instructions with include directives
- `CLAUDE.md` - This file, Claude Code-specific guidance
- `docs/repository-standards.md` - Project-specific standards (included from AGENTS.md)
- `docs/standards-and-conventions.md` - Canonical standards reference (includes external repo)

## Key References

**Canonical Standards**: https://github.com/wphillipmoore/standards-and-conventions
- Local path (preferred): `../standards-and-conventions`
- Load all skills from: `<standards-repo-path>/skills/**/SKILL.md`

**Reference implementation**: `../pymqrest` (Python version)

**External Documentation**:
- IBM MQ 9.4 administrative REST API
- MQSC command reference
- PCF command formats

**User Overrides**: `~/AGENTS.md` (optional, applied if present and readable)
