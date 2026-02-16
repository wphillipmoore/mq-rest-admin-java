# AI-assisted engineering

--8<-- "ai-engineering.md"

## Java-specific quality standards

**100% test coverage**: Every line and branch of production code is
covered by unit tests. Coverage is enforced as a CI hard gate via JaCoCo
at the BUNDLE level.

**Formatting and style**: Spotless with google-java-format enforces
consistent 2-space indentation and import ordering. Checkstyle with
`google_checks.xml` enforces naming and structural conventions.

**Static analysis**: SpotBugs (max effort, low threshold) catches bug
patterns. PMD catches code smells. Both run as CI hard gates.

**Validation pipeline**: `./mvnw verify` runs the same checks as CI:
formatting → style → compile → unit tests → integration tests → coverage
→ SpotBugs → PMD.
