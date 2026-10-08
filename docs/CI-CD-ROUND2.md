# CI/CD round-2 fixes

This change wires the round-2 regression suites into the pipeline and fixes the
remaining problems in the first-round signing / publishing / web-assets
workflows. It is stacked on the first-round CI/CD PR (`ci-fix/slsa-signing-publish-chain`).

## 1. Signing chain: environment-variable mismatch (release blocker)

`android-app/app/build.gradle.kts` reads the production signing credentials from
`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD`, and fails the release task graph closed when they are
absent.

`.github/workflows/android-release-assets.yml` decoded the keystore correctly but
then exported the credentials under the **old** names `KEYSTORE_PATH`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Gradle never saw
`RELEASE_STORE_FILE`, so `hasReleaseSigning` was always false and every tagged
release build was rejected with *"Production release signing is not configured"* —
even when all four repository secrets were present.

The workflow now exports the exact names the build reads. The
`check-ci-wiring.py` guard asserts the two files cannot drift apart again.

## 2. Duplicated release-notes section

The tag-path release notes printed a `### Device testing` heading twice (once
with the "installation is not performed by GitHub Actions" note and once with
"Physical device testing across target API matrix remains pending"). The
duplicate block is removed; the guard fails if it returns.

## 3. Round-2 regression suites wired into CI

Each round-2 fix shipped a host suite that was not invoked by any workflow. They
are now run by `android.yml` (named explicitly) and by the canonical aggregator
`android-app/tools/check-v1-source.sh`:

| Suite | Covers |
|---|---|
| `run-diagnostics-tests.sh` | F8/F9 — non-fatal failures are recorded, not swallowed |
| `run-web-mode-tests.sh` | F10 — task/Web mode survives Activity recreation |
| `run-text-scale-tests.sh` | F7 — device font scale honoured |
| `run-composer-draft-tests.sh` | #30 — composer draft survives recreation |
| `run-portable-staging-tests.sh` | #28 — plaintext staging swept after interruption |
| `run-activity-request-code-tests.sh` | #29 — Activity request codes stay unique |
| `run-portable-export-tests.sh` | #29 — interrupted export never looks complete |

The web prototype tests (`npm test`, which now includes the round-2 dark-mode
suite) run in a dedicated `web` job in `android.yml`.

## 4. Guard against silent de-wiring

`android-app/tools/check-ci-wiring.py` fails if any round-2 suite is missing from
either the workflow or the aggregator, if the web tests stop running, if the
signing variable names drift from `build.gradle.kts`, or if the release-notes
duplicate returns. It is invoked from `check-v1-source.sh`, so it runs in every
Android CI job.

## Merge order

This branch is stacked on `ci-fix/slsa-signing-publish-chain` (PR #55). Merge
that PR first; this one then applies cleanly to `main`.
