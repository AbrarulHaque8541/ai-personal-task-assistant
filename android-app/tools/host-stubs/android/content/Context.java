package android.content;

import java.io.File;

/** Minimal host-test surface only; this stub is never included in Android application sources. */
public abstract class Context {
    public abstract Context getApplicationContext();
    public abstract File getCacheDir();
    public abstract File getNoBackupFilesDir();
}
