package com.termux.shiroikuma.automation;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shiroikuma.backup.TermuxBackup;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * shiroikuma-termux (Phase 4b): the jobs the backup service has started — the flag each of them
 * watches to stop, the §1 {@code reply_id} a CANCEL_EXPORT names, and the in-process listener the
 * Export / Import panel attaches to follow its own run (one engine, one runner: the panel's
 * export goes through the same foreground service as the automation doors).
 *
 * <p>Process-local and never persisted, deliberately: a persisted "running" flag survives the
 * crash that stranded it and wedges the app for good.
 */
public final class AutomationJobs {

    /** What the panel hears; every call arrives on the main thread. */
    public interface Listener {
        void onProgress(@NonNull String text);

        /** {@code result} is the contract line ({@code OK:…} / {@code ERROR:…}); {@code lines} the human summary. */
        void onResult(@NonNull String result, @Nullable List<String> lines);
    }

    private static final class Job {
        @NonNull final String replyId;
        volatile boolean cancelled;
        @Nullable volatile Listener listener;

        Job(@NonNull String replyId) {
            this.replyId = replyId;
        }
    }

    private static final ConcurrentHashMap<String, Job> JOBS = new ConcurrentHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AutomationJobs() {
    }

    /** Mints a job id; {@code replyId} is the §1 correlation id (empty for the other doors). */
    @NonNull
    public static String begin(@Nullable String replyId) {
        String id = UUID.randomUUID().toString();
        JOBS.put(id, new Job(replyId == null ? "" : replyId));
        return id;
    }

    /** Ask a job to stop. A silent no-op for an id that is finished or was never real. */
    public static void cancel(@Nullable String jobId) {
        if (jobId == null) return;
        Job j = JOBS.get(jobId);
        if (j != null) j.cancelled = true;
    }

    /** §1 CANCEL_EXPORT: the run whose request carried {@code replyId}; empty = every run. */
    public static void cancelByReplyId(@Nullable String replyId) {
        String want = replyId == null ? "" : replyId.trim();
        for (Job j : JOBS.values()) {
            if (want.isEmpty() || want.equals(j.replyId)) j.cancelled = true;
        }
    }

    /** Polled at entry boundaries — never mid-write, so a cancelled archive is never half a file. */
    public static boolean isCancelled(@Nullable String jobId) {
        Job j = jobId == null ? null : JOBS.get(jobId);
        return j != null && j.cancelled;
    }

    @NonNull
    public static TermuxBackup.Cancel cancelOf(@NonNull final String jobId) {
        return () -> isCancelled(jobId);
    }

    public static void finish(@Nullable String jobId) {
        if (jobId != null) JOBS.remove(jobId);
    }

    public static boolean exists(@Nullable String jobId) {
        return jobId != null && JOBS.containsKey(jobId);
    }

    /** The §1 reply_id a job was started for ("" for the other doors), null when unknown. */
    @Nullable
    public static String replyIdOf(@Nullable String jobId) {
        Job j = jobId == null ? null : JOBS.get(jobId);
        return j == null ? null : j.replyId;
    }

    // ---- the in-process listener (the panel) --------------------------------------------------

    public static void attach(@NonNull String jobId, @Nullable Listener listener) {
        Job j = JOBS.get(jobId);
        if (j != null) j.listener = listener;
    }

    public static void detach(@NonNull String jobId) {
        attach(jobId, null);
    }

    static void notifyProgress(@NonNull String jobId, @NonNull final String text) {
        Job j = JOBS.get(jobId);
        final Listener l = j == null ? null : j.listener;
        if (l != null) MAIN.post(() -> l.onProgress(text));
    }

    static void notifyResult(@NonNull String jobId, @NonNull final String result, @Nullable final List<String> lines) {
        Job j = JOBS.get(jobId);
        final Listener l = j == null ? null : j.listener;
        if (l != null) MAIN.post(() -> l.onResult(result, lines));
    }
}
