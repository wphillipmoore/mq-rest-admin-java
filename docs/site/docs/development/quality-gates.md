# Quality gates

--8<-- "development/quality-gates.md"

## Java-specific validation

The Java validation pipeline runs as a single Maven lifecycle:

```bash
./mvnw verify
```

This executes in order:

1. **Spotless** — google-java-format formatting check (2-space indent)
2. **Checkstyle** — `google_checks.xml` style rules
3. **Compile** — Source compilation
4. **Surefire** — Unit tests (`*Test.java`)
5. **Failsafe** — Integration tests (`*IT.java`)
6. **JaCoCo** — 100% line and branch coverage at BUNDLE level
7. **SpotBugs** — Bug pattern detection (max effort, low threshold)
8. **PMD** — Code smell detection

The CI matrix tests against Java 17, 21, and 25-ea with Temurin
distribution.
