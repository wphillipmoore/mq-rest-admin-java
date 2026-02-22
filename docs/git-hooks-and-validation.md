# Git Hooks and Validation

## Table of Contents

- [Overview](#overview)
- [Git hooks](#git-hooks)
  - [Enabling hooks](#enabling-hooks)
  - [pre-commit](#pre-commit)
  - [commit-msg](#commit-msg)
- [Lint scripts](#lint-scripts)
  - [commit-message.sh](#commit-messagesh)
  - [co-author.sh](#co-authorsh)
  - [commit-messages.sh](#commit-messagessh)
  - [repo-profile.sh](#repo-profilesh)
  - [markdown-standards.sh](#markdown-standardssh)
  - [pr-issue-linkage.sh](#pr-issue-linkagesh)
- [Validation matrix](#validation-matrix)
- [Configuration points](#configuration-points)
- [Exit code conventions](#exit-code-conventions)
- [Error reference](#error-reference)

## Overview

The validation system has two entry points that share a common set of lint
scripts:

1. **Git hooks** (`scripts/git-hooks/`) run locally on every commit, catching
   problems before they reach the remote.
2. **CI workflows** call the same lint scripts (`scripts/lint/`) against the
   full PR range, enforcing the same rules server-side.

All scripts are managed by
[standard-tooling](https://github.com/wphillipmoore/standard-tooling) and
must not be edited in downstream repos. Each script begins with the header:

```text
# Managed by standard-tooling — DO NOT EDIT in downstream repos.
```

## Git hooks

### Enabling hooks

Git hooks are not active by default. Enable them before committing:

```bash
git config core.hooksPath scripts/git-hooks
```

This is a per-clone setting. It must be run once after every fresh clone.

### pre-commit

**File**: `scripts/git-hooks/pre-commit`

Runs five checks in sequence. The hook exits on the first failure.

#### 1. Detached HEAD check

Blocks commits when HEAD is detached (not on any branch).

```text
ERROR: detached HEAD is not allowed for commits.
Create a short-lived branch and open a PR.
```

#### 2. Protected branch check

Blocks direct commits to `develop`, `release`, and `main`.

```text
ERROR: direct commits to protected branches are forbidden (develop).
Create a short-lived branch and open a PR.
```

#### 3. Branching model lookup

Reads the `branching_model` attribute from `docs/repository-standards.md` to
determine which branch prefixes are allowed. If the attribute is missing, the
hook falls back to `feature/*/bugfix/*` with a warning:

```text
WARNING: branching_model not found in docs/repository-standards.md; falling back to feature/*/bugfix/*.
```

If the attribute contains an unrecognized value:

```text
ERROR: unrecognized branching_model 'unknown-model' in docs/repository-standards.md.
```

#### 4. Branch prefix check

Validates the current branch name against the allowed prefixes for the
resolved branching model:

| Branching model         | Allowed prefixes                                     |
|-------------------------|------------------------------------------------------|
| `docs-single-branch`    | `feature/*`, `bugfix/*`                              |
| `application-promotion` | `feature/*`, `bugfix/*`, `hotfix/*`, `promotion/*`   |
| `library-release`       | `feature/*`, `bugfix/*`, `hotfix/*`, `release/*`     |

```text
ERROR: branch name must use feature/*, bugfix/*, or hotfix/* (my-branch).
Rename the branch before committing.
```

#### 5. Issue number format check

Work branches (`feature/*`, `bugfix/*`, `hotfix/*`) must include a repository
issue number followed by a lowercase hyphenated description. The `release/*`
and `promotion/*` prefixes are exempt because they are created by automated
workflows.

Expected format: `{type}/{issue}-{description}`

Pattern: `^(feature|bugfix|hotfix)/[0-9]+-[a-z0-9][a-z0-9-]*$`

```text
ERROR: branch name must include a repo issue number (feature/add-caching).
Expected format: {type}/{issue}-{description}
Example: feature/42-add-caching
```

### commit-msg

**File**: `scripts/git-hooks/commit-msg`

A thin dispatcher that calls two lint scripts in sequence, passing the commit
message file path (`$1`) to each:

1. `scripts/lint/commit-message.sh` — validates Conventional Commits format
2. `scripts/lint/co-author.sh` — validates Co-Authored-By trailers

## Lint scripts

All lint scripts live in `scripts/lint/` and can be invoked independently
from the command line or from CI workflows. Each script is self-contained
with its own argument parsing and error reporting.

### commit-message.sh

**File**: `scripts/lint/commit-message.sh`

Validates that a single commit message follows Conventional Commits format.

**Arguments**: Path to a file containing the commit message.

**Behavior**:

- Merge commits (subject starting with `Merge`) are allowed through without
  validation.
- The subject line must match:
  `^(feat|fix|docs|style|refactor|test|chore|ci|build)(\([^\)]+\))?: .+`

**Allowed types**: `feat`, `fix`, `docs`, `style`, `refactor`, `test`,
`chore`, `ci`, `build`

```text
ERROR: commit message does not follow Conventional Commits.
Expected: <type>(optional-scope): <description>
Allowed types: feat, fix, docs, style, refactor, test, chore, ci, build
Got: bad subject line
```

### co-author.sh

**File**: `scripts/lint/co-author.sh`

Validates `Co-Authored-By` trailers against the approved identities listed in
`docs/repository-standards.md`.

**Arguments**: Path to a file containing the commit message.

**Behavior**:

- If no `Co-Authored-By` trailers are present, the commit is treated as
  human-only and passes validation.
- Trailers are extracted with a case-insensitive grep for lines starting with
  `Co-Authored-By:`.
- Approved identities are read from `docs/repository-standards.md` by matching
  lines starting with `- Co-Authored-By:` under the AI co-authors section.
- Whitespace is normalized before comparison.

```text
ERROR: unapproved co-author trailer: Co-Authored-By: unknown <unknown@example.com>
Approved identities are listed in docs/repository-standards.md under 'AI co-authors'.
```

If the repository profile is missing or contains no approved identities:

```text
ERROR: repository profile not found at docs/repository-standards.md; cannot validate co-author trailers.
```

```text
ERROR: no approved co-author identities found in docs/repository-standards.md.
```

### commit-messages.sh

**File**: `scripts/lint/commit-messages.sh`

Range-based variant of `commit-message.sh` used in CI to validate all commits
in a PR branch.

**Arguments**: `<base-ref> <head-ref>`

**Behavior**:

- Iterates over `git rev-list --no-merges <base-ref>..<head-ref>`.
- Each non-merge commit subject must match the same Conventional Commits regex
  as `commit-message.sh`.
- If a bare branch name does not resolve locally, the script tries
  `origin/<branch>` as a fallback.
- Stops on the first failing commit.

**Cutoff SHA**: The `COMMIT_CUTOFF_SHA` environment variable excludes commits
at or before the specified SHA from validation. This allows repos that adopted
Conventional Commits after their initial history to skip legacy commits.

```text
ERROR: commit abc1234 does not follow Conventional Commits.
Expected: <type>(optional-scope): <description>
Allowed types: feat, fix, docs, style, refactor, test, chore, ci, build
Got: old style commit message
```

### repo-profile.sh

**File**: `scripts/lint/repo-profile.sh`

Validates that `docs/repository-standards.md` contains all six required
repository profile attributes with non-placeholder values.

**Arguments**: None (reads `docs/repository-standards.md` from the working
directory).

**Required attributes**:

| Attribute                  | Example value          |
|----------------------------|------------------------|
| `repository_type`          | `library`              |
| `versioning_scheme`        | `library`              |
| `branching_model`          | `library-release`      |
| `release_model`            | `artifact-publishing`  |
| `supported_release_lines`  | `current and previous` |
| `primary_language`         | `java`                 |

Values containing `<`, `>`, or `|` are rejected as placeholders:

```text
ERROR: repository profile attribute 'branching_model' appears to be a placeholder: <pick-one>
```

```text
ERROR: repository profile missing required attribute 'release_model' in docs/repository-standards.md
```

### markdown-standards.sh

**File**: `scripts/lint/markdown-standards.sh`

Runs structural checks and markdownlint against all markdown files in the
repository.

**Arguments**: None (discovers files automatically).

**File discovery**:

- Standard docs: all `*.md` files under `docs/` (excluding `docs/sphinx/`,
  `docs/site/`, and `docs/announcements/`), plus `README.md` if present.
  These receive both structural checks and markdownlint.
- Doc-site files: `*.md` files under `docs/sphinx/` and `docs/site/`. These
  receive markdownlint only (structural checks like single-H1 and TOC do not
  apply to generated documentation sites).
- `CHANGELOG.md`: markdownlint only (no structural checks, since changelogs
  have different heading conventions).

**Structural checks** (applied to standard docs only):

1. **Single H1**: Exactly one `#` heading per file.
2. **Table of Contents**: A `## Table of Contents` heading must be present.
3. **No heading skips**: Heading levels must not skip (e.g., `##` to `####`).

Code blocks (fenced with `` ``` `` or `~~~`) are excluded from structural
analysis.

```text
ERROR: expected exactly one H1 heading, found 2 (docs/example.md)
```

```text
ERROR: missing ## Table of Contents (docs/example.md)
```

```text
ERROR: Heading level skips from 2 to 4 (docs/example.md:15)
```

**markdownlint**: Requires `markdownlint-cli` to be installed locally. Uses
`.markdownlint.yaml` for configuration if present; otherwise falls back to
markdownlint's default config discovery (which finds `.markdownlint.json` or
other supported config formats).

```text
ERROR: markdownlint not found. Install markdownlint-cli locally.
```

### pr-issue-linkage.sh

**File**: `scripts/lint/pr-issue-linkage.sh`

Validates that the pull request body contains primary issue linkage. This
script runs in CI only (requires `GITHUB_EVENT_PATH`).

**Arguments**: None (reads the PR event payload from `$GITHUB_EVENT_PATH`).

**Accepted linkage keywords**: `Fixes`, `Closes`, `Resolves`, `Ref`

**Accepted formats**:

- Same-repo: `Fixes #123`
- Cross-repo: `Fixes owner/repo#123`
- With optional list markers: `- Fixes #123` or `* Closes #456`

```text
ERROR: pull request body is empty; issue linkage is required.
```

```text
ERROR: pull request body must include primary issue linkage (Fixes #123, Closes #123, Resolves #123, or Ref #123). Cross-repo references (owner/repo#123) are also accepted.
```

## Validation matrix

This table shows which validators run at each stage:

| Validator              | pre-commit | commit-msg | CI  |
|------------------------|:----------:|:----------:|:---:|
| Detached HEAD          | yes        |            |     |
| Protected branch       | yes        |            |     |
| Branching model lookup | yes        |            |     |
| Branch prefix          | yes        |            |     |
| Issue number format    | yes        |            |     |
| commit-message.sh      |            | yes        |     |
| co-author.sh           |            | yes        |     |
| commit-messages.sh     |            |            | yes |
| repo-profile.sh        |            |            | yes |
| markdown-standards.sh  |            |            | yes |
| pr-issue-linkage.sh    |            |            | yes |

The git hooks catch problems at commit time. CI re-validates the full commit
range and runs additional checks that require the GitHub event context or the
complete branch history.

## Configuration points

| Configuration                 | Location                                      | Used by                              |
|-------------------------------|-----------------------------------------------|--------------------------------------|
| Branching model               | `docs/repository-standards.md`                | pre-commit (branch prefix selection) |
| Approved co-author identities | `docs/repository-standards.md`                | co-author.sh                         |
| Repository profile attributes | `docs/repository-standards.md`                | repo-profile.sh                      |
| Commit cutoff SHA             | `COMMIT_CUTOFF_SHA` env var                   | commit-messages.sh                   |
| GitHub event payload          | `GITHUB_EVENT_PATH` env var                   | pr-issue-linkage.sh                  |
| markdownlint rules            | `.markdownlint.yaml` or `.markdownlint.json`  | markdown-standards.sh                |

## Exit code conventions

All scripts follow a consistent exit code scheme:

| Exit code | Meaning                                                        |
|-----------|----------------------------------------------------------------|
| `0`       | Validation passed                                              |
| `1`       | Validation failed (the input did not meet the required rules)  |
| `2`       | Usage error (missing arguments, missing files, missing tools)  |

## Error reference

Common errors and their resolutions:

**`detached HEAD is not allowed for commits`**
Checked out a tag or specific SHA. Create a branch:
`git checkout -b feature/N-description`

**`direct commits to protected branches are forbidden`**
Committing on `develop`, `release`, or `main`. Create a feature branch first.

**`unrecognized branching_model`**
`docs/repository-standards.md` has an invalid `branching_model` value. Set to
`docs-single-branch`, `application-promotion`, or `library-release`.

**`branch name must use feature/*, bugfix/*...`**
Branch does not match the allowed prefixes for the branching model. Rename:
`git branch -m feature/N-description`

**`branch name must include a repo issue number`**
Work branch missing the `{issue}-{description}` pattern. Rename:
`git branch -m feature/42-description`

**`commit message does not follow Conventional Commits`**
Subject line does not match `type(scope): description`. Amend the commit with
a valid subject.

**`unapproved co-author trailer`**
`Co-Authored-By` value not in the approved list. Check
`docs/repository-standards.md` for approved identities.

**`no approved co-author identities found`**
`docs/repository-standards.md` has no `- Co-Authored-By:` entries. Add
approved identities to the AI co-authors section.

**`repository profile missing required attribute`**
A required profile attribute is absent. Add the missing attribute to
`docs/repository-standards.md`.

**`appears to be a placeholder`**
Profile attribute contains `<`, `>`, or `|`. Replace the placeholder with an
actual value.

**`markdownlint not found`**
`markdownlint-cli` is not installed. Install: `npm install -g markdownlint-cli`

**`expected exactly one H1 heading`**
Markdown file has zero or multiple `#` headings. Ensure exactly one `#`
heading at the top.

**`missing ## Table of Contents`**
Standard doc missing the TOC heading. Add a `## Table of Contents` section.

**`Heading level skips from N to M`**
Heading jumps more than one level (e.g., `##` to `####`). Add intermediate
heading levels.

**`pull request body is empty`**
PR has no description. Add a body with issue linkage.

**`pull request body must include primary issue linkage`**
PR body missing `Fixes #N` or equivalent. Add `Fixes #N`, `Closes #N`,
`Resolves #N`, or `Ref #N`.
