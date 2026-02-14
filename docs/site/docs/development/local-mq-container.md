# Local MQ Container

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Container management](#container-management)
- [Port assignments](#port-assignments)
- [Running integration tests](#running-integration-tests)
- [Environment variables](#environment-variables)

## Overview

Integration tests run against live IBM MQ containers managed by the
[mq-dev-environment](https://github.com/wphillipmoore/mq-dev-environment)
project. Wrapper scripts in `scripts/dev/` delegate to that project's Docker
Compose setup.

## Prerequisites

- **Docker**: Running Docker daemon
- **mq-dev-environment**: Clone as a sibling directory:

```bash
git clone https://github.com/wphillipmoore/mq-dev-environment.git ../mq-dev-environment
```

## Container management

```bash
scripts/dev/mq_start.sh    # Start MQ containers
scripts/dev/mq_stop.sh     # Stop MQ containers
scripts/dev/mq_seed.sh     # Initialize queue manager configuration
scripts/dev/mq_reset.sh    # Reset to clean state
scripts/dev/mq_verify.sh   # Verify MQ environment health
```

## Port assignments

| Service | Port |
| --- | --- |
| QM1 REST API | 9453 |
| QM2 REST API | 9454 |
| QM1 MQ listener | 1424 |
| QM2 MQ listener | 1425 |

## Running integration tests

```bash
# Start MQ and seed configuration
scripts/dev/mq_start.sh
scripts/dev/mq_seed.sh

# Run integration tests
MQ_REST_ADMIN_RUN_INTEGRATION=1 ./mvnw verify

# Stop MQ when done
scripts/dev/mq_stop.sh
```

## Environment variables

| Variable | Default | Description |
| --- | --- | --- |
| `MQ_DEV_ENV_PATH` | `../mq-dev-environment` | Path to mq-dev-environment project |
| `MQ_REST_ADMIN_RUN_INTEGRATION` | (unset) | Set to `1` to enable integration tests |
| `COMPOSE_PROJECT_NAME` | `mq-rest-admin` | Docker Compose project name |
