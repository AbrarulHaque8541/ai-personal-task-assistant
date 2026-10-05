package com.cue.daymark;

import java.io.IOException;

/** Orders active-import URI journaling, transaction reconciliation, and exact read-grant release. */
final class PortableImportGrantRecovery {
    static final int MAX_URI_CHARS = 8192;
    private static final Object ACTIVITY_RESTORE_GATE = new Object();
    private static int activeActivityRestoreWorkers;

    private PortableImportGrantRecovery() { }

    interface JournalStore {
        String read() throws IOException;
        void write(String uri) throws IOException;
        void clear() throws IOException;
    }

    interface ReadGrantLookup {
        boolean hasReadGrant(String uri);
    }

    interface ReadGrantReleaser {
        void releaseReadGrant(String exactUri) throws IOException;
    }

    interface TransactionReconciler {
        void reconcile() throws IOException;
    }

    /** Only the selected active restore URI is journaled; callers must not pass recovery keys or attachment URIs. */
    static void recordBeforeWork(JournalStore journal, String uri) throws IOException {
        requireJournal(journal);
        requireContentUri(uri);
        if (journal.read() != null) {
            throw new IOException("An earlier portable-import URI grant is still being recovered.");
        }
        journal.write(uri);
        if (!uri.equals(journal.read())) {
            throw new IOException("The active portable-import URI journal could not be verified.");
        }
    }

    static void activityRestoreWorkerStarted() {
        synchronized (ACTIVITY_RESTORE_GATE) {
            activeActivityRestoreWorkers++;
        }
    }

    static void activityRestoreWorkerFinished() {
        synchronized (ACTIVITY_RESTORE_GATE) {
            if (activeActivityRestoreWorkers > 0) activeActivityRestoreWorkers--;
            ACTIVITY_RESTORE_GATE.notifyAll();
        }
    }

    /** Recreated Activities wait until an in-process worker exits; after process death the gate resets naturally. */
    static void awaitNoActivityRestoreWorker() throws IOException {
        synchronized (ACTIVITY_RESTORE_GATE) {
            while (activeActivityRestoreWorkers > 0) {
                try {
                    ACTIVITY_RESTORE_GATE.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Portable-import recovery was interrupted.", interrupted);
                }
            }
        }
    }

    /** Startup reconciles the snapshot/blob transaction before releasing the exact abandoned URI grant. */
    static void recoverAfterProcessDeath(JournalStore journal, TransactionReconciler transaction,
                                         ReadGrantReleaser grants) throws IOException {
        requireJournal(journal);
        if (transaction == null || grants == null) throw new IOException("Portable-import recovery is unavailable.");
        transaction.reconcile();
        String abandonedUri = journal.read();
        if (abandonedUri == null) return;
        requireContentUri(abandonedUri);
        releaseAndClear(journal, grants, abandonedUri);
    }

    /** Normal completion follows the same ordering and refuses to release a different URI's permission. */
    static void finishAfterWork(JournalStore journal, String expectedUri,
                                TransactionReconciler transaction,
                                ReadGrantReleaser grants) throws IOException {
        requireJournal(journal);
        requireContentUri(expectedUri);
        if (transaction == null || grants == null) throw new IOException("Portable-import recovery is unavailable.");
        transaction.reconcile();
        String activeUri = journal.read();
        if (activeUri == null || !expectedUri.equals(activeUri)) {
            throw new IOException("The active portable-import URI no longer matches its recovery journal.");
        }
        releaseAndClear(journal, grants, activeUri);
    }

    /** Saved instance state may carry a pending picker URI across Activity recreation, never a recovery key. */
    static String uriForActivityState(String uri) {
        return isContentUri(uri) ? uri : null;
    }

    static String restorePendingActivityUri(String uri, ReadGrantLookup grants) {
        if (!isContentUri(uri) || grants == null) return null;
        return grants.hasReadGrant(uri) ? uri : null;
    }

    static boolean isContentUri(String uri) {
        if (uri == null || uri.isEmpty() || uri.length() > MAX_URI_CHARS
                || !uri.regionMatches(true, 0, "content://", 0, "content://".length())) return false;
        for (int index = 0; index < uri.length(); index++) {
            char value = uri.charAt(index);
            if (Character.isISOControl(value) || Character.isWhitespace(value)) return false;
        }
        return uri.length() > "content://".length();
    }

    private static void requireContentUri(String uri) throws IOException {
        if (!isContentUri(uri)) throw new IOException("The portable-import URI journal is malformed.");
    }

    private static void requireJournal(JournalStore journal) throws IOException {
        if (journal == null) throw new IOException("Portable-import recovery is unavailable.");
    }

    private static void releaseAndClear(JournalStore journal, ReadGrantReleaser grants,
                                        String exactUri) throws IOException {
        grants.releaseReadGrant(exactUri);
        journal.clear();
        if (journal.read() != null) {
            throw new IOException("The portable-import URI journal could not be cleared.");
        }
    }
}
