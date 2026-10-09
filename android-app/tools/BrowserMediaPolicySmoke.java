package com.cue.daymark;

/** JDK-only regression tests for the in-player media download handoff. */
public final class BrowserMediaPolicySmoke {
    private BrowserMediaPolicySmoke() { }

    public static void main(String[] args) {
        assert BrowserMediaPolicy.allowsHandoff(true, true, "daymark-download",
                "https://cdn.example/video.mp4") : "a user-initiated HTTPS media download should be allowed";
        assert !BrowserMediaPolicy.allowsHandoff(false, true, "daymark-download",
                "https://cdn.example/video.mp4") : "offline mode must block media handoff";
        assert !BrowserMediaPolicy.allowsHandoff(true, false, "daymark-download",
                "https://cdn.example/video.mp4") : "media handoff must require a user gesture";
        assert !BrowserMediaPolicy.allowsHandoff(true, true, "https",
                "https://cdn.example/video.mp4") : "ordinary HTTPS navigation must not trigger the media handler";
        assert !BrowserMediaPolicy.allowsHandoff(true, true, "daymark-download",
                "http://cdn.example/video.mp4") : "cleartext media must be rejected";
        assert !BrowserMediaPolicy.allowsHandoff(true, true, "daymark-download",
                "javascript:alert(1)") : "script URLs must be rejected";
        assert !BrowserMediaPolicy.allowsHandoff(true, true, "daymark-download", null)
                : "missing media URL must be rejected";
        System.out.println("PASS browser media policy: HTTPS-only, online-only, user-gesture custom-scheme handoff");
    }
}
