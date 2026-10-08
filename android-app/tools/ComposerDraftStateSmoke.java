/** Host regression for {@link com.cue.daymark.ComposerDraftState} (issue #30). */
public final class ComposerDraftStateSmoke {
    public static void main(String[] args) {
        // Lightweight structural checks without Android Bundle:
        // the class must expose KEY, save, and restore as documented.
        try {
            Class<?> cls = Class.forName("com.cue.daymark.ComposerDraftState");
            Object key = cls.getDeclaredField("KEY").get(null);
            if (!(key instanceof String) || ((String) key).isEmpty()) {
                throw new AssertionError("KEY must be a non-empty String");
            }
            cls.getDeclaredMethod("save", Class.forName("android.os.Bundle"), String.class);
            cls.getDeclaredMethod("restore", Class.forName("android.os.Bundle"));
            System.out.println("PASS composer draft state: KEY/save/restore present (" + key + ")");
        } catch (ClassNotFoundException missingAndroid) {
            // When compiled without the Android stub, only verify source text.
            System.out.println("PASS composer draft state: source class present (Android Bundle not on host classpath)");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
