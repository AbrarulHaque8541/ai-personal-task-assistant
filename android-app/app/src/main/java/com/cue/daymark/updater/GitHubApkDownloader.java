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
import java.util.concurrent.atomic.AtomicBoolean;

/** Downloads one consented GitHub release APK into the app-private cache, with strict byte bounds. */
public final class GitHubApkDownloader implements UpdaterCore.Downloader {
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final int MAX_REDIRECTS = 5;
    private static final String TEMP_DIRECTORY_NAME = "daymark-update-tmp";
    private static final String PARTIAL_PREFIX = "daymark-update-";
    private static final String PARTIAL_SUFFIX = ".partial";
    private static boolean startupCleanupComplete;
    private final Context context;
    private final ConnectionFactory connectionFactory;
    private final boolean allowMobileData;
    private final NetworkPolicy networkPolicy;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile HttpURLConnection activeConnection;

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    public interface NetworkPolicy {
        boolean isWifiConnected();
    }

    public GitHubApkDownloader(Context context, boolean allowMobileData, NetworkPolicy networkPolicy) {
        this(context, url -> (HttpURLConnection) url.openConnection(), allowMobileData, networkPolicy);
    }

    GitHubApkDownloader(Context context, ConnectionFactory connectionFactory) {
        this(context, connectionFactory, true, () -> true);
    }

    GitHubApkDownloader(Context context, ConnectionFactory connectionFactory,
            boolean allowMobileData, NetworkPolicy networkPolicy) {
        if (context == null || connectionFactory == null || networkPolicy == null) {
            throw new IllegalArgumentException("Context, connection factory, and network policy are required.");
        }
        this.context = context.getApplicationContext();
        this.connectionFactory = connectionFactory;
        this.allowMobileData = allowMobileData;
        this.networkPolicy = networkPolicy;
    }

    /** Cancels the current request/transfer; cancellation never touches a user-selected saved APK. */
    public void cancel() {
        cancelled.set(true);
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Runs once per app process and deletes only flat, strictly named updater staging files. */
    public static synchronized void cleanupPartialDownloads(Context context) {
        if (startupCleanupComplete || context == null) return;
        startupCleanupComplete = true;
        File directory = new File(context.getCacheDir(), TEMP_DIRECTORY_NAME);
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (file.isFile() && name.startsWith(PARTIAL_PREFIX) && name.endsWith(PARTIAL_SUFFIX)) {
                file.delete();
            }
        }
    }

    @Override
    public File download(UpdaterCore.Release release) throws UpdaterCore.UpdateException {
        UpdaterCore.validateRelease(release);
        ensureNetworkAllowed();
        if (!UpdaterCore.isAllowedAssetUrl(release.assetUrl)) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.INVALID_METADATA,
                    "APK URL is not a Daymark GitHub release asset.");
        }
        File directory = new File(context.getCacheDir(), TEMP_DIRECTORY_NAME);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.DOWNLOAD,
                    "Could not create private temporary storage for the APK.");
        }
        File apk = null;
        HttpURLConnection connection = null;
        try {
            apk = File.createTempFile(PARTIAL_PREFIX, PARTIAL_SUFFIX, directory);
            URL currentUrl = new URL(release.assetUrl);
            int redirects = 0;
            while (true) {
                throwIfCancelled();
                ensureNetworkAllowed();
                connection = connectionFactory.open(currentUrl);
                activeConnection = connection;
                throwIfCancelled();
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
                    activeConnection = null;
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
                copyExact(connection.getInputStream(), apk, release.apkSizeBytes,
                        allowMobileData, networkPolicy);
                return apk;
            }
        } catch (UpdaterCore.UpdateException exception) {
            remove(apk);
            throw exception;
        } catch (UnknownHostException | SocketTimeoutException exception) {
            remove(apk);
            if (cancelled.get()) throw cancelledException();
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.OFFLINE,
                    "GitHub could not be reached. Daymark remains usable offline.", exception);
        } catch (IOException exception) {
            remove(apk);
            if (cancelled.get()) throw cancelledException();
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.DOWNLOAD,
                    "The GitHub APK download did not complete. Nothing was installed.", exception);
        } finally {
            activeConnection = null;
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307 || status == 308;
    }

    private void ensureNetworkAllowed() throws UpdaterCore.UpdateException {
        if (!allowMobileData && !networkPolicy.isWifiConnected()) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.NETWORK_POLICY,
                    "Wi-Fi only was selected. Connect to Wi-Fi or explicitly allow mobile data before downloading.");
        }
    }

    private void copyExact(InputStream input, File destination, long expectedBytes,
            boolean allowMobileData, NetworkPolicy networkPolicy)
            throws IOException, UpdaterCore.UpdateException {
        long copied = 0L;
        try (InputStream stream = input; FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                throwIfCancelled();
                if (!allowMobileData && !networkPolicy.isWifiConnected()) {
                    throw new UpdaterCore.UpdateException(UpdaterCore.Failure.NETWORK_POLICY,
                            "Wi-Fi was disconnected during download. The APK download was stopped.");
                }
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
        throwIfCancelled();
    }

    private void throwIfCancelled() throws UpdaterCore.UpdateException {
        if (cancelled.get()) throw cancelledException();
    }

    private static UpdaterCore.UpdateException cancelledException() {
        return new UpdaterCore.UpdateException(UpdaterCore.Failure.CANCELLED,
                "The APK download was cancelled before verification completed.");
    }

    private static void remove(File file) {
        if (file != null && file.exists()) file.delete();
    }
}
