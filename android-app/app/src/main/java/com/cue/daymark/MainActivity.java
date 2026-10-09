package com.cue.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.DownloadManager;
import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.URLUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.text.NumberFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.cue.daymark.updater.AndroidApkVerifier;
import com.cue.daymark.updater.GitHubApkDownloader;
import com.cue.daymark.updater.GitHubReleaseClient;
import com.cue.daymark.updater.PendingSaveTransaction;
import com.cue.daymark.updater.SafApkSaver;
import com.cue.daymark.updater.UpdaterActivityResultRouter;
import com.cue.daymark.updater.UpdaterCore;
import com.cue.daymark.updater.UpdaterPublisherConfig;
import com.cue.daymark.updater.UpdaterRecoveryStore;

// Browser workspace + task workspace share one Activity but never share visible controls.
public final class MainActivity extends Activity {
    private static final int REQUEST_ATTACH_DOCUMENT = 7341;
    private static final int REQUEST_IMPORT_EXTENSION = 7812;
    private static final int REQUEST_EXPORT_EXTENSION = 7813;
    private static final int REQUEST_PORTABLE_EXPORT = 7342;
    private static final int REQUEST_PORTABLE_IMPORT = 7343;
    private static final String STATE_PENDING_ATTACHMENT_TASK = "pending_attachment_task";
    private static final String STATE_PENDING_PORTABLE_IMPORT_URI = "pending_portable_import_uri";
    private static final String STATE_PENDING_PORTABLE_IMPORT_TOKEN = "pending_portable_import_token";
    private static final String STATE_WEB_MODE = WebModeState.KEY;
    private static final String STATE_TASK_DRAFT = "state_task_draft";
    private static final String PREFERENCES = "daymark.preferences.v1";
    private static final String THEME_KEY = "theme_mode";
    private static final int THEME_SYSTEM = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;
    private static final String POWER_MODE_KEY = "power_mode";
    private static final String TEXT_SIZE_KEY = "text_size_mode";
    private static final String HIGH_CONTRAST_KEY = "high_contrast";
    private static final String LAST_UPDATE_CHECK_KEY = "updater.last_check_at";
    private static final String DISMISSED_UPDATE_TAG_KEY = "updater.dismissed_release_tag";
    private static final int REQUEST_SAVE_VERIFIED_APK = UpdaterActivityResultRouter.REQUEST_CODE;
    private static final String PENDING_SAVE_STATE_KEY = "updater.pending_save_transaction.v1";
    private static final String BROWSER_PREFERENCES = "daymark.browser.local.v1";
    private static final String BROWSER_HISTORY_KEY = "history_urls";
    private static final String SEARCH_ENGINE_KEY = "search_engine";
    private static final String BROWSER_ONLINE_ENABLED_KEY = "online_browsing_enabled";
    private static final String BROWSER_NETWORK_DISCLOSURE_ACCEPTED_KEY = "network_disclosure_accepted";
    private static final String SAFE_BROWSING_ENABLED_KEY = "safe_browsing_enabled";

    private final List<Task> tasks = new ArrayList<>();
    private final List<Task> lastSavedTasks = new ArrayList<>();
    private final List<TaskTemplate> taskTemplates = new ArrayList<>();
    private final List<String> filterKeys = Arrays.asList(
            TaskLogic.FILTER_ALL, TaskLogic.FILTER_TODAY,
            TaskLogic.FILTER_UPCOMING, TaskLogic.FILTER_OVERDUE,
            TaskLogic.FILTER_NO_DATE, TaskLogic.FILTER_COMPLETED);
    private final List<Button> filterButtons = new ArrayList<>();
    private final List<View> powerOnlyViews = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ActivityCallbackGate activityCallbackGate = new ActivityCallbackGate();

    private ExecutorService storageExecutor;
    private ExecutorService updaterExecutor;
    private UpdaterRecoveryStore updaterRecoveryStore;
    private EncryptedTaskStore taskStore;
    private AndroidAttachmentStore attachmentStore;
    private PortableBackupManager portableBackupManager;
    private boolean attachmentBusy;
    private volatile boolean portableBusy;
    private volatile boolean portableCanCancel;
    private volatile boolean portableCancelRequested;
    private volatile boolean portableRestoreWorkerActive;
    private volatile byte[] pendingRecoveryKey;
    private File pendingExportArchive;
    private Uri pendingPortableImportUri;
    private String pendingPortableImportOperationToken;
    private Uri activePortableImportUri;
    private String activePortableImportOperationToken;
    private boolean pendingPortableImportNeedsRepick;
    private boolean portableImportCleanupActive;
    private final Object attachmentCancelLock = new Object();
    private volatile boolean attachmentCancelRequested;
    private boolean attachmentCanCancel;
    private String pendingAttachmentTaskId;
    private String pendingExtensionExportId;
    private Uri pendingPickedAttachmentUri;
    private String pendingPickedAttachmentTaskId;
    private boolean storageReady;
    private boolean storageLoading = true;
    private boolean storageLoadFailed;
    private boolean storageSaveFailed;
    private String storageFailureDetails;
    private long saveRevision;
    private int themeMode;
    private int textSizeMode;
    private boolean powerMode;
    private boolean highContrast;
    private boolean updateCheckRunning;
    private boolean updateTransferRunning;
    private boolean verifiedSaveRunning;
    private boolean recoveryCheckRunning;
    private boolean recoveryPromptShowing;
    private boolean activityResumed;
    private GitHubApkDownloader activeUpdateDownloader;
    private AlertDialog updateDownloadDialog;
    private File pendingVerifiedApk;
    private UpdaterCore.Release pendingVerifiedRelease;
    private PendingSaveTransaction pendingSaveTransaction;
    private boolean webMode;
    private BrowserNetworkPolicy browserNetworkPolicy = new BrowserNetworkPolicy();
    private BrowserSettingsPolicy browserSettingsPolicy = new BrowserSettingsPolicy();
    private boolean suppressBrowserOnlineToggleListener;
    private boolean suppressSafeBrowsingToggleListener;
    private String taskDraft = "";
    private float textScale = 1.0f;
    private SharedPreferences browserPreferences;
    private BrowserAddress.SearchEngine searchEngine = BrowserAddress.SearchEngine.DUCKDUCKGO;
    private ExtensionRuntime extensionRuntime;
    private String activeFilter = TaskLogic.FILTER_ALL;
    private String searchQuery = "";
    private Task pendingDeletedTask;
    private int pendingDeletedIndex;
    private Task pendingAttachmentCleanupTask;
    private boolean pendingAttachmentCleanupSaved;
    private boolean pendingAttachmentCleanupExpired;
    private Runnable undoDismissal;
    private Palette palette;

    private LinearLayout root;
    private View appTopBar;
    private View sharedComposer;
    private LinearLayout taskList;
    private LinearLayout suggestionList;
    private LinearLayout undoBar;
    private TextView undoMessage;
    private Button undoButton;
    private TextView dateHeading;
    private TextView openCount;
    private TextView dueTodayCount;
    private TextView taskCount;
    private TextView storageStatus;
    private Button cancelAttachmentButton;
    private TextView emptyTitle;
    private TextView emptyCopy;
    private Button addTaskButton;
    private Button addDetailsButton;
    private Button pathButton;
    private EditText quickCaptureInput;
    private TextView captureFeedback;
    private EditText searchInput;
    private TextView searchClear;
    private View suggestionCard;
    private View filterControlView;
    private View searchControlView;
    private View taskScreen;
    private LinearLayout taskActions;
    private LinearLayout webActions;
    private EditText browserAddressInput;
    private Spinner browserSearchEngineSpinner;
    private LinearLayout browserScreen;
    private FrameLayout browserViewport;
    private View browserHomeView;
    private DaymarkWebView browserWebView;
    private Button taskModeButton;
    private Button webModeButton;
    private Button webGoButton;
    private Button browserBackButton;
    private Button browserForwardButton;
    private Button browserReloadButton;
    private Button browserHomeButton;
    private Button browserHistoryButton;
    private Button browserSettingsButton;
    private Button browserOverflowButton;
    private Button browserMediaButton;
    private String lastBrowserSearchQuery = "";
    private View fullScreenVideoView;
    private android.webkit.WebChromeClient.CustomViewCallback fullScreenVideoCallback;
    private int savedWindowFlags;
    private int savedSystemUiVisibility;
    private boolean hasSavedVideoUiState;
    private Button browserTabsButton;
    private final List<DaymarkWebView> browserTabs = new ArrayList<>();
    private int activeBrowserTabIndex = -1;
    private Button browserExpandButton;
    private Button browserReaderButton;
    private LinearLayout browserReaderActionRow;
    private android.app.Dialog fullScreenWebDialog;
    private FrameLayout fullScreenWebContainer;
    private Spinner searchEngineSpinner;
    private CheckBox browserOnlineToggle;
    private TextView browserStatus;
    private View browserProviderRow;
    private TextView browserPrivacyButton;
    private Button browserProviderPickerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences preferences = getApplicationContext()
                .getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        themeMode = Math.max(THEME_SYSTEM, Math.min(THEME_DARK, preferences.getInt(THEME_KEY, THEME_SYSTEM)));
        powerMode = preferences.getBoolean(POWER_MODE_KEY, false);
        textSizeMode = Math.max(0, Math.min(2, preferences.getInt(TEXT_SIZE_KEY, 1)));
        textScale = TextScalePolicy.combined(
                textSizeMode == 0 ? 0.9f : textSizeMode == 2 ? 1.25f : 1.0f,
                getResources().getConfiguration().fontScale);
        highContrast = preferences.getBoolean(HIGH_CONTRAST_KEY, false);
        setTheme(themeResource(themeMode));
        super.onCreate(savedInstanceState);

        GitHubApkDownloader.cleanupPartialDownloads(getApplicationContext());
        updaterRecoveryStore = new UpdaterRecoveryStore(getNoBackupFilesDir());
        restorePendingSaveTransaction(savedInstanceState);
        palette = Palette.from(this, themeMode, highContrast);
        storageExecutor = Executors.newSingleThreadExecutor();
        updaterExecutor = Executors.newSingleThreadExecutor();
        taskStore = new EncryptedTaskStore(this);
        attachmentStore = new AndroidAttachmentStore(getApplicationContext());
        extensionRuntime = ExtensionRuntime.create(this);
        portableBackupManager = new PortableBackupManager(getApplicationContext());
        if (savedInstanceState != null) {
            pendingAttachmentTaskId = savedInstanceState.getString(STATE_PENDING_ATTACHMENT_TASK);
            String savedDraft = savedInstanceState.getString(STATE_TASK_DRAFT);
            if (savedDraft != null) {
                taskDraft = savedDraft;
            }
            String savedPortableUri = savedInstanceState.getString(STATE_PENDING_PORTABLE_IMPORT_URI);
            String savedPortableToken = savedInstanceState.getString(STATE_PENDING_PORTABLE_IMPORT_TOKEN);
            if (savedPortableUri != null || savedPortableToken != null) {
                try {
                    PortableImportGrantRecovery.Selection restored = portableBackupManager
                            .restorePendingPortableImportSelection(savedPortableUri, savedPortableToken);
                    if (restored != null) {
                        pendingPortableImportUri = Uri.parse(restored.uri);
                        pendingPortableImportOperationToken = restored.operationToken;
                    } else {
                        pendingPortableImportNeedsRepick = true;
                    }
                } catch (IOException invalidSavedSelection) {
                    pendingPortableImportNeedsRepick = true;
                }
            }
            webMode = WebModeState.restore(savedInstanceState.getBoolean(STATE_WEB_MODE, false));
        }
        browserPreferences = getSharedPreferences(BROWSER_PREFERENCES, MODE_PRIVATE);
        searchEngine = BrowserAddress.SearchEngine.fromName(
                browserPreferences.getString(SEARCH_ENGINE_KEY, BrowserAddress.SearchEngine.DUCKDUCKGO.name()));
        browserNetworkPolicy = new BrowserNetworkPolicy(true);
        // Page traffic still starts only after a user taps Go, a provider, or a history entry.
        browserPreferences.edit().putBoolean(BROWSER_ONLINE_ENABLED_KEY, true).apply();
        browserSettingsPolicy = new BrowserSettingsPolicy(readSafeBrowsingPreference());
        sanitizeStoredBrowserHistory();
        buildInterface();
        loadEncryptedTasks();
    }

    @SuppressWarnings("deprecation")
    private void restorePendingSaveTransaction(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        Object saved = savedInstanceState.getSerializable(PENDING_SAVE_STATE_KEY);
        if (!(saved instanceof PendingSaveTransaction)) return;
        PendingSaveTransaction transaction = (PendingSaveTransaction) saved;
        try {
            UpdaterRecoveryStore.PendingUpdate retained = updaterRecoveryStore.readPending();
            if (retained != null && transaction.isValidFor(retained.release)
                    && retained.verifiedApk.isFile()
                    && retained.verifiedApk.length() == retained.release.apkSizeBytes) {
                pendingSaveTransaction = transaction;
                pendingVerifiedApk = retained.verifiedApk;
                pendingVerifiedRelease = retained.release;
            }
        } catch (Exception ignored) {
            // Invalid state is never trusted; any stale returned URI is handled narrowly in the result callback.
        }
    }

    private boolean readBrowserOnlinePreference() {
        try {
            return browserPreferences.getBoolean(BROWSER_ONLINE_ENABLED_KEY,
                    BrowserNetworkPolicy.DEFAULT_ONLINE_ENABLED);
        } catch (ClassCastException invalidPreference) {
            browserPreferences.edit().remove(BROWSER_ONLINE_ENABLED_KEY).apply();
            return BrowserNetworkPolicy.DEFAULT_ONLINE_ENABLED;
        }
    }

    private boolean readSafeBrowsingPreference() {
        try {
            return browserPreferences.getBoolean(SAFE_BROWSING_ENABLED_KEY,
                    BrowserSettingsPolicy.DEFAULT_SAFE_BROWSING_ENABLED);
        } catch (ClassCastException invalidPreference) {
            browserPreferences.edit().remove(SAFE_BROWSING_ENABLED_KEY).apply();
            return BrowserSettingsPolicy.DEFAULT_SAFE_BROWSING_ENABLED;
        }
    }

    private void sanitizeStoredBrowserHistory() {
        String existing;
        try {
            existing = browserPreferences.getString(BROWSER_HISTORY_KEY, "");
        } catch (ClassCastException invalidHistoryPreference) {
            existing = "";
        }
        String sanitized = BrowserHistory.sanitizeSerialized(existing);
        if (!sanitized.equals(existing)) {
            browserPreferences.edit().putString(BROWSER_HISTORY_KEY, sanitized).apply();
        }
    }

    @Override
    public void onBackPressed() {
        if (fullScreenVideoView != null) { hideFullScreenVideo(true); return; }
        if (fullScreenWebDialog != null && fullScreenWebDialog.isShowing()) {
            closeFullScreenWebReader();
            return;
        }
        if (browserWebView != null && browserWebView.canGoBack()) {
            browserWebView.goBack();
            syncBrowserButtons();
            return;
        }
        if (webMode) {
            setWebMode(false);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        if (fullScreenVideoView != null) hideFullScreenVideo(true);
        if (!browserTabs.isEmpty() || browserWebView != null) {
            destroyAllBrowserTabs(true);
            if (browserHomeView != null) browserHomeView.setVisibility(View.VISIBLE);
            if (browserStatus != null) browserStatus.setText("Browser tabs closed when Daymark went into the background; no page was restored.");
        }
        activityResumed = false;
        super.onPause();
    }

    private boolean isActivityCallbackCurrent() {
        return activityCallbackGate.isOpen() && !isFinishing() && !isDestroyed();
    }

    private void postActivityCallback(Runnable callback) {
        mainHandler.post(activityCallbackGate.guard(() -> {
            if (!isActivityCallbackCurrent()) return;
            callback.run();
        }));
    }

    @Override
    protected void onDestroy() {
        activityCallbackGate.close();
        if (undoDismissal != null) mainHandler.removeCallbacks(undoDismissal);
        portableCancelRequested = true;
        if (!portableRestoreWorkerActive) {
            PortableBackupCodec.clear(pendingRecoveryKey);
            pendingRecoveryKey = null;
        }
        if (pendingExportArchive != null && pendingExportArchive.exists()) pendingExportArchive.delete();
        if (!isChangingConfigurations()) discardPendingPortableImport();
        if (activeUpdateDownloader != null) activeUpdateDownloader.cancel();
        if (updateDownloadDialog != null && updateDownloadDialog.isShowing()) updateDownloadDialog.dismiss();
        // Recovery metadata and the verified cache file outlive this Activity/process instance.
        pendingVerifiedApk = null;
        pendingVerifiedRelease = null;
        pendingSaveTransaction = null;
        if (storageExecutor != null) storageExecutor.shutdown();
        if (updaterExecutor != null) updaterExecutor.shutdownNow();
        if (fullScreenVideoView != null) hideFullScreenVideo(true);
        destroyAllBrowserTabs(true);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (pendingAttachmentTaskId != null) {
            outState.putString(STATE_PENDING_ATTACHMENT_TASK, pendingAttachmentTaskId);
        }
        outState.putBoolean(STATE_WEB_MODE, webMode);
        if (!webMode && quickCaptureInput != null && quickCaptureInput.getText() != null) {
            taskDraft = quickCaptureInput.getText().toString();
        }
        if (taskDraft != null && !taskDraft.isEmpty()) {
            outState.putString(STATE_TASK_DRAFT, taskDraft);
        }
        PortableImportGrantRecovery.Selection pendingSelection = pendingPortableImportSelection();
        if (pendingSelection != null) {
            try {
                PortableImportGrantRecovery.ActivityState saved = portableBackupManager
                        .activityStateForSelection(pendingSelection);
                if (saved != null) {
                    outState.putString(STATE_PENDING_PORTABLE_IMPORT_URI, saved.uri);
                    outState.putString(STATE_PENDING_PORTABLE_IMPORT_TOKEN, saved.operationToken);
                }
            } catch (IOException ignored) {
                // An incomplete or unreadable journal is recovered conservatively on startup.
            }
        }
        if (pendingSaveTransaction != null) {
            outState.putSerializable(PENDING_SAVE_STATE_KEY, pendingSaveTransaction);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (!updateTransferRunning && !verifiedSaveRunning && pendingSaveTransaction == null
                && pendingVerifiedApk == null
                && !recoverPendingVerifiedUpdate()) {
            checkForUpdates(false);
        }
        checkDueReminders();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT_EXTENSION) {
            String exportId = pendingExtensionExportId;
            pendingExtensionExportId = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            if (extensionRuntime == null || exportId == null) return;
            String json = extensionRuntime.store().userPackJson(exportId);
            if (json == null) {
                showToast("This extension could not be exported.");
                return;
            }
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new IOException("Could not write the extension file.");
                out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                showToast("Extension exported.");
            } catch (Exception exception) {
                showToast("Extension could not be exported.");
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_EXTENSION) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            byte[] bytes;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Could not open extension file.");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (out.size() + read > 5 * 1024 * 1024) throw new IOException("Extension file is too large.");
                    out.write(buffer, 0, read);
                }
                bytes = out.toByteArray();
            }
            BrowserExtension parsed;
            String raw = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            String trimmed = raw.trim();
            String lowerName = uri.toString().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("{")) {
                org.json.JSONObject probe = new org.json.JSONObject(trimmed);
                parsed = (probe.has("content_scripts") || probe.has("manifest_version"))
                        ? ExtensionPackageParser.parseWebExtensionManifest(trimmed)
                        : ExtensionPackageParser.parseDaymarkJson(trimmed, false);
            } else if (trimmed.contains("==UserScript==")) {
                parsed = ExtensionPackageParser.parseUserScript(raw);
            } else if (lowerName.endsWith(".zip") || lowerName.endsWith(".xpi") || lowerName.endsWith(".crx")
                    || (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K')
                    || (bytes.length >= 4 && bytes[0] == 'C' && bytes[1] == 'r' && bytes[2] == '2' && bytes[3] == '4')) {
                parsed = ExtensionPackageParser.parseWebExtensionArchive(bytes);
            } else {
                throw new IllegalArgumentException("Unsupported extension format.");
            }

            String warning = parsed.warnings == null || parsed.warnings.isEmpty()
                    ? "No unsupported API warnings."
                    : parsed.warnings;
            String summary = "Name: " + parsed.name + "\nVersion: " + parsed.version +
                    "\nMatches: " + parsed.matches.size() +
                    "\nCSS: " + parsed.css.length() + " chars · JS: " + parsed.js.length() +
                    "\n\n" + warning +
                    "\n\nDaymark will keep this pack local and will not provide privileged browser APIs.";
            new AlertDialog.Builder(this)
                    .setTitle("Review extension")
                    .setMessage(summary)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Install", (d, w) -> {
                        try {
                            extensionRuntime.store().installUserPack(parsed);
                            showToast("Extension installed.");
                            showBrowserExtensionsManager();
                        } catch (Exception exception) {
                            showToast("Extension could not be installed.");
                        }
                    }).show();
        } catch (Exception exception) {
            showToast("Invalid or unsupported extension file.");
        }
            return;
        }

        UpdaterActivityResultRouter.Route updaterRoute = UpdaterActivityResultRouter.route(
                requestCode, resultCode == RESULT_OK, data != null && data.getData() != null);
        if (updaterRoute != UpdaterActivityResultRouter.Route.NOT_UPDATER) {
            PendingSaveTransaction transaction = pendingSaveTransaction;
            File verifiedApk = pendingVerifiedApk;
            UpdaterCore.Release release = pendingVerifiedRelease;
            pendingSaveTransaction = null;
            pendingVerifiedApk = null;
            pendingVerifiedRelease = null;
            if (transaction == null || verifiedApk == null || release == null
                    || !transaction.isValidFor(release)) {
                if (updaterRoute == UpdaterActivityResultRouter.Route.SELECTED) {
                    cleanStalePickerResult(data.getData());
                }
                recoverPendingVerifiedUpdate();
                return;
            }
            if (updaterRoute == UpdaterActivityResultRouter.Route.CANCELLED) {
                if (!recoverPendingVerifiedUpdate()) {
                    showSaveFailureChoices(release, verifiedApk,
                            "No save location was selected. The verified update remains in app-private storage.");
                }
                return;
            }
            Uri destination = data.getData();
            verifiedSaveRunning = true;
            updaterExecutor.execute(() -> {
                boolean saved = false;
                String failureMessage = null;
                try {
                    revalidatePendingSaveTransaction(transaction, verifiedApk, release);
                    copyVerifiedApkToDocument(verifiedApk, destination, release, transaction);
                    saved = true;
                } catch (Exception exception) {
                    failureMessage = "The verified APK could not be saved safely. The source is revalidated before copying, the destination is read back before finalization, and interrupted copies use a .daymark-incomplete marker; if the provider failed during finalization, check the chosen folder before retrying. The verified source remains in app-private storage for retry or explicit discard.";
                }
                final boolean savedResult = saved;
                final String saveFailure = failureMessage;
                mainHandler.post(() -> {
                    verifiedSaveRunning = false;
                    if (!activityResumed || isFinishing() || isDestroyed()) return;
                    if (savedResult) {
                        showInfo("Verified APK saved", "A copy was saved to your chosen location. Daymark did not open an installer or install it. The verified app-private copy remains available until you explicitly discard it; open the saved copy yourself from Files if you choose to continue.");
                    } else {
                        showSaveFailureChoices(release, verifiedApk, saveFailure);
                    }
                });
            });
            return;
        }
        if (requestCode == REQUEST_ATTACH_DOCUMENT) {
            String taskId = pendingAttachmentTaskId;
            pendingAttachmentTaskId = null;
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            if (taskId == null) {
                showToast("The task was no longer available. Choose it again to attach this file.");
                return;
            }
            if (!storageReady && storageLoading) {
                pendingPickedAttachmentUri = data.getData();
                pendingPickedAttachmentTaskId = taskId;
                showToast("Opening encrypted tasks; the selected file will be read after they are ready.");
                return;
            }
            importAttachment(taskId, data.getData());
            return;
        }
        if (requestCode == REQUEST_PORTABLE_EXPORT) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) {
                finishPortableExport("Backup export cancelled. No task data was changed.");
                return;
            }
            if (pendingRecoveryKey == null || pendingExportArchive == null) {
                finishPortableExport("The recovery key was cleared before export finished. Start a new export.");
                return;
            }
            writePortableExport(data.getData(), pendingExportArchive);
            return;
        }
        if (requestCode == REQUEST_PORTABLE_IMPORT) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            if (!retainPortableImportUri(data.getData(), data.getFlags())) return;
            showPortableImportKeyDialog();
        }
    }

    private void cleanStalePickerResult(Uri destination) {
        // This callback is only for our ACTION_CREATE_DOCUMENT request. Rename/delete the returned
        // newly-created URI itself; never search for, or delete, a document by display name.
        Uri cleanupUri = destination;
        String orphanName = "Daymark-orphaned-save-"
                + UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT) + ".tmp";
        try {
            Uri marked = DocumentsContract.renameDocument(getContentResolver(), cleanupUri, orphanName);
            if (marked != null) cleanupUri = marked;
        } catch (Exception ignored) {
            // Providers may not support rename; still attempt deletion of this exact returned URI.
        }
        try {
            DocumentsContract.deleteDocument(getContentResolver(), cleanupUri);
        } catch (Exception ignored) {
            // If cleanup is unsupported, a successful rename leaves a conspicuous orphan marker.
        }
    }

    private void revalidatePendingSaveTransaction(PendingSaveTransaction transaction,
            File verifiedApk, UpdaterCore.Release release) throws Exception {
        UpdaterRecoveryStore.PendingUpdate retained = updaterRecoveryStore.readPending();
        if (retained == null || !transaction.isValidFor(retained.release)
                || !transaction.isValidFor(release)
                || !verifiedApk.getCanonicalFile().equals(retained.verifiedApk.getCanonicalFile())
                || !verifiedApk.isFile()) {
            throw new IOException("The pending picker transaction no longer matches the retained verified update.");
        }
        AndroidApkVerifier verifier = new AndroidApkVerifier(getApplicationContext());
        UpdaterCore.verifyDownloadedArtifact(release, verifiedApk, getPackageName(), currentVersionCode(),
                Build.VERSION.SDK_INT, verifier.installedSignerSha256(),
                UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256, verifier);
    }

    private int themeResource(int mode) {
        if (mode == THEME_LIGHT) return R.style.AppThemeLight;
        if (mode == THEME_DARK) return R.style.AppThemeDark;
        return R.style.AppTheme;
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.setClipToPadding(false);

        appTopBar = buildTopBar();
        root.addView(appTopBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sharedComposer = buildSharedComposer();
        root.addView(sharedComposer, bottomMargin(dp(8)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        taskScreen = scrollView;
        root.addView(scrollView, scrollParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addDashboardHeading(content);
        addPrivacyCard(content);
        addSuggestionCard(content);
        addTaskSection(content);
        addLocalStorageNote(content);

        browserScreen = buildBrowserScreen();
        browserScreen.setVisibility(View.GONE);
        root.addView(browserScreen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addUndoBar();
        applySystemBarsAndInsets();
        setContentView(root);
        root.requestApplyInsets();
        syncModeUi();
        render();
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(12), dp(18), dp(8));

        LinearLayout mark = new LinearLayout(this);
        mark.setGravity(Gravity.CENTER);
        TextView markText = text("d", 20, Color.WHITE, Typeface.BOLD);
        mark.setBackground(shape(palette.accent, 14, palette.accent));
        mark.addView(markText);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        bar.addView(mark, markParams);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(10), 0, 0, 0);
        TextView brandName = text("daymark", 16, palette.text, Typeface.BOLD);
        TextView brandCaption = text("PERSONAL SPACE", 9, palette.muted, Typeface.BOLD);
        brandCaption.setLetterSpacing(0.13f);
        brand.addView(brandName);
        brand.addView(brandCaption);
        bar.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        pathButton = plainButton(powerMode ? "Simple mode" : "Power mode");
        pathButton.setContentDescription(powerMode
                ? "Switch to Simple mode. Your tasks will stay the same."
                : "Switch to Power mode for search, filters, and suggestions. Your tasks will stay the same.");
        pathButton.setOnClickListener(view -> toggleExperienceMode());
        // Keep the main header focused; advanced task mode remains available from More.
        pathButton.setVisibility(View.GONE);
        Button moreButton = plainButton("More");
        moreButton.setContentDescription("Open appearance, accessibility, and permission settings");
        moreButton.setOnClickListener(view -> showSettingsDialog());
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        moreParams.leftMargin = dp(6);
        bar.addView(moreButton, moreParams);
        return bar;
    }

    private void addDashboardHeading(LinearLayout content) {
        dateHeading = text("YOUR DAY", 10, palette.muted, Typeface.BOLD);
        dateHeading.setLetterSpacing(0.14f);
        content.addView(dateHeading, bottomMargin(dp(7)));

        TextView heading = text("What do you want\nto get done?", 29, palette.text, Typeface.BOLD);
        heading.setLineSpacing(0, 0.96f);
        content.addView(heading, bottomMargin(dp(5)));

        TextView subtitle = text("Add one task at a time. You can change it later.", 15, palette.muted, Typeface.NORMAL);
        content.addView(subtitle, bottomMargin(dp(17)));

        LinearLayout stats = horizontalCard(palette.surface);
        addStat(stats, "Open tasks", openCount = text("0", 23, palette.accent, Typeface.BOLD));
        addStat(stats, "Due today", dueTodayCount = text("0", 23, palette.text, Typeface.BOLD));
        content.addView(stats, bottomMargin(dp(14)));
    }

    private void addStat(LinearLayout parent, String label, TextView value) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(13), dp(11), dp(8), dp(10));
        TextView caption = text(label, 12, palette.muted, Typeface.BOLD);
        column.addView(caption, bottomMargin(dp(3)));
        column.addView(value);
        parent.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void addPrivacyCard(LinearLayout content) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(11), dp(13), dp(11));
        card.setBackground(shape(palette.accentSoft, 12, palette.accentSoft));

        TextView shield = text("●", 11, palette.accent, Typeface.BOLD);
        shield.setGravity(Gravity.CENTER);
        card.addView(shield, new LinearLayout.LayoutParams(dp(22), dp(22)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(8), 0, 0, 0);
        copy.addView(text("Tasks are encrypted on this device", 13, palette.text, Typeface.BOLD));
        copy.addView(text("Web requests only after your tap · no task sync", 12, palette.muted, Typeface.NORMAL));
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        storageStatus = text("Opening encrypted storage…", 12, palette.muted, Typeface.NORMAL);
        storageStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        storageStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(storageStatus);
        cancelAttachmentButton = compactButton("Cancel import", true);
        cancelAttachmentButton.setContentDescription("Cancel the active import or backup before it is committed");
        cancelAttachmentButton.setVisibility(View.GONE);
        cancelAttachmentButton.setOnClickListener(view -> requestOperationCancel());
        copy.addView(cancelAttachmentButton, topMargin(dp(4)));
        content.addView(card, bottomMargin(dp(15)));
    }

    private View buildSharedComposer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(9), dp(12), dp(9));
        card.setBackground(shape(palette.surface, 14, palette.line));
        card.setElevation(dp(1));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        taskModeButton = plainButton("Task");
        taskModeButton.setContentDescription("Task mode. Typing here creates a task only after you tap Add task.");
        taskModeButton.setOnClickListener(view -> setWebMode(false));
        webModeButton = plainButton("Web");
        webModeButton.setContentDescription("Web mode. Search or open a website only after you tap Go or a site link.");
        webModeButton.setOnClickListener(view -> setWebMode(true));
        modes.addView(taskModeButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams webModeParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        webModeParams.leftMargin = dp(6);
        modes.addView(webModeButton, webModeParams);
        card.addView(modes, bottomMargin(dp(7)));

        quickCaptureInput = new EditText(this);
        quickCaptureInput.setSingleLine(true);
        quickCaptureInput.setTextSize(16 * textScale);
        quickCaptureInput.setHint("Type a task in your own words");
        quickCaptureInput.setContentDescription("What do you want to get done? Type a task");
        quickCaptureInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        quickCaptureInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(2048) });
        quickCaptureInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        quickCaptureInput.setMinHeight(dp(52));
        quickCaptureInput.setTextColor(palette.text);
        quickCaptureInput.setHintTextColor(palette.muted);
        quickCaptureInput.setBackground(shape(palette.background, 10, palette.line));
        quickCaptureInput.setText(webMode ? "" : taskDraft);
        if (!webMode && taskDraft != null && !taskDraft.isEmpty()) {
            quickCaptureInput.setSelection(Math.min(taskDraft.length(), 2048));
        }
        card.addView(quickCaptureInput, bottomMargin(dp(7)));

        LinearLayout smartChips = new LinearLayout(this);
        smartChips.setGravity(Gravity.CENTER_VERTICAL);
        Button todayChip = compactButton("Today", false);
        Button tomorrowChip = compactButton("Tomorrow", false);
        Button highChip = compactButton("High", false);
        Button detailsChip = compactButton("Details", false);
        todayChip.setOnClickListener(v -> appendSmartToken(" today"));
        tomorrowChip.setOnClickListener(v -> appendSmartToken(" tomorrow"));
        highChip.setOnClickListener(v -> appendSmartToken(" high"));
        detailsChip.setOnClickListener(v -> showTaskEditor(null, quickCaptureInput.getText().toString()));
        smartChips.addView(todayChip, new LinearLayout.LayoutParams(0, dp(42), 1f));
        LinearLayout.LayoutParams chip2 = new LinearLayout.LayoutParams(0, dp(42), 1f); chip2.leftMargin = dp(5); smartChips.addView(tomorrowChip, chip2);
        LinearLayout.LayoutParams chip3 = new LinearLayout.LayoutParams(0, dp(42), 1f); chip3.leftMargin = dp(5); smartChips.addView(highChip, chip3);
        LinearLayout.LayoutParams chip4 = new LinearLayout.LayoutParams(0, dp(42), 1f); chip4.leftMargin = dp(5); smartChips.addView(detailsChip, chip4);
        card.addView(smartChips, bottomMargin(dp(7)));

        taskActions = new LinearLayout(this);
        taskActions.setOrientation(LinearLayout.VERTICAL);
        addTaskButton = primaryButton("Add task");
        addTaskButton.setContentDescription("Add this task with no due date and medium priority");
        addTaskButton.setOnClickListener(view -> addQuickTask());
        taskActions.addView(addTaskButton, bottomMargin(dp(5)));

        LinearLayout secondaryTaskActions = new LinearLayout(this);
        secondaryTaskActions.setOrientation(LinearLayout.HORIZONTAL);
        addDetailsButton = compactButton("Add with a date or priority", false);
        addDetailsButton.setOnClickListener(view -> showTaskEditor(null, quickCaptureInput.getText().toString()));
        secondaryTaskActions.addView(addDetailsButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button templatesButton = compactButton("Task templates", false);
        templatesButton.setContentDescription("View, reuse, or create encrypted on-device task templates. Choosing one opens an editable task; it is not created until you confirm Create task.");
        templatesButton.setOnClickListener(view -> showTaskTemplatesDialog());
        LinearLayout.LayoutParams templatesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        templatesParams.leftMargin = dp(6);
        secondaryTaskActions.addView(templatesButton, templatesParams);
        taskActions.addView(secondaryTaskActions, bottomMargin(dp(5)));
        captureFeedback = text(powerMode
                        ? "Power path adds search, filters, and ranked demo suggestions. It uses the same tasks."
                        : "New tasks start with no due date and medium priority. You can change both later.",
                12, palette.muted, Typeface.NORMAL);
        captureFeedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        taskActions.addView(captureFeedback);
        card.addView(taskActions);

        webActions = new LinearLayout(this);
        webActions.setOrientation(LinearLayout.VERTICAL);
        browserOnlineToggle = new CheckBox(this);
        browserOnlineToggle.setText("Online browsing (off by default)");
        browserOnlineToggle.setMinHeight(dp(48));
        browserOnlineToggle.setChecked(browserNetworkPolicy.isOnlineEnabled());
        browserOnlineToggle.setContentDescription(browserNetworkPolicy.isOnlineEnabled()
                ? "Online browsing is enabled. Every search or site still requires a tap. Switch off to block Daymark page and resource loads; Android System WebView Safe Browsing may make separate Google/Play Services checks."
                : "Online browsing is off by default. Turn it on after reviewing the disclosure to allow Daymark page and resource loads, then tap Go or a site to send a request. The switch does not control platform-managed Android System WebView Safe Browsing, which may make Google/Play Services URL-hash or update checks.");
        browserOnlineToggle.setOnCheckedChangeListener((button, checked) -> {
            if (suppressBrowserOnlineToggleListener) return;
            if (!checked) {
                setBrowserOnlineEnabled(false);
                return;
            }
            suppressBrowserOnlineToggleListener = true;
            button.setChecked(false);
            suppressBrowserOnlineToggleListener = false;
            confirmBrowserOnlineAccess();
        });
        browserOnlineToggle.setVisibility(View.GONE);
        LinearLayout providerRow = new LinearLayout(this);
        providerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView providerLabel = text("Search with", 12, palette.muted, Typeface.BOLD);
        providerRow.addView(providerLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        searchEngineSpinner = new Spinner(this);
        String[] engineLabels = new String[BrowserAddress.SearchEngine.values().length];
        for (int index = 0; index < engineLabels.length; index++) {
            engineLabels[index] = BrowserAddress.SearchEngine.values()[index].label;
        }
        ArrayAdapter<String> engineAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, engineLabels);
        engineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        searchEngineSpinner.setAdapter(engineAdapter);
        searchEngineSpinner.setMinimumHeight(dp(48));
        searchEngineSpinner.setContentDescription("Choose a search engine. DuckDuckGo is the default.");
        int engineIndex = java.util.Arrays.asList(engineLabels).indexOf(searchEngine.label);
        searchEngineSpinner.setSelection(Math.max(0, engineIndex));
        searchEngineSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= BrowserAddress.SearchEngine.values().length) return;
                selectSearchEngine(BrowserAddress.SearchEngine.values()[position]);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        providerRow.addView(searchEngineSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        webGoButton = primaryButton("Go");
        webGoButton.setContentDescription("Send this search to the selected provider or open the entered web address");
        webGoButton.setOnClickListener(view -> navigateFromInput());
        providerRow.addView(webGoButton, new LinearLayout.LayoutParams(dp(76), dp(48)));
        webActions.addView(providerRow);
        TextView requestNote = text("Offline by default. Online requests send the query or URL and normal connection data (such as IP address and browser identification) to the chosen destination; pages may contact third parties. The Online switch controls Daymark page/resource loads only. Android System WebView Safe Browsing is a separate platform-managed service that may contact Google/Play Services for threat-list updates or URL-hash checks, depending on WebView/device settings. Daymark sends no task text or app telemetry; WebView diagnostic metrics are opted out. HTTP is blocked.",
                11, palette.muted, Typeface.NORMAL);
        webActions.addView(requestNote, topMargin(dp(3)));
        card.addView(webActions);

        quickCaptureInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (webMode && (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_GO || enter)) {
                navigateFromInput();
                return true;
            }
            if (!webMode && (actionId == EditorInfo.IME_ACTION_DONE || enter)) {
                addQuickTask();
                return true;
            }
            return false;
        });
        quickCaptureInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) {
                if (!webMode) taskDraft = editable == null ? "" : editable.toString();
            }
        });
        return card;
    }

    private View buildBrowserAddressBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(6), dp(5), dp(6), dp(4));
        bar.setBackground(shape(palette.surface, 16, palette.line));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        browserProviderPickerButton = compactButton(searchEngine.label + "  ▾", false);
        browserProviderPickerButton.setTextSize(10 * textScale);
        browserProviderPickerButton.setMinHeight(dp(40));
        browserProviderPickerButton.setMinimumHeight(dp(40));
        browserProviderPickerButton.setMinimumWidth(dp(76));
        browserProviderPickerButton.setMaxWidth(dp(118));
        browserProviderPickerButton.setPadding(dp(5), 0, dp(5), 0);
        browserProviderPickerButton.setBackground(shape(palette.surfaceAlt, 12, palette.surfaceAlt));
        browserProviderPickerButton.setContentDescription("Choose a web search engine or AI assistant");
        browserProviderPickerButton.setOnClickListener(view -> showSearchProviderPicker());
        top.addView(browserProviderPickerButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));

        browserAddressInput = new EditText(this);
        browserAddressInput.setSingleLine(true);
        browserAddressInput.setTextSize(13 * textScale);
        browserAddressInput.setHint("Search or enter HTTPS address");
        browserAddressInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        browserAddressInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        browserAddressInput.setPadding(dp(9), 0, dp(6), 0);
        browserAddressInput.setTextColor(palette.text);
        browserAddressInput.setHintTextColor(palette.muted);
        browserAddressInput.setBackground(shape(palette.surfaceAlt, 14, palette.line));
        browserAddressInput.setContentDescription("Browser address and search field");
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        addressParams.leftMargin = dp(5);
        top.addView(browserAddressInput, addressParams);

        webGoButton = primaryButton("Go");
        webGoButton.setTextSize(12 * textScale);
        webGoButton.setMinHeight(dp(40));
        webGoButton.setMinimumHeight(dp(40));
        webGoButton.setContentDescription("Open the entered web address or search");
        webGoButton.setOnClickListener(v -> navigateFromBrowserInput());
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(dp(48), dp(40));
        goParams.leftMargin = dp(5);
        top.addView(webGoButton, goParams);
        bar.addView(top);

        browserOnlineToggle = new CheckBox(this);
        browserOnlineToggle.setText("Online browsing (off by default)");
        browserOnlineToggle.setVisibility(View.GONE);
        browserAddressInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                navigateFromBrowserInput();
                return true;
            }
            return false;
        });
        return bar;
    }

    private void navigateFromBrowserInput() {
        if (browserAddressInput == null) return;
        String value = browserAddressInput.getText() == null ? "" : browserAddressInput.getText().toString();
        if (value.trim().isEmpty()) return;
        if (!browserNetworkPolicy.allowsRemoteLoads()) {
            showBrowserOfflineStatus();
            return;
        }
        try {
            if (!BrowserAddress.isLikelyWebAddress(value)) lastBrowserSearchQuery = value.trim();
            String address = BrowserAddress.resolveInput(value, searchEngine);
            browserAddressInput.setError(null);
            navigateBrowserTo(address);
        } catch (IllegalArgumentException exception) {
            browserAddressInput.setError(exception.getMessage());
        }
    }

    private LinearLayout buildBrowserScreen() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(buildBrowserAddressBar(), bottomMargin(dp(5)));
        panel.setPadding(dp(8), dp(0), dp(8), dp(2));

        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        browserBackButton = compactButton("‹", false);
        browserBackButton.setContentDescription("Go back one page");
        browserBackButton.setOnClickListener(view -> {
            if (browserWebView != null && browserWebView.canGoBack()) browserWebView.goBack();
        });
        browserForwardButton = compactButton("›", false);
        browserForwardButton.setContentDescription("Go forward one page");
        browserForwardButton.setOnClickListener(view -> {
            if (browserWebView != null && browserWebView.canGoForward()) browserWebView.goForward();
        });
        browserReloadButton = compactButton("↻", false);
        browserReloadButton.setContentDescription("Reload the current page");
        browserReloadButton.setOnClickListener(view -> {
            if (browserWebView == null) return;
            if (browserWebView.getUrl() != null && browserWebView.getProgress() < 100) {
                browserWebView.stopLoading();
            } else {
                browserWebView.reload();
            }
        });
        browserHomeButton = compactButton("⌂", false);
        browserHomeButton.setContentDescription("Return to the local browser home screen");
        browserHomeButton.setOnClickListener(view -> showBrowserHome());
        browserHistoryButton = compactButton("Site history", false);
        browserHistoryButton.setContentDescription("View local site history, which lists HTTPS origins only, or clear site history and site data");
        browserHistoryButton.setOnClickListener(view -> showBrowserHistoryDialog());
        browserSettingsButton = compactButton("Privacy", false);
        browserSettingsButton.setContentDescription("Open Browser Settings to change Safe Browsing protection");
        browserSettingsButton.setOnClickListener(view -> showBrowserSettingsDialog());
        browserOverflowButton = compactButton("⋮", false);
        browserOverflowButton.setContentDescription("Open browser actions menu");
        browserOverflowButton.setOnClickListener(this::showBrowserOverflowMenu);
        browserTabsButton = compactButton("Tabs 0", false);
        browserTabsButton.setContentDescription("View open tabs or create a new tab");
        browserTabsButton.setOnClickListener(view -> showBrowserTabsDialog());
        browserMediaButton = compactButton("⤓", false);
        browserMediaButton.setEnabled(false);
        browserMediaButton.setContentDescription("Find downloadable media, or use the in-video Download button");
        browserMediaButton.setOnClickListener(view -> findMediaOnPage());
        browserExpandButton = compactButton("Expand", false);
        browserExpandButton.setVisibility(View.GONE);
        browserExpandButton.setContentDescription("Open the current web page in a full-screen reader");
        browserExpandButton.setEnabled(false);
        browserExpandButton.setOnClickListener(view -> openFullScreenWebReader());
        Button tasksButton = compactButton("Tasks", false);
        tasksButton.setContentDescription("Return to Daymark tasks");
        tasksButton.setOnClickListener(view -> setWebMode(false));
        for (Button button : Arrays.asList(tasksButton, browserBackButton, browserForwardButton,
                browserReloadButton, browserHomeButton, browserHistoryButton, browserSettingsButton,
                browserOverflowButton, browserTabsButton, browserMediaButton)) {
            button.setMinHeight(dp(38));
            button.setMinimumHeight(dp(38));
            button.setTextSize(12 * textScale);
            button.setPadding(dp(10), 0, dp(10), 0);
            button.setBackground(shape(palette.surfaceAlt, 18, palette.surfaceAlt));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
            params.setMargins(0, 0, dp(4), 0);
            toolbar.addView(button, params);
        }
        toolbarScroll.addView(toolbar);
        panel.addView(toolbarScroll, bottomMargin(dp(2)));

        String disclosureText =
                "Browsing starts when you tap Go, a provider, or site history; Daymark does not request a page in the background. The first network request shows a one-time privacy notice. The selected destination receives your query or URL and normal connection data such as your IP address and browser identification, and may log it; pages may contact and be logged by third-party endpoints. HTTPS only; HTTP is blocked. Android System WebView Safe Browsing is platform-managed and may contact Google/Play Services depending on WebView/device settings; Daymark's browsing disclosure does not claim zero platform traffic. Site history keeps only validated HTTPS origins (scheme, host, and non-default port). Paths, queries, fragments, URL credentials, and page titles are not saved; older entries are reduced to origins when Daymark opens. Selecting a saved site opens its origin, not its last route. Site history is local but not encrypted; use Site history to clear it and Daymark's cookies/cache/storage.";
        browserPrivacyButton = text("ⓘ Privacy & connection details", 11, palette.muted, Typeface.NORMAL);
        browserPrivacyButton.setGravity(Gravity.CENTER_VERTICAL);
        browserPrivacyButton.setPadding(dp(8), 0, dp(8), 0);
        browserPrivacyButton.setBackground(shape(palette.surface, 12, palette.surface));
        browserPrivacyButton.setContentDescription("Browser privacy: page traffic begins only after an explicit Go/provider/history tap and a one-time first-request disclosure. Destination sites may receive query/URL and normal connection data. Android System WebView Safe Browsing is separate and platform-managed. HTTPS only; HTTP is blocked.");
        browserPrivacyButton.setOnClickListener(view -> showInfo("Privacy & connection details", disclosureText));
        panel.addView(browserPrivacyButton, bottomMargin(dp(2)));

        HorizontalScrollView sitesScroll = new HorizontalScrollView(this);
        sitesScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout sites = new LinearLayout(this);
        sites.setOrientation(LinearLayout.HORIZONTAL);
        // Curated AI web providers from the shared catalog. These are normal HTTPS
        // shortcuts, not API integrations; the catalog keeps every label/URL pair in
        // one place so the row and the catalog cannot drift apart.
        for (BrowserAddress.SearchEngine engine : BrowserAddress.SearchEngine.values()) {
            sites.addView(browserProviderButton(engine));
        }
        sitesScroll.addView(sites);
        browserProviderRow = sitesScroll;

        browserStatus = text("Ready. No page has been requested.", 11, palette.muted, Typeface.NORMAL);
        browserStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(browserStatus, bottomMargin(dp(4)));

        browserReaderActionRow = new LinearLayout(this);
        browserReaderActionRow.setOrientation(LinearLayout.HORIZONTAL);
        browserReaderActionRow.setGravity(Gravity.CENTER_VERTICAL);
        browserReaderActionRow.setPadding(dp(2), 0, dp(2), 0);
        browserReaderButton = compactButton("Reader", true);
        browserReaderButton.setContentDescription("Open the current web page in a full-screen reader");
        browserReaderButton.setEnabled(false);
        browserReaderButton.setOnClickListener(view -> openFullScreenWebReader());
        browserReaderActionRow.addView(browserReaderButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        browserReaderActionRow.setVisibility(View.GONE);

        browserViewport = new FrameLayout(this);
        browserViewport.setBackgroundColor(palette.background);
        browserHomeView = buildBrowserHomeView();
        browserViewport.addView(browserHomeView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(browserViewport, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(browserProviderRow, bottomMargin(dp(2)));
        return panel;
    }

    private void selectSearchEngine(BrowserAddress.SearchEngine engine) {
        if (engine == null) return;
        searchEngine = engine;
        browserPreferences.edit().putString(SEARCH_ENGINE_KEY, searchEngine.name()).apply();
        int position = java.util.Arrays.asList(BrowserAddress.SearchEngine.values()).indexOf(engine);
        if (position >= 0) {
            if (searchEngineSpinner != null && searchEngineSpinner.getSelectedItemPosition() != position) {
                searchEngineSpinner.setSelection(position);
            }
            if (browserSearchEngineSpinner != null && browserSearchEngineSpinner.getSelectedItemPosition() != position) {
                browserSearchEngineSpinner.setSelection(position);
            }
        }
        if (browserProviderPickerButton != null) {
            browserProviderPickerButton.setText(engine.label + "  ▾");
            browserProviderPickerButton.setContentDescription("Search provider: " + engine.label + ". Tap to change provider.");
        }
    }

    private boolean isAiProvider(BrowserAddress.SearchEngine engine) {
        switch (engine) {
            case CHATGPT: case PERPLEXITY: case GEMINI: case CLAUDE: case COPILOT:
            case GROK: case DEEPSEEK: case PHIND: case KIMI: case YOU_COM_AI:
            case META_AI: case MISTRAL: case POE: case HUGGINGCHAT: case DUCK_AI:
            case QWEN_CHAT: case CHARACTER_AI:
                return true;
            default:
                return false;
        }
    }

    private void showSearchProviderPicker() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText filter = new EditText(this);
        filter.setSingleLine(true);
        filter.setTextSize(14 * textScale);
        filter.setHint("Search engines and AI assistants");
        filter.setPadding(dp(12), 0, dp(12), 0);
        filter.setTextColor(palette.text);
        filter.setHintTextColor(palette.muted);
        filter.setBackground(shape(palette.surfaceAlt, 14, palette.line));
        root.addView(filter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        String[] categories = {"All", "Web search", "AI assistants"};
        final String[] selectedCategory = {"All"};
        final LinearLayout[] rowsHost = {null};
        final AlertDialog[] dialogRef = {null};
        for (String category : categories) {
            Button tab = compactButton(category, false);
            tab.setTextSize(11 * textScale);
            tab.setMinHeight(dp(36));
            tab.setMinimumHeight(dp(36));
            tab.setPadding(dp(8), 0, dp(8), 0);
            tab.setBackground(shape(palette.surfaceAlt, 16, palette.surfaceAlt));
            tab.setOnClickListener(view -> {
                selectedCategory[0] = category;
                renderSearchProviderRows(rowsHost[0], filter.getText().toString(), selectedCategory[0], dialogRef[0]);
                for (int i = 0; i < tabs.getChildCount(); i++) {
                    View child = tabs.getChildAt(i);
                    if (child instanceof Button) {
                        String name = ((Button) child).getText().toString();
                        child.setBackground(shape(name.equals(category) ? palette.accentSoft : palette.surfaceAlt,
                                16, name.equals(category) ? palette.accentSoft : palette.surfaceAlt));
                    }
                }
            });
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            tabParams.setMargins(0, dp(8), dp(6), dp(8));
            tabs.addView(tab, tabParams);
        }
        root.addView(tabs);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        rowsHost[0] = new LinearLayout(this);
        rowsHost[0].setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rowsHost[0]);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(390)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Choose search provider")
                .setView(root)
                .setNegativeButton("Close", null)
                .create();
        dialogRef[0] = dialog;
        renderSearchProviderRows(rowsHost[0], "", selectedCategory[0], dialog);
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderSearchProviderRows(rowsHost[0], s.toString(), selectedCategory[0], dialog);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        dialog.show();
    }

    private void renderSearchProviderRows(LinearLayout host, String query, String category, AlertDialog dialog) {
        if (host == null) return;
        host.removeAllViews();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (BrowserAddress.SearchEngine engine : BrowserAddress.SearchEngine.values()) {
            boolean ai = isAiProvider(engine);
            if ("Web search".equals(category) && ai) continue;
            if ("AI assistants".equals(category) && !ai) continue;
            if (!engine.label.toLowerCase(Locale.ROOT).contains(normalized)) continue;
            TextView row = text((engine == searchEngine ? "●  " : "○  ") + engine.label,
                    14, engine == searchEngine ? palette.accent : palette.text, Typeface.NORMAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(12), 0);
            row.setMinHeight(dp(44));
            row.setBackground(shape(palette.surface, 10, palette.surface));
            row.setContentDescription("Select " + engine.label + (engine == searchEngine ? ", currently selected" : ""));
            row.setOnClickListener(view -> {
                selectSearchEngine(engine);
                dialog.dismiss();
            });
            host.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        }
        if (host.getChildCount() == 0) {
            TextView empty = text("No matching providers", 13, palette.muted, Typeface.NORMAL);
            empty.setPadding(dp(12), dp(14), dp(12), dp(14));
            host.addView(empty);
        }
    }

    private Button browserProviderButton(BrowserAddress.SearchEngine engine) {
        Button button = compactButton(engine.label, false);
        button.setMinHeight(dp(32));
        button.setMinimumHeight(dp(32));
        button.setTextSize(10 * textScale);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(shape(engine == searchEngine ? palette.accentSoft : palette.surfaceAlt, 16, palette.surfaceAlt));
        button.setContentDescription("Search this query with " + engine.label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        params.setMargins(0, 0, dp(5), 0);
        button.setLayoutParams(params);
        button.setOnClickListener(view -> {
            if (!webMode) return;
            String typed = browserAddressInput == null || browserAddressInput.getText() == null
                    ? "" : browserAddressInput.getText().toString().trim();
            String currentUrl = browserWebView == null ? "" : browserWebView.getUrl();
            String query = (!lastBrowserSearchQuery.isEmpty() && !typed.isEmpty() && typed.equals(currentUrl))
                    ? lastBrowserSearchQuery : typed;
            selectSearchEngine(engine);
            if (query.isEmpty() || BrowserAddress.isLikelyWebAddress(query)) {
                browserStatus.setText("Provider changed to " + engine.label + ". Enter a search and tap Go to submit it.");
                return;
            }
            lastBrowserSearchQuery = query;
            try {
                String target = BrowserAddress.requireAllowedWebUrl(engine.searchUrl(query));
                browserAddressInput.setText(query);
                navigateBrowserTo(target);
            } catch (IllegalArgumentException invalidQuery) {
                browserStatus.setText("Nothing was opened. Check the search query.");
            }
        });
        return button;
    }

    private Button browserSiteButton(AiSiteCatalog.Entry entry) {
        Button button = compactButton(entry.label, false);
        button.setMinHeight(dp(34));
        button.setMinimumHeight(dp(34));
        button.setTextSize(11 * textScale);
        button.setPadding(dp(9), 0, dp(9), 0);
        button.setBackground(shape(palette.surfaceAlt, 17, palette.surfaceAlt));
        button.setContentDescription("Ask " + entry.label + " with the current search, or open its website if no question is entered");
        button.setOnClickListener(view -> {
            if (!webMode) return;
            if (!browserNetworkPolicy.allowsRemoteLoads()) {
                showBrowserOfflineStatus();
                return;
            }
            String value = browserAddressInput == null || browserAddressInput.getText() == null
                    ? "" : browserAddressInput.getText().toString().trim();
            selectSearchEngine(entry.searchEngine);
            String address = entry.httpsUrl;
            if (!value.isEmpty() && !BrowserAddress.isLikelyWebAddress(value)) {
                lastBrowserSearchQuery = value;
                try {
                    address = BrowserAddress.requireAllowedWebUrl(entry.searchEngine.searchUrl(value));
                } catch (IllegalArgumentException exception) {
                    if (browserAddressInput != null) browserAddressInput.setError(exception.getMessage());
                    browserStatus.setText("Nothing was opened. Check the question or provider address.");
                    return;
                }
            }
            if (browserAddressInput != null) browserAddressInput.setError(null);
            navigateBrowserTo(address);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        params.setMargins(0, 0, dp(5), 0);
        button.setLayoutParams(params);
        return button;
    }

    private View buildBrowserHomeView() {
        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setGravity(Gravity.CENTER);
        home.setPadding(dp(24), dp(20), dp(24), dp(20));

        TextView title = text("Daymark", 28, palette.text, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        home.addView(title, bottomMargin(dp(4)));

        TextView tagline = text("Your browser, your way", 14, palette.muted, Typeface.NORMAL);
        tagline.setGravity(Gravity.CENTER);
        home.addView(tagline, bottomMargin(dp(22)));

        TextView hint = text("Search the web or ask an AI", 17, palette.text, Typeface.BOLD);
        hint.setGravity(Gravity.CENTER);
        home.addView(hint, bottomMargin(dp(7)));

        TextView detail = text(
                "Type a question in the address bar above, then tap a provider to search in one step. Or choose a provider first.",
                13, palette.muted, Typeface.NORMAL);
        detail.setGravity(Gravity.CENTER);
        detail.setLineSpacing(dp(3), 1f);
        home.addView(detail, bottomMargin(dp(18)));

        Button chooseProvider = compactButton("Choose search provider", true);
        chooseProvider.setOnClickListener(view -> showSearchProviderPicker());
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        chooseParams.gravity = Gravity.CENTER_HORIZONTAL;
        home.addView(chooseProvider, chooseParams);

        TextView privacyHint = text("Your query goes to the provider you choose. A one-time notice appears before the first connection.", 11,
                palette.muted, Typeface.NORMAL);
        privacyHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyParams.topMargin = dp(16);
        home.addView(privacyHint, privacyParams);
        return home;
    }

    private void navigateFromInput() {
        if (!webMode) return;
        if (!browserNetworkPolicy.allowsRemoteLoads()) {
            showBrowserOfflineStatus();
            return;
        }
        String input = quickCaptureInput.getText() == null ? "" : quickCaptureInput.getText().toString();
        try {
            if (!BrowserAddress.isLikelyWebAddress(input)) lastBrowserSearchQuery = input.trim();
            String address = BrowserAddress.resolveInput(input, searchEngine);
            quickCaptureInput.setError(null);
            navigateBrowserTo(address);
        } catch (IllegalArgumentException exception) {
            quickCaptureInput.setError(exception.getMessage());
            browserStatus.setText("Nothing was opened. Check the search or web address.");
        }
    }

    private void navigateBrowserTo(String address) {
        if (!webMode) return;
        if (!browserNetworkPolicy.allowsRemoteLoads()) {
            showBrowserOfflineStatus();
            return;
        }
        final String safeAddress;
        try {
            safeAddress = BrowserAddress.requireAllowedWebUrl(address);
        } catch (IllegalArgumentException exception) {
            quickCaptureInput.setError(exception.getMessage());
            return;
        }
        if (!browserPreferences.getBoolean(BROWSER_NETWORK_DISCLOSURE_ACCEPTED_KEY, false)) {
            final String pendingAddress = safeAddress;
            new AlertDialog.Builder(this)
                    .setTitle("Before you browse")
                    .setMessage("The selected search provider or website receives the query or URL and ordinary connection data such as your IP address and browser identification, and may log it. Pages may contact additional endpoints. Android System WebView Safe Browsing is separate and may contact Google/Play Services depending on the device and WebView version. Daymark blocks insecure HTTP and does not send task text. Continue only if you agree to connect.")
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        if (browserStatus != null) browserStatus.setText("No page was requested.");
                    })
                    .setPositiveButton("Continue", (dialog, which) -> {
                        browserPreferences.edit().putBoolean(BROWSER_NETWORK_DISCLOSURE_ACCEPTED_KEY, true).apply();
                        loadBrowserAddress(pendingAddress);
                    })
                    .show();
            return;
        }
        loadBrowserAddress(safeAddress);
    }

    private void loadBrowserAddress(String address) {
        if (!webMode) return;
        if (!browserNetworkPolicy.allowsRemoteLoads()) {
            showBrowserOfflineStatus();
            return;
        }
        if (!ensureBrowserWebView()) {
            showBrowserOfflineStatus();
            return;
        }
        try {
            browserWebView.getSettings().setBlockNetworkLoads(false);
        } catch (SecurityException denied) {
            setBrowserOnlineEnabled(false);
            if (browserOnlineToggle != null) browserOnlineToggle.setChecked(false);
            browserStatus.setText("Online access is unavailable. No page was opened; Daymark remains offline.");
            return;
        }
        browserHomeView.setVisibility(View.GONE);
        if (browserProviderRow != null) browserProviderRow.setVisibility(View.VISIBLE);
        if (browserPrivacyButton != null) browserPrivacyButton.setVisibility(View.GONE);
        browserWebView.setVisibility(View.VISIBLE);
        browserStatus.setText("Loading page… 0%. Its provider and page resources may receive requests.");
        browserWebView.loadUrl(address);
        syncBrowserButtons();
    }

    private boolean ensureBrowserWebView() {
        if (!browserNetworkPolicy.allowsRemoteLoads()) return false;
        if (browserWebView != null) return true;
        if (activeBrowserTabIndex >= 0 && activeBrowserTabIndex < browserTabs.size()) {
            browserWebView = browserTabs.get(activeBrowserTabIndex);
            if (browserWebView.getParent() instanceof ViewGroup) {
                ((ViewGroup) browserWebView.getParent()).removeView(browserWebView);
            }
            browserViewport.addView(browserWebView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            browserWebView.setVisibility(browserWebView.getUrl() == null ? View.GONE : View.VISIBLE);
            updateBrowserTabsButton();
            return true;
        }
        final DaymarkWebView[] tabRef = new DaymarkWebView[1];
        DaymarkWebView created = new DaymarkWebView(this, browserNetworkPolicy,
                browserSettingsPolicy.isSafeBrowsingEnabled(), new DaymarkWebView.Listener() {
            private boolean isActiveTab() {
                return isActivityCallbackCurrent() && browserWebView == tabRef[0];
            }
            @Override public void onPageStarted(String url) {
                if (!isActiveTab()) return;
                browserStatus.setText("Loading page. Embedded resources may also make network requests.");
                syncBrowserButtons();
            }
            @Override public void onPageFinished(String url) {
                if (extensionRuntime != null) extensionRuntime.onPageFinished(tabRef[0], url);
                if (!isActiveTab()) return;
                if (browserAddressInput != null) browserAddressInput.setText(url);
                String safeHistoryUrl = BrowserHistory.sanitizeUrl(url);
                if (browserNetworkPolicy.allowsRemoteLoads() && safeHistoryUrl != null) {
                    String current = browserPreferences.getString(BROWSER_HISTORY_KEY, "");
                    browserPreferences.edit().putString(BROWSER_HISTORY_KEY,
                            BrowserHistory.add(current, safeHistoryUrl)).apply();
                }
                browserStatus.setText("Page loaded. Website content may contact its own or third-party endpoints.");
                installBrowserMediaOverlay();
                syncBrowserButtons();
            }
            @Override public void onNavigationBlocked(String url) {
                if (!isActiveTab()) return;
                browserStatus.setText("A non-HTTPS page link was blocked. Use HTTPS; a per-site HTTP exception requires a separate explicit request.");
                showToast("Only HTTPS pages open here. HTTP is blocked; site exceptions need a separate request.");
            }
            @Override public void onOfflineNavigationBlocked() {
                if (isActiveTab()) postActivityCallback(MainActivity.this::showBrowserOfflineStatus);
            }
            @Override public void onHttpNavigationBlocked(String url, boolean redirect) {
                if (!isActiveTab()) return;
                String message = redirect
                        ? "An HTTP redirect/downgrade was blocked. No insecure page was opened."
                        : "An HTTP page navigation was blocked. No insecure page was opened.";
                postActivityCallback(() -> {
                    if (!isActiveTab()) return;
                    browserStatus.setText(message + " Only HTTPS is supported. A per-site exception requires a separate explicit request.");
                    showToast("Insecure HTTP navigation blocked. Use HTTPS instead.");
                });
            }
            @Override public void onLoadError(String description) {
                if (!isActiveTab()) return;
                String reason = description == null || description.trim().isEmpty() ? "Unknown network error." : description.trim();
                if (reason.length() > 120) reason = reason.substring(0, 120);
                browserStatus.setText("Page failed to load: " + reason + " · Tap Reload to retry.");
                syncBrowserButtons();
            }
            @Override public void onHttpError(int statusCode) {
                if (!isActiveTab()) return;
                browserStatus.setText("The website returned HTTP " + statusCode + ". It may be unavailable or restrict this request. Tap Reload to retry.");
            }
            @Override public void onProgressChanged(int progress) {
                if (!isActiveTab()) return;
                if (progress < 100) browserStatus.setText("Loading page… " + progress + "%");
                else if (browserWebView.getUrl() != null) browserStatus.setText("Page ready. If blank, tap Reload or try another provider.");
                syncBrowserButtons();
            }
            @Override public void onShowCustomView(View view, android.webkit.WebChromeClient.CustomViewCallback callback) {
                if (!isActiveTab()) { if (callback != null) callback.onCustomViewHidden(); return; }
                showFullScreenVideo(view, callback);
            }
            @Override public void onHideCustomView() {
                if (!isActiveTab()) return;
                if (fullScreenVideoView != null) hideFullScreenVideo(false);
            }
            @Override public void onDownloadRequested(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                if (!isActiveTab()) return;
                queueBrowserDownload(url, userAgent, contentDisposition, mimeType);
            }
            @Override public void onRendererGone() {
                if (!isActiveTab()) return;
                discardBrowserWebView(false);
                if (browserHomeView != null) browserHomeView.setVisibility(View.VISIBLE);
                if (browserProviderRow != null) browserProviderRow.setVisibility(View.VISIBLE);
                if (browserPrivacyButton != null) browserPrivacyButton.setVisibility(View.VISIBLE);
                browserStatus.setText("The page stopped unexpectedly. Open a new tab or try again.");
            }
        });
        tabRef[0] = created;
        browserWebView = created;
        if (activeBrowserTabIndex < 0 || activeBrowserTabIndex >= browserTabs.size()) {
            browserTabs.add(created);
            activeBrowserTabIndex = browserTabs.size() - 1;
        } else {
            browserTabs.set(activeBrowserTabIndex, created);
        }
        browserWebView.setBackgroundColor(palette.surface);
        browserWebView.setVisibility(View.GONE);
        browserViewport.addView(browserWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateBrowserTabsButton();
        return true;
    }

    private void updateBrowserTabsButton() {
        if (browserTabsButton != null) {
            browserTabsButton.setText("Tabs " + browserTabs.size());
            browserTabsButton.setContentDescription("View " + browserTabs.size() + " open tabs or create a new tab");
        }
    }

    private void showBrowserTabsDialog() {
        if (browserTabs.isEmpty()) {
            createBrowserTab();
            return;
        }
        String[] labels = new String[browserTabs.size()];
        for (int i = 0; i < browserTabs.size(); i++) {
            DaymarkWebView tab = browserTabs.get(i);
            String title = tab.getTitle();
            if (title == null || title.trim().isEmpty()) title = tab.getUrl();
            if (title == null || title.trim().isEmpty()) title = "New tab";
            if (title.length() > 48) title = title.substring(0, 45) + "...";
            labels[i] = (i == activeBrowserTabIndex ? "✓  " : "") + title;
        }
        new AlertDialog.Builder(this)
                .setTitle("Open tabs")
                .setItems(labels, (dialog, selected) -> switchBrowserTab(selected))
                .setNeutralButton("New tab", (dialog, which) -> createBrowserTab())
                .setPositiveButton("Close", null)
                .show();
    }

    private void createBrowserTab() {
        if (!browserNetworkPolicy.allowsRemoteLoads()) {
            showBrowserOfflineStatus();
            return;
        }
        if (browserWebView != null) {
            browserWebView.setVisibility(View.GONE);
            if (browserWebView.getParent() instanceof ViewGroup) {
                ((ViewGroup) browserWebView.getParent()).removeView(browserWebView);
            }
            browserWebView = null;
        }
        activeBrowserTabIndex = browserTabs.size();
        if (!ensureBrowserWebView()) return;
        browserHomeView.setVisibility(View.VISIBLE);
        browserProviderRow.setVisibility(View.VISIBLE);
        browserPrivacyButton.setVisibility(View.VISIBLE);
        if (browserAddressInput != null) browserAddressInput.setText("");
        browserStatus.setText("New tab. Enter a search or HTTPS address.");
        syncBrowserButtons();
        updateBrowserTabsButton();
    }

    private void switchBrowserTab(int index) {
        if (index < 0 || index >= browserTabs.size()) return;
        if (index == activeBrowserTabIndex && browserWebView != null) return;
        if (browserWebView != null) {
            browserWebView.setVisibility(View.GONE);
            if (browserWebView.getParent() instanceof ViewGroup) {
                ((ViewGroup) browserWebView.getParent()).removeView(browserWebView);
            }
            browserWebView = null;
        }
        activeBrowserTabIndex = index;
        if (!ensureBrowserWebView()) return;
        boolean hasPage = browserWebView.getUrl() != null && !browserWebView.getUrl().isEmpty();
        browserWebView.setVisibility(hasPage ? View.VISIBLE : View.GONE);
        browserHomeView.setVisibility(hasPage ? View.GONE : View.VISIBLE);
        browserProviderRow.setVisibility(View.VISIBLE);
        browserPrivacyButton.setVisibility(hasPage ? View.GONE : View.VISIBLE);
        if (browserAddressInput != null) browserAddressInput.setText(hasPage ? browserWebView.getUrl() : "");
        browserStatus.setText(hasPage ? "Tab restored: " + browserWebView.getTitle() : "New tab. Enter a search or HTTPS address.");
        syncBrowserButtons();
        updateBrowserTabsButton();
    }

    private void destroyAllBrowserTabs(boolean stopLoading) {
        if (fullScreenWebDialog != null) {
            fullScreenWebDialog.setOnDismissListener(null);
            if (fullScreenWebDialog.isShowing()) fullScreenWebDialog.dismiss();
            fullScreenWebDialog = null;
        }
        for (DaymarkWebView tab : new ArrayList<>(browserTabs)) {
            try { tab.getSettings().setBlockNetworkLoads(true); } catch (RuntimeException ignored) { }
            if (stopLoading) tab.stopLoading();
            if (tab.getParent() instanceof ViewGroup) ((ViewGroup) tab.getParent()).removeView(tab);
            tab.destroy();
        }
        browserTabs.clear();
        browserWebView = null;
        activeBrowserTabIndex = -1;
        updateBrowserTabsButton();
        syncBrowserButtons();
    }

    private void showBrowserOverflowMenu(View anchor) {
        final String[] actions = {
                "Reload / Stop", "Find in page", "Share page", "Copy page URL",
                "Open in external browser", "Desktop site", "Downloads", "Extensions", "Browser settings"
        };
        new AlertDialog.Builder(this)
                .setTitle("Browser actions")
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            if (browserWebView != null && browserWebView.getUrl() != null) {
                                if (browserWebView.getProgress() < 100) browserWebView.stopLoading();
                                else browserWebView.reload();
                            }
                            break;
                        case 1:
                            showFindInPageDialog();
                            break;
                        case 2:
                            shareCurrentBrowserUrl();
                            break;
                        case 3:
                            copyCurrentBrowserUrl();
                            break;
                        case 4:
                            openCurrentBrowserExternally();
                            break;
                        case 5:
                            toggleDesktopSite();
                            break;
                        case 6:
                            openDownloadsFolder();
                            break;
                        case 7:
                            showBrowserExtensionsManager();
                            break;
                        case 8:
                            showBrowserSettingsDialog();
                            break;
                        default:
                            break;
                    }
                }).show();
    }

    private void importExtensionFromUri(Uri uri) {
        if (uri == null) return;
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("Unable to open extension.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > 5 * 1024 * 1024) {
                    throw new IOException("Extension archive is too large.");
                }
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            String lowerName = uri.toString().toLowerCase(Locale.ROOT);
            BrowserExtension ext;
            if (lowerName.endsWith(".zip") || lowerName.endsWith(".xpi") || lowerName.endsWith(".crx")) {
                ext = ExtensionPackageParser.parseWebExtensionArchive(bytes);
            } else {
                String raw = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                if (lowerName.endsWith(".user.js") || raw.contains("==UserScript==")) {
                    ext = ExtensionPackageParser.parseUserScript(raw);
                } else if (raw.trim().startsWith("{")) {
                    ext = ExtensionPackageParser.parseDaymarkJson(raw, false);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported extension. Use .daymark-ext.json, compatible userscript, ZIP/XPI, or CRX3.");
                }
            }
            new ExtensionStore(this).installUserPack(ext);
            showToast("Extension added: " + ext.name);
            showBrowserExtensionsManager();
        } catch (Exception exception) {
            showToast("Extension rejected: " + exception.getMessage());
        }
    }

    private void showBrowserExtensionsManager() {
        if (extensionRuntime == null) return;
        List<BrowserExtension> extensions = extensionRuntime.store().listAll();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(8));

        TextView disclosure = text(
                "Extensions run only inside Daymark's HTTPS WebView. They cannot access tasks, encryption keys, or app-private data. " +
                "Chrome/Firefox privileged APIs, network interception, and GM_* storage are not available.",
                12, palette.muted, Typeface.NORMAL);
        disclosure.setLineSpacing(dp(2), 1f);
        content.addView(disclosure, bottomMargin(dp(10)));

        CheckBox masterSwitch = new CheckBox(this);
        masterSwitch.setText("Run extensions in the browser");
        masterSwitch.setTextColor(palette.text);
        masterSwitch.setTextSize(14 * textScale);
        masterSwitch.setChecked(extensionRuntime.store().isGloballyEnabled());
        masterSwitch.setContentDescription("Global extension switch. When off, no extension is injected into any page.");
        masterSwitch.setOnCheckedChangeListener((button, checked) -> {
            extensionRuntime.store().setGloballyEnabled(checked);
            showToast(checked ? "Extensions enabled."
                    : "Extensions paused. Nothing will be injected until you turn this back on.");
        });
        content.addView(masterSwitch, bottomMargin(dp(6)));
        if (!extensionRuntime.store().isGloballyEnabled()) {
            TextView paused = text("Extensions are paused. Nothing is injected into pages until you turn the switch back on.",
                    12, palette.warning, Typeface.NORMAL);
            paused.setLineSpacing(dp(2), 1f);
            content.addView(paused, bottomMargin(dp(8)));
        }

        if (extensions.isEmpty()) {
            content.addView(text("No extensions installed.", 13, palette.muted, Typeface.NORMAL),
                    bottomMargin(dp(8)));
        }
        for (BrowserExtension ext : extensions) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10), dp(9), dp(10), dp(9));
            row.setBackground(shape(palette.surface, 10, palette.line));

            CheckBox enabled = new CheckBox(this);
            enabled.setText(ext.name + (ext.builtIn ? " · built-in" : " · user pack"));
            enabled.setTextColor(palette.text);
            enabled.setTextSize(14 * textScale);
            enabled.setChecked(ext.enabled);
            enabled.setContentDescription((ext.enabled ? "Disable " : "Enable ") + ext.name);
            enabled.setOnCheckedChangeListener((button, checked) -> {
                if (ext.builtIn) extensionRuntime.store().setBuiltinEnabled(ext.id, checked);
                else extensionRuntime.store().setUserPackEnabled(ext.id, checked);
                showToast((checked ? "Enabled: " : "Disabled: ") + ext.name);
            });
            row.addView(enabled);

            String detail = "v" + ext.version + " · " +
                    (ext.matches.isEmpty() ? "all HTTPS pages" : ext.matches.size() + " match rule(s)");
            if (ext.warnings != null && !ext.warnings.isEmpty()) detail += "\nWarning: " + ext.warnings;
            TextView description = text(
                    (ext.description.isEmpty() ? detail : ext.description + "\n" + detail),
                    12, ext.warnings.isEmpty() ? palette.muted : palette.warning, Typeface.NORMAL);
            description.setLineSpacing(dp(2), 1f);
            row.addView(description, topMargin(dp(2)));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button details = compactButton("Details", false);
            details.setContentDescription("Show permissions, match rules, and warnings for " + ext.name);
            details.setOnClickListener(view -> showExtensionDetailsDialog(ext));
            actions.addView(details, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
            Button sites = compactButton("Sites", false);
            sites.setContentDescription("Choose sites where " + ext.name + " is paused");
            sites.setOnClickListener(view -> showExtensionSitesDialog(ext));
            actions.addView(sites, chipMargin());
            if (!ext.builtIn) {
                Button export = compactButton("Export", false);
                export.setContentDescription("Export extension " + ext.name + " as a Daymark pack file");
                export.setOnClickListener(view -> exportExtensionPack(ext));
                actions.addView(export, chipMargin());
                Button remove = compactButton("Remove", true);
                remove.setContentDescription("Remove extension " + ext.name);
                remove.setOnClickListener(view -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Remove extension?")
                            .setMessage("Remove " + ext.name + " from this device?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Remove", (d, w) -> {
                                extensionRuntime.store().uninstallUserPack(ext.id);
                                showToast("Extension removed.");
                                showBrowserExtensionsManager();
                            }).show();
                });
                actions.addView(remove, chipMargin());
            }
            row.addView(actions, topMargin(dp(5)));
            content.addView(row, bottomMargin(dp(7)));
        }

        Button importButton = primaryButton("Add extension / userscript");
        importButton.setContentDescription("Import a Daymark extension pack or compatible userscript from device storage");
        importButton.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_IMPORT_EXTENSION);
        });
        content.addView(importButton, topMargin(dp(4)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("Extensions")
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showExtensionDetailsDialog(BrowserExtension ext) {
        if (ext == null) return;
        StringBuilder details = new StringBuilder();
        details.append("Name: ").append(ext.name).append('\n');
        details.append("Version: ").append(ext.version).append('\n');
        details.append("Source: ").append(ext.builtIn ? "Built-in" : "User pack").append('\n');
        details.append("ID: ").append(ext.id).append('\n');
        details.append("State: ").append(ext.enabled ? "Enabled" : "Disabled").append('\n');
        details.append("Runs at: ").append("document_start".equals(ext.runAt) ? "document start" : "document end").append('\n');
        details.append("CSS: ").append(ext.css.length()).append(" chars · JS: ").append(ext.js.length()).append(" chars").append('\n');
        details.append('\n');
        details.append("Match rules (").append(ext.matches.size()).append("):").append('\n');
        for (String match : ext.matches) details.append("  · ").append(match).append('\n');
        if (!ext.excludes.isEmpty()) {
            details.append("Excludes (").append(ext.excludes.size()).append("):").append('\n');
            for (String exclude : ext.excludes) details.append("  · ").append(exclude).append('\n');
        }
        if (!ext.disabledSites.isEmpty()) {
            details.append("Paused on sites (").append(ext.disabledSites.size()).append("):").append('\n');
            for (String site : ext.disabledSites) details.append("  · ").append(site).append('\n');
        }
        details.append('\n');
        details.append("Permissions: none. This extension cannot access tasks, encryption keys, or app-private data. " +
                "Chrome/Firefox privileged APIs, network interception, and GM_* storage are not available.");
        if (!ext.warnings.isEmpty()) {
            details.append("\n\nWarnings: ").append(ext.warnings);
        }
        TextView body = text(details.toString(), 13, palette.text, Typeface.NORMAL);
        body.setLineSpacing(dp(2), 1f);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        content.addView(body);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("Extension details · " + ext.name)
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showExtensionSitesDialog(BrowserExtension ext) {
        if (ext == null || extensionRuntime == null) return;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        TextView explanation = text(
                "This extension is paused on the sites below (the host and its subdomains). Add a site like example.com or https://example.com/page.",
                12, palette.muted, Typeface.NORMAL);
        explanation.setLineSpacing(dp(2), 1f);
        content.addView(explanation, bottomMargin(dp(8)));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        final Runnable[] rerender = new Runnable[1];
        rerender[0] = () -> {
            list.removeAllViews();
            BrowserExtension current = null;
            for (BrowserExtension candidate : extensionRuntime.store().listAll()) {
                if (candidate.id.equals(ext.id)) current = candidate;
            }
            if (current == null || current.disabledSites.isEmpty()) {
                list.addView(text("Not paused on any site.", 12, palette.muted, Typeface.NORMAL));
                return;
            }
            for (String site : current.disabledSites) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView host = text(site, 14, palette.text, Typeface.NORMAL);
                row.addView(host, new LinearLayout.LayoutParams(0, dp(48), 1f));
                Button remove = compactButton("Remove", true);
                remove.setContentDescription("Resume " + ext.name + " on " + site);
                remove.setOnClickListener(view -> {
                    extensionRuntime.store().setSiteDisabled(ext.id, site, false);
                    showToast("Extension will run on " + site + " again.");
                    rerender[0].run();
                });
                row.addView(remove, new LinearLayout.LayoutParams(dp(96), dp(48)));
                list.addView(row, bottomMargin(dp(4)));
            }
        };
        rerender[0].run();
        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText siteInput = new EditText(this);
        siteInput.setHint("example.com");
        siteInput.setSingleLine(true);
        siteInput.setTextSize(14 * textScale);
        siteInput.setContentDescription("Site where this extension should be paused");
        addRow.addView(siteInput, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button addSite = compactButton("Pause", false);
        addSite.setContentDescription("Pause " + ext.name + " on this site");
        addSite.setOnClickListener(view -> {
            String input = siteInput.getText() == null ? "" : siteInput.getText().toString();
            String host = BrowserExtension.normalizeSiteHost(input);
            if (host == null) {
                showToast("Enter a site like example.com or https://example.com/page.");
                return;
            }
            extensionRuntime.store().setSiteDisabled(ext.id, host, true);
            siteInput.setText("");
            showToast("Extension paused on " + host + ".");
            rerender[0].run();
        });
        addRow.addView(addSite, chipMargin());
        content.addView(addRow, topMargin(dp(6)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("Paused sites · " + ext.name)
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }

    private void exportExtensionPack(BrowserExtension ext) {
        if (ext == null || ext.builtIn || extensionRuntime == null) return;
        if (extensionRuntime.store().userPackJson(ext.id) == null) {
            showToast("This extension could not be exported.");
            return;
        }
        pendingExtensionExportId = ext.id;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, ext.id.replaceAll("[^A-Za-z0-9._-]", "_") + ".daymark-ext.json");
        startActivityForResult(intent, REQUEST_EXPORT_EXTENSION);
    }

    private void showFindInPageDialog() {
        if (browserWebView == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Find text on this page");
        new AlertDialog.Builder(this).setTitle("Find in page").setView(input)
                .setPositiveButton("Find", (d, w) -> {
                    String query = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!query.isEmpty()) browserWebView.findAllAsync(query);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void shareCurrentBrowserUrl() {
        String url = browserWebView == null ? null : browserWebView.getUrl();
        if (url == null || !BrowserAddress.isAllowedWebUrl(url)) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(intent, "Share page"));
    }

    private void copyCurrentBrowserUrl() {
        String url = browserWebView == null ? null : browserWebView.getUrl();
        if (url == null || !BrowserAddress.isAllowedWebUrl(url)) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Page URL", url));
        showToast("Page URL copied.");
    }

    private void openCurrentBrowserExternally() {
        String url = browserWebView == null ? null : browserWebView.getUrl();
        if (url == null || !BrowserAddress.isAllowedWebUrl(url)) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception exception) {
            showToast("No browser is available.");
        }
    }

    private void toggleDesktopSite() {
        if (browserWebView == null) return;
        android.webkit.WebSettings settings = browserWebView.getSettings();
        String current = settings.getUserAgentString();
        boolean desktop = current != null && current.contains("X11");
        String mobile = android.webkit.WebSettings.getDefaultUserAgent(this);
        settings.setUserAgentString(desktop ? mobile : mobile.replace("Mobile", "X11").replace("Android", "Linux"));
        browserWebView.reload();
    }

    private void openDownloadsFolder() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivity(intent);
        } catch (Exception exception) {
            showToast("System file picker is unavailable.");
        }
    }

    private void queueBrowserDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        if (!BrowserAddress.isAllowedWebUrl(url)) {
            showToast("Download blocked: HTTPS is required.");
            return;
        }
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager == null) {
            showToast("Downloads are unavailable on this device.");
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(filename);
            request.setDescription("Daymark browser download");
            if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);
            if (userAgent != null && !userAgent.isEmpty()) request.addRequestHeader("User-Agent", userAgent);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (Build.VERSION.SDK_INT >= 29) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            } else {
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename);
            }
            manager.enqueue(request);
            showToast(Build.VERSION.SDK_INT >= 29
                    ? "Download started. Check Downloads for progress."
                    : "Download started. Check Daymark's app Downloads folder.");
        } catch (Exception exception) {
            showToast("Download could not be started.");
        }
    }

    // UC-style media grabber: a one-shot read-only DOM probe (no JS bridge, no
    // request interception) that lists direct video/audio sources and media
    // links on the current page so they can be downloaded even when the site
    // itself offers no download button. Protected streaming players (DRM or
    // hidden stream manifests) stay out of scope by design.
    private void installBrowserMediaOverlay() {
        if (browserWebView == null) return;
        String script = "(function(){try{"
                + "if(window.__daymarkMediaOverlayInstalled)return;window.__daymarkMediaOverlayInstalled=true;"
                + "function safe(u,t){try{var x=new URL(u,location.href);var p=x.pathname.toLowerCase();"
                + "if(x.protocol!=='https:'||p.endsWith('.m3u8')||p.endsWith('.mpd'))return '';"
                + "if(!/.(mp4|webm|m4v|mov|mkv)$/i.test(p)&&t!=='video/mp4'&&t!=='video/webm')return '';return x.href;}catch(e){return '';}}"
                + "function choices(v){var r=[],seen={};function add(u,l,t){u=safe(u,t);if(!u||seen[u])return;seen[u]=1;r.push({url:u,label:l||u.split('/').pop()||'Video source'});}"
                + "var ss=v.querySelectorAll('source');for(var i=0;i<ss.length;i++){add(ss[i].src,ss[i].getAttribute('label')||ss[i].getAttribute('data-quality')||ss[i].getAttribute('title')||('Video source '+(i+1)),ss[i].type||'');}"
                + "add(v.currentSrc||v.src,'Current video',v.getAttribute('type')||'');return r.slice(0,12);}"
                + "function route(u){return 'daymark-media://download?url='+encodeURIComponent(u);}"
                + "function bind(v){if(!v||v.dataset.daymarkMediaDownloadBound)return;v.dataset.daymarkMediaDownloadBound='1';var p=v.parentElement;if(!p)return;if(getComputedStyle(p).position==='static')p.style.position='relative';"
                + "var b=document.createElement('a');b.textContent='↓ Download';b.setAttribute('role','button');"
                + "b.style.cssText='position:absolute;right:8px;top:8px;z-index:2147483647;display:none;padding:7px 10px;border-radius:18px;background:#113f2b;color:#fff;font:600 12px sans-serif;text-decoration:none;box-shadow:0 2px 8px #0008;';p.appendChild(b);"
                + "function show(){b.style.display='block';}v.addEventListener('play',show);v.addEventListener('playing',show);if(!v.paused)show();"
                + "b.addEventListener('click',function(e){var cs=choices(v);if(!cs.length){e.preventDefault();b.textContent='Direct download unavailable';return;}if(cs.length===1){b.href=route(cs[0].url);return;}e.preventDefault();e.stopPropagation();"
                + "var old=p.querySelector('[data-daymark-quality-menu]');if(old)old.remove();var m=document.createElement('div');m.setAttribute('data-daymark-quality-menu','1');m.style.cssText='position:absolute;right:8px;top:42px;z-index:2147483647;background:#fff;color:#15251b;padding:6px;border-radius:10px;box-shadow:0 2px 12px #0008;font:12px sans-serif;max-width:85%;';"
                + "cs.forEach(function(c){var x=document.createElement('a');x.href=route(c.url);x.textContent=c.label;x.style.cssText='display:block;color:#15251b;padding:8px;text-decoration:none;';m.appendChild(x);});p.appendChild(m);});}"
                + "function scan(){var vs=document.querySelectorAll('video');for(var i=0;i<vs.length;i++)bind(vs[i]);}"
                + "scan();new MutationObserver(scan).observe(document.documentElement,{childList:true,subtree:true});document.addEventListener('play',function(e){if(e.target&&e.target.tagName==='VIDEO')bind(e.target);},true);"
                + "}catch(e){}})();";
        try { browserWebView.evaluateJavascript(script, null); }
        catch (RuntimeException ignored) {
            if (browserStatus != null) browserStatus.setText("Video download overlay could not be enabled on this page.");
        }
    }

    private void findMediaOnPage() {
        if (!browserNetworkPolicy.allowsRemoteLoads() || browserWebView == null) {
            showToast("Open a page first, with Online enabled.");
            return;
        }
        if (browserWebView.getUrl() == null || browserWebView.getUrl().isEmpty()) {
            showToast("Open a page first.");
            return;
        }
        browserStatus.setText("Searching the page for downloadable videos and audio...");
        String mediaProbeScript = "(function(){"
                + "var found=[];var seen={};"
                + "function push(url,kind,label,quality){if(!url)return;var abs;try{abs=new URL(url,location.href).href;}catch(x){return;}"
                + "var p='';try{p=new URL(abs).pathname.toLowerCase();}catch(x){return;}"
                + "if(abs.indexOf('https://')!==0||p.endsWith('.m3u8')||p.endsWith('.mpd')||p.startsWith('blob:')||p.startsWith('data:'))return;"
                + "if(seen[abs])return;seen[abs]=1;if(found.length<20)found.push([kind,abs,(label||'').substring(0,60),(quality||'').substring(0,32)]);}"
                + "var i,e,src,links;links=document.querySelectorAll('video');"
                + "for(i=0;i<links.length;i++){e=links[i];var q=e.videoWidth&&e.videoHeight?(e.videoWidth+'x'+e.videoHeight):'';"
                + "push(e.currentSrc||e.src,'video',e.getAttribute('title')||'',q);src=e.querySelectorAll('source');"
                + "for(var j=0;j<src.length;j++){push(src[j].src,'video',src[j].getAttribute('label')||src[j].getAttribute('title')||'',src[j].getAttribute('data-quality')||q);}}"
                + "links=document.querySelectorAll('audio');for(i=0;i<links.length;i++){e=links[i];push(e.currentSrc||e.src,'audio',e.getAttribute('title')||'','');"
                + "src=e.querySelectorAll('source');for(var j=0;j<src.length;j++){push(src[j].src,'audio',src[j].getAttribute('label')||'','');}}"
                + "links=document.querySelectorAll('a[href]');var media=/\\.(mp4|webm|m4v|mov|mkv|mp3|m4a|aac|ogg|oga|opus|wav|flac)([?#]|$)/i;"
                + "for(i=0;i<links.length;i++){var href=links[i].getAttribute('href');if(href&&href.toLowerCase().indexOf('javascript:')!==0&&media.test(href)){"
                + "push(href,'link',links[i].textContent.trim().substring(0,60),'');}}return found;})()";
        browserWebView.evaluateJavascript(mediaProbeScript, value -> {
            if (!isActivityCallbackCurrent()) return;
            if (value == null || value.equals("null") || value.equals("[]")) {
                browserStatus.setText("No direct video or audio found. Protected streaming players are not downloadable.");
                showToast("No downloadable media found on this page.");
                return;
            }
            try {
                org.json.JSONArray found = new org.json.JSONArray(value);
                showBrowserMediaDialog(found);
            } catch (Exception invalidResult) {
                showToast("Media search failed on this page.");
            }
        });
    }

    private void showBrowserMediaDialog(org.json.JSONArray found) {
        final String[] urls;
        final String[] labels;
        try {
            urls = new String[found.length()];
            labels = new String[urls.length];
            for (int i = 0; i < urls.length; i++) {
                org.json.JSONArray item = found.getJSONArray(i);
                String kind = item.getString(0);
                urls[i] = item.getString(1);
                String label = item.length() > 2 ? item.optString(2, "") : "";
                String quality = item.length() > 3 ? item.optString(3, "") : "";
                String name = URLUtil.guessFileName(urls[i], null, null);
                String description = label == null || label.isEmpty() ? name : label;
                if (quality != null && !quality.isEmpty()) description += " · " + quality;
                labels[i] = ("video".equals(kind) ? "Video · " : "audio".equals(kind) ? "Audio · " : "Link · ")
                        + description;
            }
        } catch (Exception invalidMediaResult) {
            showToast("Media search failed on this page.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Videos & audio on this page")
                .setItems(labels, (dialog, which) -> {
                    String url = urls[which];
                    if (!BrowserAddress.isAllowedWebUrl(url)) {
                        showToast("Download blocked: HTTPS is required.");
                        return;
                    }
                    browserStatus.setText("Download started for the selected media file. Check Downloads for progress.");
                    queueBrowserDownload(url, null, null, null);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void syncBrowserButtons() {
        if (browserBackButton == null) return;
        boolean available = browserNetworkPolicy.allowsRemoteLoads() && browserWebView != null;
        browserBackButton.setEnabled(available && browserWebView.canGoBack());
        browserForwardButton.setEnabled(available && browserWebView.canGoForward());
        browserReloadButton.setEnabled(available);
        boolean hasPage = browserWebView != null && browserWebView.getUrl() != null && !browserWebView.getUrl().isEmpty();
        boolean loading = hasPage && browserWebView.getProgress() < 100;
        browserReloadButton.setText(loading ? "Stop" : "Reload");
        browserReloadButton.setContentDescription(loading
                ? "Stop loading the current page" : "Reload the current page");
        if (browserExpandButton != null) {
            browserExpandButton.setEnabled(available && hasPage);
        }
        if (browserReaderActionRow != null) browserReaderActionRow.setVisibility(View.GONE);
        if (browserReaderButton != null) browserReaderButton.setEnabled(false);
        if (browserMediaButton != null) {
            browserMediaButton.setEnabled(available && hasPage);
        }
    }

    private void showBrowserHome() {
        discardBrowserWebView();
        if (browserHomeView != null) browserHomeView.setVisibility(View.VISIBLE);
        if (browserProviderRow != null) browserProviderRow.setVisibility(View.VISIBLE);
        if (browserPrivacyButton != null) browserPrivacyButton.setVisibility(View.VISIBLE);
        if (quickCaptureInput != null && webMode) quickCaptureInput.setText("");
        if (browserStatus != null) browserStatus.setText("Ready. No page has been requested.");
        syncBrowserButtons();
    }

    private void openFullScreenWebReader() {
        if (browserWebView == null || !browserNetworkPolicy.allowsRemoteLoads()) return;
        if (fullScreenWebDialog != null && fullScreenWebDialog.isShowing()) return;

        if (browserWebView.getParent() instanceof ViewGroup) {
            ((ViewGroup) browserWebView.getParent()).removeView(browserWebView);
        }

        fullScreenWebDialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        fullScreenWebDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout dialogRoot = new LinearLayout(this);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        dialogRoot.setBackgroundColor(palette.background);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackgroundColor(palette.surface);

        TextView headerTitle = text("Web result — Full screen", 16, palette.text, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(headerTitle, titleParams);

        Button closeButton = compactButton("Close", true);
        closeButton.setContentDescription("Close full-screen reader and return to standard browser view");
        closeButton.setOnClickListener(v -> closeFullScreenWebReader());
        header.addView(closeButton);

        dialogRoot.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fullScreenWebContainer = new FrameLayout(this);
        fullScreenWebContainer.addView(browserWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialogRoot.addView(fullScreenWebContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        fullScreenWebDialog.setContentView(dialogRoot);

        fullScreenWebDialog.setOnCancelListener(d -> returnWebViewToViewport());
        fullScreenWebDialog.setOnDismissListener(d -> {
            fullScreenWebDialog = null;
            returnWebViewToViewport();
        });

        fullScreenWebDialog.show();
        Window window = fullScreenWebDialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void showFullScreenVideo(View view, android.webkit.WebChromeClient.CustomViewCallback callback) {
        if (view == null || callback == null) return;
        if (fullScreenVideoView != null) { callback.onCustomViewHidden(); return; }
        fullScreenVideoView = view;
        fullScreenVideoCallback = callback;
        Window window = getWindow();
        View decor = window.getDecorView();
        savedWindowFlags = window.getAttributes().flags;
        savedSystemUiVisibility = decor.getSystemUiVisibility();
        hasSavedVideoUiState = true;
        view.setBackgroundColor(Color.BLACK);
        ((ViewGroup) decor).addView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        decor.setSystemUiVisibility(savedSystemUiVisibility | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void hideFullScreenVideo(boolean notifyPage) {
        View view = fullScreenVideoView;
        android.webkit.WebChromeClient.CustomViewCallback callback = fullScreenVideoCallback;
        fullScreenVideoView = null;
        fullScreenVideoCallback = null;
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        if (hasSavedVideoUiState) {
            Window window = getWindow();
            window.setFlags(savedWindowFlags, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(savedSystemUiVisibility);
            hasSavedVideoUiState = false;
        }
        if (notifyPage && callback != null) callback.onCustomViewHidden();
    }

    private void closeFullScreenWebReader() {
        if (fullScreenWebDialog != null && fullScreenWebDialog.isShowing()) {
            fullScreenWebDialog.dismiss();
        }
    }

    private void returnWebViewToViewport() {
        if (browserWebView != null) {
            if (browserWebView.getParent() instanceof ViewGroup) {
                ((ViewGroup) browserWebView.getParent()).removeView(browserWebView);
            }
            if (browserViewport != null && browserWebView.getParent() == null) {
                browserViewport.addView(browserWebView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }
    }

    private void discardBrowserWebView() {
        destroyAllBrowserTabs(true);
    }

    private void discardBrowserWebView(boolean stopLoading) {
        DaymarkWebView current = browserWebView;
        browserWebView = null;
        if (current != null) {
            try { current.getSettings().setBlockNetworkLoads(true); } catch (RuntimeException ignored) { }
            if (stopLoading) current.stopLoading();
            if (current.getParent() instanceof ViewGroup) ((ViewGroup) current.getParent()).removeView(current);
            current.destroy();
            int removed = browserTabs.indexOf(current);
            if (removed >= 0) {
                browserTabs.remove(removed);
                if (activeBrowserTabIndex > removed) activeBrowserTabIndex--;
                if (activeBrowserTabIndex >= browserTabs.size()) activeBrowserTabIndex = browserTabs.size() - 1;
                if (browserTabs.isEmpty()) activeBrowserTabIndex = -1;
            }
        }
        updateBrowserTabsButton();
        syncBrowserButtons();
    }

    private void showBrowserOfflineStatus() {
        if (browserStatus != null) {
            browserStatus.setText("Offline: no search/site request was sent. Daymark page/resource loads are blocked; platform-managed Android System WebView Safe Browsing may still make Google/Play Services URL-hash or update checks.");
        }
    }

    private void showBrowserHistoryDialog() {
        sanitizeStoredBrowserHistory();
        List<String> history = BrowserHistory.decode(browserPreferences.getString(BROWSER_HISTORY_KEY, ""));
        String[] entries = history.isEmpty() ? new String[] { "No recent sites" }
                : history.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Site history (HTTPS origins only)")
                .setItems(entries, (whichDialog, selected) -> {
                    if (selected < 0 || selected >= history.size()) return;
                    quickCaptureInput.setText(history.get(selected));
                    navigateBrowserTo(history.get(selected));
                })
                .setNeutralButton("Clear site history & data", null)
                .setPositiveButton("Close", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(view -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("Clear site history and data?")
                            .setMessage("Site history stores only validated HTTPS origins: no paths, queries, fragments, URL credentials, or page titles. Older entries are reduced to origins on launch. Selecting a saved site opens that origin, not the last route. Clearing removes Daymark's site history, the current WebView's back/forward list, resource cache, and SSL exception preferences, plus cookies and Web SQL/HTML5 Web Storage for all websites used in Daymark (not just the current site). It may sign you out of any site opened in Daymark. It only dismisses an open WebView form-autocomplete popup; saved Android Autofill or password-manager data is not cleared. Cookie removal finishes asynchronously. This does not clear other apps' browser data or erase requests/data retained by websites or search providers.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Clear site data", (confirm, selected) -> clearBrowserData())
                            .show();
                }));
        dialog.show();
    }

    private void clearBrowserData() {
        if (browserWebView != null) {
            browserWebView.clearHistory();
            browserWebView.clearCache(true);
            browserWebView.clearFormData();
            browserWebView.clearSslPreferences();
        }
        browserPreferences.edit().putString(BROWSER_HISTORY_KEY, BrowserHistory.clear()).apply();
        WebStorage.getInstance().deleteAllData();
        CookieManager cookies = CookieManager.getInstance();
        showBrowserHome();
        browserStatus.setText("Clearing Daymark site history and local site data...");
        cookies.removeAllCookies(removed -> {
            cookies.flush();
            if (isActivityCallbackCurrent()) {
                browserStatus.setText("Daymark site history, WebView cache, Web SQL/HTML5 Storage, and cookies were cleared. Android Autofill and password-manager data were not changed.");
                showToast("Local site history and site data cleared.");
            }
        });
    }

    private void addSuggestionCard(LinearLayout content) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(shape(palette.suggestionSurface, 15, palette.line));
        card.setElevation(dp(1));

        TextView label = text("✳  DEMO SUGGESTION", 10, palette.accent, Typeface.BOLD);
        label.setLetterSpacing(0.10f);
        card.addView(label, bottomMargin(dp(8)));
        card.addView(text("One place to start", 17, palette.text, Typeface.BOLD), bottomMargin(dp(4)));
        card.addView(text("Deterministic local ranking · not AI", 12, palette.muted, Typeface.NORMAL), bottomMargin(dp(11)));

        suggestionList = new LinearLayout(this);
        suggestionList.setOrientation(LinearLayout.VERTICAL);
        card.addView(suggestionList);
        card.addView(text("Open tasks ranked by due date, then priority. No AI service is connected.",
                12, palette.muted, Typeface.NORMAL), topMargin(dp(9)));
        suggestionCard = card;
        powerOnlyViews.add(card);
        content.addView(card, bottomMargin(dp(25)));
    }

    private void addTaskSection(LinearLayout content) {
        LinearLayout sectionTitle = new LinearLayout(this);
        sectionTitle.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Your tasks", 22, palette.text, Typeface.BOLD);
        sectionTitle.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        taskCount = text("0 tasks", 11, palette.muted, Typeface.NORMAL);
        sectionTitle.addView(taskCount);
        content.addView(sectionTitle, bottomMargin(dp(10)));

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (String key : filterKeys) {
            Button chip = new Button(this);
            chip.setAllCaps(false);
            chip.setTextSize(12 * textScale);
            chip.setMinHeight(dp(48));
            chip.setMinimumHeight(dp(48));
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setOnClickListener(view -> {
                activeFilter = key;
                render();
            });
            filterButtons.add(chip);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
            chipParams.setMargins(0, 0, dp(5), 0);
            chips.addView(chip, chipParams);
        }
        filterScroll.addView(chips);
        filterControlView = filterScroll;
        powerOnlyViews.add(filterScroll);
        content.addView(filterScroll, bottomMargin(dp(9)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextSize(16 * textScale);
        searchInput.setHint("Search tasks");
        searchInput.setContentDescription("Search tasks by title");
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setPadding(dp(12), 0, dp(12), 0);
        searchInput.setTextColor(palette.text);
        searchInput.setHintTextColor(palette.muted);
        searchInput.setBackground(shape(palette.surface, 10, palette.line));
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(48), 1f));
        searchClear = text("Clear", 12, palette.accent, Typeface.BOLD);
        searchClear.setGravity(Gravity.CENTER);
        searchClear.setPadding(dp(11), 0, dp(5), 0);
        searchClear.setMinimumWidth(dp(64));
        searchClear.setFocusable(true);
        searchClear.setBackground(shape(palette.accentSoft, 8, palette.accentSoft));
        searchClear.setContentDescription("Clear task search");
        searchClear.setOnClickListener(view -> {
            searchInput.setText("");
            searchInput.requestFocus();
        });
        searchRow.addView(searchClear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        searchControlView = searchRow;
        powerOnlyViews.add(searchRow);
        content.addView(searchRow, bottomMargin(dp(11)));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                searchQuery = text == null ? "" : text.toString();
                renderTaskList();
                renderTaskCount();
            }
            @Override public void afterTextChanged(Editable editable) { }
        });

        taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        content.addView(taskList);

        LinearLayout empty = new LinearLayout(this);
        empty.setGravity(Gravity.CENTER);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setPadding(dp(20), dp(25), dp(20), dp(25));
        empty.setBackground(shape(palette.surface, 13, palette.line));
        emptyTitle = text("A clear start", 15, palette.text, Typeface.BOLD);
        emptyCopy = text("Capture your first task above. Small steps count.", 12, palette.muted, Typeface.NORMAL);
        emptyCopy.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle, bottomMargin(dp(5)));
        empty.addView(emptyCopy);
        empty.setTag("empty-state");
        content.addView(empty, bottomMargin(dp(13)));
        emptyStateView = empty;
    }

    private View emptyStateView;

    private void addLocalStorageNote(LinearLayout content) {
        TextView note = text("Tasks and attachments stay encrypted in this app on this device. No cloud sync or automatic backup.",
                12, palette.muted, Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(4), dp(10), dp(4), dp(6));
        content.addView(note);
    }

    private void addUndoBar() {
        undoBar = new LinearLayout(this);
        undoBar.setGravity(Gravity.CENTER_VERTICAL);
        undoBar.setPadding(dp(14), dp(5), dp(10), dp(5));
        undoBar.setBackground(shape(palette.surfaceAlt, 12, palette.line));
        undoBar.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        undoMessage = text("Task deleted", 12, palette.text, Typeface.NORMAL);
        undoBar.addView(undoMessage, new LinearLayout.LayoutParams(0, dp(48), 1f));
        undoButton = plainButton("Undo");
        undoButton.setTextColor(palette.accent);
        undoButton.setOnClickListener(view -> undoDelete());
        undoBar.addView(undoButton);
        undoBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(14), dp(7), dp(14), dp(7));
        root.addView(undoBar, params);
    }

    private void applySystemBarsAndInsets() {
        Window window = getWindow();
        window.setStatusBarColor(palette.background);
        window.setNavigationBarColor(palette.background);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        int flags = palette.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26 && !palette.dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int imeBottom = WindowInsetsPolicy.CONSUME_IME_INSETS
                        ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
                root.setPadding(dp(8) + bars.left, bars.top, dp(8) + bars.right,
                        WindowInsetsPolicy.bottomPadding(bars.bottom, imeBottom));
            } else {
                // API 26-29: the decor view still fits the system windows by
                // default, so content is already positioned below the status bar.
                // Re-adding the system-bar insets here would double the top
                // padding on these versions, so only the base side padding is
                // applied and the system bars are left to the decor.
                root.setPadding(dp(8), 0, dp(8), 0);
            }
            return insets;
        });
    }

    private void loadEncryptedTasks() {
        storageLoading = true;
        storageStatus.setText("Opening encrypted storage…");
        render();
        PortableImportGrantRecovery.Selection restoredSelection = pendingPortableImportSelection();
        storageExecutor.execute(() -> {
            List<Task> loaded = null;
            List<TaskTemplate> loadedTemplates = null;
            Exception failure = null;
            boolean portableSelectionPreserved = false;
            try {
                PortableImportGrantRecovery.awaitNoActivityRestoreWorker();
                synchronized (AndroidAttachmentStore.transactionLock()) {
                    loaded = taskStore.load();
                    loadedTemplates = taskStore.loadTemplates();
                    portableSelectionPreserved = portableBackupManager.reconcileStartupImportUri(
                            loaded, attachmentStore, restoredSelection);
                    try { attachmentStore.cleanupOrphans(attachmentIds(loaded)); }
                    catch (Exception exception) { StartupDiagnostics.record(StartupDiagnostics.ORPHAN_CLEANUP_FAILED, exception); }
                    portableBackupManager.cleanupTransientFiles();
                }
            } catch (Exception exception) {
                failure = exception;
            }
            List<Task> result = loaded;
            List<TaskTemplate> templateResult = loadedTemplates;
            Exception error = failure;
            boolean selectionPreserved = portableSelectionPreserved;
            postActivityCallback(() -> {
                storageLoading = false;
                if (error == null) {
                    tasks.clear();
                    tasks.addAll(result);
                    taskTemplates.clear();
                    taskTemplates.addAll(templateResult);
                    lastSavedTasks.clear();
                    lastSavedTasks.addAll(result);
                    storageReady = true;
                    storageLoadFailed = false;
                    storageSaveFailed = false;
                    storageFailureDetails = null;
                    storageStatus.setText(attachmentBusy ? "Importing attachment…" : "Encrypted storage ready");
                    storageStatus.setTextColor(attachmentBusy ? palette.muted : palette.accent);
                } else {
                    pendingPickedAttachmentUri = null;
                    pendingPickedAttachmentTaskId = null;
                    storageReady = false;
                    storageLoadFailed = true;
                    storageFailureDetails = storageFailureDetails(error);
                    storageStatus.setText(storageFailureStatus(error));
                    storageStatus.setTextColor(palette.danger);
                    captureFeedback.setText("Saved tasks could not be opened. The app did not save or clear data; editing is paused.");
                    showToast("Saved tasks are unavailable. The encrypted data was not cleared; editing is paused.");
                }
                if (error == null && restoredSelection != null && !selectionPreserved
                        && restoredSelection.uri.equals(pendingPortableImportUri == null
                        ? null : pendingPortableImportUri.toString())
                        && restoredSelection.operationToken.equals(pendingPortableImportOperationToken)) {
                    pendingPortableImportUri = null;
                    pendingPortableImportOperationToken = null;
                    pendingPortableImportNeedsRepick = true;
                }
                render();
                checkDueReminders();
                if (pendingPortableImportNeedsRepick) {
                    pendingPortableImportNeedsRepick = false;
                    showToast("Access to the selected backup was not retained. Choose the backup again to restore it.");
                }
                if (error == null && pendingPortableImportUri != null) showPortableImportKeyDialog();
                if (error == null && pendingPickedAttachmentUri != null) {
                    Uri picked = pendingPickedAttachmentUri;
                    String pickedTaskId = pendingPickedAttachmentTaskId;
                    pendingPickedAttachmentUri = null;
                    pendingPickedAttachmentTaskId = null;
                    importAttachment(pickedTaskId, picked);
                }
            });
        });
    }

    private void saveTasksAsync() {
        saveTasksAsync("Saving encrypted tasks…");
    }

    private void saveTasksAsync(String savingStatus) {
        if (!storageReady) return;
        long revision = ++saveRevision;
        List<Task> snapshot = new ArrayList<>(tasks);
        List<TaskTemplate> templateSnapshot = new ArrayList<>(taskTemplates);
        storageStatus.setText(savingStatus);
        storageStatus.setTextColor(palette.muted);
        storageExecutor.execute(() -> {
            Exception failure = null;
            try {
                taskStore.saveSnapshot(snapshot, templateSnapshot);
            } catch (Exception exception) {
                failure = exception;
            }
            Exception error = failure;
            postActivityCallback(() -> {
                if (error == null) {
                    lastSavedTasks.clear();
                    lastSavedTasks.addAll(snapshot);
                    if (pendingAttachmentCleanupTask != null
                            && findTaskIn(snapshot, pendingAttachmentCleanupTask.id) == null) {
                        pendingAttachmentCleanupSaved = true;
                        cleanupDeletedAttachmentsIfReady();
                    }
                    if (revision == saveRevision) {
                        storageReady = true;
                        storageSaveFailed = false;
                        storageStatus.setText(attachmentBusy ? "Importing attachment…" : "Encrypted storage ready");
                        storageStatus.setTextColor(attachmentBusy ? palette.muted : palette.accent);
                    }
                } else if (revision == saveRevision) {
                    if (pendingAttachmentCleanupTask != null && !pendingAttachmentCleanupSaved) {
                        pendingAttachmentCleanupTask = null;
                        pendingAttachmentCleanupExpired = false;
                    }
                    storageReady = false;
                    storageSaveFailed = true;
                    storageStatus.setText("Not saved · unsaved changes are shown");
                    storageStatus.setTextColor(palette.danger);
                    captureFeedback.setText("These changes are not saved and may be lost if you leave. Editing is paused.");
                    hideUndoBar();
                    render();
                    showToast("Not saved. Your unsaved changes are still shown; editing is paused.");
                }
            });
        });
    }

    private String storageFailureStatus(Exception failure) {
        if (failure instanceof EncryptedBlobStore.StorageException) {
            EncryptedBlobStore.Kind kind = ((EncryptedBlobStore.StorageException) failure).kind();
            if (kind == EncryptedBlobStore.Kind.KEY_UNAVAILABLE) {
                return "Saved tasks unavailable · encryption key unavailable";
            }
            if (kind == EncryptedBlobStore.Kind.AUTHENTICATION_FAILED) {
                return "Saved tasks unavailable · authentication failed";
            }
            if (kind == EncryptedBlobStore.Kind.CORRUPT_DATA) {
                return "Saved tasks unavailable · file incomplete or unsupported";
            }
        }
        return "Saved tasks unavailable · storage could not be read";
    }

    private String storageFailureDetails(Exception failure) {
        if (failure instanceof EncryptedBlobStore.StorageException) {
            EncryptedBlobStore.Kind kind = ((EncryptedBlobStore.StorageException) failure).kind();
            if (kind == EncryptedBlobStore.Kind.KEY_UNAVAILABLE) {
                return "The task file may still be on this device, but its Keystore key is missing or unusable. V1 has no key-recovery or export path. Editing is paused; do not clear app data or reinstall.";
            }
            if (kind == EncryptedBlobStore.Kind.AUTHENTICATION_FAILED) {
                return "The saved encrypted task data could not be authenticated. V1 has no automatic recovery or export path. Editing is paused; do not clear app data or reinstall.";
            }
            if (kind == EncryptedBlobStore.Kind.CORRUPT_DATA) {
                return "The saved encrypted task data is incomplete or unsupported. V1 has no automatic recovery or export path. Editing is paused; do not clear app data or reinstall.";
            }
        }
        return "Encrypted task storage could not be read. The app did not save or clear task data during this load. Editing is paused; do not clear app data or reinstall.";
    }

    private void render() {
        syncModeUi();
        LocalDate today = LocalDate.now();
        dateHeading.setText(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
                .format(today).toUpperCase(Locale.getDefault()));
        int open = 0;
        int dueToday = 0;
        for (Task task : tasks) {
            if (!task.completed) {
                open++;
                if (today.toString().equals(task.dueDate)) dueToday++;
            }
        }
        openCount.setText(storageLoading || storageLoadFailed ? "—" : String.valueOf(open));
        dueTodayCount.setText(storageLoading || storageLoadFailed ? "—" : String.valueOf(dueToday));
        addTaskButton.setEnabled(canEdit() && !webMode);
        addTaskButton.setAlpha(canEdit() ? 1f : 0.55f);
        addDetailsButton.setEnabled(canEdit() && !webMode);
        quickCaptureInput.setEnabled(canEdit() || webMode);
        if (searchInput != null) searchInput.setEnabled(!storageLoading && !storageLoadFailed);
        for (View powerOnly : powerOnlyViews) {
            powerOnly.setVisibility(powerMode ? View.VISIBLE : View.GONE);
        }
        renderFilters(today);
        renderTaskList(today);
        renderTaskCount(today);
        renderSuggestions(today);
    }

    private void syncModeUi() {
        if (taskScreen == null || browserScreen == null || quickCaptureInput == null) return;
        // Web mode is a dedicated browser workspace. Hide the task composer/dashboard
        // instead of stacking Task + Web controls and wasting the viewport.
        // Web is a dedicated workspace: Task dashboard, Task/Web switcher, and app header
        // must not remain stacked above the browser.
        if (appTopBar != null) appTopBar.setVisibility(webMode ? View.GONE : View.VISIBLE);
        if (sharedComposer != null) sharedComposer.setVisibility(webMode ? View.GONE : View.VISIBLE);
        taskScreen.setVisibility(webMode ? View.GONE : View.VISIBLE);
        browserScreen.setVisibility(webMode ? View.VISIBLE : View.GONE);
        taskActions.setVisibility(webMode ? View.GONE : View.VISIBLE);
        webActions.setVisibility(View.GONE);
        pathButton.setVisibility(webMode ? View.GONE : View.VISIBLE);
        quickCaptureInput.setHint(webMode ? "Search the web or enter a URL" : "Type a task in your own words");
        quickCaptureInput.setContentDescription(webMode
                ? "Search the web or enter an HTTPS web address. Browser network access is Offline by default; this is sent only when Online is enabled and you tap Go."
                : "What do you want to get done? Type a task");
        quickCaptureInput.setImeOptions(webMode ? EditorInfo.IME_ACTION_SEARCH : EditorInfo.IME_ACTION_DONE);
        taskModeButton.setTextColor(webMode ? palette.muted : palette.accent);
        taskModeButton.setBackground(shape(webMode ? palette.surfaceAlt : palette.accentSoft,
                18, webMode ? palette.line : palette.accent));
        webModeButton.setTextColor(webMode ? palette.accent : palette.muted);
        webModeButton.setBackground(shape(webMode ? palette.accentSoft : palette.surfaceAlt,
                18, webMode ? palette.accent : palette.line));
        taskModeButton.setContentDescription(webMode
                ? "Switch to Task mode. Your web search is not added to your tasks."
                : "Task mode selected. Typing creates a task only after Add task.");
        webModeButton.setContentDescription(webMode
                ? "Web mode selected. Browser starts Offline; requests require Online enabled and a separate Go or site tap."
                : "Switch to Web mode. The current task draft stays in Daymark and is not sent to a site.");
        if (undoBar != null) {
            if (webMode) undoBar.setVisibility(View.GONE);
            else if (pendingDeletedTask != null) undoBar.setVisibility(View.VISIBLE);
        }
        syncBrowserButtons();
    }

    private void setWebMode(boolean enabled) {
        if (webMode == enabled) {
            syncModeUi();
            return;
        }
        if (!webMode) {
            taskDraft = quickCaptureInput.getText() == null ? "" : quickCaptureInput.getText().toString();
        } else if (browserWebView != null) {
            discardBrowserWebView();
        }
        webMode = enabled;
        quickCaptureInput.setText(enabled ? "" : taskDraft);
        quickCaptureInput.setError(null);
        if (enabled && browserStatus != null) {
            browserStatus.setText(browserNetworkPolicy.isOnlineEnabled()
                    ? "Online is enabled. No website opens until you tap Go or a site."
                    : "Website access is Offline. Turn Online on, then tap Go or a site.");
        }
        syncModeUi();
        animateModeTransition(enabled);
    }

    private void animateModeTransition(boolean enteringWeb) {
        View target = enteringWeb ? browserScreen : taskScreen;
        if (target == null) return;
        target.setAlpha(0f);
        target.setTranslationY(enteringWeb ? dp(10) : -dp(6));
        target.animate().alpha(1f).translationY(0f)
                .setDuration(180L).start();
    }

    private void setBrowserOnlineEnabled(boolean enabled) {
        if (browserNetworkPolicy.isOnlineEnabled() == enabled) return;
        browserNetworkPolicy.setOnlineEnabled(enabled);
        if (enabled) {
            browserPreferences.edit().putBoolean(BROWSER_ONLINE_ENABLED_KEY, true).apply();
            if (browserStatus != null) browserStatus.setText("Online access enabled. No website opens until you tap Go or a site.");
        } else {
            browserPreferences.edit().putBoolean(BROWSER_ONLINE_ENABLED_KEY, false).apply();
            showBrowserHome();
            showBrowserOfflineStatus();
        }
        syncBrowserButtons();
        if (browserOnlineToggle != null) {
            browserOnlineToggle.setContentDescription(browserNetworkPolicy.isOnlineEnabled()
                    ? "Online browsing is enabled. Every search or site still requires a tap. Switch off to block Daymark page and resource loads; platform-managed Android System WebView Safe Browsing may make Google/Play Services checks separately."
                    : "Online browsing is off by default. Turn it on after reviewing the disclosure to allow Daymark page and resource loads, then tap Go or a site to send a request. The switch does not control platform-managed Android System WebView Safe Browsing, which may contact Google/Play Services for URL-hash or update checks.");
        }
    }

    private void confirmBrowserOnlineAccess() {
        new AlertDialog.Builder(this)
                .setTitle("Enable online browsing?")
                .setMessage("Online browsing sends a search query or requested URL, plus normal connection data such as your IP address and browser identification, to the selected provider/site; those services may log requests. Pages may contact their own or third-party endpoints, which may also be logged. The Online switch blocks Daymark page/resource loads only and does not control Android System WebView Safe Browsing, a separate platform-managed service that may contact Google/Play Services for threat-list updates or URL-hash-based checks; its provider is not selectable in Daymark, though its protection setting is available in Browser Settings. WebView M126+ may send a partial URL hash through a proxy for real-time checks; earlier versions use a local partial-hash database and may query a server on prefix match. This is not a claim that every full URL is sent; the method depends on WebView version and device settings. Daymark sends no task text and adds no app analytics; WebView diagnostic metrics are opted out. HTTP remains blocked. Enabling Online alone makes no page request; each search or site still requires a tap. This choice is saved on this device. Turning Online off stops and closes the active page.")
                .setNegativeButton("Stay offline", (dialog, which) -> showBrowserOfflineStatus())
                .setPositiveButton("Enable Online", (dialog, which) -> {
                    setBrowserOnlineEnabled(true);
                    if (browserOnlineToggle != null) {
                        suppressBrowserOnlineToggleListener = true;
                        browserOnlineToggle.setChecked(true);
                        suppressBrowserOnlineToggleListener = false;
                    }
                })
                .setOnCancelListener(dialog -> showBrowserOfflineStatus())
                .show();
    }

    private boolean setBrowserSafeBrowsingEnabled(boolean enabled) {
        if (browserSettingsPolicy.isSafeBrowsingEnabled() == enabled) return true;
        if (browserWebView != null) {
            try {
                browserWebView.getSettings().setSafeBrowsingEnabled(enabled);
            } catch (RuntimeException updateFailed) {
                showToast("Safe Browsing could not be changed. The previous setting remains active.");
                return false;
            }
        }
        browserSettingsPolicy.setSafeBrowsingEnabled(enabled);
        browserPreferences.edit().putBoolean(SAFE_BROWSING_ENABLED_KEY, enabled).apply();
        return true;
    }

    private void showBrowserSettingsDialog() {
        TextView explanation = text(
                "Safe Browsing is enabled by default and helps protect against known harmful pages. Its platform-managed provider is not selectable in Daymark and may contact Google/Play Services for version/device-dependent threat-list updates or URL-hash checks. The Online switch only blocks Daymark page/resource loads; this setting controls Safe Browsing separately.",
                14, palette.text, Typeface.NORMAL);
        explanation.setLineSpacing(dp(3), 1f);
        explanation.setPadding(dp(16), dp(8), dp(16), dp(8));

        CheckBox safeBrowsingToggle = new CheckBox(this);
        safeBrowsingToggle.setText("Safe Browsing (recommended)");
        safeBrowsingToggle.setMinHeight(dp(48));
        safeBrowsingToggle.setChecked(browserSettingsPolicy.isSafeBrowsingEnabled());
        safeBrowsingToggle.setContentDescription(browserSettingsPolicy.isSafeBrowsingEnabled()
                ? "Safe Browsing is enabled. Turn it off only after reviewing the protection warning."
                : "Safe Browsing is disabled. Turn it on to restore protection against known harmful pages.");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(explanation);
        content.addView(safeBrowsingToggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        AlertDialog settingsDialog = new AlertDialog.Builder(this)
                .setTitle("Browser Settings")
                .setView(content)
                .setNeutralButton("Clear site data", null)
                .setPositiveButton("Done", null)
                .create();
        settingsDialog.setOnShowListener(ignored -> settingsDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(view -> {
                    settingsDialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("Clear site data?")
                            .setMessage("This clears Daymark's HTTPS site history, WebView cache, cookies, storage, form data, and current page history. It can sign you out of websites opened in Daymark. Android Autofill and password-manager data are not changed.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Clear", (d, w) -> clearBrowserData())
                            .show();
                }));

        safeBrowsingToggle.setOnCheckedChangeListener((button, enabled) -> {
            if (suppressSafeBrowsingToggleListener) return;
            if (enabled) {
                if (!setBrowserSafeBrowsingEnabled(true)) {
                    suppressSafeBrowsingToggleListener = true;
                    button.setChecked(false);
                    suppressSafeBrowsingToggleListener = false;
                    button.setContentDescription("Safe Browsing remains disabled because the setting could not be applied.");
                } else {
                    button.setContentDescription("Safe Browsing is enabled. Turn it off only after reviewing the protection warning.");
                }
                return;
            }

            suppressSafeBrowsingToggleListener = true;
            button.setChecked(true);
            suppressSafeBrowsingToggleListener = false;
            new AlertDialog.Builder(this)
                    .setTitle("Disable Safe Browsing?")
                    .setMessage("Turning this off reduces protection. Known harmful or deceptive sites may no longer be blocked or warned about in Daymark's WebView. The choice is saved on this device and applied to the current and future Daymark WebViews. This does not guarantee zero platform network activity; Android/Google/Play Services may have independent behavior. Daymark's Online setting continues to control page/resource loads separately.")
                    .setNegativeButton("Keep Safe Browsing", null)
                    .setPositiveButton("Disable Safe Browsing", (warning, choice) -> {
                        if (setBrowserSafeBrowsingEnabled(false)) {
                            suppressSafeBrowsingToggleListener = true;
                            button.setChecked(false);
                            suppressSafeBrowsingToggleListener = false;
                            button.setContentDescription("Safe Browsing is disabled. Turn it on to restore protection against known harmful pages.");
                        }
                    })
                    .show();
        });
        settingsDialog.show();
    }

    private void renderFilters(LocalDate today) {
        for (int index = 0; index < filterKeys.size(); index++) {
            String key = filterKeys.get(index);
            Button button = filterButtons.get(index);
            int count = 0;
            for (Task task : tasks) {
                if (TaskLogic.FILTER_ALL.equals(key)
                        || (TaskLogic.FILTER_TODAY.equals(key) && today.toString().equals(task.dueDate))
                        || (TaskLogic.FILTER_UPCOMING.equals(key) && task.dueDate != null
                            && LocalDate.parse(task.dueDate).isAfter(today))
                        || (TaskLogic.FILTER_OVERDUE.equals(key) && !task.completed && task.dueDate != null
                            && LocalDate.parse(task.dueDate).isBefore(today))
                        || (TaskLogic.FILTER_NO_DATE.equals(key) && task.dueDate == null)
                        || (TaskLogic.FILTER_COMPLETED.equals(key) && task.completed)) count++;
            }
            String label;
            if (TaskLogic.FILTER_ALL.equals(key)) label = "All";
            else if (TaskLogic.FILTER_TODAY.equals(key)) label = "Today";
            else if (TaskLogic.FILTER_UPCOMING.equals(key)) label = "Upcoming";
            else if (TaskLogic.FILTER_OVERDUE.equals(key)) label = "Overdue";
            else if (TaskLogic.FILTER_NO_DATE.equals(key)) label = "No date";
            else label = "Completed";
            button.setEnabled(!storageLoading && !storageLoadFailed);
            button.setText(label + "  " + (storageLoading || storageLoadFailed ? "—" : count));
            boolean selected = key.equals(activeFilter);
            button.setTextColor(selected ? palette.accent : palette.muted);
            button.setBackground(shape(selected ? palette.accentSoft : palette.surface,
                    20, selected ? palette.accent : palette.line));
            button.setContentDescription(storageLoading
                    ? label + ", waiting for saved tasks to open"
                    : storageLoadFailed
                    ? label + ", unavailable because saved task data could not be opened"
                    : label + ", " + count + " tasks" + (selected ? ", selected" : ""));
        }
    }

    private SmartTaskDraft parseSmartTaskDraft(String raw) {
        String title = raw.trim();
        String lower = title.toLowerCase(Locale.ROOT);
        String dueDate = null;
        LocalDate today = LocalDate.now();
        if (lower.matches(".*\\b(today)\\b.*")) {
            dueDate = today.toString();
            title = title.replaceAll("(?i)\\b(today)\\b", "").trim();
        } else if (lower.matches(".*\\b(tomorrow)\\b.*")) {
            dueDate = today.plusDays(1).toString();
            title = title.replaceAll("(?i)\\b(tomorrow)\\b", "").trim();
        } else if (lower.matches(".*\\b(next week)\\b.*")) {
            dueDate = today.plusWeeks(1).toString();
            title = title.replaceAll("(?i)\\b(next week)\\b", "").trim();
        }
        String priority = "medium";
        if (lower.matches(".*\\b(high|urgent|important)\\b.*")) {
            priority = "high";
            title = title.replaceAll("(?i)\\b(high|urgent|important)\\b", "").trim();
        } else if (lower.matches(".*\\b(low)\\b.*")) {
            priority = "low";
            title = title.replaceAll("(?i)\\blow\\b", "").trim();
        }
        title = title.replaceAll("\\s{2,}", " ").replaceAll("^[,.:;\\-]+|[,.:;\\-]+$", "").trim();
        return new SmartTaskDraft(title, dueDate, priority);
    }

    private String dueLabelForDate(String date) {
        if (date == null) return "no due date";
        LocalDate due = LocalDate.parse(date);
        if (due.equals(LocalDate.now())) return "due today";
        if (due.equals(LocalDate.now().plusDays(1))) return "due tomorrow";
        return "due " + TaskLogic.formatDate(due);
    }

    private static final class SmartTaskDraft {
        final String title;
        final String dueDate;
        final String priority;
        SmartTaskDraft(String title, String dueDate, String priority) {
            this.title = title;
            this.dueDate = dueDate;
            this.priority = priority;
        }
    }

    private void renderTaskList() {
        renderTaskList(LocalDate.now());
    }

    private void renderTaskList(LocalDate today) {
        if (storageLoading || storageLoadFailed) {
            taskList.removeAllViews();
            if (storageLoading) {
                emptyTitle.setText("Opening saved tasks");
                emptyCopy.setText("Checking encrypted storage on this device…");
            } else {
                emptyTitle.setText("Saved tasks unavailable");
                emptyCopy.setText(storageFailureDetails == null
                        ? "Saved task data could not be opened. Editing is paused; do not clear app data or reinstall."
                        : storageFailureDetails);
            }
            emptyStateView.setVisibility(View.VISIBLE);
            return;
        }
        String selectedFilter = powerMode ? activeFilter : TaskLogic.FILTER_ALL;
        String selectedQuery = powerMode ? searchQuery : "";
        List<Task> visible = TaskLogic.filter(tasks, selectedFilter, selectedQuery, today);
        taskList.removeAllViews();
        for (Task task : visible) {
            taskList.addView(buildTaskRow(task), bottomMargin(dp(8)));
        }
        boolean showEmpty = visible.isEmpty();
        emptyStateView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        if (showEmpty) {
            if (storageSaveFailed) {
                emptyTitle.setText("Change not saved");
                emptyCopy.setText("The visible change is unsaved. Your last saved data remains on this device; editing is paused.");
            } else if (!selectedQuery.trim().isEmpty()) {
                emptyTitle.setText("No matching tasks");
                emptyCopy.setText("Try another search or clear the field above.");
            } else if (TaskLogic.FILTER_COMPLETED.equals(selectedFilter)) {
                emptyTitle.setText("No tasks done yet");
                emptyCopy.setText("Tasks you finish will appear here.");
            } else if (tasks.isEmpty()) {
                emptyTitle.setText("Your list is empty");
                emptyCopy.setText("Type a task above to get started.");
            } else if (TaskLogic.FILTER_TODAY.equals(selectedFilter)) {
                emptyTitle.setText("Nothing due today");
                emptyCopy.setText("Try another filter or add a task.");
            } else if (TaskLogic.FILTER_UPCOMING.equals(selectedFilter)) {
                emptyTitle.setText("Nothing coming up");
                emptyCopy.setText("Add a due date to plan ahead.");
            } else {
                emptyTitle.setText("No open tasks");
                emptyCopy.setText("Add a task above when you are ready.");
            }
        }
    }

    private void renderTaskCount() {
        renderTaskCount(LocalDate.now());
    }

    private void renderTaskCount(LocalDate today) {
        if (storageLoading) {
            taskCount.setText("Loading");
            searchClear.setVisibility(View.GONE);
            return;
        }
        if (storageLoadFailed) {
            taskCount.setText("Unavailable");
            searchClear.setVisibility(View.GONE);
            return;
        }
        String selectedFilter = powerMode ? activeFilter : TaskLogic.FILTER_ALL;
        String selectedQuery = powerMode ? searchQuery : "";
        int visible = TaskLogic.filter(tasks, selectedFilter, selectedQuery, today).size();
        taskCount.setText(selectedQuery.trim().isEmpty()
                ? visible + (visible == 1 ? " task" : " tasks")
                : visible + (visible == 1 ? " match" : " matches"));
        searchClear.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void appendSmartToken(String token) {
        if (webMode || quickCaptureInput == null) return;
        String current = quickCaptureInput.getText() == null ? "" : quickCaptureInput.getText().toString().trim();
        String next = (current + token).trim().replaceAll("\\s{2,}", " ");
        quickCaptureInput.setText(next);
        quickCaptureInput.setSelection(next.length());
        quickCaptureInput.requestFocus();
    }

    private void addQuickTask() {
        if (webMode || !canEdit()) return;
        String rawTitle = quickCaptureInput.getText() == null ? "" : quickCaptureInput.getText().toString().trim();
        if (rawTitle.isEmpty()) {
            quickCaptureInput.setError("Type a task first");
            captureFeedback.setText("Type a task in the box above, or choose Add with a date or priority.");
            quickCaptureInput.requestFocus();
            return;
        }
        SmartTaskDraft draft = parseSmartTaskDraft(rawTitle);
        String title = draft.title;
        if (title.isEmpty()) {
            quickCaptureInput.setError("Type a task first");
            captureFeedback.setText("Type a task in the box above, or choose Add with a date or priority.");
            quickCaptureInput.requestFocus();
            return;
        }
        try {
            tasks.add(TaskLogic.create(title, draft.dueDate, draft.priority));
            quickCaptureInput.setText("");
            quickCaptureInput.setError(null);
            captureFeedback.setText("Task added" + (draft.dueDate == null ? "" : " with " + dueLabelForDate(draft.dueDate)) + " and " + draft.priority + " priority. Tap Edit for more options.");
            render();
            saveTasksAsync();
            showToast("Task added.");
        } catch (IllegalArgumentException exception) {
            quickCaptureInput.setError(exception.getMessage());
        }
    }

    private void renderSuggestions(LocalDate today) {
        suggestionList.removeAllViews();
        if (storageLoading) {
            TextView loading = text("Checking saved tasks…", 12, palette.muted, Typeface.NORMAL);
            suggestionList.addView(loading);
            return;
        }
        if (storageLoadFailed) {
            TextView unavailable = text("Suggestions are unavailable until saved tasks can be opened.",
                    12, palette.muted, Typeface.NORMAL);
            suggestionList.addView(unavailable);
            return;
        }
        List<TaskLogic.Suggestion> suggestions = TaskLogic.suggestions(tasks, today);
        if (suggestions.isEmpty()) {
            TextView empty = text("Your open-task suggestions will appear here after you add a task.",
                    12, palette.muted, Typeface.NORMAL);
            suggestionList.addView(empty);
            return;
        }
        for (int index = 0; index < suggestions.size(); index++) {
            TaskLogic.Suggestion suggestion = suggestions.get(index);
            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10), dp(9), dp(10), dp(9));
            item.setBackground(shape(palette.surface, 10, palette.line));
            TextView number = text(String.valueOf(index + 1), 11, palette.accent, Typeface.BOLD);
            number.setGravity(Gravity.CENTER);
            number.setBackground(shape(palette.accentSoft, 8, palette.accentSoft));
            item.addView(number, new LinearLayout.LayoutParams(dp(27), dp(27)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(dp(9), 0, 0, 0);
            TextView taskTitle = text(suggestion.task.title, 13, palette.text, Typeface.BOLD);
            TextView reason = text(suggestion.reason, 12, palette.muted, Typeface.NORMAL);
            copy.addView(taskTitle);
            copy.addView(reason, topMargin(dp(2)));
            item.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            suggestionList.addView(item, bottomMargin(dp(6)));
        }
    }

    private View buildTaskRow(Task task) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(8), dp(8), dp(8));
        row.setBackground(shape(palette.surface, 13, palette.line));
        row.setElevation(dp(1));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setButtonTintList(ColorStateList.valueOf(task.completed ? palette.accent : palette.muted));
        checkBox.setChecked(task.completed);
        checkBox.setContentDescription(task.completed
                ? "Mark “" + task.title + "” as not done"
                : "Mark “" + task.title + "” as done");
        checkBox.setEnabled(canEdit());
        row.addView(checkBox, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(2), dp(2), dp(4), dp(2));
        TextView title = text(task.title, 14, task.completed ? palette.muted : palette.text, Typeface.BOLD);
        if (task.completed) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        copy.addView(title);
        TextView details = text(dueLabel(task) + "  ·  " + task.priority.toUpperCase(Locale.ROOT),
                12, dueColor(task), Typeface.NORMAL);
        copy.addView(details, topMargin(dp(4)));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        Button attachments = compactButton("Files · " + task.attachments.size(), false);
        attachments.setContentDescription("Manage " + task.attachments.size()
                + " attachments for task: " + task.title);
        attachments.setEnabled(canEdit());
        attachments.setOnClickListener(view -> showAttachmentManager(task));
        chips.addView(attachments, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        if (!task.subtasks.isEmpty()) {
            int doneCount = TaskLogic.completedSubtaskCount(task);
            Button subtasksChip = compactButton("Subtasks · " + doneCount + "/" + task.subtasks.size(), false);
            subtasksChip.setContentDescription("Checklist for task: " + task.title
                    + ". " + doneCount + " of " + task.subtasks.size() + " done. Open to update.");
            subtasksChip.setEnabled(canEdit());
            subtasksChip.setOnClickListener(view -> showSubtasksDialog(task));
            chips.addView(subtasksChip, chipMargin());
        }
        if (!task.notes.isEmpty()) {
            Button notesChip = compactButton("Note", false);
            notesChip.setContentDescription("Show the note for task: " + task.title);
            notesChip.setOnClickListener(view -> showNotesDialog(task));
            chips.addView(notesChip, chipMargin());
        }
        String reminder = reminderLabel(task);
        if (reminder != null && !task.completed) {
            Button reminderChip = compactButton(reminder, false);
            reminderChip.setContentDescription(reminder + " for task: " + task.title);
            reminderChip.setEnabled(false);
            chips.addView(reminderChip, chipMargin());
        }
        copy.addView(chips, topMargin(dp(4)));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button edit = compactButton("Edit", false);
        edit.setContentDescription("Edit task: " + task.title);
        edit.setEnabled(canEdit());
        edit.setOnClickListener(view -> showTaskEditor(task));
        row.addView(edit);
        Button more = compactButton("More", false);
        more.setContentDescription("More actions for task: " + task.title);
        more.setEnabled(canEdit());
        more.setOnClickListener(view -> showTaskActions(task));
        row.addView(more);

        Button delete = compactButton("Delete", true);
        delete.setContentDescription("Delete task: " + task.title);
        delete.setEnabled(canEdit() && pendingDeletedTask == null);
        delete.setOnClickListener(view -> confirmDeleteTask(task));
        row.addView(delete);

        checkBox.setOnCheckedChangeListener((button, checked) -> {
            if (!canEdit() || checked == task.completed) return;
            replaceTask(TaskLogic.toggleCompleted(task));
            render();
            saveTasksAsync();
        });
        return row;
    }

    private void checkDueReminders() {
        if (!storageReady || webMode) return;
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        List<Task> due = new ArrayList<>();
        for (Task task : tasks) {
            if (task.completed || task.reminderLeadMinutes == null) continue;
            Instant fire = TaskLogic.reminderFireInstant(task, zone);
            if (fire == null || now.isBefore(fire)) continue;
            if (fire.toString().equals(task.reminderShownFire)) continue;
            due.add(task);
        }
        if (due.isEmpty()) return;
        for (Task task : due) {
            Instant fire = TaskLogic.reminderFireInstant(task, zone);
            if (fire != null) {
                replaceTask(task.withReminderShown(fire.toString(), Instant.now().toString()));
            }
        }
        saveTasksAsync();
        StringBuilder message = new StringBuilder();
        for (Task task : due) {
            if (message.length() > 0) message.append("\n\n");
            message.append("• ").append(task.title).append(" — ").append(dueLabel(task));
        }
        new AlertDialog.Builder(this)
                .setTitle(due.size() == 1 ? "Reminder" : "Reminders (" + due.size() + ")")
                .setMessage(message.toString())
                .setPositiveButton("Done", null)
                .show();
    }

    private void showTaskActions(Task task) {
        if (task == null || !canEdit()) return;
        String[] actions = {
                "Duplicate task", "Mark as done", "Move due date to today",
                "Move due date to tomorrow", "Clear due date",
                "Set high priority", "Set medium priority", "Set low priority",
                "Save as template"
        };
        new AlertDialog.Builder(this)
                .setTitle(task.title)
                .setItems(actions, (dialog, which) -> {
                    Task current = findTask(task.id);
                    if (current == null || !canEdit()) return;
                    try {
                        if (which == 0) {
                            String copyTitle = current.title;
                            if (!copyTitle.endsWith(" (copy)")) copyTitle += " (copy)";
                            tasks.add(TaskLogic.create(copyTitle, current.dueDate, current.priority));
                            render();
                            saveTasksAsync();
                            showToast("Task duplicated.");
                        } else if (which == 1) {
                            if (!current.completed) {
                                replaceTask(TaskLogic.toggleCompleted(current));
                                render();
                                saveTasksAsync();
                            }
                            showToast("Task marked done.");
                        } else if (which == 2) {
                            replaceTask(TaskLogic.update(current, current.title, LocalDate.now().toString(), current.priority));
                            render();
                            saveTasksAsync();
                            showToast("Due date moved to today.");
                        } else if (which == 3) {
                            replaceTask(TaskLogic.update(current, current.title, LocalDate.now().plusDays(1).toString(), current.priority));
                            render();
                            saveTasksAsync();
                            showToast("Due date moved to tomorrow.");
                        } else if (which == 4) {
                            replaceTask(TaskLogic.update(current, current.title, null, current.priority));
                            render();
                            saveTasksAsync();
                            showToast("Due date cleared.");
                        } else if (which == 5 || which == 6 || which == 7) {
                            String priority = which == 5 ? "high" : which == 6 ? "medium" : "low";
                            replaceTask(TaskLogic.update(current, current.title, current.dueDate, priority));
                            render();
                            saveTasksAsync();
                            showToast("Priority set to " + priority + ".");
                        } else if (which == 8) {
                            if (taskTemplates.size() >= TaskTemplateLogic.MAX_TEMPLATES) throw new IllegalStateException("Template limit reached.");
                            taskTemplates.add(TaskTemplateLogic.create(current.title, current.dueDate, current.priority));
                            saveTasksAsync("Saving templates");
                            render();
                            showToast("Template saved.");
                        }
                    } catch (Exception exception) {
                        showToast("Could not update this task.");
                    }
                }).show();
    }

    private String dueLabel(Task task) {
        if (task.dueDate == null) return "No due date";
        LocalDate due = LocalDate.parse(task.dueDate);
        LocalDate today = LocalDate.now();
        String label;
        if (due.isBefore(today)) label = "Overdue · " + TaskLogic.formatDate(due);
        else if (due.equals(today)) label = "Due today";
        else label = "Due " + TaskLogic.formatDate(due);
        if (task.dueTime != null) label += " · " + TaskLogic.formatTime(task.dueTime);
        return label;
    }

    private String reminderLabel(Task task) {
        if (task.reminderLeadMinutes == null) return null;
        if (task.reminderLeadMinutes == 0) return "Reminder at due time";
        if (task.reminderLeadMinutes % 1440 == 0) return "Reminder 1 day before";
        if (task.reminderLeadMinutes % 60 == 0) return "Reminder " + (task.reminderLeadMinutes / 60) + " hour before";
        return "Reminder " + task.reminderLeadMinutes + " minutes before";
    }

    private int dueColor(Task task) {
        if (task.completed || task.dueDate == null) return palette.muted;
        LocalDate due = LocalDate.parse(task.dueDate);
        if (due.isBefore(LocalDate.now())) return palette.danger;
        if (due.equals(LocalDate.now())) return palette.warning;
        if ("high".equals(task.priority)) return palette.danger;
        return palette.muted;
    }

    private boolean canEdit() {
        return storageReady && !attachmentBusy && !portableBusy;
    }

    private void showSubtasksDialog(Task task) {
        if (task == null || !canEdit()) return;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        final Runnable[] rerender = new Runnable[1];
        rerender[0] = () -> {
            list.removeAllViews();
            Task current = findTask(task.id);
            if (current == null || current.subtasks.isEmpty()) {
                list.addView(text("No subtasks yet.", 12, palette.muted, Typeface.NORMAL));
                return;
            }
            for (Subtask subtask : current.subtasks) {
                CheckBox subtaskCheck = new CheckBox(this);
                subtaskCheck.setText(subtask.title);
                subtaskCheck.setTextColor(subtask.done ? palette.muted : palette.text);
                subtaskCheck.setTextSize(14 * textScale);
                subtaskCheck.setChecked(subtask.done);
                subtaskCheck.setContentDescription((subtask.done ? "Mark subtask as not done: " : "Mark subtask as done: ")
                        + subtask.title);
                subtaskCheck.setOnCheckedChangeListener((button, checked) -> {
                    if (!canEdit() || checked == subtask.done) return;
                    Task latest = findTask(task.id);
                    if (latest == null) return;
                    try {
                        replaceTask(TaskLogic.toggleSubtask(latest, subtask.id));
                        render();
                        saveTasksAsync();
                    } catch (IllegalArgumentException exception) {
                        showToast("Could not update this subtask.");
                    }
                    rerender[0].run();
                });
                list.addView(subtaskCheck, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            }
        };
        rerender[0].run();
        Button addSubtask = compactButton("Add subtask", false);
        addSubtask.setContentDescription("Add a subtask to task: " + task.title);
        addSubtask.setOnClickListener(view -> {
            Task current = findTask(task.id);
            if (current == null || !canEdit()) return;
            if (current.subtasks.size() >= TaskLogic.MAX_SUBTASKS) {
                showToast("A task can have at most " + TaskLogic.MAX_SUBTASKS + " subtasks.");
                return;
            }
            EditText input = new EditText(this);
            input.setHint("Subtask name");
            input.setSingleLine(true);
            input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(TaskLogic.MAX_SUBTASK_TITLE) });
            input.setContentDescription("New subtask name");
            new AlertDialog.Builder(this)
                    .setTitle("Add subtask")
                    .setView(input)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Add", (dialog, which) -> {
                        String title = input.getText() == null ? "" : input.getText().toString().trim();
                        Task latest = findTask(task.id);
                        if (latest == null || !canEdit()) return;
                        try {
                            replaceTask(TaskLogic.addSubtask(latest, title));
                            render();
                            saveTasksAsync();
                            rerender[0].run();
                        } catch (IllegalArgumentException exception) {
                            showToast(exception.getMessage());
                        }
                    })
                    .show();
        });
        content.addView(addSubtask, topMargin(dp(6)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("Subtasks · " + task.title)
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showNotesDialog(Task task) {
        if (task == null) return;
        TextView notes = text(task.notes, 14, palette.text, Typeface.NORMAL);
        notes.setLineSpacing(dp(2), 1f);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        content.addView(notes);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("Note · " + task.title)
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showAttachmentManager(Task task) {
        if (!canEdit()) return;
        Task current = findTask(task.id);
        if (current == null) return;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        TextView limits = text("Up to 5 files per task and 100 files overall · 20 MiB each · 100 MiB total. Files are encrypted here. A limited format allowlist can be sent to another app only after you choose Open; all files remain untrusted, and unknown or active formats cannot be opened by Daymark.",
                12, palette.muted, Typeface.NORMAL);
        limits.setLineSpacing(dp(2), 1f);
        content.addView(limits, bottomMargin(dp(9)));
        if (current.attachments.isEmpty()) {
            content.addView(text("No files attached yet.", 13, palette.muted, Typeface.NORMAL), bottomMargin(dp(8)));
        }
        for (AttachmentRef attachment : current.attachments) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(9), dp(7), dp(9), dp(7));
            item.setBackground(shape(palette.surface, 9, palette.line));
            boolean present = attachmentStore.exists(attachment.id);
            String detail = attachment.displayName + "\n" + formatFileSize(attachment.sizeBytes)
                    + " · " + attachment.mimeType + (present ? " · stored" : " · file unavailable");
            TextView label = text(detail, 12, present ? palette.text : palette.danger, Typeface.NORMAL);
            label.setContentDescription(detail);
            item.addView(label, bottomMargin(dp(3)));
            if (AttachmentLogic.isSafeToOpenExternally(attachment)) {
                Button open = compactButton("Open with another app", false);
                open.setContentDescription("Choose another app to open " + attachment.displayName);
                open.setEnabled(present);
                open.setOnClickListener(view -> confirmExternalOpen(attachment));
                item.addView(open, bottomMargin(dp(3)));
            }
            Button remove = compactButton("Remove attachment", true);
            remove.setContentDescription("Remove attachment " + attachment.displayName);
            remove.setOnClickListener(view -> {
                confirmRemoveAttachment(current.id, attachment);
            });
            item.addView(remove);
            content.addView(item, bottomMargin(dp(7)));
        }
        Button add = compactButton("Attach a photo, audio, video, or document", false);
        add.setContentDescription("Choose a photo, audio, video, or document using Android's document picker");
        add.setOnClickListener(view -> {
            Task latest = findTask(current.id);
            if (latest != null && AttachmentLogic.canAddToTask(latest, tasks)) {
                openAttachmentPicker(latest.id);
            } else {
                showToast("The attachment limit has been reached.");
            }
        });
        content.addView(add, topMargin(dp(4)));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("Attachments").setView(scroll)
                .setPositiveButton("Done", null).show();
    }

    private void confirmExternalOpen(AttachmentRef attachment) {
        if (!AttachmentLogic.isSafeToOpenExternally(attachment) || !attachmentStore.exists(attachment.id)) {
            showToast("No safe external opener is available for this file.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Share with another app?")
                .setMessage("Daymark will stream a read-only decrypted copy of “" + attachment.displayName
                        + "” to the app you choose. That app may retain it. Files are untrusted; Daymark never opens them automatically or executes them.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose app", (dialog, which) -> openWithAnotherApp(attachment))
                .show();
    }

    private void openWithAnotherApp(AttachmentRef attachment) {
        if (!AttachmentLogic.isSafeToOpenExternally(attachment) || !attachmentStore.exists(attachment.id)) {
            showToast("The attachment is no longer available.");
            return;
        }
        Uri uri = new Uri.Builder().scheme("content")
                .authority(getPackageName() + ".attachments")
                .appendPath(attachment.id).build();
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, attachment.mimeType);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        view.setClipData(ClipData.newRawUri(attachment.displayName, uri));
        try {
            startActivity(Intent.createChooser(view, "Choose an app to open attachment"));
        } catch (android.content.ActivityNotFoundException exception) {
            showToast("No app can open this file type. It remains attached in Daymark.");
        }
    }

    private void openAttachmentPicker(String taskId) {
        if (!canEdit()) return;
        Task task = findTask(taskId);
        if (task == null || !AttachmentLogic.canAddToTask(task, tasks)) {
            showToast("The attachment limit has been reached.");
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        pendingAttachmentTaskId = taskId;
        try {
            startActivityForResult(picker, REQUEST_ATTACH_DOCUMENT);
        } catch (android.content.ActivityNotFoundException exception) {
            pendingAttachmentTaskId = null;
            showToast("Android's document picker is not available on this device.");
        }
    }

    private void importAttachment(String taskId, Uri selectedUri) {
        if (!canEdit()) return;
        Task target = findTask(taskId);
        if (target == null || !AttachmentLogic.canAddToTask(target, tasks)) {
            showToast("The task or available attachment space is no longer available.");
            return;
        }
        attachmentBusy = true;
        ++saveRevision;
        synchronized (attachmentCancelLock) {
            attachmentCancelRequested = false;
            attachmentCanCancel = true;
        }
        cancelAttachmentButton.setText("Cancel import");
        cancelAttachmentButton.setEnabled(true);
        cancelAttachmentButton.setVisibility(View.VISIBLE);
        List<Task> snapshot = new ArrayList<>(tasks);
        storageStatus.setText("Importing encrypted attachment…");
        storageStatus.setTextColor(palette.muted);
        render();
        storageExecutor.execute(() -> {
            String newId = AttachmentBlobStore.newId();
            List<Task> committed = null;
            Exception failure = null;
            boolean saveAttempted = false;
            boolean cleanupDeferred = false;
            try {
                synchronized (AndroidAttachmentStore.transactionLock()) {
                    AndroidAttachmentStore.Imported imported = attachmentStore.importSelected(selectedUri, taskId, newId,
                            AttachmentLogic.remainingBytes(snapshot), () -> {
                                synchronized (attachmentCancelLock) { return attachmentCancelRequested; }
                            });
                    Task current = findTaskIn(snapshot, taskId);
                    if (current == null) throw new AttachmentQuotaException();
                    List<AttachmentRef> references = new ArrayList<>(current.attachments);
                    references.add(new AttachmentRef(imported.id, imported.displayName, imported.mimeType,
                            imported.sizeBytes));
                    List<Task> next = replaceTaskInSnapshot(snapshot, current.withAttachments(references));
                    if (!TaskLogic.isValidTaskList(next)) throw new AttachmentQuotaException();
                    synchronized (attachmentCancelLock) {
                        if (attachmentCancelRequested) throw new AttachmentBlobStore.CancelledException();
                        attachmentCanCancel = false;
                    }
                    postActivityCallback(() -> {
                        storageStatus.setText("Saving attachment metadata…");
                        if (cancelAttachmentButton != null) cancelAttachmentButton.setVisibility(View.GONE);
                    });
                    saveAttempted = true;
                    taskStore.save(next);
                    committed = next;
                }
            } catch (Exception exception) {
                failure = exception;
                if (saveAttempted) {
                    // The task snapshot may have committed before a later verification failed.
                    // Keep the payload until a successful load can reconcile its references.
                    cleanupDeferred = true;
                } else {
                    try {
                        synchronized (AndroidAttachmentStore.transactionLock()) { attachmentStore.delete(newId); }
                    } catch (Exception ignored) { cleanupDeferred = true; }
                }
            } finally {
                synchronized (attachmentCancelLock) { attachmentCanCancel = false; }
            }
            List<Task> saved = committed;
            Exception error = failure;
            boolean saveOutcomeAmbiguous = saveAttempted && error != null;
            boolean cleanupPending = cleanupDeferred;
            postActivityCallback(() -> {
                attachmentBusy = false;
                if (cancelAttachmentButton != null) cancelAttachmentButton.setVisibility(View.GONE);
                if (error == null && saved != null) {
                    tasks.clear();
                    tasks.addAll(saved);
                    lastSavedTasks.clear();
                    lastSavedTasks.addAll(saved);
                    storageReady = true;
                    storageSaveFailed = false;
                    storageStatus.setText("Encrypted storage ready");
                    storageStatus.setTextColor(palette.accent);
                    showToast("Attachment saved privately on this device.");
                } else if (saveOutcomeAmbiguous) {
                    storageReady = false;
                    storageSaveFailed = true;
                    storageStatus.setText("Attachment save could not be verified");
                    storageStatus.setTextColor(palette.danger);
                    captureFeedback.setText("The attachment save could not be verified. No automatic reset was performed; editing is paused. Reopen Daymark to reconcile the saved snapshot.");
                    showToast("Attachment save could not be verified. The encrypted copy was retained; reopen Daymark to check saved data.");
                } else {
                    storageStatus.setText(storageReady ? "Encrypted storage ready" : "Saved tasks unavailable");
                    storageStatus.setTextColor(storageReady ? palette.accent : palette.danger);
                    showToast(attachmentFailureMessage(error, cleanupPending));
                }
                render();
            });
        });
    }

    private void requestAttachmentCancel() {
        boolean accepted;
        synchronized (attachmentCancelLock) {
            accepted = attachmentBusy && attachmentCanCancel;
            if (accepted) attachmentCancelRequested = true;
        }
        if (accepted) {
            cancelAttachmentButton.setText("Cancelling…");
            cancelAttachmentButton.setEnabled(false);
        } else {
            showToast("The attachment is already being saved and can no longer be cancelled.");
        }
    }

    private String attachmentFailureMessage(Exception failure, boolean cleanupPending) {
        String message;
        if (failure instanceof AttachmentBlobStore.CancelledException) {
            message = "Attachment import cancelled. No attachment was saved.";
        } else if (failure instanceof AttachmentBlobStore.StorageSpaceException) {
            message = "There is not enough free space to copy this attachment safely.";
        } else if (failure instanceof AttachmentBlobStore.FileLimitException) {
            message = "That file exceeds the 20 MiB per-file limit.";
        } else if (failure instanceof AttachmentBlobStore.StorageLimitException
                || failure instanceof AttachmentQuotaException) {
            message = "The attachment count or 100 MiB total storage limit has been reached.";
        } else if (failure instanceof SecurityException || failure instanceof java.io.IOException) {
            message = "Daymark could not read or safely copy that file. It may be unavailable from the selected provider.";
        } else {
            message = "The attachment could not be saved.";
        }
        return cleanupPending ? message + " An encrypted copy was retained; the next successful task load will keep it if referenced or remove it if orphaned." : message;
    }

    private void confirmRemoveAttachment(String taskId, AttachmentRef attachment) {
        new AlertDialog.Builder(this).setTitle("Remove attachment?")
                .setMessage("Remove “" + attachment.displayName + "” from this task and delete its local encrypted copy?")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Remove", (dialog, which) -> removeAttachment(taskId, attachment.id))
                .show();
    }

    private void removeAttachment(String taskId, String attachmentId) {
        if (!canEdit()) return;
        Task current = findTask(taskId);
        if (current == null) return;
        List<AttachmentRef> remaining = new ArrayList<>();
        for (AttachmentRef attachment : current.attachments) {
            if (!attachment.id.equals(attachmentId)) remaining.add(attachment);
        }
        if (remaining.size() == current.attachments.size()) return;
        List<Task> snapshot = replaceTaskInSnapshot(new ArrayList<>(tasks), current.withAttachments(remaining));
        attachmentBusy = true;
        long revision = ++saveRevision;
        storageStatus.setText("Removing attachment…");
        storageStatus.setTextColor(palette.muted);
        render();
        storageExecutor.execute(() -> {
            Exception failure = null;
            boolean deleted = false;
            try {
                synchronized (AndroidAttachmentStore.transactionLock()) {
                    taskStore.save(snapshot);
                    try {
                        attachmentStore.delete(attachmentId);
                        deleted = true;
                    } catch (Exception cleanupError) {
                        // The unreferenced encrypted payload is retried by startup cleanup.
                    }
                }
            } catch (Exception exception) {
                failure = exception;
            }
            Exception error = failure;
            boolean payloadDeleted = deleted;
            postActivityCallback(() -> {
                attachmentBusy = false;
                if (error == null) {
                    tasks.clear();
                    tasks.addAll(snapshot);
                    lastSavedTasks.clear();
                    lastSavedTasks.addAll(snapshot);
                    storageReady = true;
                    storageSaveFailed = false;
                    storageStatus.setText("Encrypted storage ready");
                    storageStatus.setTextColor(palette.accent);
                    showToast(payloadDeleted ? "Attachment removed and local copy deleted."
                            : "Attachment removed; local cleanup will retry on next launch.");
                } else if (revision == saveRevision) {
                    storageReady = false;
                    storageSaveFailed = true;
                    storageStatus.setText("Attachment removal not saved");
                    storageStatus.setTextColor(palette.danger);
                    captureFeedback.setText("Attachment removal was not saved. Editing is paused; the previous saved task remains.");
                    showToast("Attachment removal was not saved. Editing is paused.");
                }
                render();
            });
        });
    }

    private Task findTask(String id) {
        for (Task task : tasks) if (task.id.equals(id)) return task;
        return null;
    }

    private Task findTaskIn(List<Task> snapshot, String id) {
        for (Task task : snapshot) if (task.id.equals(id)) return task;
        return null;
    }

    private List<Task> replaceTaskInSnapshot(List<Task> snapshot, Task replacement) {
        for (int index = 0; index < snapshot.size(); index++) {
            if (snapshot.get(index).id.equals(replacement.id)) {
                snapshot.set(index, replacement);
                return snapshot;
            }
        }
        throw new IllegalArgumentException("The task is no longer available.");
    }

    private java.util.Set<String> attachmentIds(List<Task> source) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        if (source != null) {
            for (Task task : source) for (AttachmentRef attachment : task.attachments) ids.add(attachment.id);
        }
        return ids;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private static final class AttachmentQuotaException extends Exception { }

    private void showTaskTemplatesDialog() {
        if (!canEdit()) {
            showToast("Encrypted task storage is unavailable; templates are paused.");
            return;
        }
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(18), dp(4), dp(18), dp(4));
        if (taskTemplates.isEmpty()) {
            TextView empty = text("No templates saved yet. Create one here, or save a template from task details. Templates stay encrypted on this device.",
                    14, palette.muted, Typeface.NORMAL);
            rows.addView(empty, bottomMargin(dp(8)));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(rows);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Task templates")
                .setView(scroll)
                .setNegativeButton("Close", null)
                .setNeutralButton("New template", null)
                .create();
        for (TaskTemplate template : new ArrayList<>(taskTemplates)) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(9), dp(7), dp(5), dp(7));
            row.setBackground(shape(palette.background, 10, palette.line));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.addView(text(template.title, 14, palette.text, Typeface.BOLD));
            String due = template.dueDate == null ? "No due date" : dateButtonLabel(template.dueDate);
            copy.addView(text(due + " · " + template.priority.toUpperCase(Locale.ROOT) + " priority",
                    12, palette.muted, Typeface.NORMAL));
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button use = compactButton("Use", false);
            use.setContentDescription("Review and edit " + template.title + " before creating a task");
            use.setOnClickListener(view -> {
                dialog.dismiss();
                showTaskEditorFromTemplate(template);
            });
            row.addView(use, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
            Button remove = compactButton("Remove", false);
            remove.setContentDescription("Remove the " + template.title + " task template");
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
            removeParams.leftMargin = dp(4);
            row.addView(remove, removeParams);
            remove.setOnClickListener(view -> new AlertDialog.Builder(this)
                    .setTitle("Remove template?")
                    .setMessage("Remove \"" + template.title + "\" from this device? Existing tasks will not change.")
                    .setNegativeButton("Keep", null)
                    .setPositiveButton("Remove", (confirm, which) -> {
                        removeTaskTemplate(template.id);
                        dialog.dismiss();
                        showTaskTemplatesDialog();
                    })
                    .show());
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(7);
            rows.addView(row, rowParams);
        }
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(view -> {
                    dialog.dismiss();
                    showNewTemplateEditor();
                }));
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.min(dp(480), dp(120 + taskTemplates.size() * 88)));
    }

    private void removeTaskTemplate(String id) {
        for (int index = 0; index < taskTemplates.size(); index++) {
            if (taskTemplates.get(index).id.equals(id)) {
                taskTemplates.remove(index);
                render();
                saveTasksAsync("Saving encrypted templates…");
                showToast("Template removed. Existing tasks are unchanged.");
                return;
            }
        }
    }

    private boolean saveTaskTemplate(String title, String dueDate, String priority,
                                     EditText titleInput, TextView validation) {
        if (!canEdit()) {
            showToast("Encrypted task storage is unavailable; templates are paused.");
            return false;
        }
        if (taskTemplates.size() >= TaskTemplateLogic.MAX_TEMPLATES) {
            validation.setText("You can save up to 100 templates. Remove one before adding another.");
            validation.setVisibility(View.VISIBLE);
            return false;
        }
        try {
            taskTemplates.add(TaskTemplateLogic.create(title, dueDate, priority));
            render();
            saveTasksAsync("Saving encrypted templates…");
            showToast("Template saved on this device. No task was created.");
            return true;
        } catch (IllegalArgumentException exception) {
            validation.setText(exception.getMessage());
            validation.setVisibility(View.VISIBLE);
            titleInput.setError(exception.getMessage());
            titleInput.requestFocus();
            return false;
        }
    }

    private void showTaskEditor(Task editing) {
        showTaskEditor(editing, "", editing == null ? null : editing.dueDate,
                editing == null ? "medium" : editing.priority, null, false);
    }

    private void showTaskEditor(Task editing, String draftTitle) {
        showTaskEditor(editing, draftTitle, editing == null ? null : editing.dueDate,
                editing == null ? "medium" : editing.priority, null, false);
    }

    private void showTaskEditorFromTemplate(TaskTemplate template) {
        showTaskEditor(null, template.title, template.dueDate, template.priority, template, false);
    }

    private void showNewTemplateEditor() {
        showTaskEditor(null, "", null, "medium", null, true);
    }

    private void showTaskEditor(Task editing, String draftTitle, String initialDueDate,
                                String initialPriority, TaskTemplate sourceTemplate, boolean templateOnly) {
        if (webMode) return;
        if (!canEdit()) {
            showToast("Encrypted task storage is unavailable; edits are paused.");
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(8), dp(22), dp(5));

        EditText titleInput = new EditText(this);
        titleInput.setSingleLine(true);
        titleInput.setTextSize(16 * textScale);
        titleInput.setHint("What do you want to get done?");
        titleInput.setContentDescription("Task name. You can edit this before saving.");
        titleInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(160) });
        titleInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (editing != null) titleInput.setText(editing.title);
        else if (draftTitle != null && !draftTitle.trim().isEmpty()) titleInput.setText(draftTitle.trim());
        form.addView(titleInput, bottomMargin(dp(4)));
        TextView titleCounter = text((titleInput.getText() == null ? 0 : titleInput.getText().length()) + " / 160",
                11, palette.muted, Typeface.NORMAL);
        titleCounter.setGravity(Gravity.END);
        form.addView(titleCounter, bottomMargin(dp(10)));
        titleInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                titleCounter.setText((s == null ? 0 : s.length()) + " / 160");
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        EditText notesInput = new EditText(this);
        notesInput.setTextSize(14 * textScale);
        notesInput.setHint("Notes (optional)");
        notesInput.setContentDescription("Optional notes for this task, up to 4000 characters");
        notesInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(TaskLogic.MAX_NOTES_CHARS) });
        notesInput.setMinLines(2);
        notesInput.setGravity(Gravity.TOP | Gravity.START);
        if (editing != null) notesInput.setText(editing.notes);

        TextView fieldLabel = text("Due date (optional)", 13, palette.muted, Typeface.BOLD);
        fieldLabel.setLetterSpacing(0.08f);
        form.addView(fieldLabel, bottomMargin(dp(4)));

        final String[] selectedDate = { initialDueDate };
        LinearLayout dateActions = new LinearLayout(this);
        dateActions.setGravity(Gravity.CENTER_VERTICAL);
        Button dateButton = compactButton(dateButtonLabel(selectedDate[0]), false);
        dateButton.setOnClickListener(view -> showDatePicker(selectedDate, dateButton));
        dateActions.addView(dateButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button clearDate = compactButton("Clear", false);
        dateActions.addView(clearDate, new LinearLayout.LayoutParams(dp(76), dp(48)));
        form.addView(dateActions, bottomMargin(dp(6)));

        LinearLayout quickDates = new LinearLayout(this);
        quickDates.setGravity(Gravity.CENTER_VERTICAL);
        Button todayButton = compactButton("Today", false);
        Button tomorrowButton = compactButton("Tomorrow", false);
        Button nextWeekButton = compactButton("Next week", false);
        todayButton.setOnClickListener(view -> {
            selectedDate[0] = LocalDate.now().toString();
            dateButton.setText(dateButtonLabel(selectedDate[0]));
        });
        tomorrowButton.setOnClickListener(view -> {
            selectedDate[0] = LocalDate.now().plusDays(1).toString();
            dateButton.setText(dateButtonLabel(selectedDate[0]));
        });
        nextWeekButton.setOnClickListener(view -> {
            selectedDate[0] = LocalDate.now().plusWeeks(1).toString();
            dateButton.setText(dateButtonLabel(selectedDate[0]));
        });
        quickDates.addView(todayButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams tomorrowParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        tomorrowParams.leftMargin = dp(5);
        quickDates.addView(tomorrowButton, tomorrowParams);
        LinearLayout.LayoutParams nextWeekParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        nextWeekParams.leftMargin = dp(5);
        quickDates.addView(nextWeekButton, nextWeekParams);
        form.addView(quickDates, bottomMargin(dp(14)));

        if (!templateOnly) form.addView(notesInput, bottomMargin(dp(10)));

        final String[] selectedTime = { editing == null ? null : editing.dueTime };
        LinearLayout timeActions = new LinearLayout(this);
        timeActions.setGravity(Gravity.CENTER_VERTICAL);
        Button timeButton = compactButton(selectedTime[0] == null
                ? "No time" : TaskLogic.formatTime(selectedTime[0]), false);
        timeButton.setContentDescription("Choose an optional due time");
        timeButton.setOnClickListener(view -> showTimePicker(selectedTime, timeButton));
        timeActions.addView(timeButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button clearTime = compactButton("Clear", false);
        clearTime.setContentDescription("Clear the optional due time");
        clearTime.setOnClickListener(view -> {
            selectedTime[0] = null;
            timeButton.setText("No time");
        });
        timeActions.addView(clearTime, new LinearLayout.LayoutParams(dp(76), dp(48)));
        Runnable syncTimeWithDate = () -> {
            if (selectedDate[0] == null && selectedTime[0] != null) {
                selectedTime[0] = null;
                timeButton.setText("No time");
            }
        };
        clearDate.setOnClickListener(view -> {
            selectedDate[0] = null;
            dateButton.setText("Choose a date");
            syncTimeWithDate.run();
        });
        if (!templateOnly) {
            TextView timeLabel = text("Due time (optional)", 13, palette.muted, Typeface.BOLD);
            timeLabel.setLetterSpacing(0.08f);
            form.addView(timeLabel, bottomMargin(dp(4)));
            form.addView(timeActions, bottomMargin(dp(6)));
        }

        TextView priorityLabel = text("Priority", 13, palette.muted, Typeface.BOLD);
        form.addView(priorityLabel, bottomMargin(dp(4)));
        Spinner prioritySpinner = new Spinner(this);
        prioritySpinner.setContentDescription("Choose low, medium, or high priority");
        String[] priorities = { "Low priority", "Medium priority", "High priority" };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, priorities) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getView(position, convertView, parent);
                item.setTextSize(14 * textScale);
                item.setTextColor(palette.text);
                return item;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getDropDownView(position, convertView, parent);
                item.setTextSize(14 * textScale);
                item.setTextColor(palette.text);
                return item;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        prioritySpinner.setAdapter(adapter);
        prioritySpinner.setMinimumHeight(dp(48));
        prioritySpinner.setSelection(priorityIndex(initialPriority));
        form.addView(prioritySpinner, bottomMargin(dp(8)));

        Spinner reminderSpinner = new Spinner(this);
        reminderSpinner.setContentDescription("Choose when Daymark should remind you about this task");
        String[] reminderOptions = { "No reminder", "At due time", "30 minutes before", "1 hour before", "1 day before" };
        ArrayAdapter<String> reminderAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, reminderOptions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getView(position, convertView, parent);
                item.setTextSize(14 * textScale);
                item.setTextColor(palette.text);
                return item;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView item = (TextView) super.getDropDownView(position, convertView, parent);
                item.setTextSize(14 * textScale);
                item.setTextColor(palette.text);
                return item;
            }
        };
        reminderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reminderSpinner.setAdapter(reminderAdapter);
        reminderSpinner.setMinimumHeight(dp(48));
        reminderSpinner.setSelection(reminderIndex(editing == null ? null : editing.reminderLeadMinutes));
        TextView reminderHint = text(
                "Daymark shows a reminder when you open the app after the reminder time. It does not post notifications.",
                11, palette.muted, Typeface.NORMAL);
        reminderHint.setLineSpacing(dp(2), 1f);
        if (!templateOnly) {
            TextView reminderLabel = text("Reminder", 13, palette.muted, Typeface.BOLD);
            reminderLabel.setLetterSpacing(0.08f);
            form.addView(reminderLabel, bottomMargin(dp(4)));
            form.addView(reminderSpinner, bottomMargin(dp(4)));
            form.addView(reminderHint, bottomMargin(dp(8)));
        }

        final List<Subtask> draftSubtasks = new ArrayList<>();
        if (editing != null) draftSubtasks.addAll(editing.subtasks);
        LinearLayout subtasksList = new LinearLayout(this);
        subtasksList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout subtasksSection = new LinearLayout(this);
        subtasksSection.setOrientation(LinearLayout.VERTICAL);
        TextView subtasksLabel = text("Subtasks", 13, palette.muted, Typeface.BOLD);
        subtasksLabel.setLetterSpacing(0.08f);
        subtasksSection.addView(subtasksLabel, bottomMargin(dp(4)));
        subtasksSection.addView(subtasksList);
        Button addSubtaskButton = compactButton("Add subtask", false);
        addSubtaskButton.setContentDescription("Add a subtask to this task");
        addSubtaskButton.setOnClickListener(view -> showAddSubtaskDialog(draftSubtasks, subtasksList));
        subtasksSection.addView(addSubtaskButton, topMargin(dp(6)));
        renderDraftSubtasks(subtasksList, draftSubtasks);
        subtasksSection.setVisibility(View.GONE);
        CheckBox moreOptions = new CheckBox(this);
        moreOptions.setText("More options (subtasks)");
        moreOptions.setTextColor(palette.text);
        moreOptions.setTextSize(14 * textScale);
        moreOptions.setContentDescription("Show the optional subtask checklist editor");
        moreOptions.setOnCheckedChangeListener((button, checked) ->
                subtasksSection.setVisibility(checked ? View.VISIBLE : View.GONE));
        if (!templateOnly) {
            form.addView(moreOptions, bottomMargin(dp(6)));
            form.addView(subtasksSection, bottomMargin(dp(4)));
        }

        TextView validation = text("Please enter a task title.", 12, palette.danger, Typeface.NORMAL);
        validation.setVisibility(View.GONE);
        form.addView(validation);

        ScrollView formScroll = new ScrollView(this);
        formScroll.setFillViewport(false);
        formScroll.addView(form);
        AlertDialog.Builder editorBuilder = new AlertDialog.Builder(this)
                .setTitle(templateOnly ? "New task template"
                        : sourceTemplate != null ? "Review task from template"
                        : editing == null ? "Add task details" : "Edit task")
                .setView(formScroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(templateOnly ? "Save template"
                        : editing != null ? "Save changes"
                        : sourceTemplate != null ? "Create task" : "Add task", null);
        if (!templateOnly) editorBuilder.setNeutralButton("Save as template", null);
        AlertDialog dialog = editorBuilder.create();
        dialog.setOnShowListener(ignored -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            titleInput.setOnEditorActionListener((view, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveButton.performClick();
                    return true;
                }
                return false;
            });
            saveButton.setOnClickListener(view -> {
                String normalized = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
                if (normalized.isEmpty()) {
                    validation.setVisibility(View.VISIBLE);
                    titleInput.setError("Enter a task title");
                    titleInput.requestFocus();
                    return;
                }
                String priority = priorityValue(prioritySpinner.getSelectedItemPosition());
                if (!templateOnly) {
                    String normalizedKey = normalized.toLowerCase(Locale.ROOT);
                    for (Task existing : tasks) {
                        if (editing != null && existing.id.equals(editing.id)) continue;
                        if (existing.title != null
                                && existing.title.trim().toLowerCase(Locale.ROOT).equals(normalizedKey)) {
                            new AlertDialog.Builder(this)
                                    .setTitle("Possible duplicate task")
                                    .setMessage("A task with the same title already exists. Add another one anyway?")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Add anyway", (d, w) ->
                                            saveTaskFromEditor(dialog, editing, normalized, selectedDate[0], priority, sourceTemplate,
                                                    notesInput.getText() == null ? "" : notesInput.getText().toString(),
                                                    selectedTime[0],
                                                    reminderLeadValue(reminderSpinner.getSelectedItemPosition()),
                                                    draftSubtasks))
                                    .show();
                            return;
                        }
                    }
                }
                if (templateOnly) {
                    if (saveTaskTemplate(normalized, selectedDate[0], priority, titleInput, validation)) {
                        dialog.dismiss();
                    }
                    return;
                }
                try {
                    if (editing == null) {
                        tasks.add(sourceTemplate == null
                                ? TaskLogic.create(normalized, selectedDate[0], priority,
                                        notesInput.getText() == null ? "" : notesInput.getText().toString(),
                                        selectedTime[0],
                                        reminderLeadValue(reminderSpinner.getSelectedItemPosition()),
                                        draftSubtasks)
                                : TaskTemplateLogic.instantiate(sourceTemplate, normalized, selectedDate[0], priority));
                        if (sourceTemplate == null) quickCaptureInput.setText("");
                        captureFeedback.setText(sourceTemplate == null
                                ? "Task added. You can edit it later in your list."
                                : "Task created from the reviewed template. You can edit it later in your list.");
                    } else {
                        replaceTask(TaskLogic.update(editing, normalized,
                                notesInput.getText() == null ? "" : notesInput.getText().toString(),
                                selectedDate[0], selectedTime[0], priority,
                                reminderLeadValue(reminderSpinner.getSelectedItemPosition()),
                                draftSubtasks));
                    }
                    dialog.dismiss();
                    render();
                    saveTasksAsync();
                    showToast(editing == null
                            ? sourceTemplate == null ? "Task added." : "Task created from template."
                            : "Task updated.");
                } catch (IllegalArgumentException exception) {
                    validation.setText(exception.getMessage());
                    validation.setVisibility(View.VISIBLE);
                }
            });
            if (!templateOnly) {
                Button saveTemplateButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                saveTemplateButton.setOnClickListener(view -> {
                    String normalized = titleInput.getText() == null
                            ? "" : titleInput.getText().toString().trim();
                    if (normalized.isEmpty()) {
                        validation.setText("Please enter a task title.");
                        validation.setVisibility(View.VISIBLE);
                        titleInput.setError("Enter a task title");
                        titleInput.requestFocus();
                        return;
                    }
                    String priority = priorityValue(prioritySpinner.getSelectedItemPosition());
                    if (saveTaskTemplate(normalized, selectedDate[0], priority, titleInput, validation)) {
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        titleInput.requestFocus();
    }

    private void saveTaskFromEditor(AlertDialog dialog, Task editing, String normalized,
                                    String selectedDate, String priority, TaskTemplate sourceTemplate,
                                    String notes, String selectedTime, Integer reminderLead,
                                    List<Subtask> subtasks) {
        try {
            if (editing == null) {
                tasks.add(sourceTemplate == null
                        ? TaskLogic.create(normalized, selectedDate, priority, notes,
                                selectedTime, reminderLead, subtasks)
                        : TaskTemplateLogic.instantiate(sourceTemplate, normalized, selectedDate, priority));
                if (sourceTemplate == null) quickCaptureInput.setText("");
                captureFeedback.setText(sourceTemplate == null
                        ? "Task added. You can edit it later in your list."
                        : "Task created from the reviewed template. You can edit it later in your list.");
            } else {
                replaceTask(TaskLogic.update(editing, normalized, notes, selectedDate,
                        selectedTime, priority, reminderLead, subtasks));
            }
            dialog.dismiss();
            render();
            saveTasksAsync();
            showToast(editing == null
                    ? sourceTemplate == null ? "Task added." : "Task created from template."
                    : "Task updated.");
        } catch (IllegalArgumentException exception) {
            showToast(exception.getMessage());
        }
    }

    private void showDatePicker(String[] selectedDate, Button dateButton) {
        LocalDate initial = selectedDate[0] == null ? LocalDate.now() : LocalDate.parse(selectedDate[0]);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate[0] = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);
            dateButton.setText(dateButtonLabel(selectedDate[0]));
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        picker.show();
    }

    private String dateButtonLabel(String isoDate) {
        if (isoDate == null) return "Choose a date";
        return DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()).format(LocalDate.parse(isoDate));
    }

    private void showTimePicker(String[] selectedTime, Button timeButton) {
        java.time.LocalTime initial = TaskDuePresets.isTimeOnly(selectedTime[0])
                ? java.time.LocalTime.parse(selectedTime[0]) : java.time.LocalTime.now();
        TimePickerDialog picker = new TimePickerDialog(this, (view, hour, minute) -> {
            selectedTime[0] = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
            timeButton.setText(TaskLogic.formatTime(selectedTime[0]));
        }, initial.getHour(), initial.getMinute(), false);
        picker.show();
    }

    private int reminderIndex(Integer leadMinutes) {
        if (leadMinutes == null) return 0;
        for (int index = 0; index < TaskLogic.REMINDER_LEADS.length; index++) {
            if (TaskLogic.REMINDER_LEADS[index] == leadMinutes) return index + 1;
        }
        return 0;
    }

    private Integer reminderLeadValue(int spinnerIndex) {
        if (spinnerIndex <= 0 || spinnerIndex > TaskLogic.REMINDER_LEADS.length) return null;
        return TaskLogic.REMINDER_LEADS[spinnerIndex - 1];
    }

    private void renderDraftSubtasks(LinearLayout subtasksList, List<Subtask> draftSubtasks) {
        subtasksList.removeAllViews();
        if (draftSubtasks.isEmpty()) {
            subtasksList.addView(text("No subtasks yet.", 12, palette.muted, Typeface.NORMAL));
            return;
        }
        for (int index = 0; index < draftSubtasks.size(); index++) {
            final Subtask subtask = draftSubtasks.get(index);
            final int position = index;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox subtaskCheck = new CheckBox(this);
            subtaskCheck.setText(subtask.title);
            subtaskCheck.setTextColor(subtask.done ? palette.muted : palette.text);
            subtaskCheck.setTextSize(14 * textScale);
            subtaskCheck.setChecked(subtask.done);
            subtaskCheck.setContentDescription((subtask.done ? "Mark subtask as not done: " : "Mark subtask as done: ")
                    + subtask.title);
            subtaskCheck.setOnCheckedChangeListener((button, checked) -> {
                if (checked == subtask.done) return;
                draftSubtasks.set(position, subtask.withDone(checked));
                renderDraftSubtasks(subtasksList, draftSubtasks);
            });
            row.addView(subtaskCheck, new LinearLayout.LayoutParams(0, dp(48), 1f));
            Button removeSubtask = compactButton("Remove", true);
            removeSubtask.setContentDescription("Remove subtask " + subtask.title);
            removeSubtask.setOnClickListener(view -> {
                draftSubtasks.remove(position);
                renderDraftSubtasks(subtasksList, draftSubtasks);
            });
            row.addView(removeSubtask, new LinearLayout.LayoutParams(dp(96), dp(48)));
            subtasksList.addView(row, bottomMargin(dp(4)));
        }
    }

    private void showAddSubtaskDialog(List<Subtask> draftSubtasks, LinearLayout subtasksList) {
        if (draftSubtasks.size() >= TaskLogic.MAX_SUBTASKS) {
            showToast("A task can have at most " + TaskLogic.MAX_SUBTASKS + " subtasks.");
            return;
        }
        EditText input = new EditText(this);
        input.setHint("Subtask name");
        input.setSingleLine(true);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(TaskLogic.MAX_SUBTASK_TITLE) });
        input.setContentDescription("New subtask name");
        new AlertDialog.Builder(this)
                .setTitle("Add subtask")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> {
                    String title = input.getText() == null ? "" : input.getText().toString().trim();
                    if (title.isEmpty()) {
                        showToast("Enter a subtask name.");
                        return;
                    }
                    if (title.length() > TaskLogic.MAX_SUBTASK_TITLE) {
                        showToast("Subtasks can be at most " + TaskLogic.MAX_SUBTASK_TITLE + " characters.");
                        return;
                    }
                    draftSubtasks.add(new Subtask(UUID.randomUUID().toString(), title, false));
                    renderDraftSubtasks(subtasksList, draftSubtasks);
                })
                .show();
    }

    private int priorityIndex(String priority) {
        if ("low".equals(priority)) return 0;
        if ("high".equals(priority)) return 2;
        return 1;
    }

    private String priorityValue(int index) {
        if (index == 0) return "low";
        if (index == 2) return "high";
        return "medium";
    }

    private void replaceTask(Task replacement) {
        for (int index = 0; index < tasks.size(); index++) {
            if (tasks.get(index).id.equals(replacement.id)) {
                tasks.set(index, replacement);
                return;
            }
        }
    }

    private void deleteTask(Task task) {
        if (!canEdit() || pendingDeletedTask != null) return;
        int index = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id.equals(task.id)) { index = i; break; }
        }
        if (index < 0) return;
        pendingDeletedIndex = index;
        pendingDeletedTask = tasks.remove(index);
        pendingAttachmentCleanupTask = pendingDeletedTask;
        pendingAttachmentCleanupSaved = false;
        pendingAttachmentCleanupExpired = false;
        undoMessage.setText("Task deleted.");
        undoBar.setVisibility(View.VISIBLE);
        // Bring focus to Undo so screen-reader and keyboard users can act on the
        // seven-second recovery window without having to find the control.
        undoButton.requestFocus();
        if (undoDismissal != null) mainHandler.removeCallbacks(undoDismissal);
        undoDismissal = this::expireUndoWindow;
        mainHandler.postDelayed(undoDismissal, 7000);
        render();
        saveTasksAsync();
    }

    private void confirmDeleteTask(Task task) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this task?")
                .setMessage("“" + task.title + "” will be removed. You can use Undo for 7 seconds.")
                .setNegativeButton("Keep task", null)
                .setPositiveButton("Delete task", (dialog, which) -> deleteTask(task))
                .show();
    }

    private void undoDelete() {
        if (pendingDeletedTask == null || !canEdit()) return;
        for (Task task : tasks) {
            if (task.id.equals(pendingDeletedTask.id)) {
                pendingAttachmentCleanupTask = null;
                pendingAttachmentCleanupSaved = false;
                pendingAttachmentCleanupExpired = false;
                hideUndoBar();
                return;
            }
        }
        tasks.add(Math.min(pendingDeletedIndex, tasks.size()), pendingDeletedTask);
        pendingAttachmentCleanupTask = null;
        pendingAttachmentCleanupSaved = false;
        pendingAttachmentCleanupExpired = false;
        pendingDeletedTask = null;
        hideUndoBar();
        render();
        saveTasksAsync();
        showToast("Task restored.");
    }

    private void expireUndoWindow() {
        pendingAttachmentCleanupExpired = true;
        hideUndoBar();
        cleanupDeletedAttachmentsIfReady();
    }

    private void cleanupDeletedAttachmentsIfReady() {
        if (pendingAttachmentCleanupTask == null || !pendingAttachmentCleanupSaved
                || !pendingAttachmentCleanupExpired) return;
        pendingAttachmentCleanupTask = null;
        pendingAttachmentCleanupSaved = false;
        pendingAttachmentCleanupExpired = false;
        storageExecutor.execute(() -> {
            try {
                synchronized (AndroidAttachmentStore.transactionLock()) {
                    List<Task> latest = taskStore.load();
                    attachmentStore.cleanupOrphans(attachmentIds(latest));
                }
            } catch (Exception ignored) {
                // A later launch retries cleanup; task metadata is already durably removed.
            }
        });
    }

    private void hideUndoBar() {
        if (undoDismissal != null) mainHandler.removeCallbacks(undoDismissal);
        undoDismissal = null;
        pendingDeletedTask = null;
        if (undoBar != null) undoBar.setVisibility(View.GONE);
    }

    private String themeLabel() {
        if (themeMode == THEME_LIGHT) return "Light";
        if (themeMode == THEME_DARK) return "Dark";
        return "System";
    }

    private void toggleExperienceMode() {
        if (webMode) return;
        powerMode = !powerMode;
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(POWER_MODE_KEY, powerMode).apply();
        activeFilter = TaskLogic.FILTER_ALL;
        searchQuery = "";
        if (searchInput != null) searchInput.setText("");
        pathButton.setText(powerMode ? "Simple path" : "Power path");
        pathButton.setContentDescription(powerMode
                ? "Switch to Simple path. Your tasks will stay the same."
                : "Switch to Power mode for search, filters, and suggestions. Your tasks will stay the same.");
        captureFeedback.setText(powerMode
                ? "Power path adds search, filters, and ranked demo suggestions. It uses the same tasks."
                : "Simple path shows your tasks without extra filters. Your tasks are unchanged.");
        render();
    }

    private void showPortableBackupDialog() {
        if (!canEdit()) {
            showToast("Encrypted storage must be ready before creating or restoring a backup.");
            return;
        }
        String message = "Create a new encrypted .dmbackup file or add tasks from one. Restore never replaces tasks; repeated imports of the same backup are blocked on this device.\n\n"
                + "Attachment payloads are limited to 20 MiB each and 100 MiB total (100 attachments overall, five per task). The complete archive is limited to 110 MiB, measured from streamed bytes; import does not trust provider size hints.\n\n"
                + "Export uses a randomly generated recovery key shown once. Loss of that key means no restore, and Daymark cannot verify where you save it. No passphrase or online service is used.\n\n"
                + "Task text, attachment names, MIME types and contents are encrypted. The outer format/version, backup ID, record-type flags, record count, exact attachment sizes and opaque task-association tokens remain visible. Daymark has no vendor login, network client, upload or sync; the document provider you choose may be remote and use its own network or sign-in.";
        new AlertDialog.Builder(this)
                .setTitle("Encrypted portable backup")
                .setMessage(message)
                .setItems(new String[] { "Export encrypted backup", "Import backup as new tasks" },
                        (dialog, selected) -> {
                            if (selected == 0) showRecoveryKeyAcknowledgement();
                            else choosePortableImport();
                        })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showRecoveryKeyAcknowledgement() {
        if (!canEdit()) return;
        byte[] key = PortableBackupCodec.newRecoveryKey(new SecureRandom());
        String printable;
        try {
            printable = PortableBackupCodec.encodeRecoveryKey(key);
        } catch (Exception failure) {
            PortableBackupCodec.clear(key);
            showToast("A recovery key could not be generated securely.");
            return;
        }
        pendingRecoveryKey = key;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(8));
        TextView warning = text("Save this key somewhere separate from the backup file. Anyone with the backup and key can read its contents. If you lose the key, the backup cannot be restored. Daymark does not keep a copy.",
                14, palette.text, Typeface.NORMAL);
        warning.setLineSpacing(dp(3), 1f);
        content.addView(warning, bottomMargin(dp(12)));
        TextView keyView = text(printable, 18, palette.text, Typeface.BOLD);
        keyView.setTextIsSelectable(false);
        keyView.setGravity(Gravity.CENTER);
        keyView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        content.addView(keyView, bottomMargin(dp(12)));
        CheckBox acknowledgement = new CheckBox(this);
        acknowledgement.setText("I saved the key separately. I understand losing it means no restore.");
        acknowledgement.setTextColor(palette.text);
        acknowledgement.setTextSize(14 * textScale);
        content.addView(acknowledgement);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Save your recovery key")
                .setView(scroll)
                .setNegativeButton("Cancel export", null)
                .setPositiveButton("Continue to create file", null)
                .create();
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        final boolean[] continueExport = { false };
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            acknowledgement.setOnCheckedChangeListener((button, checked) -> positive.setEnabled(checked));
            positive.setOnClickListener(view -> {
                if (!acknowledgement.isChecked()) return;
                continueExport[0] = true;
                dialog.dismiss();
                beginPortableExport(key);
            });
        });
        dialog.setOnDismissListener(ignored -> {
            keyView.setText("");
            Window window = dialog.getWindow();
            if (window != null) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (!continueExport[0]) {
                PortableBackupCodec.clear(key);
                if (pendingRecoveryKey == key) pendingRecoveryKey = null;
            }
        });
        dialog.show();
    }

    private void beginPortableExport(byte[] recoveryKey) {
        if (!canEdit()) {
            PortableBackupCodec.clear(recoveryKey);
            pendingRecoveryKey = null;
            showToast("Encrypted storage is not ready for export.");
            return;
        }
        pendingRecoveryKey = recoveryKey;
        portableBusy = true;
        portableCanCancel = true;
        portableCancelRequested = false;
        cancelAttachmentButton.setText("Cancel backup");
        cancelAttachmentButton.setEnabled(true);
        cancelAttachmentButton.setVisibility(View.VISIBLE);
        List<Task> snapshot = new ArrayList<>(tasks);
        storageStatus.setText("Encrypting private backup…");
        storageStatus.setTextColor(palette.muted);
        render();
        storageExecutor.execute(() -> {
            File staged = null;
            Exception failure = null;
            try {
                staged = portableBackupManager.createExportStageFile();
                try (FileOutputStream output = new FileOutputStream(staged)) {
                    PortableBackupCodec.writeArchive(output, recoveryKey, snapshot,
                            (task, attachment) -> attachmentStore.openDecrypted(task.id, attachment.id),
                            new SecureRandom(), () -> portableCancelRequested);
                    output.flush();
                    output.getFD().sync();
                }
            } catch (Exception exception) {
                failure = exception;
            }
            File completedStage = staged;
            Exception error = failure;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (completedStage != null && completedStage.exists()) completedStage.delete();
                    PortableBackupCodec.clear(recoveryKey);
                    return;
                }
                pendingExportArchive = completedStage;
                if (error != null || completedStage == null) {
                    String reason = portableCancelRequested
                            ? "Backup export cancelled. No task data was changed."
                            : "Daymark could not create a safe encrypted backup. No task data was changed.";
                    finishPortableExport(reason);
                    return;
                }
                storageStatus.setText("Choose a destination for the new backup file…");
                Intent picker = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType("application/octet-stream");
                picker.putExtra(Intent.EXTRA_TITLE, "daymark-backup.dmbackup");
                try {
                    startActivityForResult(picker, REQUEST_PORTABLE_EXPORT);
                } catch (android.content.ActivityNotFoundException exception) {
                    finishPortableExport("Android's document picker is not available. No task data was changed.");
                }
            });
        });
    }

    private void writePortableExport(Uri destination, File stagedArchive) {
        portableBusy = true;
        portableCanCancel = true;
        portableCancelRequested = false;
        storageStatus.setText("Writing encrypted backup through Android's document picker…");
        storageStatus.setTextColor(palette.muted);
        render();
        storageExecutor.execute(() -> {
            Exception failure = null;
            OutputStream output = null;
            try (InputStream input = new FileInputStream(stagedArchive)) {
                output = getContentResolver().openOutputStream(destination, "w");
                if (output == null) throw new IOException("The document provider did not open the new file.");
                byte[] buffer = new byte[32 * 1024];
                long copied = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (portableCancelRequested) throw new IOException("Portable backup cancelled.");
                    if (copied > PortableBackupCodec.MAX_ARCHIVE_BYTES - read) throw new IOException("The encrypted backup exceeded its size limit.");
                    output.write(buffer, 0, read);
                    copied += read;
                }
                if (portableCancelRequested) throw new IOException("Portable backup cancelled.");
                output.flush();
            } catch (Exception exception) {
                failure = exception;
            } finally {
                if (output != null) {
                    try { output.close(); }
                    catch (Exception closeFailure) {
                        if (failure == null) failure = closeFailure;
                        else failure.addSuppressed(closeFailure);
                    }
                }
            }
            Exception error = failure;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (error != null) deleteCreatedDocument(destination);
                    cleanupPortableExportStage(stagedArchive);
                    PortableBackupCodec.clear(pendingRecoveryKey);
                    pendingRecoveryKey = null;
                    return;
                }
                if (error != null) {
                    boolean removed = deleteCreatedDocument(destination);
                    String detail = portableCancelRequested
                            ? "Backup export cancelled. " : "The encrypted backup could not be written. ";
                    if (!removed) detail += "A partial encrypted document may remain; it contains no plaintext. ";
                    detail += "Task data was not changed.";
                    finishPortableExport(detail);
                } else {
                    finishPortableExport("Encrypted backup saved. Keep its recovery key separate; Daymark does not store it.");
                }
            });
        });
    }

    private void finishPortableExport(String message) {
        cleanupPortableExportStage(pendingExportArchive);
        pendingExportArchive = null;
        PortableBackupCodec.clear(pendingRecoveryKey);
        pendingRecoveryKey = null;
        portableBusy = false;
        portableCanCancel = false;
        portableCancelRequested = false;
        cancelAttachmentButton.setVisibility(View.GONE);
        storageStatus.setText(storageReady ? "Encrypted storage ready" : "Saved tasks unavailable");
        storageStatus.setTextColor(storageReady ? palette.accent : palette.danger);
        render();
        showToast(message);
    }

    private void cleanupPortableExportStage(File stagedArchive) {
        if (stagedArchive != null && stagedArchive.exists()) stagedArchive.delete();
    }

    private boolean deleteCreatedDocument(Uri uri) {
        try { return DocumentsContract.deleteDocument(getContentResolver(), uri); }
        catch (Exception exception) {
            StartupDiagnostics.record(StartupDiagnostics.EXPORT_STAGE_CLEANUP_FAILED, exception);
            return false;
        }
    }

    private void choosePortableImport() {
        if (portableRestoreWorkerActive || portableImportCleanupActive || activePortableImportUri != null) {
            showToast("A backup restore is already being finished. Try again after it completes.");
            return;
        }
        if (pendingPortableImportUri != null) {
            showPortableImportKeyDialog();
            return;
        }
        if (!canEdit()) {
            showToast("Encrypted storage must be ready before restoring a backup.");
            return;
        }
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] { "application/octet-stream", "application/vnd.daymark.dmbackup" });
        try {
            startActivityForResult(picker, REQUEST_PORTABLE_IMPORT);
        } catch (android.content.ActivityNotFoundException exception) {
            showToast("Android's document picker is not available on this device.");
        }
    }

    private boolean retainPortableImportUri(Uri uri, int resultFlags) {
        if (portableRestoreWorkerActive || portableImportCleanupActive || activePortableImportUri != null) {
            showToast("A backup restore is already being finished. Try again after it completes.");
            return false;
        }
        if (pendingPortableImportUri != null) {
            if (pendingPortableImportUri.equals(uri) && pendingPortableImportOperationToken != null
                    && hasPersistedPortableReadGrant(uri)) return true;
            showToast("Finish or cancel the selected backup before choosing another one.");
            return false;
        }
        int readFlag = resultFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (uri == null || !android.content.ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                || readFlag == 0 || (resultFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0) {
            showToast("Android could not keep access to that backup. Choose it again to restore.");
            return false;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException failure) {
            showToast("Android could not keep access to that backup. Choose it again to restore.");
            return false;
        }
        if (!hasPersistedPortableReadGrant(uri)) {
            releasePersistablePortableReadGrant(uri);
            showToast("Android could not keep access to that backup. Choose it again to restore.");
            return false;
        }
        PortableImportGrantRecovery.Selection selection;
        try {
            selection = portableBackupManager.recordActivePortableImportUri(uri);
        } catch (IOException journalFailure) {
            // The manager immediately releases only this URI if its durable journal write fails.
            showToast("The restore could not be prepared safely. Reopen Daymark before trying again.");
            return false;
        }
        pendingPortableImportUri = uri;
        pendingPortableImportOperationToken = selection.operationToken;
        return true;
    }

    private PortableImportGrantRecovery.Selection pendingPortableImportSelection() {
        if (pendingPortableImportUri == null || pendingPortableImportOperationToken == null) return null;
        return new PortableImportGrantRecovery.Selection(pendingPortableImportOperationToken,
                pendingPortableImportUri.toString());
    }

    private boolean hasPersistedPortableReadGrant(Uri uri) {
        if (uri == null) return false;
        try {
            for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                if (uri.equals(permission.getUri()) && permission.isReadPermission()) return true;
            }
        } catch (RuntimeException ignored) {
            // Treat an unavailable grant list as a lost grant and require a fresh picker selection.
        }
        return false;
    }

    private void discardPendingPortableImport() {
        discardPendingPortableImport(null);
    }

    private void discardPendingPortableImport(PortableImportGrantRecovery.Selection expected) {
        PortableImportGrantRecovery.Selection selected = pendingPortableImportSelection();
        if (selected == null || (expected != null && !expected.matches(selected))) return;
        pendingPortableImportUri = null;
        pendingPortableImportOperationToken = null;
        reconcileAndReleasePortableImportSelection(selected);
    }

    private void reconcileAndReleasePortableImportSelection(PortableImportGrantRecovery.Selection selected) {
        if (selected == null) return;
        portableImportCleanupActive = true;
        try {
            storageExecutor.execute(() -> {
                Exception failure = null;
                try {
                    synchronized (AndroidAttachmentStore.transactionLock()) {
                        List<Task> latest = taskStore.load();
                        portableBackupManager.finishPortableImportSelection(selected, latest, attachmentStore);
                    }
                } catch (Exception cleanupFailure) {
                    failure = cleanupFailure;
                }
                Exception result = failure;
                postActivityCallback(() -> {
                    if (result == null) {
                        portableImportCleanupActive = false;
                    } else {
                        showToast("Temporary backup access could not be safely released. Reopen Daymark to recover it.");
                    }
                });
            });
        } catch (RuntimeException schedulingFailure) {
            if (!isFinishing() && !isDestroyed()) {
                showToast("Temporary backup access could not be safely released. Reopen Daymark to recover it.");
            }
        }
    }

    private void releasePersistablePortableReadGrant(Uri uri) {
        if (!hasPersistedPortableReadGrant(uri)) return;
        try {
            getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException ignored) {
            // A provider may revoke the grant independently; there is no retained app data to clean up.
        }
    }

    private void showPortableImportKeyDialog() {
        PortableImportGrantRecovery.Selection selectedOperation = pendingPortableImportSelection();
        if (selectedOperation == null) return;
        Uri selected = Uri.parse(selectedOperation.uri);
        if (!hasPersistedPortableReadGrant(selected)) {
            pendingPortableImportUri = null;
            pendingPortableImportOperationToken = null;
            reconcileAndReleasePortableImportSelection(selectedOperation);
            showToast("Access to the selected backup was not retained. Choose it again to restore.");
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(8));
        TextView notice = text("Enter the recovery key for this encrypted backup. Restore adds separate copies with fresh task and attachment IDs; it never replaces existing tasks. Importing the same backup again on this device is blocked.",
                14, palette.text, Typeface.NORMAL);
        notice.setLineSpacing(dp(3), 1f);
        content.addView(notice, bottomMargin(dp(10)));
        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(100) });
        keyInput.setSaveEnabled(false);
        keyInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        keyInput.setHint("Recovery key (DMK1-…)");
        content.addView(keyInput);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Restore encrypted backup")
                .setView(scroll)
                .setNegativeButton("Cancel", (ignored, which) -> discardPendingPortableImport(selectedOperation))
                .setPositiveButton("Restore as new tasks", null)
                .create();
        dialog.setOnCancelListener(ignored -> {
            if (!isChangingConfigurations()) discardPendingPortableImport(selectedOperation);
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            byte[] key;
            try {
                String entered = keyInput.getText() == null ? "" : keyInput.getText().toString().trim().toUpperCase(Locale.ROOT);
                key = PortableBackupCodec.decodeRecoveryKey(entered);
            } catch (Exception failure) {
                keyInput.setError("Check the recovery key and try again.");
                return;
            }
            keyInput.setText("");
            pendingPortableImportUri = null;
            pendingPortableImportOperationToken = null;
            dialog.dismiss();
            beginPortableRestore(selectedOperation, key);
        }));
        dialog.setOnDismissListener(ignored -> keyInput.setText(""));
        dialog.show();
    }

    private void beginPortableRestore(PortableImportGrantRecovery.Selection selectedOperation, byte[] recoveryKey) {
        Uri selected = Uri.parse(selectedOperation.uri);
        if (!canEdit()) {
            PortableBackupCodec.clear(recoveryKey);
            reconcileAndReleasePortableImportSelection(selectedOperation);
            showToast("Encrypted storage is not ready for restore.");
            return;
        }
        activePortableImportUri = selected;
        activePortableImportOperationToken = selectedOperation.operationToken;
        pendingRecoveryKey = recoveryKey;
        portableBusy = true;
        portableCanCancel = true;
        portableCancelRequested = false;
        cancelAttachmentButton.setText("Cancel restore");
        cancelAttachmentButton.setEnabled(true);
        cancelAttachmentButton.setVisibility(View.VISIBLE);
        int originalCount = tasks.size();
        storageStatus.setText("Authenticating and validating the full backup privately…");
        storageStatus.setTextColor(palette.muted);
        render();
        portableRestoreWorkerActive = true;
        PortableImportGrantRecovery.activityRestoreWorkerStarted();
        try {
            storageExecutor.execute(() -> {
                List<Task> restored = null;
                Exception failure = null;
                try {
                    synchronized (AndroidAttachmentStore.transactionLock()) {
                        try (InputStream source = getContentResolver().openInputStream(selected)) {
                            if (source == null) throw new IOException("The selected backup could not be opened.");
                            restored = portableBackupManager.restore(source, recoveryKey, taskStore, attachmentStore,
                                    () -> portableCancelRequested, () -> {
                                        portableCanCancel = false;
                                        mainHandler.post(() -> {
                                            if (isFinishing() || isDestroyed()) return;
                                            cancelAttachmentButton.setText("Finishing restore…");
                                            cancelAttachmentButton.setEnabled(false);
                                            storageStatus.setText("Committing encrypted task snapshot…");
                                            storageStatus.setTextColor(palette.muted);
                                        });
                                    });
                        } catch (Exception exception) {
                            failure = exception;
                            PortableBackupCodec.clear(recoveryKey);
                        } finally {
                            try {
                                List<Task> current = taskStore.load();
                                portableBackupManager.finishActivePortableImportUri(
                                        selectedOperation, current, attachmentStore);
                            } catch (Exception cleanupFailure) {
                                if (failure == null) {
                                    failure = new PortableBackupManager.RestoreOutcomeUncertainException(
                                            "The restore URI grant could not be safely reconciled. Reopen Daymark before retrying.",
                                            cleanupFailure);
                                } else {
                                    failure.addSuppressed(cleanupFailure);
                                }
                            }
                        }
                    }
                } finally {
                    PortableBackupCodec.clear(recoveryKey);
                    portableRestoreWorkerActive = false;
                    PortableImportGrantRecovery.activityRestoreWorkerFinished();
                }
            List<Task> result = restored;
            Exception error = failure;
            postActivityCallback(() -> {
                if (activePortableImportUri == selected
                        && selectedOperation.operationToken.equals(activePortableImportOperationToken)) {
                    activePortableImportUri = null;
                    activePortableImportOperationToken = null;
                }
                PortableBackupCodec.clear(pendingRecoveryKey);
                pendingRecoveryKey = null;
                pendingPortableImportUri = null;
                pendingPortableImportOperationToken = null;
                portableBusy = false;
                portableCanCancel = false;
                portableCancelRequested = false;
                cancelAttachmentButton.setVisibility(View.GONE);
                if (error == null && result != null) {
                    tasks.clear();
                    tasks.addAll(result);
                    lastSavedTasks.clear();
                    lastSavedTasks.addAll(result);
                    storageReady = true;
                    storageLoadFailed = false;
                    storageSaveFailed = false;
                    storageFailureDetails = null;
                    storageStatus.setText("Encrypted storage ready");
                    storageStatus.setTextColor(palette.accent);
                    int added = Math.max(0, result.size() - originalCount);
                    showToast(added == 0 ? "Backup checked. It contained no new tasks." : "Restored " + added + " tasks as new copies.");
                } else if (error instanceof PortableBackupManager.RestoreOutcomeUncertainException) {
                    storageReady = false;
                    storageLoadFailed = true;
                    storageSaveFailed = true;
                    storageStatus.setText("Restore outcome not verified · reopen Daymark");
                    storageStatus.setTextColor(palette.danger);
                    captureFeedback.setText("Restore commit status could not be verified. Editing is paused; reopen Daymark to reconcile the encrypted snapshot and restore journal.");
                    showToast("Restore outcome could not be verified. Reopen Daymark before retrying.");
                } else {
                    storageStatus.setText(storageReady ? "Encrypted storage ready" : "Saved tasks unavailable");
                    storageStatus.setTextColor(storageReady ? palette.accent : palette.danger);
                    showToast(portableRestoreFailure(error));
                }
                render();
            });
            });
        } catch (RuntimeException schedulingFailure) {
            portableRestoreWorkerActive = false;
            PortableImportGrantRecovery.activityRestoreWorkerFinished();
            PortableBackupCodec.clear(recoveryKey);
            activePortableImportUri = null;
            activePortableImportOperationToken = null;
            reconcileAndReleasePortableImportSelection(selectedOperation);
            pendingRecoveryKey = null;
            showToast("The restore did not start. Reopen Daymark to recover its temporary document access.");
        }
    }

    private String portableRestoreFailure(Exception failure) {
        if (portableCancelRequested || (failure != null && failure.getMessage() != null
                && failure.getMessage().toLowerCase(Locale.ROOT).contains("cancelled"))) {
            return "Restore cancelled. Existing tasks were not replaced.";
        }
        if (failure != null && failure.getMessage() != null
                && failure.getMessage().toLowerCase(Locale.ROOT).contains("already imported")) {
            return "This backup was already imported on this device; repeated restore was blocked.";
        }
        return "Backup was not restored. It may be damaged, unsupported, or paired with the wrong recovery key; existing tasks were not replaced.";
    }

    private void requestOperationCancel() {
        if (portableBusy) {
            if (!portableCanCancel) {
                showToast("The restore is committing its task snapshot and can no longer be cancelled.");
                return;
            }
            portableCancelRequested = true;
            cancelAttachmentButton.setText("Cancelling…");
            cancelAttachmentButton.setEnabled(false);
            return;
        }
        requestAttachmentCancel();
    }

    private void showSettingsDialog() {
        String[] options = {
                "Appearance: " + themeLabel(),
                "Text size: " + textSizeLabel(),
                "High contrast: " + (highContrast ? "On" : "Off"),
                "Accessibility",
                "Permission status: " + permissionStatusLabel(),
                "Encrypted backup / restore",
                "Browser & extensions",
                "Check now"
        };
        new AlertDialog.Builder(this)
                .setTitle("More")
                .setItems(options, (dialog, selected) -> {
                    if (selected == 0) showThemePicker();
                    else if (selected == 1) showTextSizePicker();
                    else if (selected == 2) toggleHighContrast();
                    else if (selected == 3) showScreenReaderInfo();
                    else if (selected == 4) showPermissionStatus();
                    else if (selected == 5) showPortableBackupDialog();
                    else if (selected == 6) {
                        if (webMode) showBrowserExtensionsManager();
                        else setWebMode(true);
                    } else checkForUpdates(true);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showThemePicker() {
        String[] themes = { "Follow device settings", "Light", "Dark" };
        new AlertDialog.Builder(this)
                .setTitle("Appearance")
                .setSingleChoiceItems(themes, themeMode, (dialog, selected) -> {
                    themeMode = selected;
                    getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putInt(THEME_KEY, themeMode).apply();
                    dialog.dismiss();
                    recreate();
                })
                .setPositiveButton("Done", null)
                .show();
    }

    private String textSizeLabel() {
        if (textSizeMode == 0) return "Compact";
        if (textSizeMode == 2) return "Extra large";
        return "Standard";
    }

    private void showTextSizePicker() {
        String[] sizes = { "Compact", "Standard", "Extra large" };
        new AlertDialog.Builder(this)
                .setTitle("Text size")
                .setSingleChoiceItems(sizes, textSizeMode, (dialog, selected) -> {
                    textSizeMode = selected;
                    textScale = TextScalePolicy.combined(
                            selected == 0 ? 0.9f : selected == 2 ? 1.25f : 1.0f,
                            getResources().getConfiguration().fontScale);
                    getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                            .putInt(TEXT_SIZE_KEY, textSizeMode).apply();
                    dialog.dismiss();
                    recreate();
                })
                .setPositiveButton("Done", null)
                .show();
    }

    private void toggleHighContrast() {
        highContrast = !highContrast;
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(HIGH_CONTRAST_KEY, highContrast).apply();
        recreate();
    }

    private void showLanguageInfo() {
        showInfo("Language", "This prototype's screen text is English only. Dates and number formatting follow your device language. Other translations are not bundled yet.");
    }

    private void showScreenReaderInfo() {
        showInfo("Screen reader support", "Buttons and task controls have spoken labels, and this screen uses native Android controls. TalkBack and other screen readers are provided by Android under Accessibility settings.");
    }

    private void showReducedMotionInfo() {
        showInfo("Reduced motion", "This version has no looping or auto-playing animation. Android system controls still apply to platform dialogs and touch feedback; there is no separate in-app motion switch.");
    }

    @SuppressWarnings("deprecation")
    private PackageInfo readPermissionPackageInfo() throws PackageManager.NameNotFoundException {
        return getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
    }

    private String permissionStatusLabel() {
        try {
            String[] requested = readPermissionPackageInfo().requestedPermissions;
            if (requested == null || requested.length == 0) return "None needed";
            if (requested.length == 1 && "android.permission.INTERNET".equals(requested[0])) return "Browser only";
            return requested.length + " declared";
        } catch (Exception ignored) {
            return "Unavailable";
        }
    }

    private void showPermissionStatus() {
        String message;
        try {
            String[] requested = readPermissionPackageInfo().requestedPermissions;
            if (requested == null || requested.length == 0) {
                message = "Status: no Android permissions are declared by this version.\n\n"
                        + "Task capture, editing, completion, deletion, and encrypted on-device storage need no permission. The browser needs INTERNET to load pages; if it is absent, Web mode cannot connect. Daymark does not show a runtime permission prompt when it opens.\n\n"
                        + "No task synchronization or background search is enabled. Special app access would open Android Settings. An ordinary app cannot grant signature-only or privileged permissions. Daymark does not request root or superuser access.";
            } else {
                StringBuilder details = new StringBuilder("Permissions declared by this app:\n");
                boolean internetDeclared = false;
                for (String permission : requested) {
                    details.append("• ").append(permission).append('\n');
                    if ("android.permission.INTERNET".equals(permission)) internetDeclared = true;
                }
                if (internetDeclared) {
                    details.append("\nINTERNET is used for embedded browser requests only after you tap Go or choose a listed site. It does not synchronize tasks or copy task text automatically. Open pages may contact their own or third-party endpoints, which may log requests.");
                }
                details.append("\nThis screen reports manifest declarations; it does not request or grant access. INTERNET is a normal permission and does not show a runtime prompt. Other runtime permission requests must follow a user action for the feature that needs them.");
                message = details.toString();
            }
        } catch (Exception ignored) {
            message = "Permission status could not be read. No permission was requested by this screen.";
        }
        showInfo("Permission status", message);
    }

    private void checkForUpdates(boolean manual) {
        boolean publisherConfigured = UpdaterPublisherConfig.isUpdaterConfigured();
        if (!UpdaterCore.isNetworkCheckAllowed(hasInternetPermission(), publisherConfigured)) {
            if (manual) {
                String reason = !BuildConfig.UPDATER_ENABLED
                        ? "Update checks are available only in the GitHub sideload distribution."
                        : publisherConfigured
                        ? "This build does not declare the Internet permission."
                        : "Update checks are disabled for this build; a release build and trusted publisher signing certificate are required.";
                showInfo("Updates unavailable", reason + " No network request was made. "
                        + "Your tasks remain available offline.");
            }
            return;
        }
        if (updateCheckRunning) {
            if (manual) Toast.makeText(this, "An update check is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastCheck = preferences.getLong(LAST_UPDATE_CHECK_KEY, 0L);
        if (!UpdaterCore.shouldCheck(lastCheck, now, manual)) return;
        updateCheckRunning = true;
        preferences.edit().putLong(LAST_UPDATE_CHECK_KEY, now).apply();
        updaterExecutor.execute(() -> {
            UpdaterCore.CheckResult result = null;
            UpdaterCore.UpdateException failure = null;
            try {
                long installedVersionCode = currentVersionCode();
                result = UpdaterCore.check(new GitHubReleaseClient(), getPackageName(), installedVersionCode);
            } catch (UpdaterCore.UpdateException exception) {
                failure = exception;
            } catch (Exception exception) {
                failure = new UpdaterCore.UpdateException(UpdaterCore.Failure.HTTP,
                        "Update metadata could not be read.", exception);
            }
            final UpdaterCore.CheckResult checked = result;
            final UpdaterCore.UpdateException error = failure;
            mainHandler.post(() -> {
                updateCheckRunning = false;
                if (!activityResumed || isFinishing() || isDestroyed()) return;
                if (error != null) {
                    if (manual) showInfo("Update check", updateFailureMessage(error));
                    return;
                }
                if (checked == null || checked.status == UpdaterCore.CheckStatus.NO_UPDATE) {
                    if (manual) showInfo("Update check", "No newer stable Daymark release was found. The app remains usable offline.");
                    return;
                }
                String dismissedTag = preferences.getString(DISMISSED_UPDATE_TAG_KEY, "");
                if (!manual && checked.release.tag.equals(dismissedTag)) return;
                showUpdateDetails(checked.release);
            });
        });
    }

    private boolean hasInternetPermission() {
        return getPackageManager().checkPermission(Manifest.permission.INTERNET, getPackageName())
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private long currentVersionCode() throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
    }

    private String updateFailureMessage(UpdaterCore.UpdateException error) {
        switch (error.failure) {
            case OFFLINE:
                return "GitHub could not be reached. Check your connection and try Check now later. Daymark tasks remain available offline.";
            case RATE_LIMITED:
                return "GitHub is rate-limiting update checks. Wait before trying Check now again. Daymark tasks remain available.";
            case NETWORK_POLICY:
                return "Wi-Fi only was selected. Connect to Wi-Fi or explicitly allow mobile data before downloading. No APK was saved.";
            case INVALID_METADATA:
                return "The GitHub release metadata was incomplete or invalid. No APK was downloaded.";
            case HTTP:
                return "GitHub did not return a usable release response. No APK was downloaded.";
            case APK_MISMATCH:
                return "The downloaded APK did not match the release metadata or Daymark package/version. Nothing was installed.";
            case SIGNER_MISMATCH:
                return "The release signing certificate did not exactly match this installed app and publisher configuration. Nothing was installed. Do not uninstall Daymark; encrypted local tasks may be lost.";
            case DOWNLOAD:
                return "The APK download did not complete. Nothing was installed; Daymark tasks remain available.";
            case CANCELLED:
                return "The APK download was cancelled. No verified APK was saved; Daymark tasks remain available.";
            default:
                return "The update check could not be completed. No APK was downloaded.";
        }
    }

    private void showUpdateDetails(UpdaterCore.Release release) {
        boolean updaterConfigured = BuildConfig.UPDATER_ENABLED
                && UpdaterPublisherConfig.isUpdaterConfigured() && hasInternetPermission();
        String message = "Version " + release.versionName + "\n"
                + release.name + "\n\n"
                + (release.notes.trim().isEmpty() ? "No release notes were provided." : release.notes.trim())
                + "\n\nAPK size: " + NumberFormat.getIntegerInstance(Locale.getDefault()).format(release.apkSizeBytes)
                + " bytes. Choose Wi-Fi only (the default) or explicitly allow mobile data below. No APK is downloaded until you choose Download and verify."
                + "\n\nDaymark sends no task data or history in an update request. Update checks contact the fixed public GitHub Releases endpoint only while the app is foregrounded or when you choose Check now."
                + "\n\nAfter verification, you choose where to save the APK and open it yourself from Files if you want to continue. Daymark does not open an installer or install it; Android controls any install-source approval and final confirmation."
                + "\n\nDo not uninstall to work around a signing mismatch; encrypted local tasks may be lost.";
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage(message);
        if (updaterConfigured) {
            boolean[] allowMobileData = { false };
            dialog.setSingleChoiceItems(new String[] { "Wi-Fi only (recommended)", "Allow mobile data" }, 0,
                    (choiceDialog, selected) -> allowMobileData[0] = selected == 1);
            dialog.setPositiveButton("Download and verify",
                    (ignored, which) -> beginUpdateDownload(release, allowMobileData[0]));
            dialog.setNegativeButton("Cancel", null);
            dialog.setNeutralButton("Dismiss release", (ignored, which) -> rememberDismissedRelease(release.tag));
        } else {
            dialog.setPositiveButton("OK", (ignored, which) -> rememberDismissedRelease(release.tag));
            dialog.setNegativeButton("Close", null);
        }
        dialog.show();
    }

    private void beginUpdateDownload(UpdaterCore.Release release, boolean allowMobileData) {
        if (updateTransferRunning) {
            Toast.makeText(this, "An update download is already running.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!BuildConfig.UPDATER_ENABLED || !UpdaterPublisherConfig.isUpdaterConfigured()
                || !hasInternetPermission()) {
            showInfo("Update unavailable", "The GitHub sideload build, Internet access, and a protected publisher signer are required. No APK was downloaded; tasks remain usable.");
            return;
        }
        try {
            updaterRecoveryStore.recordPending(release);
        } catch (Exception exception) {
            showInfo("Update not started", "A prior verified update must be recovered or explicitly discarded before another download. No APK was downloaded.");
            return;
        }
        updateTransferRunning = true;
        GitHubApkDownloader downloader = new GitHubApkDownloader(getApplicationContext(), allowMobileData,
                () -> UpdaterNetworkAccess.isWifiConnected(getApplicationContext()));
        activeUpdateDownloader = downloader;
        updateDownloadDialog = new AlertDialog.Builder(this)
                .setTitle("Downloading and verifying")
                .setMessage("The APK is being downloaded to temporary app storage. Cancel stops active transfer or verification. A fully verified APK remains private until you choose Save or Discard.")
                .setNegativeButton("Cancel", (dialog, which) -> downloader.cancel())
                .create();
        updateDownloadDialog.setCancelable(false);
        updateDownloadDialog.show();
        Toast.makeText(this, allowMobileData
                ? "Downloading and verifying the APK using the network you allowed."
                : "Downloading and verifying the APK over Wi-Fi only.", Toast.LENGTH_LONG).show();
        updaterExecutor.execute(() -> {
            UpdaterCore.VerificationResult result = null;
            UpdaterCore.UpdateException failure = null;
            try {
                AndroidApkVerifier verifier = new AndroidApkVerifier(getApplicationContext());
                String installedSigner = verifier.installedSignerSha256();
                result = UpdaterCore.downloadAndVerify(release,
                        getPackageName(), currentVersionCode(), Build.VERSION.SDK_INT,
                        installedSigner, UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256,
                        consentedRelease -> true,
                        downloader, verifier);
            } catch (UpdaterCore.UpdateException exception) {
                failure = exception;
            } catch (Exception exception) {
                failure = new UpdaterCore.UpdateException(UpdaterCore.Failure.APK_MISMATCH,
                        "Downloaded update could not be safely verified.", exception);
            }
            if (failure != null || (result != null && result.status == UpdaterCore.VerificationStatus.CANCELLED)) {
                try { updaterRecoveryStore.clearIfNoVerifiedArtifact(release); }
                catch (Exception exception) { StartupDiagnostics.record(StartupDiagnostics.UPDATER_RECOVERY_CLEAR_FAILED, exception); }
            }
            final UpdaterCore.VerificationResult completedResult = result;
            final UpdaterCore.UpdateException completedFailure = failure;
            mainHandler.post(() -> {
                updateTransferRunning = false;
                if (updateDownloadDialog != null && updateDownloadDialog.isShowing()) {
                    updateDownloadDialog.dismiss();
                }
                updateDownloadDialog = null;
                if (activeUpdateDownloader == downloader) activeUpdateDownloader = null;
                if (!activityResumed || isFinishing() || isDestroyed()) {
                    // A verified result remains paired with its persistent recovery record.
                    return;
                }
                if (completedFailure != null) {
                    showInfo(completedFailure.failure == UpdaterCore.Failure.CANCELLED
                            ? "Download cancelled" : "Update verification failed",
                            updateFailureMessage(completedFailure));
                } else if (completedResult != null
                        && completedResult.status == UpdaterCore.VerificationStatus.VERIFIED
                        && completedResult.verifiedApk != null) {
                    offerManualApkSave(release, completedResult.verifiedApk);
                } else {
                    showInfo("Verification cancelled", "No update was installed. The existing app and encrypted tasks remain unchanged.");
                }
            });
        });
    }

    /** Revalidate any retained artifact before offering it; no verified cache file is swept at startup. */
    private boolean recoverPendingVerifiedUpdate() {
        if (updaterRecoveryStore == null || recoveryPromptShowing) return recoveryPromptShowing;
        boolean hasRecord = updaterRecoveryStore.hasPendingRecord();
        if (!hasRecord) {
            try {
                List<File> orphanedArtifacts = updaterRecoveryStore.listVerifiedArtifacts();
                if (!orphanedArtifacts.isEmpty()) {
                    offerUnrecoverableArtifact(orphanedArtifacts.get(0));
                    return true;
                }
            } catch (IOException exception) { StartupDiagnostics.record(StartupDiagnostics.UPDATER_ARTIFACT_LIST_FAILED, exception); }
            return false;
        }
        if (recoveryCheckRunning) return true;
        recoveryCheckRunning = true;
        updaterExecutor.execute(() -> {
            UpdaterRecoveryStore.PendingUpdate pending = null;
            UpdaterCore.UpdateException failure = null;
            boolean unreadableRecord = false;
            try {
                pending = updaterRecoveryStore.readPending();
                if (pending != null && !pending.verifiedApk.isFile()) {
                    updaterRecoveryStore.clearIfNoVerifiedArtifact(pending.release);
                    pending = null;
                } else if (pending != null) {
                    AndroidApkVerifier verifier = new AndroidApkVerifier(getApplicationContext());
                    UpdaterCore.verifyDownloadedArtifact(pending.release, pending.verifiedApk,
                            getPackageName(), currentVersionCode(), Build.VERSION.SDK_INT,
                            verifier.installedSignerSha256(),
                            UpdaterPublisherConfig.PUBLISHER_SIGNER_SHA256, verifier);
                }
            } catch (UpdaterCore.UpdateException exception) {
                failure = exception;
                unreadableRecord = pending == null;
            } catch (Exception exception) {
                failure = new UpdaterCore.UpdateException(UpdaterCore.Failure.APK_MISMATCH,
                        "Retained update could not be safely revalidated.", exception);
                unreadableRecord = pending == null;
            }
            final UpdaterRecoveryStore.PendingUpdate recovered = pending;
            final UpdaterCore.UpdateException recoveryFailure = failure;
            final boolean recoveryRecordUnreadable = unreadableRecord;
            mainHandler.post(() -> {
                recoveryCheckRunning = false;
                if (!activityResumed || isFinishing() || isDestroyed()) return;
                if (recovered != null) {
                    if (recoveryFailure == null) offerManualApkSave(recovered.release, recovered.verifiedApk);
                    else offerInvalidRecovery(recovered);
                } else if (recoveryRecordUnreadable) {
                    offerUnreadableRecoveryRecord();
                } else if (!recoverPendingVerifiedUpdate()) {
                    checkForUpdates(false);
                }
            });
        });
        return true;
    }

    private void offerInvalidRecovery(UpdaterRecoveryStore.PendingUpdate pending) {
        if (recoveryPromptShowing) return;
        recoveryPromptShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Update recovery not verified")
                .setMessage("The retained APK no longer matches its stored digest, package, version, or signer expectations. It will not be offered for saving or opening. Keep it in private storage or explicitly discard it.")
                .setPositiveButton("Discard retained APK", (ignored, which) -> discardVerifiedUpdate(pending.release))
                .setNegativeButton("Keep for now", null)
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(ignored -> recoveryPromptShowing = false);
        dialog.show();
    }

    private void offerUnreadableRecoveryRecord() {
        try {
            List<File> artifacts = updaterRecoveryStore.listVerifiedArtifacts();
            if (!artifacts.isEmpty()) {
                offerUnrecoverableArtifact(artifacts.get(0));
                return;
            }
        } catch (IOException exception) { StartupDiagnostics.record(StartupDiagnostics.UPDATER_ARTIFACT_LIST_FAILED, exception); }
        if (recoveryPromptShowing) return;
        recoveryPromptShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Update recovery unavailable")
                .setMessage("The retained update record could not be validated and no APK will be offered. Clear only the invalid recovery record, or keep it for now?")
                .setPositiveButton("Clear invalid record", (ignored, which) -> {
                    try { updaterRecoveryStore.clearInvalidRecordAfterUserChoice(); }
                    catch (IOException exception) {
                        showInfo("Recovery record retained", "The invalid update record could not be cleared safely.");
                    }
                })
                .setNegativeButton("Keep for now", null)
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(ignored -> recoveryPromptShowing = false);
        dialog.show();
    }

    private void offerUnrecoverableArtifact(File artifact) {
        if (recoveryPromptShowing) return;
        recoveryPromptShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Retained APK needs review")
                .setMessage("A verified-cache APK has no readable recovery metadata, so Daymark cannot safely offer it for saving or opening. It will remain in private storage unless you explicitly discard it.")
                .setPositiveButton("Discard retained APK", (ignored, which) -> {
                    try {
                        updaterRecoveryStore.discardOrphanAfterUserChoice(artifact);
                        Toast.makeText(this, "Retained APK discarded. Nothing was installed.", Toast.LENGTH_LONG).show();
                    } catch (IOException exception) {
                        showInfo("APK retained", "The retained update could not be discarded safely.");
                    }
                    mainHandler.post(() -> {
                        if (activityResumed && !recoverPendingVerifiedUpdate()) checkForUpdates(false);
                    });
                })
                .setNegativeButton("Keep for now", null)
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(ignored -> recoveryPromptShowing = false);
        dialog.show();
    }

    @SuppressWarnings("deprecation")
    private void offerManualApkSave(UpdaterCore.Release release, File verifiedApk) {
        if (recoveryPromptShowing) return;
        recoveryPromptShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("APK verified — not installed")
                .setMessage("The APK passed the declared size, SHA-256, package, version, minimum Android version, and signer checks. The verified app-private copy is retained across restarts until you explicitly discard it. Daymark will not open an installer. Save a copy to a location you choose; if you decide to continue, open it yourself from Files. Android controls any install-source approval and final confirmation.")
                .setPositiveButton("Save verified APK", (ignored, which) -> startVerifiedApkSave(release, verifiedApk))
                .setNegativeButton("Discard", (ignored, which) -> discardVerifiedUpdate(release))
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(ignored -> recoveryPromptShowing = false);
        dialog.show();
    }

    @SuppressWarnings("deprecation")
    private void startVerifiedApkSave(UpdaterCore.Release release, File verifiedApk) {
        String token = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        PendingSaveTransaction transaction = new PendingSaveTransaction(release, token);
        pendingVerifiedApk = verifiedApk;
        pendingVerifiedRelease = release;
        pendingSaveTransaction = transaction;
        Intent saveIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        saveIntent.addCategory(Intent.CATEGORY_OPENABLE);
        saveIntent.setType("application/vnd.android.package-archive");
        saveIntent.putExtra(Intent.EXTRA_TITLE, transaction.pickerTitle);
        try {
            startActivityForResult(saveIntent, REQUEST_SAVE_VERIFIED_APK);
        } catch (Exception exception) {
            pendingVerifiedApk = null;
            pendingVerifiedRelease = null;
            pendingSaveTransaction = null;
            postActivityCallback(() -> showSaveFailureChoices(release, verifiedApk,
                    "Android's file picker could not be opened. The verified update remains in app-private storage."));
        }
    }

    private void discardVerifiedUpdate(UpdaterCore.Release release) {
        try {
            updaterRecoveryStore.removeAfterUserChoice(release);
            Toast.makeText(this, "Verified update discarded. Nothing was installed.", Toast.LENGTH_LONG).show();
        } catch (Exception exception) {
            showInfo("Update retained", "The verified update could not be discarded safely and remains in app-private storage.");
        }
    }

    private void showSaveFailureChoices(UpdaterCore.Release release, File verifiedApk, String message) {
        if (recoveryPromptShowing) return;
        recoveryPromptShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("APK not saved")
                .setMessage(message + " No update was installed.")
                .setPositiveButton("Try saving again", (ignored, which) -> startVerifiedApkSave(release, verifiedApk))
                .setNegativeButton("Discard", (ignored, which) -> discardVerifiedUpdate(release))
                .setNeutralButton("Keep for later", null)
                .setCancelable(false)
                .create();
        dialog.setOnDismissListener(ignored -> recoveryPromptShowing = false);
        dialog.show();
    }

    private void copyVerifiedApkToDocument(File source, Uri destination, UpdaterCore.Release release,
            PendingSaveTransaction transaction) throws Exception {
        // ACTION_CREATE_DOCUMENT returns a newly created document; all operations remain scoped
        // to that URI. Never open an existing document by a guessed display name.
        SafApkSaver.copyVerifiedApk(source, release, new SafApkSaver.Document() {
            private Uri documentUri = destination;

            @Override public String displayName() throws IOException {
                return queryDocumentDisplayName(documentUri);
            }

            @Override public String renameTo(String requestedName) throws IOException {
                try {
                    Uri renamed = DocumentsContract.renameDocument(getContentResolver(), documentUri, requestedName);
                    if (renamed == null) return null;
                    documentUri = renamed;
                    return queryDocumentDisplayName(documentUri);
                } catch (Exception exception) {
                    throw new IOException("The selected document provider could not rename the app-created document.", exception);
                }
            }

            @Override public OutputStream openForWrite() throws IOException {
                try {
                    return getContentResolver().openOutputStream(documentUri, "w");
                } catch (Exception exception) {
                    throw new IOException("The selected document could not be opened for writing.", exception);
                }
            }

            @Override public InputStream openForRead() throws IOException {
                try {
                    return getContentResolver().openInputStream(documentUri);
                } catch (Exception exception) {
                    throw new IOException("The selected document could not be read back for verification.", exception);
                }
            }

            @Override public boolean delete() throws IOException {
                try {
                    return DocumentsContract.deleteDocument(getContentResolver(), documentUri);
                } catch (Exception exception) {
                    throw new IOException("The app-created document could not be removed.", exception);
                }
            }
        }, transaction.token, transaction.pickerTitle, transaction.preferredFinalName);
    }

    private String queryDocumentDisplayName(Uri documentUri) throws IOException {
        try (Cursor cursor = getContentResolver().query(documentUri,
                new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IOException("The selected document name could not be read.");
            }
            int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            if (nameColumn < 0) throw new IOException("The selected provider omitted the document name.");
            return cursor.getString(nameColumn);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("The selected document name could not be read.", exception);
        }
    }

    private void rememberDismissedRelease(String tag) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putString(DISMISSED_UPDATE_TAG_KEY, tag).apply();
    }

    private void showAdvancedDetails() {
        String version = "unknown";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception exception) { StartupDiagnostics.record(StartupDiagnostics.PACKAGE_INFO_FAILED, exception); }
        String distributionDetails = BuildConfig.UPDATER_ENABLED
                ? "GitHub sideload distribution: declares INTERNET and ACCESS_NETWORK_STATE for foreground checks and consented, Wi-Fi/mobile-choice downloads. Verified APKs can be saved for manual opening; Daymark does not launch an installer. The protected publisher release gate is closed."
                : "Play distribution: no network or package-install permissions are declared. Tasks remain local and offline.";
        String details = "Daymark " + version + "\n\n"
                + "Tasks: encrypted in an app-private file; attachment references use schema v2. Existing v1 task data remains readable.\n\n"
                + "Encryption: AES-GCM; the key is stored in Android Keystore. Hardware protection depends on the device.\n\n"
                + "Attachments: encrypted app-private payloads; up to 20 MiB per file, 100 MiB total, and five per task. No attachment is opened or executed by Daymark.\n\n"
                + "Suggestions: due-date and priority rules only; no AI service.\n\n"
                + "Tasks stay local and are never sent automatically. The embedded browser uses the Internet only after Go or an AI-site tap; page resources may also contact their own or third-party endpoints. No account, task sync, analytics, or background search is built in.\n\n"
                + "Not included: voice input, GGUF models, command-line bridge, plugins, or app/OS updates.\n\n"
                + "Copied diagnostics contain version, Android API, theme, task count, and storage status. They never contain task titles.";
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(8), dp(22), dp(8));
        TextView body = text(details, 14, palette.text, Typeface.NORMAL);
        body.setLineSpacing(dp(3), 1f);
        content.addView(body);
        Button copy = compactButton("Copy non-sensitive diagnostics", false);
        copy.setOnClickListener(view -> copyDiagnostics());
        content.addView(copy, topMargin(dp(12)));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("Advanced details").setView(scroll)
                .setPositiveButton("Done", null).show();
    }

    private void copyDiagnostics() {
        String version = "unknown";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception exception) { StartupDiagnostics.record(StartupDiagnostics.PACKAGE_INFO_FAILED, exception); }
        String report = "Daymark " + version + " | Android API " + Build.VERSION.SDK_INT
                + " | theme " + themeLabel() + " | tasks " + tasks.size()
                + " | encrypted storage " + (storageReady ? "ready" : "unavailable");
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            showToast("Clipboard is not available on this device.");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Daymark diagnostics", report));
        showToast("Diagnostics copied. Task titles were not included.");
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("Done", null).show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14 * textScale);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(palette.dark ? palette.background : Color.WHITE);
        button.setMinHeight(dp(50));
        button.setMinimumHeight(dp(50));
        button.setBackground(shape(palette.accent, 13, palette.accent));
        return button;
    }

    private Button plainButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(11 * textScale);
        button.setTextColor(palette.accent);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(shape(palette.accentSoft, 18, palette.line));
        return button;
    }

    private Button compactButton(String label, boolean danger) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(11 * textScale);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinimumWidth(dp(60));
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setTextColor(danger ? palette.danger : palette.accent);
        button.setBackground(shape(danger ? palette.dangerSoft : palette.surfaceAlt, 9, palette.line));
        return button;
    }

    private LinearLayout horizontalCard(int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(shape(color, 14, palette.line));
        card.setElevation(dp(1));
        return card;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size * textScale);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        view.setIncludeFontPadding(true);
        return view;
    }

    private GradientDrawable shape(int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams bottomMargin(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = value;
        return params;
    }

    private LinearLayout.LayoutParams chipMargin() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        params.leftMargin = dp(5);
        return params;
    }

    private LinearLayout.LayoutParams topMargin(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = value;
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Palette {
        final boolean dark;
        final int background, surface, surfaceAlt, suggestionSurface, text, muted, line;
        final int accent, accentSoft, warning, danger, dangerSoft;

        private Palette(boolean dark, boolean highContrast) {
            this.dark = dark;
            if (dark) {
                background = Color.rgb(18, 24, 21);
                surface = Color.rgb(27, 36, 31);
                surfaceAlt = Color.rgb(36, 51, 42);
                suggestionSurface = Color.rgb(29, 43, 34);
                text = highContrast ? Color.WHITE : Color.rgb(237, 243, 238);
                muted = highContrast ? Color.rgb(225, 233, 227) : Color.rgb(164, 178, 167);
                line = highContrast ? Color.rgb(136, 153, 141) : Color.rgb(54, 68, 59);
                accent = highContrast ? Color.rgb(190, 235, 199) : Color.rgb(155, 197, 164);
                accentSoft = highContrast ? Color.rgb(31, 58, 39) : Color.rgb(37, 57, 43);
                warning = highContrast ? Color.rgb(255, 214, 147) : Color.rgb(229, 188, 126);
                danger = highContrast ? Color.rgb(255, 177, 164) : Color.rgb(233, 153, 143);
                dangerSoft = highContrast ? Color.rgb(71, 33, 29) : Color.rgb(62, 39, 37);
            } else {
                background = Color.rgb(246, 247, 244);
                surface = Color.WHITE;
                surfaceAlt = Color.rgb(239, 244, 238);
                suggestionSurface = Color.rgb(239, 245, 238);
                text = highContrast ? Color.BLACK : Color.rgb(32, 41, 35);
                muted = highContrast ? Color.rgb(48, 55, 50) : Color.rgb(91, 104, 94);
                line = highContrast ? Color.rgb(101, 112, 103) : Color.rgb(226, 233, 226);
                accent = highContrast ? Color.rgb(17, 64, 39) : Color.rgb(49, 91, 70);
                accentSoft = highContrast ? Color.rgb(224, 239, 225) : Color.rgb(232, 241, 232);
                warning = highContrast ? Color.rgb(109, 62, 12) : Color.rgb(151, 84, 53);
                danger = highContrast ? Color.rgb(129, 35, 26) : Color.rgb(167, 71, 71);
                dangerSoft = highContrast ? Color.rgb(255, 232, 227) : Color.rgb(251, 239, 236);
            }
        }

        static Palette from(Activity activity, int mode, boolean highContrast) {
            boolean dark;
            if (mode == THEME_DARK) dark = true;
            else if (mode == THEME_LIGHT) dark = false;
            else dark = (activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            return new Palette(dark, highContrast);
        }
    }
}


