package com.termux.boot;

/*
 * shiroikuma-termux (Phase 4c): Termux:Boot absorbed into the app.
 *
 * Upstream: https://github.com/termux/termux-boot — fetch-only remote `upstream-boot`,
 * ported from commit a8493bd6ba016bc370af34aa65fcbe065cc00ced (2026-01-20, "Fixed: Do not abort
 * processing files in boot scripts directory if a non-regular file is found"). The package name
 * `com.termux.boot` is kept so an upstream diff ports by hand. Changes against upstream:
 *  - the hard-coded "/data/data/com.termux/files/home/.termux/boot" → TermuxConstants.TERMUX_BOOT_SCRIPTS_DIR;
 *  - android.util.Log → the app's Logger;
 *  - BootActivity (the "open the app once" info screen) and its assets/icons are dropped: this app
 *    IS the app that has to be opened once after install (a freshly installed package stays in the
 *    stopped state and receives no BOOT_COMPLETED until it has been launched).
 * BootJobService carries the same header.
 */

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.Arrays;

public class BootReceiver extends BroadcastReceiver {

    public static final int TERMUX_BOOT_JOB_ID_BASE = 1000;
    static int jobId = TERMUX_BOOT_JOB_ID_BASE;

    private static final String LOG_TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        final File BOOT_SCRIPT_DIR = TermuxConstants.TERMUX_BOOT_SCRIPTS_DIR;
        File[] files = BOOT_SCRIPT_DIR.listFiles();
        if (files == null) files = new File[0];

        // Sort files so that they get executed in a repeatable and logical order.
        Arrays.sort(files, (f1, f2) -> f1.getName().compareTo(f2.getName()));

        StringBuilder logMessage = new StringBuilder();
        for (File file : files) {
            if (!file.isFile()) continue;

            if (logMessage.length() > 0) logMessage.append(", ");
            logMessage.append(file.getName());

            ensureFileReadableAndExecutable(file);

            PersistableBundle extras = new PersistableBundle();
            extras.putString(BootJobService.SCRIPT_FILE_PATH, file.getAbsolutePath());

            ComponentName serviceComponent = new ComponentName(context, BootJobService.class);
            JobInfo job = new JobInfo.Builder(jobId++, serviceComponent)
                    .setExtras(extras)
                    .setOverrideDeadline(3 * 1000)
                    .build();
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            assert jobScheduler != null;
            jobScheduler.schedule(job);
        }

        if (logMessage.length() > 0) {
            Logger.logInfo(LOG_TAG, "Executed files at boot: " + logMessage);
        } else {
            Logger.logInfo(LOG_TAG, "No files to execute at boot");
        }
    }

    /** Ensure readable and executable file if user forgot to do so. */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void ensureFileReadableAndExecutable(File file) {
        if (!file.canRead()) file.setReadable(true);
        if (!file.canExecute()) file.setExecutable(true);
    }
}
