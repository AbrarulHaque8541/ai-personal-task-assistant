package com.cue.daymark.updater;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
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
        productionUpdaterAcceptsExactly100MiBAndRejectsOneByteOver();
        downloaderCancellationMidTransferCleansPartial();
        interruptedTransferIsRemovedOnRestartWithoutTouchingUserData();
        promotedCandidateIsRecoveredAndRevalidatedAfterRestart();
        pickerSaveTransactionSurvivesRecreationBeforeResult();
        safSaveFinalizesAfterIndependentReadBack();
        interruptedSafCopyLeavesClearlyMarkedPartialDocument();
        interruptedSafCopyIoFailureCleansOnlyCreatedDocument();
        safProviderWithoutRenameDoesNotWriteFinalDocument();
        safFinalizationCollisionPreservesUnrelatedDocument();
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
        check(Arrays.equals(APK, Files.readAllBytes(downloaded.toPath())), "exact APK bytes are copied to persistent private updater storage");
        check(downloaded.getParentFile().getName().equals("daymark-update-tmp"), "APK temporary path is scoped to persistent no-backup app storage");
        check(!new File(context.getCacheDir(), "daymark-update-tmp").exists(),
                "updater staging does not use Android's evictable cache directory");
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

    private static void productionUpdaterAcceptsExactly100MiBAndRejectsOneByteOver() throws Exception {
        File cache = Files.createTempDirectory("daymark-updater-cache-").toFile();
        String maxDigest = sha256RepeatedByte(UpdaterCore.MAX_APK_BYTES, (byte) 0x5a);
        UpdaterCore.Release maxRelease = new UpdaterCore.Release("v1.2.3", "1.2.3", "Daymark 1.2.3", "Notes",
                UpdaterCore.APPLICATION_ID, 2L, 26, UpdaterCore.MAX_APK_BYTES, maxDigest, SIGNER,
                assetUrl(), false, false);

        FakeHttpConnection exact = response(HttpURLConnection.HTTP_OK,
                new SizedInputStream(UpdaterCore.MAX_APK_BYTES, null, false));
        exact.header("Content-Length", Long.toString(UpdaterCore.MAX_APK_BYTES));
        UpdaterCore.VerificationResult accepted = UpdaterCore.downloadAndVerify(
                maxRelease, UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                release -> true,
                new GitHubApkDownloader(new TestContext(cache), url -> exact),
                apk -> new UpdaterCore.ApkIdentity(UpdaterCore.APPLICATION_ID, "1.2.3", 2L, 26, SIGNER));
        check(accepted.status == UpdaterCore.VerificationStatus.VERIFIED,
                "production downloader and core accept exactly the 100 MiB ceiling");
        check(accepted.verifiedApk.length() == 104_857_600L,
                "accepted production artifact is exactly 104,857,600 bytes");
        check(accepted.verifiedApk.getName().equals(UpdaterCore.verifiedArtifactFileName(maxDigest)),
                "accepted production artifact is promoted to its digest-addressed verified name");
        check(accepted.verifiedApk.delete(), "host test removes accepted exact-limit artifact");

        FakeHttpConnection oversized = response(HttpURLConnection.HTTP_OK,
                new SizedInputStream(UpdaterCore.MAX_APK_BYTES + 1L, null, false));
        oversized.header("Content-Length", Long.toString(UpdaterCore.MAX_APK_BYTES));
        expectDownloadFailure(UpdaterCore.Failure.APK_MISMATCH,
                () -> UpdaterCore.downloadAndVerify(maxRelease, UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER,
                        release -> true,
                        new GitHubApkDownloader(new TestContext(cache), url -> oversized),
                        apk -> { throw new AssertionError("APK verifier must not run after streamed size rejection"); }),
                "production downloader and core reject an actual streamed body one byte over the 100 MiB ceiling");
        check(listFiles(new File(cache, "daymark-update-tmp")).isEmpty(),
                "actual 100 MiB plus one rejection removes staging and leaves no verified APK");
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
        File filesDirectory = context.getNoBackupFilesDir();
        UpdaterRecoveryStore recoveryStore = new UpdaterRecoveryStore(filesDirectory);
        recoveryStore.recordPending(validRelease());
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
        Files.write(savedApk.toPath(), APK);
        Files.write(taskData.toPath(), new byte[] { 1, 2, 3 });

        GitHubApkDownloader.cleanupPartialDownloads(context);
        check(partialFiles(tempDirectory).isEmpty(), "startup cleanup removes only leftover partial APKs");
        check(savedApk.isFile(), "startup cleanup leaves a user-selected saved APK untouched");
        check(taskData.isFile(), "startup cleanup leaves task data untouched");
        UpdaterRecoveryStore restartedStore = new UpdaterRecoveryStore(filesDirectory);
        UpdaterRecoveryStore.PendingUpdate pending = restartedStore.readPending();
        check(pending != null && !pending.verifiedApk.exists(),
                "an interrupted pre-verification transfer retains metadata but no fabricated verified artifact");
        check(restartedStore.hasPendingRecord(), "startup cleanup preserves the pending record while removing only partial downloads");
        deleteTree(cache);
    }

    private static void promotedCandidateIsRecoveredAndRevalidatedAfterRestart() throws Exception {
        File cache = Files.createTempDirectory("daymark-updater-restart-").toFile();
        TestContext context = new TestContext(cache);
        File filesDirectory = context.getNoBackupFilesDir();
        UpdaterCore.Release release = validRelease();
        UpdaterRecoveryStore beforeTransfer = new UpdaterRecoveryStore(filesDirectory);
        beforeTransfer.recordPending(release);

        FakeHttpConnection response = response(HttpURLConnection.HTTP_OK, APK);
        response.header("Content-Length", Integer.toString(APK.length));
        AtomicInteger verifierCalls = new AtomicInteger();
        UpdaterCore.ApkVerifier verifier = apk -> {
            verifierCalls.incrementAndGet();
            return new UpdaterCore.ApkIdentity(UpdaterCore.APPLICATION_ID, release.versionName,
                    release.versionCode, release.minSdkVersion, SIGNER);
        };
        UpdaterCore.VerificationResult downloaded = UpdaterCore.downloadAndVerify(release,
                UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER, ignored -> true,
                new GitHubApkDownloader(context, url -> response), verifier);
        File promoted = downloaded.verifiedApk;
        check(downloaded.status == UpdaterCore.VerificationStatus.VERIFIED,
                "production downloader and verifier complete the consented update path");
        check(promoted.isFile() && promoted.getName().equals(UpdaterCore.verifiedArtifactFileName(release.apkSha256)),
                "the production verification path promotes the candidate to its digest-addressed private artifact");
        check(Arrays.equals(APK, Files.readAllBytes(promoted.toPath())),
                "the promoted artifact contains the bytes returned by the production downloader");
        check(verifierCalls.get() == 1, "initial promotion runs the APK identity verifier once");

        UpdaterRecoveryStore afterRestart = new UpdaterRecoveryStore(filesDirectory);
        UpdaterRecoveryStore.PendingUpdate recovered = afterRestart.readPending();
        check(recovered != null && recovered.verifiedApk.getCanonicalFile().equals(promoted.getCanonicalFile()),
                "a recreated recovery-store object resolves the actual promoted candidate");
        check(recovered.release.apkSha256.equals(release.apkSha256)
                        && recovered.release.versionCode == release.versionCode
                        && recovered.release.signerSha256.equals(release.signerSha256),
                "restart recovery reloads the stored digest, version, and signer expectations");
        UpdaterCore.verifyDownloadedArtifact(recovered.release, recovered.verifiedApk,
                UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER, verifier);
        check(verifierCalls.get() == 2,
                "the recovered promoted candidate is rechecked through production digest and APK-identity validation");

        Files.write(recovered.verifiedApk.toPath(), Arrays.copyOf(APK, APK.length + 1));
        expectFailure(UpdaterCore.Failure.APK_MISMATCH,
                () -> UpdaterCore.verifyDownloadedArtifact(recovered.release, recovered.verifiedApk,
                        UpdaterCore.APPLICATION_ID, 1L, 35, SIGNER, SIGNER, verifier),
                "restart revalidation rejects a modified promoted candidate against persisted expectations");
        deleteTree(cache);
    }

    private static void interruptedSafCopyLeavesClearlyMarkedPartialDocument() throws Exception {
        File directory = Files.createTempDirectory("daymark-saf-interruption-").toFile();
        File source = new File(directory, "verified-source.apk");
        Files.write(source.toPath(), APK);
        UpdaterCore.Release release = validRelease();
        PendingSaveTransaction transaction = new PendingSaveTransaction(release, repeat('e', 32));
        File chosenDocument = new File(directory, transaction.pickerTitle);
        check(chosenDocument.createNewFile(), "provider creates a unique temporary-title picker document");
        File finalDocument = new File(directory, transaction.preferredFinalName);
        File unrelated = new File(directory, "unrelated.txt");
        byte[] unrelatedBytes = "preserve-me".getBytes(StandardCharsets.UTF_8);
        Files.write(unrelated.toPath(), unrelatedBytes);
        FakeSafDocument document = new FakeSafDocument(chosenDocument, true, false, true);
        try {
            SafApkSaver.copyVerifiedApk(source, release, document, transaction.token,
                    transaction.pickerTitle, transaction.preferredFinalName);
            throw new AssertionError("simulated process interruption did not occur during SAF copy");
        } catch (SimulatedProcessDeath expected) {
            assertions++;
        }
        check(document.displayName().contains(".daymark-incomplete-" + transaction.token),
                "process death during copy leaves a conspicuously marked staging name");
        check(document.file().isFile() && document.file().length() > 0
                        && document.file().length() < APK.length,
                "the interrupted provider document contains identifiable partial bytes");
        check(!finalDocument.exists(), "no final-looking APK name exists before complete verification");
        check(Arrays.equals(unrelatedBytes, Files.readAllBytes(unrelated.toPath())),
                "interrupted staging never changes an unrelated document");
        deleteTree(directory);
    }

    private static void interruptedSafCopyIoFailureCleansOnlyCreatedDocument() throws Exception {
        File directory = Files.createTempDirectory("daymark-saf-io-interruption-").toFile();
        File source = new File(directory, "verified-source.apk");
        Files.write(source.toPath(), APK);
        UpdaterCore.Release release = validRelease();
        PendingSaveTransaction transaction = new PendingSaveTransaction(release, repeat('a', 32));
        File chosenDocument = new File(directory, transaction.pickerTitle);
        check(chosenDocument.createNewFile(), "picker creates a fresh temporary document before interrupted copy");
        byte[] unrelatedBytes = "unrelated-must-survive".getBytes(StandardCharsets.UTF_8);
        File unrelated = new File(directory, "unrelated.txt");
        Files.write(unrelated.toPath(), unrelatedBytes);
        FakeSafDocument document = new FakeSafDocument(chosenDocument, false, false, true, true);
        assertions++;
        try {
            SafApkSaver.copyVerifiedApk(source, release, document, transaction.token,
                    transaction.pickerTitle, transaction.preferredFinalName);
            throw new AssertionError("simulated destination I/O interruption unexpectedly succeeded");
        } catch (IOException expected) {
            // Ordinary I/O failure attempts best-effort deletion of only the URI-backed fixture.
        }
        check(!document.file().exists(), "ordinary copy interruption best-effort deletes its app-created partial document");
        check(Arrays.equals(unrelatedBytes, Files.readAllBytes(unrelated.toPath())),
                "ordinary copy interruption leaves an unrelated document byte-for-byte unchanged");
        deleteTree(directory);
    }

    private static void safProviderWithoutRenameDoesNotWriteFinalDocument() throws Exception {
        File directory = Files.createTempDirectory("daymark-saf-no-rename-").toFile();
        File source = new File(directory, "verified-source.apk");
        Files.write(source.toPath(), APK);
        UpdaterCore.Release release = validRelease();
        PendingSaveTransaction transaction = new PendingSaveTransaction(release, repeat('f', 32));
        File chosenDocument = new File(directory, transaction.pickerTitle);
        check(chosenDocument.createNewFile(), "picker creates a new destination for a provider without rename support");
        FakeSafDocument document = new FakeSafDocument(chosenDocument, false, false, false);
        assertions++;
        try {
            SafApkSaver.copyVerifiedApk(source, release, document, transaction.token,
                    transaction.pickerTitle, transaction.preferredFinalName);
            throw new AssertionError("provider without rename support unexpectedly accepted a copy");
        } catch (IOException expected) {
            // A provider that cannot mark the document must not receive partial APK bytes.
        }
        check(!document.writeOpened(), "provider without rename support receives no APK bytes");
        check(!chosenDocument.exists(), "the empty app-created picker document is removed best-effort on safe abort");
        deleteTree(directory);
    }

    private static void safFinalizationCollisionPreservesUnrelatedDocument() throws Exception {
        File directory = Files.createTempDirectory("daymark-saf-collision-").toFile();
        File source = new File(directory, "verified-source.apk");
        Files.write(source.toPath(), APK);
        UpdaterCore.Release release = validRelease();
        PendingSaveTransaction transaction = new PendingSaveTransaction(release, repeat('b', 32));
        File chosenDocument = new File(directory, transaction.pickerTitle);
        check(chosenDocument.createNewFile(), "picker creates a fresh destination before copy");
        FakeSafDocument document = new FakeSafDocument(chosenDocument, false, true, true);
        byte[] unrelatedBytes = "unrelated-existing-document".getBytes(StandardCharsets.UTF_8);
        assertions++;
        try {
            SafApkSaver.copyVerifiedApk(source, release, document, transaction.token,
                    transaction.pickerTitle, transaction.preferredFinalName);
            throw new AssertionError("finalization collision unexpectedly succeeded");
        } catch (IOException expected) {
            // The provider refuses the final rename instead of replacing the document that appeared there.
        }
        File unrelated = new File(directory, transaction.preferredFinalName);
        check(unrelated.isFile() && Arrays.equals(unrelatedBytes, Files.readAllBytes(unrelated.toPath())),
                "finalization does not overwrite an unrelated document that occupies the requested name");
        check(document.file().isFile() && document.displayName().contains(".daymark-incomplete-" + transaction.token),
                "failed provider finalization leaves the verified candidate identifiable under its staging name");
        deleteTree(directory);
    }

    private static void pickerSaveTransactionSurvivesRecreationBeforeResult() throws Exception {
        UpdaterCore.Release release = validRelease();
        PendingSaveTransaction beforeRotation = new PendingSaveTransaction(release, repeat('c', 32));
        ByteArrayOutputStream stateBytes = new ByteArrayOutputStream();
        try (ObjectOutputStream state = new ObjectOutputStream(stateBytes)) {
            state.writeObject(beforeRotation);
        }
        PendingSaveTransaction afterRecreation;
        try (ObjectInputStream state = new ObjectInputStream(
                new ByteArrayInputStream(stateBytes.toByteArray()))) {
            afterRecreation = (PendingSaveTransaction) state.readObject();
        }
        check(afterRecreation.isValidFor(release),
                "a saved picker transaction still matches its app-private verified release after recreation");
        check(afterRecreation.token.equals(beforeRotation.token)
                        && afterRecreation.pickerTitle.equals(beforeRotation.pickerTitle),
                "recreation restores the exact transaction token and temporary picker title before the result arrives");
        check(SafApkSaver.isReservedPendingPickerName(afterRecreation.pickerTitle),
                "the default empty picker document has a strict, identifiable non-APK title");
        check(!SafApkSaver.isReservedPendingPickerName(afterRecreation.preferredFinalName)
                        && !SafApkSaver.isReservedPendingPickerName("unrelated.txt"),
                "stale-result cleanup recognition excludes final APKs and unrelated document names");
    }

    private static void safSaveFinalizesAfterIndependentReadBack() throws Exception {
        File directory = Files.createTempDirectory("daymark-saf-readback-").toFile();
        File source = new File(directory, "verified-source.apk");
        Files.write(source.toPath(), APK);
        UpdaterCore.Release release = validRelease();
        PendingSaveTransaction transaction = new PendingSaveTransaction(release, repeat('d', 32));
        File pickerDocument = new File(directory, transaction.pickerTitle);
        check(pickerDocument.createNewFile(), "picker creates only a unique temporary-title document before copy");
        check(!new File(directory, transaction.preferredFinalName).exists(),
                "the final-looking APK does not exist while the empty picker destination is pending");
        FakeSafDocument document = new FakeSafDocument(pickerDocument, false, false, true);

        SafApkSaver.copyVerifiedApk(source, release, document, transaction.token,
                transaction.pickerTitle, transaction.preferredFinalName);

        File finalized = new File(directory, transaction.preferredFinalName);
        check(finalized.isFile() && !pickerDocument.exists(),
                "successful verified read-back finalizes the temporary title to the requested APK name");
        byte[] independentlyReadBytes = Files.readAllBytes(finalized.toPath());
        check(Arrays.equals(APK, independentlyReadBytes),
                "the test independently reads the finalized destination bytes after save");
        check(sha256(independentlyReadBytes).equals(release.apkSha256),
                "independently read destination bytes match the release SHA-256 digest");
        check(document.readOpenedCount() == 1,
                "the production saver independently opened the provider destination for read-back");
        deleteTree(directory);
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

    private static String sha256RepeatedByte(long byteCount, byte value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] block = new byte[16 * 1024];
            Arrays.fill(block, value);
            long remaining = byteCount;
            while (remaining > 0L) {
                int count = (int) Math.min((long) block.length, remaining);
                digest.update(block, 0, count);
                remaining -= count;
            }
            StringBuilder result = new StringBuilder(64);
            for (byte hashByte : digest.digest()) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", hashByte & 0xff));
            }
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

    private static final class FakeSafDocument implements SafApkSaver.Document {
        private final boolean interruptDuringWrite;
        private final boolean ioFailureDuringWrite;
        private final boolean createFinalCollision;
        private final boolean renameSupported;
        private File file;
        private boolean writeOpened;
        private int readOpenedCount;

        FakeSafDocument(File file, boolean interruptDuringWrite,
                boolean createFinalCollision, boolean renameSupported) {
            this(file, interruptDuringWrite, createFinalCollision, renameSupported, false);
        }

        FakeSafDocument(File file, boolean interruptDuringWrite,
                boolean createFinalCollision, boolean renameSupported, boolean ioFailureDuringWrite) {
            this.file = file;
            this.interruptDuringWrite = interruptDuringWrite;
            this.ioFailureDuringWrite = ioFailureDuringWrite;
            this.createFinalCollision = createFinalCollision;
            this.renameSupported = renameSupported;
        }

        @Override public String displayName() { return file.getName(); }

        @Override public String renameTo(String displayName) throws IOException {
            if (!renameSupported) return null;
            File target = new File(file.getParentFile(), displayName);
            if (createFinalCollision && displayName.endsWith(".apk")) {
                Files.write(target.toPath(), "unrelated-existing-document".getBytes(StandardCharsets.UTF_8));
                return null;
            }
            if (target.exists() && !target.equals(file)) return null;
            if (!file.renameTo(target)) return null;
            file = target;
            return file.getName();
        }

        @Override public OutputStream openForWrite() throws IOException {
            writeOpened = true;
            OutputStream output = new FileOutputStream(file, false);
            if (!interruptDuringWrite && !ioFailureDuringWrite) return output;
            return new FilterOutputStream(output) {
                private boolean interrupted;

                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (!interrupted) {
                        out.write(bytes, offset, Math.max(1, length / 2));
                        interrupted = true;
                        if (ioFailureDuringWrite) throw new IOException("simulated provider write failure");
                        throw new SimulatedProcessDeath();
                    }
                    out.write(bytes, offset, length);
                }
            };
        }

        @Override public InputStream openForRead() throws IOException {
            readOpenedCount++;
            return new FileInputStream(file);
        }

        @Override public boolean delete() { return file.delete() || !file.exists(); }

        File file() { return file; }
        boolean writeOpened() { return writeOpened; }
        int readOpenedCount() { return readOpenedCount; }
    }

    private static final class TestContext extends Context {
        private final File cache;
        TestContext(File cache) { this.cache = cache; }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getCacheDir() { return new File(cache, "evictable-cache"); }
        @Override public File getNoBackupFilesDir() { return cache; }
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
