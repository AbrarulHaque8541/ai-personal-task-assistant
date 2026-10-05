package com.cue.daymark;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.graphics.drawable.StateListDrawable;
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
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String PREFERENCES = "daymark.preferences.v1";
    private static final String THEME_KEY = "theme_mode";
    private static final int THEME_SYSTEM = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;
    private static final String POWER_MODE_KEY = "power_mode";
    private static final String TEXT_SIZE_KEY = "text_size_mode";
    private static final String HIGH_CONTRAST_KEY = "high_contrast";
    private static final String BROWSER_PREFERENCES = "daymark.browser.local.v1";
    private static final String BROWSER_HISTORY_KEY = "history_urls";
    private static final String SEARCH_ENGINE_KEY = "search_engine";
    private static final String BROWSER_ONLINE_ENABLED_KEY = "online_browsing_enabled";
    private static final String SAFE_BROWSING_ENABLED_KEY = "safe_browsing_enabled";

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
    private boolean storageReady;
    private long saveRevision;
    private int themeMode;
    private int textSizeMode;
    private boolean powerMode;
    private boolean highContrast;
    private boolean webMode;
    private BrowserNetworkPolicy browserNetworkPolicy = new BrowserNetworkPolicy();
    private BrowserSettingsPolicy browserSettingsPolicy = new BrowserSettingsPolicy();
    private boolean suppressBrowserOnlineToggleListener;
    private boolean suppressSafeBrowsingToggleListener;
    private String taskDraft = "";
    private float textScale = 1.0f;
    private SharedPreferences browserPreferences;
    private BrowserAddress.SearchEngine searchEngine = BrowserAddress.SearchEngine.DUCKDUCKGO;
    private String activeFilter = TaskLogic.FILTER_ALL;
    private String searchQuery = "";
    private Task pendingDeletedTask;
    private int pendingDeletedIndex;
    private Runnable undoDismissal;
    private String highlightedTaskId;
    private Runnable suggestionHighlightReset;
    private Palette palette;

    private LinearLayout root;
    private LinearLayout taskList;
    private LinearLayout suggestionList;
    private ScrollView contentScroll;
    private LinearLayout undoBar;
    private TextView undoMessage;
    private Button undoButton;
    private TextView dateHeading;
    private TextView openCount;
    private TextView dueTodayCount;
    private TextView taskCount;
    private TextView storageStatus;
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
    private Spinner searchEngineSpinner;
    private CheckBox browserOnlineToggle;
    private TextView browserStatus;

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
        browserPreferences = getSharedPreferences(BROWSER_PREFERENCES, MODE_PRIVATE);
        searchEngine = BrowserAddress.SearchEngine.fromName(
                browserPreferences.getString(SEARCH_ENGINE_KEY, BrowserAddress.SearchEngine.DUCKDUCKGO.name()));
        browserNetworkPolicy = new BrowserNetworkPolicy(readBrowserOnlinePreference());
        browserSettingsPolicy = new BrowserSettingsPolicy(readSafeBrowsingPreference());
        sanitizeStoredBrowserHistory();
        buildInterface();
        loadEncryptedTasks();
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
    protected void onPause() {
        if (browserWebView != null) {
            discardBrowserWebView();
            if (browserHomeView != null) browserHomeView.setVisibility(View.VISIBLE);
            if (browserStatus != null) {
                browserStatus.setText(browserNetworkPolicy.isOnlineEnabled()
                        ? "The page closed when Daymark went into the background. Online remains enabled; no page was restored."
                        : "The page closed when Daymark went into the background. Offline; WebView network loads are blocked.");
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (undoDismissal != null) mainHandler.removeCallbacks(undoDismissal);
        if (suggestionHighlightReset != null) mainHandler.removeCallbacks(suggestionHighlightReset);
        if (storageExecutor != null) storageExecutor.shutdown();
        if (browserWebView != null) discardBrowserWebView();
        super.onDestroy();
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
        root.addView(buildSharedComposer(), bottomMargin(dp(8)));

        ScrollView scrollView = new ScrollView(this);
        contentScroll = scrollView;
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
        copy.addView(text("Web requests only after your tap · no task sync", 12, palette.muted, Typeface.NORMAL));
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        storageStatus = text("Opening encrypted storage…", 12, palette.muted, Typeface.NORMAL);
        storageStatus.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        storageStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        card.addView(storageStatus);
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
        card.addView(quickCaptureInput, bottomMargin(dp(9)));

        taskActions = new LinearLayout(this);
        taskActions.setOrientation(LinearLayout.VERTICAL);
        addTaskButton = primaryButton("Add task");
        addTaskButton.setContentDescription("Add this task with no due date and medium priority");
        addTaskButton.setOnClickListener(view -> addQuickTask());
        taskActions.addView(addTaskButton, bottomMargin(dp(5)));

        addDetailsButton = compactButton("Add with a date or priority", false);
        addDetailsButton.setOnClickListener(view -> showTaskEditor(null, quickCaptureInput.getText().toString()));
        taskActions.addView(addDetailsButton, bottomMargin(dp(5)));
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
        webActions.addView(browserOnlineToggle, bottomMargin(dp(2)));
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
                searchEngine = BrowserAddress.SearchEngine.values()[position];
                browserPreferences.edit().putString(SEARCH_ENGINE_KEY, searchEngine.name()).apply();
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

    private LinearLayout buildBrowserScreen() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(2), dp(8), dp(6));

        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        browserBackButton = compactButton("Back", false);
        browserBackButton.setContentDescription("Go back one page");
        browserBackButton.setOnClickListener(view -> {
            if (browserWebView != null && browserWebView.canGoBack()) browserWebView.goBack();
        });
        browserForwardButton = compactButton("Forward", false);
        browserForwardButton.setContentDescription("Go forward one page");
        browserForwardButton.setOnClickListener(view -> {
            if (browserWebView != null && browserWebView.canGoForward()) browserWebView.goForward();
        });
        browserReloadButton = compactButton("Reload", false);
        browserReloadButton.setContentDescription("Reload the current page");
        browserReloadButton.setOnClickListener(view -> {
            if (browserWebView != null) browserWebView.reload();
        });
        browserHomeButton = compactButton("Home", false);
        browserHomeButton.setContentDescription("Return to the local browser home screen");
        browserHomeButton.setOnClickListener(view -> showBrowserHome());
        browserHistoryButton = compactButton("Site history", false);
        browserHistoryButton.setContentDescription("View local site history, which lists HTTPS origins only, or clear site history and site data");
        browserHistoryButton.setOnClickListener(view -> showBrowserHistoryDialog());
        browserSettingsButton = compactButton("Settings", false);
        browserSettingsButton.setContentDescription("Open Browser Settings to change Safe Browsing protection");
        browserSettingsButton.setOnClickListener(view -> showBrowserSettingsDialog());
        for (Button button : Arrays.asList(browserBackButton, browserForwardButton,
                browserReloadButton, browserHomeButton, browserHistoryButton, browserSettingsButton)) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
            params.setMargins(0, 0, dp(5), 0);
            toolbar.addView(button, params);
        }
        toolbarScroll.addView(toolbar);
        panel.addView(toolbarScroll, bottomMargin(dp(4)));

        TextView disclosure = text(
                "Offline by default. Enable Online only after its disclosure and confirmation; each search or site still needs a tap. Queries/URLs and connection data go to the chosen destination, which may log them; pages may contact third parties. The Online switch blocks Daymark page/resource loads only. Android System WebView Safe Browsing is separate and platform-managed; it may contact Google/Play Services for version/device-dependent threat-list updates or URL-hash checks. The Safe Browsing provider itself is not selectable in Daymark. HTTPS only; HTTP is blocked.",
                11, palette.muted, Typeface.NORMAL);
        disclosure.setPadding(dp(11), dp(8), dp(11), dp(8));
        disclosure.setBackground(shape(palette.accentSoft, 10, palette.accentSoft));
        disclosure.setContentDescription("Browser privacy: Offline by default. Enabling Online requires reviewing a confirmation first, and each search or site still requires a separate tap. The selected destination receives your query or URL and normal connection data such as your IP address and browser identification, and may log it; pages may contact and be logged by third-party endpoints. The Online switch blocks Daymark page and resource loads only and does not control Android System WebView Safe Browsing, a separate platform-managed service that may contact Google/Play Services for threat-list updates or URL-hash-based checks. The Safe Browsing provider itself is not selectable in Daymark. Browser Settings can disable the protection feature only after a warning. WebView M126 and later may send a partial URL hash through a proxy for real-time checks; earlier versions use a local partial-hash database and may query a server on prefix match. This does not mean every full URL is sent; the method depends on WebView version and device settings. Daymark sends no task text, adds no app analytics, and opts out of WebView diagnostic metrics. HTTPS only; HTTP is blocked.");
        panel.addView(disclosure, bottomMargin(dp(5)));

        HorizontalScrollView sitesScroll = new HorizontalScrollView(this);
        sitesScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout sites = new LinearLayout(this);
        sites.setOrientation(LinearLayout.HORIZONTAL);
        sites.addView(browserSiteButton("ChatGPT", "https://chatgpt.com/"));
        sites.addView(browserSiteButton("Claude", "https://claude.ai/"));
        sites.addView(browserSiteButton("Gemini", "https://gemini.google.com/"));
        sites.addView(browserSiteButton("Perplexity", "https://www.perplexity.ai/"));
        sitesScroll.addView(sites);
        panel.addView(sitesScroll, bottomMargin(dp(3)));

        browserStatus = text("Ready. No page has been requested.", 11, palette.muted, Typeface.NORMAL);
        browserStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(browserStatus, bottomMargin(dp(4)));

        browserViewport = new FrameLayout(this);
        browserViewport.setBackground(shape(palette.surface, 12, palette.line));
        browserHomeView = buildBrowserHomeView();
        browserViewport.addView(browserHomeView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(browserViewport, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return panel;
    }

    private Button browserSiteButton(String label, String address) {
        Button button = compactButton(label, false);
        button.setContentDescription("Open the " + label + " website in Daymark's browser");
        button.setOnClickListener(view -> {
            if (!webMode) return;
            quickCaptureInput.setText(address);
            navigateBrowserTo(address);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        params.setMargins(0, 0, dp(5), 0);
        button.setLayoutParams(params);
        return button;
    }

    private View buildBrowserHomeView() {
        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setGravity(Gravity.CENTER_VERTICAL);
        home.setPadding(dp(22), dp(20), dp(22), dp(20));
        TextView title = text("A browser, when you choose", 22, palette.text, Typeface.BOLD);
        home.addView(title, bottomMargin(dp(9)));
        TextView copy = text(
                "Browser access starts Offline. Turn Online on, choose a search engine, enter a search or HTTPS address, then tap Go or a site shortcut for each request. Your query or URL and normal connection details go to that destination; pages may contact third parties. The Online switch blocks Daymark page/resource loads only. Android System WebView Safe Browsing is platform-managed and may contact Google/Play Services for version/device-dependent hash or update checks; its provider is not selectable in Daymark. Daymark sends no task text or app analytics. HTTP is blocked; any per-site exception requires a separate explicit request. AI shortcuts are ordinary websites, not connected model APIs. No website opens automatically.",
                14, palette.muted, Typeface.NORMAL);
        copy.setLineSpacing(dp(3), 1f);
        home.addView(copy, bottomMargin(dp(12)));
        TextView local = text("Site history keeps only validated HTTPS origins (scheme, host, and non-default port). Paths, queries, fragments, URL credentials, and page titles are not saved; older entries are reduced to origins when Daymark opens. Selecting a saved site opens its origin, not its last route. Site history is local but not encrypted. Use Site history to clear it and Daymark's cookies/cache/storage.",
                12, palette.muted, Typeface.NORMAL);
        local.setLineSpacing(dp(2), 1f);
        home.addView(local);
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
        browserWebView.setVisibility(View.VISIBLE);
        browserStatus.setText("Opening page. Its provider and page resources may receive requests.");
        browserWebView.loadUrl(address);
        syncBrowserButtons();
    }

    private boolean ensureBrowserWebView() {
        if (!browserNetworkPolicy.allowsRemoteLoads()) return false;
        if (browserWebView != null) return true;
        browserWebView = new DaymarkWebView(this, browserNetworkPolicy,
                browserSettingsPolicy.isSafeBrowsingEnabled(), new DaymarkWebView.Listener() {
            @Override public void onPageStarted(String url) {
                browserStatus.setText("Loading page. Embedded resources may also make network requests.");
                syncBrowserButtons();
            }

            @Override public void onPageFinished(String url) {
                String safeHistoryUrl = BrowserHistory.sanitizeUrl(url);
                if (browserNetworkPolicy.allowsRemoteLoads() && safeHistoryUrl != null) {
                    String current = browserPreferences.getString(BROWSER_HISTORY_KEY, "");
                    browserPreferences.edit().putString(BROWSER_HISTORY_KEY,
                            BrowserHistory.add(current, safeHistoryUrl)).apply();
                }
                browserStatus.setText("Page loaded. Website content may contact its own or third-party endpoints.");
                syncBrowserButtons();
            }

            @Override public void onNavigationBlocked(String url) {
                browserStatus.setText("A non-HTTPS page link was blocked. Use HTTPS; a per-site HTTP exception requires a separate explicit request.");
                showToast("Only HTTPS pages open here. HTTP is blocked; site exceptions need a separate request.");
            }

            @Override public void onOfflineNavigationBlocked() {
                mainHandler.post(MainActivity.this::showBrowserOfflineStatus);
            }

            @Override public void onHttpNavigationBlocked(String url, boolean redirect) {
                String message = redirect
                        ? "An HTTP redirect/downgrade was blocked. No insecure page was opened."
                        : "An HTTP page navigation was blocked. No insecure page was opened.";
                mainHandler.post(() -> {
                    browserStatus.setText(message + " Only HTTPS is supported. A per-site exception requires a separate explicit request.");
                    showToast("Insecure HTTP navigation blocked. Use HTTPS instead.");
                });
            }

            @Override public void onLoadError() {
                browserStatus.setText("The page could not load securely. Certificate errors are not bypassed.");
            }

            @Override public void onDownloadRequested() {
                showToast("Downloads are not supported in this lightweight browser.");
            }

            @Override public void onRendererGone() {
                discardBrowserWebView(false);
                if (browserHomeView != null) browserHomeView.setVisibility(View.VISIBLE);
                browserStatus.setText("The page stopped unexpectedly. Return to browser home and try again.");
            }
        });
        browserWebView.setBackgroundColor(palette.surface);
        browserWebView.setVisibility(View.GONE);
        browserViewport.addView(browserWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return true;
    }

    private void syncBrowserButtons() {
        if (browserBackButton == null) return;
        boolean available = browserNetworkPolicy.allowsRemoteLoads() && browserWebView != null;
        browserBackButton.setEnabled(available && browserWebView.canGoBack());
        browserForwardButton.setEnabled(available && browserWebView.canGoForward());
        browserReloadButton.setEnabled(available);
    }

    private void showBrowserHome() {
        discardBrowserWebView();
        if (browserHomeView != null) browserHomeView.setVisibility(View.VISIBLE);
        if (quickCaptureInput != null && webMode) quickCaptureInput.setText("");
        if (browserStatus != null) browserStatus.setText("Ready. No page has been requested.");
        syncBrowserButtons();
    }

    private void discardBrowserWebView() {
        discardBrowserWebView(true);
    }

    private void discardBrowserWebView(boolean stopLoading) {
        DaymarkWebView current = browserWebView;
        browserWebView = null;
        if (current != null) {
            try {
                current.getSettings().setBlockNetworkLoads(true);
            } catch (RuntimeException ignored) {
                // A crashed WebView renderer is already detached from remote loading.
            }
            if (stopLoading) current.stopLoading();
            if (current.getParent() instanceof ViewGroup) {
                ((ViewGroup) current.getParent()).removeView(current);
            }
            current.destroy();
        }
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
            if (!isFinishing()) {
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
        TextView note = text("Your tasks stay in this app on this device. They are not backed up or synced.",
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
        storageStatus.setText("Opening encrypted storage…");
        storageExecutor.execute(() -> {
            List<Task> loaded = null;
            Exception failure = null;
            try {
                loaded = taskStore.load();
            } catch (Exception exception) {
                failure = exception;
            }
            List<Task> result = loaded;
            Exception error = failure;
            mainHandler.post(() -> {
                if (isFinishing()) return;
                if (error == null) {
                    tasks.clear();
                    tasks.addAll(result);
                    lastSavedTasks.clear();
                    lastSavedTasks.addAll(result);
                    storageReady = true;
                    storageStatus.setText("Encrypted storage ready");
                    storageStatus.setTextColor(palette.accent);
                } else {
                    tasks.clear();
                    lastSavedTasks.clear();
                    storageReady = false;
                    storageStatus.setText("Storage unavailable · saved data preserved");
                    storageStatus.setTextColor(palette.danger);
                    showToast("Could not unlock encrypted task storage. Existing data was left untouched.");
                }
                render();
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
                    if (revision == saveRevision) {
                        storageReady = true;
                        storageStatus.setText("Encrypted storage ready");
                        storageStatus.setTextColor(palette.accent);
                    }
                } else if (revision == saveRevision) {
                    tasks.clear();
                    tasks.addAll(lastSavedTasks);
                    storageReady = false;
                    storageStatus.setText("Save failed · changes reverted");
                    storageStatus.setTextColor(palette.danger);
                    hideUndoBar();
                    render();
                    showToast("Could not save. Your last saved tasks were restored; further edits are paused.");
                }
            });
        });
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
        openCount.setText(String.valueOf(open));
        dueTodayCount.setText(String.valueOf(dueToday));
        addTaskButton.setEnabled(storageReady && !webMode);
        addTaskButton.setAlpha(storageReady ? 1f : 0.55f);
        addDetailsButton.setEnabled(storageReady && !webMode);
        quickCaptureInput.setEnabled(webMode || storageReady);
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
        taskScreen.setVisibility(webMode ? View.GONE : View.VISIBLE);
        browserScreen.setVisibility(webMode ? View.VISIBLE : View.GONE);
        taskActions.setVisibility(webMode ? View.GONE : View.VISIBLE);
        webActions.setVisibility(webMode ? View.VISIBLE : View.GONE);
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
                .setPositiveButton("Done", null)
                .create();

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
                        || (TaskLogic.FILTER_COMPLETED.equals(key) && task.completed)) count++;
            }
            String label;
            if (TaskLogic.FILTER_ALL.equals(key)) label = "All";
            else if (TaskLogic.FILTER_TODAY.equals(key)) label = "Today";
            else if (TaskLogic.FILTER_UPCOMING.equals(key)) label = "Upcoming";
            else label = "Completed";
            button.setText(label + "  " + count);
            boolean selected = key.equals(activeFilter);
            button.setTextColor(selected ? palette.accent : palette.muted);
            button.setBackground(shape(selected ? palette.accentSoft : palette.surface,
                    20, selected ? palette.accent : palette.line));
            button.setContentDescription(label + ", " + count + " tasks" + (selected ? ", selected" : ""));
        }
    }

    private void renderTaskList() {
        renderTaskList(LocalDate.now());
    }

    private void renderTaskList(LocalDate today) {
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
            if (!selectedQuery.trim().isEmpty()) {
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
        String selectedFilter = powerMode ? activeFilter : TaskLogic.FILTER_ALL;
        String selectedQuery = powerMode ? searchQuery : "";
        int visible = TaskLogic.filter(tasks, selectedFilter, selectedQuery, today).size();
        taskCount.setText(selectedQuery.trim().isEmpty()
                ? visible + (visible == 1 ? " task" : " tasks")
                : visible + (visible == 1 ? " match" : " matches"));
        searchClear.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void addQuickTask() {
        if (webMode || !storageReady) return;
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
            item.setMinimumHeight(dp(48));
            item.setBackground(suggestionItemBackground());
            item.setClickable(true);
            item.setFocusable(true);
            item.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            item.setContentDescription("Demo suggestion " + (index + 1) + ": "
                    + suggestion.task.title + ". " + suggestion.reason
                    + ". Open this task in the list. Read-only; task details will not change.");
            item.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(Button.class.getName());
                }
            });
            item.setOnClickListener(view -> showSuggestedTask(suggestion.task.id));
            item.setOnKeyListener((view, keyCode, event) -> {
                if (!isSuggestionActivationKey(keyCode)) return false;
                if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
                    view.performClick();
                }
                return true;
            });
            TextView number = text(String.valueOf(index + 1), 11, palette.accent, Typeface.BOLD);
            number.setGravity(Gravity.CENTER);
            number.setBackground(shape(palette.accentSoft, 8, palette.accentSoft));
            number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(number, new LinearLayout.LayoutParams(dp(27), dp(27)));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setPadding(dp(9), 0, 0, 0);
            copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            TextView taskTitle = text(suggestion.task.title, 13, palette.text, Typeface.BOLD);
            TextView reason = text(suggestion.reason, 12, palette.muted, Typeface.NORMAL);
            copy.addView(taskTitle);
            copy.addView(reason, topMargin(dp(2)));
            item.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView openHint = text("Open", 11, palette.accent, Typeface.BOLD);
            openHint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintParams.leftMargin = dp(8);
            item.addView(openHint, hintParams);
            suggestionList.addView(item, bottomMargin(dp(6)));
        }
    }

    private boolean isSuggestionActivationKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_SPACE
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER;
    }

    private Task findTaskById(String taskId) {
        if (taskId == null) return null;
        for (Task task : tasks) {
            if (taskId.equals(task.id)) return task;
        }
        return null;
    }

    private void showSuggestedTask(String taskId) {
        if (!storageReady || taskId == null) return;
        Task task = findTaskById(taskId);
        if (task == null) {
            captureFeedback.setText("That task is no longer available. Your saved tasks were not changed.");
            renderSuggestions(LocalDate.now());
            return;
        }

        if (suggestionHighlightReset != null) {
            mainHandler.removeCallbacks(suggestionHighlightReset);
            suggestionHighlightReset.run();
            suggestionHighlightReset = null;
        }
        activeFilter = TaskLogic.FILTER_ALL;
        highlightedTaskId = task.id;
        if (searchInput != null) {
            if (searchInput.length() > 0) searchInput.setText("");
            searchInput.clearFocus();
            InputMethodManager inputMethodManager =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            }
        }
        searchQuery = "";
        LocalDate today = LocalDate.now();
        renderFilters(today);
        renderTaskList(today);
        renderTaskCount(today);
        captureFeedback.setText("Showing “" + task.title + "” from suggestions. "
                + "The task is focused and highlighted; its details were not changed.");

        View taskRow = findTaskRow(task.id);
        if (taskRow == null) {
            highlightedTaskId = null;
            captureFeedback.setText("That task is no longer available. Your saved tasks were not changed.");
            return;
        }
        suggestionHighlightReset = () -> {
            if (task.id.equals(highlightedTaskId)) {
                highlightedTaskId = null;
                View currentRow = findTaskRow(task.id);
                if (currentRow != null) {
                    currentRow.setBackground(taskRowBackground(false));
                    if (!currentRow.hasFocus()) {
                        currentRow.setFocusable(false);
                        currentRow.setContentDescription(null);
                        currentRow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                    }
                }
            }
            suggestionHighlightReset = null;
        };
        mainHandler.postDelayed(suggestionHighlightReset, 2500);
        taskRow.post(() -> {
            if (taskRow.getParent() != taskList || contentScroll == null) return;
            int[] rowLocation = new int[2];
            int[] scrollLocation = new int[2];
            taskRow.getLocationOnScreen(rowLocation);
            contentScroll.getLocationOnScreen(scrollLocation);
            int targetY = contentScroll.getScrollY() + rowLocation[1] - scrollLocation[1] - dp(16);
            if (ValueAnimator.areAnimatorsEnabled()) {
                contentScroll.smoothScrollTo(0, Math.max(0, targetY));
            } else {
                contentScroll.scrollTo(0, Math.max(0, targetY));
            }
            taskRow.requestFocus();
            taskRow.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        });
    }

    private View findTaskRow(String taskId) {
        if (taskList == null || taskId == null) return null;
        for (int index = 0; index < taskList.getChildCount(); index++) {
            View row = taskList.getChildAt(index);
            if (taskId.equals(row.getTag())) return row;
        }
        return null;
    }

    private View buildTaskRow(Task task) {
        LinearLayout row = new LinearLayout(this);
        row.setTag(task.id);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(7), dp(8), dp(8), dp(8));
        boolean highlighted = task.id.equals(highlightedTaskId);
        row.setFocusable(highlighted);
        row.setImportantForAccessibility(highlighted
                ? View.IMPORTANT_FOR_ACCESSIBILITY_YES : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        row.setContentDescription(highlighted
                ? "Task: " + task.title + ". " + dueLabel(task) + ". Priority " + task.priority + "."
                : null);
        row.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus && !task.id.equals(highlightedTaskId)) {
                view.setFocusable(false);
                view.setContentDescription(null);
                view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
        });
        row.setBackground(taskRowBackground(highlighted));
        row.setElevation(dp(1));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setButtonTintList(ColorStateList.valueOf(task.completed ? palette.accent : palette.muted));
        checkBox.setChecked(task.completed);
        checkBox.setContentDescription(task.completed
                ? "Mark “" + task.title + "” as not done"
                : "Mark “" + task.title + "” as done");
        checkBox.setEnabled(storageReady);
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
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button edit = compactButton("Edit", false);
        edit.setContentDescription("Edit task: " + task.title);
        edit.setEnabled(storageReady);
        edit.setOnClickListener(view -> showTaskEditor(task));
        row.addView(edit);
        Button delete = compactButton("Delete", true);
        delete.setContentDescription("Delete task: " + task.title);
        delete.setEnabled(storageReady);
        delete.setOnClickListener(view -> confirmDeleteTask(task));
        row.addView(delete);

        checkBox.setOnCheckedChangeListener((button, checked) -> {
            if (!storageReady || checked == task.completed) return;
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

    private void showTaskEditor(Task editing) {
        showTaskEditor(editing, "");
    }

    private void showTaskEditor(Task editing, String draftTitle) {
        if (webMode) return;
        if (!storageReady) {
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
        if (!storageReady) return;
        int index = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id.equals(task.id)) { index = i; break; }
        }
        if (index < 0) return;
        pendingDeletedIndex = index;
        pendingDeletedTask = tasks.remove(index);
        undoMessage.setText("Task deleted.");
        undoBar.setVisibility(View.VISIBLE);
        // Bring focus to Undo so screen-reader and keyboard users can act on the
        // seven-second recovery window without having to find the control.
        undoButton.requestFocus();
        if (undoDismissal != null) mainHandler.removeCallbacks(undoDismissal);
        undoDismissal = this::hideUndoBar;
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
        if (pendingDeletedTask == null || !storageReady) return;
        for (Task task : tasks) {
            if (task.id.equals(pendingDeletedTask.id)) {
                hideUndoBar();
                return;
            }
        }
        tasks.add(Math.min(pendingDeletedIndex, tasks.size()), pendingDeletedTask);
        pendingDeletedTask = null;
        hideUndoBar();
        render();
        saveTasksAsync();
        showToast("Task restored.");
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
                : "Switch to Power path for search, filters, and suggestions. Your tasks will stay the same.");
        captureFeedback.setText(powerMode
                ? "Power path adds search, filters, and ranked demo suggestions. It uses the same tasks."
                : "Simple path shows your tasks without extra filters. Your tasks are unchanged.");
        render();
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
                "Advanced details"
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
                    else showAdvancedDetails();
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

    private void showAdvancedDetails() {
        String version = "unknown";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) { }
        String details = "Daymark " + version + "\n\n"
                + "Tasks: schema v1, encrypted in an app-private file.\n\n"
                + "Encryption: AES-GCM; the key is stored in Android Keystore. Hardware protection depends on the device.\n\n"
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

    private StateListDrawable suggestionItemBackground() {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] { android.R.attr.state_pressed },
                shape(palette.accentSoft, 10, palette.accent));
        background.addState(new int[] { android.R.attr.state_focused },
                shape(palette.accentSoft, 10, palette.accent));
        background.addState(new int[0], shape(palette.surface, 10, palette.line));
        return background;
    }

    private StateListDrawable taskRowBackground(boolean highlighted) {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[] { android.R.attr.state_focused },
                shape(palette.accentSoft, 13, palette.accent));
        background.addState(new int[] { android.R.attr.state_pressed },
                shape(palette.accentSoft, 13, palette.accent));
        background.addState(new int[0], shape(highlighted ? palette.accentSoft : palette.surface,
                13, highlighted ? palette.accent : palette.line));
        return background;
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
