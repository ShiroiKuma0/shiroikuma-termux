package com.termux.shiroikuma.backup;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * shiroikuma-termux (Phase 4b): writes one directory tree into a tar stream the way 白い熊's
 * {@code baktermux} script did with GNU tar — relative names under a prefix ({@code home/…},
 * {@code usr/…}), every entry's mode / uid / gid / mtime from {@code lstat}, directories, regular
 * files, symlinks (target from {@code readlink}), hard links (an inode map → {@code LF_LINK} to
 * the first name seen), and sockets / FIFOs / devices SKIPPED and counted (baktermux piped tar's
 * output through {@code sed '/socket ignored/d'}).
 *
 * <p>The tree is live while it is read (sessions may be running): a file that shrinks under us
 * is zero-padded to the size the header promised, one that grows is cut there, and one that can
 * no longer be opened is skipped — a walk is never aborted by the tree changing. {@link #count}
 * is the pre-walk that gives the progress line honest totals.
 */
public final class TarPack {

    /** Called after every entry written. */
    public interface Progress {
        void onEntry(long entries, long bytes);
    }

    /** What a walk produced (or, from {@link #count}, would produce). */
    public static final class Totals {
        /** Entries: directories, files, symlinks, hard links. */
        public long entries;
        /** Regular-file content bytes. */
        public long bytes;
        /** Sockets, FIFOs, devices, and anything that could not be stat'ed or opened. */
        public long skipped;
        /** Files whose size changed while being read. */
        public long changed;
    }

    private static final int BUFFER = 256 * 1024;

    private TarPack() {
    }

    /** The pre-walk: the same traversal, nothing written. */
    @NonNull
    public static Totals count(@NonNull File root, @Nullable TermuxBackup.Cancel cancel) throws IOException {
        Totals t = new Totals();
        if (lstat(root) == null) return t;
        t.entries++;
        countDir(root, t, new HashSet<>(), cancel);
        return t;
    }

    private static void countDir(@NonNull File dir, @NonNull Totals t, @NonNull Set<String> inodes,
                                 @Nullable TermuxBackup.Cancel cancel) throws IOException {
        String[] names = dir.list();
        if (names == null) return;
        for (String name : names) {
            if (cancel != null && cancel.isCancelled()) throw new TermuxBackup.CancelledException();
            File f = new File(dir, name);
            StructStat st = lstat(f);
            if (st == null) {
                t.skipped++;
                continue;
            }
            int mode = st.st_mode;
            if (OsConstants.S_ISDIR(mode)) {
                t.entries++;
                countDir(f, t, inodes, cancel);
            } else if (OsConstants.S_ISREG(mode)) {
                t.entries++;
                // The same inode map as the walk: a second hard link carries no data.
                if (st.st_nlink <= 1 || inodes.add(st.st_dev + ":" + st.st_ino)) t.bytes += st.st_size;
            } else if (OsConstants.S_ISLNK(mode)) {
                t.entries++;
            } else {
                t.skipped++;
            }
        }
    }

    /**
     * Writes {@code root} and everything under it as {@code prefix/…} (prefix without a slash,
     * e.g. {@code home}). The caller finishes/closes the tar stream.
     */
    @NonNull
    public static Totals pack(@NonNull File root, @NonNull String prefix, @NonNull TarArchiveOutputStream out,
                              @Nullable Progress progress, @Nullable TermuxBackup.Cancel cancel) throws IOException {
        Totals t = new Totals();
        StructStat st = lstat(root);
        if (st == null) return t;
        Map<String, String> inodes = new HashMap<>();
        byte[] buf = new byte[BUFFER];
        putDir(out, prefix + "/", st);
        t.entries++;
        if (progress != null) progress.onEntry(t.entries, t.bytes);
        packDir(root, prefix, out, t, inodes, buf, progress, cancel);
        return t;
    }

    private static void packDir(@NonNull File dir, @NonNull String arcDir, @NonNull TarArchiveOutputStream out,
                                @NonNull Totals t, @NonNull Map<String, String> inodes, @NonNull byte[] buf,
                                @Nullable Progress progress, @Nullable TermuxBackup.Cancel cancel) throws IOException {
        String[] names = dir.list();
        if (names == null) return;
        Arrays.sort(names);
        for (String name : names) {
            if (cancel != null && cancel.isCancelled()) throw new TermuxBackup.CancelledException();
            File f = new File(dir, name);
            String arcName = arcDir + "/" + name;
            StructStat st = lstat(f);
            if (st == null) {
                t.skipped++;
                continue;
            }
            int mode = st.st_mode;
            if (OsConstants.S_ISDIR(mode)) {
                putDir(out, arcName + "/", st);
                t.entries++;
                if (progress != null) progress.onEntry(t.entries, t.bytes);
                packDir(f, arcName, out, t, inodes, buf, progress, cancel);
            } else if (OsConstants.S_ISREG(mode)) {
                String key = st.st_nlink > 1 ? st.st_dev + ":" + st.st_ino : null;
                if (key != null) {
                    String first = inodes.get(key);
                    if (first != null) {
                        TarArchiveEntry e = new TarArchiveEntry(arcName, TarConstants.LF_LINK);
                        e.setLinkName(first);
                        fill(e, st);
                        e.setSize(0);
                        out.putArchiveEntry(e);
                        out.closeArchiveEntry();
                        t.entries++;
                        if (progress != null) progress.onEntry(t.entries, t.bytes);
                        continue;
                    }
                }
                // Open BEFORE the header goes out: a file we cannot read is skipped whole, never
                // promised in a header and then left short.
                FileInputStream in;
                try {
                    in = new FileInputStream(f);
                } catch (IOException e) {
                    t.skipped++;
                    continue;
                }
                try {
                    TarArchiveEntry e = new TarArchiveEntry(arcName, TarConstants.LF_NORMAL);
                    fill(e, st);
                    long size = st.st_size;
                    e.setSize(size);
                    out.putArchiveEntry(e);
                    long remaining = size;
                    while (remaining > 0) {
                        int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                        if (n < 0) break;
                        out.write(buf, 0, n);
                        remaining -= n;
                    }
                    if (remaining > 0) {
                        // It shrank while we read it: pad to the promised size so the archive stays
                        // well-formed (GNU tar reports "file changed as we read it" and does the same).
                        Arrays.fill(buf, (byte) 0);
                        while (remaining > 0) {
                            int n = (int) Math.min(buf.length, remaining);
                            out.write(buf, 0, n);
                            remaining -= n;
                        }
                        t.changed++;
                    } else if (in.read() >= 0) {
                        t.changed++; // it grew; the header size wins
                    }
                    out.closeArchiveEntry();
                    // Only a name that really went out may be the target of a later hard link.
                    if (key != null) inodes.put(key, arcName);
                    t.bytes += size;
                    t.entries++;
                    if (progress != null) progress.onEntry(t.entries, t.bytes);
                } finally {
                    in.close();
                }
            } else if (OsConstants.S_ISLNK(mode)) {
                String target;
                try {
                    target = Os.readlink(f.getPath());
                } catch (ErrnoException e) {
                    t.skipped++;
                    continue;
                }
                TarArchiveEntry e = new TarArchiveEntry(arcName, TarConstants.LF_SYMLINK);
                e.setLinkName(target);
                fill(e, st);
                e.setSize(0);
                out.putArchiveEntry(e);
                out.closeArchiveEntry();
                t.entries++;
                if (progress != null) progress.onEntry(t.entries, t.bytes);
            } else {
                // socket, FIFO, character / block device — "socket ignored"
                t.skipped++;
            }
        }
    }

    private static void putDir(@NonNull TarArchiveOutputStream out, @NonNull String arcName, @NonNull StructStat st) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(arcName, TarConstants.LF_DIR);
        fill(e, st);
        e.setSize(0);
        out.putArchiveEntry(e);
        out.closeArchiveEntry();
    }

    private static void fill(@NonNull TarArchiveEntry e, @NonNull StructStat st) {
        e.setMode(st.st_mode & 07777);
        e.setUserId((long) st.st_uid);
        e.setGroupId((long) st.st_gid);
        e.setModTime(st.st_mtime * 1000L);
    }

    @Nullable
    static StructStat lstat(@NonNull File f) {
        try {
            return Os.lstat(f.getPath());
        } catch (ErrnoException e) {
            return null;
        }
    }
}
