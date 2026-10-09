# v1.0.5 release trigger note

Source on `main` is `versionName 1.0.5` / `versionCode 6`.

To publish:

```bash
git fetch origin main
git checkout main && git pull
git tag v1.0.5
git push origin v1.0.5
```

Workflow: `.github/workflows/android-production-release.yml`

After success, delete this note or leave it historical.
