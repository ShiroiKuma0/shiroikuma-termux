package com.termux.shiroikuma.ui;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.activities.SettingsActivity;
import com.termux.shiroikuma.AppLanguage;
import com.termux.shiroikuma.ExternalAppsAccess;
import com.termux.shiroikuma.automation.AutomationAuth;
import com.termux.shiroikuma.automation.TrustedCallers;
import com.termux.shiroikuma.backup.ExportDir;
import com.termux.shiroikuma.backup.TermuxBackup;
import com.termux.shiroikuma.ui.TerminalStyleFiles.FontOption;
import com.termux.widget.TermuxWidgetProvider;
import com.termux.widget.utils.ShortcutUtils;
import com.termux.window.TermuxFloatService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.termux.shiroikuma.ui.ShiroikumaIcons.*;
import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux (Phase 4): the 白い熊 Termux UI page — wires every row of
 * {@code preferences_shiroikuma_ui.xml} (a port of arcanechat's {@code ShiroikumaUiPreferenceFragment}).
 *
 * <ul>
 * <li>Colour rows: swatch + {@code #AARRGGBB} at the right, tap → {@link ShiroikumaColorPicker} applying
 *     live on every slider tick, long-press → default.</li>
 * <li>Size rows: {@link ShiroikumaSeekBarPreference}, live, long-press → default.</li>
 * <li>Terminal: the three main colours are written to {@code ~/.termux/colors.properties} (picker
 *     with alpha disabled — the terminal accepts {@code #RRGGBB} only), the scheme picker writes a
 *     whole file, the font picker copies to {@code ~/.termux/font.ttf}, the font size goes through
 *     {@link TermuxAppSharedPreferences#setFontSize}.</li>
 * </ul>
 * Every change ends in {@link ShiroikumaStyle#changed}: upstream's reload broadcast, throttled.
 *
 * <p>Phase 4b — the first section: 「Export / Import…」 opens {@link ExportImportPanel}; 「Export
 * directory」 shows the absolute path (red "not set"; a red second line while All-files access is
 * missing, tap → the grant page; otherwise tap → the SAF tree picker, resolved by
 * {@link ExportDir}); 「Last export」 is queried on every resume on a background thread; then the
 * three automation rows of the contract §2 (master switch ON, 「Use authorization token?」 OFF,
 * the token row only while it is asked for).
 *
 * <p>External apps — the second section: {@code allow-external-apps} as a switch (written into the
 * properties file by {@link ExternalAppsAccess}, switching ON asks first), 「Warn at start while
 * off」 (the start-up dialog's 「Never show again」, inverted), and three status rows re-read on
 * every resume: the apps holding the RUN_COMMAND permission, battery optimisation and the overlay
 * grant (red while missing; a tap opens the grant).
 *
 * <p>Action rows (App settings, Language, Export / Import…, the two grants of External apps,
 * Refresh widgets, the dynamic shortcuts, Open floating terminal, the two resets) are
 * {@link ShiroikumaActionPreference}s — outlined pills.
 *
 * <p>The page is a folding accordion under a chip index (the family's UI-page rule): every
 * top-level section is a {@link ShiroikumaSectionCategory}, folded by default, whose heading folds
 * and unfolds its rows; {@link #SECTIONS} is the registry — id, icon, section-icon colour — that
 * heading and chip both read. The index ({@link ShiroikumaIndexPreference}) jumps on a tap and
 * reorders on a long-press drag; order and fold state live in {@link ShiroikumaPageState}. 「App
 * language」 is the first section of the default order ({@link AppLanguage}); 「This page」 and
 * 「Buttons &amp; switches」 hold the colour roles of the page itself, which repaint it live
 * ({@link #repaintPage}); Maintenance holds 「Reset section order」 and 「Reset all to defaults」.
 * Every row is drawn by {@link ShiroikumaRows} through {@link #onCreateAdapter}.
 *
 * <p>Phase 4c — the absorbed plugins: the Widget section (list colours / text size pushed to the
 * placed widgets' RemoteViews through {@link TermuxWidgetProvider#refreshAppWidgets}, debounced;
 * Refresh widgets; Create / Remove dynamic shortcuts) and the Floating terminal section (Open →
 * {@link TermuxFloatService}; frame colours, border, corner and the notification text restyle a
 * running window live through {@link TermuxFloatService#reloadStyle()}).
 */
public class ShiroikumaUiFragment extends PreferenceFragmentCompat implements ShiroikumaIndexPreference.Host {

    private static final String LOG_TAG = "ShiroikumaUiFragment";

    private static final String[] COLOR_KEYS = {
        EXTRAKEYS_BG, EXTRAKEYS_TEXT, EXTRAKEYS_ACTIVE_TEXT, EXTRAKEYS_ACTIVE_BG, EXTRAKEYS_BORDER_COLOR,
        DRAWER_BG, DRAWER_BUTTON_TEXT, DRAWER_ICON_TINT, SESSION_TEXT, SESSION_SELECTED_BG, SESSION_DEAD_TEXT,
        TOOLBAR_BG, TOOLBAR_TEXT, TOOLBAR_ICON, STATUSBAR_BG, NAVBAR_BG,
        DIALOG_BG, DIALOG_TEXT, DIALOG_TITLE, DIALOG_BUTTON, DIALOG_BORDER_COLOR,
        MENU_BG, MENU_TEXT, MENU_BORDER_COLOR,
    };
    /** The colour roles of the page itself: a change repaints the page ({@link #repaintPage}). */
    private static final String[] PAGE_COLOR_KEYS = {
        MENU_ICON_CONNECT, MENU_ICON_SHARE, MENU_ICON_CONTACT, MENU_ICON_BLOCK, MENU_DELETE,
        PILL_FRAME, PILL_TEXT, SWITCH_ON, SWITCH_OFF,
        PAGE_BG, PAGE_HEADING, PAGE_LABEL, PAGE_NOTE, PAGE_VALUE, PAGE_DIVIDER, PAGE_WARNING, PAGE_ICON,
        CHIP_FRAME, CHIP_TEXT, SWATCH_FRAME, SLIDER, SLIDER_TRACK,
        SECTION_BASE, SECTION_CONTROLS, SECTION_WORDS, SECTION_FRAME, SECTION_REACH, SECTION_WARNING, SECTION_FLASH,
    };
    /** Rows whose summary is a value (a path, a name, a list), not an explanation. */
    private static final String[] VALUE_ROW_KEYS = {
        "export_dir", "last_export", "automation_token", "external_apps_callers", "trusted_last_refusal",
        "terminal_scheme", "terminal_font", "float_notification_text",
    };

    /**
     * The section registry — id (the category's key without {@code section_}), icon, and which of
     * the seven section-icon colours the section takes by what it is: the base yellow (the app
     * and its settings), green (controls), light blue (words), heavy blue (the frame), orange
     * (the app reaches out), red (warning). Heading and chip both read this row. The DEFAULT
     * ORDER is the order of the XML, where no two neighbours share a colour.
     */
    private static final Object[][] SECTIONS = {
        {"language", ICON_GLOBE, SECTION_WORDS},
        {"eximport", ICON_EXPORT, SECTION_REACH},
        {"terminal", ICON_TERMINAL, SECTION_BASE},
        {"external_apps", ICON_PLUG, SECTION_REACH},
        {"extrakeys", ICON_KEYBOARD, SECTION_CONTROLS},
        {"drawer", ICON_MENU, SECTION_FRAME},
        {"dialogs", ICON_DIALOG, SECTION_REACH},
        {"toolbar", ICON_BAR, SECTION_FRAME},
        {"widget", ICON_GRID, SECTION_REACH},
        {"float", ICON_WINDOW, SECTION_BASE},
        // "buttons", never "controls": section_controls is the key of a colour role
        {"buttons", ICON_TOGGLE, SECTION_CONTROLS},
        {"page", ICON_PAGE, SECTION_BASE},
        {"maintenance", ICON_WARNING, SECTION_WARNING},
    };
    private static final String[] SIZE_KEYS = {
        EXTRAKEYS_TEXT_SIZE_SP, EXTRAKEYS_BORDER_DP, EXTRAKEYS_CORNER_DP, SESSION_TEXT_SIZE_SP,
        DIALOG_BORDER_DP, DIALOG_CORNER_DP, MENU_BORDER_DP, MENU_CORNER_DP,
    };
    /** Row key → colors.properties key. */
    private static final String[][] TERMINAL_COLOR_KEYS = {
        {"terminal_background", TerminalStyleFiles.KEY_BACKGROUND},
        {"terminal_foreground", TerminalStyleFiles.KEY_FOREGROUND},
        {"terminal_cursor", TerminalStyleFiles.KEY_CURSOR},
    };

    private final ActivityResultLauncher<String[]> mFontPickLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) onFontDocumentPicked(uri);
        });

    /** The Export / Import window and its two SAF pickers. */
    @Nullable private ExportImportPanel mPanel;
    private final ActivityResultLauncher<Uri> mDirPickLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocumentTree(), this::onExportDirPicked);
    private final ActivityResultLauncher<String[]> mImportPickLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null && mPanel != null && mPanel.isShowing()) mPanel.onImportFilePicked(uri);
        });

    private static final String ARG_SCROLL_TO = "scroll_to";

    /** {@code scrollTo} = key of the row or section to bring into view, or null. */
    @NonNull
    public static ShiroikumaUiFragment newInstance(@Nullable String scrollTo) {
        ShiroikumaUiFragment fragment = new ShiroikumaUiFragment();
        if (scrollTo != null) {
            Bundle args = new Bundle();
            args.putString(ARG_SCROLL_TO, scrollTo);
            fragment.setArguments(args);
        }
        return fragment;
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_shiroikuma_ui, rootKey);
        setUpSections();
        for (String key : VALUE_ROW_KEYS) {
            ShiroikumaPreference row = findPreference(key);
            if (row != null) row.setSummaryValue(true);
        }

        wireTop();
        wireExportImport();
        wireExternalApps();
        wireTerminal();
        for (String key : COLOR_KEYS) wireColor(key, null);
        for (String key : PAGE_COLOR_KEYS) wireColor(key, this::repaintPage);
        for (String key : SIZE_KEYS) wireSize(key, null);
        wireWidget();
        wireFloat();
        wireReset();
    }

    /** Every row of the page is drawn in one place: {@link ShiroikumaRows}. */
    @NonNull
    @Override
    protected RecyclerView.Adapter<?> onCreateAdapter(@NonNull PreferenceScreen preferenceScreen) {
        return new ShiroikumaRows.Adapter(preferenceScreen);
    }

    // ---- the accordion: sections, their order, the chip index ------------------------------------

    @Nullable private ShiroikumaIndexPreference mIndex;

    /** Gives every section its look and its stored fold state, adds the index, applies the order. */
    private void setUpSections() {
        Context ctx = requireContext();
        PreferenceScreen screen = getPreferenceScreen();
        for (Object[] row : SECTIONS) {
            ShiroikumaSectionCategory section = findPreference(ShiroikumaSectionCategory.KEY_PREFIX + row[0]);
            if (section == null) continue;
            section.look((Integer) row[1], (String) row[2]);
            section.setOpen(ShiroikumaPageState.isOpen(ctx, (String) row[0]), false);
        }
        mIndex = new ShiroikumaIndexPreference(ctx, this);
        screen.addPreference(mIndex);
        applyOrder();
    }

    @NonNull
    private static List<String> defaultOrder() {
        List<String> ids = new ArrayList<>();
        for (Object[] row : SECTIONS) ids.add((String) row[0]);
        return ids;
    }

    /** The App settings pill, the index, then the sections in their stored order. */
    private void applyOrder() {
        Context ctx = requireContext();
        Preference settings = findPreference("app_settings");
        if (settings != null) settings.setOrder(0);
        if (mIndex != null) mIndex.setOrder(1);
        int order = 10;
        for (String id : ShiroikumaPageState.order(ctx, defaultOrder())) {
            Preference section = findPreference(ShiroikumaSectionCategory.KEY_PREFIX + id);
            if (section != null) section.setOrder(order++);
        }
    }

    @NonNull
    @Override
    public List<ShiroikumaSectionCategory> sections() {
        List<ShiroikumaSectionCategory> out = new ArrayList<>();
        Context ctx = getContext();
        if (ctx == null) return out;
        for (String id : ShiroikumaPageState.order(ctx, defaultOrder())) {
            ShiroikumaSectionCategory section = findPreference(ShiroikumaSectionCategory.KEY_PREFIX + id);
            if (section != null) out.add(section);
        }
        return out;
    }

    /** A chip was tapped: unfold the section and scroll its heading to the top. */
    @Override
    public void jump(@NonNull final ShiroikumaSectionCategory section) {
        if (!section.isOpen()) section.setOpen(true, true);
        final RecyclerView list = getListView();
        if (list == null) return;
        // Posted: the adapter takes the unfolded rows in on its own posted sync first.
        list.post(() -> {
            RecyclerView.Adapter<?> adapter = list.getAdapter();
            RecyclerView.LayoutManager layout = list.getLayoutManager();
            if (!(adapter instanceof PreferenceGroup.PreferencePositionCallback) || layout == null) return;
            int position = ((PreferenceGroup.PreferencePositionCallback) adapter).getPreferenceAdapterPosition(section);
            if (position == RecyclerView.NO_POSITION) return;
            LinearSmoothScroller scroller = new LinearSmoothScroller(list.getContext()) {
                @Override
                protected int getVerticalSnapPreference() {
                    return SNAP_TO_START;
                }
            };
            scroller.setTargetPosition(position);
            layout.startSmoothScroll(scroller);
        });
    }

    /** The chips were dragged into a new order: the sections follow, fold state untouched. */
    @Override
    public void reorder(@NonNull List<String> order) {
        Context ctx = getContext();
        if (ctx == null) return;
        ShiroikumaPageState.setOrder(ctx, order);
        applyOrder();
    }

    /** A colour role of the page itself changed: ground, bars and every bound row again. */
    private void repaintPage() {
        Activity activity = getActivity();
        if (activity instanceof ShiroikumaUiActivity) ((ShiroikumaUiActivity) activity).applyPageColors();
        View view = getView();
        Context ctx = getContext();
        if (view != null && ctx != null) view.setBackgroundColor(color(ctx, PAGE_BG));
        RecyclerView list = getListView();
        if (list != null && list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
        // The menu icon and switch roles also draw the terminal's menus.
        if (ctx != null) ShiroikumaStyle.changed(ctx);
    }

    // ---- the top of the page and the small sections: App settings, App language, Maintenance -----

    private void wireTop() {
        final Context ctx = requireContext();

        ShiroikumaPreference settings = findPreference("app_settings");
        if (settings != null) settings.setOnPreferenceClickListener(p -> {
            startActivity(new Intent(ctx, SettingsActivity.class));
            return true;
        });

        ShiroikumaPreference language = findPreference("app_language");
        if (language != null) {
            language.setTitle(getString(R.string.shiroikuma_lang_row, AppLanguage.currentName(ctx)));
            language.setOnPreferenceClickListener(p -> {
                Activity activity = getActivity();
                // Choosing a language recreates the page; the row is then built again.
                if (activity != null) AppLanguage.showPicker(activity);
                return true;
            });
        }

        ShiroikumaPreference resetOrder = findPreference("reset_order");
        if (resetOrder != null) resetOrder.setOnPreferenceClickListener(p -> {
            ShiroikumaPageState.resetOrder(ctx);
            applyOrder();
            Toast.makeText(ctx, R.string.shiroikuma_page_reset_order_done, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshExportDirRow();
        refreshLastExportRow();
        refreshExternalAppsRows();
    }

    // ---- External apps (allow-external-apps and what the RUN_COMMAND door needs) ----------------

    private void wireExternalApps() {
        final Context ctx = requireContext();

        final SwitchPreferenceCompat allow = findPreference("allow_external_apps");
        if (allow != null) {
            allow.setOnPreferenceChangeListener((p, value) -> {
                if (!Boolean.TRUE.equals(value)) return writeAllowExternalApps(false);
                // Opening the door is confirmed: the switch moves only after 「Allow」.
                Activity activity = getActivity();
                if (activity == null) return false;
                ShiroikumaDialogs.confirmDialog(activity, getString(R.string.shiroikuma_ext_allow_confirm_title),
                    getString(R.string.shiroikuma_ext_allow_confirm_body), getString(R.string.shiroikuma_ext_allow_action), () -> {
                        if (writeAllowExternalApps(true)) allow.setChecked(true);
                    });
                return false;
            });
        }

        final SwitchPreferenceCompat warn = findPreference("external_apps_warn");
        if (warn != null) {
            warn.setChecked(ExternalAppsAccess.warnsAtStart(ctx));
            warn.setOnPreferenceChangeListener((p, value) -> {
                ExternalAppsAccess.setWarnsAtStart(ctx, Boolean.TRUE.equals(value));
                return true;
            });
        }

        final SwitchPreferenceCompat enforce = findPreference("trusted_enforce");
        if (enforce != null) {
            enforce.setChecked(TrustedCallers.isEnforced(ctx));
            enforce.setOnPreferenceChangeListener((p, value) -> {
                if (Boolean.TRUE.equals(value)) {
                    TrustedCallers.setEnforced(ctx, true);
                    return true;
                }
                // Opening the door to every holder of the permission is confirmed.
                Activity activity = getActivity();
                if (activity == null) return false;
                ShiroikumaDialogs.confirmDialog(activity, getString(R.string.shiroikuma_trusted_off_title),
                    getString(R.string.shiroikuma_trusted_off_body), getString(R.string.shiroikuma_trusted_off_action), () -> {
                        TrustedCallers.setEnforced(ctx, false);
                        enforce.setChecked(false);
                    });
                return false;
            });
        }

        // Two pills: the grant while it is missing, the system's own page of it once given.
        ShiroikumaPreference battery = findPreference("external_apps_battery");
        if (battery != null) battery.setOnPreferenceClickListener(p -> {
            Activity activity = getActivity();
            if (activity == null) return true;
            if (!PermissionUtils.checkIfBatteryOptimizationsDisabled(ctx)) PermissionUtils.requestDisableBatteryOptimizations(activity);
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) openSystemPage(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            return true;
        });

        ShiroikumaPreference overlay = findPreference("external_apps_overlay");
        if (overlay != null) overlay.setOnPreferenceClickListener(p -> {
            Activity activity = getActivity();
            if (activity == null) return true;
            if (!PermissionUtils.checkDisplayOverOtherAppsPermission(ctx)) PermissionUtils.requestDisplayOverOtherAppsPermission(activity);
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                openSystemPage(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.getPackageName())));
            return true;
        });
    }

    private void openSystemPage(@NonNull Intent intent) {
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot open " + intent.getAction(), e);
        }
    }

    private boolean writeAllowExternalApps(boolean allowed) {
        try {
            ExternalAppsAccess.setAllowed(allowed);
            return true;
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot write allow-external-apps", e);
            Context ctx = getContext();
            if (ctx != null) Toast.makeText(ctx, getString(R.string.shiroikuma_ext_write_failed,
                ExternalAppsAccess.propertiesFileLabel(), String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** On every resume: the file may have been edited by hand, a grant given on a system page. */
    private void refreshExternalAppsRows() {
        Context ctx = getContext();
        if (ctx == null) return;

        SwitchPreferenceCompat allow = findPreference("allow_external_apps");
        if (allow != null) {
            allow.setChecked(ExternalAppsAccess.isAllowed());
            allow.setSummary(getString(R.string.shiroikuma_ext_allow_desc, ExternalAppsAccess.propertiesFileLabel()));
        }

        ShiroikumaPreference callers = findPreference("external_apps_callers");
        if (callers != null) {
            java.util.List<String> labels = ExternalAppsAccess.permissionHolders(ctx);
            callers.setSummary(labels.isEmpty() ? red(getString(R.string.shiroikuma_ext_callers_none))
                : android.text.TextUtils.join("\n", labels));
        }

        refreshTrustedRows();

        ShiroikumaPreference battery = findPreference("external_apps_battery");
        if (battery != null) battery.setSummary(PermissionUtils.checkIfBatteryOptimizationsDisabled(ctx)
            ? getString(R.string.shiroikuma_ext_battery_ok) : red(getString(R.string.shiroikuma_ext_battery_bad)));

        ShiroikumaPreference overlay = findPreference("external_apps_overlay");
        if (overlay != null) overlay.setSummary(PermissionUtils.checkDisplayOverOtherAppsPermission(ctx)
            ? getString(R.string.shiroikuma_ext_overlay_ok) : red(getString(R.string.shiroikuma_ext_overlay_bad)));
    }

    // ---- Trusted callers: one block per app that requests RUN_COMMAND ---------------------------

    private final List<Preference> mTrustedRows = new java.util.ArrayList<>();

    /** Rebuilt on every resume: an app may have been installed, removed or re-signed meanwhile. */
    private void refreshTrustedRows() {
        final Context ctx = getContext();
        ShiroikumaSectionCategory section = findPreference(ShiroikumaSectionCategory.KEY_PREFIX + "external_apps");
        if (ctx == null || section == null) return;

        SwitchPreferenceCompat enforce = findPreference("trusted_enforce");
        if (enforce != null) enforce.setChecked(TrustedCallers.isEnforced(ctx));

        ShiroikumaPreference last = findPreference("trusted_last_refusal");
        if (last != null) {
            String line = TrustedCallers.lastRefusal(ctx);
            last.setSummary(line != null ? red(line) : getString(R.string.shiroikuma_trusted_last_none));
        }

        for (Preference row : mTrustedRows) section.removePreference(row);
        mTrustedRows.clear();

        List<String> packages = TrustedCallers.candidates(ctx);
        Preference none = findPreference("trusted_none");
        if (none != null) ShiroikumaSectionCategory.setWanted(none, packages.isEmpty());

        PackageManager pm = ctx.getPackageManager();
        for (final String pkg : packages) {
            CharSequence label = pkg;
            boolean installed = true;
            try {
                label = pm.getApplicationInfo(pkg, 0).loadLabel(pm);
            } catch (PackageManager.NameNotFoundException e) {
                installed = false;
            }
            ShiroikumaPreference app = new ShiroikumaPreference(ctx);
            app.setKey("trusted_app:" + pkg);
            app.setTitle(label);
            android.text.SpannableStringBuilder info = new android.text.SpannableStringBuilder(pkg);
            if (!installed) info.append("\n").append(red(getString(R.string.shiroikuma_trusted_not_installed)));
            else if (pm.checkPermission(TermuxConstants.PERMISSION_RUN_COMMAND, pkg) != PackageManager.PERMISSION_GRANTED)
                info.append("\n").append(red(getString(R.string.shiroikuma_trusted_permission_missing)));
            app.setSummary(info);
            addTrustedRow(section, app, false);
            addTrustedRow(section, trustedSwitch(ctx, pkg, label, TrustedCallers.ROLE_RUN, "trusted_run:", R.string.shiroikuma_trusted_run), true);
            addTrustedRow(section, trustedSwitch(ctx, pkg, label, TrustedCallers.ROLE_RESTORE, "trusted_restore:", R.string.shiroikuma_trusted_restore), true);
        }
    }

    private void addTrustedRow(@NonNull ShiroikumaSectionCategory section, @NonNull Preference row, boolean selectable) {
        row.setLayoutResource(R.layout.preference_shiroikuma_indent2);
        row.setPersistent(false);
        row.setSelectable(selectable);
        section.addPreference(row);
        mTrustedRows.add(row);
    }

    @NonNull
    private SwitchPreferenceCompat trustedSwitch(@NonNull final Context ctx, @NonNull final String pkg, @NonNull final CharSequence label,
                                                 @NonNull final String role, @NonNull String keyPrefix, int title) {
        final SwitchPreferenceCompat row = new SwitchPreferenceCompat(ctx);
        row.setKey(keyPrefix + pkg);
        row.setTitle(title);
        row.setChecked(TrustedCallers.isListed(ctx, pkg, role));
        if (TrustedCallers.certificateChanged(ctx, pkg, role)) row.setSummary(red(getString(R.string.shiroikuma_trusted_cert_changed)));
        row.setOnPreferenceChangeListener((p, value) -> {
            boolean on = Boolean.TRUE.equals(value);
            if (!TrustedCallers.setListed(ctx, pkg, role, on)) {
                Toast.makeText(ctx, getString(R.string.shiroikuma_trusted_cert_unreadable, label), Toast.LENGTH_LONG).show();
                return false;
            }
            row.setSummary(null); // switched on = the present certificate; switched off = nothing to warn of
            return true;
        });
        return row;
    }

    // ---- Export / Import (Phase 4b) --------------------------------------------------------------

    private void wireExportImport() {
        final Context ctx = requireContext();

        ShiroikumaPreference entry = findPreference("eximport");
        if (entry != null) {
            entry.setOnPreferenceClickListener(p -> {
                openPanel();
                return true;
            });
        }

        ShiroikumaPreference dir = findPreference("export_dir");
        if (dir != null) {
            dir.setOnPreferenceClickListener(p -> {
                Activity activity = getActivity();
                if (activity == null) return true;
                if (ExportDir.needsAllFilesGrant()) {
                    // The red line: the grant first, the picker on the next tap.
                    PermissionUtils.requestManageStorageExternalPermission(activity);
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !ExportDir.hasStorageAccess(ctx)
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PermissionUtils.requestLegacyStorageExternalPermission(activity, PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION);
                } else {
                    mDirPickLauncher.launch(ExportDir.uri(ctx));
                }
                return true;
            });
            // long-press: the picker regardless of the grant state
            dir.setOnLongClick(() -> mDirPickLauncher.launch(ExportDir.uri(ctx)));
        }

        final SwitchPreferenceCompat enabled = findPreference("automation_enabled");
        if (enabled != null) {
            enabled.setChecked(AutomationAuth.isEnabled(ctx));
            enabled.setOnPreferenceChangeListener((p, value) -> {
                AutomationAuth.setEnabled(ctx, Boolean.TRUE.equals(value));
                return true;
            });
        }

        final AutomationTokenPreference token = findPreference("automation_token");
        final SwitchPreferenceCompat requireToken = findPreference("automation_require_token");
        if (requireToken != null) {
            boolean required = AutomationAuth.isTokenRequired(ctx);
            requireToken.setChecked(required);
            if (token != null) ShiroikumaSectionCategory.setWanted(token, required);
            requireToken.setOnPreferenceChangeListener((p, value) -> {
                boolean now = Boolean.TRUE.equals(value);
                AutomationAuth.setTokenRequired(ctx, now);
                if (token != null) ShiroikumaSectionCategory.setWanted(token, now);
                return true;
            });
        }
        if (token != null) {
            refreshTokenRow(token);
            token.setOnPreferenceClickListener(p -> {
                ClipboardManager cb = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("automation_token", AutomationAuth.token(ctx)));
                Toast.makeText(ctx, R.string.shiroikuma_auto_token_copied, Toast.LENGTH_SHORT).show();
                return true;
            });
            token.setOnRegenerateListener(() -> {
                Activity activity = getActivity();
                if (activity == null) return;
                ShiroikumaDialogs.confirmDialog(activity, getString(R.string.shiroikuma_auto_token_regen_title),
                    getString(R.string.shiroikuma_auto_token_regen_msg), getString(R.string.shiroikuma_auto_regenerate), () -> {
                        AutomationAuth.regenerateToken(ctx);
                        refreshTokenRow(token);
                        Toast.makeText(ctx, R.string.shiroikuma_auto_token_regenerated, Toast.LENGTH_SHORT).show();
                    });
            });
        }
    }

    private void refreshTokenRow(@NonNull AutomationTokenPreference token) {
        Context ctx = getContext();
        if (ctx == null) return;
        token.setSummary(AutomationAuth.abbreviate(AutomationAuth.token(ctx)) + "\n" + getString(R.string.shiroikuma_auto_token_summary));
    }

    /** The path (in the value colour), or a red "not set"; a red second line while All-files access is missing. */
    private void refreshExportDirRow() {
        ShiroikumaPreference dir = findPreference("export_dir");
        Context ctx = getContext();
        if (dir == null || ctx == null) return;
        String path = ExportDir.path(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (path != null) {
            sb.append(path);
        } else {
            sb.append(red(getString(R.string.shiroikuma_eim_dir_unset)));
        }
        if (ExportDir.needsAllFilesGrant()) {
            sb.append('\n').append(red(getString(R.string.shiroikuma_eim_dir_grant)));
        }
        dir.setSummary(sb);
    }

    /** Queried on a background thread: the newest shiroikuma-termux_*.zip in the directory. */
    private void refreshLastExportRow() {
        final ShiroikumaPreference last = findPreference("last_export");
        final Context ctx = getContext();
        if (last == null || ctx == null) return;
        final File dir = ExportDir.dir(ctx);
        final Context app = ctx.getApplicationContext();
        // The strings are read here, from the page's own (localised) context, not on the thread.
        final String noDir = getString(R.string.shiroikuma_eim_warn_nodir);
        final String none = getString(R.string.shiroikuma_eim_warn_none);
        new Thread(() -> {
            final CharSequence value;
            if (dir == null) {
                value = red(noDir);
            } else {
                File newest = ExportDir.newestExport(dir);
                value = newest == null ? red(none)
                    : ExportImportPanel.formatTs(app, newest.lastModified()) + " · " + TermuxBackup.humanSize(newest.length());
            }
            View view = getView();
            if (view != null) view.post(() -> {
                if (isAdded()) last.setSummary(value);
            });
        }, "shiroikuma-last-export").start();
    }

    /** A warning in the 「Warnings」 colour of the page. */
    @NonNull
    private CharSequence red(@NonNull String text) {
        Context ctx = getContext();
        SpannableString s = new SpannableString(text);
        s.setSpan(new ForegroundColorSpan(ctx != null ? color(ctx, PAGE_WARNING) : RED), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private void openPanel() {
        final Activity activity = getActivity();
        if (activity == null) return;
        mPanel = new ExportImportPanel(activity, new ExportImportPanel.Host() {
            @Override
            public void pickExportDir(@Nullable Uri initial) {
                mDirPickLauncher.launch(initial);
            }

            @Override
            public void pickImportFile() {
                mImportPickLauncher.launch(new String[]{"application/zip", "application/octet-stream", "*/*"});
            }

            @Override
            public void onChainFinished() {
                // A finished export/import closes the whole chain: info dialog → panel → this page.
                activity.finish();
            }
        });
        mPanel.show();
    }

    private void onExportDirPicked(@Nullable Uri uri) {
        if (uri == null) return;
        Context ctx = getContext();
        if (ctx == null) return;
        if (mPanel != null && mPanel.isShowing()) {
            mPanel.onDirPicked(uri);
        } else if (!ExportDir.set(ctx, uri)) {
            Toast.makeText(ctx, R.string.shiroikuma_eim_dir_unresolvable, Toast.LENGTH_LONG).show();
        }
        refreshExportDirRow();
        refreshLastExportRow();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // no dividers between rows — the section lines are the only lines on the page
        setDivider(null);
        setDividerHeight(0);
        view.setBackgroundColor(color(view.getContext(), PAGE_BG));
        RecyclerView list = getListView();
        if (list != null) {
            // The edge glow would take the theme's colour, and the page's theme carries none.
            list.setEdgeEffectFactory(new RecyclerView.EdgeEffectFactory() {
                @NonNull
                @Override
                protected android.widget.EdgeEffect createEdgeEffect(@NonNull RecyclerView rv, int direction) {
                    android.widget.EdgeEffect effect = new android.widget.EdgeEffect(rv.getContext());
                    effect.setColor(color(rv.getContext(), PAGE_NOTE));
                    return effect;
                }
            });
            list.setVerticalScrollBarEnabled(false);
        }
        // Opened for one section (the external-apps dialog's 「Open settings」): unfold it and bring
        // it into view. A row key works too.
        Bundle args = getArguments();
        String scrollTo = args != null ? args.getString(ARG_SCROLL_TO) : null;
        if (scrollTo != null && savedInstanceState == null) {
            Preference target = findPreference(scrollTo);
            if (target instanceof ShiroikumaSectionCategory) {
                jump((ShiroikumaSectionCategory) target);
            } else if (target != null) {
                if (target.getParent() instanceof ShiroikumaSectionCategory) ((ShiroikumaSectionCategory) target.getParent()).setOpen(true, true);
                scrollToPreference(target);
            }
        }
    }

    // ---- house colour / size rows --------------------------------------------------------------

    /** {@code after} = what a change previews; null = the terminal reload ({@link ShiroikumaStyle}). */
    private void wireColor(@NonNull final String key, @Nullable Runnable after) {
        final ShiroikumaPreference pref = findPreference(key);
        if (pref == null) return;
        final Context ctx = requireContext();
        final Runnable changed = after != null ? after : () -> ShiroikumaStyle.changed(ctx);
        showColor(pref, color(ctx, key));
        pref.setOnPreferenceClickListener(p -> {
            Activity activity = getActivity();
            if (activity == null) return true;
            ShiroikumaColorPicker.show(activity, p.getTitle() != null ? p.getTitle() : ShiroikumaColorPicker.defaultTitle(ctx),
                color(ctx, key), true, argb -> {
                    setInt(ctx, key, argb);
                    showColor(pref, argb);
                    changed.run();
                });
            return true;
        });
        pref.setOnLongClick(() -> {
            remove(ctx, key);
            showColor(pref, color(ctx, key));
            changed.run();
        });
    }

    /** The hex at the right, beside the swatch — the summary stays the row's explanation. */
    private static void showColor(@NonNull ShiroikumaPreference pref, int argb) {
        pref.setValueText(hex(argb));
        pref.setColor(argb);
    }

    private void wireSize(@NonNull final String key, @Nullable Runnable after) {
        final ShiroikumaSeekBarPreference pref = findPreference(key);
        if (pref == null) return;
        final Context ctx = requireContext();
        final Runnable changed = after != null ? after : () -> ShiroikumaStyle.changed(ctx);
        pref.setValue(value(ctx, key));
        pref.setOnValueChanged(v -> {
            setInt(ctx, key, v);
            changed.run();
        });
        pref.setOnLongClick(() -> {
            remove(ctx, key);
            pref.setValue(value(ctx, key));
            changed.run();
        });
    }

    // ---- Widget (Phase 4c: the absorbed Termux:Widget) ------------------------------------------

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mWidgetRefresh = this::refreshWidgets;

    /** Every placed widget re-reads its colours and its file list; a no-op when none is placed. */
    private void refreshWidgets() {
        Context ctx = getContext();
        if (ctx == null) return;
        TermuxWidgetProvider.refreshAppWidgets(ctx, placedWidgetIds(ctx), true);
    }

    /** Slider ticks come many per second; the RemoteViews round-trip is not that cheap. */
    private void refreshWidgetsDebounced() {
        mHandler.removeCallbacks(mWidgetRefresh);
        mHandler.postDelayed(mWidgetRefresh, 150);
    }

    @NonNull
    private static int[] placedWidgetIds(@NonNull Context ctx) {
        int[] ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, TermuxWidgetProvider.class));
        return ids != null ? ids : new int[0];
    }

    private void wireWidget() {
        final Context ctx = requireContext();
        wireColor(WIDGET_BG, this::refreshWidgetsDebounced);
        wireColor(WIDGET_TEXT, this::refreshWidgetsDebounced);
        wireSize(WIDGET_TEXT_SIZE_SP, this::refreshWidgetsDebounced);

        ShiroikumaPreference refresh = findPreference("widget_refresh");
        if (refresh != null) refresh.setOnPreferenceClickListener(p -> {
            int[] ids = placedWidgetIds(ctx);
            if (ids.length == 0) {
                Toast.makeText(ctx, R.string.msg_no_widgets_found_to_refresh, Toast.LENGTH_SHORT).show();
            } else {
                TermuxWidgetProvider.refreshAppWidgets(ctx, ids, true);
                Toast.makeText(ctx, getString(R.string.msg_widgets_refreshed, Arrays.toString(ids)), Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        ShiroikumaPreference create = findPreference("widget_dynamic_create");
        if (create != null) create.setOnPreferenceClickListener(p -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) ShortcutUtils.createDynamicShortcuts(ctx);
            else Toast.makeText(ctx, R.string.shiroikuma_ui_widget_needs_api, Toast.LENGTH_SHORT).show();
            return true;
        });
        ShiroikumaPreference removeRow = findPreference("widget_dynamic_remove");
        if (removeRow != null) removeRow.setOnPreferenceClickListener(p -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) ShortcutUtils.removeDynamicShortcuts(ctx);
            else Toast.makeText(ctx, R.string.shiroikuma_ui_widget_needs_api, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    // ---- Floating terminal (Phase 4c: the absorbed Termux:Float) --------------------------------

    private void wireFloat() {
        final Context ctx = requireContext();
        final Runnable restyle = TermuxFloatService::reloadStyle;

        ShiroikumaPreference open = findPreference("float_open");
        if (open != null) open.setOnPreferenceClickListener(p -> {
            // The window is a TYPE_APPLICATION_OVERLAY: it comes up over this page, so the rows
            // below restyle it while it is in view. The overlay-permission prompt, if any, is
            // the service's own (TermuxFloatPermissionActivity).
            ctx.startService(new Intent(ctx, TermuxFloatService.class));
            return true;
        });

        wireColor(FLOAT_BG, restyle);
        wireColor(FLOAT_BORDER_COLOR, restyle);
        wireSize(FLOAT_BORDER_DP, restyle);
        wireSize(FLOAT_CORNER_DP, restyle);

        final ShiroikumaPreference text = findPreference("float_notification_text");
        if (text != null) {
            refreshFloatTextRow(text);
            text.setOnPreferenceClickListener(p -> {
                Activity activity = getActivity();
                if (activity == null) return true;
                ShiroikumaDialogs.inputDialog(activity, p.getTitle(), getString(R.string.shiroikuma_ui_float_notification_hint),
                    ShiroikumaTheme.getString(ctx, FLOAT_NOTIFICATION_TEXT), value -> {
                        setString(ctx, FLOAT_NOTIFICATION_TEXT, value.isEmpty() ? null : value);
                        refreshFloatTextRow(text);
                        restyle.run();
                    });
                return true;
            });
            text.setOnLongClick(() -> {
                setString(ctx, FLOAT_NOTIFICATION_TEXT, null);
                refreshFloatTextRow(text);
                restyle.run();
            });
        }
    }

    private void refreshFloatTextRow(@NonNull ShiroikumaPreference row) {
        Context ctx = getContext();
        if (ctx == null) return;
        String value = ShiroikumaTheme.getString(ctx, FLOAT_NOTIFICATION_TEXT);
        row.setSummary(value == null || value.isEmpty() ? getString(R.string.shiroikuma_ui_float_notification_default) : value);
    }

    // ---- terminal --------------------------------------------------------------------------------

    private void wireTerminal() {
        final Context ctx = requireContext();

        for (final String[] pair : TERMINAL_COLOR_KEYS) {
            final String rowKey = pair[0];
            final String fileKey = pair[1];
            final ShiroikumaPreference pref = findPreference(rowKey);
            if (pref == null) continue;
            showColor(pref, TerminalStyleFiles.currentColor(fileKey));
            pref.setOnPreferenceClickListener(p -> {
                Activity activity = getActivity();
                if (activity == null) return true;
                final int initial = TerminalStyleFiles.currentColor(fileKey);
                final String schemeBefore = ShiroikumaTheme.getString(ctx, TERMINAL_SCHEME);
                ShiroikumaColorPicker.show(activity, p.getTitle() != null ? p.getTitle() : ShiroikumaColorPicker.defaultTitle(ctx),
                    initial, false, argb -> {
                        writeTerminalColor(pref, fileKey, argb);
                        // Cancel (or dragging back) lands on the colour we started from: the named
                        // scheme still holds, so keep its name.
                        if (argb == initial && schemeBefore != null) {
                            setString(ctx, TERMINAL_SCHEME, schemeBefore);
                            refreshSchemeRow();
                        }
                    });
                return true;
            });
            pref.setOnLongClick(() -> writeTerminalColor(pref, fileKey, TerminalStyleFiles.houseColor(fileKey)));
        }

        final ShiroikumaPreference scheme = findPreference("terminal_scheme");
        if (scheme != null) {
            refreshSchemeRow();
            scheme.setOnPreferenceClickListener(p -> {
                Activity activity = getActivity();
                if (activity == null) return true;
                ShiroikumaSchemePicker.show(activity, s -> {
                    try {
                        TerminalStyleFiles.applyScheme(ctx, s);
                    } catch (IOException e) {
                        fail(e);
                        return;
                    }
                    refreshTerminalRows();
                    ShiroikumaStyle.changed(ctx);
                });
                return true;
            });
        }

        final ShiroikumaPreference font = findPreference("terminal_font");
        if (font != null) {
            font.setSummary(TerminalStyleFiles.currentFontLabel(ctx));
            font.setOnPreferenceClickListener(p -> {
                Activity activity = getActivity();
                if (activity == null) return true;
                ShiroikumaFontPicker.show(activity, new ShiroikumaFontPicker.Callback() {
                    @Override
                    public void onPick(@NonNull FontOption option) {
                        selectFont(option);
                    }

                    @Override
                    public void onAddFont() {
                        mFontPickLauncher.launch(new String[]{"*/*"});
                    }
                });
                return true;
            });
            font.setOnLongClick(() -> selectFont(TerminalStyleFiles.fontOptions(ctx).get(0)));
        }

        final ShiroikumaSeekBarPreference size = findPreference("terminal_font_size");
        final TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(ctx);
        if (size != null && prefs != null) {
            final int[] sizes = TermuxAppSharedPreferences.getDefaultFontSizes(ctx); // default, min, max
            size.setRange(sizes[1], sizes[2]);
            size.setValue(prefs.getFontSize());
            size.setOnValueChanged(v -> {
                prefs.setFontSize(v);
                refreshSample();
                ShiroikumaStyle.changed(ctx);
            });
            size.setOnLongClick(() -> {
                prefs.setFontSize(sizes[0]);
                size.setValue(sizes[0]);
                refreshSample();
                ShiroikumaStyle.changed(ctx);
            });
        }
    }

    private void writeTerminalColor(@NonNull ShiroikumaPreference pref, @NonNull String fileKey, int argb) {
        Context ctx = requireContext();
        try {
            TerminalStyleFiles.setColor(fileKey, argb);
        } catch (IOException e) {
            fail(e);
            return;
        }
        // a hand-edited colour is no longer the named scheme
        setString(ctx, TERMINAL_SCHEME, null);
        refreshSchemeRow();
        showColor(pref, TerminalStyleFiles.currentColor(fileKey));
        refreshSample();
        ShiroikumaStyle.changed(ctx);
    }

    private void selectFont(@NonNull FontOption option) {
        Context ctx = requireContext();
        try {
            TerminalStyleFiles.selectFont(ctx, option);
        } catch (IOException e) {
            fail(e);
            return;
        }
        ShiroikumaPreference font = findPreference("terminal_font");
        if (font != null) font.setSummary(TerminalStyleFiles.currentFontLabel(ctx));
        refreshSample();
        ShiroikumaStyle.changed(ctx);
    }

    private void onFontDocumentPicked(@NonNull Uri uri) {
        Context ctx = getContext();
        if (ctx == null) return;
        FontOption imported = TerminalStyleFiles.importFont(ctx, uri);
        if (imported == null) {
            Toast.makeText(ctx, R.string.shiroikuma_ui_terminal_font_unreadable, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(ctx, getString(R.string.shiroikuma_ui_terminal_font_added, imported.label), Toast.LENGTH_SHORT).show();
        selectFont(imported);
    }

    private void refreshSchemeRow() {
        ShiroikumaPreference scheme = findPreference("terminal_scheme");
        if (scheme == null) return;
        Context ctx = requireContext();
        String name = ShiroikumaTheme.getString(ctx, TERMINAL_SCHEME);
        if (name == null) scheme.setSummary(R.string.shiroikuma_ui_terminal_scheme_custom);
        else if (TerminalStyleFiles.HOUSE_SCHEME.equals(name)) scheme.setSummary(R.string.shiroikuma_ui_terminal_scheme_house);
        else scheme.setSummary(name);
    }

    private void refreshTerminalRows() {
        for (String[] pair : TERMINAL_COLOR_KEYS) {
            ShiroikumaPreference pref = findPreference(pair[0]);
            if (pref != null) showColor(pref, TerminalStyleFiles.currentColor(pair[1]));
        }
        refreshSchemeRow();
        refreshSample();
    }

    private void refreshSample() {
        TerminalSamplePreference sample = findPreference("terminal_sample");
        if (sample != null) sample.refresh();
    }

    private void fail(@NonNull Exception e) {
        Logger.logStackTraceWithMessage(LOG_TAG, "Terminal style file write failed", e);
        Context ctx = getContext();
        if (ctx != null) Toast.makeText(ctx, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
    }

    // ---- reset -----------------------------------------------------------------------------------

    private void wireReset() {
        ShiroikumaPreference reset = findPreference("reset_ui");
        if (reset == null) return;
        reset.setOnPreferenceClickListener(p -> {
            final Activity activity = getActivity();
            if (activity == null) return true;
            ShiroikumaDialogs.confirmDialog(activity, getString(R.string.shiroikuma_ui_reset_confirm_title),
                getString(R.string.shiroikuma_ui_reset_confirm_body), getString(R.string.shiroikuma_ui_reset_action),
                () -> resetAll(activity));
            return true;
        });
    }

    private void resetAll(@NonNull Activity activity) {
        Context ctx = activity.getApplicationContext();
        ShiroikumaTheme.reset(ctx);
        try {
            TerminalStyleFiles.writeHouseColors();
            setString(ctx, TERMINAL_SCHEME, TerminalStyleFiles.HOUSE_SCHEME);
            if (TermuxConstants.TERMUX_FONT_FILE.exists() && !TermuxConstants.TERMUX_FONT_FILE.delete())
                throw new IOException("cannot delete " + TermuxConstants.TERMUX_FONT_FILE);
        } catch (IOException e) {
            fail(e);
        }
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(ctx);
        if (prefs != null) prefs.setFontSize(TermuxAppSharedPreferences.getDefaultFontSizes(ctx)[0]);
        ShiroikumaStyle.changed(ctx);
        refreshWidgets();
        TermuxFloatService.reloadStyle();
        activity.recreate();
    }
}
