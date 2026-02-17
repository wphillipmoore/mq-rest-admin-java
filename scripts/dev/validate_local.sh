#!/usr/bin/env bash
# Local validation script mirroring CI hard gates.
# See: https://github.com/wphillipmoore/standards-and-conventions/blob/develop/docs/repository/local-validation-scripts.md

set -euo pipefail

# --- Prerequisite checks ---

missing=()

if ! command -v java &>/dev/null; then
    missing+=("java")
fi

if [[ ! -x ./mvnw ]]; then
    missing+=("./mvnw (Maven Wrapper)")
fi

if [[ ${#missing[@]} -gt 0 ]]; then
    echo "ERROR: Missing required tools: ${missing[*]}" >&2
    echo "Install with:" >&2
    for tool in "${missing[@]}"; do
        case "$tool" in
            java)  echo "  brew install openjdk@17  (or use SDKMAN)" >&2 ;;
            *)     echo "  Maven Wrapper should be checked into the repo" >&2 ;;
        esac
    done
    exit 1
fi

# --- Validation steps ---

run() {
    echo "Running: $*"
    "$@"
}

run ./mvnw verify -B
