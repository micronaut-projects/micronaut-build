#!/usr/bin/env bash
set -euo pipefail

workflows=(
  ".github/workflows/gradle.yml"
  ".github/workflows/release.yml"
  ".github/workflows/update-gradle-wrapper.yml"
)

failed=0

for workflow in "${workflows[@]}"; do
  if [[ ! -f "$workflow" ]]; then
    echo "Expected workflow does not exist: $workflow" >&2
    failed=1
    continue
  fi

  while IFS= read -r match; do
    line="${match%%:*}"
    ref="${match##*@}"
    ref="${ref%%[[:space:]#]*}"

    if [[ ! "$ref" =~ ^[0-9a-f]{40}$ ]]; then
      echo "$workflow:$line uses a mutable action ref: @$ref" >&2
      failed=1
    fi
  done < <(grep -nE 'uses:[[:space:]]+[^#[:space:]]+@' "$workflow" || true)
done

exit "$failed"
