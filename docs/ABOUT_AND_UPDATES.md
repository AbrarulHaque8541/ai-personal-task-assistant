# About this app & updates

## User flow

1. **More → About this app** — version, package, short privacy summary, links to source and latest releases.
2. **About → Check for updates** or **More → Check for updates** — queries public GitHub `releases/latest` (sideload flavor only).
3. If a newer **signed** release is available:
   - **Download and verify** — in-app download, hash/signer checks, then user saves/opens APK (existing flow).
   - **Open APK link** — opens the GitHub `browser_download_url` so the system/WebView can start the APK download.

## Why both paths

- In-app verify is safer (signer + digest).
- Direct APK link matches the “open link and download starts” expectation.

## Requirements

- GitHub sideload build (`UPDATER_ENABLED`)
- Production publisher pin configured
- Internet permission
- Next published release should include `daymark-updater-v1` metadata so the parser accepts it
