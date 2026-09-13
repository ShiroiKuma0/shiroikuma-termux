package com.termux.shiroikuma;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shiroikuma.ui.ShiroikumaTheme;
import com.termux.shiroikuma.ui.TerminalStyleFiles;

/**
 * shiroikuma-termux (Phase 4): first-run defaults. The house look of the terminal itself lives in
 * {@code ~/.termux/colors.properties} (yellow on black, yellow cursor — exactly what termux-styling
 * would have written), so on the first start that finds no such file we write it once, and
 * remember that in the {@code defaults_written} marker so a later deliberate deletion of the file
 * is respected. An existing file (an upgrade over a styled install) is never touched.
 */
public final class ShiroikumaDefaults {

    private static final String LOG_TAG = "ShiroikumaDefaults";

    private ShiroikumaDefaults() {
    }

    public static void ensure(@NonNull Context context) {
        SharedPreferences prefs = ShiroikumaTheme.prefs(context);
        if (prefs.getBoolean(ShiroikumaTheme.DEFAULTS_WRITTEN, false)) return;
        try {
            if (!TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE.exists()) {
                TerminalStyleFiles.writeHouseColors();
                ShiroikumaTheme.setString(context, ShiroikumaTheme.TERMINAL_SCHEME, TerminalStyleFiles.HOUSE_SCHEME);
            }
            prefs.edit().putBoolean(ShiroikumaTheme.DEFAULTS_WRITTEN, true).commit();
        } catch (Exception e) {
            // ~/.termux may not be writable yet (files dir still being set up) — retried next time.
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not write the house colors.properties", e);
        }
    }
}
