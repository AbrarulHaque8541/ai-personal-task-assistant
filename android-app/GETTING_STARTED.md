# Getting started with Daymark

Daymark keeps task records on this Android device; task use needs no account or Internet connection. The optional Web mode does connect after you tap **Go** or a website shortcut. Wait for **Encrypted storage ready** before adding or changing tasks; Web mode does not depend on task storage.

## Add a task — Simple path

The first screen asks **“What do you want to get done?”** and shows an everyday example. Type a task in your own words, then tap **Add task** or use the keyboard's Done action. A title is required and can be up to 160 characters.

Quick add sets **no due date** and **medium priority**; the screen tells you this before you add the task. To choose a date or priority first, tap **Add with a date or priority**. Your typed text is carried into the editor and can be changed before saving. Choose a due date if useful, choose Low / Medium / High priority, then tap Add task. Cancel or Android Back closes the editor without adding anything.

## Work with tasks

Tap the checkbox to mark a task done or open again. Tap **Edit** to change its title, due date, or priority. **Delete** asks you to confirm; after confirming, tap **Undo** within seven seconds to restore the task.

## Power path

Tap **Power path** in the top bar to show **All**, **Today**, **Upcoming**, and **Completed** filters, title search, and **Demo suggestion**. It is the same task list as Simple path; switching paths does not make a copy. Today means a due date equal to today. Upcoming means after today; tasks with no date still appear in All.

Demo suggestions use a fixed local rule: open tasks by overdue/nearest due date, then High → Medium → Low priority for a matching date. They are **not AI advice** and use no online service.

Tap **Simple path** to return to the uncluttered view. Your tasks remain the same.

## Browse the web (HTTPS only)

Tap **Web** in the shared composer. DuckDuckGo is selected by default; choose Google, Bing, or Brave Search if you prefer, type a search or HTTPS address, then tap **Go**. A bare domain such as `example.com` is opened as `https://example.com`. ChatGPT, Claude, Gemini, and Perplexity are ordinary website shortcuts—not connected model APIs or page automation. Login and compatibility depend on each provider.

Daymark does not load a page until you act. The search/site provider receives the query or address you request; open pages may contact their own and third-party endpoints, which may log requests. Your task draft stays local when you switch to Web and is restored only after you switch back; Web text is never added as a task. **HTTP is currently blocked**, including typed `http://` addresses and main-frame links/redirects that downgrade to HTTP. This branch keeps Android's cleartext-traffic policy disabled; any per-site HTTP exception needs a separate explicit request.

Tap **History & data** to view the latest 50 local page URLs. **Clear history & site data** removes local URL history, WebView history, cookies, cache, form/SSL state, and WebStorage-managed data; it may sign you out. It cannot remove request logs or data kept by the sites/search providers. Local browser history is app-private but not encrypted.

## More settings and accessibility

Tap **More** to change appearance (follow the device, light, or dark), app text size (Compact, Standard, Extra large), or high-contrast colors. **Permission status** reports the normal `INTERNET` permission used by the embedded browser; task operations need no permission, and Android does not show a runtime prompt for `INTERNET`. No camera, microphone, location, or storage permissions are requested. The Language item explains that app text is English-only; dates use the device language. Screen-reader labels are present on task controls, and Android's TalkBack can be enabled in system Accessibility settings; device-level TalkBack testing is still pending. Reduced motion has no separate app switch because this version has no looping or auto-playing animation; Android still controls system UI motion.

## Storage and limits

Tasks are encrypted with AES-GCM and stored in this app's private files using Android Keystore. They are not synced, exported, or backed up. Clearing app data or uninstalling Daymark can permanently remove them. If storage cannot be unlocked or a save fails, the app preserves the stored file and pauses edits rather than claiming an unsaved change succeeded. Browser history is separate from task storage and is not encrypted.

This version has no voice capture or speech provider, no AI/live LLM or AssistantProvider routing, no cloud fallback, no GGUF/model download or inference, no language/voice/plugin pack, no Termux/CLI bridge, and no OTA or background update. Telegram is not connected; a hosted bot would require a backend and token and is outside the offline app. No optional content is bundled or downloaded.

## Deferred optional services

Daymark has no current cloud-model API provider or Telegram integration; its AI-site shortcuts are ordinary web destinations only. The task list remains local and usable offline. These are future ideas only, not on-device features: [DeepSeek Harness](https://www.deepseek.com/en/harness/) is a preview-stage desktop/web runtime used here only as a design reference; [Grok Bot](https://x.ai/news/introducing-grok-bot) is a separate hosted product announced for desktop and iOS, not a Telegram bot; and [Alibaba Cloud Model Studio](https://www.alibabacloud.com/help/en/model-studio/what-is-model-studio) offers hosted APIs that require network access and can incur token charges. Any future provider must use an `AssistantProvider` contract, be explicitly selected with no silent cloud fallback, disclose what data leaves the phone and possible cost, send minimum context, never log prompts or keys, and encrypt user-provided credentials with a Keystore-protected key. Telegram is a separate hosted bot requiring a backend and token, so it does not fit the no-background V1. See [Android design notes](ANDROID_DESIGN.md) for the deferred policy.

## For developers

Build and SDK status are in [README.md](README.md). Run `sh ./tools/check-v1-source.sh` from `android-app/` for the 25 task-logic assertions, browser address/history smoke tests, manifest/privacy/security checks, and source-derived palette contrast regression. The API 35 Java compile passed for this branch; it was compile-only—no APK was assembled/signed. No device was available for installation or launch, so real browser/network and UI behavior remain unverified. See the [acceptance checklist](V1_ACCEPTANCE.md) for device tests that remain unrun.
