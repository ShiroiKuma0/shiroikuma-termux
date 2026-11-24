package com.termux.shiroikuma;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shiroikuma.ui.ShiroikumaDialogs;
import com.termux.shiroikuma.ui.ShiroikumaUiActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * shiroikuma-termux: the {@code allow-external-apps} property in the app instead of only in a text
 * editor — the door 白い熊 自由作業盤 knocks on ({@code RunCommandService}, upstream's and untouched)
 * answers only while it is {@code true}, and until now nothing said so when it was not.
 *
 * <ul>
 * <li>{@link #isAllowed}: re-reads the properties file and asks upstream's own
 *     {@code shouldAllowExternalApps()} — the very value {@code RunCommandService} tests.</li>
 * <li>{@link #setAllowed}: rewrites the one line in the properties file upstream reads (the first
 *     existing of {@code ~/.termux/termux.properties}, {@code ~/.config/termux/termux.properties};
 *     the primary one when neither exists) — an existing line is replaced, a commented-out one is
 *     revived, otherwise the line is appended; everything else in the file stays byte for byte.
 *     Written through the canonical path, so a symlinked properties file keeps its link. Then the
 *     process-wide properties are reloaded: the door opens or closes at once.</li>
 * <li>{@link #maybeWarn}: once per process, on the terminal's first resume — a dialog when the
 *     property is off: 「Open settings」 (the UI page, scrolled to its External apps section),
 *     Cancel, and a 「Never show again」 box (the 「Warn at start while off」 switch of that
 *     section, so it can be switched back on). Silent before the bootstrap is installed.</li>
 * </ul>
 * The warning flag lives in the device-local {@code shiroikuma_external_apps} file; it is not part
 * of an export, and a restore never switches the property on (TermuxBackup's tighten-only rule).
 */
public final class ExternalAppsAccess {

    private static final String LOG_TAG = "ExternalAppsAccess";

    public static final String PREFS = "shiroikuma_external_apps";
    private static final String KEY_WARN_AT_START = "warn_at_start";

    /** The UI page's section; {@link ShiroikumaUiActivity#open(Context, String)} scrolls to it. */
    public static final String SECTION_KEY = "section_external_apps";

    public static final String RUN_COMMAND_PERMISSION = TermuxConstants.TERMUX_PACKAGE_NAME + ".permission.RUN_COMMAND";

    private static final String PROP = TermuxConstants.PROP_ALLOW_EXTERNAL_APPS;
    private static final Pattern LINE = Pattern.compile("(?m)^[ \\t]*" + Pattern.quote(PROP) + "[ \\t]*[=:].*$");
    private static final Pattern COMMENTED = Pattern.compile("(?m)^[ \\t]*#+[ \\t]*" + Pattern.quote(PROP) + "[ \\t]*[=:].*$");

    private static boolean sWarnedThisProcess;

    private ExternalAppsAccess() {
    }

    // ---- the property ---------------------------------------------------------------------------

    /** The value RunCommandService will see, read fresh from the file. */
    public static boolean isAllowed() {
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.getProperties();
        if (properties == null) return false;
        properties.loadTermuxPropertiesFromDisk();
        return properties.shouldAllowExternalApps();
    }

    /** The file upstream reads: the first existing one of its list, else the primary path. */
    @NonNull
    public static File propertiesFile() {
        for (String path : TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST) {
            File f = new File(path);
            if (f.isFile() && f.canRead()) return f;
        }
        return TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
    }

    /** {@code ~/.termux/termux.properties}, for the row's description. */
    @NonNull
    public static String propertiesFileLabel() {
        return TermuxFileUtils.getUnExpandedTermuxPath(propertiesFile().getAbsolutePath());
    }

    public static void setAllowed(boolean allowed) throws IOException {
        File file = propertiesFile();
        String text = file.isFile() ? new String(read(file), StandardCharsets.UTF_8) : "";
        String line = PROP + " = " + allowed;
        String out;
        Matcher m = LINE.matcher(text);
        if (m.find()) {
            // Every occurrence: java.util.Properties lets the last one win.
            out = m.replaceAll(Matcher.quoteReplacement(line));
        } else if (!allowed) {
            out = null; // absent already means false
        } else {
            Matcher c = COMMENTED.matcher(text);
            if (c.find()) {
                out = c.replaceFirst(Matcher.quoteReplacement(line));
            } else {
                StringBuilder sb = new StringBuilder(text);
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
                if (sb.length() > 0) sb.append('\n');
                sb.append("# Set in 白い熊 Termux UI → External apps\n").append(line).append('\n');
                out = sb.toString();
            }
        }
        if (out != null && !out.equals(text)) write(file, out.getBytes(StandardCharsets.UTF_8));

        TermuxAppSharedProperties properties = TermuxAppSharedProperties.getProperties();
        if (properties != null) properties.loadTermuxPropertiesFromDisk();
    }

    @NonNull
    private static byte[] read(@NonNull File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /** Temp file beside the real one, then rename — through the canonical path (symlinks stay). */
    private static void write(@NonNull File file, @NonNull byte[] bytes) throws IOException {
        File target = file.getCanonicalFile();
        File parent = target.getParentFile();
        if (parent == null) throw new IOException("no parent directory: " + target);
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent);
        File tmp = new File(parent, target.getName() + ".shiroikuma-tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
            out.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("cannot replace " + target);
        }
    }

    // ---- who may call ---------------------------------------------------------------------------

    /** Labels of the installed apps that hold the RUN_COMMAND permission, sorted; ours excluded. */
    @NonNull
    public static List<String> permissionHolders(@NonNull Context context) {
        List<String> labels = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        try {
            List<PackageInfo> holders = pm.getPackagesHoldingPermissions(new String[]{RUN_COMMAND_PERMISSION}, 0);
            for (PackageInfo info : holders) {
                if (info.applicationInfo == null) continue;
                // The shared-UID family (termux-api, termux-x11, …) runs as Termux anyway.
                if (info.applicationInfo.uid == context.getApplicationInfo().uid) continue;
                CharSequence label = info.applicationInfo.loadLabel(pm);
                labels.add(label != null ? label.toString() : info.packageName);
            }
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot list the RUN_COMMAND permission holders", e);
        }
        Collections.sort(labels);
        return labels;
    }

    // ---- the warning ----------------------------------------------------------------------------

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean warnsAtStart(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_WARN_AT_START, true);
    }

    public static void setWarnsAtStart(@NonNull Context context, boolean warn) {
        prefs(context).edit().putBoolean(KEY_WARN_AT_START, warn).commit();
    }

    /** One line in ShiroikumaLifecycle: the terminal's first resume of this process. */
    public static void maybeWarn(@NonNull final Activity activity) {
        if (sWarnedThisProcess) return;
        // Before the bootstrap there is no home to hold a properties file; ask on a later start.
        if (TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) return;
        sWarnedThisProcess = true;
        if (!warnsAtStart(activity) || activity.isFinishing() || isAllowed()) return;

        LinearLayout box = ShiroikumaDialogs.infoBox(activity, activity.getString(R.string.shiroikuma_ext_dialog_title),
            activity.getString(R.string.shiroikuma_ext_dialog_body));
        final CheckBox never = ShiroikumaDialogs.checkBox(activity, activity.getString(R.string.shiroikuma_ext_dialog_never));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ShiroikumaDialogs.dp(activity, 10);
        box.addView(never, lp);

        final AlertDialog dialog = ShiroikumaDialogs.panel(activity, box, true);
        LinearLayout buttons = ShiroikumaDialogs.buttonRow(activity);
        ShiroikumaDialogs.addPill(buttons, ShiroikumaDialogs.pill(activity, activity.getString(android.R.string.cancel), v -> {
            if (never.isChecked()) setWarnsAtStart(activity, false);
            dialog.dismiss();
        }));
        ShiroikumaDialogs.addPill(buttons, ShiroikumaDialogs.mainPill(activity, activity.getString(R.string.shiroikuma_ext_dialog_open), v -> {
            if (never.isChecked()) setWarnsAtStart(activity, false);
            dialog.dismiss();
            ShiroikumaUiActivity.open(activity, SECTION_KEY);
        }));
        box.addView(buttons);
        try {
            dialog.show();
        } catch (RuntimeException e) {
            // The window went away between the resume and the post.
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot show the external-apps dialog", e);
        }
    }
}
