package com.cue.daymark.updater;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Host fixtures exercise the production release parser and HTTP transport without network access. */
public final class GitHubTransportSmoke {
    private static final String SIGNER = repeat('a', 64);
    private static final byte[] APK = "fixture-apk-bytes".getBytes(StandardCharsets.UTF_8);
    private static int assertions;

    private GitHubTransportSmoke() { }

    public static void main(String[] args) throws Exception {
        parserAcceptsRealisticReleaseFixture();
        parserIgnoresOnlyWellFormedUnstableReleaseFlags();
        parserRejectsMalformedOrAmbiguousMetadata();
        releaseClientConstrainsRequestAndClassifiesResponses();
        releaseClientRejectsRedirectAndOversizedBody();
        downloaderFollowsOnlyValidatedRedirectsAndCopiesExactBytes();
        downloaderEnforcesExplicitNetworkChoice();
        downloaderRejectsUntrustedRedirectsAndCleansTemporaryFiles();
        downloaderEnforcesRedirectAndBodyBounds();
        downloaderRejectsActualBodyOver100MiB();
        downloaderCancellationMidTransferCleansPartial();
        interruptedTransferIsRemovedOnRestartWithoutTouchingUserData();
        System.out.println("PASS updater transport/parser fixtures: " + assertions + " assertions");
    }

    private static void parserAcceptsRealisticReleaseFixture() throws Exception {
        UpdaterCore.Release release = GitHubReleaseClient.parseRelease(fixture(false, false,
                "{\"applicationId\":\"com.cue.daymark\",\"versionCode\":2,\"minSdkVersion\":26,"
                        + "\"signerCertificateSha256\":\"" + SIGNER + "\"}", APK.length,
                "sha256:" + sha256(APK), assetUrl(), false));
        check("v1.2.3".equals(release.tag), "release tag is parsed from GitHub JSON");
        check("1.2.3".equals(release.versionName), "version name is derived from the validated tag");
        check("Daymark 1.2.3".equals(release.name), "release name is parsed");
        check("Critical fixes and offline reliability.".equals(release.notes), "machine metadata is removed from visible notes");
        check(release.versionCode == 2L && release.minSdkVersion == 26, "typed publisher integers are parsed");
        check(release.apkSizeBytes == APK.length, "asset byte count is parsed");
        check(sha256(APK).equals(release.apkSha256), "GitHub SHA-256 digest prefix is normalized");
        check(SIGNER.equals(release.signerSha256), "publisher certificate fingerprint is parsed");
        check(assetUrl().equals(release.assetUrl), "release asset URL is parsed");
        UpdaterCore.CheckResult result = UpdaterCore.check(() -> release, UpdaterCore.APPLICATION_ID, 1L);
        check(result.status == UpdaterCore.CheckStatus.UPDATE_AVAILABLE, "parsed fixture reaches normal stable-version policy");
    }

    private static void parserIgnoresOnlyWellFormedUnstableReleaseFlags() throws Exception {
        UpdaterCore.Release draft = GitHubReleaseClient.parseRelease(
                "{\"draft\":true,\"prerelease\":false,\"tag_name\":\"v2.0.0\",\"name\":\"draft\",\"body\":\"\",\"assets\":[]}");
        UpdaterCore.Release prerelease = GitHubReleaseClient.parseRelease(
                "{\"draft\":false,\"prerelease\":true,\"tag_name\":\"v2.0.0\",\"name\":\"preview\",\"body\":\"\",\"assets\":[]}");
        check(UpdaterCore.check(() -> draft, UpdaterCore.APPLICATION_ID, 1L).status
                == UpdaterCore.CheckStatus.NO_UPDATE, "draft release is ignored after parsing actual JSON");
        check(UpdaterCore.check(() -> prerelease, UpdaterCore.APPLICATION_ID, 1L).status
                == UpdaterCore.CheckStatus.NO_UPDATE, "prerelease is ignored after parsing actual JSON");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease("{\"prerelease\":false}"),
                "missing draft flag is not silently treated as a stable release");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease("{\"draft\":false,\"prerelease\":\"false\"}"),
                "wrongly typed prerelease flag is rejected");
    }

    private static void parserRejectsMalformedOrAmbiguousMetadata() throws Exception {
        String publisher = "{\"applicationId\":\"com.cue.daymark\",\"versionCode\":2,\"minSdkVersion\":26,"
                + "\"signerCertificateSha256\":\"" + SIGNER + "\"}";
        String good = fixture(false, false, publisher, APK.length, "sha256:" + sha256(APK), assetUrl(), false);
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease(good.substring(0, good.length() - 1)), "truncated release JSON fails closed");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease(good.replace("\"draft\":false", "\"draft\":false,\"draft\":true")),
                "duplicate JSON keys are rejected");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease(fixture(false, false,
                        publisher.replace("\"versionCode\":2", "\"versionCode\":2.5"), APK.length,
                        "sha256:" + sha256(APK), assetUrl(), false)), "fractional version code is rejected");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease(fixture(false, false,
                        publisher.replace("\"versionCode\":2", "\"versionCode\":2.0"), APK.length,
                        "sha256:" + sha256(APK), assetUrl(), false)), "decimal-form version code is rejected");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease(fixture(false, false, publisher, APK.length,
                        "sha256:bad", assetUrl(), false)), "malformed digest is rejected");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease(fixture(false, false, publisher, APK.length,
                        "sha256:" + sha256(APK), "https://evil.example/Daymark.apk", false)),
                "untrusted asset URL is rejected by release validation");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease(fixture(false, false, publisher, APK.length,
                        "sha256:" + sha256(APK), assetUrl(), true)), "multiple APK assets are rejected");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease(fixture(false, false,
                        "{\"applicationId\":\"com.attacker.app\",\"versionCode\":2,\"minSdkVersion\":26,"
                                + "\"signerCertificateSha256\":\"" + SIGNER + "\"}", APK.length,
                        "sha256:" + sha256(APK), assetUrl(), false)), "wrong application ID is rejected");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease(fixture(false, false, "{broken}", APK.length,
                        "sha256:" + sha256(APK), assetUrl(), false)), "malformed publisher JSON is rejected");
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> GitHubReleaseClient.parseRelease(fixture(false, false, publisher, APK.length,
                        "sha256:" + sha256(APK), assetUrl(), false).replace("v1.2.3", "v1.2")),
                "non-semver release tag is rejected");
    }

    private static void releaseClientConstrainsRequestAndClassifiesResponses() throws Exception {
        FakeHttpConnection success = response(HttpURLConnection.HTTP_OK, fixture(false, false,
                "{\"applicationId\":\"com.cue.daymark\",\"versionCode\":2,\"minSdkVersion\":26,"
                        + "\"signerCertificateSha256\":\"" + SIGNER + "\"}", APK.length,
                "sha256:" + sha256(APK), assetUrl(), false).getBytes(StandardCharsets.UTF_8));
        List<String> requested = new ArrayList<>();
        GitHubReleaseClient client = new GitHubReleaseClient(url -> {
            requested.add(url.toExternalForm());
            return success;
        });
        UpdaterCore.Release parsed = client.fetchLatestStable();
        check(parsed != null && parsed.tag.equals("v1.2.3"), "metadata client returns parsed latest stable release");
        check(requested.equals(Arrays.asList(GitHubReleaseClient.RELEASES_ENDPOINT)), "metadata client uses only the fixed endpoint");
        check("GET".equals(success.getRequestMethod()), "metadata request uses GET");
        check(!success.getInstanceFollowRedirects(), "metadata redirects are not followed automatically");
        check("application/vnd.github+json".equals(success.getRequestProperty("Accept")), "GitHub media type is explicit");
        check("Daymark-Android-Updater".equals(success.getRequestProperty("User-Agent")), "metadata user agent is explicit");
        check(success.disconnected, "metadata connection is closed after response");

        check(new GitHubReleaseClient(url -> response(HttpURLConnection.HTTP_NOT_FOUND, new byte[0]))
                .fetchLatestStable() == null, "missing latest release is treated as no update");
        expectFailure(UpdaterCore.Failure.RATE_LIMITED,
                () -> new GitHubReleaseClient(url -> response(403, new byte[0])).fetchLatestStable(),
                "GitHub 403 rate limit is classified");
        expectFailure(UpdaterCore.Failure.RATE_LIMITED,
                () -> new GitHubReleaseClient(url -> response(429, new byte[0])).fetchLatestStable(),
                "GitHub 429 rate limit is classified");
        expectFailure(UpdaterCore.Failure.HTTP,
                () -> new GitHubReleaseClient(url -> response(500, new byte[0])).fetchLatestStable(),
                "GitHub server failures are classified");
        expectFailure(UpdaterCore.Failure.OFFLINE,
                () -> new GitHubReleaseClient(url -> { throw new UnknownHostException("fixture offline"); }).fetchLatestStable(),
                "offline transport failures are classified");
    }

    private static void releaseClientRejectsRedirectAndOversizedBody() throws Exception {
        FakeHttpConnection redirect = response(HttpURLConnection.HTTP_MOVED_TEMP, new byte[0]);
        redirect.header("Location", "https://evil.example/redirect");
        expectFailure(UpdaterCore.Failure.HTTP,
                () -> new GitHubReleaseClient(url -> redirect).fetchLatestStable(),
                "metadata redirect is rejected rather than followed");
        check(redirect.disconnected, "metadata redirect response is safely abandoned");

        byte[] oversized = new byte[1_000_001];
        FakeHttpConnection large = response(HttpURLConnection.HTTP_OK, oversized);
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> new GitHubReleaseClient(url -> large).fetchLatestStable(),
                "oversized metadata body is rejected before parsing");
        check(large.disconnected, "oversized metadata response is disconnected");

        FakeHttpConnection invalidUtf8 = response(HttpURLConnection.HTTP_OK, new byte[] { (byte) 0xc3, 0x28 });
        expectFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> new GitHubReleaseClient(url -> invalidUtf8).fetchLatestStable(),
                "malformed UTF-8 metadata is rejected rather than replacement-decoded");
        check(invalidUtf8.disconnected, "malformed UTF-8 metadata response is disconnected");
    }

    private static void downloaderFollowsOnlyValidatedRedirectsAndCopiesExactBytes() throws Exception {
        File cache = Files.createTempDirectory("daymark-updater-cache-").toFile();
        TestContext context = new TestContext(cache);
        FakeHttpConnection original = response(HttpURLConnection.HTTP_MOVED_TEMP, new byte[0]);
        original.header("Location", "https://release-assets.githubusercontent.com/download/asset?token=fixture");
        FakeHttpConnection asset = response(HttpURLConnection.HTTP_OK, APK);
        asset.header("Content-Length", Integer.toString(APK.length));
        Deque<FakeHttpConnection> sequence = new ArrayDeque<>(Arrays.asList(original, asset));
        List<String> requested = new ArrayList<>();
        GitHubApkDownloader downloader = new GitHubApkDownloader(context, url -> {
            requested.add(url.toExternalForm());
            if (sequence.isEmpty()) throw new IOException("unexpected extra connection");
            return sequence.removeFirst();
        });
        File downloaded = downloader.download(validRelease());
        check(Arrays.equals(APK, Files.readAllBytes(downloaded.toPath())), "exact APK bytes are copied to private cache");
        check(downloaded.getParentFile().getName().equals("daymark-update-tmp"), "APK temporary path is app cache scoped");
        check(requested.size() == 2 && requested.get(1).contains("release-assets.githubusercontent.com"),
                "download follows one validated GitHub asset redirect");
        check(!original.getInstanceFollowRedirects() && !asset.getInstanceFollowRedirects(),
                "automatic redirects are disabled for every download request");
        check("application/octet-stream".equals(original.getRequestProperty("Accept")), "APK request asks for binary bytes");
        check(original.disconnected && asset.disconnected, "redirect and final connections are closed");
        check(downloaded.delete(), "successful temporary APK can be removed by caller after verification");
        deleteTree(cache);
    }

    private static void downloaderRejectsUntrustedRedirectsAndCleansTemporaryFiles() throws Exception {
        File cache = Files.createTempDirectory("daymark-updater-cache-").toFile();
        FakeHttpConnection original = response(HttpURLConnection.HTTP_MOVED_TEMP, new byte[0]);
        original.header("Location", "https://evil.example/Daymark.apk");
        expectDownloadFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> new GitHubApkDownloader(new TestContext(cache), url -> original).download(validRelease()),
                "untrusted APK redirect is rejected");
        check(original.disconnected, "untrusted redirect connection is closed");
        check(listFiles(new File(cache, "daymark-update-tmp")).isEmpty(), "failed redirect leaves no temporary APK");
        deleteTree(cache);
    }

    private static void downloaderEnforcesRedirectAndBodyBounds() throws Exception {
        File cache = Files.createTempDirectory("daymark-updater-cache-").toFile();
        Deque<FakeHttpConnection> tooMany = new ArrayDeque<>();
        for (int index = 0; index < 6; index++) {
            FakeHttpConnection redirect = response(HttpURLConnection.HTTP_MOVED_TEMP, new byte[0]);
            redirect.header("Location", "https://release-assets.githubusercontent.com/download/" + index + "?x=y");
            tooMany.add(redirect);
        }
        expectDownloadFailure(UpdaterCore.Failure.INVALID_METADATA,
                () -> new GitHubApkDownloader(new TestContext(cache), url -> {
                    if (tooMany.isEmpty()) throw new IOException("redirect limit not enforced");
                    return tooMany.removeFirst();
                }).download(validRelease()), "redirect chain beyond the documented limit is rejected");
        check(listFiles(new File(cache, "daymark-update-tmp")).isEmpty(), "redirect-limit failure removes partial file");

        FakeHttpConnection wrongLength = response(HttpURLConnection.HTTP_OK, APK);
        wrongLength.header("Content-Length", Integer.toString(APK.length + 1));
        expectDownloadFailure(UpdaterCore.Failure.APK_MISMATCH,
                () -> new GitHubApkDownloader(new TestContext(cache), url -> wrongLength).download(validRelease()),
                "Content-Length mismatch is rejected before writing the body");

        FakeHttpConnection shortBody = response(HttpURLConnection.HTTP_OK, Arrays.copyOf(APK, APK.length - 1));
        expectDownloadFailure(UpdaterCore.Failure.APK_MISMATCH,
                () -> new GitHubApkDownloader(new TestContext(cache), url -> shortBody).download(validRelease()),
                "truncated body is rejected");

        byte[] longBody = Arrays.copyOf(APK, APK.length + 1);
        FakeHttpConnection longResponse = response(HttpURLConnection.HTTP_OK, longBody);
        expectDownloadFailure(UpdaterCore.Failure.APK_MISMATCH,
                () -> new GitHubApkDownloader(new TestContext(cache), url -> longResponse).download(validRelease()),
                "body exceeding declared size is rejected");
        check(listFiles(new File(cache, "daymark-update-tmp")).isEmpty(), "size failures remove all temporary APK files");
        deleteTree(cache);
    }

    private static void downloaderRejectsActualBodyOver100MiB() throws Exception {
        File cache = Files.createTempDirectory("daymark-updater-cache-").toFile();
        UpdaterCore.Release maxRelease = new UpdaterCore.Release("v1.2.3", "1.2.3", "Daymark 1.2.3", "Notes",
                UpdaterCore.APPLICATION_ID, 2L, 26, UpdaterCore.MAX_APK_BYTES, sha256(APK), SIGNER,
                assetUrl(), false, false);
        FakeHttpConnection oversized = response(HttpURLConnection.HTTP_OK,
                new SizedInputStream(UpdaterCore.MAX_APK_BYTES + 1L, null, false));
        expectDownloadFailure(UpdaterCore.Failure.APK_MISMATCH,
                () -> new GitHubApkDownloader(new TestContext(cache), url -> oversized).download(maxRelease),
                "an actual streamed body above the 100 MiB ceiling is rejected");
        check(listFiles(new File(cache, "daymark-update-tmp")).isEmpty(),
                "actual 100 MiB ceiling rejection deletes the staging file");
        deleteTree(cache);
    }

    private static void downloaderCancellationMidTransferCleansPartial() throws Exception {
        File cache = Files.createTempDirectory("daymark-updater-cache-").toFile();
        GitHubApkDownloader[] downloaderRef = new GitHubApkDownloader[1];
        InputStream body = new SizedInputStream(APK.length, () -> downloaderRef[0].cancel(), false);
        FakeHttpConnection response = response(HttpURLConnection.HTTP_OK, body);
        response.header("Content-Length", Integer.toString(APK.length));
        downloaderRef[0] = new GitHubApkDownloader(new TestContext(cache), url -> response);
        expectDownloadFailure(UpdaterCore.Failure.CANCELLED,
                () -> downloaderRef[0].download(validRelease()),
                "cancellation after transfer bytes begin stops before verification");
        check(response.disconnected, "cancelled transfer disconnects its active HTTP connection");
        check(listFiles(new File(cache, "daymark-update-tmp")).isEmpty(),
                "mid-transfer cancellation removes the partial APK");
        deleteTree(cache);
    }

    private static void interruptedTransferIsRemovedOnRestartWithoutTouchingUserData() throws Exception {
        File cache = Files.createTempDirectory("daymark-updater-cache-").toFile();
        TestContext context = new TestContext(cache);
        FakeHttpConnection response = response(HttpURLConnection.HTTP_OK,
                new SizedInputStream(APK.length, null, true));
        response.header("Content-Length", Integer.toString(APK.length));
        try {
            new GitHubApkDownloader(context, url -> response).download(validRelease());
            throw new AssertionError("simulated process interruption did not occur");
        } catch (SimulatedProcessDeath expected) {
            assertions++;
        }
        File tempDirectory = new File(cache, "daymark-update-tmp");
        List<File> staging = partialFiles(tempDirectory);
        check(staging.size() == 1 && staging.get(0).length() > 0,
                "simulated process death leaves one unverified partial staging file");

        File savedApk = new File(cache, "Daymark-v1.2.3.apk");
        File taskData = new File(cache, "encrypted-tasks.data");
        File verifiedTemp = new File(tempDirectory, "daymark-update-preserved.verified.apk");
        Files.write(savedApk.toPath(), APK);
        Files.write(taskData.toPath(), new byte[] { 1, 2, 3 });
        Files.write(verifiedTemp.toPath(), APK);

        GitHubApkDownloader.cleanupPartialDownloads(context);
        check(partialFiles(tempDirectory).isEmpty(), "startup cleanup removes only leftover partial APKs");
        check(savedApk.isFile(), "startup cleanup leaves a user-selected saved APK untouched");
        check(taskData.isFile(), "startup cleanup leaves task data untouched");
        check(verifiedTemp.isFile(), "startup cleanup leaves a verified cache artifact untouched");
        deleteTree(cache);
    }

    private static void downloaderEnforcesExplicitNetworkChoice() throws Exception {
        File cache = Files.createTempDirectory("daymark-updater-cache-").toFile();
        AtomicInteger blockedRequests = new AtomicInteger();
        GitHubApkDownloader wifiOnly = new GitHubApkDownloader(new TestContext(cache), url -> {
            blockedRequests.incrementAndGet();
            return response(HttpURLConnection.HTTP_OK, APK);
        }, false, () -> false);
        expectDownloadFailure(UpdaterCore.Failure.NETWORK_POLICY, () -> wifiOnly.download(validRelease()),
                "Wi-Fi-only selection blocks non-Wi-Fi before the HTTP request");
        check(blockedRequests.get() == 0, "Wi-Fi-only policy makes no request over mobile data");

        FakeHttpConnection mobileResponse = response(HttpURLConnection.HTTP_OK, APK);
        mobileResponse.header("Content-Length", Integer.toString(APK.length));
        AtomicInteger allowedRequests = new AtomicInteger();
        GitHubApkDownloader mobileAllowed = new GitHubApkDownloader(new TestContext(cache), url -> {
            allowedRequests.incrementAndGet();
            return mobileResponse;
        }, true, () -> false);
        File downloaded = mobileAllowed.download(validRelease());
        check(allowedRequests.get() == 1, "explicit mobile-data choice permits the bounded APK request");
        check(Arrays.equals(APK, Files.readAllBytes(downloaded.toPath())), "mobile-data download is still exact-byte bounded");
        check(downloaded.delete(), "test removes the mobile-data fixture APK");
        deleteTree(cache);
    }

    private static UpdaterCore.Release validRelease() {
        return new UpdaterCore.Release("v1.2.3", "1.2.3", "Daymark 1.2.3", "Notes",
                UpdaterCore.APPLICATION_ID, 2L, 26, APK.length, sha256(APK), SIGNER,
                assetUrl(), false, false);
    }

    private static String fixture(boolean draft, boolean prerelease, String publisherJson,
            int size, String digest, String asset, boolean duplicateApk) {
        String notes = "<!-- daymark-updater-v1\n" + publisherJson + "\n-->\nCritical fixes and offline reliability.";
        String assetEntry = "{\"name\":\"Daymark-v1.2.3.apk\",\"size\":" + size
                + ",\"digest\":" + quote(digest) + ",\"browser_download_url\":" + quote(asset) + "}";
        String assets = duplicateApk ? "[" + assetEntry + "," + assetEntry + "]" : "[" + assetEntry + "]";
        return "{\"draft\":" + draft + ",\"prerelease\":" + prerelease
                + ",\"tag_name\":\"v1.2.3\",\"name\":\"Daymark 1.2.3\",\"body\":"
                + quote(notes) + ",\"assets\":" + assets + "}";
    }

    private static String assetUrl() {
        return "https://github.com/AbrarulHaque8541/ai-personal-task-assistant/releases/download/v1.2.3/Daymark-v1.2.3.apk";
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default: result.append(current);
            }
        }
        return result.append('"').toString();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : hash) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static FakeHttpConnection response(int status, byte[] body) {
        try {
            return new FakeHttpConnection(new URL("https://fixture.invalid/"), status, body);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static FakeHttpConnection response(int status, InputStream body) {
        try {
            return new FakeHttpConnection(new URL("https://fixture.invalid/"), status, body);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<File> listFiles(File directory) {
        File[] files = directory.listFiles();
        return files == null ? new ArrayList<>() : Arrays.asList(files);
    }

    private static List<File> partialFiles(File directory) {
        List<File> partials = new ArrayList<>();
        for (File file : listFiles(directory)) {
            if (file.isFile() && file.getName().startsWith("daymark-update-")
                    && file.getName().endsWith(".partial")) partials.add(file);
        }
        return partials;
    }

    private static void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private interface CheckedOperation { void run() throws Exception; }

    private static void expectFailure(UpdaterCore.Failure failure, CheckedOperation operation, String message)
            throws Exception {
        assertions++;
        try {
            operation.run();
            throw new AssertionError(message + " (operation unexpectedly succeeded)");
        } catch (UpdaterCore.UpdateException exception) {
            if (exception.failure != failure) throw new AssertionError(message + " (expected " + failure + ", got " + exception.failure + ")");
        }
    }

    private static void expectDownloadFailure(UpdaterCore.Failure failure, CheckedOperation operation,
            String message) throws Exception {
        expectFailure(failure, operation, message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class TestContext extends Context {
        private final File cache;
        TestContext(File cache) { this.cache = cache; }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getCacheDir() { return cache; }
    }

    private static final class FakeHttpConnection extends HttpURLConnection {
        private final int status;
        private final InputStream body;
        private final Map<String, String> headers = new HashMap<>();
        private boolean disconnected;

        FakeHttpConnection(URL url, int status, byte[] body) {
            this(url, status, new ByteArrayInputStream(body));
        }

        FakeHttpConnection(URL url, int status, InputStream body) {
            super(url);
            this.status = status;
            this.body = body;
        }

        void header(String name, String value) { headers.put(name.toLowerCase(java.util.Locale.ROOT), value); }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { connected = true; }
        @Override public int getResponseCode() { return status; }
        @Override public String getHeaderField(String name) {
            return headers.get(name.toLowerCase(java.util.Locale.ROOT));
        }
        @Override public java.io.InputStream getInputStream() throws IOException {
            if (status >= 400) throw new IOException("HTTP error");
            return body;
        }
    }

    private static final class SizedInputStream extends InputStream {
        private long remaining;
        private final Runnable afterFirstRead;
        private final boolean simulateProcessDeath;
        private boolean firstRead = true;

        SizedInputStream(long byteCount, Runnable afterFirstRead, boolean simulateProcessDeath) {
            this.remaining = byteCount;
            this.afterFirstRead = afterFirstRead;
            this.simulateProcessDeath = simulateProcessDeath;
        }

        @Override public int read() throws IOException {
            byte[] single = new byte[1];
            int count = read(single, 0, 1);
            return count == -1 ? -1 : single[0] & 0xff;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            if (!firstRead && simulateProcessDeath) throw new SimulatedProcessDeath();
            if (remaining <= 0L) return -1;
            int count = (int) Math.min(Math.min((long) length, 16L * 1024L), remaining);
            Arrays.fill(buffer, offset, offset + count, (byte) 0x5a);
            remaining -= count;
            if (firstRead) {
                firstRead = false;
                if (afterFirstRead != null) afterFirstRead.run();
            }
            return count;
        }
    }

    private static final class SimulatedProcessDeath extends Error { }
}
