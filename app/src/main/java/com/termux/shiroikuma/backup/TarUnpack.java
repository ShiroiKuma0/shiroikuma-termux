package com.termux.shiroikuma.backup;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * shiroikuma-termux (Phase 4b): extracts a {@link TarPack} archive into a directory — the
 * staging tree ({@code files/home-staging}, {@code files/usr-staging}) that the restore swaps into
 * place only once the whole ZIP has been read. Names must start with the expected prefix
 * ({@code home/}, {@code usr/}); that first component is stripped, and anything that would
 * escape the target ({@code ..}, an absolute name) fails the whole restore.
 *
 * <p>Modes come back through {@code chmod} (files as they are written, directories at the end so a
 * read-only directory never blocks its own children), mtimes through {@link File#setLastModified},
 * symlinks through {@code symlink}, hard links through {@code link}. Ownership is not restored:
 * everything is ours anyway.
 */
public final class TarUnpack {

    public interface Progress {
        void onEntry(long entries, long bytes);
    }

    public static final class Totals {
        public long entries;
        public long bytes;
    }

    private static final int BUFFER = 256 * 1024;

    private static final class Deferred {
        final File dir;
        final int mode;
        final long mtime;

        Deferred(File dir, int mode, long mtime) {
            this.dir = dir;
            this.mode = mode;
            this.mtime = mtime;
        }
    }

    private TarUnpack() {
    }

    /**
     * Reads every entry of {@code in} (already positioned inside the ZIP member) into
     * {@code target}, which is created if missing. {@code prefix} is the archive's root name
     * without a slash ({@code home}).
     */
    @NonNull
    public static Totals unpack(@NonNull TarArchiveInputStream in, @NonNull String prefix, @NonNull File target,
                                @Nullable Progress progress, @Nullable TermuxBackup.Cancel cancel) throws IOException {
        Totals t = new Totals();
        List<Deferred> dirs = new ArrayList<>();
        byte[] buf = new byte[BUFFER];
        if (!target.isDirectory() && !target.mkdirs()) throw new IOException("cannot create " + target);
        String targetPath = target.getCanonicalPath();

        TarArchiveEntry e;
        while ((e = in.getNextEntry()) != null) {
            if (cancel != null && cancel.isCancelled()) throw new TermuxBackup.CancelledException();
            String rel = strip(e.getName(), prefix);
            if (rel == null) throw new IOException("unexpected entry outside " + prefix + "/: " + e.getName());
            int mode = e.getMode() & 07777;
            long mtime = mtimeOf(e);
            if (rel.isEmpty()) {
                // the root itself
                dirs.add(new Deferred(target, mode, mtime));
                t.entries++;
                continue;
            }
            File dest = new File(target, rel);
            if (!dest.getCanonicalPath().startsWith(targetPath + File.separator)) {
                throw new IOException("entry escapes the target: " + e.getName());
            }
            File parent = dest.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent);

            if (e.isDirectory()) {
                if (!dest.isDirectory()) {
                    removeIfExists(dest);
                    if (!dest.mkdir()) throw new IOException("cannot create " + dest);
                }
                dirs.add(new Deferred(dest, mode, mtime));
            } else if (e.isSymbolicLink()) {
                removeIfExists(dest);
                try {
                    Os.symlink(e.getLinkName(), dest.getPath());
                } catch (ErrnoException ex) {
                    throw new IOException("symlink " + dest + " -> " + e.getLinkName() + ": " + ex.getMessage(), ex);
                }
            } else if (e.isLink()) {
                String linkRel = strip(e.getLinkName(), prefix);
                if (linkRel == null || linkRel.isEmpty()) throw new IOException("hard link outside the tree: " + e.getLinkName());
                File existing = new File(target, linkRel);
                removeIfExists(dest);
                try {
                    Os.link(existing.getPath(), dest.getPath());
                } catch (ErrnoException ex) {
                    throw new IOException("link " + dest + " = " + existing + ": " + ex.getMessage(), ex);
                }
            } else if (e.isCharacterDevice() || e.isBlockDevice() || e.isFIFO()) {
                // never written by TarPack; some other tar's — skip quietly
                continue;
            } else {
                removeIfExists(dest);
                long size = e.getSize();
                try (FileOutputStream out = new FileOutputStream(dest)) {
                    long remaining = size;
                    while (remaining > 0) {
                        int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                        if (n < 0) throw new IOException("archive truncated inside " + e.getName());
                        out.write(buf, 0, n);
                        remaining -= n;
                    }
                }
                try {
                    Os.chmod(dest.getPath(), mode);
                } catch (ErrnoException ex) {
                    throw new IOException("chmod " + dest + ": " + ex.getMessage(), ex);
                }
                //noinspection ResultOfMethodCallIgnored
                dest.setLastModified(mtime);
                t.bytes += size;
            }
            t.entries++;
            if (progress != null) progress.onEntry(t.entries, t.bytes);
        }

        // Deepest first: a child's creation bumped its parent's mtime, and a read-only parent
        // must not be locked before its children are in.
        for (int i = dirs.size() - 1; i >= 0; i--) {
            Deferred d = dirs.get(i);
            try {
                Os.chmod(d.dir.getPath(), d.mode == 0 ? 0700 : d.mode);
            } catch (ErrnoException ex) {
                throw new IOException("chmod " + d.dir + ": " + ex.getMessage(), ex);
            }
            //noinspection ResultOfMethodCallIgnored
            d.dir.setLastModified(d.mtime);
        }
        return t;
    }

    /** {@code home/a/b} → {@code a/b}; {@code home/} or {@code home} → ""; anything else → null. */
    @Nullable
    static String strip(@Nullable String name, @NonNull String prefix) {
        if (name == null) return null;
        String n = name;
        while (n.startsWith("/")) n = n.substring(1);
        if (n.equals(prefix) || n.equals(prefix + "/")) return "";
        if (!n.startsWith(prefix + "/")) return null;
        String rel = n.substring(prefix.length() + 1);
        while (rel.endsWith("/")) rel = rel.substring(0, rel.length() - 1);
        for (String part : rel.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return null;
        }
        return rel;
    }

    private static long mtimeOf(@NonNull TarArchiveEntry e) {
        Date d = e.getModTime();
        return d == null ? System.currentTimeMillis() : d.getTime();
    }

    /** Removes a file, symlink or (empty) directory sitting where an entry must go. */
    private static void removeIfExists(@NonNull File f) throws IOException {
        StructStat st;
        try {
            st = Os.lstat(f.getPath());
        } catch (ErrnoException e) {
            return; // nothing there
        }
        if (OsConstants.S_ISDIR(st.st_mode)) {
            TermuxBackup.deleteTree(f);
        } else {
            try {
                Os.remove(f.getPath());
            } catch (ErrnoException e) {
                throw new IOException("cannot remove " + f + ": " + e.getMessage(), e);
            }
        }
    }
}
