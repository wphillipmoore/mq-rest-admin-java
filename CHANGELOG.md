# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/)
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.1.4] - 2026-02-17

### Bug fixes

- sync prepare_release.py ruff formatting from canonical (#113)

### Features

- use GitHub App token for bump PR to trigger CI (#116)

## [1.1.3] - 2026-02-16

### Bug fixes

- sync prepare_release.py changelog conflict fix from canonical (#107)

## [1.1.2] - 2026-02-16

### Bug fixes

- sync prepare_release.py with canonical version
- sync prepare_release.py merge message fix from canonical (#102)

## [1.1.1] - 2026-02-16

### Bug fixes

- set docs default version to latest on main deploy
- allow commits on release/* branches in library-release model
- remove PR_BUMP_TOKEN and add issue linkage to version bump PR

### Documentation

- refresh CLAUDE.md with current project state (#91)

## [1.1.0] - 2026-02-16

### Bug fixes

- resolve three CI job failures (#27)
- add blank line and Table of Contents to README.md (#29)
- sync mapping data with pymqrest and extract parameters from response (#38)
- disable MD041 for mkdocs snippet-include files
- correct table column count in local MQ container docs
- correct snippets base_path resolution for fragment includes (#74)
- run mike from repo root so snippet base_path resolves in CI (#75)
- propagate 4 missing mapping entries from canonical JSON (#80)

### Documentation

- record tier 1 decisions (groupId, Java 17, Maven) (#2)
- record tier 3 architecture decisions (#5)
- update open-decisions to reflect completed implementation (#32)
- add documentation site generator to open decisions (#37)
- enrich ensure-methods and getting-started pages (#45)
- enrich documentation pages and remove inline TOC sections (#47)
- add sync hallucination case study (#48)
- rewrite sync documentation to match actual implementation (#49)
- close content gaps in ensure, sync, mapping, and getting-started pages (#57)
- replace BasicAuth with LtpaAuth in examples and remove Next Steps (#61)
- address medium-severity documentation consistency findings (#63)
- address cross-library documentation consistency nits (#67)
- switch to shared fragment includes from common repo (#71)
- add quality gates documentation page
- close stale documentation generator TBD in open-decisions

### Features

- add repository scaffolding and research documents (#1)
- add Maven project skeleton (#3)
- add testing framework and code quality tooling (#4)
- implement sealed exception hierarchy (#7)
- add transport interface and response record (#8)
- add auth types (Credentials sealed interface + records) (#9)
- add mapping issue types (MappingIssue record + MappingException) (#11)
- implement attribute mapping pipeline (AttributeMapper + MappingData + MappingOverrideMode) (#12)
- implement MqRestSession core with Builder, mqscCommand, and helpers (#13)
- add 118 command methods to MqRestSession (#14)
- add ensure package (EnsureAction, EnsureResult) and 16 ensure methods on MqRestSession (#15)
- add sync package (SyncOperation, SyncConfig, SyncResult) and 9 sync methods on MqRestSession (#18)
- add git hooks for branch protection, commit message, and co-author validation (#21)
- implement HttpClientTransport with JDK HttpClient (#19)
- populate mapping-data.json with full MQ attribute set (#23)
- add GitHub Actions CI workflow with 6-job pipeline (#25)
- add Error Prone, NullAway, and JSpecify null-safety tooling (#30)
- adopt canonical git hooks from standards-and-conventions (#31)
- add integration test strategy and MQ dev wrapper scripts (#6)
- wire up CI integration tests with live MQ containers (#35)
- add MkDocs documentation site with shared fragment architecture (#41)
- add Tier 1 security tooling (CodeQL, attestations, license compliance)
- add Trivy and Semgrep CI jobs and SBOM generation
- configure Maven Central publication via Central Portal API
