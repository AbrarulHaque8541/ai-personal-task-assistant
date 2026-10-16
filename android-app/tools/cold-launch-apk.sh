#!/usr/bin/env bash
set -euo pipefail
label="${1:?usage: cold-launch-apk.sh LABEL APK_PATH}"
apk="${2:?usage: cold-launch-apk.sh LABEL APK_PATH}"
package_id="com.abrar.daymark"
activity="$package_id/$package_id.MainActivity"
if [[ ! -s "$apk" ]]; then echo "::error::$label APK is missing or empty: $apk"; exit 1; fi
echo "::group::Cold launch: $label"
echo "::notice::Installing $label"
if ! timeout 60s adb install -r "$apk"; then adb logcat -d -v threadtime | tail -n 500; exit 1; fi
adb logcat -c
timeout 15s adb shell am force-stop "$package_id" || true
echo "::notice::Starting $label MainActivity"
if ! timeout 45s adb shell am start -W -n "$activity"; then adb logcat -d -v threadtime | tail -n 500; exit 1; fi
sleep 8
pid="$(timeout 15s adb shell pidof "$package_id" | tr -d '\r' || true)"
if [[ -z "$pid" ]]; then echo "::error::$label process exited during cold launch"; adb logcat -d -v threadtime | tail -n 500; exit 1; fi
focus="$(timeout 15s adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' || true)"
if ! grep -q "$package_id" <<< "$focus"; then echo "::error::$label MainActivity is not resumed: $focus"; adb logcat -d -v threadtime | tail -n 500; exit 1; fi
startup_failure="$(adb logcat -d -v threadtime | grep -F 'DaymarkStartup: cold start failed' || true)"
if [[ -n "$startup_failure" ]]; then echo "::error::$label displayed the startup-failure screen"; echo "$startup_failure"; adb logcat -d -v threadtime | tail -n 500; exit 1; fi
crash_lines="$(adb logcat -d -v threadtime | grep -E 'FATAL EXCEPTION:|Process: com.abrar.daymark|Fatal signal' || true)"
if [[ -n "$crash_lines" ]]; then echo "::error::$label emitted crash log entries"; adb logcat -d -v threadtime | tail -n 500; exit 1; fi
echo "PASS: $label stayed alive and MainActivity remained resumed."
echo "::endgroup::"
