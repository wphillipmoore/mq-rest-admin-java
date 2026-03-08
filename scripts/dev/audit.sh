#!/usr/bin/env bash
set -euo pipefail

export DOCKER_DEV_IMAGE="${DOCKER_DEV_IMAGE:-dev-java:21}"
export DOCKER_TEST_CMD="${DOCKER_TEST_CMD:-./mvnw dependency:tree -B -q && ./mvnw org.codehaus.mojo:license-maven-plugin:2.7.0:add-third-party -Dlicense.excludedScopes=test -Dlicense.failIfWarning=true '-Dlicense.includedLicenses=Apache-2.0|Apache 2.0|The Apache License, Version 2.0|MIT License|BSD-2-Clause|BSD-3-Clause|ISC|MPL-2.0|GPL-3.0-or-later' -B}"

if ! command -v st-docker-test >/dev/null 2>&1; then
  echo "ERROR: st-docker-test not found on PATH." >&2
  echo "Set up standard-tooling: export PATH=../standard-tooling/.venv/bin:\$PATH" >&2
  exit 1
fi
exec st-docker-test
