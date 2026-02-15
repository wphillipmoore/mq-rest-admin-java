# Contributing

This project welcomes contributions from humans working with or without
AI assistance. AI tooling is available but not required.

## Branch workflow

This project follows a library-release branching model:

- **`main`**: Release branch — tagged releases only
- **`develop`**: Integration branch — PRs merge here
- **`feature/*`**: Feature branches — created from `develop`
- **`release/*`**: Release preparation branches

Branch from `develop` using `feature/*`, `bugfix/*`, `hotfix/*`, or
`chore/*` prefixes.

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

| Gate | Tool |
| --- | --- |
| Formatting | Spotless with google-java-format (2-space indent) |
| Style | Checkstyle with `google_checks.xml` |
| Coverage | JaCoCo — 100% line and branch coverage |
| Bug analysis | SpotBugs (Max effort, Low threshold) |
| Code smells | PMD |
| Markdown lint | markdownlint |
| Commit messages | Conventional commit validation |

Run the full suite locally before pushing:

```bash
./mvnw verify
```

Run `./mvnw spotless:apply` to auto-format before committing.

## Pull request process

1. Create a `feature/*` branch from `develop`
2. Make changes and ensure `./mvnw verify` passes
3. Open a PR targeting `develop`
4. PR body must include `Fixes #N` or `Ref #N` (validated by CI)
5. CI runs the full validation pipeline
6. After review and approval, squash-merge into `develop`

## For human contributors

- Run `./mvnw verify` before pushing to catch issues early.
- Reference `docs/repository-standards.md` for the full standards
  specification.
- The `CLAUDE.md` and `AGENTS.md` files document architecture,
  patterns, and key design decisions. They are useful as reference
  material even when not using an AI agent.

## For AI agent contributors

### Agent entry points

- **Claude Code**: reads `CLAUDE.md`, which loads repository standards
  via include directives.
- **Codex and other agents**: reads `AGENTS.md`, which loads the same
  standards plus shared skills from the `standards-and-conventions`
  repository.

### Quality expectations

AI-generated code must pass all the same validation gates listed
above. There are no exceptions.

### What AI agents handle well

- Code generation from mapping data
- Test writing and coverage gap filling
- Linting and formatting fixes
- Refactoring with consistent patterns
- PR creation and submission

### What requires human judgment

- Architectural decisions and API design
- MQ domain knowledge and MQSC semantics
- Release decisions and version management
- Mapping data curation (attribute names, value translations)

### Co-author trailers

AI agents add co-author trailers to commits automatically when
following the repository standards.
