# Tier 2 decisions: testing framework and code quality tooling

## Table of Contents

- [Results](#results)
- [Reasoning](#reasoning)
- [Options not chosen](#options-not-chosen)
- [Dependencies and external constraints](#dependencies-and-external-constraints)
- [References](#references)

## Results

### Decisions made

- **Test framework**: JUnit 5 (Jupiter) -- industry standard for Java libraries.
- **Mocking library**: Mockito 5 with `mockito-junit-jupiter` extension.
- **Assertion library**: AssertJ -- fluent assertion API.
- **Coverage tool**: JaCoCo -- 100% line and branch coverage at the BUNDLE level
  (matches pymqrest and canonical standards).
- **Unit test convention**: `*Test.java` via `maven-surefire-plugin`.
- **Integration test convention**: `*IT.java` via `maven-failsafe-plugin`.
- **Formatter**: Spotless with `google-java-format` (bound to `validate` phase).
- **Style checker**: Checkstyle with `google_checks.xml` (bound to `validate`
  phase).
- **Bug analysis**: SpotBugs at max effort / low threshold (bound to `verify`
  phase).
- **Code smell detection**: PMD with default ruleset (bound to `verify` phase).

### Deferred decisions

- **Null-safety (JSpecify)**: Deferred until the API surface takes shape. Adding
  nullability annotations to an unstable API creates churn.
- **JUnit 6 upgrade**: Deferred until Mockito ships JUnit 6 support. JUnit 6
  exists (6.0.x) but Mockito's `mockito-junit-jupiter` extension still targets
  JUnit 5. Upgrading prematurely would require a custom integration or losing
  Mockito support.

### Action items

- Update `pom.xml` with test dependencies and all plugin configurations.
- Create `config/checkstyle/suppressions.xml` for `package-info` and
  `module-info`.
- Create placeholder test to verify the toolchain works.
- Update `CLAUDE.md`, `docs/repository-standards.md`, and
  `docs/plans/open-decisions.md`.

## Reasoning

### Testing stack

#### Test framework: JUnit 5

- JUnit 5 is the de facto standard for Java library testing. Every major Java
  library uses it. TestNG still exists but has negligible mindshare outside
  legacy codebases.
- JUnit 5's extension model (`@ExtendWith`) integrates cleanly with Mockito and
  JaCoCo.
- Rich parameterized test support (`@ParameterizedTest`, `@CsvSource`,
  `@MethodSource`) will be valuable for attribute mapping tests.

#### Mocking: Mockito 5

- Mockito is the overwhelming default for Java mocking. PowerMock is defunct.
  EasyMock has negligible adoption.
- `mockito-junit-jupiter` provides `@ExtendWith(MockitoExtension.class)` for
  automatic mock initialization.
- Mockito 5 requires Java 11+ and uses `byte-buddy` for dynamic proxies, which
  works cleanly on Java 17+.

#### Assertions: AssertJ

- AssertJ provides a fluent assertion API that is more readable and discoverable
  than JUnit's built-in assertions: `assertThat(result).isNotNull().hasSize(3)`.
- Type-safe assertions reduce the risk of accidental argument swaps (a common
  issue with `assertEquals(expected, actual)`).
- AssertJ is used by Spring, Hibernate, and most modern Java libraries.

#### Coverage: JaCoCo at 100%

- 100% line and branch coverage matches the pymqrest standard and the canonical
  standards convention.
- For a library this small (mapping layer + thin HTTP client), 100% is achievable
  and appropriate.
- JaCoCo is the only actively maintained free coverage tool for Java. Clover was
  open-sourced and abandoned. Cobertura is unmaintained.
- `package-info.class` is excluded from coverage since it contains no executable
  code.

### Code quality stack

#### Formatter: Spotless + google-java-format

- `google-java-format` is opinionated and non-configurable, eliminating style
  debates. This matches the project's philosophy of following community
  standards.
- Spotless wraps `google-java-format` as a Maven plugin with lifecycle binding,
  providing `spotless:check` (CI) and `spotless:apply` (developer) goals.
- Bound to `validate` phase so formatting violations are caught before
  compilation.

#### Style: Checkstyle + google_checks.xml

- Checkstyle enforces style rules that a formatter cannot (naming conventions,
  Javadoc requirements, import ordering beyond what the formatter handles).
- `google_checks.xml` is bundled with the plugin and aligns with
  `google-java-format`.
- Bound to `validate` phase alongside Spotless for fast feedback.

#### Bug analysis: SpotBugs

- SpotBugs (successor to FindBugs) performs bytecode analysis to detect common
  bug patterns: null pointer dereferences, resource leaks, synchronization
  issues, etc.
- `effort=Max` and `threshold=Low` enables all detectors at maximum sensitivity.
  For a new project with no legacy code, this is appropriate.
- Bound to `verify` phase (requires compiled bytecode).

#### Code smell detection: PMD

- PMD performs source-level analysis complementary to SpotBugs: unused variables,
  empty catch blocks, unnecessary object creation, etc.
- Default ruleset provides a reasonable baseline without overwhelming false
  positives.
- Bound to `verify` phase.

### Lifecycle binding rationale

- **validate** (Spotless, Checkstyle): Fast checks that don't require
  compilation. Fail early on formatting and style issues.
- **test** (Surefire, JaCoCo report): Unit tests and coverage report generation.
- **verify** (Failsafe, JaCoCo check, SpotBugs, PMD): Integration tests,
  coverage enforcement, and bytecode/source analysis. These require compilation
  and are slower.

Single command `./mvnw verify` runs the entire pipeline.

## Options not chosen

### Test framework: TestNG

- **Description**: Alternative test framework with data-driven testing features.
- **Reason not chosen**: Negligible adoption in new Java projects. JUnit 5's
  parameterized tests cover the same use cases. Mockito and AssertJ have
  first-class JUnit 5 integration.
- **Status**: Rejected.

### Test framework: JUnit 6

- **Description**: Latest JUnit generation (6.0.x), requires Java 17+.
- **Reason not chosen**: Mockito's `mockito-junit-jupiter` extension does not yet
  support JUnit 6. Upgrading would require either dropping Mockito integration
  or writing a custom extension.
- **Status**: Deferred until Mockito ships JUnit 6 support.

### Assertion library: Hamcrest

- **Description**: Matcher-based assertion library (`assertThat(x, is(3))`).
- **Reason not chosen**: Less readable than AssertJ's fluent API. AssertJ
  provides better IDE auto-completion and error messages. Hamcrest is effectively
  in maintenance mode.
- **Status**: Rejected.

### Formatter: Palantir Java Format

- **Description**: Alternative opinionated formatter (fork of google-java-format
  with 4-space indentation).
- **Reason not chosen**: Google Java Format is the community standard. Using the
  same style as Google, Spring, and most open-source Java libraries reduces
  cognitive overhead for contributors.
- **Status**: Rejected.

### Static analysis: Error Prone

- **Description**: Compile-time bug detection via javac plugin.
- **Reason not chosen**: Overlaps significantly with SpotBugs and PMD. Error
  Prone requires compiler plugin configuration that can be brittle across Java
  versions. SpotBugs + PMD provide equivalent detection with simpler setup.
- **Status**: Rejected. Can be revisited if SpotBugs + PMD prove insufficient.

### Null-safety: JSpecify

- **Description**: Standard nullability annotations (`@Nullable`,
  `@NonNull`).
- **Reason not chosen**: Adding nullability annotations to an API that hasn't
  been designed yet creates churn. Deferred until the API surface stabilizes.
- **Status**: Deferred.
- **Revisit trigger**: After the core API surface (session, commands, mapping)
  is designed.

## Dependencies and external constraints

- JUnit 5, Mockito, and AssertJ are all test-scoped dependencies with no
  transitive impact on library consumers.
- `google-java-format` requires Java 17+ at build time (matches the project's
  minimum Java version).
- Spotless 3.x requires Java 17+ to run (matches the project's minimum Java
  version).
- `google_checks.xml` is bundled with `maven-checkstyle-plugin` and does not
  require external download.
- SpotBugs and PMD analyze compiled bytecode and source respectively; they add
  build time but no runtime dependencies.

## References

- `docs/plans/2026-02-12-tier1-decisions.md` -- tier 1 decisions
- `docs/plans/open-decisions.md` -- remaining open decisions
- `../pymqrest` -- reference implementation (100% coverage standard)
- `../standards-and-conventions/docs/foundation/summarize-decisions-protocol.md`
  -- protocol followed for this document
