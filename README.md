# Daymark

Local-first Android task assistant (`com.cue.daymark`).

## Download (same app — data preserved on update)

| Release | APK |
|---------|-----|
| **Current stable** | [v1.0.1](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/tag/v1.0.1) — [Daymark-v1.0.1.apk](https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.0.1/Daymark-v1.0.1.apk) |
| **Next same-app update** | Tag `v1.0.2` on `main` → Actions builds signed APK with the **same keystore secrets** (versionCode 3). Install over 1.0.1 to keep local data. |

Package ID and production signing identity must stay the same across updates. Uninstalling wipes local encrypted data.

## Secrets (already in GitHub)

Release builds use: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Publish next version

```bash
# after main has versionName 1.0.2 / versionCode 3
git tag -a v1.0.2 -m "Daymark 1.0.2 same-app update"
git push origin v1.0.2
```

Workflow: `.github/workflows/android-production-release.yml`

## License

MIT — see [LICENSE](LICENSE).
