# Automatic GitHub Issues from Daymark

## Goal

Any intentional report (button / shake) and captured crashes can become a **real GitHub Issue** in
`AbrarulHaque8541/ai-personal-task-assistant` without leaving the app when possible.

## How (secure path)

1. Owner creates a **fine-grained personal access token**:
   - Resource owner: your user
   - Repository access: **only** `ai-personal-task-assistant`
   - Permissions: **Issues → Read and write**
2. Daymark → **More → Bug report GitHub token** → paste once → Save
3. Token stays in **app-private** SharedPreferences on that phone only (not in git, not in APK source)
4. **Report a bug** / **shake** → **Create Issue now** → `POST /repos/.../issues` → Issue appears for agents
5. Crashes: stack is queued under `no_backup`; next launch flushes to Issues if token present

## Without token

Prefilled Issues form opens inside Daymark WebView; user submits once while logged into GitHub.

## Limits

- GitHub requires auth to create Issues — no magic public endpoint
- Do not put a write token in source code or CI logs
- Revoke token from GitHub if the phone is lost
