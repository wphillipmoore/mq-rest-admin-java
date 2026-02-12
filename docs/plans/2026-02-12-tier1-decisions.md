# Tier 1 decisions: groupId, Java version, build tool

## Table of Contents

- [Results](#results)
- [Reasoning](#reasoning)
- [Options not chosen](#options-not-chosen)
- [Dependencies and external constraints](#dependencies-and-external-constraints)
- [References](#references)

## Results

### Decisions made

- **Maven groupId**: `io.github.wphillipmoore`
- **Full Java package**: `io.github.wphillipmoore.mq.rest.admin`
- **Minimum Java version**: 17
- **CI test matrix**: 17, 21, and 25
- **Build tool**: Maven (with Maven Wrapper)

### Implicitly converged decisions

- **Publishing target**: Maven Central via the Central Portal (implicit --
  Maven Central is the standard for open-source Java libraries, and the groupId
  decision assumed Central Portal verification).
- **Publishing mechanism**: Central Portal API (implicit -- OSSRH was shut down
  June 2025; the Central Portal is the only remaining path).
- **Java distribution for CI**: Temurin (implicit -- Eclipse Temurin is the
  standard open-source OpenJDK distribution used by `actions/setup-java`).

### Action items

- Register the `io.github.wphillipmoore` namespace on the Central Portal
  (auto-verified via GitHub login).
- Generate the Maven project skeleton with `pom.xml` and Maven Wrapper.
- Update `CLAUDE.md` with concrete development commands once the project
  skeleton exists.

## Reasoning

### Maven groupId

#### Key constraints

- Maven Central coordinates are effectively permanent. Published artifacts
  cannot be deleted or renamed.
- The MavenGate attack (January 2024) demonstrated that expired domains can be
  re-registered to hijack groupIds. Sonatype responded by disabling accounts
  tied to expired domains, but the risk remains for domain-based namespaces.
- The `io.github.*` namespace is tied to a GitHub account, which does not expire
  and has no renewal cost.

#### Evidence cited

- Well-respected Java libraries use `io.github.*` groupIds:
  - Resilience4j (`io.github.resilience4j`) -- standard fault-tolerance library
  - ClassGraph (`io.github.classgraph`) -- Duke's Choice Award winner
  - OpenFeign (`io.github.openfeign`) -- Netflix-origin HTTP client
  - WebDriverManager (`io.github.bonigarcia`) -- standard Selenium tooling
- Auto-verification via GitHub login to the Central Portal eliminates manual
  steps. No DNS TXT records, no support tickets, no waiting.

#### Tradeoffs

- `io.github.wphillipmoore` is verbose in import statements (26 characters
  before the artifact package). This is a cosmetic concern -- IDEs handle
  imports automatically.
- The groupId is tied to a personal GitHub username. If the project grows to
  warrant its own organization, a new groupId can be published alongside the
  original (Maven supports relocation POMs).

### Minimum Java version

#### Key constraints

- Java 17 is the most-used version in production (~35%, New Relic 2024; 34%,
  Azul 2025).
- Combined with Java 21+ users, targeting Java 17 covers >60% of production
  deployments.
- Java 8 and 11 are in extended/legacy support only with declining adoption.

#### Evidence cited

- Major libraries converged on Java 17 as the baseline in 2025:
  - Jackson 3.0 (October 2025): Java 17
  - JUnit 6 (October 2025): Java 17
  - Spring Framework 6 and 7: Java 17
  - Spring Boot 3 and 4: Java 17
- Java 17 language features critical for library API design:
  - **Records**: immutable data carriers for DTOs and API return types
  - **Sealed classes**: controlled type hierarchies for result/error types
  - **Pattern matching for `instanceof`**: cleaner internal implementation
  - **Text blocks**: multi-line string literals
  - **Switch expressions**: concise control flow

#### Tradeoffs

- Excludes the ~23% of production on Java 8 and ~30% on Java 11. These
  populations are declining and the language features gained are
  transformative for API design, not luxuries.
- Java 21 was considered but its production share is still catching up.
  Libraries targeting 17 can still be used from Java 21 virtual threads.
  Raising the minimum to 21 can be revisited when 21's share exceeds 40-50%
  (estimated mid-2027).

### Build tool

#### Key constraints

- The developer is new to Java. Learning curve is a primary concern.
- This is a library, not an application. Build speed advantages of Gradle
  (incremental compilation, daemon, build cache) are irrelevant for a small
  library.
- OSSRH was shut down June 2025. The new Central Portal has first-party Maven
  support; Gradle requires third-party publishing plugins.

#### Evidence cited

- Adoption: Maven ~60%, Gradle ~35% (JetBrains 2024). Maven dominates for
  libraries; Gradle dominates for Android.
- Libraries using Maven: Jackson, Gson, Guava, Apache HttpClient (4 of 7
  surveyed). Libraries using Gradle: OkHttp, JUnit 5, Spring Framework (3 of
  7).
- Maven's `pom.xml` is a single declarative file, conceptually similar to
  Python's `pyproject.toml`. Gradle's `build.gradle.kts` is a Kotlin program
  with a steeper learning curve.
- The Central Portal's compatibility layer was designed for Maven's deployment
  protocol. Gradle publishing requires additional third-party plugin research.

#### Tradeoffs

- Maven's XML is more verbose than Gradle's Kotlin DSL. For a small library
  this is ~30 lines of additional boilerplate, not a meaningful burden.
- Maven lacks native dependency lock files. For a library (whose consumers
  resolve their own dependency tree), this is acceptable. Pinning exact
  dependency versions in `pom.xml` provides practical reproducibility.
- Gradle's incremental builds and build cache are foregone. These matter for
  large multi-module projects, not a small library that compiles in seconds.

## Options not chosen

### groupId: personal domain

- **Description**: Use a personal domain (e.g., `dev.pmoore`) for a shorter,
  more "branded" groupId.
- **Reason not chosen**: Creates an ongoing domain renewal obligation. The
  MavenGate attack demonstrated supply-chain risks from expired domains.
  Sonatype disables accounts tied to expired domains, potentially locking out
  the legitimate publisher. The `io.github.*` namespace has no such risk.
- **Status**: Rejected.

### groupId: GitHub organization (`io.github.mq-rest-admin`)

- **Description**: Create a GitHub organization for the project and use its
  namespace.
- **Reason not chosen**: Requires manual verification via Central Support
  (slower). Hyphens in the org name create a groupId/package mismatch
  (`io.github.mq-rest-admin` is a valid groupId but not a valid Java package).
  Adds organizational overhead for a solo project.
- **Status**: Rejected.

### Java version: 21

- **Description**: Target Java 21 as the minimum to gain virtual threads,
  record patterns, and pattern matching for switch.
- **Reason not chosen**: Production share is still catching up to Java 17.
  Would exclude the ~35% of production on Java 17. Libraries targeting 17 can
  still be used from virtual threads. Virtual-thread-specific APIs can be
  offered in a future optional module if needed.
- **Status**: Deferred. Revisit when Java 21 production share exceeds 40-50%.
- **Revisit trigger**: Mid-2027 or when ecosystem baseline shifts.

### Java version: 11

- **Description**: Target Java 11 for maximum backward compatibility.
- **Reason not chosen**: Gains almost nothing over Java 8 in language features
  (the major features landed in 12-17). Would sacrifice records, sealed
  classes, text blocks, and pattern matching. No major library released in 2025
  chose Java 11 as its baseline (Mockito 5 is the lone exception).
- **Status**: Rejected.

### Build tool: Gradle

- **Description**: Use Gradle with Kotlin DSL for more concise build files and
  advanced dependency management.
- **Reason not chosen**: Steeper learning curve for a Java newcomer. Gradle's
  strengths (incremental builds, build cache, daemon) are irrelevant for a
  small library. Maven Central publishing requires third-party Gradle plugins
  since OSSRH shutdown. The majority of Java libraries use Maven.
- **Status**: Rejected.

## Dependencies and external constraints

- Maven Central namespace verification requires a GitHub login to the Central
  Portal at [central.sonatype.com](https://central.sonatype.com).
- Java 17 extended support (Oracle) runs until September 2029. Temurin support
  runs until October 2027.
- Maven Wrapper (`mvnw`) should be checked into source control so contributors
  do not need Maven pre-installed.

## References

- `docs/plans/2026-02-12-project-naming.md` -- project naming decision
- `docs/plans/open-decisions.md` -- remaining open decisions
- `docs/research/mq-java-ecosystem.md` -- ecosystem survey
- `docs/research/admin-rest-api-gap-analysis.md` -- REST API gap analysis
- `../standards-and-conventions/docs/foundation/summarize-decisions-protocol.md`
  -- protocol followed for this document
