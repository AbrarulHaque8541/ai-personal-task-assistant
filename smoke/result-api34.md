# Cold-launch smoke result (API 34, githubSideloadDebug)

- Run: 38795002688
- Head: 7c28ce4768fc5895dd55fbb4ab81d4019d4bb43f

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
TotalTime: 1359
WaitTime: 1364
Complete
~~~

## Process alive after 25s
~~~
1950
~~~

## Verdict: LAUNCH SURVIVED (no FATAL EXCEPTION in logcat)
