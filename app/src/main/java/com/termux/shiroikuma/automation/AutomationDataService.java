package com.termux.shiroikuma.automation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shiroikuma.backup.TermuxBackup;
import com.termux.shiroikuma.backup.TermuxBackup.Cat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * shiroikuma-termux (Phase 4b): where every export and import actually runs — the §1 broadcast
 * door's file export, the §2a provider door's descriptor export / import, and the in-app panel's
 * own runs, all in ONE foreground {@code dataSync} service with a partial wakelock.
 *
 * <p>Why a foreground service and never {@code goAsync()}: a tar of {@code usr} takes minutes,
 * and a manifest receiver must finish inside the broadcast window (~10 s / ~60 s) or the system
 * ANRs the app mid-export. EMUI additionally force-releases a background app's wakelock and
 * freezes the process, which yields a truncated archive under a success reply.
 *
 * <p>The recipe of the contract, in this order: <b>read the extras</b> (nothing can throw),
 * <b>go foreground inside a try</b> (a refusal is answered with the terminal reply —
 * {@code ERROR:no-foreground-start} only for {@code ForegroundServiceStartNotAllowedException},
 * matched by class NAME, when the app is not battery-exempt), <b>drain the descriptor handover in
 * the same try/finally</b> so it always has an owner, <b>then</b> the early returns (a stale job id
 * stops SILENTLY — that request already had its one reply). The "already running" flag is claimed
 * only after the promotion succeeded, so a refused start can never wedge later ones.
 *
 * <p>Exactly one terminal reply per job, guarded by an {@link AtomicBoolean}; the in-process
 * listener of the panel is told before the job is forgotten.
 */
public class AutomationDataService extends Service {

    private static final String LOG_TAG = "AutomationDataService";

    public static final String CHANNEL = "shiroikuma_backup";
    private static final int NOTIFICATION_ID = 9714;
    private static final long WAKELOCK_TIMEOUT_MS = 60 * 60 * 1000L;

    static final String EXTRA_JOB = "job";
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_DIR = "dir";
    static final int MODE_EXPORT_FILE = 1;
    static final int MODE_EXPORT_FD = 2;
    static final int MODE_IMPORT_FD = 3;

    /** The descriptor's way across: an Intent extra is duplicated by the system on delivery and
     * the copy's lifetime stops being ours; a map keyed by job id keeps exactly one owner. */
    private static final ConcurrentHashMap<String, ParcelFileDescriptor> HANDOVER = new ConcurrentHashMap<>();

    private volatile NotificationManager mNotifications;
    private volatile String mLastNotified;
    private volatile long mLastNotifiedAt;
    /** Workers in flight: stopForeground() is service-wide, so only the last one out demotes us. */
    private final AtomicInteger mActive = new AtomicInteger(0);

    // ---- starting ------------------------------------------------------------------------------

    /**
     * §1 (and the panel): export into {@code dir} as {@code shiroikuma-termux_<stamp>.zip}. The
     * reply trio may be empty for the panel, which listens through {@link AutomationJobs}.
     */
    public static void startFileExport(@NonNull Context context, @NonNull String jobId, @NonNull File dir,
                                       @Nullable String items, @Nullable String replyAction, @Nullable String replyPackage,
                                       @Nullable String replyId, @Nullable String progressAction) {
        Intent intent = base(context, jobId, MODE_EXPORT_FILE, items, replyAction, replyPackage, replyId, progressAction);
        intent.putExtra(EXTRA_DIR, dir.getAbsolutePath());
        startGuarded(context, intent, jobId);
    }

    /** §2a (and the panel's import): the payload moves through a descriptor we already own. */
    public static void startDescriptor(@NonNull Context context, @NonNull String jobId, @NonNull ParcelFileDescriptor fd,
                                       boolean importing, @Nullable String items, @Nullable String replyAction,
                                       @Nullable String replyPackage, @Nullable String progressAction) {
        HANDOVER.put(jobId, fd);
        Intent intent = base(context, jobId, importing ? MODE_IMPORT_FD : MODE_EXPORT_FD, items, replyAction, replyPackage, jobId, progressAction);
        try {
            startGuarded(context, intent, jobId);
        } catch (RuntimeException e) {
            HANDOVER.remove(jobId); // never strand the descriptor if the service could not start
            throw e;
        }
    }

    private static Intent base(@NonNull Context context, @NonNull String jobId, int mode, @Nullable String items,
                               @Nullable String replyAction, @Nullable String replyPackage, @Nullable String replyId,
                               @Nullable String progressAction) {
        Intent intent = new Intent(context, AutomationDataService.class);
        intent.putExtra(EXTRA_JOB, jobId);
        intent.putExtra(EXTRA_MODE, mode);
        intent.putExtra(AutomationProvider.KEY_ITEMS, items);
        intent.putExtra(AutomationProvider.KEY_REPLY_ACTION, replyAction);
        intent.putExtra(AutomationProvider.KEY_REPLY_PACKAGE, replyPackage);
        intent.putExtra(AutomationProvider.KEY_REPLY_ID, replyId);
        intent.putExtra(AutomationProvider.KEY_PROGRESS_ACTION, progressAction);
        return intent;
    }

    private static void startGuarded(@NonNull Context context, @NonNull Intent intent, @NonNull String jobId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    /**
     * The contract's reserved key, emitted only when BOTH hold: the throwable is
     * {@code ForegroundServiceStartNotAllowedException} (by NAME — it is API 31) and the app is not
     * already battery-exempt. Everything else keeps a descriptive line. (targetSdk 28 means the
     * platform will not throw this today; the recipe is kept so a later target does not regress.)
     */
    @NonNull
    static String startFailure(@NonNull Context context, @NonNull Throwable t) {
        if ("ForegroundServiceStartNotAllowedException".equals(t.getClass().getSimpleName())) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean exempt = pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && pm.isIgnoringBatteryOptimizations(context.getPackageName());
            if (!exempt) return "ERROR:no-foreground-start";
        }
        return "ERROR:cannot start export service: " + t.getClass().getSimpleName();
    }

    // ---- the service ---------------------------------------------------------------------------

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, final int startId) {
        // 1. Read the extras — microseconds, nothing can throw, no early return yet.
        final String jobId = intent == null ? null : intent.getStringExtra(EXTRA_JOB);
        final int mode = intent == null ? 0 : intent.getIntExtra(EXTRA_MODE, 0);
        final String dir = intent == null ? null : intent.getStringExtra(EXTRA_DIR);
        final String items = intent == null ? null : intent.getStringExtra(AutomationProvider.KEY_ITEMS);
        final String replyAction = intent == null ? null : intent.getStringExtra(AutomationProvider.KEY_REPLY_ACTION);
        final String replyPackage = intent == null ? null : intent.getStringExtra(AutomationProvider.KEY_REPLY_PACKAGE);
        final String replyId = intent == null ? null : intent.getStringExtra(AutomationProvider.KEY_REPLY_ID);
        final String progressAction = intent == null ? null : intent.getStringExtra(AutomationProvider.KEY_PROGRESS_ACTION);
        final boolean importing = mode == MODE_IMPORT_FD;
        // §1 echoes reply_id; §2a mirrors the job id into both names so one reader serves both doors.
        final String[] correlationExtras = mode == MODE_EXPORT_FILE
            ? new String[]{AutomationProvider.KEY_REPLY_ID}
            : new String[]{AutomationProvider.KEY_JOB_ID, AutomationProvider.KEY_REPLY_ID};
        final String correlationId = replyId == null ? "" : replyId;

        // Built before anything that can throw, so a failure to even start is still reported.
        final AtomicBoolean replied = new AtomicBoolean(false);
        final Replier reply = (result, lines) -> {
            if (!replied.compareAndSet(false, true)) return;
            if (jobId != null) {
                AutomationJobs.notifyResult(jobId, result, lines); // the panel, before the job is forgotten
                AutomationJobs.finish(jobId);
            }
            if (replyAction == null || replyAction.trim().isEmpty() || replyPackage == null || replyPackage.trim().isEmpty()) return;
            Intent out = new Intent(replyAction.trim());
            out.setPackage(replyPackage.trim());
            out.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            for (String extra : correlationExtras) out.putExtra(extra, correlationId);
            out.putExtra(AutomationProvider.KEY_RESULT, result);
            sendBroadcast(out);
        };

        // 2. Go foreground, guarded; 3. drain the handover in the same try/finally; 4. only then bail.
        ParcelFileDescriptor fd = null;
        boolean handedOff = false;
        try {
            startForeground(NOTIFICATION_ID, notification(importing, getString(importing
                ? R.string.shiroikuma_eim_notif_importing : R.string.shiroikuma_eim_notif_exporting)));
            fd = jobId == null ? null : HANDOVER.remove(jobId);

            if (jobId == null || mode == 0 || !AutomationJobs.exists(jobId)) {
                return START_NOT_STICKY; // stale: that request already had its one reply — silence
            }
            if (mode != MODE_EXPORT_FILE && fd == null) {
                return START_NOT_STICKY; // descriptor already consumed
            }

            final ParcelFileDescriptor owned = fd;
            Thread worker = new Thread(() -> run(jobId, mode, dir, owned, items, replyPackage, progressAction, reply, startId),
                "shiroikuma-backup");
            handedOff = true;
            mActive.incrementAndGet();
            worker.start();
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Backup service could not start", t);
            reply.send(startFailure(this, t), null);
        } finally {
            if (!handedOff) {
                if (fd == null && jobId != null) fd = HANDOVER.remove(jobId);
                closeQuietly(fd);
                stopSilently(startId);
            }
        }
        return START_NOT_STICKY;
    }

    private void run(@NonNull String jobId, int mode, @Nullable String dir, @Nullable ParcelFileDescriptor fd,
                     @Nullable String items, @Nullable String replyPackage, @Nullable String progressAction,
                     @NonNull Replier reply, int startId) {
        PowerManager.WakeLock wakeLock = null;
        boolean claimed = false;
        AutomationProgress progress = null;
        try {
            // The flag is claimed only now — after the promotion succeeded.
            if (!TermuxBackup.RUNNING.compareAndSet(false, true)) {
                reply.send(mode == MODE_IMPORT_FD ? "ERROR:import already running" : "ERROR:export already running", null);
                return;
            }
            claimed = true;
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shiroikuma-termux:backup");
                wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
            }
            Set<Cat> cats = Cat.resolve(items);
            if (cats == null) {
                reply.send("ERROR:unknown category in items: " + items, null);
                return;
            }
            String replyIdForProgress = mode == MODE_EXPORT_FILE ? currentReplyId(jobId) : jobId;
            progress = new AutomationProgress(this, progressAction, replyPackage, replyIdForProgress,
                mode == MODE_EXPORT_FILE ? new String[]{AutomationProvider.KEY_REPLY_ID}
                    : new String[]{AutomationProvider.KEY_JOB_ID, AutomationProvider.KEY_REPLY_ID},
                jobId, TermuxConstants.TERMUX_APP_NAME, text -> notifyProgress(mode == MODE_IMPORT_FD, text));
            progress.start();

            TermuxBackup.Cancel cancel = AutomationJobs.cancelOf(jobId);
            if (mode == MODE_EXPORT_FILE) {
                if (dir == null || dir.isEmpty()) {
                    reply.send("ERROR:no-directory", null);
                    return;
                }
                TermuxBackup.Result r = TermuxBackup.exportToDirectory(this, cats, new File(dir), TermuxBackup.exportFileName(), progress, cancel);
                File file = r.file;
                long bytes = file == null ? 0 : file.length();
                reply.send("OK:" + (file == null ? "" : file.getAbsolutePath()) + "|" + bytes + "|" + TermuxBackup.humanSize(bytes)
                    + "|" + r.categories.size() + " categories", r.lines);
            } else if (mode == MODE_EXPORT_FD) {
                final long[] written = {0};
                OutputStream raw = new ParcelFileDescriptor.AutoCloseOutputStream(fd);
                TermuxBackup.Result r;
                try {
                    // Counted as it goes: the caller owns the file and we may not be able to stat it.
                    OutputStream counting = new OutputStream() {
                        @Override
                        public void write(int b) throws IOException {
                            raw.write(b);
                            written[0]++;
                        }

                        @Override
                        public void write(@NonNull byte[] b, int off, int len) throws IOException {
                            raw.write(b, off, len);
                            written[0] += len;
                        }

                        @Override
                        public void flush() throws IOException {
                            raw.flush();
                        }
                    };
                    r = TermuxBackup.export(this, cats, counting, progress, cancel);
                    counting.flush();
                } finally {
                    raw.close();
                }
                reply.send("OK:" + written[0] + "|" + TermuxBackup.humanSize(written[0]) + "|" + r.categories.size() + " categories", r.lines);
            } else {
                InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(fd);
                TermuxBackup.Result r;
                try {
                    r = TermuxBackup.importZip(this, in, cats, progress, cancel);
                } finally {
                    in.close();
                }
                // Every write above went through commit() / a closed file; a caller that
                // force-stops us on this reply loses nothing.
                reply.send("OK:" + r.categories.size() + " categories restored", r.lines);
            }
        } catch (TermuxBackup.CancelledException e) {
            reply.send("ERROR:cancelled", null);
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Backup job failed", t);
            String message = t.getMessage();
            reply.send("ERROR:" + (message != null ? message : t.getClass().getSimpleName()), null);
        } finally {
            if (progress != null) progress.stop();
            closeQuietly(fd);
            if (claimed) TermuxBackup.RUNNING.set(false);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            mActive.decrementAndGet();
            stopSilently(startId);
        }
    }

    /** The §1 reply_id of a job, for the progress line's correlation extra. */
    @NonNull
    private static String currentReplyId(@NonNull String jobId) {
        String id = AutomationJobs.replyIdOf(jobId);
        return id == null ? "" : id;
    }

    private void stopSilently(int startId) {
        if (mActive.get() <= 0) {
            try {
                stopForeground(true);
            } catch (Throwable ignored) {
                // we may never have been foreground at all
            }
        }
        stopSelf(startId);
    }

    // ---- notification --------------------------------------------------------------------------

    private Notification notification(boolean importing, @Nullable String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifications = manager;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.shiroikuma_eim_notif_channel), NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setContentTitle(getString(importing ? R.string.shiroikuma_eim_notif_import : R.string.shiroikuma_eim_notif_export))
            .setSmallIcon(R.drawable.ic_service_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true);
        if (text != null) b.setContentText(text);
        Intent open = new Intent(this, TermuxActivity.class);
        b.setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return b.build();
    }

    /** The progress line in the notification, at most once a second. */
    private void notifyProgress(boolean importing, @NonNull String text) {
        long now = SystemClock.uptimeMillis();
        if (text.equals(mLastNotified) || now - mLastNotifiedAt < 1000) return;
        mLastNotified = text;
        mLastNotifiedAt = now;
        NotificationManager manager = mNotifications;
        if (manager == null) return;
        try {
            manager.notify(NOTIFICATION_ID, notification(importing, text));
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(@Nullable ParcelFileDescriptor fd) {
        if (fd == null) return;
        try {
            fd.close();
        } catch (Exception ignored) {
            // an already-closed descriptor is the normal case here
        }
    }

    private interface Replier {
        void send(@NonNull String result, @Nullable List<String> lines);
    }
}
