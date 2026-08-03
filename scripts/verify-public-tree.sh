#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

allowed_top_level='^(.github|.gitignore|LICENSE|NOTICE.md|README.md|RELEASING.md|TRADEMARKS.md|app|build.gradle.kts|gradle|gradle.properties|gradlew|gradlew.bat|scripts|settings.gradle.kts)(/|$)'

while IFS= read -r path; do
  if [[ ! "$path" =~ $allowed_top_level ]]; then
    echo "Disallowed public path: $path" >&2
    exit 1
  fi
  case "$path" in
    *.jks|*.keystore|*.p12|*.apk|*.aab|*.apks|*.xapk|*.sql|*.sqlite|*.db|*.env)
      echo "Disallowed public artifact: $path" >&2
      exit 1
      ;;
  esac
done < <(git ls-files)

if git grep -n -I -E \
  '(-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|database_id[[:space:]]*[=:]|account_id[[:space:]]*[=:])' \
  -- . ':!scripts/verify-public-tree.sh'; then
  echo "Possible credential or private infrastructure identifier found" >&2
  exit 1
fi

required=(
  LICENSE
  NOTICE.md
  README.md
  RELEASING.md
  TRADEMARKS.md
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  gradle/wrapper/gradle-wrapper.jar
  gradle/wrapper/gradle-wrapper.properties
  gradlew
  settings.gradle.kts
)

for path in "${required[@]}"; do
  if [[ ! -f "$path" ]]; then
    echo "Required public source file is missing: $path" >&2
    exit 1
  fi
done

echo "Public source boundary verified."
