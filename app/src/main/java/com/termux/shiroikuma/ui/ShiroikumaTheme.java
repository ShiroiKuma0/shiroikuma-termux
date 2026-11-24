package com.termux.shiroikuma.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * shiroikuma-termux (Phase 4): the prefs of the 白い熊 Termux UI page and their house defaults.
 *
 * <p>Everything lives in the {@code shiroikuma_ui} SharedPreferences file (exported by the
 * Export/Import "settings" category of Phase 4b). An absent key means "house default" — the
 * page's long-press-to-reset simply removes the key. Colours are ARGB ints, sizes plain ints in
 * the unit their key name carries ({@code _sp}, {@code _dp}). The terminal's own colours and font
 * are NOT here: they are files under {@code ~/.termux} (see {@link TerminalStyleFiles}), because
 * that is where upstream reads them from.
 *
 * <p>Resolution is deliberately flat (the raikidoban {@code UiTheme} cascade is overkill for two
 * dozen slots): {@link #color} / {@link #value} return the stored value or the default.
 */
public final class ShiroikumaTheme {

    public static final String PREFS = "shiroikuma_ui";

    public static final int BLACK = 0xFF000000;
    public static final int YELLOW = 0xFFFFFF00;
    public static final int YELLOW_DIM = 0xFFC8C800;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int RED = 0xFFFF5252;
    public static final int SELECTED = 0x33FFFF00;

    // --- extra keys row ---
    public static final String EXTRAKEYS_BG = "extrakeys_bg";
    public static final String EXTRAKEYS_TEXT = "extrakeys_text";
    public static final String EXTRAKEYS_ACTIVE_TEXT = "extrakeys_active_text";
    public static final String EXTRAKEYS_ACTIVE_BG = "extrakeys_active_bg";
    public static final String EXTRAKEYS_TEXT_SIZE_SP = "extrakeys_text_size_sp";
    public static final String EXTRAKEYS_BORDER_DP = "extrakeys_border_dp";
    public static final String EXTRAKEYS_BORDER_COLOR = "extrakeys_border_color";
    public static final String EXTRAKEYS_CORNER_DP = "extrakeys_corner_dp";
    // --- drawer / sessions ---
    public static final String DRAWER_BG = "drawer_bg";
    public static final String DRAWER_BUTTON_TEXT = "drawer_button_text";
    public static final String DRAWER_ICON_TINT = "drawer_icon_tint";
    public static final String SESSION_TEXT = "session_text";
    public static final String SESSION_SELECTED_BG = "session_selected_bg";
    public static final String SESSION_DEAD_TEXT = "session_dead_text";
    public static final String SESSION_TEXT_SIZE_SP = "session_text_size_sp";
    // --- toolbar / status bar ---
    public static final String TOOLBAR_BG = "toolbar_bg";
    public static final String TOOLBAR_TEXT = "toolbar_text";
    public static final String TOOLBAR_ICON = "toolbar_icon";
    public static final String STATUSBAR_BG = "statusbar_bg";
    public static final String NAVBAR_BG = "navbar_bg";
    // --- dialogs ---
    public static final String DIALOG_BG = "dialog_bg";
    public static final String DIALOG_TEXT = "dialog_text";
    public static final String DIALOG_TITLE = "dialog_title";
    public static final String DIALOG_BUTTON = "dialog_button";
    public static final String DIALOG_BORDER_COLOR = "dialog_border_color";
    public static final String DIALOG_BORDER_DP = "dialog_border_dp";
    public static final String DIALOG_CORNER_DP = "dialog_corner_dp";
    // --- menus ---
    public static final String MENU_BG = "menu_bg";
    public static final String MENU_TEXT = "menu_text";
    public static final String MENU_BORDER_COLOR = "menu_border_color";
    public static final String MENU_BORDER_DP = "menu_border_dp";
    public static final String MENU_CORNER_DP = "menu_corner_dp";
    // --- menus: the icon colour of a row says what the row does (pill menus); "view" rows use MENU_TEXT ---
    public static final String MENU_ICON_CONNECT = "menu_icon_connect";
    public static final String MENU_ICON_SHARE = "menu_icon_share";
    public static final String MENU_ICON_CONTACT = "menu_icon_contact";
    public static final String MENU_ICON_BLOCK = "menu_icon_block";
    /** Frame, icon and label of a destructive row. */
    public static final String MENU_DELETE = "menu_delete";
    // --- absorbed plugins (Phase 4c): the launcher widget's list and the floating terminal's frame ---
    public static final String WIDGET_BG = "widget_bg";
    public static final String WIDGET_TEXT = "widget_text";
    public static final String WIDGET_TEXT_SIZE_SP = "widget_text_size_sp";
    public static final String FLOAT_BG = "float_bg";
    public static final String FLOAT_BORDER_COLOR = "float_border_color";
    public static final String FLOAT_BORDER_DP = "float_border_dp";
    public static final String FLOAT_CORNER_DP = "float_corner_dp";
    /** The floating terminal's notification text; absent = upstream's "Touch to hide/show window." */
    public static final String FLOAT_NOTIFICATION_TEXT = "float_notification_text";
    // --- the UI page itself: every item of the page is a role of its own ---
    public static final String PAGE_BG = "page_bg";
    public static final String PAGE_HEADING = "page_heading";
    public static final String PAGE_LABEL = "page_label";
    /** Descriptions, loose notes and the 2 dp left rule of an explained row. */
    public static final String PAGE_NOTE = "page_note";
    public static final String PAGE_VALUE = "page_value";
    /** The 1 px line between sections. */
    public static final String PAGE_DIVIDER = "page_divider";
    public static final String PAGE_WARNING = "page_warning";
    /** The icon of a row that only views / opens / sets something. */
    public static final String PAGE_ICON = "page_icon";
    public static final String CHIP_FRAME = "chip_frame";
    public static final String CHIP_TEXT = "chip_text";
    public static final String SWATCH_FRAME = "swatch_frame";
    public static final String SLIDER = "slider";
    public static final String SLIDER_TRACK = "slider_track";
    // --- pills and switches of the page (the switch pair also draws the menu's switch) ---
    public static final String PILL_FRAME = "pill_frame";
    public static final String PILL_TEXT = "pill_text";
    public static final String SWITCH_ON = "switch_on";
    public static final String SWITCH_OFF = "switch_off";
    // --- the seven section-icon colours: what kind of thing a section is ---
    public static final String SECTION_BASE = "section_base";
    public static final String SECTION_CONTROLS = "section_controls";
    public static final String SECTION_WORDS = "section_words";
    public static final String SECTION_FRAME = "section_frame";
    public static final String SECTION_REACH = "section_reach";
    public static final String SECTION_WARNING = "section_warning";
    public static final String SECTION_FLASH = "section_flash";
    // --- the page's arrangement and the app language (strings; kept by a reset) ---
    /** BCP-47 tag of the app language, absent / empty = the system's. See AppLanguage. */
    public static final String APP_LANGUAGE = "app_language";
    public static final String SECTION_ORDER = "ui_section_order";
    public static final String SECTIONS_OPEN = "ui_sections_open";
    // --- terminal bookkeeping (display only; the truth is ~/.termux) ---
    public static final String TERMINAL_SCHEME = "terminal_scheme";
    public static final String TERMINAL_FONT = "terminal_font";
    // --- the colour picker's recent swatches (CSV of ints) and the first-run marker ---
    public static final String RECENT_COLORS = "recent_colors";
    public static final String DEFAULTS_WRITTEN = "defaults_written";

    private ShiroikumaTheme() {
    }

    @NonNull
    public static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** House default of a colour slot. */
    public static int defaultColor(@NonNull String key) {
        switch (key) {
            case EXTRAKEYS_BG:
            case DRAWER_BG:
            case TOOLBAR_BG:
            case STATUSBAR_BG:
            case NAVBAR_BG:
            case DIALOG_BG:
            case MENU_BG:
            case WIDGET_BG:
            case FLOAT_BG:
            case PAGE_BG:
                return BLACK;
            case PAGE_NOTE:
            case SLIDER_TRACK:
                return YELLOW_DIM;
            case PAGE_DIVIDER:
                return 0xFFB3B300; // 70 % of the way from black to yellow
            case PAGE_WARNING:
                return RED;
            case SWITCH_OFF:
                return 0xFF8C8C00; // opaque dim yellow, never a faded one
            case SECTION_CONTROLS:
                return 0xFF00A550; // heavy green
            case SECTION_WORDS:
                return 0xFF00B0FF; // light blue
            case SECTION_FRAME:
                return 0xFF0066FF; // heavy blue
            case SECTION_REACH:
                return 0xFFFF8000; // orange
            case SECTION_WARNING:
                return 0xFFFF0000; // red
            case SECTION_FLASH:
                return 0xFFFF3D7F; // rose
            case EXTRAKEYS_ACTIVE_TEXT:
                return WHITE;
            case EXTRAKEYS_ACTIVE_BG:
            case SESSION_SELECTED_BG:
                return SELECTED;
            case SESSION_DEAD_TEXT:
                return RED;
            case MENU_ICON_CONNECT:
                return 0xFF00A550; // heavy green
            case MENU_ICON_SHARE:
                return 0xFFFF8000; // orange
            case MENU_ICON_CONTACT:
                return 0xFF00B0FF; // light blue
            case MENU_ICON_BLOCK:
                return 0xFFFF3D7F; // rose
            case MENU_DELETE:
                return 0xFFFF0000; // red
            default:
                // extrakeys_text/border, drawer_button_text/icon_tint, session_text, toolbar_text/icon,
                // dialog_text/title/button/border, menu_text/border, widget_text, float_border_color,
                // page_heading/label/value/icon, chip_*, swatch_frame, slider, pill_*, switch_on, section_base
                return YELLOW;
        }
    }

    /** House default of a size slot (in the unit of its key name). */
    public static int defaultValue(@NonNull String key) {
        switch (key) {
            case EXTRAKEYS_TEXT_SIZE_SP:
                return 0; // 0 = the platform button default
            case EXTRAKEYS_BORDER_DP:
                return 1;
            case EXTRAKEYS_CORNER_DP:
                return 4;
            case SESSION_TEXT_SIZE_SP:
                return 14;
            case DIALOG_BORDER_DP:
                return 2;
            case DIALOG_CORNER_DP:
                return 8;
            case MENU_BORDER_DP:
                return 2;
            case MENU_CORNER_DP:
                return 14;
            case WIDGET_TEXT_SIZE_SP:
                return 16;
            case FLOAT_BORDER_DP:
                return 1;
            case FLOAT_CORNER_DP:
                return 8;
            default:
                return 0;
        }
    }

    public static int color(@NonNull Context context, @NonNull String key) {
        return prefs(context).getInt(key, defaultColor(key));
    }

    public static int value(@NonNull Context context, @NonNull String key) {
        return prefs(context).getInt(key, defaultValue(key));
    }

    public static boolean isSet(@NonNull Context context, @NonNull String key) {
        return prefs(context).contains(key);
    }

    public static void setInt(@NonNull Context context, @NonNull String key, int value) {
        prefs(context).edit().putInt(key, value).apply();
    }

    public static void remove(@NonNull Context context, @NonNull String key) {
        prefs(context).edit().remove(key).apply();
    }

    @Nullable
    public static String getString(@NonNull Context context, @NonNull String key) {
        return prefs(context).getString(key, null);
    }

    public static void setString(@NonNull Context context, @NonNull String key, @Nullable String value) {
        SharedPreferences.Editor e = prefs(context).edit();
        if (value == null) e.remove(key); else e.putString(key, value);
        e.apply();
    }

    /**
     * Wipes every colour and size back to the house default. Kept: the first-run marker, the app
     * language and the page's arrangement (section order, open sections) — none of them a look.
     */
    public static void reset(@NonNull Context context) {
        SharedPreferences p = prefs(context);
        boolean written = p.getBoolean(DEFAULTS_WRITTEN, false);
        String language = p.getString(APP_LANGUAGE, null);
        String order = p.getString(SECTION_ORDER, null);
        String open = p.getString(SECTIONS_OPEN, null);
        SharedPreferences.Editor e = p.edit().clear().putBoolean(DEFAULTS_WRITTEN, written);
        if (language != null) e.putString(APP_LANGUAGE, language);
        if (order != null) e.putString(SECTION_ORDER, order);
        if (open != null) e.putString(SECTIONS_OPEN, open);
        e.commit();
    }

    /** A disabled item: the same colour at 38 %. */
    public static int disabled(int color) {
        return withAlpha(color, Math.round(Color.alpha(color) * 0.38f));
    }

    /** {@code #AARRGGBB}, the summary format of every colour row. */
    @NonNull
    public static String hex(int argb) {
        return String.format(java.util.Locale.ROOT, "#%08X", argb);
    }

    /** Multiplies the alpha of a colour (dim summaries, ripples). */
    public static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** True when the colour is closer to black than to white — picks light status-bar icons. */
    public static boolean isDark(int color) {
        return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) < 128;
    }
}
