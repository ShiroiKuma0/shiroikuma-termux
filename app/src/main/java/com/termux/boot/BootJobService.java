package com.termux.boot;

/*
 * shiroikuma-termux (Phase 4c): Termux:Boot absorbed into the app — see BootReceiver for the
 * upstream commit (a8493bd6ba016bc370af34aa65fcbe065cc00ced). Changes against upstream: the
 * copied TermuxService literals ("com.termux.app.TermuxService", "com.termux.service_execute",
 * "com.termux.execute.background") come from TermuxConstants, the runner is also passed as
 * EXTRA_RUNNER (EXTRA_BACKGROUND stays for the same reason upstream's widget keeps it), and the
 * log goes through the app's Logger.
 */

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;

import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

public class BootJobService extends JobService {

    public static final String SCRIPT_FILE_PATH = "com.termux.boot.script_path";

    private static final String LOG_TAG = "BootJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        Logger.logInfo(LOG_TAG, "Executing job " + params.getJobId() + ".");

        PersistableBundle extras = params.getExtras();
        String filePath = extras.getString(SCRIPT_FILE_PATH);

        Uri scriptUri = new Uri.Builder().scheme(TERMUX_SERVICE.URI_SCHEME_SERVICE_EXECUTE).path(filePath).build();
        Intent executeIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, scriptUri);
        executeIntent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME, TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        executeIntent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, Runner.APP_SHELL.getName());
        executeIntent.putExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, true);

        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // https://developer.android.com/about/versions/oreo/background.html
            context.startForegroundService(executeIntent);
        } else {
            context.startService(executeIntent);
        }

        return false; // offloaded to Termux; job is done
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Logger.logInfo(LOG_TAG, "Execution of job " + params.getJobId() + " has been cancelled.");
        return false; // do not reschedule
    }
}
