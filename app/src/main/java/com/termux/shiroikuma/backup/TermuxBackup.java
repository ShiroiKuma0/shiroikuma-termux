package com.termux.shiroikuma.backup;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.StatFs;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shiroikuma.ui.ShiroikumaTheme;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;

/**
 * shiroikuma-termux (Phase 4b): the ONE backup engine — the Export / Import panel, the §1
 * broadcast door and the §2a descriptor door are three thin callers of it.
 *
 * <h3>Categories</h3>
 * {@code settings} (the app's SharedPreferences + the files under {@code ~/.termux} and
 * {@code ~/.config/termux} that style the terminal), and {@code data} with two selectable parts:
 * {@code data.home} ({@code /data/data/com.termux/files/home}) and {@code data.usr} (the package
 * prefix, {@code …/files/usr}). All default on — this is 白い熊's {@code baktermux} in the app.
 *
 * <h3>The ZIP</h3>
 * <pre>
 * manifest.json                 FIRST: format, version, app, appVersion, createdTs, categories[], bytes{}
 * settings.json                 typed dump of com.termux_preferences (minus current_session) + shiroikuma_ui
 * settings/termux.properties    ~/.termux/termux.properties
 * settings/config/termux.properties  ~/.config/termux/termux.properties
 * settings/colors.properties    ~/.termux/colors.properties
 * settings/font.ttf             ~/.termux/font.ttf
 * settings/fonts/&lt;name&gt;         ~/.termux/fonts/*
 * data/home.tar                 tar of files/home as home/…   (DEFLATED level 0 — streamable)
 * data/usr.tar                  tar of files/usr  as usr/…
 * </pre>
 * The tars are stored inside the zip with DEFLATE level 0, so {@code unzip -p x.zip data/home.tar |
 * tar t} works and the whole thing streams both ways ({@link ZipArchiveInputStream} on import — no
 * multi-GB spool). Why Java tars and not {@code $PREFIX/bin/tar}: a wiped phone has no {@code usr}
 * yet, and restoring {@code usr} with a tar that lives inside {@code usr} is circular.
 *
 * <h3>Restore</h3>
 * {@code settings} merges preferences with {@code commit()} and copies the files, with the
 * tighten-only rule for {@code allow-external-apps} (never written {@code true} unless the device
 * already had it). {@code data.*} extract into {@code files/home-staging} / {@code files/usr-staging}
 * and are swapped in only after the last byte of the ZIP was read — a truncated archive never
 * touches live data — after a free-space check from the manifest and a {@link ProcessReaper} run,
 * because sessions hold the old prefix open. {@code TermuxInstaller} skips the bootstrap when
 * {@code $PREFIX} is non-empty, so a restore before first launch boots into the restored prefix.
 */
public final class TermuxBackup {

    private static final String LOG_TAG = "TermuxBackup";

    public static final String FORMAT = "shiroikuma-termux";
    public static final int VERSION = 1;
    /** Contract §1: {@code <english-dash-separated-app-name>_<yyyy-MM-dd_HH-mm-ss>.zip}. */
    public static final String EXPORT_PREFIX = "shiroikuma-termux_";
    public static final String PART_SUFFIX = ".part";

    static final String MANIFEST = "manifest.json";
    static final String SETTINGS_JSON = "settings.json";
    static final String SETTINGS_DIR = "settings/";
    static final String HOME_TAR = "data/home.tar";
    static final String USR_TAR = "data/usr.tar";

    /** Preference files of the settings category, and the keys inside them that stay home. */
    static final String PREFS_TERMUX = TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION;
    static final String PREFS_UI = ShiroikumaTheme.PREFS;
    private static final Set<String> PREFS_TERMUX_SKIP = Collections.singleton("current_session");

    private static final Pattern ALLOW_EXTERNAL_APPS = Pattern.compile("(?m)^\\s*allow-external-apps\\s*=\\s*(\\S+)\\s*$");

    /** Guards against two exports/imports at once. Process-local, released in a {@code finally}. */
    public static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private TermuxBackup() {
    }

    // ---- categories ----------------------------------------------------------------------------

    public enum Cat {
        SETTINGS("settings", null, R.string.shiroikuma_eim_cat_settings, R.string.shiroikuma_eim_cat_settings_short),
        DATA("data", null, R.string.shiroikuma_eim_cat_data, R.string.shiroikuma_eim_cat_data_short),
        DATA_HOME("data.home", "data", R.string.shiroikuma_eim_cat_home, R.string.shiroikuma_eim_cat_home_short),
        DATA_USR("data.usr", "data", R.string.shiroikuma_eim_cat_usr, R.string.shiroikuma_eim_cat_usr_short);

        @NonNull public final String id;
        @Nullable public final String parentId;
        public final int labelRes;
        public final int shortLabelRes;
        /** What LIST_CATEGORIES calls {@code on} — every one of ours. */
        public final boolean defaultSelected = true;

        Cat(@NonNull String id, @Nullable String parentId, int labelRes, int shortLabelRes) {
            this.id = id;
            this.parentId = parentId;
            this.labelRes = labelRes;
            this.shortLabelRes = shortLabelRes;
        }

        public boolean isChild() {
            return parentId != null;
        }

        @Nullable
        public static Cat byId(@Nullable String id) {
            if (id == null) return null;
            for (Cat c : values()) if (c.id.equals(id)) return c;
            return null;
        }

        @NonNull
        public static Set<Cat> all() {
            return EnumSet.allOf(Cat.class);
        }

        @NonNull
        public static Set<Cat> defaults() {
            Set<Cat> out = EnumSet.noneOf(Cat.class);
            for (Cat c : values()) if (c.defaultSelected) out.add(c);
            return out;
        }

        /** Resolves a comma list of ids; null when one is unknown; empty input = the defaults. */
        @Nullable
        public static Set<Cat> resolve(@Nullable String items) {
            if (items == null || items.trim().isEmpty()) return defaults();
            Set<Cat> out = EnumSet.noneOf(Cat.class);
            for (String raw : items.split(",")) {
                String id = raw.trim();
                if (id.isEmpty()) continue;
                Cat c = byId(id);
                if (c == null) return null;
                out.add(c);
            }
            return out.isEmpty() ? defaults() : out;
        }
    }

    /**
     * The leaves that actually get written for a selection: {@code data} alone means both of its
     * parts (it has no data of its own); a named part means that part; a part implies its parent
     * in the manifest.
     */
    @NonNull
    static Set<Cat> leaves(@NonNull Set<Cat> cats) {
        Set<Cat> out = EnumSet.noneOf(Cat.class);
        if (cats.contains(Cat.SETTINGS)) out.add(Cat.SETTINGS);
        boolean home = cats.contains(Cat.DATA_HOME);
        boolean usr = cats.contains(Cat.DATA_USR);
        if (cats.contains(Cat.DATA) && !home && !usr) {
            home = true;
            usr = true;
        }
        if (home) out.add(Cat.DATA_HOME);
        if (usr) out.add(Cat.DATA_USR);
        return out;
    }

    // ---- callbacks -----------------------------------------------------------------------------

    public interface Progress {
        /** Files done / total and content bytes done / total for the category being written (totals may be 0 while unknown). */
        void onProgress(@NonNull Cat cat, long files, long filesTotal, long bytes, long bytesTotal);

        /** A step with no honest count of its own (the pre-walk, the swap). */
        void onNote(@Nullable Cat cat, @NonNull String text);
    }

    public interface Cancel {
        boolean isCancelled();
    }

    /** Thrown out of the engine when the cancel flag went up between two entries. */
    public static final class CancelledException extends IOException {
        public CancelledException() {
            super("cancelled");
        }
    }

    /** One human line per category that was written or restored, plus totals for the reply. */
    public static final class Result {
        @NonNull public final List<String> lines = new ArrayList<>();
        @NonNull public final Set<Cat> categories = new LinkedHashSet<>();
        public long skipped;
        public long changed;
        /** The finished archive, when {@link #exportToDirectory} wrote one. */
        @Nullable public File file;

        @NonNull
        public String summary() {
            StringBuilder sb = new StringBuilder();
            for (String l : lines) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(l);
            }
            return sb.toString();
        }
    }

    // ---- names and sizes -----------------------------------------------------------------------

    @NonNull
    public static String exportFileName() {
        return EXPORT_PREFIX + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date()) + ".zip";
    }

    /** {@code 4.6 MB}, {@code 1.20 GB} — the contract's display size. */
    @NonNull
    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, mb < 10 ? "%.1f MB" : "%.0f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, gb < 10 ? "%.2f GB" : "%.1f GB", gb);
    }

    /** The data half needs {@code java.nio.file.attribute.FileTime} inside Commons Compress — API 26+. */
    public static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    // ---- export --------------------------------------------------------------------------------

    /**
     * Writes the selected categories as one ZIP to {@code os} (left open). Progress and cancel
     * are optional. Throws {@link CancelledException} when stopped, any other IOException on
     * failure — the caller deletes whatever it was writing into.
     */
    @NonNull
    public static Result export(@NonNull Context context, @NonNull Set<Cat> cats, @NonNull OutputStream os,
                                @Nullable Progress progress, @Nullable Cancel cancel) throws IOException {
        if (!supported()) throw new IOException("backup needs Android 8.0 or newer");
        Context app = context.getApplicationContext();
        Set<Cat> leaves = leaves(cats);
        if (leaves.isEmpty()) throw new IOException("no categories selected");
        Result result = new Result();

        // The pre-walk: honest totals for the progress line, and the byte counts the manifest
        // carries for the restore's free-space check. It must run before the manifest is written.
        Map<Cat, TarPack.Totals> totals = new EnumMap<>(Cat.class);
        for (Cat cat : leaves) {
            if (cat == Cat.SETTINGS) continue;
            checkCancel(cancel);
            if (progress != null) progress.onNote(cat, app.getString(R.string.shiroikuma_eim_progress_counting, app.getString(cat.shortLabelRes)));
            totals.put(cat, TarPack.count(rootOf(cat), cancel));
        }

        // The zip is closed (deflater released) at the end; the caller's stream is only flushed.
        ZipArchiveOutputStream zout = new ZipArchiveOutputStream(new BufferedOutputStream(new NonClosing(os), 256 * 1024));
        zout.setUseZip64(Zip64Mode.Always);
        zout.setLevel(Deflater.DEFAULT_COMPRESSION);

        // manifest FIRST
        JSONObject manifest = new JSONObject();
        try {
            manifest.put("format", FORMAT);
            manifest.put("version", VERSION);
            manifest.put("app", app.getPackageName());
            manifest.put("appVersion", appVersion(app));
            manifest.put("createdTs", System.currentTimeMillis());
            JSONArray categories = new JSONArray();
            if (leaves.contains(Cat.SETTINGS)) categories.put(Cat.SETTINGS.id);
            if (leaves.contains(Cat.DATA_HOME) || leaves.contains(Cat.DATA_USR)) categories.put(Cat.DATA.id);
            if (leaves.contains(Cat.DATA_HOME)) categories.put(Cat.DATA_HOME.id);
            if (leaves.contains(Cat.DATA_USR)) categories.put(Cat.DATA_USR.id);
            manifest.put("categories", categories);
            JSONObject bytes = new JSONObject();
            for (Map.Entry<Cat, TarPack.Totals> e : totals.entrySet()) bytes.put(e.getKey().id, e.getValue().bytes);
            manifest.put("bytes", bytes);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        putBytes(zout, MANIFEST, pretty(manifest));

        if (leaves.contains(Cat.SETTINGS)) {
            checkCancel(cancel);
            int n = exportSettings(app, zout, progress);
            result.lines.add(app.getString(R.string.shiroikuma_eim_line_settings, n));
            result.categories.add(Cat.SETTINGS);
        }

        // The tars: DEFLATE level 0 keeps them streamable and `unzip -p`-able.
        zout.setLevel(Deflater.NO_COMPRESSION);
        for (Cat cat : new Cat[]{Cat.DATA_HOME, Cat.DATA_USR}) {
            if (!leaves.contains(cat)) continue;
            checkCancel(cancel);
            TarPack.Totals expected = totals.get(cat);
            final long filesTotal = expected != null ? expected.entries : 0;
            final long bytesTotal = expected != null ? expected.bytes : 0;
            if (progress != null) progress.onProgress(cat, 0, filesTotal, 0, bytesTotal);
            ZipArchiveEntry entry = new ZipArchiveEntry(cat == Cat.DATA_HOME ? HOME_TAR : USR_TAR);
            zout.putArchiveEntry(entry);
            TarArchiveOutputStream tout = new TarArchiveOutputStream(new NonClosing(zout), 10240, "UTF-8");
            tout.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tout.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            final Cat current = cat;
            final long[] last = {0};
            TarPack.Totals done = TarPack.pack(rootOf(cat), prefixOf(cat), tout, (files, bytes) -> {
                // every 64 entries; the sender throttles further and the final line follows below
                if (progress != null && files - last[0] >= 64) {
                    last[0] = files;
                    progress.onProgress(current, files, filesTotal, bytes, bytesTotal);
                }
            }, cancel);
            tout.close();
            zout.closeArchiveEntry();
            if (progress != null) progress.onProgress(cat, done.entries, Math.max(filesTotal, done.entries), done.bytes, Math.max(bytesTotal, done.bytes));
            result.skipped += done.skipped;
            result.changed += done.changed;
            result.lines.add(app.getString(R.string.shiroikuma_eim_line_data, app.getString(cat.shortLabelRes),
                done.entries, humanSize(done.bytes)) + (done.skipped > 0 ? app.getString(R.string.shiroikuma_eim_line_skipped, done.skipped) : ""));
            result.categories.add(Cat.DATA);
            result.categories.add(cat);
        }

        checkCancel(cancel);
        zout.finish();
        zout.close();
        return result;
    }

    /**
     * Writes into {@code dir} as {@code <name>.part}, renamed to {@code <name>} only once the
     * archive is complete; any failure or cancel deletes the partial in the same {@code finally}.
     */
    @NonNull
    public static Result exportToDirectory(@NonNull Context context, @NonNull Set<Cat> cats, @NonNull File dir,
                                           @NonNull String fileName, @Nullable Progress progress, @Nullable Cancel cancel) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("not a directory: " + dir);
        File target = new File(dir, fileName);
        File partial = new File(dir, fileName + PART_SUFFIX);
        boolean done = false;
        try {
            Result r;
            try (OutputStream os = new FileOutputStream(partial)) {
                r = export(context, cats, os, progress, cancel);
            }
            if (!partial.renameTo(target)) throw new IOException("cannot rename " + partial.getName() + " to " + target.getName());
            done = true;
            r.file = target;
            return r;
        } finally {
            if (!done) {
                //noinspection ResultOfMethodCallIgnored
                partial.delete();
            }
        }
    }

    private static int exportSettings(@NonNull Context app, @NonNull ZipArchiveOutputStream zout, @Nullable Progress progress) throws IOException {
        int n = 0;
        JSONObject settings = new JSONObject();
        try {
            settings.put(PREFS_TERMUX, PrefsJson.dumpFile(app, PREFS_TERMUX, PREFS_TERMUX_SKIP));
            settings.put(PREFS_UI, PrefsJson.dumpFile(app, PREFS_UI, null));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        putBytes(zout, SETTINGS_JSON, pretty(settings));
        n++;
        List<File[]> files = settingsFiles();
        long total = files.size() + 1;
        if (progress != null) progress.onProgress(Cat.SETTINGS, n, total, 0, 0);
        for (File[] pair : files) {
            File src = pair[0];
            if (!src.isFile()) continue;
            putFile(zout, SETTINGS_DIR + pair[1].getPath(), src);
            n++;
            if (progress != null) progress.onProgress(Cat.SETTINGS, n, total, 0, 0);
        }
        if (progress != null) progress.onProgress(Cat.SETTINGS, n, n, 0, 0);
        return n;
    }

    /** {source file, name inside settings/} for every styling file that exists. */
    @NonNull
    private static List<File[]> settingsFiles() {
        List<File[]> out = new ArrayList<>();
        out.add(new File[]{TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE, new File("termux.properties")});
        out.add(new File[]{TermuxConstants.TERMUX_PROPERTIES_SECONDARY_FILE, new File("config/termux.properties")});
        out.add(new File[]{TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE, new File("colors.properties")});
        out.add(new File[]{TermuxConstants.TERMUX_FONT_FILE, new File("font.ttf")});
        File fontsDir = new File(TermuxConstants.TERMUX_DATA_HOME_DIR, "fonts");
        File[] fonts = fontsDir.listFiles();
        if (fonts != null) {
            List<File> sorted = new ArrayList<>();
            for (File f : fonts) if (f.isFile()) sorted.add(f);
            Collections.sort(sorted, (a, b) -> a.getName().compareTo(b.getName()));
            for (File f : sorted) out.add(new File[]{f, new File("fonts/" + f.getName())});
        }
        return out;
    }

    @NonNull
    private static byte[] pretty(@NonNull JSONObject o) throws IOException {
        try {
            return o.toString(2).getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private static void putBytes(@NonNull ZipArchiveOutputStream zout, @NonNull String name, @NonNull byte[] data) throws IOException {
        ZipArchiveEntry e = new ZipArchiveEntry(name);
        zout.putArchiveEntry(e);
        zout.write(data);
        zout.closeArchiveEntry();
    }

    private static void putFile(@NonNull ZipArchiveOutputStream zout, @NonNull String name, @NonNull File src) throws IOException {
        ZipArchiveEntry e = new ZipArchiveEntry(name);
        zout.putArchiveEntry(e);
        try (InputStream in = new FileInputStream(src)) {
            copy(in, zout);
        }
        zout.closeArchiveEntry();
    }

    /** A tar stream must not close the zip it sits in. */
    private static final class NonClosing extends FilterOutputStream {
        NonClosing(@NonNull OutputStream out) {
            super(out);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    // ---- import --------------------------------------------------------------------------------

    /**
     * Streams a backup ZIP from {@code in}: the categories present (manifest) that are also in
     * {@code wanted} are restored — settings straight away, data into staging, swapped in only
     * after the archive has been read to its end. The returned lines are per category.
     */
    @NonNull
    public static Result importZip(@NonNull Context context, @NonNull InputStream in, @NonNull Set<Cat> wanted,
                                   @Nullable Progress progress, @Nullable Cancel cancel) throws IOException {
        if (!supported()) throw new IOException("backup needs Android 8.0 or newer");
        Context app = context.getApplicationContext();
        Set<Cat> wantedLeaves = leaves(wanted);
        ZipArchiveInputStream zin = new ZipArchiveInputStream(new BufferedInputStream(new NonClosingIn(in), 256 * 1024), "UTF-8", true, true);
        try {
            return importZip(app, zin, wantedLeaves, progress, cancel);
        } finally {
            zin.close(); // releases the inflater; the caller's stream stays open for them to close
        }
    }

    private static Result importZip(@NonNull Context app, @NonNull ZipArchiveInputStream zin, @NonNull Set<Cat> wantedLeaves,
                                    @Nullable Progress progress, @Nullable Cancel cancel) throws IOException {
        Result result = new Result();
        ZipArchiveEntry first = zin.getNextEntry();
        if (first == null || !MANIFEST.equals(first.getName())) throw new IOException("not a " + FORMAT + " backup (no manifest first)");
        JSONObject manifest;
        try {
            manifest = new JSONObject(new String(readAll(zin), StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IOException("unreadable manifest", e);
        }
        if (!FORMAT.equals(manifest.optString("format"))) throw new IOException("not a " + FORMAT + " backup");
        if (manifest.optInt("version", 1) > VERSION) throw new IOException("backup format " + manifest.optInt("version") + " is newer than this app reads (" + VERSION + ")");
        Set<Cat> present = EnumSet.noneOf(Cat.class);
        JSONArray cats = manifest.optJSONArray("categories");
        if (cats != null) {
            for (int i = 0; i < cats.length(); i++) {
                Cat c = Cat.byId(cats.optString(i));
                if (c != null) present.add(c);
            }
        }
        Set<Cat> todo = EnumSet.copyOf(leaves(present));
        todo.retainAll(wantedLeaves);
        if (todo.isEmpty()) throw new IOException("archive carries none of the requested categories");

        // Free space, up front, from the manifest: a second copy of every restored tree must fit
        // beside the live one.
        long need = 0;
        JSONObject bytes = manifest.optJSONObject("bytes");
        if (bytes != null) {
            if (todo.contains(Cat.DATA_HOME)) need += bytes.optLong(Cat.DATA_HOME.id, 0);
            if (todo.contains(Cat.DATA_USR)) need += bytes.optLong(Cat.DATA_USR.id, 0);
        }
        if (need > 0) {
            need += need / 20 + 64L * 1024 * 1024; // metadata slack
            long have = availableBytes(TermuxConstants.TERMUX_FILES_DIR);
            if (have >= 0 && have < need) throw new IOException("not enough space (need " + humanSize(need) + ", have " + humanSize(have) + ")");
        }

        // The device's own answer to allow-external-apps, captured before anything is written.
        boolean deviceAllowsExternal = deviceAllowsExternalApps();

        File homeStaging = new File(TermuxConstants.TERMUX_FILES_DIR, "home-staging");
        File usrStaging = TermuxConstants.TERMUX_STAGING_PREFIX_DIR;
        Map<Cat, TarUnpack.Totals> staged = new EnumMap<>(Cat.class);
        int prefsKeys = 0;
        int settingsFiles = 0;
        boolean settingsSeen = false;
        List<String> notes = new ArrayList<>();
        boolean success = false;
        try {
            ZipArchiveEntry e;
            while ((e = zin.getNextEntry()) != null) {
                checkCancel(cancel);
                String name = e.getName();
                if (e.isDirectory()) continue;
                if (SETTINGS_JSON.equals(name)) {
                    if (!todo.contains(Cat.SETTINGS)) continue;
                    settingsSeen = true;
                    JSONObject settings;
                    try {
                        settings = new JSONObject(new String(readAll(zin), StandardCharsets.UTF_8));
                    } catch (JSONException ex) {
                        throw new IOException("unreadable settings.json", ex);
                    }
                    JSONObject termux = settings.optJSONObject(PREFS_TERMUX);
                    if (termux != null) prefsKeys += PrefsJson.mergeFile(app, PREFS_TERMUX, termux, PREFS_TERMUX_SKIP);
                    JSONObject ui = settings.optJSONObject(PREFS_UI);
                    if (ui != null) prefsKeys += PrefsJson.mergeFile(app, PREFS_UI, ui, null);
                    if (progress != null) progress.onProgress(Cat.SETTINGS, 1, 0, 0, 0);
                } else if (name.startsWith(SETTINGS_DIR)) {
                    if (!todo.contains(Cat.SETTINGS)) continue;
                    settingsSeen = true;
                    File dest = settingsDestination(name.substring(SETTINGS_DIR.length()));
                    if (dest == null) continue;
                    byte[] data = readAll(zin);
                    if (dest.getName().equals("termux.properties")) {
                        String[] tightened = {null};
                        data = tightenAllowExternalApps(data, deviceAllowsExternal, tightened);
                        if (tightened[0] != null) notes.add(tightened[0]);
                    }
                    writeFile(dest, data);
                    settingsFiles++;
                    if (progress != null) progress.onProgress(Cat.SETTINGS, 1 + settingsFiles, 0, 0, 0);
                } else if (HOME_TAR.equals(name) || USR_TAR.equals(name)) {
                    Cat cat = HOME_TAR.equals(name) ? Cat.DATA_HOME : Cat.DATA_USR;
                    if (!todo.contains(cat)) continue;
                    File staging = cat == Cat.DATA_HOME ? homeStaging : usrStaging;
                    deleteTree(staging);
                    final long filesTotal = 0;
                    final long bytesTotal = bytes != null ? bytes.optLong(cat.id, 0) : 0;
                    if (progress != null) progress.onProgress(cat, 0, filesTotal, 0, bytesTotal);
                    TarArchiveInputStream tin = new TarArchiveInputStream(new NonClosingIn(zin), 10240, "UTF-8");
                    final long[] last = {0};
                    TarUnpack.Totals t = TarUnpack.unpack(tin, prefixOf(cat), staging, (files, b) -> {
                        if (progress != null && files - last[0] >= 64) {
                            last[0] = files;
                            progress.onProgress(cat, files, filesTotal, b, bytesTotal);
                        }
                    }, cancel);
                    if (progress != null) progress.onProgress(cat, t.entries, t.entries, t.bytes, Math.max(bytesTotal, t.bytes));
                    staged.put(cat, t);
                }
            }
            // Every byte read: the archive is complete. Only now does live data move.
            if (!staged.isEmpty()) {
                if (progress != null) progress.onNote(null, app.getString(R.string.shiroikuma_eim_progress_stopping));
                ProcessReaper.run(app);
                for (Cat cat : new Cat[]{Cat.DATA_HOME, Cat.DATA_USR}) {
                    if (!staged.containsKey(cat)) continue;
                    if (progress != null) progress.onNote(cat, app.getString(R.string.shiroikuma_eim_progress_swapping, app.getString(cat.shortLabelRes)));
                    swap(cat == Cat.DATA_HOME ? homeStaging : usrStaging, rootOf(cat));
                }
                if (staged.containsKey(Cat.DATA_HOME)) {
                    // The restored home carries its own termux.properties: same tighten-only rule.
                    for (File f : new File[]{TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE, TermuxConstants.TERMUX_PROPERTIES_SECONDARY_FILE}) {
                        if (!f.isFile()) continue;
                        byte[] data = readFile(f);
                        String[] tightened = {null};
                        byte[] fixed = tightenAllowExternalApps(data, deviceAllowsExternal, tightened);
                        if (tightened[0] != null) {
                            writeFile(f, fixed);
                            notes.add(tightened[0]);
                        }
                    }
                }
            }
            success = true;
        } finally {
            if (!success) {
                deleteTree(homeStaging);
                deleteTree(usrStaging);
            }
        }

        if (settingsSeen) {
            result.lines.add(app.getString(R.string.shiroikuma_eim_line_settings_restored, prefsKeys, settingsFiles));
            result.categories.add(Cat.SETTINGS);
        }
        for (Cat cat : new Cat[]{Cat.DATA_HOME, Cat.DATA_USR}) {
            TarUnpack.Totals t = staged.get(cat);
            if (t == null) continue;
            result.lines.add(app.getString(R.string.shiroikuma_eim_line_data_restored, app.getString(cat.shortLabelRes), t.entries, humanSize(t.bytes)));
            result.categories.add(Cat.DATA);
            result.categories.add(cat);
        }
        for (String note : new LinkedHashSet<>(notes)) result.lines.add(note);
        return result;
    }

    /** Where a {@code settings/…} member goes; null for a name we do not know. */
    @Nullable
    private static File settingsDestination(@NonNull String rel) {
        if (rel.contains("..")) return null;
        switch (rel) {
            case "termux.properties":
                return TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
            case "config/termux.properties":
                return TermuxConstants.TERMUX_PROPERTIES_SECONDARY_FILE;
            case "colors.properties":
                return TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
            case "font.ttf":
                return TermuxConstants.TERMUX_FONT_FILE;
            default:
                if (rel.startsWith("fonts/") && rel.indexOf('/', 6) < 0 && rel.length() > 6) {
                    return new File(new File(TermuxConstants.TERMUX_DATA_HOME_DIR, "fonts"), rel.substring(6));
                }
                return null;
        }
    }

    /**
     * The tighten-only rule: {@code allow-external-apps=true} from an archive is written as
     * {@code false} unless the device already has it on — a restore must never silently open
     * the RUN_COMMAND door. Returns the (possibly rewritten) bytes; {@code note[0]} says so.
     */
    @NonNull
    static byte[] tightenAllowExternalApps(@NonNull byte[] data, boolean deviceAllows, @NonNull String[] note) {
        if (deviceAllows) return data;
        String text = new String(data, StandardCharsets.UTF_8);
        Matcher m = ALLOW_EXTERNAL_APPS.matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;
        while (m.find()) {
            if ("true".equalsIgnoreCase(m.group(1))) {
                m.appendReplacement(sb, Matcher.quoteReplacement("# tightened by 白い熊 Termux restore (was true):\nallow-external-apps=false"));
                changed = true;
            }
        }
        m.appendTail(sb);
        if (!changed) return data;
        note[0] = "allow-external-apps kept off (the device did not have it on)";
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** True when either live properties file already says {@code allow-external-apps=true}. */
    static boolean deviceAllowsExternalApps() {
        for (File f : new File[]{TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE, TermuxConstants.TERMUX_PROPERTIES_SECONDARY_FILE}) {
            if (!f.isFile()) continue;
            try {
                Matcher m = ALLOW_EXTERNAL_APPS.matcher(new String(readFile(f), StandardCharsets.UTF_8));
                boolean value = false;
                while (m.find()) value = "true".equalsIgnoreCase(m.group(1)); // the last line wins, as for the parser
                if (value) return true;
            } catch (IOException ignored) {
            }
        }
        return false;
    }

    /**
     * {@code live} → {@code live.old}, {@code staging} → {@code live}, then the old tree is
     * deleted: a crash between the two renames leaves a valid tree under one name or the other.
     */
    private static void swap(@NonNull File staging, @NonNull File live) throws IOException {
        File old = new File(live.getPath() + ".old");
        deleteTree(old);
        if (exists(live) && !live.renameTo(old)) throw new IOException("cannot move " + live + " aside");
        if (!staging.renameTo(live)) {
            // put it back rather than leave no tree at all
            if (exists(old)) //noinspection ResultOfMethodCallIgnored
                old.renameTo(live);
            throw new IOException("cannot move " + staging + " into place");
        }
        deleteTree(old);
    }

    private static boolean exists(@NonNull File f) {
        return TarPack.lstat(f) != null;
    }

    /** Deletes a tree without following symlinks (a link is unlinked, never descended). */
    public static void deleteTree(@NonNull File f) throws IOException {
        StructStat st = TarPack.lstat(f);
        if (st == null) return;
        if (OsConstants.S_ISDIR(st.st_mode)) {
            // A read-only directory cannot have its children unlinked: open it up first.
            if ((st.st_mode & 0700) != 0700) {
                try {
                    Os.chmod(f.getPath(), 0700);
                } catch (ErrnoException ignored) {
                }
            }
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteTree(c);
        }
        try {
            Os.remove(f.getPath());
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.ENOENT) throw new IOException("cannot delete " + f + ": " + e.getMessage(), e);
        }
    }

    private static long availableBytes(@NonNull File dir) {
        try {
            File probe = dir.isDirectory() ? dir : dir.getParentFile();
            if (probe == null) return -1;
            return new StatFs(probe.getPath()).getAvailableBytes();
        } catch (Exception e) {
            return -1;
        }
    }

    // ---- roots ---------------------------------------------------------------------------------

    @NonNull
    static File rootOf(@NonNull Cat cat) {
        return cat == Cat.DATA_HOME ? TermuxConstants.TERMUX_HOME_DIR : TermuxConstants.TERMUX_PREFIX_DIR;
    }

    @NonNull
    static String prefixOf(@NonNull Cat cat) {
        return cat == Cat.DATA_HOME ? "home" : "usr";
    }

    // ---- small helpers -------------------------------------------------------------------------

    private static void checkCancel(@Nullable Cancel cancel) throws CancelledException {
        if (cancel != null && cancel.isCancelled()) throw new CancelledException();
    }

    @NonNull
    private static String appVersion(@NonNull Context app) {
        try {
            PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    @NonNull
    private static byte[] readAll(@NonNull InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        copy(in, bos);
        return bos.toByteArray();
    }

    @NonNull
    private static byte[] readFile(@NonNull File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        }
    }

    private static void writeFile(@NonNull File dest, @NonNull byte[] data) throws IOException {
        File dir = dest.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File tmp = new File(dest.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(dest)) throw new IOException("cannot replace " + dest);
    }

    private static void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /** A tar reader must not close the zip it sits in. */
    private static final class NonClosingIn extends java.io.FilterInputStream {
        NonClosingIn(@NonNull InputStream in) {
            super(in);
        }

        @Override
        public void close() {
        }
    }

    static void log(@NonNull String message, @Nullable Throwable t) {
        if (t != null) Logger.logStackTraceWithMessage(LOG_TAG, message, t);
        else Logger.logInfo(LOG_TAG, message);
    }
}
