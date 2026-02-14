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

Track technical decisions for the Java port of pymqrest. All library code is
implemented; remaining items are tooling and infrastructure.

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

- **Formatter**: Spotless + google-java-format (decided 2026-02-12, see
  `docs/plans/2026-02-12-tier2-decisions.md`).
- **Style checker**: Checkstyle with `google_checks.xml` (decided 2026-02-12).
- **Bug analysis**: SpotBugs at max effort / low threshold (decided 2026-02-12).
- **Code smell detection**: PMD with default ruleset (decided 2026-02-12).
- **Null-safety strategy**: JSpecify 1.0.0 adopted for `@Nullable` annotations,
  enforced by NullAway 0.13.1 at ERROR severity via Error Prone 2.47.0 (JDK
  21+ only, decided 2026-02-14).

## Testing

- **Test framework**: JUnit 5 (Jupiter) (decided 2026-02-12, see
  `docs/plans/2026-02-12-tier2-decisions.md`).
- **Mocking library**: Mockito 5 with `mockito-junit-jupiter` (decided
  2026-02-12).
- **Coverage tool and threshold**: JaCoCo, 100% line and branch coverage
  (decided 2026-02-12).
- **Unit test convention**: `*Test.java` via `maven-surefire-plugin` (decided
  2026-02-12).
- **Integration test convention**: `*IT.java` via `maven-failsafe-plugin`
  (decided 2026-02-12).
- **Integration test strategy**: TBD (same local MQ container as pymqrest).

## Architecture

- **HTTP client library**: `java.net.http.HttpClient` (JDK built-in) (decided
  2026-02-12, see `docs/plans/2026-02-12-tier3-decisions.md`).
- **JSON library**: Gson (`com.google.code.gson:gson`) (decided 2026-02-12, see
  `docs/plans/2026-02-12-tier3-decisions.md`).
- **API surface style**: Method-per-command mirroring pymqrest, with
  `Map<String, Object>` for request/response attributes (decided 2026-02-12, see
  `docs/plans/2026-02-12-tier3-decisions.md`).
- **Attribute mapping approach**: Direct port of pymqrest's 3-layer mapping
  pipeline (decided 2026-02-12, see
  `docs/plans/2026-02-12-tier3-decisions.md`).
- **Exception hierarchy**: Unchecked (`RuntimeException`), sealed hierarchy
  mirroring pymqrest's structure (decided 2026-02-12, see
  `docs/plans/2026-02-12-tier3-decisions.md`).

## CI and publishing

- **CI platform**: GitHub Actions (consistent with pymqrest).
- **CI workflows**: 6-job pipeline (docs-only, standards-compliance,
  dependency-audit, release-gates, test-and-validate, integration-tests) with
  Java 17/21/25 matrix (decided 2026-02-13).
- **Dependency audit**: `actions/dependency-review-action@v4` (GitHub-native,
  zero-config) over OWASP Dependency-Check Maven Plugin (decided 2026-02-13).
- **Publishing target**: Maven Central (implicit from groupId decision).
- **Publishing mechanism**: Central Portal API (OSSRH shut down June 2025).

## Validation scripts

- **Local validation command**: `./mvnw verify` (decided 2026-02-12, see
  `docs/plans/2026-02-12-tier2-decisions.md`).
- **Docs-only validation**: TBD (markdownlint is already available).
- **Git hooks**: `pre-commit` (branching-model-aware branch protection) and
  `commit-msg` (Conventional Commits + co-author validation with whitespace
  normalization). Adopted canonical versions from standards-and-conventions
  (decided 2026-02-13, updated 2026-02-14 via PR #31).
