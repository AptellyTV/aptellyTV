#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_FILE="$ROOT/app/build.gradle.kts"
VERSION_NAME="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$BUILD_FILE" | head -n 1)"
VERSION_CODE="$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' "$BUILD_FILE" | head -n 1)"

if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" || "$VERSION_CODE" -le 0 ]]; then
  echo "Unable to read a valid client version" >&2
  exit 1
fi

if [[ $# -gt 0 && "$1" != "v${VERSION_NAME}" ]]; then
  echo "Tag $1 does not match client version v${VERSION_NAME}" >&2
  exit 1
fi

echo "Aptelly ${VERSION_NAME} (${VERSION_CODE})"
