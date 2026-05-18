#!/usr/bin/env bash
set -euo pipefail

workflows=(
  ".github/workflows/gradle.yml"
  ".github/workflows/release.yml"
  ".github/workflows/update-gradle-wrapper.yml"
)

failed=0

verify_commit_ref() {
  local workflow="$1"
  local line="$2"
  local action_path="$3"
  local ref="$4"
  local owner repo repo_url tmp object_type

  IFS=/ read -r owner repo _ <<< "$action_path"
  if [[ -z "${owner:-}" || -z "${repo:-}" ]]; then
    echo "$workflow:$line cannot determine action repository for $action_path" >&2
    failed=1
    return
  fi

  repo_url="https://github.com/$owner/$repo.git"
  tmp="$(mktemp -d)"
  git -C "$tmp" init -q

  if ! git -C "$tmp" fetch --depth=1 "$repo_url" "$ref" >/dev/null 2>&1; then
    echo "$workflow:$line cannot fetch $action_path@$ref" >&2
    failed=1
    rm -rf "$tmp"
    return
  fi

  object_type="$(git -C "$tmp" cat-file -t "$ref" 2>/dev/null || true)"
  if [[ "$object_type" != "commit" ]]; then
    echo "$workflow:$line uses a non-commit action ref: $action_path@$ref is a $object_type object" >&2
    failed=1
  fi

  rm -rf "$tmp"
}

for workflow in "${workflows[@]}"; do
  if [[ ! -f "$workflow" ]]; then
    echo "Expected workflow does not exist: $workflow" >&2
    failed=1
    continue
  fi

  while IFS= read -r match; do
    line="${match%%:*}"
    spec="$(sed -E 's/^[^:]+:[[:space:]-]*uses:[[:space:]]*([^#[:space:]]+).*/\1/' <<< "$match")"
    action_path="${spec%@*}"
    ref="${spec##*@}"
    ref="${ref%%[[:space:]#]*}"

    if [[ ! "$ref" =~ ^[0-9a-f]{40}$ ]]; then
      echo "$workflow:$line uses a mutable action ref: @$ref" >&2
      failed=1
      continue
    fi

    verify_commit_ref "$workflow" "$line" "$action_path" "$ref"
  done < <(grep -nE 'uses:[[:space:]]+[^#[:space:]]+@' "$workflow" || true)
done

exit "$failed"
