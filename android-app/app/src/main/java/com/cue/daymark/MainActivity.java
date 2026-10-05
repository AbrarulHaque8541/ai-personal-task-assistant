package com.cue.daymark;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.provider.DocumentsContract;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_ATTACH_DOCUMENT = 7341;
    private static final int REQUEST_PORTABLE_EXPORT = 7342;
    private static final int REQUEST_PORTABLE_IMPORT = 7343;
    private static final String STATE_PENDING_ATTACHMENT_TASK = "pending_attachment_task";
    private static final String STATE_PENDING_PORTABLE_IMPORT_URI = "pending_portable_import_uri";
    private static final String PREFERENCES = "daymark.preferences.v1";
    private static final String THEME_KEY = "theme_mode";
    private static final int THEME_SYSTEM = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;
    private static final String POWER_MODE_KEY = "power_mode";
    private static final String TEXT_SIZE_KEY = "text_size_mode";
    private static final String HIGH_CONTRAST_KEY = "high_contrast";

    private final List<Task> tasks = new ArrayList<>();
    private final List<Task> lastSavedTasks = new ArrayList<>();
    private final List<String> filterKeys = Arrays.asList(
            TaskLogic.FILTER_ALL, TaskLogic.FILTER_TODAY,
            TaskLogic.FILTER_UPCOMING, TaskLogic.FILTER_COMPLETED);
    private final List<Button> filterButtons = new ArrayList<>();
    private final List<View> powerOnlyViews = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ExecutorService storageExecutor;
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
    private Uri activePortableImportUri;
    private boolean pendingPortableImportNeedsRepick;
    private boolean portableImportCleanupActive;
    private final Object attachmentCancelLock = new Object();
    private volatile boolean attachmentCancelRequested;
    private boolean attachmentCanCancel;
    private String pendingAttachmentTaskId;
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
    private float textScale = 1.0f;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences preferences = getApplicationContext()
                .getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        themeMode = Math.max(THEME_SYSTEM, Math.min(THEME_DARK, preferences.getInt(THEME_KEY, THEME_SYSTEM)));
        powerMode = preferences.getBoolean(POWER_MODE_KEY, false);
        textSizeMode = Math.max(0, Math.min(2, preferences.getInt(TEXT_SIZE_KEY, 1)));
        textScale = textSizeMode == 0 ? 0.9f : textSizeMode == 2 ? 1.25f : 1.0f;
        highContrast = preferences.getBoolean(HIGH_CONTRAST_KEY, false);
        setTheme(themeResource(themeMode));
        super.onCreate(savedInstanceState);

        palette = Palette.from(this, themeMode, highContrast);
        storageExecutor = Executors.newSingleThreadExecutor();
        taskStore = new EncryptedTaskStore(this);
        attachmentStore = new AndroidAttachmentStore(getApplicationContext());
        portableBackupManager = new PortableBackupManager(getApplicationContext());
        if (savedInstanceState != null) {
            pendingAttachmentTaskId = savedInstanceState.getString(STATE_PENDING_ATTACHMENT_TASK);
            String savedPortableUri = savedInstanceState.getString(STATE_PENDING_PORTABLE_IMPORT_URI);
            if (savedPortableUri != null) {
                pendingPortableImportUri = restorePendingPortableImportUri(savedPortableUri);
                pendingPortableImportNeedsRepick = pendingPortableImportUri == null;
            }
        }
        buildInterface();
        loadEncryptedTasks();
    }

    @Override
    protected void onDestroy() {
        if (undoDismissal != null) mainHandler.removeCallbacks(undoDismissal);
        portableCancelRequested = true;
        if (!portableRestoreWorkerActive) {
            PortableBackupCodec.clear(pendingRecoveryKey);
            pendingRecoveryKey = null;
        }
        if (pendingExportArchive != null && pendingExportArchive.exists()) pendingExportArchive.delete();
        if (!isChangingConfigurations()) discardPendingPortableImport();
        if (storageExecutor != null) storageExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (pendingAttachmentTaskId != null) {
            state.putString(STATE_PENDING_ATTACHMENT_TASK, pendingAttachmentTaskId);
        }
        String pendingUriForState = pendingPortableImportUri == null ? null
                : PortableImportGrantRecovery.uriForActivityState(pendingPortableImportUri.toString());
        if (pendingUriForState != null && hasPersistedPortableReadGrant(pendingPortableImportUri)) {
            state.putString(STATE_PENDING_PORTABLE_IMPORT_URI, pendingUriForState);
        }
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scrollView, scrollParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addDashboardHeading(content);
        addCaptureButton(content);
        addPrivacyCard(content);
        addSuggestionCard(content);
        addTaskSection(content);
        addLocalStorageNote(content);
        addUndoBar();
        applySystemBarsAndInsets();
        setContentView(root);
        root.requestApplyInsets();
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

        pathButton = plainButton(powerMode ? "Simple path" : "Power path");
        pathButton.setContentDescription(powerMode
                ? "Switch to Simple path. Your tasks will stay the same."
                : "Switch to Power path for search, filters, and suggestions. Your tasks will stay the same.");
        pathButton.setOnClickListener(view -> toggleExperienceMode());
        bar.addView(pathButton);
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
        copy.addView(text("No account, internet connection, or sync", 12, palette.muted, Typeface.NORMAL));
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

    private void addCaptureButton(LinearLayout content) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(shape(palette.surface, 14, palette.line));
        card.setElevation(dp(1));

        TextView example = text("For example: Call the school about Friday’s trip.",
                14, palette.muted, Typeface.NORMAL);
        card.addView(example, bottomMargin(dp(9)));
        quickCaptureInput = new EditText(this);
        quickCaptureInput.setSingleLine(true);
        quickCaptureInput.setTextSize(16 * textScale);
        quickCaptureInput.setHint("Type a task in your own words");
        quickCaptureInput.setContentDescription("What do you want to get done? Type a task");
        quickCaptureInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        quickCaptureInput.setFilters(new InputFilter[] { new InputFilter.LengthFilter(160) });
        quickCaptureInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        quickCaptureInput.setMinHeight(dp(52));
        quickCaptureInput.setTextColor(palette.text);
        quickCaptureInput.setHintTextColor(palette.muted);
        quickCaptureInput.setBackground(shape(palette.background, 10, palette.line));
        card.addView(quickCaptureInput, bottomMargin(dp(9)));

        addTaskButton = primaryButton("Add task");
        addTaskButton.setContentDescription("Add this task with no due date and medium priority");
        addTaskButton.setOnClickListener(view -> addQuickTask());
        card.addView(addTaskButton, bottomMargin(dp(7)));

        addDetailsButton = compactButton("Add with a date or priority", false);
        addDetailsButton.setOnClickListener(view -> showTaskEditor(null, quickCaptureInput.getText().toString()));
        card.addView(addDetailsButton, bottomMargin(dp(7)));
        captureFeedback = text(powerMode
                        ? "Power path adds search, filters, and ranked demo suggestions. It uses the same tasks."
                        : "New tasks start with no due date and medium priority. You can change both later.",
                12, palette.muted, Typeface.NORMAL);
        captureFeedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(captureFeedback);
        quickCaptureInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                addQuickTask();
                return true;
            }
            return false;
        });
        content.addView(card, bottomMargin(dp(15)));
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
                root.setPadding(dp(8) + bars.left, bars.top, dp(8) + bars.right, bars.bottom);
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
        storageExecutor.execute(() -> {
            List<Task> loaded = null;
            Exception failure = null;
            try {
                PortableImportGrantRecovery.awaitNoActivityRestoreWorker();
                synchronized (AndroidAttachmentStore.transactionLock()) {
                    loaded = taskStore.load();
                    portableBackupManager.reconcileAndReleaseAbandonedImportUri(loaded, attachmentStore);
                    try { attachmentStore.cleanupOrphans(attachmentIds(loaded)); }
                    catch (Exception ignored) { /* Retry orphan cleanup on a later launch. */ }
                    portableBackupManager.cleanupTransientFiles();
                }
            } catch (Exception exception) {
                failure = exception;
            }
            List<Task> result = loaded;
            Exception error = failure;
            mainHandler.post(() -> {
                if (isFinishing()) return;
                storageLoading = false;
                if (error == null) {
                    tasks.clear();
                    tasks.addAll(result);
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
                render();
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
        if (!storageReady) return;
        long revision = ++saveRevision;
        List<Task> snapshot = new ArrayList<>(tasks);
        storageStatus.setText("Saving encrypted tasks…");
        storageStatus.setTextColor(palette.muted);
        storageExecutor.execute(() -> {
            Exception failure = null;
            try {
                taskStore.save(snapshot);
            } catch (Exception exception) {
                failure = exception;
            }
            Exception error = failure;
            mainHandler.post(() -> {
                if (isFinishing()) return;
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
        addTaskButton.setEnabled(canEdit());
        addTaskButton.setAlpha(canEdit() ? 1f : 0.55f);
        addDetailsButton.setEnabled(canEdit());
        quickCaptureInput.setEnabled(canEdit());
        if (searchInput != null) searchInput.setEnabled(!storageLoading && !storageLoadFailed);
        for (View powerOnly : powerOnlyViews) {
            powerOnly.setVisibility(powerMode ? View.VISIBLE : View.GONE);
        }
        renderFilters(today);
        renderTaskList(today);
        renderTaskCount(today);
        renderSuggestions(today);
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
                        || (TaskLogic.FILTER_COMPLETED.equals(key) && task.completed)) count++;
            }
            String label;
            if (TaskLogic.FILTER_ALL.equals(key)) label = "All";
            else if (TaskLogic.FILTER_TODAY.equals(key)) label = "Today";
            else if (TaskLogic.FILTER_UPCOMING.equals(key)) label = "Upcoming";
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

    private void addQuickTask() {
        if (!canEdit()) return;
        String title = quickCaptureInput.getText() == null ? "" : quickCaptureInput.getText().toString().trim();
        if (title.isEmpty()) {
            quickCaptureInput.setError("Type a task first");
            captureFeedback.setText("Type a task in the box above, or choose Add with a date or priority.");
            quickCaptureInput.requestFocus();
            return;
        }
        try {
            tasks.add(TaskLogic.create(title, null, "medium"));
            quickCaptureInput.setText("");
            quickCaptureInput.setError(null);
            captureFeedback.setText("Task added. It has no due date and medium priority; tap Edit to change either.");
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
        Button attachments = compactButton("Files · " + task.attachments.size(), false);
        attachments.setContentDescription("Manage " + task.attachments.size()
                + " attachments for task: " + task.title);
        attachments.setEnabled(canEdit());
        attachments.setOnClickListener(view -> showAttachmentManager(task));
        copy.addView(attachments, topMargin(dp(4)));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button edit = compactButton("Edit", false);
        edit.setContentDescription("Edit task: " + task.title);
        edit.setEnabled(canEdit());
        edit.setOnClickListener(view -> showTaskEditor(task));
        row.addView(edit);
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

    private String dueLabel(Task task) {
        if (task.dueDate == null) return "No due date";
        LocalDate due = LocalDate.parse(task.dueDate);
        LocalDate today = LocalDate.now();
        if (due.isBefore(today)) return "Overdue · " + TaskLogic.formatDate(due);
        if (due.equals(today)) return "Due today";
        return "Due " + TaskLogic.formatDate(due);
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
                    mainHandler.post(() -> {
                        if (isFinishing()) return;
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
            mainHandler.post(() -> {
                if (isFinishing()) return;
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
            mainHandler.post(() -> {
                if (isFinishing()) return;
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

    private void showTaskEditor(Task editing) {
        showTaskEditor(editing, "");
    }

    private void showTaskEditor(Task editing, String draftTitle) {
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
        form.addView(titleInput, bottomMargin(dp(12)));

        TextView fieldLabel = text("Due date (optional)", 13, palette.muted, Typeface.BOLD);
        fieldLabel.setLetterSpacing(0.08f);
        form.addView(fieldLabel, bottomMargin(dp(4)));

        final String[] selectedDate = { editing == null ? null : editing.dueDate };
        LinearLayout dateActions = new LinearLayout(this);
        dateActions.setGravity(Gravity.CENTER_VERTICAL);
        Button dateButton = compactButton(dateButtonLabel(selectedDate[0]), false);
        dateButton.setOnClickListener(view -> showDatePicker(selectedDate, dateButton));
        dateActions.addView(dateButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button clearDate = compactButton("Clear", false);
        clearDate.setOnClickListener(view -> {
            selectedDate[0] = null;
            dateButton.setText("Choose a date");
        });
        dateActions.addView(clearDate, new LinearLayout.LayoutParams(dp(76), dp(48)));
        form.addView(dateActions, bottomMargin(dp(14)));

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
        prioritySpinner.setSelection(priorityIndex(editing == null ? "medium" : editing.priority));
        form.addView(prioritySpinner, bottomMargin(dp(8)));

        TextView validation = text("Please enter a task title.", 12, palette.danger, Typeface.NORMAL);
        validation.setVisibility(View.GONE);
        form.addView(validation);

        ScrollView formScroll = new ScrollView(this);
        formScroll.setFillViewport(false);
        formScroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing == null ? "Add task details" : "Edit task")
                .setView(formScroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(editing == null ? "Add task" : "Save changes", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(view -> {
                String normalized = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
                if (normalized.isEmpty()) {
                    validation.setVisibility(View.VISIBLE);
                    titleInput.setError("Enter a task title");
                    titleInput.requestFocus();
                    return;
                }
                String priority = priorityValue(prioritySpinner.getSelectedItemPosition());
                try {
                    if (editing == null) {
                        tasks.add(TaskLogic.create(normalized, selectedDate[0], priority));
                        quickCaptureInput.setText("");
                        captureFeedback.setText("Task added. You can edit it later in your list.");
                    } else {
                        replaceTask(TaskLogic.update(editing, normalized, selectedDate[0], priority));
                    }
                    dialog.dismiss();
                    render();
                    saveTasksAsync();
                    showToast(editing == null ? "Task added." : "Task updated.");
                } catch (IllegalArgumentException exception) {
                    validation.setText(exception.getMessage());
                    validation.setVisibility(View.VISIBLE);
                }
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        titleInput.requestFocus();
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
        powerMode = !powerMode;
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(POWER_MODE_KEY, powerMode).apply();
        activeFilter = TaskLogic.FILTER_ALL;
        searchQuery = "";
        if (searchInput != null) searchInput.setText("");
        pathButton.setText(powerMode ? "Simple path" : "Power path");
        pathButton.setContentDescription(powerMode
                ? "Switch to Simple path. Your tasks will stay the same."
                : "Switch to Power path for search, filters, and suggestions. Your tasks will stay the same.");
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
        catch (Exception ignored) { return false; }
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
            if (pendingPortableImportUri.equals(uri) && hasPersistedPortableReadGrant(uri)) return true;
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
        try {
            portableBackupManager.recordActivePortableImportUri(uri);
        } catch (IOException journalFailure) {
            // The manager immediately releases only this URI if its durable journal write fails.
            showToast("The restore could not be prepared safely. Reopen Daymark before trying again.");
            return false;
        }
        pendingPortableImportUri = uri;
        return true;
    }

    private Uri restorePendingPortableImportUri(String savedUri) {
        String restored = PortableImportGrantRecovery.restorePendingActivityUri(savedUri,
                value -> hasPersistedPortableReadGrant(Uri.parse(value)));
        return restored == null ? null : Uri.parse(restored);
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

    private void discardPendingPortableImport(Uri expected) {
        Uri selected = pendingPortableImportUri;
        if (selected == null || (expected != null && !expected.equals(selected))) return;
        pendingPortableImportUri = null;
        reconcileAndReleasePortableImportSelection(selected);
    }

    private void reconcileAndReleasePortableImportSelection(Uri selected) {
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
                mainHandler.post(() -> {
                    if (result == null) {
                        portableImportCleanupActive = false;
                    } else if (!isFinishing() && !isDestroyed()) {
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
        Uri selected = pendingPortableImportUri;
        if (selected == null) return;
        if (!hasPersistedPortableReadGrant(selected)) {
            pendingPortableImportUri = null;
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
                .setNegativeButton("Cancel", (ignored, which) -> discardPendingPortableImport(selected))
                .setPositiveButton("Restore as new tasks", null)
                .create();
        dialog.setOnCancelListener(ignored -> {
            if (!isChangingConfigurations()) discardPendingPortableImport(selected);
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
            dialog.dismiss();
            beginPortableRestore(selected, key);
        }));
        dialog.setOnDismissListener(ignored -> keyInput.setText(""));
        dialog.show();
    }

    private void beginPortableRestore(Uri selected, byte[] recoveryKey) {
        if (!canEdit()) {
            PortableBackupCodec.clear(recoveryKey);
            reconcileAndReleasePortableImportSelection(selected);
            showToast("Encrypted storage is not ready for restore.");
            return;
        }
        activePortableImportUri = selected;
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
                                portableBackupManager.finishActivePortableImportUri(selected, current, attachmentStore);
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
            mainHandler.post(() -> {
                if (activePortableImportUri == selected) activePortableImportUri = null;
                if (isFinishing() || isDestroyed()) return;
                PortableBackupCodec.clear(pendingRecoveryKey);
                pendingRecoveryKey = null;
                pendingPortableImportUri = null;
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
            reconcileAndReleasePortableImportSelection(selected);
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
                "Language: English only",
                "Screen reader support",
                "Reduced motion",
                "Permission status: " + permissionStatusLabel(),
                "Advanced details",
                "Encrypted backup / restore"
        };
        new AlertDialog.Builder(this)
                .setTitle("More settings")
                .setItems(options, (dialog, selected) -> {
                    if (selected == 0) showThemePicker();
                    else if (selected == 1) showTextSizePicker();
                    else if (selected == 2) toggleHighContrast();
                    else if (selected == 3) showLanguageInfo();
                    else if (selected == 4) showScreenReaderInfo();
                    else if (selected == 5) showReducedMotionInfo();
                    else if (selected == 6) showPermissionStatus();
                    else if (selected == 7) showAdvancedDetails();
                    else showPortableBackupDialog();
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
                    textScale = selected == 0 ? 0.9f : selected == 2 ? 1.25f : 1.0f;
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
            return requested == null || requested.length == 0 ? "None needed" : requested.length + " declared";
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
                        + "Task capture, editing, completion, deletion, and encrypted on-device storage need none. Attachments use Android's document picker after you choose a file; Daymark does not ask for broad storage permission. Daymark does not ask for permissions when it opens.\n\n"
                        + "A future feature may request a runtime permission only after you choose to use that feature, with a clear explanation. You can deny it; core tasks must remain available. Special app access would open Android Settings. An ordinary app cannot grant signature-only or privileged permissions. Daymark does not request root or superuser access.";
            } else {
                StringBuilder details = new StringBuilder("Permissions declared by this app:\n");
                for (String permission : requested) {
                    details.append("• ").append(permission).append('\n');
                }
                details.append("\nThis screen reports manifest declarations; it does not request or grant access. Runtime permission requests must follow a user action for the feature that needs them.");
                message = details.toString();
            }
        } catch (Exception ignored) {
            message = "Permission status could not be read. No permission was requested by this screen.";
        }
        showInfo("Permission status", message);
    }

    private void showAdvancedDetails() {
        String version = "unknown";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { }
        String details = "Daymark " + version + "\n\n"
                + "Tasks: encrypted in an app-private file; attachment references use schema v2. Existing v1 task data remains readable.\n\n"
                + "Encryption: AES-GCM; the key is stored in Android Keystore. Hardware protection depends on the device.\n\n"
                + "Attachments: encrypted app-private payloads; up to 20 MiB per file, 100 MiB total, and five per task. No attachment is opened or executed by Daymark.\n\n"
                + "Suggestions: due-date and priority rules only; no AI service.\n\n"
                + "Offline: no account, network permission, background service, or optional content download.\n\n"
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
        } catch (Exception ignored) { }
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
