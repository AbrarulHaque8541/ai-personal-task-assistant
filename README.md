# Daymark

Local-first Android task assistant (`com.cue.daymark`).

## Download

| Release | APK |
|---------|-----|
| **Current stable production** | [v1.0.2](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.2) — [Daymark-v1.0.2.apk](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.0.2/Daymark-v1.0.2.apk) |
| Previous | [v1.0.1](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.1) — [Daymark-v1.0.1.apk](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.0.1/Daymark-v1.0.1.apk) |

**Next release status:** this PR prepares source version **v1.0.5** (`versionCode = 6`); it is **not published yet**. The repository's published-download table still identifies v1.0.2 as the latest stable production release, so verify that any claimed v1.0.4 release has an actual GitHub Release tag and APK asset. Do not distribute a production update until the APK signer matches the pinned production certificate and the release verification workflow passes. Follow [RELEASE_SIGNING.md](RELEASE_SIGNING.md).

**Same package + same production signing key + higher versionCode = in-place update (data kept).**  
Uninstalling the app deletes local encrypted data. Prefer exporting a portable backup before reinstalling.

If installation fails with *package conflicts*, the installed copy is signed with a different key (often a debug build). Do not uninstall without first considering/exporting a backup. See [RELEASE_SIGNING.md](RELEASE_SIGNING.md).

## Secrets (GitHub Actions)

`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` — **do not rotate** without a planned migration.

## Publish next production update

1. Resolve the production signing blocker and confirm the signer SHA-256 matches the pinned certificate.
2. Confirm the source version and CI metadata checks agree.
3. Merge the release-ready changes into `main`.
4. Create and push the tag matching the Gradle `versionName`, for example:

```bash
git tag -a vX.Y.Z -m "Daymark X.Y.Z"
git push origin vX.Y.Z
```

5. Confirm **Android production release** succeeds and the expected APK is attached to that exact GitHub Release before announcing it.

Full rules for humans and agents: **[RELEASE_SIGNING.md](RELEASE_SIGNING.md)** · **[AGENTS.md](AGENTS.md)**

## License

MIT — see [LICENSE](LICENSE).
