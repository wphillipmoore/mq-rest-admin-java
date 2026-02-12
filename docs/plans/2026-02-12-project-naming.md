# Project naming decision

## Table of Contents

- [Results](#results)
- [Reasoning](#reasoning)
- [Options not chosen](#options-not-chosen)
- [Open questions](#open-questions)
- [Dependencies and external constraints](#dependencies-and-external-constraints)
- [References](#references)

## Results

### Decisions made

- **Project name**: `mq-rest-admin`
- **GitHub repository name**: `mq-rest-admin`
- **Maven artifactId**: `mq-rest-admin`
- **Java package segment**: dot-separated (`mq.rest.admin`), yielding a full
  package of `{groupId}.mq.rest.admin`

### Deliverables

- Repository to be created on GitHub as `mq-rest-admin`.
- Open decisions document (`docs/plans/open-decisions.md`) to be updated to
  reflect the resolved naming items and record the groupId decision when made.

### Open decisions explicitly flagged as unresolved

- **Maven groupId**: depends on whether `io.github.wphillipmoore` or a personal
  domain (e.g., `dev.pmoore`) is used. This determines the full Java package
  namespace (e.g., `io.github.wphillipmoore.mq.rest.admin` vs
  `dev.pmoore.mq.rest.admin`).

## Reasoning

### Key constraints

- **Maven Central coordinates are effectively permanent.** Published artifacts
  cannot be deleted. Relocation POMs exist but are messy and the old coordinates
  persist forever.
- **Java package names cannot contain hyphens.** Hyphens are idiomatic in Maven
  artifact IDs but forbidden in Java identifiers. The chosen name must work in
  both contexts.
- **The MQ REST API has both administrative and messaging endpoints.** The
  library targets only the administrative portion. The name must not imply
  broader scope.
- **Naming should fit the existing IBM MQ ecosystem.** IBM's GitHub organization
  (`ibm-messaging`) consistently uses the `mq-{purpose}` pattern. Community
  projects use hyphenated lowercase with `mq` or `ibm-mq` in the name.

### Tradeoffs discussed

- **Brevity vs accuracy**: `mqrest` is shorter and simpler, but overpromises by
  implying coverage of the entire MQ REST API (admin + messaging). `mq-rest-admin`
  is longer but accurately scopes the library to administration only.
- **Hyphen/package mismatch**: `mq-rest-admin` requires a different form for the
  Java package (`mq.rest.admin`). This is a routine tradeoff in the Java
  ecosystem -- Jackson, Spring, and JUnit all manage it. The dot-separated form
  provides natural sub-package boundaries (e.g., `mq.rest.admin.session`,
  `mq.rest.admin.mapping`).

### Evidence cited

- The IBM MQ REST API documents both `/admin/` and `/messaging/` endpoint
  families. See `docs/research/admin-rest-api-gap-analysis.md`.
- pymqrest (the Python reference implementation) already exists, so the claim
  that `mqrest` would be "the only client library" is false. The name does not
  need to be language-neutral.
- IBM's own MQ GitHub repos follow `mq-{purpose}`: `mq-jms-spring`,
  `mq-mqi-nodejs`, `mq-container`, `mq-metric-samples`. `mq-rest-admin` fits
  this pattern cleanly.
- Well-known Java libraries routinely manage the hyphen/package divergence:
  `jackson-databind` → `com.fasterxml.jackson.databind`, `spring-web` →
  `org.springframework.web`, `junit-jupiter-api` → `org.junit.jupiter.api`.

## Options not chosen

### `mqrest`

- **Description**: Short, language-neutral name. No hyphen means the artifact ID
  and package segment would be identical.
- **Reason not chosen**: Overpromises scope. The MQ REST API includes both
  administrative and messaging endpoints; this library covers only
  administration. A user discovering `mqrest` on Maven Central would reasonably
  expect messaging support.
- **Status**: Rejected.

### `jmqrest`

- **Description**: Direct analogy to `pymqrest`, using the `j` prefix for Java.
- **Reason not chosen**: The `j`-prefix convention is not established in the Java
  ecosystem the way `py`-prefix is for Python. No major Java libraries follow
  this pattern.
- **Status**: Rejected.

### `mq-admin-rest-client`

- **Description**: More descriptive, Maven-style artifact name.
- **Reason not chosen**: Discussed briefly but not carried forward. Overly long
  and the `-client` suffix is redundant for a library that is inherently a
  client.
- **Status**: Not pursued.

### `mq-rest-admin` with concatenated package (`mqrestadmin`)

- **Description**: Same artifact ID but using a single concatenated segment for
  the Java package rather than dot-separated.
- **Reason not chosen**: Dot-separated packages (`mq.rest.admin`) provide
  natural sub-package boundaries and follow the more common convention among
  multi-word Java libraries (Spring, Jackson, JUnit).
- **Status**: Rejected in favor of dot-separated form.

## Open questions

- Maven groupId selection (see open decisions above).
- Whether the library will eventually cover the resource-specific CRUD endpoints
  (level 2 in `docs/research/admin-rest-api-gap-analysis.md`) in addition to the
  MQSC endpoint. The name `mq-rest-admin` accommodates both scopes.

## Dependencies and external constraints

- Maven Central namespace verification requires either a DNS TXT record (personal
  domain) or a temporary GitHub repository (for `io.github.*` namespace).
- The groupId namespace, once verified, allows publishing under any sub-group
  without additional verification.
- GitHub repository name can be changed later (redirects are created), but Maven
  coordinates cannot.

## References

- `docs/research/mq-java-ecosystem.md` -- ecosystem survey and naming
  conventions
- `docs/research/admin-rest-api-gap-analysis.md` -- REST API surface and gap
  analysis
- `docs/plans/open-decisions.md` -- remaining open decisions
- `../standards-and-conventions/docs/foundation/summarize-decisions-protocol.md`
  -- protocol followed for this document
