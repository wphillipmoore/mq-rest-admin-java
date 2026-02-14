# Documentation architecture

**Date**: 2026-02-14

## Table of Contents

- [Decision](#decision)
- [Context](#context)
- [Options considered](#options-considered)
- [Architecture](#architecture)
- [Shared fragment design](#shared-fragment-design)
- [Java doc site (MkDocs + Material)](#java-doc-site-mkdocs--material)
- [Java API reference (Javadoc)](#java-api-reference-javadoc)
- [Mapping doc generation](#mapping-doc-generation)
- [CI workflow](#ci-workflow)
- [Implementation order](#implementation-order)
- [Follow-up work](#follow-up-work)

## Decision

Per-repo documentation sites using language-appropriate tooling, with shared
conceptual content as Markdown fragments in a new `mq-rest-admin-common` repo.

| Repo | Doc tooling | API reference | Status |
| --- | --- | --- | --- |
| mq-rest-admin-python | Sphinx + MyST + Furo | Sphinx autodoc | Existing |
| mq-rest-admin-java | MkDocs + Material | maven-javadoc-plugin | This plan |
| mq-rest-admin-go | MkDocs + Material | godoc | Future |
| mq-rest-admin-common | N/A (fragment source) | N/A | This plan |

## Context

The mq-rest-admin project family (Java, Python, Go) aims for feature and API
parity across implementations. Much of the documentation is conceptual content
about IBM MQ, the REST API, the attribute mapping pipeline, and design
rationale -- all language-neutral. Each language also needs its own installation
guide, quickstart, code examples, and auto-generated API reference.

Two high-level options were evaluated:

1. **Single doc tree** in a common repo (e.g., Sphinx with multi-language tabs)
2. **Per-repo doc trees** with shared fragments for common content

## Options considered

### Option 1: Single Sphinx doc tree (rejected)

A single Sphinx site documenting all three languages side-by-side.

**Pros**: Single source of truth, no sync problem.

**Cons**: Sphinx autodoc only works for Python natively. Javadoc and godoc cannot
be integrated into a Sphinx build. Documenting three languages side-by-side
clutters the reading experience. A Java developer shouldn't have to navigate past
Python and Go content to find what they need.

### Option 2: Per-repo with shared fragments (selected)

Each repo owns its doc site using that language's community-standard tooling.
Shared conceptual content lives as Markdown fragments in a common repo, included
at build time.

**Pros**: Best autodoc integration per language. Clean single-language reading
experience. Shared prose has a single source of truth.

**Cons**: More infrastructure (common repo, include mechanism, CI clone step).
Each repo composes pages from fragments, requiring some boilerplate.

### Tooling sub-decisions

**Why MkDocs + Material for Java (not Antora + AsciiDoc)**:

Antora is the Java ecosystem standard (Spring, Quarkus, Hibernate) and has
built-in multi-repo support. However:

- Antora uses AsciiDoc, not Markdown. Shared fragments would need format
  conversion or dual maintenance.
- Java developers care about Javadoc being standard (and it will be, via
  maven-javadoc-plugin). The surrounding doc site tooling matters less.
- MkDocs + Material reads Markdown natively, matching the fragment format.
- The Go repo would also use MkDocs, giving two of three repos the same tooling.
- Nobody picks a Java library based on whether the user guide uses Antora vs
  MkDocs.

**Why maven-javadoc-plugin (not Dokka or hand-written)**:

- Standard Javadoc is universally expected by Java developers.
- Source already has comprehensive Javadoc annotations (`@param`, `@return`,
  `@throws`, `@code`).
- Published as a `/javadoc/` subdirectory under the MkDocs site for a unified
  URL.
- Dokka is Kotlin-first and adds complexity for a pure Java project.

**Why CI clone step (not git submodules)**:

- Same pattern already used for `mq-dev-environment` in CI.
- Local dev uses sibling directory convention (`../mq-rest-admin-common`).
- Simpler than submodules (no `.gitmodules`, no recursive clone friction).
- A helper script creates a symlink for local builds.

## Architecture

### Include mechanism

```text
mq-rest-admin-common/fragments/    (shared Markdown fragments)
         |
         +--- CI: actions/checkout clones to .mq-rest-admin-common/
         +--- Local: symlink .mq-rest-admin-common -> ../mq-rest-admin-common
         |
    +----+----+
    |         |
    v         v
  Python    Java
  Sphinx    MkDocs
  {include} --8<-- (snippets)
```

Both Sphinx (MyST `{include}`) and MkDocs (`pymdownx.snippets`) can include
external Markdown files. The `mkdocs.yml` snippets config lists
`.mq-rest-admin-common/fragments` as a base path. Sphinx uses relative paths
from the doc source directory.

## Shared fragment design

### Repository structure

```text
mq-rest-admin-common/
  README.md
  fragments/
    architecture/
      component-overview.md
      request-lifecycle.md
      transport-abstraction.md
      single-endpoint-design.md
      gateway-routing.md
    mapping-pipeline/
      three-namespace-problem.md
      qualifier-based-mapping.md
      request-mapping-flow.md
      response-mapping-flow.md
      strict-vs-lenient.md
      custom-mapping-overrides.md
    design/
      rationale.md
      runcommand-endpoint.md
      nested-object-flattening.md
    concepts/
      ensure-pattern.md
      sync-pattern.md
```

### Content extraction rules

Fragments are **partial sections** (not complete pages). Each consuming site
composes them into larger pages with language-specific framing text.

**Fragments contain**:

- Concept explanations (the "what" and "why")
- Language-neutral diagrams and tables
- MQ REST API protocol documentation (endpoint structure, payload format, CSRF)

**Fragments do NOT contain**:

- Language-specific code examples or class/method names
- Doc-tool-specific syntax (no Sphinx `{doc}` or MkDocs cross-refs)
- References to `pymqrest`, `MqRestSession`, etc. (use "the session object",
  "the transport interface", etc.)

**Source material**: extract from pymqrest's existing docs at
`mq-rest-admin-python/docs/sphinx/`:

| pymqrest file | Fragments extracted |
| --- | --- |
| `architecture.md` | `architecture/*.md` (5 fragments) |
| `mapping-pipeline.md` | `mapping-pipeline/*.md` (6 fragments) |
| `design/rationale.md` | `design/rationale.md` |
| `design/runcommand-endpoint.md` | `design/runcommand-endpoint.md` |
| `design/nested-object-flattening.md` | `design/nested-object-flattening.md` |
| `ensure-methods.md` (conceptual parts) | `concepts/ensure-pattern.md` |
| `sync-methods.md` (conceptual parts) | `concepts/sync-pattern.md` |

## Java doc site (MkDocs + Material)

### Directory layout

```text
docs/site/
  mkdocs.yml
  docs/
    index.md                        # Project intro
    getting-started.md              # Java installation, quickstart
    ensure-methods.md               # Java ensure examples
    sync-methods.md                 # Java sync examples
    architecture.md                 # Shared fragments + Java specifics
    mapping-pipeline.md             # Shared fragments + Java specifics
    api/
      index.md
      session.md                    # MqRestSession
      commands.md                   # Command methods
      auth.md                       # Credentials hierarchy
      transport.md                  # MqRestTransport, HttpClientTransport
      mapping.md                    # AttributeMapper, MappingData
      exceptions.md                 # Exception hierarchy
      ensure.md                     # EnsureResult, EnsureAction
      sync.md                       # SyncConfig, SyncResult
    mappings/
      index.md                      # Generated qualifier index
      queue.md, channel.md, ...     # Generated (42+ files)
    design/
      index.md
      rationale.md
      runcommand-endpoint.md
      nested-object-flattening.md
    development/
      index.md
      developer-setup.md
      contributing.md
      local-mq-container.md
    javadoc.md                      # Landing page linking to /javadoc/
```

### Fragment composition example

`docs/site/docs/architecture.md` composes shared fragments with Java-specific
content:

```markdown
# Architecture

## Component overview

--8<-- "architecture/component-overview.md"

In the Java implementation, the core components are:

**MqRestSession** — the main entry point, owns authentication and URL
construction. Created via builder pattern.

(Java-specific class descriptions and code examples follow)
```

### Sibling directory pattern

- **CI**: `actions/checkout` clones common repo to `.mq-rest-admin-common/`
- **Local dev**: `scripts/dev/docs-setup.sh` creates symlink
  `.mq-rest-admin-common -> ../mq-rest-admin-common`
- **mkdocs.yml**: `pymdownx.snippets` base_path includes
  `.mq-rest-admin-common/fragments`
- `.mq-rest-admin-common` added to `.gitignore`

### Key mkdocs.yml settings

- **Theme**: `material` with `navigation.tabs`, `content.code.copy`, `search.suggest`
- **Snippets**: base_path includes both `docs/site/docs` and
  `.mq-rest-admin-common/fragments`
- **Strict mode**: `strict: true` (warnings as errors in CI)
- **Extensions**: admonitions, highlight, superfences, tabbed, tables, toc

## Java API reference (Javadoc)

Add `maven-javadoc-plugin` to `pom.xml`:

- Version property: `maven-javadoc-plugin.version`
- Config: `source=17`, `doclint=all,-missing`, `quiet=true`
- Output: `docs/site/site/javadoc/` (same directory MkDocs builds to)
- Run: `./mvnw javadoc:javadoc`

Javadoc is published as a `/javadoc/` subdirectory under the MkDocs site. The
`javadoc.md` page in the MkDocs nav links to it.

## Mapping doc generation

- **Script**: `scripts/dev/generate_mapping_docs.py` (Python -- MkDocs already
  requires Python, and the pymqrest generator provides a proven template)
- **Input**: `src/main/resources/.../mapping/mapping-data.json`
- **Output**: `docs/site/docs/mappings/*.md` (42+ qualifier pages)
- **Template**: port from
  `mq-rest-admin-python/scripts/dev/generate_mapping_docs.py`, adapted to read
  JSON via `json.load()` instead of importing a Python module
- **Tables**: request key map, response key map, request value map, response
  value map, request key-value map (same structure as pymqrest)

## CI workflow

New file: `.github/workflows/docs.yml`

```text
Trigger: push to main, PRs to main/develop

Jobs:
  build:
    - Checkout code
    - Clone mq-rest-admin-common to .mq-rest-admin-common/
    - Setup Java 17 + Python 3.12
    - pip install mkdocs-material
    - python scripts/dev/generate_mapping_docs.py
    - ./mvnw javadoc:javadoc -B -q
    - mkdocs build -f docs/site/mkdocs.yml
    - Upload pages artifact (docs/site/site/)

  deploy (main only):
    - actions/deploy-pages@v4
    - URL: https://wphillipmoore.github.io/mq-rest-admin-java/
```

## Implementation order

1. Create `mq-rest-admin-common` repo with fragment structure and README
2. Extract shared content from pymqrest docs into fragments
3. Add `maven-javadoc-plugin` to `pom.xml`
4. Create `docs/site/mkdocs.yml` and `scripts/dev/docs-setup.sh`
5. Create `scripts/dev/generate_mapping_docs.py`, generate mapping pages
6. Write Java doc pages (index, getting-started, architecture, etc.)
7. Write API reference pages (session, commands, auth, etc.)
8. Create `.github/workflows/docs.yml`
9. Test full build locally, then deploy via CI

## Follow-up work

- **Python refactoring**: update pymqrest Sphinx site to include shared fragments
  from mq-rest-admin-common via MyST `{include}` directives (replace inline
  content with fragment includes + Python-specific framing)
- **Go doc site**: MkDocs + Material setup mirroring the Java structure
- **Update open-decisions.md**: mark documentation site generator as decided
