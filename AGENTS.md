# Humans & Agents Rules — Daymark

Read this file **before** any code, release, or question to the owner about keys.

## 0. Start here (new agent, 60 seconds)

1. **App:** Daymark — local-first Android tasks + HTTPS WebView browser. Package: `com.cue.daymark`.
2. **Repo:** https://github.com/AbrarulHaque8541/ai-personal-task-assistant
3. **Published release (verify live):** check GitHub Releases. Do not invent version numbers.
4. **Source version:** `android-app/app/build.gradle.kts` → `versionName` / `versionCode`.
5. **Signing:** production keystore is **already** in GitHub Actions secrets:
   - `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
   - **Never ask the owner for keystore files or passwords.**
   - Pin: `UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256` = `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`
6. **How to release (owner tags; agents prepare main):**
   - Land fixes on `main` via PR + green CI
   - Owner (or authorized agent) pushes tag `v` + `versionName` (example `v1.0.5`) on that commit
   - Workflow `.github/workflows/android-production-release.yml` builds signed APK/AAB, verifies signer, publishes Release with `<!-- daymark-updater-v1 {...} -->` notes
7. **Open work:** `gh pr list`, `gh issue list`, recent commits on `main`. Prefer merge/fix over duplicate features.
8. **In-app bugs from the owner:** GitHub **Issues** (prefilled from Daymark Report a bug / shake). Triage Issues before inventing new work.

## 1. Mission and operating style

Daymark is a personal, local-first Android task assistant with an embedded HTTPS browser. The owner is the primary user and is willing to test advanced/power-user features. Prefer useful, testable improvements over enterprise process for its own sake, but do not trade away data integrity, device security, or honest capability claims.

**Do the work, not just a plan:** inspect the live default branch, reproduce claims, research official docs when platform behavior matters, implement focused changes, add regression coverage, run available checks, and open a PR. Do not stop at a recommendation when repository access permits implementation.

## 2. Non-negotiable product invariants

1. Keep `applicationId = com.cue.daymark`; never change the package identity.
2. Keep the production signing certificate pinned in `UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256`:
   `ad6be60bd89c076c2d3985ec20c30533dd9ff10e07418d64ad1d825418030580`.
3. Keep the same four GitHub Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Never print, request, commit, or rotate them casually. **If a release fails, fix workflow/code — do not demand secrets from the owner.**
4. Increase `versionCode` monotonically for each production release. The tag must equal `v` + `versionName`.
5. Publish production APK/AAB assets only through `.github/workflows/android-production-release.yml` from a tag that points to a commit already on `main`.
6. Never publish debug-signed APKs as production assets. Never overwrite or delete historical release assets to hide a mistake.
7. Preserve fail-closed encrypted-storage behavior, portable backup compatibility, HTTPS-only browsing, no cleartext traffic, and explicit user consent for external/network actions.
8. Never claim a feature is implemented because it appears in `PROJECT_PLAN.md`; verify the production source and tests.

## 3. Workflow: branch, inspect, test, PR

- Start by checking current `main`, recent commits, open PRs/issues, relevant source files, workflows, and current release/tag state. Previous audit notes may be stale; verify them.
- Create a focused branch from the current `main`. **Do not commit feature work directly to `main`.**
- Keep changes scoped. Avoid bundling unrelated refactors into a feature or security fix.
- Add or update regression tests with every bug fix where practical.
- Run `sh android-app/tools/check-v1-source.sh`, `npm test`, and Android Gradle debug build/lint/unit tests when possible.
- Open a PR with user impact, root cause, behavior change, tests, gaps, risk.
- Inspect CI after opening the PR. Fix failures you introduced.
- Do not create duplicate issues/PRs; search existing items first.
- **Coordinate with other agents:** read open PRs before starting the same feature (browser Back, Reader, extensions, tasks).

## 4. User data and security

- Never delete or silently empty user task data after errors.
- Portable backup is add-only; recovery keys are one-time — never log them.
- No analytics, ad SDKs, or silent cloud sync.
- Imported page scripts are untrusted; require Trust confirmation before enable.
- No broad `addJavascriptInterface` to arbitrary pages.
- Bug reports must not embed signing secrets or recovery keys.

## 5. Feature boundaries

- WebView ≠ Chrome/Firefox. No full `.crx`, no network adblock via `shouldInterceptRequest` policy, limited userscripts.
- AI buttons are website shortcuts unless a real API integration exists.
- Personal sideload ≠ Play Store readiness (targetSdk may lag Play’s API floor).

## 6. UX standard

Phone-first, ~48dp targets, honest labels, nested screens consistent (More, extensions, Reader, backup, task editor).

## 7. Evidence labels

PASS · SOURCE-VERIFIED · PARTIALLY VERIFIED · NOT TESTED · DEVICE VERIFIED · PUBLISHED — use precisely.

## 8. Release checklist

Before asking the owner to push a production tag:

- [ ] Critical Issues/PRs addressed or explicitly deferred by owner
- [ ] `main` CI green; versionName/versionCode consistent
- [ ] Signer pin unchanged; secrets not rotated
- [ ] Release notes will include `daymark-updater-v1` metadata (workflow responsibility)
- [ ] Device QA listed as separate from CI

**Tag command (owner):** create annotated or lightweight tag `vX.Y.Z` on the intended `main` commit only.

## 9. Current baseline (update when you merge release-train work)

Verify live before acting — this snapshot can lag:

- Latest **published** release: check https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases (was **v1.0.4** at last audit).
- Source candidate often **v1.0.5 / versionCode 6** until tagged.
- Merged highlights: updater metadata (#181), Reader Mode (#182), browser/media/extensions work on main.
- **Do not tag** until the owner says other agents’ work is integrated.
- Physical-device QA is still required for install/update, TalkBack, Reader quality, shake-report, SAF.

Primary references: [README](README.md), [RELEASE_SIGNING.md](RELEASE_SIGNING.md), [PROJECT_PLAN.md](PROJECT_PLAN.md), [docs/DEEP_AUDIT_2026-10-09.md](docs/DEEP_AUDIT_2026-10-09.md) (if present).
