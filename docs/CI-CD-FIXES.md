# CI/CD: fix SLSA fake-artifact provenance, production signing and the publishing chain

Closes / advances: #21 (SLSA), #41 (34A signing), #40 (34B publishing), #43 (deploy scope).
Related: #34 (parent), #45 (source-check reporting).

## What was wrong

### #21 — SLSA provenance was signing fake files
`.github/workflows/generator-generic-ossf-slsa3-publish.yml` was the untouched OpenSSF
generic-generator template. Its "Build artifacts" step literally ran:

```yaml
run: |
  echo "artifact1" > artifact1
  echo "artifact2" > artifact2
```

It then generated **valid, verifiable SLSA v3 provenance** for those two text files and attached
it to every release. The repo's supply-chain story (SHA-256-pinned updater, verified APK digests,
fail-closed publisher) was therefore being "attested" for content that has nothing to do with the
app. Verifiers checking the updater's expected digest would find no matching subject.

### #41 (34A) — there was no production signing configuration at all
`android-app/app/build.gradle.kts` had no `signingConfigs` block and no `buildTypes.release`
signing. Any release build produced an unsigned artifact, and there was nothing stopping a
future pipeline from publishing it as a production APK under the real package name.

### #40 (34B) — no production publishing pipeline
The only release workflow, `android-release-assets.yml`, builds the **debug** variant
(`assembleGithubSideloadDebug`) and publishes it under a `-device-test.*` tag with an honest
"DEBUG/device-test, not production signed" label. There was **no** workflow that builds, signs,
verifies and publishes a *production* APK/AAB — so #40's "publish verified production-signed
artifacts" had no implementation.

### #43 — a deploy could publish the repo root
`wrangler.jsonc` set `assets.directory` to the repository root, and the root `.assetsignore`
missed APKs, `.github/`, `artifacts/` and repository docs. A `wrangler deploy` risked serving
binaries, CI config and docs alongside the web prototype.

## What this changes

### SLSA provenance now attests the real artifacts
`generator-generic-ossf-slsa3-publish.yml` is rewritten to:
- check out the exact ref, set up JDK 17 + the Android SDK, restore the keystore secret, and run
  `assembleGithubSideloadRelease bundleGithubSideloadRelease`;
- compute the provenance subjects with `sha256sum` over the **real** APK/AAB, failing if none
  exists — there is no `ls artifact*` placeholder left anywhere;
- pin the reusable generator to `slsa-framework/...@f7dd8c54c2067bafc12ca7a55595d5ee9b75204a`
  (v2.1.0) instead of the stale, floating `v1.4.0`;
- delete the decoded keystore in an `always()` step.

Because a release build is now refused without signing credentials, provenance can only ever
describe a properly signed artifact.

### Production signing (#41)
`android-app/app/build.gradle.kts` gains a real, fail-closed signing configuration:
- credentials from `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` /
  `RELEASE_KEY_PASSWORD` (env, fed from secrets) or an untracked `android-app/keystore.properties`;
- a `gradle.taskGraph.whenReady` guard that throws for `assemble*Release`, `bundle*Release`,
  `package*Release` when credentials are absent — **no debug-key fallback**;
- v1/v2/v3 signature schemes enabled;
- debug/device-test builds untouched.
Full runbook (secret names, keytool, fingerprint, upgrade caveats): `docs/PRODUCTION-SIGNING.md`.

### Production publishing (#40)
New `.github/workflows/android-release.yml`: tag-triggered (`vMAJOR.MINOR.PATCH`) or manual
`dry_run`.
- verifies the tag commit is on `main` and matches `vX.Y.Z`;
- fails closed if any of the four secrets is missing;
- builds the signed release APK **and** AAB, then verifies `apksigner` v2/v3, compares the signer
  against a pinned fingerprint when one is recorded, checks package id `com.cue.daymark` and that
  the tag matches the built `versionName`;
- publishes the APK, AAB and an `SHA256SUMS.txt` checksum manifest, re-verifies the checksums in
  the publish job, and confirms each asset exists via the releases API.
The pre-existing device-test workflow is left as the honest DEBUG pipeline; the tracked debug
APKs under `artifacts/` are left in place because #40 requires removing them only **after** the
replacement publishing path is verified — that removal is a manual, reviewed follow-up.

### Deploy scope (#43)
- `npm run build:web` (`tools/build-web-assets.mjs`) copies an **allowlist** of runtime files
  (`index.html`, `app.js`, `task-logic.js`, `styles.css`, plus root icon assets) into
  `public-web/`, and `wrangler.jsonc` now points `assets.directory` at `./public-web`.
- `npm run check:web-assets` (`tools/check-web-assets.mjs`) walks that package and fails if it
  contains an APK/AAB/JKS/PEM, `.github/`, `android-app/`, `artifacts/`, `tests/`, `tools/`,
  docs, credentials, or any unexpected extension.
- `.assetsignore` is expanded to enumerate everything that must never be published, as defence in
  depth for the case where `assets.directory` changes again.
- `.github/workflows/web-assets.yml` runs the build + inspection on every push/PR. It does not
  deploy or touch production configuration.

### Cross-workflow hygiene
- `android.yml` pins all actions to immutable commit SHAs, adds least-privilege `permissions:
  contents: read`, a concurrency group and a timeout, and adds a step that asserts the signing
  guard actually fires (`assembleGithubSideloadRelease --dry-run` must print the fail-closed
  message).

## How it was verified

- Every changed workflow YAML parses (Python YAML, flow-style `on:` normalised) and every job
  has `runs-on` + `steps`.
- All embedded `run:` blocks were extracted and passed through `bash -n`.
- The three new/changed JS tools run under Node: `build-web-assets.mjs` builds a package of the
  allowlisted files, and `check-web-assets.mjs` passes on it — then fails as intended when a
  dummy `evil.apk`, a `.github/` dir, or a `package.json` is planted in `public-web/`.
- SHA pins were resolved with `git ls-remote` against the upstream repos.

## Honest limitations

- **No Android SDK/emulator in this environment**, so no Gradle/Gradle-build, `apksigner` or
  device run is claimed. The signing guard is Gradle configuration, verified structurally and by
  `--dry-run` logic; the workflow's own CI runs are the first place a real build happens.
- The production signing workflow **cannot succeed until the four secrets exist**. That is the
  intended fail-closed behaviour, not a defect.
- The Cloudflare Workers package is inspected, never deployed — no live-deployment claim is made
  (matching #43's acceptance criteria).
- Publishing to a release writes to GitHub; the workflow is `dry_run`-by-default on
  `workflow_dispatch` so it can be exercised safely before a real tag.
