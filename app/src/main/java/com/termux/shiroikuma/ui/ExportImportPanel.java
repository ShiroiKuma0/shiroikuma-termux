package com.termux.shiroikuma.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.android.PermissionUtils;
import com.termux.shiroikuma.automation.AutomationDataService;
import com.termux.shiroikuma.automation.AutomationJobs;
import com.termux.shiroikuma.backup.ExportDir;
import com.termux.shiroikuma.backup.TermuxBackup;
import com.termux.shiroikuma.backup.TermuxBackup.Cat;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.termux.shiroikuma.ui.ShiroikumaDialogs.addPill;
import static com.termux.shiroikuma.ui.ShiroikumaDialogs.buttonRow;
import static com.termux.shiroikuma.ui.ShiroikumaDialogs.divider;
import static com.termux.shiroikuma.ui.ShiroikumaDialogs.dp;
import static com.termux.shiroikuma.ui.ShiroikumaDialogs.infoBox;
import static com.termux.shiroikuma.ui.ShiroikumaDialogs.panel;
import static com.termux.shiroikuma.ui.ShiroikumaDialogs.pill;
import static com.termux.shiroikuma.ui.ShiroikumaDialogs.text;
import static com.termux.shiroikuma.ui.ShiroikumaTheme.DIALOG_BG;
import static com.termux.shiroikuma.ui.ShiroikumaTheme.DIALOG_BORDER_COLOR;
import static com.termux.shiroikuma.ui.ShiroikumaTheme.DIALOG_BUTTON;
import static com.termux.shiroikuma.ui.ShiroikumaTheme.DIALOG_TEXT;
import static com.termux.shiroikuma.ui.ShiroikumaTheme.DIALOG_TITLE;
import static com.termux.shiroikuma.ui.ShiroikumaTheme.RED;
import static com.termux.shiroikuma.ui.ShiroikumaTheme.color;

/**
 * shiroikuma-termux (Phase 4b): the Export / Import window — a port of raikidoban's
 * {@code ExportImportPanel} in the family's shared shape: one bordered rounded black box with a
 * centred title, a dim description, a tappable bordered directory box (red when unset, yellow once
 * set), the last-backup line, a divider, 全選択 + the category checkboxes (the two data parts
 * indented under Data and following its toggle), a divider, and the pill row — Cancel alone on
 * the left, Import then Export on the right. While a run is in flight a progress line
 * ("Home 1234/8942 · 512 MB / 4.2 GB") sits above the pills and Cancel stops the job.
 *
 * <p>All work goes through {@link TermuxBackup} inside the foreground
 * {@link AutomationDataService} — the same runner the automation doors use — and this panel
 * follows its own job through an {@link AutomationJobs.Listener}. A successful export or import
 * ends with a bordered info dialog whose acknowledgement closes the whole chain (info dialog →
 * this panel → the UI page, via {@link Host#onChainFinished()}); failures leave the panel open.
 */
public class ExportImportPanel {

    /** What the hosting fragment provides: the two SAF pickers and the "close everything" hook. */
    public interface Host {
        void pickExportDir(@Nullable Uri initial);

        void pickImportFile();

        void onChainFinished();
    }

    private final Activity mActivity;
    private final Host mHost;
    private final Set<Cat> mSelected = new LinkedHashSet<>(Cat.defaults());

    private AlertDialog mDialog;
    private LinearLayout mBox;
    private TextView mProgressLine;
    private Button mCancelPill;
    private Button mImportPill;
    private Button mExportPill;
    @Nullable private String mJobId;

    public ExportImportPanel(@NonNull Activity activity, @NonNull Host host) {
        mActivity = activity;
        mHost = host;
    }

    public boolean isShowing() {
        return mDialog != null && mDialog.isShowing();
    }

    public void show() {
        mBox = new LinearLayout(mActivity);
        mBox.setOrientation(LinearLayout.VERTICAL);
        mBox.setPadding(dp(mActivity, 20), dp(mActivity, 16), dp(mActivity, 20), dp(mActivity, 20));
        mBox.setBackground(ShiroikumaDialogs.panelBackground(mActivity));
        mDialog = panel(mActivity, mBox, true);
        mDialog.setOnDismissListener(d -> {
            if (mJobId != null) AutomationJobs.detach(mJobId);
        });
        mDialog.show();
        rebuild();
    }

    public void dismiss() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    // ---- content -------------------------------------------------------------------------------

    public void rebuild() {
        if (mBox == null) return;
        mBox.removeAllViews();
        Context ctx = mActivity;

        TextView title = text(ctx, mActivity.getString(R.string.shiroikuma_eim_title), 18, color(ctx, DIALOG_TITLE), true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(ctx, 2), 0, dp(ctx, 6));
        mBox.addView(title);

        TextView desc = text(ctx, mActivity.getString(R.string.shiroikuma_eim_desc), 13, color(ctx, DIALOG_TEXT), false);
        desc.setAlpha(0.85f);
        desc.setPadding(0, 0, 0, dp(ctx, 10));
        mBox.addView(desc);

        mBox.addView(dirBox());
        mBox.addView(statusLine());
        mBox.addView(divider(ctx, 0));

        final CheckBox selectAll = checkbox(mActivity.getString(R.string.shiroikuma_eim_select_all), true, 0);
        selectAll.setChecked(mSelected.size() == Cat.values().length);
        selectAll.setOnClickListener(v -> {
            if (selectAll.isChecked()) mSelected.addAll(Cat.all());
            else mSelected.clear();
            rebuild();
        });
        mBox.addView(selectAll);
        for (Cat cat : Cat.values()) mBox.addView(categoryRow(cat));

        mBox.addView(divider(ctx, 8));

        mProgressLine = text(ctx, "", 13, color(ctx, DIALOG_TEXT), false);
        mProgressLine.setAlpha(0.85f);
        mProgressLine.setPadding(dp(ctx, 2), dp(ctx, 10), 0, 0);
        mProgressLine.setVisibility(View.GONE);
        mBox.addView(mProgressLine);

        mBox.addView(pillRow());
        setRunning(mJobId != null, null);
    }

    /** The folder box: bordered, clearly tappable — small label over the value, red when unset. */
    private View dirBox() {
        Context ctx = mActivity;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClickable(true);
        box.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(ctx, DIALOG_BG));
        bg.setStroke(Math.max(1, dp(ctx, 2)), color(ctx, DIALOG_BORDER_COLOR));
        bg.setCornerRadius(dp(ctx, 10));
        box.setBackground(bg);
        box.setOnClickListener(v -> mHost.pickExportDir(ExportDir.uri(ctx)));

        box.addView(text(ctx, mActivity.getString(R.string.shiroikuma_eim_dir), 12, color(ctx, DIALOG_TITLE), false));
        String path = ExportDir.path(ctx);
        box.addView(text(ctx, path != null ? path : mActivity.getString(R.string.shiroikuma_eim_dir_unset), 15,
            path != null ? color(ctx, DIALOG_TEXT) : RED, true));
        if (ExportDir.needsAllFilesGrant()) {
            TextView hint = text(ctx, mActivity.getString(R.string.shiroikuma_eim_dir_grant), 12, RED, false);
            hint.setPadding(0, dp(ctx, 2), 0, 0);
            hint.setClickable(true);
            hint.setOnClickListener(v -> requestStorage());
            box.addView(hint);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 6);
        lp.bottomMargin = dp(ctx, 6);
        box.setLayoutParams(lp);
        return box;
    }

    private View statusLine() {
        Context ctx = mActivity;
        String msg;
        boolean warn;
        File dir = ExportDir.dir(ctx);
        if (dir == null) {
            msg = mActivity.getString(R.string.shiroikuma_eim_warn_nodir);
            warn = true;
        } else {
            File newest = ExportDir.newestExport(dir);
            if (newest == null) {
                msg = mActivity.getString(R.string.shiroikuma_eim_warn_none);
                warn = true;
            } else {
                msg = mActivity.getString(R.string.shiroikuma_eim_last, formatTs(ctx, newest.lastModified()), TermuxBackup.humanSize(newest.length()));
                warn = false;
            }
        }
        TextView tv = text(ctx, msg, 14, warn ? RED : color(ctx, DIALOG_TEXT), false);
        tv.setAlpha(warn ? 1f : 0.8f);
        tv.setPadding(dp(ctx, 2), 0, 0, dp(ctx, 8));
        return tv;
    }

    /** {@code 2026-09-13 10:24 · 4.2 GB} pieces: date + time in the device's formats. */
    @NonNull
    public static String formatTs(@NonNull Context ctx, long ts) {
        return DateFormat.getDateFormat(ctx).format(ts) + " " + DateFormat.getTimeFormat(ctx).format(ts);
    }

    private View categoryRow(final Cat cat) {
        boolean isChild = cat.isChild();
        CheckBox cb = checkbox(mActivity.getString(cat.labelRes), false, isChild ? dp(mActivity, 28) : 0);
        boolean parentOn = !isChild || mSelected.contains(Cat.byId(cat.parentId));
        cb.setChecked(mSelected.contains(cat) && parentOn);
        cb.setEnabled(parentOn);
        cb.setAlpha(parentOn ? 1f : 0.5f);
        cb.setOnClickListener(v -> {
            boolean checked = cb.isChecked();
            if (checked) mSelected.add(cat);
            else mSelected.remove(cat);
            // A parent drags its children along, so turning a group off never leaves orphans on.
            boolean hasChildren = false;
            for (Cat other : Cat.values()) {
                if (cat.id.equals(other.parentId)) {
                    hasChildren = true;
                    if (checked) mSelected.add(other);
                    else mSelected.remove(other);
                }
            }
            if (hasChildren) rebuild();
        });
        return cb;
    }

    /** Cancel alone on the left, Import + Export grouped on the right. */
    private View pillRow() {
        Context ctx = mActivity;
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(ctx, 14), 0, 0);
        mCancelPill = pill(ctx, mActivity.getString(android.R.string.cancel), v -> {
            if (mJobId != null) {
                AutomationJobs.cancel(mJobId);
                setProgressText(mActivity.getString(R.string.shiroikuma_eim_cancelling));
            } else {
                dismiss();
            }
        });
        row.addView(mCancelPill);
        View spacer = new View(ctx);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
        mImportPill = pill(ctx, mActivity.getString(R.string.shiroikuma_eim_import), v -> onImportClicked());
        ((LinearLayout.LayoutParams) mImportPill.getLayoutParams()).rightMargin = dp(ctx, 8);
        row.addView(mImportPill);
        mExportPill = pill(ctx, mActivity.getString(R.string.shiroikuma_eim_export), v -> onExportClicked());
        row.addView(mExportPill);
        return row;
    }

    private void setRunning(boolean running, @Nullable String text) {
        if (mImportPill != null) {
            mImportPill.setEnabled(!running);
            mImportPill.setAlpha(running ? 0.4f : 1f);
        }
        if (mExportPill != null) {
            mExportPill.setEnabled(!running);
            mExportPill.setAlpha(running ? 0.4f : 1f);
        }
        if (mProgressLine != null) mProgressLine.setVisibility(running ? View.VISIBLE : View.GONE);
        if (text != null) setProgressText(text);
        if (mDialog != null) {
            mDialog.setCancelable(!running);
            mDialog.setCanceledOnTouchOutside(!running);
            if (mDialog.getWindow() != null) {
                if (running) mDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else mDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }

    private void setProgressText(@NonNull String text) {
        if (mProgressLine != null) mProgressLine.setText(text);
    }

    private boolean dataSelected() {
        return mSelected.contains(Cat.DATA) || mSelected.contains(Cat.DATA_HOME) || mSelected.contains(Cat.DATA_USR);
    }

    @NonNull
    private String itemsOf(@NonNull Set<Cat> cats) {
        StringBuilder sb = new StringBuilder();
        for (Cat c : cats) {
            if (sb.length() > 0) sb.append(',');
            sb.append(c.id);
        }
        return sb.toString();
    }

    private void requestStorage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            PermissionUtils.requestManageStorageExternalPermission(mActivity);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PermissionUtils.requestLegacyStorageExternalPermission(mActivity, PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION);
        }
    }

    // ---- export --------------------------------------------------------------------------------

    private void onExportClicked() {
        if (mSelected.isEmpty()) {
            showInfo(mActivity.getString(R.string.shiroikuma_eim_export_fail_title), mActivity.getString(R.string.shiroikuma_eim_none_selected), false);
            return;
        }
        File dir = ExportDir.dir(mActivity);
        if (dir == null) {
            mHost.pickExportDir(null); // no folder yet: ask for one instead of failing
            return;
        }
        if (!ExportDir.hasStorageAccess(mActivity)) {
            requestStorage();
            return;
        }
        if (dataSelected() && !PermissionUtils.checkIfBatteryOptimizationsDisabled(mActivity)) {
            // A tar of usr takes minutes; EMUI starves a non-exempt app part-way through.
            LinearLayout box = infoBox(mActivity, mActivity.getString(R.string.shiroikuma_eim_battery_title),
                mActivity.getString(R.string.shiroikuma_eim_battery_body));
            final AlertDialog dialog = panel(mActivity, box, true);
            LinearLayout buttons = buttonRow(mActivity);
            addPill(buttons, pill(mActivity, mActivity.getString(R.string.shiroikuma_eim_battery_anyway), v -> {
                dialog.dismiss();
                startExport(dir);
            }));
            addPill(buttons, pill(mActivity, mActivity.getString(R.string.shiroikuma_eim_battery_settings), v -> {
                dialog.dismiss();
                PermissionUtils.requestDisableBatteryOptimizations(mActivity);
            }));
            box.addView(buttons);
            dialog.show();
            return;
        }
        startExport(dir);
    }

    private void startExport(@NonNull File dir) {
        final Set<Cat> cats = new LinkedHashSet<>(mSelected);
        final String jobId = AutomationJobs.begin("");
        mJobId = jobId;
        AutomationJobs.attach(jobId, new AutomationJobs.Listener() {
            @Override
            public void onProgress(@NonNull String text) {
                setProgressText(text);
            }

            @Override
            public void onResult(@NonNull String result, @Nullable List<String> lines) {
                mJobId = null;
                setRunning(false, null);
                if (result.startsWith("OK:")) {
                    String[] parts = result.substring(3).split("\\|");
                    String path = parts.length > 0 ? parts[0] : "";
                    String human = parts.length > 2 ? parts[2] : "";
                    String body = mActivity.getString(R.string.shiroikuma_eim_export_done_body, path, human)
                        + (lines != null && !lines.isEmpty() ? "\n\n" + join(lines) : "");
                    showInfo(mActivity.getString(R.string.shiroikuma_eim_export_done_title), body, true);
                } else if ("ERROR:cancelled".equals(result)) {
                    showInfo(mActivity.getString(R.string.shiroikuma_eim_export_fail_title), mActivity.getString(R.string.shiroikuma_eim_cancelled), false);
                } else {
                    showInfo(mActivity.getString(R.string.shiroikuma_eim_export_fail_title),
                        mActivity.getString(R.string.shiroikuma_eim_export_fail, reason(result)), false);
                }
                rebuild();
            }
        });
        setRunning(true, mActivity.getString(R.string.shiroikuma_eim_exporting));
        try {
            AutomationDataService.startFileExport(mActivity, jobId, dir, itemsOf(cats), null, null, "", null);
        } catch (Throwable t) {
            AutomationJobs.finish(jobId);
            mJobId = null;
            setRunning(false, null);
            showInfo(mActivity.getString(R.string.shiroikuma_eim_export_fail_title),
                mActivity.getString(R.string.shiroikuma_eim_export_fail, String.valueOf(t.getMessage())), false);
        }
    }

    // ---- import --------------------------------------------------------------------------------

    private void onImportClicked() {
        if (mSelected.isEmpty()) {
            showInfo(mActivity.getString(R.string.shiroikuma_eim_import_fail_title), mActivity.getString(R.string.shiroikuma_eim_none_selected), false);
            return;
        }
        final List<File> backups = ExportDir.listExports(ExportDir.dir(mActivity));
        Context ctx = mActivity;
        LinearLayout box = ShiroikumaDialogs.box(ctx);
        box.addView(text(ctx, mActivity.getString(R.string.shiroikuma_eim_pick_backup), 19, color(ctx, DIALOG_TITLE), true));
        final AlertDialog dialog = panel(mActivity, box, true);
        List<String> labels = new ArrayList<>();
        for (File f : backups) labels.add(f.getName());
        labels.add(mActivity.getString(R.string.shiroikuma_eim_browse));
        for (int i = 0; i < labels.size(); i++) {
            final int which = i;
            TextView row = text(ctx, labels.get(i), 16, color(ctx, DIALOG_TEXT), false);
            row.setPadding(dp(ctx, 4), dp(ctx, 12), dp(ctx, 4), dp(ctx, 12));
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setClickable(true);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (which >= backups.size()) mHost.pickImportFile();
                else confirmThenImport(backups.get(which), null);
            });
            box.addView(row);
        }
        LinearLayout buttons = buttonRow(ctx);
        addPill(buttons, pill(ctx, mActivity.getString(android.R.string.cancel), v -> dialog.dismiss()));
        box.addView(buttons);
        dialog.show();
    }

    /** Called by the host after the SAF file picker returns. */
    public void onImportFilePicked(@Nullable Uri uri) {
        if (uri != null) confirmThenImport(null, uri);
    }

    private void confirmThenImport(@Nullable File file, @Nullable Uri uri) {
        if (dataSelected()) {
            ShiroikumaDialogs.confirmDialog(mActivity, mActivity.getString(R.string.shiroikuma_eim_restore_confirm_title),
                mActivity.getString(R.string.shiroikuma_eim_restore_confirm_body), mActivity.getString(R.string.shiroikuma_eim_import),
                () -> runImport(file, uri));
        } else {
            runImport(file, uri);
        }
    }

    private void runImport(@Nullable File file, @Nullable Uri uri) {
        ParcelFileDescriptor pfd;
        try {
            if (file != null) pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            else pfd = mActivity.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) throw new java.io.IOException("cannot open the backup");
        } catch (Exception e) {
            showInfo(mActivity.getString(R.string.shiroikuma_eim_import_fail_title),
                mActivity.getString(R.string.shiroikuma_eim_import_fail, String.valueOf(e.getMessage())), false);
            return;
        }
        final Set<Cat> cats = new LinkedHashSet<>(mSelected);
        final String jobId = AutomationJobs.begin("");
        mJobId = jobId;
        AutomationJobs.attach(jobId, new AutomationJobs.Listener() {
            @Override
            public void onProgress(@NonNull String text) {
                setProgressText(text);
            }

            @Override
            public void onResult(@NonNull String result, @Nullable List<String> lines) {
                mJobId = null;
                setRunning(false, null);
                if (result.startsWith("OK:")) {
                    showImportResult(lines != null && !lines.isEmpty() ? join(lines) : result.substring(3));
                } else if ("ERROR:cancelled".equals(result)) {
                    showInfo(mActivity.getString(R.string.shiroikuma_eim_import_fail_title), mActivity.getString(R.string.shiroikuma_eim_cancelled), false);
                } else {
                    showInfo(mActivity.getString(R.string.shiroikuma_eim_import_fail_title),
                        mActivity.getString(R.string.shiroikuma_eim_import_fail, reason(result)), false);
                }
                rebuild();
            }
        });
        setRunning(true, mActivity.getString(R.string.shiroikuma_eim_importing));
        try {
            AutomationDataService.startDescriptor(mActivity, jobId, pfd, true, itemsOf(cats), null, null, null);
        } catch (Throwable t) {
            AutomationJobs.finish(jobId);
            mJobId = null;
            setRunning(false, null);
            showInfo(mActivity.getString(R.string.shiroikuma_eim_import_fail_title),
                mActivity.getString(R.string.shiroikuma_eim_import_fail, String.valueOf(t.getMessage())), false);
        }
    }

    /**
     * The import result: a persistent bordered dialog with an explicit restart button. Both pills
     * close the whole chain; "Restart now" additionally relaunches the process so the restored
     * prefix and settings are what the terminal starts from.
     */
    private void showImportResult(@NonNull String summary) {
        String body = summary + "\n\n" + mActivity.getString(R.string.shiroikuma_eim_restart_hint);
        LinearLayout box = infoBox(mActivity, mActivity.getString(R.string.shiroikuma_eim_import_done_title), body);
        final AlertDialog dialog = panel(mActivity, box, false);
        LinearLayout buttons = buttonRow(mActivity);
        addPill(buttons, pill(mActivity, mActivity.getString(R.string.shiroikuma_eim_restart_later), v -> {
            dialog.dismiss();
            dismiss();
            mHost.onChainFinished();
        }));
        addPill(buttons, pill(mActivity, mActivity.getString(R.string.shiroikuma_eim_restart_now), v -> restartApp()));
        box.addView(buttons);
        dialog.show();
    }

    private void restartApp() {
        Intent launch = Intent.makeRestartActivityTask(new ComponentName(mActivity, TermuxActivity.class));
        mActivity.startActivity(launch);
        Runtime.getRuntime().exit(0);
    }

    // ---- the export directory ------------------------------------------------------------------

    /** Called by the host after the SAF folder picker returns. */
    public void onDirPicked(@Nullable Uri uri) {
        if (uri == null) return;
        if (!ExportDir.set(mActivity, uri)) {
            showInfo(mActivity.getString(R.string.shiroikuma_eim_dir), mActivity.getString(R.string.shiroikuma_eim_dir_unresolvable), false);
        }
        rebuild();
    }

    // ---- info dialogs --------------------------------------------------------------------------

    /**
     * A bordered info dialog with a single OK. When {@code closeChain} is set (a successful
     * export), acknowledging it closes this panel and the UI page too; failures only dismiss the
     * dialog, leaving the panel open to retry.
     */
    private void showInfo(@NonNull String title, @NonNull String body, final boolean closeChain) {
        ShiroikumaDialogs.infoDialog(mActivity, title, body, !closeChain, closeChain ? () -> {
            dismiss();
            mHost.onChainFinished();
        } : null);
    }

    @NonNull
    private static String reason(@NonNull String result) {
        return result.startsWith("ERROR:") ? result.substring(6) : result;
    }

    @NonNull
    private static String join(@NonNull List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(l);
        }
        return sb.toString();
    }

    // ---- view builders -------------------------------------------------------------------------

    private CheckBox checkbox(@NonNull String label, boolean bold, int indent) {
        Context ctx = mActivity;
        CheckBox cb = new CheckBox(ctx);
        cb.setText(label);
        cb.setTextColor(color(ctx, DIALOG_TEXT));
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        if (bold) cb.setTypeface(cb.getTypeface(), Typeface.BOLD);
        cb.setButtonTintList(ColorStateList.valueOf(color(ctx, DIALOG_BUTTON)));
        cb.setPadding(dp(ctx, 8) + indent, dp(ctx, 7), 0, dp(ctx, 7));
        return cb;
    }
}
