# Contributing

## Table of Contents

- [Branch workflow](#branch-workflow)
- [Commit conventions](#commit-conventions)
- [Code quality requirements](#code-quality-requirements)
- [Pull request process](#pull-request-process)

## Branch workflow

This project follows a library-release branching model:

- **`main`**: Release branch — tagged releases only
- **`develop`**: Integration branch — PRs merge here
- **`feature/*`**: Feature branches — created from `develop`
- **`release/*`**: Release preparation branches

## Commit conventions

Commits must follow the [Conventional Commits](https://www.conventionalcommits.org/)
format:

```text
<type>: <description>

[optional body]

[optional footer(s)]
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

## Code quality requirements

All code must pass the full validation pipeline before merging:

- **Formatting**: Spotless with google-java-format (2-space indent)
- **Style**: Checkstyle with `google_checks.xml`
- **Coverage**: 100% line and branch coverage (JaCoCo)
- **Bug analysis**: SpotBugs (Max effort, Low threshold)
- **Code smells**: PMD

Run `./mvnw spotless:apply` to auto-format before committing.

## Pull request process

1. Create a `feature/*` branch from `develop`
2. Make changes and ensure `./mvnw verify` passes
3. Open a PR targeting `develop`
4. CI runs the full validation pipeline
5. After review and approval, squash-merge into `develop`
