# Getting started with Daymark

Daymark keeps one task list on this Android device. You do not need an account or an internet connection. Wait for **Encrypted storage ready** before adding or changing tasks.

## Add a task — Simple path

The first screen asks **“What do you want to get done?”** and shows an everyday example. Type a task in your own words, then tap **Add task** or use the keyboard's Done action. A title is required and can be up to 160 characters.

Quick add sets **no due date** and **medium priority**; the screen tells you this before you add the task. To choose a date or priority first, tap **Add with a date or priority**. Your typed text is carried into the editor and can be changed before saving. Choose a due date if useful, choose Low / Medium / High priority, then tap Add task. Cancel or Android Back closes the editor without adding anything.

## Work with tasks

Tap the checkbox to mark a task done or open again. Tap **Edit** to change its title, due date, or priority. **Delete** asks you to confirm; after confirming, tap **Undo** within seven seconds to restore the task.

## Power path

Tap **Power path** in the top bar to show **All**, **Today**, **Upcoming**, and **Completed** filters, title search, and **Demo suggestion**. It is the same task list as Simple path; switching paths does not make a copy. Today means a due date equal to today. Upcoming means after today; tasks with no date still appear in All.

Demo suggestions use a fixed local rule: open tasks by overdue/nearest due date, then High → Medium → Low priority for a matching date. They are **not AI advice** and use no online service.

Tap **Simple path** to return to the uncluttered view. Your tasks remain the same.

## More settings and accessibility

Tap **More** to change appearance (follow the device, light, or dark), app text size (Compact, Standard, Extra large), or high-contrast colors. **Permission status** checks the installed app's declared permissions; V1 reports none required and does not show a permission prompt. The Language item explains that app text is English-only; dates use the device language. Screen-reader labels are present on task controls, and Android's TalkBack can be enabled in system Accessibility settings; device-level TalkBack testing is still pending. Reduced motion has no separate app switch because this version has no looping or auto-playing animation; Android still controls system UI motion.

## Storage and limits

Tasks are encrypted with AES-GCM and stored in this app's private files using Android Keystore. They are not synced, exported, or backed up. Clearing app data or uninstalling Daymark can permanently remove them. If storage cannot be unlocked or a save fails, the app preserves the stored file and pauses edits rather than claiming an unsaved change succeeded.

This version has no voice capture or speech provider, no AI/live LLM or AssistantProvider routing, no cloud fallback, no GGUF/model download or inference, no language/voice/plugin pack, no Termux/CLI bridge, and no OTA or background update. Telegram is not connected; a hosted bot would require a backend and token and is outside the offline app. No optional content is bundled or downloaded.

## Deferred optional services

Daymark has no current cloud-model provider or Telegram integration; the task list remains local and usable offline. These are future ideas only, not on-device features: [DeepSeek Harness](https://www.deepseek.com/en/harness/) is a preview-stage desktop/web runtime used here only as a design reference; [Grok Bot](https://x.ai/news/introducing-grok-bot) is a separate hosted product announced for desktop and iOS, not a Telegram bot; and [Alibaba Cloud Model Studio](https://www.alibabacloud.com/help/en/model-studio/what-is-model-studio) offers hosted APIs that require network access and can incur token charges. Any future provider must use an `AssistantProvider` contract, be explicitly selected with no silent cloud fallback, disclose what data leaves the phone and possible cost, send minimum context, never log prompts or keys, and encrypt user-provided credentials with a Keystore-protected key. Telegram is a separate hosted bot requiring a backend and token, so it does not fit the offline/no-background V1. See [Android design notes](ANDROID_DESIGN.md) for the deferred policy.

## For developers

Build and SDK status are in [README.md](README.md). Run `sh ./tools/check-v1-source.sh` from `android-app/` for JDK-only task-logic assertions, manifest/privacy policy checks, and a source-derived palette contrast regression test. The checks do not validate rendered UI or real-device behavior. A debug APK build succeeded in the latest review and its package metadata/signature were verified, but no device was available for installation or launch. See the [acceptance checklist](V1_ACCEPTANCE.md) for exact build evidence and device tests that remain unrun.
