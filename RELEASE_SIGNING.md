# Daymark — release identity and signing contract

This document describes durable release rules plus a dated source/release snapshot. Verify the live `main` branch, GitHub Releases, and exact workflow run before acting.

## Current source and published release (checked 2026-10-16)

- Current `main` baseline at the audit uses application ID `com.abrar.daymark`, `versionName = 1.2`, `versionCode = 9`, `compileSdk = 36`, and `targetSdk = 35`.
- Latest published release is [**v1.2**](https://github.com/AbrarulHaque8541/daymark/releases/tag/v1.2). It contains one APK asset (`Daymark-v1.2-githubSideload.apk`, SHA-256 `de3e59757182992d7da5df3c47007720a813f9bed796c21e1a15dd162807bdce`); it targets API 35 and uses versionCode 9.
- [PR #310](https://github.com/AbrarulHaque8541/daymark/pull/310) proposes the next candidate `versionName = 1.2.1`, `versionCode = 10`, `targetSdk = 36`, with API-29/API-36 emulator cold-launch gates. It is not merged and CI is pending at this snapshot; no new tag or production release has been created.
- Do not infer publication from Gradle values or CI. The live Releases page is the source of truth for published assets; the current source values are in [`android-app/app/build.gradle.kts`](android-app/app/build.gradle.kts).

The canonical GitHub repository slug is now `AbrarulHaque8541/daymark`. Older documents that say the repository rename is still pending are historical and should be updated when touched.

## Immutable identity and signer

| Field | Current value | Rule |
|---|---|---|
| `applicationId` | `com.abrar.daymark` | Owner-approved identity; do not change without explicit owner approval. |
| Production keystore | GitHub Actions secrets only | Keep key material out of source and artifacts other than the signed app. |
| Signing secrets | `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Preserve the configured production secrets; never ask the owner to send keystore files or passwords. |
| Production cert SHA-256 | `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580` | Must match the signer pin in `UpdaterPublisherConfig` and every production APK. |

This repository's release and updater checks require exact production-signer continuity. Do not rotate or replace the key without a separately approved and verified migration. For the **same application ID**, a different signer can prevent an in-place Android update; for **different application IDs**, the apps are separate installs even when they use the same signer. Neither case permits an automatic transfer of app-private encrypted task data.

## Version and publication rules

1. Keep `versionCode` strictly increasing.
2. Keep `versionName` in `android-app/app/build.gradle.kts`; the production tag must be exactly `v` + that value.
3. Publish only through `.github/workflows/android-production-release.yml` on the exact version tag after all required checks pass.
4. The production workflow builds the signed APK/AAB and verifies package ID, version, target SDK 36, and signer before publication. Main Android CI is not a signed production release and does not publish assets.
5. The expected APK asset name is `Daymark-v{versionName}-githubSideload.apk`; verify the actual release assets and checksums before distribution.
6. A successful CI build does not establish Android-device behavior or upgrade/data-migration compatibility.

## Separate-install and data boundary

The current owner-approved application identity is separate from the previously published app line. Installing it does not replace that app or carry its app-private encrypted data across. The user must retain access to the earlier installation and use Daymark's explicit encrypted backup/restore flow if they choose to move tasks; no automatic package-to-package migration is implemented. Reusing the production signer preserves publisher verification, but does not make distinct application IDs update-compatible.

## Historical package-conflict note — 2026-10-08

A prior device reported Android's “App not installed as package conflicts with an existing package” error when a production-signed build was installed over a build signed with a different certificate (for example, a debug build). For the same application ID, the signing mismatch is an Android install/update constraint, not evidence that a new production key should be generated.

The original recovery note referred to backing up, uninstalling that specific older install, then installing [v1.0.2](https://github.com/AbrarulHaque8541/daymark/releases/download/v1.0.2/Daymark-v1.0.2.apk) and restoring. That is **historical, device-specific guidance**, not a general update path. Do not tell a user to uninstall as a routine fix; uninstall can remove the encrypted task file and its device-held key. Confirm a usable backup/recovery plan before any user chooses to remove an installation.

After a clean install, a later build can update in place only when it has the same application ID, the accepted signer continuity, and a higher `versionCode`. A different application ID installs alongside the earlier app and does not migrate its data automatically.

## Required production-release sequence

1. On `main`, set the intended `versionName` and a higher `versionCode` in `android-app/app/build.gradle.kts`.
2. Preserve `UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256` and the protected signing secrets.
3. Complete review and merge the release change under the owner's authority.
4. Only when the owner authorizes publication, create/push the exact `v<versionName>` tag on the intended `main` commit.
5. Wait for the production-release workflow; verify its exact commit, package/version/target-SDK/signer assertions, signed APK/AAB, checksums, and attached release assets.
6. Describe an update as in-place only for a compatible same-application-ID installation. For the current separate identity, do not promise replacement of the older app or automatic data transfer.

## Debug vs. production

| Build | Signature | Can update a production install? |
|---|---|---|
| `assembleGithubSideloadDebug` | Debug keystore | **No** — signer differs from production. |
| `assembleGithubSideloadRelease` + protected production secrets | Production keystore | **Only if** application ID matches, signer continuity is accepted, and `versionCode` is higher. |

Use debug builds for development; never present one as a production release or tell an end user to sideload it over a production installation.

## Portable backup

Before a user voluntarily removes an installation, advise them to use **More → Encrypted backup / restore** and confirm they have retained the recovery key separately. Restoring is an explicit user action; it is not an automatic migration, and an unavailable or lost device-held key cannot be replaced by changing the signer or application ID.

---
Work by: Cue
Model: not disclosed by runtime
Tooling: GitHub connector CLI, git, local source/workflow/documentation review
Timestamp (UTC): 2026-10-13T17:59:30Z
