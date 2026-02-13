#!/usr/bin/env bash
set -euo pipefail

commit_message_file="${1:-}"

if [[ -z "$commit_message_file" || ! -f "$commit_message_file" ]]; then
  echo "ERROR: commit message file path is required." >&2
  exit 2
fi

repo_root="$(git rev-parse --show-toplevel)"
standards_file="$repo_root/docs/repository-standards.md"

if [[ ! -f "$standards_file" ]]; then
  echo "ERROR: repository standards file not found at $standards_file" >&2
  exit 2
fi

# Extract Co-Authored-By trailers from the commit message (name <email> portion).
commit_trailers=()
while IFS= read -r line; do
  # Strip "Co-Authored-By: " prefix to get "name <email>"
  identity="${line#*Co-Authored-By: }"
  commit_trailers+=("$identity")
done < <(grep -i '^Co-Authored-By:' "$commit_message_file" || true)

# No trailers means no AI contribution — that's fine.
if [[ ${#commit_trailers[@]} -eq 0 ]]; then
  exit 0
fi

# Extract approved identities from repository-standards.md.
# Lines look like: "- Co-Authored-By: name <email>"
approved_identities=()
while IFS= read -r line; do
  identity="${line#*Co-Authored-By: }"
  approved_identities+=("$identity")
done < <(grep -E '^\- Co-Authored-By:' "$standards_file" || true)

if [[ ${#approved_identities[@]} -eq 0 ]]; then
  echo "ERROR: no approved Co-Authored-By identities found in $standards_file" >&2
  exit 2
fi

# Validate each commit trailer against the approved list.
failed=0
for trailer in "${commit_trailers[@]}"; do
  match=0
  for approved in "${approved_identities[@]}"; do
    if [[ "$trailer" == "$approved" ]]; then
      match=1
      break
    fi
  done
  if [[ $match -eq 0 ]]; then
    echo "ERROR: unapproved Co-Authored-By trailer:" >&2
    echo "  Co-Authored-By: $trailer" >&2
    failed=1
  fi
done

if [[ $failed -ne 0 ]]; then
  echo "" >&2
  echo "Approved identities (from docs/repository-standards.md):" >&2
  for approved in "${approved_identities[@]}"; do
    echo "  Co-Authored-By: $approved" >&2
  done
  exit 1
fi
