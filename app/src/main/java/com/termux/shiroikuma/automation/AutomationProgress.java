package com.termux.shiroikuma.automation;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shiroikuma.backup.TermuxBackup;

/**
 * shiroikuma-termux (Phase 4b): the <b>one</b> §3 progress sender, shared by both automation
 * doors and the panel (a port of raikidoban's, counting files and bytes instead of categories).
 *
 * <p>The broadcast door echoes {@code reply_id}, the provider door hands out a {@code job_id}; the
 * correlation id is written into every name in {@code correlationExtras}, so one reader on the
 * caller's side serves both doors. Every line carries {@code item} (the category id being
 * written), {@code text} numbers-first ("Home 1234/8942 · 512 MB / 4.2 GB"), {@code current} /
 * {@code total} (files), {@code unit}, and {@code bytes} / {@code bytes_total}.
 *
 * <p>At most one message per {@value #MIN_INTERVAL_MS} ms (the final one always goes out), and a
 * heartbeat re-sends the last line every {@value #HEARTBEAT_MS} ms when nothing moved — the
 * caller presumes an app silent for two minutes dead, and a single big file, or a pipe the caller
 * is slow to drain, can easily be quiet longer than that. Inert without a {@code progress_action}
 * and a {@code reply_package} (an implicit broadcast reaches no manifest receiver since API 26),
 * but the in-process {@link AutomationJobs.Listener} of the panel is fed either way.
 */
public final class AutomationProgress implements TermuxBackup.Progress {

    private static final String UNIT = "files";
    private static final long MIN_INTERVAL_MS = 500;
    private static final long HEARTBEAT_MS = 20_000;
    private static final long HEARTBEAT_TICK_MS = 5_000;

    /** Someone else who wants the display line (the service's notification). */
    public interface Mirror {
        void onText(@NonNull String text);
    }

    private final Context context;
    private final String action;
    private final String replyPackage;
    private final String[] correlationExtras;
    private final String correlationId;
    private final String jobId;
    private final String appLabel;
    private final boolean active;
    @Nullable private final Mirror mirror;

    private volatile String lastItem;
    private volatile String lastText;
    private volatile long lastCurrent;
    private volatile long lastTotal;
    private volatile long lastBytes;
    private volatile long lastBytesTotal;
    private volatile long lastSentMs;
    private volatile boolean running;
    private Thread heartbeat;

    public AutomationProgress(@NonNull Context context, @Nullable String action, @Nullable String replyPackage,
                              @NonNull String correlationId, @NonNull String[] correlationExtras, @NonNull String jobId,
                              @NonNull String appLabel, @Nullable Mirror mirror) {
        this.context = context.getApplicationContext();
        this.action = action == null ? "" : action.trim();
        this.replyPackage = replyPackage == null ? "" : replyPackage.trim();
        this.correlationExtras = correlationExtras;
        this.correlationId = correlationId;
        this.jobId = jobId;
        this.appLabel = appLabel;
        this.mirror = mirror;
        this.active = !this.action.isEmpty() && !this.replyPackage.isEmpty();
    }

    /** Begin the heartbeat. Safe on an inert sender. */
    public void start() {
        if (!active || running) return;
        running = true;
        lastSentMs = System.currentTimeMillis();
        heartbeat = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(HEARTBEAT_TICK_MS);
                } catch (InterruptedException e) {
                    return;
                }
                if (!running || lastText == null) continue;
                if (System.currentTimeMillis() - lastSentMs >= HEARTBEAT_MS) {
                    emit(lastItem, lastText, lastCurrent, lastTotal, lastBytes, lastBytesTotal, true);
                }
            }
        }, "shiroikuma-backup-heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    /** Stop the heartbeat. Always call this in a {@code finally}. */
    public void stop() {
        running = false;
        Thread t = heartbeat;
        if (t != null) {
            t.interrupt();
            heartbeat = null;
        }
    }

    @Override
    public void onProgress(@NonNull TermuxBackup.Cat cat, long files, long filesTotal, long bytes, long bytesTotal) {
        boolean last = filesTotal > 0 && files >= filesTotal;
        long now = System.currentTimeMillis();
        if (!last && now - lastSentMs < MIN_INTERVAL_MS) return;
        StringBuilder sb = new StringBuilder(context.getString(cat.shortLabelRes)).append(' ').append(files);
        if (filesTotal > 0) sb.append('/').append(filesTotal);
        if (bytesTotal > 0 || bytes > 0) {
            sb.append(" · ").append(TermuxBackup.humanSize(bytes));
            if (bytesTotal > 0) sb.append(" / ").append(TermuxBackup.humanSize(bytesTotal));
        }
        emit(cat.id, sb.toString(), files, filesTotal, bytes, bytesTotal, false);
    }

    @Override
    public void onNote(@Nullable TermuxBackup.Cat cat, @NonNull String text) {
        emit(cat == null ? null : cat.id, text, 0, 0, 0, 0, false);
    }

    private void emit(@Nullable String item, @NonNull String text, long current, long total, long bytes, long bytesTotal, boolean heartbeat) {
        lastItem = item;
        lastText = text;
        lastCurrent = current;
        lastTotal = total;
        lastBytes = bytes;
        lastBytesTotal = bytesTotal;
        lastSentMs = System.currentTimeMillis();

        if (!heartbeat) {
            AutomationJobs.notifyProgress(jobId, text);
            if (mirror != null) mirror.onText(text);
        }
        if (!active) return;

        Intent out = new Intent(action);
        out.setPackage(replyPackage);
        out.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        for (String extra : correlationExtras) out.putExtra(extra, correlationId);
        out.putExtra("app", appLabel);
        if (item != null) out.putExtra("item", item);
        out.putExtra("text", text);
        out.putExtra("current", current);
        out.putExtra("total", total);
        out.putExtra("unit", UNIT);
        if (bytes > 0 || bytesTotal > 0) {
            out.putExtra("bytes", bytes);
            out.putExtra("bytes_total", bytesTotal);
        }
        context.sendBroadcast(out);
    }
}
