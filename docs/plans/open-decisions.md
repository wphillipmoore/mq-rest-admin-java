# Open decisions

## Table of Contents

- [Purpose](#purpose)
- [Project identity](#project-identity)
- [Java platform](#java-platform)
- [Build tooling](#build-tooling)
- [Code quality and static analysis](#code-quality-and-static-analysis)
- [Testing](#testing)
- [Architecture](#architecture)
- [CI and publishing](#ci-and-publishing)
- [Validation scripts](#validation-scripts)

## Purpose

Track decisions that must be made before substantive development begins on the
Java port of pymqrest.

## Project identity

- **Project name**: `mq-rest-admin` (decided 2026-02-12, see
  `docs/plans/2026-02-12-project-naming.md`).
- **GitHub repository name**: `mq-rest-admin`.
- **Maven artifactId**: `mq-rest-admin`.
- **Maven groupId**: `io.github.wphillipmoore` (decided 2026-02-12, see
  `docs/plans/2026-02-12-tier1-decisions.md`).
- **Java package**: `io.github.wphillipmoore.mq.rest.admin` (decided
  2026-02-12).

## Java platform

- **Minimum Java version**: 17 (decided 2026-02-12, see
  `docs/plans/2026-02-12-tier1-decisions.md`).
- **CI test matrix**: 17, 21, and 25.
- **Distribution**: Temurin (Eclipse Adoptium).

## Build tooling

- **Build tool**: Maven (decided 2026-02-12, see
  `docs/plans/2026-02-12-tier1-decisions.md`).
- **Dependency lock file strategy**: Pin exact versions in `pom.xml` (no native
  lock file in Maven; acceptable for a library).
- **Wrapper inclusion**: Maven Wrapper (`mvnw`) checked into source control.

## Code quality and static analysis

- **Linter / static analysis**: TBD (Checkstyle, SpotBugs, Error Prone, PMD,
  etc.).
- **Formatter**: TBD (google-java-format, Spotless, etc.).
- **Null-safety strategy**: TBD (annotations, Optional, etc.).

## Testing

- **Test framework**: TBD (JUnit 5, TestNG, etc.).
- **Mocking library**: TBD (Mockito, etc.).
- **Coverage tool and threshold**: TBD (JaCoCo target percentage).
- **Integration test strategy**: TBD (same local MQ container as pymqrest).

## Architecture

- **HTTP client library**: TBD (java.net.http, OkHttp, Apache HttpClient, etc.).
- **JSON library**: TBD (Jackson, Gson, etc.).
- **API surface style**: TBD (fluent builder, method-per-command mirroring
  pymqrest, etc.).
- **Attribute mapping approach**: TBD (port pymqrest mapping pipeline, or
  redesign for Java idioms).
- **Exception hierarchy**: TBD (mirror pymqrest or adapt to Java conventions).

## CI and publishing

- **CI platform**: GitHub Actions (consistent with pymqrest).
- **Publishing target**: Maven Central (implicit from groupId decision).
- **Publishing mechanism**: Central Portal API (OSSRH shut down June 2025).

## Validation scripts

- **Local validation command**: TBD (equivalent of pymqrest's
  `validate_local.py`).
- **Docs-only validation**: TBD (markdownlint is already available).
- **Git hooks**: TBD (scripts/git-hooks directory created, hooks not yet
  written).
