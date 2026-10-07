# Production release signing

This document covers the production signing chain tracked by issues
[#41](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/41) (34A) and
[#40](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/issues/40) (34B).
It is the operational half of the signing configuration in
`android-app/app/build.gradle.kts`.

## Variants and what they are signed with

| Build | Signing | Where it may be published |
|---|---|---|
| `githubSideloadDebug` / `playDebug` | Android **debug** key (Gradle default) | Device-test prereleases only, labelled DEBUG |
| `githubSideloadRelease` / `playRelease` | **Production key** — or the build is refused | Production releases only |

Debug/device-test builds stay usable locally and in CI: the signing guard only inspects the
release/bundle/package task set, so `assembleDebug`, `lintGithubSideloadDebug`,
`testGithubSideloadDebugUnitTest` and friends are unaffected.

## Credentials the CI expects

Configure these as **protected repository secrets** (or, preferably, as environment secrets on a
`release` environment with required reviewers):

| Secret | Meaning |
|---|---|
| `KEYSTORE_BASE64` | The release JKS, base64-encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias inside the keystore |
| `KEY_PASSWORD` | Password for that alias |

Locally you can use the same flow through environment variables
`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`,
or through an untracked `android-app/keystore.properties`:

```properties
storeFile=/absolute/path/daymark-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

`android-app/.gitignore` already excludes `keystore.properties`, `*.jks` and `*.keystore`, and
the CI secret guard fails on any tracked file matching `.env*` / `.jks` / `.keystore` / `.pem`.

## Fail-closed contract

`android-app/app/build.gradle.kts` refuses the release task graph when the credentials are
missing:

```
Production release signing is not configured. Set RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD (or provide android-app/keystore.properties) before
building a release variant. Refusing to produce an unsigned or debug-signed production artifact.
```

Consequences, all intentional:

- There is **no fallback to the debug key**. A missing keystore can never yield something that
  looks like a production artifact.
- `android-release.yml` and the SLSA provenance workflow therefore fail closed until the
  secrets exist — that is what "no unsigned release" means in practice.
- `android.yml` verifies the guard itself on every push/PR (`--dry-run` with blanked
  credentials must fail with exactly that message), so the contract cannot silently regress.

## Creating the keystore (one time)

```bash
keytool -genkeypair -v \
  -keystore daymark-release.jks -alias daymark \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Daymark, OU=Release, O=Cue, C=IN"
base64 -w0 daymark-release.jks   # → paste into the KEYSTORE_BASE64 secret
```

Record the certificate fingerprint here once the key exists, and keep it out of git if you
prefer to treat it as sensitive:

```
SHA-256: <fill in after generating the key: keytool -list -v -keystore daymark-release.jks>
```

## Upgrade implications

- The signer of record for `com.cue.daymark` must never change after the first production
  release. Android refuses an in-place update whose signing certificate differs, so switching
  keys forces every user to uninstall (losing app data unless they exported a portable backup
  first).
- The APK Signature Scheme v2/v3 blocks are enabled (`enableV1Signing`, `enableV2Signing`,
  `enableV3Signing`), which covers API 26+ cleanly; v1 is kept for older tooling compatibility.
- The verified device-test artefacts already published under the `v1.0.0*` tags were built with
  the debug key and are explicitly **not** production artefacts.

## Rotation

1. Generate a new keystore (or new alias) and update the four secrets.
2. Re-run `android.yml` to confirm the guard and the signature check pass with the new key.
3. Only then publish. If the previous key was exposed, treat all releases signed with it as
   untrusted and re-publish from a clean checkout.
