# Daymark — same-app update contract (DO NOT BREAK)

This document is binding for **humans and AI agents** working on this repository.

## Goal

Every production APK after the user's clean install of **v1.0.2** must install as an **in-place update**:

- same package → no "package conflicts"
- same signing certificate → Android allows upgrade
- higher `versionCode` → Android accepts the update
- app-private encrypted data stays on device

## Immutable identity

| Field | Value | Rule |
|-------|--------|------|
| `applicationId` | `com.cue.daymark` | **Never change** |
| Production keystore | GitHub Actions secrets only | **Never replace** with a new JKS for "convenience" |
| Secrets | `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Must stay the **same four secrets** used for v1.0.1 / v1.0.2 |
| Production cert SHA-256 | `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580` | Must match every production APK; also pinned in `UpdaterPublisherConfig` |

If the keystore is lost or rotated without a careful migration, **all existing installs cannot update** — users must uninstall (data loss) or use portable backup restore.

## Version rules (every release)

1. Bump **`versionCode` by at least +1** (integer, always increasing).
2. Bump **`versionName`** (e.g. `1.0.4` → `1.0.5`).
3. Tag must be exactly `v` + `versionName` (e.g. tag `v1.0.5` for `versionName 1.0.5`).
4. Publish only via `.github/workflows/android-production-release.yml` (tag push on `main`).
5. APK asset name: `Daymark-v{versionName}-githubSideload.apk`.

Latest published GitHub release: v1.0.4 / versionCode 5. The current source candidate is v1.0.5 / versionCode 6 and is not published yet. The source version is not proof that a corresponding release asset exists; verify the exact tag's production workflow and attached artifacts before distribution.

- `versionName = 1.0.5` (release candidate)
- `versionCode = 6` (release candidate)

## What caused "package conflicts" (2026-10-08)

Android error: *App not installed as package conflicts with an existing package.*

**Cause:** The phone had Daymark installed with a **different signing certificate** (typically a **debug** or ad-hoc build) than the production keystore in GitHub Secrets. Same `applicationId` + different cert = conflict. This is OS policy, not a bug in the APK.

**Fix for that device:** optional portable backup → uninstall old Daymark → install [Daymark-v1.0.2.apk](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.0.2/Daymark-v1.0.2.apk) → restore backup.

**After that clean production install:** every later release built with the **same secrets** and higher `versionCode` updates in place with **no conflict**.

## Forbidden actions (agents)

- Do **not** change `applicationId`.
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
4. Tag: `git tag -a vX.Y.Z -m "..." && git push origin vX.Y.Z` (or GitHub UI: new release tag on `main`).
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
