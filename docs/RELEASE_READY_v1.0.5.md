# Release-ready notes — v1.0.5 (hold tag)

Owner will tag when **all** agent work is reviewed. Do not push `v1.0.5` early.

## Baseline

- Published: **v1.0.4** (same production signer as pin)
- Source: **1.0.5 / versionCode 6**
- Merged: #181 (release metadata + trust UX), #182 (Reader Mode)
- Open follow-ups: wire Report-a-bug + shared SmartTaskDraft (this branch), plus any other agent PRs

## When owner says “release”

1. Merge remaining good PRs; close duplicates.
2. Confirm `main` CI green.
3. Tag **`v1.0.5`** on that commit only.
4. Confirm production workflow published APK/AAB + `daymark-updater-v1` notes.
5. Install as update over v1.0.4 (same signer).

## Secrets

Do not rotate `KEYSTORE_*`. v1.0.1–v1.0.4 already share the pinned cert.
