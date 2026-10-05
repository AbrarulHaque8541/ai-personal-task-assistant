package com.cue.daymark;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Orders portable-import Activity recreation, transaction reconciliation, and exact read-grant release. */
final class PortableImportGrantRecovery {
    static final int MAX_URI_CHARS = 8192;
    private static final Object ACTIVITY_RESTORE_GATE = new Object();
    private static final Object ACTIVE_SELECTION_LOCK = new Object();
    private static final Map<String, String> ACTIVE_SELECTIONS = new HashMap<>();
    private static int activeActivityRestoreWorkers;

    private PortableImportGrantRecovery() { }

    static final class Selection {
        final String operationToken;
        final String uri;

        Selection(String operationToken, String uri) {
            this.operationToken = operationToken;
            this.uri = uri;
        }

        boolean matches(Selection other) {
            return other != null && operationToken != null && operationToken.equals(other.operationToken)
                    && uri != null && uri.equals(other.uri);
        }
    }

    static final class ActivityState {
        final String uri;
        final String operationToken;

        ActivityState(String uri, String operationToken) {
            this.uri = uri;
            this.operationToken = operationToken;
        }
    }

    interface JournalStore {
        Selection read() throws IOException;
        void write(Selection selection) throws IOException;
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

    /** Journals the exact selected URI before work and registers it only in this process. */
    static Selection recordTakenGrantOrRelease(JournalStore journal, String uri,
                                               ReadGrantReleaser grants) throws IOException {
        requireJournal(journal);
        requireContentUri(uri);
        if (grants == null) throw new IOException("Portable-import grant cleanup is unavailable.");
        Selection selection = new Selection(UUID.randomUUID().toString(), uri);
        try {
            recordBeforeWork(journal, selection);
            synchronized (ACTIVE_SELECTION_LOCK) {
                ACTIVE_SELECTIONS.put(selection.operationToken, selection.uri);
            }
            return selection;
        } catch (IOException journalFailure) {
            try {
                grants.releaseReadGrant(uri);
            } catch (IOException releaseFailure) {
                journalFailure.addSuppressed(releaseFailure);
            }
            throw journalFailure;
        }
    }

    static void recordBeforeWork(JournalStore journal, Selection selection) throws IOException {
        requireJournal(journal);
        requireSelection(selection);
        if (journal.read() != null) {
            throw new IOException("An earlier portable-import URI grant is still being recovered.");
        }
        journal.write(selection);
        Selection verified = journal.read();
        if (verified == null || !selection.matches(verified)) {
            throw new IOException("The active portable-import URI journal could not be verified.");
        }
    }

    /** A saved Activity can resume only a live same-process selection with matching journal and grant. */
    static ActivityState activityStateForSelection(JournalStore journal, Selection selection,
                                                   ReadGrantLookup grants) throws IOException {
        if (!isValidLiveSelection(selection) || grants == null || !grants.hasReadGrant(selection.uri)) return null;
        Selection recorded = journal.read();
        if (recorded == null || !selection.matches(recorded)) return null;
        return new ActivityState(selection.uri, selection.operationToken);
    }

    static Selection restorePendingActivitySelection(JournalStore journal, String savedUri,
                                                     String savedOperationToken,
                                                     ReadGrantLookup grants) throws IOException {
        Selection restored = new Selection(savedOperationToken, savedUri);
        if (!isValidLiveSelection(restored) || grants == null || !grants.hasReadGrant(restored.uri)) return null;
        Selection recorded = journal.read();
        if (recorded == null || !restored.matches(recorded)) return null;
        return restored;
    }

    /** Startup always reconciles storage first; only an explicit matching same-process state may retain a grant. */
    static boolean reconcileStartup(JournalStore journal, Selection restoredSelection,
                                    TransactionReconciler transaction, ReadGrantLookup grants,
                                    ReadGrantReleaser releaser) throws IOException {
        requireJournal(journal);
        requireRecovery(transaction, grants, releaser);
        transaction.reconcile();
        Selection journaled = journal.read();
        if (journaled == null) {
            forget(restoredSelection);
            return false;
        }
        if (restoredSelection != null && restoredSelection.matches(journaled)
                && isValidLiveSelection(restoredSelection) && grants.hasReadGrant(journaled.uri)) {
            return true;
        }
        forget(restoredSelection);
        releaseAndClear(journal, releaser, journaled);
        return false;
    }

    /** A fresh process/launch has no restored pending selection and releases only the journaled URI. */
    static void recoverAfterProcessDeath(JournalStore journal, TransactionReconciler transaction,
                                         ReadGrantReleaser grants) throws IOException {
        requireJournal(journal);
        if (transaction == null || grants == null) throw new IOException("Portable-import recovery is unavailable.");
        transaction.reconcile();
        Selection abandoned = journal.read();
        if (abandoned == null) return;
        requireContentUri(abandoned.uri);
        releaseAndClear(journal, grants, abandoned);
    }

    /** Normal completion and user cancellation reconcile before releasing their exact operation's URI. */
    static void finishAfterWork(JournalStore journal, Selection expectedSelection,
                                TransactionReconciler transaction,
                                ReadGrantReleaser grants) throws IOException {
        requireJournal(journal);
        requireSelection(expectedSelection);
        if (transaction == null || grants == null) throw new IOException("Portable-import recovery is unavailable.");
        transaction.reconcile();
        Selection active = journal.read();
        if (active == null || !expectedSelection.matches(active)) {
            throw new IOException("The active portable-import operation no longer matches its recovery journal.");
        }
        releaseAndClear(journal, grants, active);
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

    /** Recreated Activities wait until an in-process restore worker exits; after process death the gate resets. */
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

    static boolean isValidLiveSelection(Selection selection) {
        if (selection == null || !isOperationToken(selection.operationToken) || !isContentUri(selection.uri)) return false;
        synchronized (ACTIVE_SELECTION_LOCK) {
            return selection.uri.equals(ACTIVE_SELECTIONS.get(selection.operationToken));
        }
    }

    static boolean isOperationToken(String token) {
        if (token == null || token.length() != 36) return false;
        try {
            return token.equals(UUID.fromString(token).toString());
        } catch (IllegalArgumentException malformed) {
            return false;
        }
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

    private static void requireSelection(Selection selection) throws IOException {
        if (selection == null || !isOperationToken(selection.operationToken) || !isContentUri(selection.uri)) {
            throw new IOException("The portable-import recovery journal is malformed.");
        }
    }

    private static void requireJournal(JournalStore journal) throws IOException {
        if (journal == null) throw new IOException("Portable-import recovery is unavailable.");
    }

    private static void requireRecovery(TransactionReconciler transaction, ReadGrantLookup grants,
                                        ReadGrantReleaser releaser) throws IOException {
        if (transaction == null || grants == null || releaser == null) {
            throw new IOException("Portable-import recovery is unavailable.");
        }
    }

    private static void releaseAndClear(JournalStore journal, ReadGrantReleaser grants,
                                        Selection exactSelection) throws IOException {
        grants.releaseReadGrant(exactSelection.uri);
        journal.clear();
        if (journal.read() != null) {
            throw new IOException("The portable-import URI journal could not be cleared.");
        }
        forget(exactSelection);
    }

    private static void forget(Selection selection) {
        if (selection == null || selection.operationToken == null) return;
        synchronized (ACTIVE_SELECTION_LOCK) {
            if (selection.uri == null || selection.uri.equals(ACTIVE_SELECTIONS.get(selection.operationToken))) {
                ACTIVE_SELECTIONS.remove(selection.operationToken);
            }
        }
    }
}
