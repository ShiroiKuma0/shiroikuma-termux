package com.termux.shiroikuma.backup;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * shiroikuma-termux (Phase 4b): the export directory — an ABSOLUTE path, written with plain
 * {@link java.io.File}.
 *
 * <p>Termux declares {@code MANAGE_EXTERNAL_STORAGE} (its {@code termux-setup-storage} needs it), so
 * unlike the SAF-only sister apps this one never writes through a {@code DocumentFile}: the SAF
 * tree picker is used only to CHOOSE the folder, and its tree URI is resolved here to a path —
 * {@code primary:Foo/Bar} → {@code /storage/emulated/0/Foo/Bar}, a removable volume
 * {@code XXXX-XXXX:Foo} → {@code /storage/XXXX-XXXX/Foo}. Both the path and the original URI are
 * kept in the device-local prefs file {@value #PREFS}, which is never part of a backup.
 *
 * <p>Whether the path may actually be written is {@link #hasStorageAccess}: All-files access on
 * API 30+ ({@link Environment#isExternalStorageManager()}), the legacy read/write pair below.
 * The contract's reserved reply {@code ERROR:no-storage-access} keys on exactly that test — it is
 * checked up front, never discovered by failing.
 */
public final class ExportDir {

    public static final String PREFS = "shiroikuma_eximport";
    public static final String KEY_DIR = "export_dir";
    public static final String KEY_URI = "export_dir_uri";

    private static final String EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents";

    private ExportDir() {
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The configured absolute path, or null when none has been chosen. */
    @Nullable
    public static String path(@NonNull Context context) {
        String p = prefs(context).getString(KEY_DIR, null);
        return p == null || p.isEmpty() ? null : p;
    }

    /** The configured directory as a File (it may not exist yet), or null. */
    @Nullable
    public static File dir(@NonNull Context context) {
        String p = path(context);
        return p == null ? null : new File(p);
    }

    /** The tree URI the directory was picked as — where the picker opens next time. */
    @Nullable
    public static Uri uri(@NonNull Context context) {
        String u = prefs(context).getString(KEY_URI, null);
        return u == null || u.isEmpty() ? null : Uri.parse(u);
    }

    /**
     * Remembers a folder picked with {@code ACTION_OPEN_DOCUMENT_TREE}. Returns false when the tree
     * cannot be named as a path (a cloud provider, say) — nothing is stored then, because a path
     * we cannot write is not an export directory.
     */
    public static boolean set(@NonNull Context context, @NonNull Uri treeUri) {
        String path = resolve(treeUri);
        if (path == null) return false;
        try {
            // Not needed for java.io.File, but harmless, and it keeps the picker opening there.
            context.getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        // commit(): the gate-style rule — a value 白い熊 just set must not be lost to a force-stop.
        prefs(context).edit().putString(KEY_DIR, path).putString(KEY_URI, treeUri.toString()).commit();
        return true;
    }

    /**
     * The absolute path of an {@code com.android.externalstorage.documents} tree: the primary
     * volume under {@link Environment#getExternalStorageDirectory()}, any other volume under
     * {@code /storage/<volume>}. Null for every other provider.
     */
    @Nullable
    public static String resolve(@Nullable Uri treeUri) {
        if (treeUri == null || !EXTERNAL_STORAGE_AUTHORITY.equals(treeUri.getAuthority())) return null;
        String docId;
        try {
            docId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception e) {
            return null;
        }
        if (docId == null) return null;
        int colon = docId.indexOf(':');
        if (colon < 0) return null;
        String volume = docId.substring(0, colon);
        String rel = docId.substring(colon + 1);
        while (rel.startsWith("/")) rel = rel.substring(1);
        while (rel.endsWith("/")) rel = rel.substring(0, rel.length() - 1);
        String base;
        if ("primary".equals(volume)) {
            base = Environment.getExternalStorageDirectory().getAbsolutePath();
        } else if (volume.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) {
            base = "/storage/" + volume;
        } else {
            return null;
        }
        return rel.isEmpty() ? base : base + "/" + rel;
    }

    /**
     * May this app write an arbitrary path on external storage right now? All-files access on
     * API 30+, the legacy storage pair below it.
     */
    public static boolean hasStorageAccess(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /** The one grant the red row line asks for: All-files access, missing on API 30+. */
    public static boolean needsAllFilesGrant() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager();
    }

    // ---- what is already there ---------------------------------------------------------------

    /** {@code shiroikuma-termux_<stamp>.zip} — the family file name (contract §1). */
    public static boolean isExportFileName(@Nullable String name) {
        return name != null && name.startsWith(TermuxBackup.EXPORT_PREFIX) && name.endsWith(".zip");
    }

    /** Every backup of ours in the directory, newest first (by name = by stamp, then by mtime). */
    @NonNull
    public static List<File> listExports(@Nullable File dir) {
        List<File> out = new ArrayList<>();
        if (dir == null) return out;
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Exception e) {
            return out;
        }
        if (files == null) return out;
        for (File f : files) {
            if (f.isFile() && isExportFileName(f.getName())) out.add(f);
        }
        Collections.sort(out, (a, b) -> {
            int byName = b.getName().compareTo(a.getName());
            return byName != 0 ? byName : Long.compare(b.lastModified(), a.lastModified());
        });
        return out;
    }

    @Nullable
    public static File newestExport(@Nullable File dir) {
        List<File> all = listExports(dir);
        return all.isEmpty() ? null : all.get(0);
    }
}
