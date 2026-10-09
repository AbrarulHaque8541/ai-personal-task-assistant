package com.cue.daymark;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Process-wide crash capture. If a GitHub Issues token is saved, the next launch
 * (or same process if possible) posts a private queue file as a GitHub Issue.
 * Never embeds tokens in the APK.
 */
final class DaymarkBugReporter {
    private static final String TAG = "DaymarkBugReporter";
    private static final String PENDING_NAME = "pending_bug_report.json.txt";

    private static Thread.UncaughtExceptionHandler previous;
    private static boolean installed;

    private DaymarkBugReporter() { }

    static synchronized void install(Context context, BugReportTokenStore tokenStore) {
        if (installed) return;
        installed = true;
        final Context app = context.getApplicationContext();
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                queueCrash(app, throwable);
            } catch (Exception ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, throwable);
            else {
                Log.e(TAG, "Uncaught", throwable);
                System.exit(2);
            }
        });
    }

    static void queueCrash(Context app, Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String versionName = "?";
        int versionCode = 0;
        try {
            PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            if (info != null) {
                versionName = info.versionName == null ? "?" : info.versionName;
                versionCode = info.versionCode;
            }
        } catch (Exception ignored) { }
        String title = BugReportComposer.issueTitle("crash", versionName);
        String body = BugReportComposer.buildCrashBody(versionName, versionCode, sw.toString());
        // Simple line format: title\n then body
        String payload = title + "\n" + body;
        File file = new File(app.getNoBackupFilesDir(), PENDING_NAME);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "queue crash failed", e);
        }
    }

    /** Call on startup: if pending crash + token, POST Issue and delete queue. */
    static void flushPending(Context context, BugReportTokenStore tokenStore) {
        if (tokenStore == null || !tokenStore.hasToken()) return;
        final Context app = context.getApplicationContext();
        final File file = new File(app.getNoBackupFilesDir(), PENDING_NAME);
        if (!file.isFile() || file.length() == 0) return;
        new Thread(() -> {
            try {
                byte[] raw = readFile(file);
                String text = new String(raw, StandardCharsets.UTF_8);
                int nl = text.indexOf('\n');
                if (nl <= 0) return;
                String title = text.substring(0, nl).trim();
                String body = text.substring(nl + 1);
                BugReportApiClient.createIssue(tokenStore.getToken(), title, body);
                // noinspection ResultOfMethodCallIgnored
                file.delete();
            } catch (Exception e) {
                Log.w(TAG, "flush pending bug report failed", e);
            }
        }, "daymark-bug-flush").start();
    }

    private static byte[] readFile(File file) throws Exception {
        byte[] buf = new byte[(int) Math.min(file.length(), 200_000)];
        try (FileInputStream in = new FileInputStream(file)) {
            int n = in.read(buf);
            if (n <= 0) return new byte[0];
            if (n == buf.length) return buf;
            byte[] out = new byte[n];
            System.arraycopy(buf, 0, out, 0, n);
            return out;
        }
    }
}
