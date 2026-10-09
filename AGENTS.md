# Humans & Agents Rules — Daymark

Read this file **before** any code, release, or question to the owner about keys.

## 0. Start here (new agent, 60 seconds)

1. **App:** Daymark — local-first Android tasks + HTTPS WebView browser. Package: `com.cue.daymark`.
2. **Repo:** https://github.com/AbrarulHaque8541/ai-personal-task-assistant
3. **Published release:** always check [Releases](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases) / `releases/latest` — do not invent version numbers from memory.
4. **Source version:** `android-app/app/build.gradle.kts` → `versionName` / `versionCode` (must match the next tag you intend to publish).
5. **Signing secrets are already in GitHub Actions:**
   - `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
   - **Never ask the owner for keystore files or passwords.**
   - Pin: `UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256` = `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`
6. **Release sequence:** land work on `main` via PR + green CI → push tag `v` + `versionName` on that commit → workflow `.github/workflows/android-production-release.yml` builds signed APK/AAB, verifies signer, publishes Release with `<!-- daymark-updater-v1 {...} -->` notes.
7. **Open work:** live Issues/PRs first; prefer merge/fix over duplicate features.
8. After each production release, update only docs that must stay accurate (README download links if hardcoded, release handoff files). Prefer linking to **latest** release rather than freezing a version in agent rules.

## 1. Mission and operating style

Daymark is a personal, local-first Android task assistant with an embedded HTTPS browser. Prefer useful, testable improvements. **Do the work, not just a plan.**

## 2. Non-negotiable product invariants

1. `applicationId = com.cue.daymark` forever.
2. Production signer pin: `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`.
3. Same four GitHub Actions secrets; never print or casually rotate them.
4. Monotonic `versionCode`; tag = `v` + `versionName`.
5. Production APK/AAB only via `android-production-release.yml` from a tag on `main`.
6. Never publish debug-signed APKs as production assets.
7. Fail-closed encrypted storage, portable backup compatibility, HTTPS-only browsing.
8. Verify source and tests before claiming a feature is implemented.

## 3. Workflow

Branch from current `main` → focused PR → tests → CI → merge. No direct feature commits to `main` unless policy allows. Search existing Issues/PRs before opening duplicates.

## 4. Security and data

Never empty user task data on error. No analytics/ad SDKs. Imported scripts untrusted. No broad `addJavascriptInterface`. Never embed GitHub write tokens in the APK.

## 5. Feature boundaries

WebView ≠ full Chrome/Firefox extensions. AI buttons are website shortcuts unless a real API integration exists. Personal sideload ≠ Play Store readiness.

## 6. UX

Phone-first, ~48dp targets, honest labels, consistent nested screens.

## 7. Evidence labels

PASS · SOURCE-VERIFIED · PARTIALLY VERIFIED · NOT TESTED · DEVICE VERIFIED · PUBLISHED — use precisely.

## 8. Release checklist

- [ ] No release-blocking open PR/issue the owner still wants fixed first
- [ ] `main` CI green; `versionName`/`versionCode` match the tag you will push
- [ ] Signer pin unchanged
- [ ] Workflow will emit `daymark-updater-v1` metadata
- [ ] Device QA listed separately from CI

**After a successful production tag:** confirm Release assets exist; point README download section at the new latest if it uses fixed version links.

## 9. How to discover current status (do not freeze in this file)

| Question | Source of truth |
|----------|-----------------|
| Latest **published** APK | GitHub Releases / `releases/latest` |
| Source version in tree | `android-app/app/build.gradle.kts` |
| Open bugs/features | GitHub Issues |
| In-flight changes | Open PRs |
| Signing / package rules | `RELEASE_SIGNING.md` |

Do **not** maintain a long “v1.0.X not published yet” narrative in this file. Snapshot handoff docs may exist for a release train; treat them as temporary.

Primary references: [README](README.md), [RELEASE_SIGNING.md](RELEASE_SIGNING.md), [PROJECT_PLAN.md](PROJECT_PLAN.md).
