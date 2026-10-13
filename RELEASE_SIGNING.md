# Daymark — release identity and signing contract

This document is binding for **humans and AI agents** working on this repository.

## Goal

The owner approved a fresh application identity for the new Daymark line. The next production artifact uses `com.abrar.daymark`, displayed version `1.0`, and a fresh install. Legacy `com.cue.daymark` installs remain separate and do not update in place; their local data is not migrated automatically. The production signing certificate remains pinned and must still be verified.

## Immutable identity

| Field | Value | Rule |
|-------|--------|------|
| `applicationId` | `com.abrar.daymark` | Current identity after owner-approved reset; do not change again without explicit owner approval |
| Production keystore | GitHub Actions secrets only | **Never replace** with a new JKS for "convenience" |
| Secrets | `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Must stay the **same four secrets** used for v1.0.1 / v1.0.2 |
| Production cert SHA-256 | `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580` | Must match every production APK; also pinned in `UpdaterPublisherConfig` |

If the keystore is lost or rotated without a careful migration, **all existing installs cannot update** — users must uninstall (data loss) or use portable backup restore.

## Version rules (every release)

1. Bump **`versionCode` by at least +1** (integer, always increasing).
2. Current fresh-install baseline is `versionName = 1.0`; subsequent releases must bump it.
3. Tag must be exactly `v` + `versionName` (initial tag `v1.0`; three-component versions such as `v1.0.1` are also accepted).
4. Publish only via `.github/workflows/android-production-release.yml` (tag push on `main`).
5. APK asset name: `Daymark-v{versionName}-githubSideload.apk`.

Published releases and their APK and AAB assets are listed on the repository's Releases page; old `com.cue.daymark` releases remain historical and do not become updates for `com.abrar.daymark`. The current source's `versionName`/`versionCode` live in `android-app/app/build.gradle.kts`, and a version counts as published only when its exact `v<versionName>` tag's production workflow completes successfully. Main CI validates the protected signing secrets, generates signed candidate APK/AAB artifacts, and confirms signer continuity; it does not publish a release or prove physical-device behavior. Always verify the exact tag's production workflow and attached artifacts before distribution.


## Fresh identity migration notes

- `com.abrar.daymark` installs alongside `com.cue.daymark`; Android does not treat it as an in-place update.
- Existing encrypted task data is app-private and will not appear in the new package. The owner explicitly accepted a fresh install and no automatic data migration.
- Old release history and assets are not deleted. Once the new package release becomes GitHub latest stable, the legacy updater may reject its metadata because it targets a different application ID; this is expected and fail-closed.
- Keep the same production signer pin. Reusing the signer preserves publisher verification but does not make different application IDs update-compatible.

## What caused "package conflicts" (2026-10-08)

Android error: *App not installed as package conflicts with an existing package.*

**Cause:** The phone had Daymark installed with a **different signing certificate** (typically a **debug** or ad-hoc build) than the production keystore in GitHub Secrets. Same `applicationId` + different cert = conflict. This is OS policy, not a bug in the APK.

**Fix for that device:** optional portable backup → uninstall old Daymark → install [Daymark-v1.0.2.apk](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.0.2/Daymark-v1.0.2.apk) → restore backup.

**After that clean production install:** every later release built with the **same secrets** and higher `versionCode` updates in place with **no conflict**.

## Forbidden actions (agents)

- Do **not** change `applicationId` again without explicit owner approval.
- Do **not** generate a new keystore or commit any `.jks` / `.keystore`.
- Do **not** ship a **debug**-signed APK as a production GitHub Release asset.
- Do **not** lower `versionCode`.
- Do **not** use a different signing path for "just this once".
- Do **not** enable Play flavor updater (`BuildConfig.UPDATER_ENABLED` stays false for `play`).
- Do **not** rewrite or delete existing release assets for old tags unless the owner explicitly requests it.

## Required release sequence (agents)

1. On `main`: set next `versionCode` / `versionName` in `android-app/app/build.gradle.kts`.
2. Keep `UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256` equal to the production cert above (unless the owner rotates keys in a dedicated, documented migration).
3. Merge to `main`.
4. Tag: `git tag -a v1.0 -m "Daymark 1.0" && git push origin v1.0` for the fresh baseline; future tags must equal `v` + exact `versionName`.
5. Wait for **Android production release** workflow — it signs with repo secrets and attaches `Daymark-vX.Y.Z.apk`.
6. Users update over the previous production install — data preserved.

## Debug vs production

| Build | Signature | May update production install? |
|-------|-----------|--------------------------------|
| `assembleGithubSideloadDebug` | Debug keystore | **No** — will conflict |
| `assembleGithubSideloadRelease` + CI secrets | Production keystore | **Yes** — if versionCode higher |

Local developers: use debug for day-to-day; never tell end users to sideload debug over production.

## Portable backup escape hatch

If a signing mistake ever forces uninstall, users should use **More → Encrypted backup / restore** before uninstalling, then restore after installing a production APK.
