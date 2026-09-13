package com.termux.shiroikuma.ui;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TextStyle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * shiroikuma-termux (Phase 4): the terminal's own styling files, the way termux-styling wrote them
 * and the way upstream reads them back in {@code checkForFontAndColors()}:
 * {@code ~/.termux/colors.properties} (keys {@code background}, {@code foreground}, {@code cursor},
 * {@code color0..15}, values {@code #RRGGBB}) and {@code ~/.termux/font.ttf}. Bundled schemes and
 * fonts live under {@code assets/shiroikuma/}; imported fonts under {@code ~/.termux/fonts/}.
 *
 * <p>Colours are written by hand as {@code key=#RRGGBB} lines — never {@code Properties.store},
 * which would escape the {@code #}.
 */
public final class TerminalStyleFiles {

    public static final String HOUSE_SCHEME = "shiroikuma";
    public static final String ASSET_COLORS_DIR = "shiroikuma/colors";
    public static final String ASSET_FONTS_DIR = "shiroikuma/fonts";
    public static final File FONTS_DIR = new File(TermuxConstants.TERMUX_DATA_HOME_DIR, "fonts");

    public static final String KEY_BACKGROUND = "background";
    public static final String KEY_FOREGROUND = "foreground";
    public static final String KEY_CURSOR = "cursor";

    /** The keys in the order they are written. */
    private static final List<String> KEY_ORDER;

    static {
        List<String> keys = new ArrayList<>(Arrays.asList(KEY_BACKGROUND, KEY_FOREGROUND, KEY_CURSOR));
        for (int i = 0; i < 16; i++) keys.add("color" + i);
        KEY_ORDER = keys;
    }

    private TerminalStyleFiles() {
    }

    // ---- colours -------------------------------------------------------------------------------

    /** The current file as key → raw value (empty when there is none). */
    @NonNull
    public static Map<String, String> readColors() {
        Map<String, String> out = new LinkedHashMap<>();
        File f = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
        if (!f.isFile()) return out;
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            props.load(in);
        } catch (IOException ignored) {
            return out;
        }
        for (String key : KEY_ORDER) {
            String v = props.getProperty(key);
            if (v != null) out.put(key, v.trim());
        }
        for (String name : props.stringPropertyNames()) {
            if (!out.containsKey(name)) out.put(name, props.getProperty(name).trim());
        }
        return out;
    }

    public static void writeColors(@NonNull Map<String, String> colors) throws IOException {
        File f = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        StringBuilder sb = new StringBuilder();
        sb.append("# Written by 白い熊 Termux UI — edit there, or by hand.\n");
        for (String key : KEY_ORDER) {
            String v = colors.get(key);
            if (v != null) sb.append(key).append('=').append(v).append('\n');
        }
        for (Map.Entry<String, String> e : colors.entrySet()) {
            if (!KEY_ORDER.contains(e.getKey())) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) throw new IOException("cannot replace " + f);
    }

    /** Sets one colour in the file (keeping every other key) as {@code #RRGGBB}. */
    public static void setColor(@NonNull String key, int argb) throws IOException {
        Map<String, String> colors = readColors();
        colors.put(key, rgbHex(argb));
        writeColors(colors);
    }

    /** The house scheme: yellow on black, yellow cursor. */
    public static void writeHouseColors() throws IOException {
        Map<String, String> colors = new LinkedHashMap<>();
        colors.put(KEY_BACKGROUND, "#000000");
        colors.put(KEY_FOREGROUND, "#FFFF00");
        colors.put(KEY_CURSOR, "#FFFF00");
        writeColors(colors);
    }

    /** The house default of one of the three main slots (what long-press restores). */
    public static int houseColor(@NonNull String key) {
        return KEY_BACKGROUND.equals(key) ? ShiroikumaTheme.BLACK : ShiroikumaTheme.YELLOW;
    }

    /**
     * The colour currently in effect for a slot: the file's value when it parses, else what the
     * emulator's loaded scheme holds (which is what the terminal is actually drawing).
     */
    public static int currentColor(@NonNull String key) {
        String raw = readColors().get(key);
        int parsed = raw != null ? parse(raw) : 0;
        if (parsed != 0) return parsed;
        int index;
        switch (key) {
            case KEY_BACKGROUND:
                index = TextStyle.COLOR_INDEX_BACKGROUND;
                break;
            case KEY_CURSOR:
                index = TextStyle.COLOR_INDEX_CURSOR;
                break;
            default:
                index = TextStyle.COLOR_INDEX_FOREGROUND;
        }
        return TerminalColors.COLOR_SCHEME.mDefaultColors[index];
    }

    /** {@code #RRGGBB}, the only form every termux reader accepts (no alpha). */
    @NonNull
    public static String rgbHex(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    /**
     * Parses {@code #RGB}, {@code #RRGGBB}, {@code #RRRGGGBBB}, {@code #RRRRGGGGBBBB} (what
     * {@code TerminalColors.parse} accepts, which is package-private) into opaque ARGB; 0 on failure.
     */
    public static int parse(@Nullable String c) {
        if (c == null || c.length() < 4 || c.charAt(0) != '#') return 0;
        int chars = c.length() - 1;
        if (chars % 3 != 0) return 0;
        int len = chars / 3;
        if (len < 1 || len > 4) return 0;
        try {
            double mult = 255 / (Math.pow(2, len * 4) - 1);
            int r = (int) (Integer.parseInt(c.substring(1, 1 + len), 16) * mult);
            int g = (int) (Integer.parseInt(c.substring(1 + len, 1 + 2 * len), 16) * mult);
            int b = (int) (Integer.parseInt(c.substring(1 + 2 * len, 1 + 3 * len), 16) * mult);
            return 0xFF << 24 | r << 16 | g << 8 | b;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- schemes -------------------------------------------------------------------------------

    /** One bundled colour scheme, parsed. */
    public static final class Scheme {
        @NonNull public final String name;
        @NonNull public final Map<String, String> colors;
        public final int background;
        public final int foreground;
        public final int cursor;

        Scheme(@NonNull String name, @NonNull Map<String, String> colors) {
            this.name = name;
            this.colors = colors;
            background = parseOr(colors.get(KEY_BACKGROUND), 0xFF000000);
            foreground = parseOr(colors.get(KEY_FOREGROUND), 0xFFFFFFFF);
            String cur = colors.get(KEY_CURSOR);
            cursor = cur != null ? parseOr(cur, foreground) : foreground;
        }

        private static int parseOr(@Nullable String raw, int fallback) {
            int v = parse(raw);
            return v != 0 ? v : fallback;
        }
    }

    /** Every bundled scheme, the house one first, the rest alphabetically. */
    @NonNull
    public static List<Scheme> bundledSchemes(@NonNull Context context) {
        AssetManager assets = context.getAssets();
        List<Scheme> out = new ArrayList<>();
        String[] names;
        try {
            names = assets.list(ASSET_COLORS_DIR);
        } catch (IOException e) {
            return out;
        }
        if (names == null) return out;
        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        Scheme house = null;
        for (String file : names) {
            if (!file.endsWith(".properties")) continue;
            String name = file.substring(0, file.length() - ".properties".length());
            Map<String, String> colors = new LinkedHashMap<>();
            try (InputStream in = assets.open(ASSET_COLORS_DIR + "/" + file)) {
                Properties props = new Properties();
                props.load(in);
                for (String key : KEY_ORDER) {
                    String v = props.getProperty(key);
                    if (v != null) colors.put(key, v.trim());
                }
            } catch (IOException e) {
                continue;
            }
            Scheme s = new Scheme(name, colors);
            if (HOUSE_SCHEME.equals(name)) house = s; else out.add(s);
        }
        if (house != null) out.add(0, house);
        return out;
    }

    /** Writes a whole scheme to colors.properties (every key, replacing the file). */
    public static void applyScheme(@NonNull Context context, @NonNull Scheme scheme) throws IOException {
        writeColors(new LinkedHashMap<>(scheme.colors));
        ShiroikumaTheme.setString(context, ShiroikumaTheme.TERMINAL_SCHEME, scheme.name);
    }

    // ---- fonts ---------------------------------------------------------------------------------

    /** One pickable font: the platform monospace, a bundled asset, or a file under ~/.termux/fonts. */
    public static final class FontOption {
        @NonNull public final String label;
        @Nullable public final String asset; // "shiroikuma/fonts/X.ttf"
        @Nullable public final File file;    // ~/.termux/fonts/X.ttf

        FontOption(@NonNull String label, @Nullable String asset, @Nullable File file) {
            this.label = label;
            this.asset = asset;
            this.file = file;
        }

        public boolean isDefault() {
            return asset == null && file == null;
        }

        /** What the Font row shows and TERMINAL_FONT remembers. */
        @NonNull
        public String id() {
            if (asset != null) return "asset:" + label;
            if (file != null) return "file:" + file.getName();
            return "";
        }
    }

    @NonNull
    public static List<FontOption> fontOptions(@NonNull Context context) {
        List<FontOption> out = new ArrayList<>();
        out.add(new FontOption(context.getString(com.termux.R.string.shiroikuma_ui_terminal_font_default), null, null));
        try {
            String[] names = context.getAssets().list(ASSET_FONTS_DIR);
            if (names != null) {
                Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
                for (String n : names) {
                    if (isFontFile(n)) out.add(new FontOption(stripExtension(n), ASSET_FONTS_DIR + "/" + n, null));
                }
            }
        } catch (IOException ignored) {
        }
        File[] files = FONTS_DIR.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                if (f.isFile() && isFontFile(f.getName())) out.add(new FontOption(stripExtension(f.getName()), null, f));
            }
        }
        return out;
    }

    public static boolean isFontFile(@NonNull String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    @NonNull
    private static String stripExtension(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** The typeface of an option, or the platform monospace when it cannot be loaded. */
    @NonNull
    public static Typeface typeface(@NonNull Context context, @NonNull FontOption option) {
        try {
            if (option.asset != null) return Typeface.createFromAsset(context.getAssets(), option.asset);
            if (option.file != null) return Typeface.createFromFile(option.file);
        } catch (Exception ignored) {
        }
        return Typeface.MONOSPACE;
    }

    /** What the terminal is drawing with right now: ~/.termux/font.ttf, or the platform monospace. */
    @NonNull
    public static Typeface currentTypeface() {
        File f = TermuxConstants.TERMUX_FONT_FILE;
        try {
            if (f.isFile() && f.length() > 0) return Typeface.createFromFile(f);
        } catch (Exception ignored) {
        }
        return Typeface.MONOSPACE;
    }

    /** The id of the option in effect ("" when font.ttf is absent, i.e. the default). */
    @NonNull
    public static String currentFontId(@NonNull Context context) {
        File f = TermuxConstants.TERMUX_FONT_FILE;
        if (!f.isFile() || f.length() == 0) return "";
        String id = ShiroikumaTheme.getString(context, ShiroikumaTheme.TERMINAL_FONT);
        return id != null ? id : "?";
    }

    /** The Font row's summary. */
    @NonNull
    public static String currentFontLabel(@NonNull Context context) {
        String id = currentFontId(context);
        if (id.isEmpty()) return context.getString(com.termux.R.string.shiroikuma_ui_terminal_font_default);
        int colon = id.indexOf(':');
        String name = colon >= 0 ? id.substring(colon + 1) : id;
        return id.startsWith("file:") ? stripExtension(name) : name;
    }

    /** Copies the option into ~/.termux/font.ttf (or deletes it for the default), as termux-styling did. */
    public static void selectFont(@NonNull Context context, @NonNull FontOption option) throws IOException {
        File target = TermuxConstants.TERMUX_FONT_FILE;
        if (option.isDefault()) {
            if (target.exists() && !target.delete()) throw new IOException("cannot delete " + target);
            ShiroikumaTheme.setString(context, ShiroikumaTheme.TERMINAL_FONT, null);
            return;
        }
        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File tmp = new File(target.getPath() + ".tmp");
        try (InputStream in = option.asset != null
            ? context.getAssets().open(option.asset)
            : new FileInputStream(option.file);
             OutputStream out = new FileOutputStream(tmp)) {
            copy(in, out);
        }
        if (!tmp.renameTo(target)) throw new IOException("cannot replace " + target);
        ShiroikumaTheme.setString(context, ShiroikumaTheme.TERMINAL_FONT, option.id());
    }

    /**
     * Copies a picked document into ~/.termux/fonts/ under its display name and returns the
     * resulting option, or null when it is not a .ttf/.otf or cannot be read.
     */
    @Nullable
    public static FontOption importFont(@NonNull Context context, @NonNull Uri uri) {
        String name = displayName(context, uri);
        if (name == null || !isFontFile(name)) return null;
        name = name.replaceAll("[/\\\\ ]", "_");
        if (!FONTS_DIR.isDirectory() && !FONTS_DIR.mkdirs()) return null;
        File target = new File(FONTS_DIR, name);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("no stream");
            copy(in, out);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return null;
        }
        return new FontOption(stripExtension(name), null, target);
    }

    @Nullable
    private static String displayName(@NonNull Context context, @NonNull Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = c.getString(idx);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    private static void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
