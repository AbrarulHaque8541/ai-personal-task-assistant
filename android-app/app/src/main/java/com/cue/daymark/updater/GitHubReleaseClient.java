package com.cue.daymark.updater;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads only the public latest-release endpoint for the fixed Daymark GitHub repository. */
public final class GitHubReleaseClient implements UpdaterCore.ReleaseClient {
    public static final String RELEASES_ENDPOINT =
            "https://api.github.com/repos/AbrarulHaque8541/ai-personal-task-assistant/releases/latest";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 15_000;
    private static final int MAX_RESPONSE_BYTES = 1_000_000;
    private static final Pattern PUBLISHER_METADATA = Pattern.compile(
            "(?s)<!--\\s*daymark-updater-v1\\s*(\\{.*?})\\s*-->");
    private static final Pattern METADATA_BLOCK = Pattern.compile("(?s)<!--\\s*daymark-updater-v1\\s*\\{.*?}\\s*-->");

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    private final ConnectionFactory connectionFactory;

    public GitHubReleaseClient() {
        this(url -> (HttpURLConnection) url.openConnection());
    }

    GitHubReleaseClient(ConnectionFactory connectionFactory) {
        if (connectionFactory == null) throw new IllegalArgumentException("Connection factory is required.");
        this.connectionFactory = connectionFactory;
    }

    @Override
    public UpdaterCore.Release fetchLatestStable() throws UpdaterCore.UpdateException {
        HttpURLConnection connection = null;
        try {
            connection = connectionFactory.open(new URL(RELEASES_ENDPOINT));
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "Daymark-Android-Updater");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null;
            if (status == 403 || status == 429) {
                throw new UpdaterCore.UpdateException(UpdaterCore.Failure.RATE_LIMITED,
                        "GitHub temporarily limited update checks. Try again later or choose Check now.");
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new UpdaterCore.UpdateException(UpdaterCore.Failure.HTTP,
                        "GitHub update check returned HTTP " + status + ".");
            }
            String response = readLimited(connection.getInputStream(), MAX_RESPONSE_BYTES);
            return parseRelease(response);
        } catch (UpdaterCore.UpdateException exception) {
            throw exception;
        } catch (UnknownHostException | SocketTimeoutException exception) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.OFFLINE,
                    "GitHub could not be reached. Daymark remains usable offline.", exception);
        } catch (IOException exception) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.OFFLINE,
                    "The update check could not reach GitHub. Daymark remains usable offline.", exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static UpdaterCore.Release parseRelease(String json) throws UpdaterCore.UpdateException {
        try {
            Map<String, Object> release = StrictJsonParser.object(StrictJsonParser.parse(json), "GitHub release");
            boolean draft = StrictJsonParser.requiredBoolean(release, "draft");
            boolean prerelease = StrictJsonParser.requiredBoolean(release, "prerelease");
            String tag = StrictJsonParser.requiredString(release, "tag_name");
            String name = StrictJsonParser.optionalString(release, "name", "");
            String body = StrictJsonParser.optionalString(release, "body", "");
            if (draft || prerelease) {
                return new UpdaterCore.Release(tag, versionFromTag(tag), name, stripMetadata(body),
                        "", 0L, 0, 0L, "", "", "", draft, prerelease);
            }

            Matcher metadataMatch = PUBLISHER_METADATA.matcher(body);
            if (!metadataMatch.find()) throw invalid("Release is missing Daymark publisher metadata.");
            String metadataJson = metadataMatch.group(1);
            if (metadataMatch.find()) throw invalid("Release contains more than one Daymark publisher metadata block.");
            Map<String, Object> metadata = StrictJsonParser.object(
                    StrictJsonParser.parse(metadataJson), "Daymark publisher metadata");
            String applicationId = StrictJsonParser.requiredString(metadata, "applicationId");
            long versionCode = StrictJsonParser.requiredLong(metadata, "versionCode");
            int minSdkVersion = StrictJsonParser.requiredInt(metadata, "minSdkVersion");
            String signerSha256 = StrictJsonParser.requiredString(metadata, "signerCertificateSha256");

            List<Object> assets = StrictJsonParser.array(release.get("assets"), "GitHub assets");
            Map<String, Object> apkAsset = null;
            for (Object value : assets) {
                Map<String, Object> asset = StrictJsonParser.object(value, "GitHub asset");
                String assetName = StrictJsonParser.requiredString(asset, "name");
                if (assetName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    if (apkAsset != null) throw invalid("Release contains more than one APK asset.");
                    apkAsset = asset;
                }
            }
            if (apkAsset == null) throw invalid("Release does not contain one APK asset.");
            String digest = StrictJsonParser.requiredString(apkAsset, "digest");
            if (!digest.matches("(?i)sha256:[0-9a-f]{64}")) {
                throw invalid("Release APK is missing its GitHub SHA-256 digest.");
            }
            String assetUrl = StrictJsonParser.requiredString(apkAsset, "browser_download_url");
            String versionName = versionFromTag(tag);
            UpdaterCore.Release result = new UpdaterCore.Release(tag, versionName,
                    name.trim().isEmpty() ? tag : name,
                    stripMetadata(body), applicationId, versionCode, minSdkVersion,
                    StrictJsonParser.requiredLong(apkAsset, "size"), digest.substring("sha256:".length()),
                    signerSha256, assetUrl, draft, prerelease);
            UpdaterCore.validateRelease(result);
            return result;
        } catch (UpdaterCore.UpdateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.INVALID_METADATA,
                    "GitHub returned invalid release metadata.", exception);
        }
    }

    private static String readLimited(InputStream input, int maximumBytes) throws IOException,
            UpdaterCore.UpdateException {
        byte[] bytes;
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if (output.size() + read > maximumBytes) {
                    throw new UpdaterCore.UpdateException(UpdaterCore.Failure.INVALID_METADATA,
                            "GitHub release response exceeded the safe size limit.");
                }
                output.write(buffer, 0, read);
            }
            bytes = output.toByteArray();
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.INVALID_METADATA,
                    "GitHub release response was not valid UTF-8.", exception);
        }
    }

    private static String versionFromTag(String tag) {
        if (tag == null) return "";
        return tag.startsWith("v") ? tag.substring(1) : tag;
    }

    private static String stripMetadata(String body) {
        if (body == null) return "";
        return METADATA_BLOCK.matcher(body).replaceFirst("").trim();
    }

    private static UpdaterCore.UpdateException invalid(String message) {
        return new UpdaterCore.UpdateException(UpdaterCore.Failure.INVALID_METADATA, message);
    }
}
