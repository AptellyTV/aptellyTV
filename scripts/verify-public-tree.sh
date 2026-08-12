#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

allowed='^(.github/workflows/release-policy.yml|.gitignore|LICENSE|NOTICE.md|README.md|README\.(zh-CN|ja|ko)\.md|TRADEMARKS.md|assets/aptelly-(logo|hero|youtube|netflix|disney).png|assets/aptelly-github-promo.gif|scripts/verify-public-tree.sh|scripts/verify-release.mjs)$'
while IFS= read -r path; do
  if [[ ! "$path" =~ $allowed ]]; then
    echo "Disallowed public path: $path" >&2
    exit 1
  fi
done < <(git ls-files)

if git grep -n -I -E \
  '(-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|database_id[[:space:]]*[=:]|account_id[[:space:]]*[=:])' \
  -- . ':!scripts/verify-public-tree.sh'; then
  echo "Private source, infrastructure or credential material found" >&2
  exit 1
fi


if git rev-list HEAD --objects | grep -E \
  '(^|[[:space:]])(app/|services/|gradle/|dows/|tools/|build\.gradle|settings\.gradle|gradlew|wrangler)' ; then
  echo "Source, server or internal project history found in public repository" >&2
  exit 1
fi

echo "Public download-only repository boundary verified."
