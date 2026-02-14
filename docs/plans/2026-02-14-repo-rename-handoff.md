# Repo rename handoff plan

## Table of Contents

- [Context](#context)
- [What is already done](#what-is-already-done)
- [What remains (pick up here)](#what-remains-pick-up-here)
- [Important notes](#important-notes)

## Context

On 2026-02-14, the following GitHub repositories were renamed to normalize the
naming convention across the mq-rest-admin family:

| Old name | New name |
| --- | --- |
| `wphillipmoore/mq-rest-admin` | `wphillipmoore/mq-rest-admin-java` |
| `wphillipmoore/pymqrest` | `wphillipmoore/mq-rest-admin-python` |
| `wphillipmoore/mq-rest-admin-go` | (already correct) |

Library/package names are unchanged (`mq-rest-admin` Maven artifact, `pymqrest`
PyPI package). Only repo names and URLs changed.

GitHub redirects are active for old URLs. Local directories have been renamed.
Branch protection rules were configured on both `develop` and `main` for
mq-rest-admin-java before the rename (they transferred automatically).

## What is already done

1. GitHub repos renamed via API
2. Local directories renamed:
   - `~/dev/github/mq-rest-admin` → `~/dev/github/mq-rest-admin-java`
   - `~/dev/github/pymqrest` → `~/dev/github/mq-rest-admin-python`
3. Local git remotes updated to new URLs
4. All file edits completed (see below) — NOT YET COMMITTED

## What remains (pick up here)

### Step 1: Commit and PR for mq-rest-admin-java

Branch: create `feature/repo-rename-references` from `develop`

Files changed (all edits already applied, just need commit + PR):

- **`pom.xml`** — 4 SCM/project URLs updated from
  `wphillipmoore/mq-rest-admin` to `wphillipmoore/mq-rest-admin-java`
- **`README.md`** — pymqrest link updated to
  `wphillipmoore/mq-rest-admin-python`
- **`CLAUDE.md`** — reference implementation path `../pymqrest` →
  `../mq-rest-admin-python`
- **`docs/plans/2026-02-12-tier2-decisions.md`** — 1 path reference
- **`docs/plans/2026-02-12-tier3-decisions.md`** — 5 path references

Also update `docs/plans/open-decisions.md` line 25:

```text
- **GitHub repository name**: `mq-rest-admin`.
```

Change to:

```text
- **GitHub repository name**: `mq-rest-admin-java`.
```

Commit message:

```text
chore: update references for repo rename to mq-rest-admin-java

Update SCM URLs in pom.xml, cross-repo references to the renamed
mq-rest-admin-python repo, and relative path references in decision
docs.
```

Run `./mvnw verify -B` before submitting the PR.

### Step 2: Commit and PR for mq-rest-admin-python

Branch: create `feature/repo-rename-references` from `develop`

Files changed (all edits already applied, just need commit + PR):

- **`pyproject.toml`** — 4 project URLs updated from
  `wphillipmoore/pymqrest` to `wphillipmoore/mq-rest-admin-python`
- **`README.md`** — docs URL updated
- **`AGENTS.md`** — title updated from `pymqrest` to
  `mq-rest-admin-python`
- **`docs/announcements/pymqrest-1.0.0-ga.md`** — 5 GitHub/docs URLs
- **`docs/sphinx/development/developer-setup.md`** — repo URL, clone
  command, directory layout
- **`.github/workflows/publish.yml`** — 2 docs site URLs
- **`src/pymqrest/commands.py`** — 50+ docs site URL references (bulk
  `replace_all` already applied)
- **`scripts/dev/generate_commands.py`** — 1 `DOCS_BASE_URL` constant

Commit message:

```text
chore: update references for repo rename to mq-rest-admin-python

Update GitHub URLs, documentation site URLs, and developer setup
instructions to reflect the repository rename from pymqrest to
mq-rest-admin-python. Python package name (pymqrest) is unchanged.
```

Run the Python repo's validation before submitting the PR.

### Step 3: Update MEMORY.md

Update the Claude Code memory file to reflect:

- New repo names and directory paths
- The rename context for future sessions

### Step 4: Verify

- Confirm both PRs merge cleanly
- Verify GitHub Pages docs URL works at new path (may require GitHub
  Pages settings update if the site was configured for the old repo name)
- Verify `git pull` works from both local directories

## Important notes

- Maven artifactId remains `mq-rest-admin` (NOT `mq-rest-admin-java`)
- Python package name remains `pymqrest` (NOT `mq-rest-admin-python`)
- Java package remains `io.github.wphillipmoore.mq.rest.admin`
- COMPOSE_PROJECT_NAME remains `mq-rest-admin` (container isolation)
- GitHub redirects from old URLs are automatic and permanent
- The `test-and-validate (25-ea)` CI check is intentionally a soft gate
  (Java 25 is not GA)
