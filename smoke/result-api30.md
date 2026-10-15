# Cold-launch smoke result (API 30 ATD, githubSideloadDebug)

- Run: 38794477535
- Head: 2dc8b8bdc665902105eb0b3ff7989b70de822da6

## Chosen APK
~~~
APK=/home/runner/work/daymark/daymark/android-app/app/build/outputs/apk/githubSideload/debug/app-githubSideload-debug.apk
~~~

## All APK files
~~~
/home/runner/work/daymark/daymark/android-app/app/build/outputs/apk/githubSideload/debug/app-githubSideload-debug.apk
~~~
## Install
~~~
Performing Streamed Install
Success
~~~

## am start -W
~~~
Starting: Intent { cmp=com.abrar.daymark/.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.abrar.daymark/.MainActivity
TotalTime: 334
WaitTime: 337
Complete
~~~

## Process alive after 25s
~~~
1614
~~~

## Verdict: LAUNCH SURVIVED (no FATAL EXCEPTION in logcat)
