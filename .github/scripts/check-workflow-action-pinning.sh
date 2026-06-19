#!/usr/bin/env bash
set -euo pipefail

workflow_dir=".github/workflows"

failed=0

list_action_uses() {
  local workflow="$1"

  python3 - "$workflow" <<'PY'
import re
import sys

workflow = sys.argv[1]
block_scalar_indent = None
block_scalar_re = re.compile(r"^(\s*)(?:-\s*)?[\w.-]+:\s*[|>][-+0-9 ]*(?:#.*)?$")
uses_re = re.compile(r"^\s*(?:-\s*)?uses:\s*([^#\s]+)")

with open(workflow, encoding="utf-8") as handle:
    for line_number, line in enumerate(handle, 1):
        text = line.rstrip("\n")
        if not text.strip():
            continue

        indent = len(text) - len(text.lstrip(" "))
        if block_scalar_indent is not None:
            if indent > block_scalar_indent:
                continue
            block_scalar_indent = None

        match = uses_re.match(text)
        if match:
            spec = match.group(1)
            if "@" in spec and not spec.startswith(("./", "docker://")):
                print(f"{line_number}:{spec}")
            continue

        if block_scalar_re.match(text):
            block_scalar_indent = indent
PY
}

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

if [[ ! -d "$workflow_dir" ]]; then
  echo "Expected workflow directory does not exist: $workflow_dir" >&2
  exit 1
fi

mapfile -t workflows < <(find "$workflow_dir" -maxdepth 1 -type f \( -name "*.yml" -o -name "*.yaml" \) | sort)
if [[ "${#workflows[@]}" -eq 0 ]]; then
  echo "No workflow files found under $workflow_dir" >&2
  exit 1
fi

for workflow in "${workflows[@]}"; do
  while IFS= read -r match; do
    line="${match%%:*}"
    spec="${match#*:}"
    action_path="${spec%@*}"
    ref="${spec##*@}"
    ref="${ref%%[[:space:]#]*}"

    if [[ ! "$ref" =~ ^[0-9a-f]{40}$ ]]; then
      echo "$workflow:$line uses a mutable action ref: @$ref" >&2
      failed=1
      continue
    fi

    verify_commit_ref "$workflow" "$line" "$action_path" "$ref"
  done < <(list_action_uses "$workflow")
done

exit "$failed"
