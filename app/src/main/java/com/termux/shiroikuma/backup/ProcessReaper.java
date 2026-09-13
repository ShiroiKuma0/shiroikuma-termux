package com.termux.shiroikuma.backup;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;

import com.termux.app.TermuxService;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * shiroikuma-termux (Phase 4b): everything that runs out of {@code usr} or {@code home} must be
 * gone before those trees are replaced — a shell with {@code usr/bin/bash} mapped keeps running
 * old code over a swapped prefix, and its open files pin the old inodes until it exits.
 *
 * <p>Two steps, in this order: {@link TermuxService}'s own {@code ACTION_STOP_SERVICE} (which
 * kills every session and task it knows about and stops the foreground service the way the
 * notification's Exit does), then a sweep of {@code /proc} for every other pid of OUR uid —
 * sshd, cron, a detached tmux, anything started outside the service — each SIGKILLed. Our own
 * process is spared: this runs inside it.
 */
public final class ProcessReaper {

    private static final String LOG_TAG = "ProcessReaper";
    private static final long SERVICE_GRACE_MS = 1500;
    private static final long SWEEP_TOTAL_MS = 4000;

    private ProcessReaper() {
    }

    /** Returns the number of processes killed by the sweep (the service's own kills not counted). */
    public static int run(@NonNull Context context) {
        Context app = context.getApplicationContext();
        try {
            Intent stop = new Intent(app, TermuxService.class).setAction(TERMUX_SERVICE.ACTION_STOP_SERVICE);
            app.startService(stop);
            SystemClock.sleep(SERVICE_GRACE_MS);
        } catch (Exception e) {
            // A background start refused, or the service not running — the sweep below covers it.
            Logger.logDebug(LOG_TAG, "ACTION_STOP_SERVICE not delivered: " + e);
        }
        int killed = 0;
        long deadline = SystemClock.uptimeMillis() + SWEEP_TOTAL_MS;
        while (true) {
            List<Integer> pids = otherPidsOfOurUid();
            if (pids.isEmpty()) break;
            for (int pid : pids) {
                try {
                    Os.kill(pid, OsConstants.SIGKILL);
                    killed++;
                } catch (Exception e) {
                    Logger.logDebug(LOG_TAG, "kill " + pid + ": " + e);
                }
            }
            if (SystemClock.uptimeMillis() > deadline) break;
            SystemClock.sleep(200);
        }
        return killed;
    }

    /** Every pid in /proc whose real uid is ours, except this process. */
    @NonNull
    static List<Integer> otherPidsOfOurUid() {
        List<Integer> out = new ArrayList<>();
        int myUid = Process.myUid();
        int myPid = Process.myPid();
        File[] entries = new File("/proc").listFiles();
        if (entries == null) return out;
        for (File e : entries) {
            String name = e.getName();
            if (name.isEmpty() || !Character.isDigit(name.charAt(0))) continue;
            int pid;
            try {
                pid = Integer.parseInt(name);
            } catch (NumberFormatException ex) {
                continue;
            }
            if (pid == myPid) continue;
            if (uidOf(pid) == myUid) out.add(pid);
        }
        return out;
    }

    /** The real uid from /proc/<pid>/status ("Uid:\treal\teffective\tsaved\tfs"), -1 if unreadable. */
    static int uidOf(int pid) {
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/" + pid + "/status"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("Uid:")) {
                    String[] parts = line.substring(4).trim().split("\\s+");
                    return parts.length > 0 ? Integer.parseInt(parts[0]) : -1;
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return -1;
    }
}
