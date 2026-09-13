package com.termux.shiroikuma.ui;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shiroikuma.automation.AutomationAuth;
import com.termux.shiroikuma.backup.ExportDir;
import com.termux.shiroikuma.backup.TermuxBackup;
import com.termux.shiroikuma.ui.TerminalStyleFiles.FontOption;
import com.termux.widget.TermuxWidgetProvider;
import com.termux.widget.utils.ShortcutUtils;
import com.termux.window.TermuxFloatService;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux (Phase 4): the 白い熊 Termux UI page — wires every row of
 * {@code preferences_shiroikuma_ui.xml} (a port of arcanechat's {@code ShiroikumaUiPreferenceFragment}).
 *
 * <ul>
 * <li>Colour rows: swatch + {@code #AARRGGBB} summary, tap → {@link ShiroikumaColorPicker} applying
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
 * <p>Phase 4c — the absorbed plugins: the Widget section (list colours / text size pushed to the
 * placed widgets' RemoteViews through {@link TermuxWidgetProvider#refreshAppWidgets}, debounced;
 * Refresh widgets; Create / Remove dynamic shortcuts) and the Floating terminal section (Open →
 * {@link TermuxFloatService}; frame colours, border, corner and the notification text restyle a
 * running window live through {@link TermuxFloatService#reloadStyle()}).
 */
public class ShiroikumaUiFragment extends PreferenceFragmentCompat {

    private static final String LOG_TAG = "ShiroikumaUiFragment";

    private static final String[] COLOR_KEYS = {
        EXTRAKEYS_BG, EXTRAKEYS_TEXT, EXTRAKEYS_ACTIVE_TEXT, EXTRAKEYS_ACTIVE_BG, EXTRAKEYS_BORDER_COLOR,
        DRAWER_BG, DRAWER_BUTTON_TEXT, DRAWER_ICON_TINT, SESSION_TEXT, SESSION_SELECTED_BG, SESSION_DEAD_TEXT,
        TOOLBAR_BG, TOOLBAR_TEXT, TOOLBAR_ICON, STATUSBAR_BG, NAVBAR_BG,
        DIALOG_BG, DIALOG_TEXT, DIALOG_TITLE, DIALOG_BUTTON, DIALOG_BORDER_COLOR,
        MENU_BG, MENU_TEXT, MENU_BORDER_COLOR,
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

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_shiroikuma_ui, rootKey);

        wireExportImport();
        wireTerminal();
        for (String key : COLOR_KEYS) wireColor(key, null);
        for (String key : SIZE_KEYS) wireSize(key, null);
        wireWidget();
        wireFloat();
        wireReset();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshExportDirRow();
        refreshLastExportRow();
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
            if (token != null) token.setVisible(required);
            requireToken.setOnPreferenceChangeListener((p, value) -> {
                boolean now = Boolean.TRUE.equals(value);
                AutomationAuth.setTokenRequired(ctx, now);
                if (token != null) token.setVisible(now);
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

    /** The path in yellow, or a red "not set"; a red second line while All-files access is missing. */
    private void refreshExportDirRow() {
        ShiroikumaPreference dir = findPreference("export_dir");
        Context ctx = getContext();
        if (dir == null || ctx == null) return;
        String path = ExportDir.path(ctx);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (path != null) {
            SpannableString s = new SpannableString(path);
            s.setSpan(new ForegroundColorSpan(YELLOW), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(s);
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
        new Thread(() -> {
            final CharSequence value;
            if (dir == null) {
                value = red(app.getString(R.string.shiroikuma_eim_warn_nodir));
            } else {
                File newest = ExportDir.newestExport(dir);
                if (newest == null) {
                    value = red(app.getString(R.string.shiroikuma_eim_warn_none));
                } else {
                    SpannableString s = new SpannableString(ExportImportPanel.formatTs(app, newest.lastModified())
                        + " · " + TermuxBackup.humanSize(newest.length()));
                    s.setSpan(new ForegroundColorSpan(YELLOW), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    value = s;
                }
            }
            View view = getView();
            if (view != null) view.post(() -> {
                if (isAdded()) last.setSummary(value);
            });
        }, "shiroikuma-last-export").start();
    }

    @NonNull
    private static CharSequence red(@NonNull String text) {
        SpannableString s = new SpannableString(text);
        s.setSpan(new ForegroundColorSpan(RED), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        // no dividers between rows — the section hairlines are the only lines on the page
        setDivider(null);
        setDividerHeight(0);
        view.setBackgroundColor(BLACK);
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

    private static void showColor(@NonNull ShiroikumaPreference pref, int argb) {
        pref.setSummary(hex(argb));
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
