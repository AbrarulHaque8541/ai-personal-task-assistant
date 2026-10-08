# Agent instructions — Daymark (`ai-personal-task-assistant`)

Read **[RELEASE_SIGNING.md](RELEASE_SIGNING.md)** before any Android release, version bump, or signing change.

## Non-negotiable

1. **Same app forever:** `applicationId = com.cue.daymark`
2. **Same production keystore:** only GitHub secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
3. **Monotonic versionCode:** always increase for production releases
4. **Production APKs only via** tag `vX.Y.Z` → workflow `android-production-release.yml`
5. **Never** publish debug-signed APKs as GitHub Release assets for end users

## Why users saw package conflicts

Installing a production APK over a **debug** (or other-key) install fails with *package conflicts*. That is expected Android behavior. Solution: uninstall the non-production build (backup first), then install production `Daymark-v*.apk`. Future production→production updates will not conflict.

## Baseline after v1.0.2

- Latest production: **v1.0.2** / versionCode **3**
- Cert pin: `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`

Next release must be versionCode **≥ 4** and tag matching `versionName`.
