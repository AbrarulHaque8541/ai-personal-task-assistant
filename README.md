# Daymark

Local-first Android task assistant (`com.cue.daymark`).

## Download

| Release | APK |
|---------|-----|
| **Current production** | [v1.0.2](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.2) — [Daymark-v1.0.2.apk](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.0.2/Daymark-v1.0.2.apk) |
| Previous | [v1.0.1](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.1) |

**Same package + same production signing key + higher versionCode = in-place update (data kept).**  
Uninstalling the app deletes local encrypted data. Prefer export backup first if you must reinstall.

If install fails with *package conflicts*, the phone has Daymark signed with a **different key** (often a debug build). Uninstall that copy, then install the production APK above. See [RELEASE_SIGNING.md](RELEASE_SIGNING.md).

## Secrets (GitHub Actions)

`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — **do not rotate** without a planned migration.

## Publish next production update

1. On `main`, bump `versionCode` and `versionName` in `android-app/app/build.gradle.kts`.
2. Tag and push:

```bash
git tag -a v1.0.3 -m "Daymark 1.0.3"
git push origin v1.0.3
```

3. Workflow **Android production release** attaches `Daymark-v1.0.3.apk`.

Full rules for humans and agents: **[RELEASE_SIGNING.md](RELEASE_SIGNING.md)** · **[AGENTS.md](AGENTS.md)**

## License

MIT — see [LICENSE](LICENSE).
