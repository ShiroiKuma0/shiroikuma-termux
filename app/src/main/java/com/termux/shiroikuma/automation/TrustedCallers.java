package com.termux.shiroikuma.automation;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * shiroikuma-termux: the "Trusted callers" list — which apps may run commands in Termux, and
 * which may restore its data, chosen by 白い熊 app by app in the UI page's External apps section.
 *
 * <h3>Why</h3>
 *
 * The {@code RUN_COMMAND} permission is {@code dangerous}: one tap grants it to any app that asks.
 * Once Termux can be given root on demand, whoever can make Termux run a command can get root, so
 * the door must admit only chosen callers. The family's apps are signed with different keys, so a
 * signature-level permission is not possible; hence a pinned list (package name + the SHA-256 of
 * its signing certificate at the moment it was switched on).
 *
 * <h3>Why not the result PendingIntent's creator</h3>
 *
 * A service start carries no sender. A PendingIntent's creator proves who MADE the token, not who
 * SENT the request — and any app with notification access can read the PendingIntents of another
 * app's notifications and attach one to a request of its own. So identity is taken where the
 * kernel reports it: the Binder call of {@link AutomationProvider} ({@code run_command}), and the
 * provider hands the request on to {@code RunCommandService} with {@link #NONCE}, a secret of this
 * process. While the setting is on, {@code RunCommandService} refuses every request without it.
 *
 * <h3>The doors behind this list (while {@link #isEnforced} — default ON)</h3>
 * <ul>
 * <li>{@code AutomationProvider.run_command} — {@link #ROLE_RUN};</li>
 * <li>{@code RunCommandService} — only with the nonce ({@link #refuseService});</li>
 * <li>{@code TermuxOpenReceiver$ContentProvider.openFile} — a holder of the permission could
 *     otherwise WRITE any file under Termux's files ({@code .bashrc}, {@code $PREFIX/bin}): access
 *     without a URI grant needs {@link #ROLE_RUN} ({@link #refuseFileAccess});</li>
 * <li>{@code AutomationProvider.import} — {@link #ROLE_RESTORE}, on top of the pinned
 *     {@link AutomationCallers}.</li>
 * </ul>
 * With the setting off everything behaves as upstream's: {@code allow-external-apps} and the
 * Android permission decide.
 *
 * <p>Prefs file {@code shiroikuma_trusted} — device-local, written with {@code commit()}, NEVER
 * part of an export: a restore must not be able to grant trust.
 */
public final class TrustedCallers {

    public static final String ROLE_RUN = "run";
    public static final String ROLE_RESTORE = "restore";

    private static final String PREFS = "shiroikuma_trusted";
    private static final String KEY_ENFORCE = "enforce";

    /** The extra under which the provider passes {@link #NONCE} to {@code RunCommandService}. */
    public static final String EXTRA_NONCE = "com.termux.shiroikuma.TRUSTED_NONCE";
    /** Known to this process only; a new one at every process start. */
    public static final String NONCE = newNonce();

    /** Refusals: one line each, under Termux's own files; never named *.log (EMUI deletes those). */
    private static final String TRACE_DIR = "shiroikuma";
    private static final String TRACE_FILE = "trusted-callers-refused.txt";
    private static final long TRACE_MAX_BYTES = 256 * 1024;
    private static final Object TRACE_LOCK = new Object();

    private TrustedCallers() {
    }

    @NonNull
    private static String newNonce() {
        byte[] raw = new byte[24];
        new SecureRandom().nextBytes(raw);
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- the setting and the list ---------------------------------------------------------------

    public static boolean isEnforced(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_ENFORCE, true);
    }

    public static void setEnforced(@NonNull Context context, boolean enforce) {
        prefs(context).edit().putBoolean(KEY_ENFORCE, enforce).commit();
    }

    @NonNull
    private static String key(@NonNull String role, @NonNull String pkg) {
        return role + ":" + pkg;
    }

    /** Switched on for this role — whether or not its certificate still matches. */
    public static boolean isListed(@NonNull Context context, @NonNull String pkg, @NonNull String role) {
        return prefs(context).getString(key(role, pkg), null) != null;
    }

    /** The stored certificate no longer equals the installed app's: listed, but refused. */
    public static boolean certificateChanged(@NonNull Context context, @NonNull String pkg, @NonNull String role) {
        String stored = prefs(context).getString(key(role, pkg), null);
        if (stored == null) return false;
        String now = AutomationCallers.signingSha256(context, pkg);
        return now == null || !sameHash(stored, now);
    }

    /**
     * Switching on stores the package's PRESENT signing certificate; switching off forgets it.
     * {@code false} when the certificate cannot be read (the app is gone, or has several signers).
     */
    public static boolean setListed(@NonNull Context context, @NonNull String pkg, @NonNull String role, boolean on) {
        if (!on) {
            prefs(context).edit().remove(key(role, pkg)).commit();
            return true;
        }
        String now = AutomationCallers.signingSha256(context, pkg);
        if (now == null) return false;
        return prefs(context).edit().putString(key(role, pkg), now).commit();
    }

    /**
     * The rows of the list: every installed app that requests the RUN_COMMAND permission (granted
     * or not) and every package still stored, sorted by package name; the shared-UID family runs
     * as Termux anyway and is left out.
     */
    @NonNull
    public static List<String> candidates(@NonNull Context context) {
        TreeSet<String> out = new TreeSet<>();
        PackageManager pm = context.getPackageManager();
        int myUid = context.getApplicationInfo().uid;
        try {
            for (PackageInfo info : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
                if (info.requestedPermissions == null || info.applicationInfo == null) continue;
                if (info.applicationInfo.uid == myUid) continue;
                for (String p : info.requestedPermissions) {
                    if (TermuxConstants.PERMISSION_RUN_COMMAND.equals(p)) {
                        out.add(info.packageName);
                        break;
                    }
                }
            }
        } catch (RuntimeException e) {
            // the stored ones are still worth showing
        }
        for (Map.Entry<String, ?> e : prefs(context).getAll().entrySet()) {
            int colon = e.getKey().indexOf(':');
            if (colon > 0) out.add(e.getKey().substring(colon + 1));
        }
        return new ArrayList<>(out);
    }

    private static boolean sameHash(@NonNull String a, @NonNull String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    // ---- the checks -----------------------------------------------------------------------------

    /**
     * For a Binder door: {@code null} when the caller of the CURRENT transaction is listed for
     * {@code role} with an unchanged certificate, otherwise the {@code ERROR:} line. The uid is the
     * kernel's ({@link Binder#getCallingUid()}); {@code declared} only names the package in the
     * message. Termux's own uid always passes. Does not look at {@link #isEnforced}.
     */
    @Nullable
    public static String verifyCaller(@NonNull Context context, @Nullable String declared, @NonNull String role) {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return null;
        String[] pkgs;
        try {
            pkgs = context.getPackageManager().getPackagesForUid(uid);
        } catch (RuntimeException e) {
            pkgs = null;
        }
        if (pkgs == null || pkgs.length == 0) return "ERROR:caller unknown";
        String changed = null;
        for (String pkg : pkgs) {
            String stored = prefs(context).getString(key(role, pkg), null);
            if (stored == null) continue;
            String now = AutomationCallers.signingSha256(context, pkg);
            if (now != null && sameHash(stored, now)) return null;
            changed = pkg;
        }
        if (changed != null) return "ERROR:caller certificate changed: " + changed;
        String name = declared != null && java.util.Arrays.asList(pkgs).contains(declared) ? declared : pkgs[0];
        return ROLE_RESTORE.equals(role) ? "ERROR:caller not trusted for restore: " + name : "ERROR:caller not trusted: " + name;
    }

    /** The package of the current Binder caller, for a trace line; never trusted for a decision. */
    @NonNull
    public static String callerName(@NonNull Context context, @Nullable String declared) {
        try {
            String[] pkgs = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
            if (pkgs != null && pkgs.length > 0) {
                if (declared != null && java.util.Arrays.asList(pkgs).contains(declared)) return declared;
                return pkgs[0];
            }
        } catch (RuntimeException e) {
            // fall through
        }
        return "uid " + Binder.getCallingUid();
    }

    /**
     * {@code RunCommandService}: {@code true} when this request must be refused — the setting is
     * on and the intent did not come through the provider (no nonce of this process). The refusal
     * is traced with the creator of the result PendingIntent, if one was attached.
     */
    public static boolean refuseService(@NonNull Context context, @NonNull Intent intent) {
        if (!isEnforced(context)) return false;
        String nonce = null;
        try {
            nonce = intent.getStringExtra(EXTRA_NONCE);
        } catch (RuntimeException e) {
            // an unreadable extras bundle carries no nonce
        }
        if (nonce != null && sameHash(nonce, NONCE)) return false;
        String who = "unknown sender";
        try {
            PendingIntent pi = intent.getParcelableExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT);
            if (pi != null && pi.getCreatorPackage() != null) who = "unknown sender (result PendingIntent by " + pi.getCreatorPackage() + ")";
        } catch (RuntimeException e) {
            // keep "unknown sender"
        }
        trace(context, "RunCommandService", who, "not sent through the trusted door (com.termux.automation run_command)");
        return true;
    }

    /**
     * The file-serving provider: {@code null} when the current Binder caller may open {@code path}
     * although it has no URI grant for it — the setting is off, or the caller is listed for
     * {@link #ROLE_RUN}; otherwise the reason (already traced).
     */
    @Nullable
    public static String refuseFileAccess(@NonNull Context context, @Nullable String declared, @NonNull String path, @NonNull String mode) {
        if (!isEnforced(context)) return null;
        String refused = verifyCaller(context, declared, ROLE_RUN);
        if (refused == null) return null;
        trace(context, "files provider", callerName(context, declared), refused.substring("ERROR:".length()) + " — " + mode + " " + path);
        return refused;
    }

    // ---- the trace ------------------------------------------------------------------------------

    @NonNull
    public static File traceFile(@NonNull Context context) {
        return new File(new File(context.getApplicationContext().getFilesDir(), TRACE_DIR), TRACE_FILE);
    }

    /** time TAB door TAB caller TAB reason. Never throws. */
    public static void trace(@NonNull Context context, @NonNull String door, @NonNull String who, @NonNull String reason) {
        synchronized (TRACE_LOCK) {
            try {
                File file = traceFile(context);
                File dir = file.getParentFile();
                if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
                if (file.length() > TRACE_MAX_BYTES) keepNewerHalf(file);
                String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                    + "\t" + door + "\t" + oneLine(who) + "\t" + oneLine(reason) + "\n";
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                // a refusal that cannot be written is still a refusal
            }
        }
    }

    @NonNull
    private static String oneLine(@NonNull String s) {
        return s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    private static void keepNewerHalf(@NonNull File file) throws Exception {
        byte[] tail;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long keep = TRACE_MAX_BYTES / 2;
            raf.seek(raf.length() - keep);
            tail = new byte[(int) keep];
            raf.readFully(tail);
        }
        int start = 0;
        while (start < tail.length && tail[start] != '\n') start++;
        start = Math.min(tail.length, start + 1);
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(tail, start, tail.length - start);
        }
    }

    /** The newest refusal, or {@code null} when there is none. */
    @Nullable
    public static String lastRefusal(@NonNull Context context) {
        synchronized (TRACE_LOCK) {
            File file = traceFile(context);
            if (!file.isFile() || file.length() == 0) return null;
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                long len = raf.length();
                int n = (int) Math.min(len, 2048);
                byte[] buf = new byte[n];
                raf.seek(len - n);
                raf.readFully(buf);
                String text = new String(buf, StandardCharsets.UTF_8).trim();
                int nl = text.lastIndexOf('\n');
                return (nl >= 0 ? text.substring(nl + 1) : text).replace("\t", " · ");
            } catch (Exception e) {
                return null;
            }
        }
    }
}
