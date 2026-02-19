# Integration test strategy

**Date**: 2026-02-14

## Table of Contents

- [Decision](#decision)
- [Context](#context)
- [Isolation mechanism](#isolation-mechanism)
- [Local development](#local-development)
- [CI](#ci)
- [Java integration test gating](#java-integration-test-gating)
- [Backward compatibility](#backward-compatibility)

## Decision

Each consuming project (pymqrest, mq-rest-admin, future Go port) runs distinct
MQ containers isolated via `COMPOSE_PROJECT_NAME` and project-specific host port
allocation. Shared Docker infrastructure is provided by the `mq-rest-admin-dev-environment`
repository.

## Context

The `mq-rest-admin-dev-environment` repo provides shared Docker infrastructure: a
docker-compose.yml with QM1 and QM2, seed scripts for test objects, lifecycle
scripts (start/seed/verify/stop/reset), and a CI composite action. This is
already consumed by pymqrest.

Previously, container names, network name, and host ports were hardcoded,
preventing multiple projects from running integration tests simultaneously.

## Isolation mechanism

### COMPOSE_PROJECT_NAME

Docker Compose uses `COMPOSE_PROJECT_NAME` to prefix container and network names.
Each project sets a unique project name:

- `pymqrest` (or `mq-dev`, the default)
- `mq-rest-admin`

This prevents container name collisions when multiple projects run
simultaneously.

### Port allocation

Each project uses a unique set of host ports with an offset of 10 per project:

| Project | QM1 REST | QM2 REST | QM1 MQ | QM2 MQ |
| --- | --- | --- | --- | --- |
| pymqrest | 9443 (default) | 9444 (default) | 1414 (default) | 1415 (default) |
| mq-rest-admin | 9453 | 9454 | 1424 | 1425 |
| Go port (future) | 9463 | 9464 | 1434 | 1435 |

Port configuration is passed via environment variables (`QM1_REST_PORT`,
`QM2_REST_PORT`, `QM1_MQ_PORT`, `QM2_MQ_PORT`) which docker-compose.yml
interpolates into the `ports:` mapping.

## Local development

Wrapper scripts in `scripts/dev/mq_*.sh` set the project-specific environment
variables and delegate to the corresponding `mq-rest-admin-dev-environment` scripts:

- `scripts/dev/mq_start.sh` — start containers on project-specific ports
- `scripts/dev/mq_seed.sh` — seed MQ objects
- `scripts/dev/mq_verify.sh` — verify environment readiness
- `scripts/dev/mq_stop.sh` — stop containers
- `scripts/dev/mq_reset.sh` — stop containers and remove volumes

The `mq-rest-admin-dev-environment` repo is expected at `../mq-rest-admin-dev-environment` (sibling
directory) by default, overridable via `MQ_DEV_ENV_PATH`.

## CI

The `mq-rest-admin-dev-environment` composite action (`setup-mq`) accepts `project-name`,
`qm1-rest-port`, and `qm2-rest-port` inputs. mq-rest-admin's CI workflow will
pass its project-specific values.

## Java integration test gating

- Integration test files follow the `*IT.java` naming convention
- `maven-failsafe-plugin` runs them during `mvn verify`
- Tests are gated by `@EnabledIfEnvironmentVariable` on
  `MQ_REST_ADMIN_RUN_INTEGRATION` — they are skipped unless the environment
  variable is set
- Integration tests are excluded from JaCoCo coverage enforcement (unit tests
  already achieve 100% line and branch coverage)
- CI runs integration tests on a single Java version (not the full 17/21/25
  matrix) to reduce resource usage

## Backward compatibility

All environment variables default to current values. Callers that do not set
any variables get identical behavior to the pre-parameterization setup:

- Default ports: 9443/9444 (REST), 1414/1415 (MQ)
- Default project name: `mq-dev`

The only visible change is container naming (from `mq-dev-qm1` to
`<project>-qm1-1`), but no consumer references containers by name — scripts
use `docker compose exec <service>`.
