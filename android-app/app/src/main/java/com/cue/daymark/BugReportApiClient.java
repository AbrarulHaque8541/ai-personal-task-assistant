package com.cue.daymark;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** POST /repos/{owner}/{repo}/issues using a user-provided token. */
final class BugReportApiClient {
    private BugReportApiClient() { }

    /**
     * @return html_url of the created issue
     */
    static String createIssue(String token, String title, String body) throws Exception {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing GitHub token");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing title");
        }
        JSONObject payload = new JSONObject();
        payload.put("title", title.trim());
        payload.put("body", body == null ? "" : body);
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(BugReportComposer.ISSUES_API).openConnection();
        try {
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("Authorization", "Bearer " + token.trim());
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "Daymark-BugReport");
            conn.setFixedLengthStreamingMode(bytes.length);
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.close();

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String response = readAll(stream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("GitHub HTTP " + code + ": " + trim(response, 300));
            }
            JSONObject created = new JSONObject(response);
            String html = created.optString("html_url", "");
            if (html.isEmpty()) {
                int number = created.optInt("number", -1);
                if (number > 0) {
                    html = "https://github.com/" + BugReportComposer.OWNER + "/"
                            + BugReportComposer.REPO + "/issues/" + number;
                }
            }
            if (html.isEmpty()) throw new IllegalStateException("Issue created but URL missing");
            return html;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }

    private static String trim(String v, int max) {
        if (v == null) return "";
        String s = v.trim();
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
