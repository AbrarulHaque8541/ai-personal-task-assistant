package com.cue.daymark.updater;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

/** Downloads one consented GitHub release APK into the app-private cache, with strict byte bounds. */
public final class GitHubApkDownloader implements UpdaterCore.Downloader {
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final int MAX_REDIRECTS = 5;
    private final Context context;
    private final ConnectionFactory connectionFactory;

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    public GitHubApkDownloader(Context context) {
        this(context, url -> (HttpURLConnection) url.openConnection());
    }

    GitHubApkDownloader(Context context, ConnectionFactory connectionFactory) {
        if (context == null || connectionFactory == null) {
            throw new IllegalArgumentException("Context and connection factory are required.");
        }
        this.context = context.getApplicationContext();
        this.connectionFactory = connectionFactory;
    }

    @Override
    public File download(UpdaterCore.Release release) throws UpdaterCore.UpdateException {
        UpdaterCore.validateRelease(release);
        if (!UpdaterCore.isAllowedAssetUrl(release.assetUrl)) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.INVALID_METADATA,
                    "APK URL is not a Daymark GitHub release asset.");
        }
        File directory = new File(context.getCacheDir(), "daymark-update-tmp");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.DOWNLOAD,
                    "Could not create private temporary storage for the APK.");
        }
        File apk = null;
        HttpURLConnection connection = null;
        try {
            apk = File.createTempFile("verified-update-", ".apk", directory);
            URL currentUrl = new URL(release.assetUrl);
            int redirects = 0;
            while (true) {
                connection = connectionFactory.open(currentUrl);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/octet-stream");
                connection.setRequestProperty("User-Agent", "Daymark-Android-Updater");
                int status = connection.getResponseCode();
                if (isRedirect(status)) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || redirects >= MAX_REDIRECTS) {
                        throw new UpdaterCore.UpdateException(UpdaterCore.Failure.INVALID_METADATA,
                                "GitHub APK redirect chain was missing or exceeded its safe limit.");
                    }
                    URL redirect = new URL(currentUrl, location);
                    if (!UpdaterCore.isAllowedAssetRedirectUrl(redirect.toExternalForm())) {
                        throw new UpdaterCore.UpdateException(UpdaterCore.Failure.INVALID_METADATA,
                                "GitHub APK redirect target was not an approved HTTPS release-asset host.");
                    }
                    redirects++;
                    connection.disconnect();
                    connection = null;
                    currentUrl = redirect;
                    continue;
                }
                if (status == 403 || status == 429) {
                    throw new UpdaterCore.UpdateException(UpdaterCore.Failure.RATE_LIMITED,
                            "GitHub temporarily limited the APK download. Nothing was installed.");
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new UpdaterCore.UpdateException(UpdaterCore.Failure.HTTP,
                            "GitHub APK download returned HTTP " + status + ".");
                }
                long contentLength = connection.getHeaderFieldLong("Content-Length", -1L);
                if (contentLength >= 0L && contentLength != release.apkSizeBytes) {
                    throw new UpdaterCore.UpdateException(UpdaterCore.Failure.APK_MISMATCH,
                            "GitHub APK byte count did not match release metadata.");
                }
                copyExact(connection.getInputStream(), apk, release.apkSizeBytes);
                return apk;
            }
        } catch (UpdaterCore.UpdateException exception) {
            remove(apk);
            throw exception;
        } catch (UnknownHostException | SocketTimeoutException exception) {
            remove(apk);
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.OFFLINE,
                    "GitHub could not be reached. Daymark remains usable offline.", exception);
        } catch (IOException exception) {
            remove(apk);
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.DOWNLOAD,
                    "The GitHub APK download did not complete. Nothing was installed.", exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307 || status == 308;
    }

    private static void copyExact(InputStream input, File destination, long expectedBytes)
            throws IOException, UpdaterCore.UpdateException {
        long copied = 0L;
        try (InputStream stream = input; FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                copied += count;
                if (copied > expectedBytes || copied > UpdaterCore.MAX_APK_BYTES) {
                    throw new UpdaterCore.UpdateException(UpdaterCore.Failure.APK_MISMATCH,
                            "Downloaded APK exceeded the declared safe byte limit.");
                }
                output.write(buffer, 0, count);
            }
            output.flush();
        }
        if (copied != expectedBytes) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.APK_MISMATCH,
                    "Downloaded APK ended before its declared byte count.");
        }
    }

    private static void remove(File file) {
        if (file != null && file.exists()) file.delete();
    }
}
