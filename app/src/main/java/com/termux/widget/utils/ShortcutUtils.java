package com.termux.widget.utils;

import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.file.TermuxFileUtils;
import com.termux.widget.NaturalOrderComparator;
import com.termux.R;
import com.termux.widget.ShortcutFile;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class ShortcutUtils {

    private static final String LOG_TAG = "ShortcutUtils";

    public static final int TERMUX_SHORTCUTS_SCRIPTS_DIR_MAX_SEARCH_DEPTH = 5;

    // shiroikuma-termux (Phase 4c): the two dynamic-shortcut paths of upstream's
    // activities/TermuxWidgetMainActivity, which is not ported (no second launcher icon).
    /** Termux:Widget app data home directory path. */
    public static final String TERMUX_WIDGET_DATA_HOME_DIR_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/widget"; // Default: "/data/data/com.termux/files/home/.termux/widget"

    /** Termux:Widget app directory path to store scripts/binaries to be used as dynamic shortcuts. */
    public static final String TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH = TERMUX_WIDGET_DATA_HOME_DIR_PATH + "/dynamic_shortcuts"; // Default: "/data/data/com.termux/files/home/.termux/widget/dynamic_shortcuts"

    /* Allowed paths under which shortcut files can exist. */
    public static final List<String> SHORTCUT_FILES_ALLOWED_PATHS_LIST = Arrays.asList(
            TermuxConstants.TERMUX_SHORTCUT_SCRIPTS_DIR_PATH,
            TermuxConstants.TERMUX_DATA_HOME_DIR_PATH);

    /* Allowed paths under which shortcut icons files can exist. */
    public static final List<String> SHORTCUT_ICONS_FILES_ALLOWED_PATHS_LIST = Arrays.asList(
            TermuxConstants.TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_PATH,
            TermuxConstants.TERMUX_DATA_HOME_DIR_PATH);

    public static final FileFilter SHORTCUT_FILES_FILTER = new FileFilter() {
        public boolean accept(File file) {
            if (file.getName().startsWith(".")) {
                // Do not show hidden files starting with a dot.
                return false;
            } else if (!FileUtils.fileExists(file.getAbsolutePath(), true)) {
                // Do not show broken symlinks
                return false;
            } else if (!FileUtils.isPathInDirPaths(file.getAbsolutePath(), SHORTCUT_FILES_ALLOWED_PATHS_LIST, true)) {
                // Do not show files that are not under SHORTCUT_FILES_ALLOWED_PATHS_LIST
                return false;
            } else if (TermuxConstants.TERMUX_SHORTCUT_SCRIPTS_DIR.equals(file.getParentFile()) &&
                    file.getName().equals(TermuxConstants.TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_BASENAME)) {
                // Do not show files under TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_PATH
                return false;
            }
            return true;
        }
    };

    public static void enumerateShortcutFiles(List<ShortcutFile> files, boolean sorted) {
        enumerateShortcutFiles(files, TermuxConstants.TERMUX_SHORTCUT_SCRIPTS_DIR, sorted, 0);
    }

    public static void enumerateShortcutFiles(List<ShortcutFile> files, File dir, boolean sorted) {
        enumerateShortcutFiles(files, dir, sorted, 0);
    }

    public static void enumerateShortcutFiles(List<ShortcutFile> files, File dir, boolean sorted, int depth) {
        if (depth > TERMUX_SHORTCUTS_SCRIPTS_DIR_MAX_SEARCH_DEPTH) return;

        File[] current_files = dir.listFiles(SHORTCUT_FILES_FILTER);

        if (current_files == null) return;

        if (sorted) {
            Arrays.sort(current_files, (lhs, rhs) -> {
                if (lhs.isDirectory() != rhs.isDirectory()) {
                    return lhs.isDirectory() ? 1 : -1;
                }
                return NaturalOrderComparator.compare(lhs.getName(), rhs.getName());
            });
        }

        for (File file : current_files) {
            if (file.isDirectory()) {
                enumerateShortcutFiles(files, file, sorted, depth + 1);
            } else {
                files.add(new ShortcutFile(file, depth));
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    public static ShortcutManager getShortcutManager(@NonNull Context context, @NonNull String logTag, boolean showErrorToast) {
        ShortcutManager shortcutManager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        if (shortcutManager == null)  {
            Logger.logErrorAndShowToast(showErrorToast ? context : null, logTag, context.getString(R.string.error_failed_to_get_shortcut_manager));
            return null;
        }
        return shortcutManager;
    }

    public static boolean isTermuxAppAccessible(@NonNull Context context, @NonNull String logTag, boolean showErrorToast) {
        String errmsg = TermuxUtils.isTermuxAppAccessible(context);
        if (errmsg != null) {
            Logger.logErrorAndShowToast(showErrorToast ? context : null, logTag, errmsg);
            return false;
        }
        return true;
    }



    // shiroikuma-termux (Phase 4c): upstream's TermuxWidgetMainActivity.createDynamicShortcuts() /
    // removeDynamicShortcuts(), verbatim but static — the 白い熊 Termux UI page's Widget section calls
    // them. Dynamic shortcuts hang off this app's launcher icon (long-press), next to the static ones.

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    public static void createDynamicShortcuts(@NonNull Context context) {
        ShortcutManager shortcutManager = ShortcutUtils.getShortcutManager(context, LOG_TAG, true);
        if (shortcutManager == null) return;

        // Create directory if necessary so user more easily finds where to put shortcuts
        Error error = FileUtils.createDirectoryFile(TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH);
        if (error != null) {
            Logger.logError(LOG_TAG, error.toString());
            Logger.showToast(context, error.getMinimalErrorLogString(), true);
        }

        List<ShortcutFile> shortcutFiles = new ArrayList<>();
        ShortcutUtils.enumerateShortcutFiles(shortcutFiles, new File(TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH), false);

        if (shortcutFiles.size() == 0) {
            Logger.showToast(context, context.getString(R.string.msg_no_shortcut_files_found_in_directory,
                    TermuxFileUtils.getUnExpandedTermuxPath(TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH)), true);
            return;
        }

        List<ShortcutInfo> shortcuts = new ArrayList<>();
        for (ShortcutFile shortcutFile : shortcutFiles) {
            shortcuts.add(shortcutFile.getShortcutInfo(context, false));
        }

        // Remove shortcuts that can not be added.
        // shiroikuma-termux: the limit counts the app's static shortcuts (res/xml/shortcuts.xml)
        // too — upstream's plugin had none; addDynamicShortcuts() throws when it is exceeded.
        int maxShortcuts = Math.max(0, shortcutManager.getMaxShortcutCountPerActivity() - shortcutManager.getManifestShortcuts().size());
        Logger.logDebug(LOG_TAG, "Found " + shortcutFiles.size() + " shortcuts and max shortcuts limit is " + maxShortcuts);
        if (shortcuts.size() > maxShortcuts) {
            Logger.logErrorAndShowToast(context, LOG_TAG, context.getString(R.string.msg_dynamic_shortcuts_limit_reached, maxShortcuts));
            while (shortcuts.size() > maxShortcuts) {
                String message = context.getString(R.string.msg_skipping_shortcut,
                        shortcuts.get(shortcuts.size() - 1).getId().replaceAll(
                                "^" + Pattern.quote(TERMUX_WIDGET_DYNAMIC_SHORTCUTS_DIR_PATH + "/"), ""));
                Logger.showToast(context, message, false);
                Logger.logDebug(LOG_TAG, message);
                shortcuts.remove(shortcuts.size() - 1);
            }
        }

        shortcutManager.removeAllDynamicShortcuts();
        try {
            shortcutManager.addDynamicShortcuts(shortcuts);
        } catch (Exception e) { // shiroikuma-termux: a launcher / rate-limit refusal is a toast, not a crash
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to add dynamic shortcuts", e);
            Logger.showToast(context, String.valueOf(e.getMessage()), true);
            return;
        }
        Logger.showToast(context, context.getString(R.string.msg_created_dynamic_shortcuts_successfully, shortcuts.size()), false);
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    public static void removeDynamicShortcuts(@NonNull Context context) {
        ShortcutManager shortcutManager = ShortcutUtils.getShortcutManager(context, LOG_TAG, true);
        if (shortcutManager == null) return;

        List<ShortcutInfo> shortcuts = shortcutManager.getDynamicShortcuts();
        if (shortcuts != null && shortcuts.size() == 0) {
            Logger.showToast(context, context.getString(R.string.msg_no_dynamic_shortcuts_currently_created), false);
            return;
        }

        shortcutManager.removeAllDynamicShortcuts();
        Logger.showToast(context, context.getString(R.string.msg_removed_dynamic_shortcuts_successfully), false);
    }

}
