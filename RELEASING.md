# Public client release policy

This policy is mandatory for every official Aptelly release.

## Source boundary

The public repository contains the complete Android client required to build the
application. It must not contain cloud service implementations, production data,
database migrations, infrastructure identifiers, backups, internal reports,
signing material, credentials, or device evidence.

Only the following top-level paths are allowed:

- Android source and build files: `app/`, `gradle/`, `build.gradle.kts`,
  `settings.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`;
- public automation: `.github/`, `scripts/`;
- public project documents: `README.md`, `RELEASING.md`, `NOTICE.md`,
  `TRADEMARKS.md`, `LICENSE`, `.gitignore`.

## Release gate

An official release is valid only when all of these conditions pass:

1. The release tag is `v<versionName>` and points to the exact public source used
   to build the APK.
2. `versionCode` is positive and greater than the previous official release.
3. Public CI passes unit tests, lint, and a complete Debug APK build with JDK 21.
4. The official APK is produced from the same source with the protected Aptelly
   release key and passes Android signature verification.
5. The Release contains exactly three assets: the APK, its `.sha256`, and the
   offline-signed `.json` update envelope.
6. The APK byte size and SHA-256 match both companion files.
7. The signed payload matches the package name, version, minimum SDK, immutable
   GitHub Release URL, APK size, APK hash, and pinned release certificate.
8. The Release body is empty; user documentation remains in `README.md`.
9. The release is created as a draft, verified after upload, and published only
   after every check passes.

The Release workflow repeats the checks after publication. A release that bypasses
the draft gate or fails validation is returned to draft state automatically. After
a successful release, only the five newest official releases and tags are retained.

## Public verification

```sh
./scripts/verify-public-tree.sh
./scripts/verify-source-version.sh v<version>
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
node scripts/verify-release.mjs --tag v<version> --dir <downloaded-assets-directory>
```
