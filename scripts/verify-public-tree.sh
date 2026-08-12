#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

allowed='^(.github/workflows/(release-policy|source-ci)\.yml|.gitignore|LICENSE|NOTICE.md|README.md|README\.(zh-CN|ja|ko)\.md|TRADEMARKS.md|assets/aptelly-(logo|hero|youtube|netflix|disney).png|assets/aptelly-github-promo.gif|scripts/verify-public-tree.sh|scripts/verify-release.mjs|app/.*|build\.gradle\.kts|settings\.gradle\.kts|gradle\.properties|gradle/wrapper/gradle-wrapper\.(jar|properties)|gradlew|gradlew\.bat)$'
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


if git grep -n -I -E \
  '(workers\.dev|APTELLY_MATCH_API_BASE_URL|APTELLY_POSTER_FEED_URL|ApiRequestAuth|MatchingApiClient|CatalogAvailabilityClient|DeviceProfileReporter|/v1/(auth/register|apps/resolve|catalog/availability|device-profiles|install-events|privacy/delete|aptelly/releases/stable))' \
  -- app build.gradle.kts settings.gradle.kts gradle.properties; then
  echo "Aptelly private-service request code found in public client source" >&2
  exit 1
fi

required=(
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/app/aptelly/tv/MainActivity.java
  app/src/main/res/values/strings.xml
  build.gradle.kts
  settings.gradle.kts
  gradle/wrapper/gradle-wrapper.jar
  gradle/wrapper/gradle-wrapper.properties
  gradlew
)
for path in "${required[@]}"; do
  if [[ ! -f "$path" ]]; then
    echo "Required Android source file missing: $path" >&2
    exit 1
  fi
done

if git ls-files | grep -E '(^|/)(build|outputs)/|\.apk$|\.aab$'; then
  echo "Generated build output found in the public repository" >&2
  exit 1
fi

echo "Public Android source boundary and required structure verified."
