package com.cue.daymark.updater;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
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

    @Override
    public UpdaterCore.Release fetchLatestStable() throws UpdaterCore.UpdateException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASES_ENDPOINT).openConnection();
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
            JSONObject release = new JSONObject(json);
            boolean draft = release.optBoolean("draft", false);
            boolean prerelease = release.optBoolean("prerelease", false);
            String tag = release.optString("tag_name", "");
            String name = release.isNull("name") ? "" : release.optString("name", "");
            String body = release.isNull("body") ? "" : release.optString("body", "");
            if (draft || prerelease) {
                return new UpdaterCore.Release(tag, versionFromTag(tag), name, stripMetadata(body),
                        "", 0L, 0, 0L, "", "", "", draft, prerelease);
            }

            Matcher metadataMatch = PUBLISHER_METADATA.matcher(body);
            if (!metadataMatch.find()) throw invalid("Release is missing Daymark publisher metadata.");
            JSONObject metadata = new JSONObject(metadataMatch.group(1));
            String applicationId = metadata.getString("applicationId");
            long versionCode = metadata.getLong("versionCode");
            int minSdkVersion = metadata.getInt("minSdkVersion");
            String signerSha256 = metadata.getString("signerCertificateSha256");

            JSONArray assets = release.getJSONArray("assets");
            JSONObject apkAsset = null;
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.getJSONObject(index);
                if (asset.optString("name", "").toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                    if (apkAsset != null) throw invalid("Release contains more than one APK asset.");
                    apkAsset = asset;
                }
            }
            if (apkAsset == null) throw invalid("Release does not contain one APK asset.");
            String digest = apkAsset.optString("digest", "");
            if (!digest.matches("(?i)sha256:[0-9a-f]{64}")) {
                throw invalid("Release APK is missing its GitHub SHA-256 digest.");
            }
            String assetUrl = apkAsset.getString("browser_download_url");
            String versionName = versionFromTag(tag);
            UpdaterCore.Release result = new UpdaterCore.Release(tag, versionName,
                    name == null || name.trim().isEmpty() ? tag : name,
                    stripMetadata(body), applicationId, versionCode, minSdkVersion,
                    apkAsset.getLong("size"), digest.substring("sha256:".length()),
                    signerSha256, assetUrl, draft, prerelease);
            UpdaterCore.validateRelease(result);
            return result;
        } catch (UpdaterCore.UpdateException exception) {
            throw exception;
        } catch (JSONException | RuntimeException exception) {
            throw new UpdaterCore.UpdateException(UpdaterCore.Failure.INVALID_METADATA,
                    "GitHub returned invalid release metadata.", exception);
        }
    }

    private static String readLimited(InputStream input, int maximumBytes) throws IOException,
            UpdaterCore.UpdateException {
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
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
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
