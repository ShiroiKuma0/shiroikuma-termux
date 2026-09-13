package com.termux.window;

/*
 * shiroikuma-termux (Phase 4c): Termux:Float absorbed into the app.
 *
 * Upstream: https://github.com/termux/termux-float — fetch-only remote `upstream-float`, ported
 * from commit 75352bde928e9888a2aa2ad7e130f16118391b75 (2025-10-29, "Changed: Bump termux-shared
 * to 8aca6dbbf4"). The package `com.termux.window` is kept so an upstream diff ports by hand; every
 * change against upstream is marked "shiroikuma-termux". Package-wide:
 *  - TermuxFloatApplication is no longer an Application (this process has TermuxApplication) —
 *    only its setLogConfig() stub remains so the call sites stay as upstream wrote them;
 *  - TermuxFloatActivity is NOT a launcher activity any more: it is reached from the static
 *    "Floating terminal" shortcut on the app's launcher icon and from the 白い熊 Termux UI page;
 *  - res: activity_main.xml → float_window.xml, activity_permission.xml → float_permission.xml,
 *    the strings in termux_float_strings.xml with our name, the notification icon is the app's
 *    R.drawable.ic_service_notification, floating_window_background_resize.xml / styles.xml /
 *    the launcher icons are dropped;
 *  - the window frame (background, border colour / width, corner radius) and the notification
 *    text come from ShiroikumaTheme (float_*), applied by TermuxFloatView.applyWindowStyle() and
 *    re-applied live through TermuxFloatService.reloadStyle().
 * Preferences (window position / size / font size) still go through
 * TermuxFloatAppSharedPreferences, whose build() now resolves to this app's own preferences when
 * the caller is com.termux (termux-shared change).
 */

import com.termux.R; // shiroikuma-termux: the app's R (namespace com.termux), not the plugin's
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;

import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_FLOAT_APP.TERMUX_FLOAT_SERVICE;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shiroikuma.ui.ShiroikumaTheme;
import com.termux.terminal.TerminalSession;

public class TermuxFloatService extends Service {

    private TermuxFloatView mFloatingWindow;

    private TermuxSession mSession;

    private boolean mVisibleWindow = true;

    /** shiroikuma-termux: the running instance, so the UI page can restyle the window live. */
    @Nullable private static TermuxFloatService sInstance;

    private static final String LOG_TAG = "TermuxFloatService";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        sInstance = this; // shiroikuma-termux
        runStartForeground();
        TermuxFloatApplication.setLogConfig(this, false);
        Logger.logVerbose(LOG_TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.logDebug(LOG_TAG, "onStartCommand");

        // Run again in case service is already started and onCreate() is not called
        runStartForeground();

        if (mFloatingWindow == null && !initializeFloatView())
            return Service.START_NOT_STICKY;

        String action = null;
        if (intent != null) {
            Logger.logVerboseExtended(LOG_TAG, "Received intent:\n" + IntentUtils.getIntentString(intent));
            action = intent.getAction();
        }

        if (action != null) {
            switch (action) {
                case TERMUX_FLOAT_SERVICE.ACTION_STOP_SERVICE:
                    actionStopService();
                    break;
                case TERMUX_FLOAT_SERVICE.ACTION_SHOW:
                    setVisible(true);
                    break;
                case TERMUX_FLOAT_SERVICE.ACTION_HIDE:
                    setVisible(false);
                    break;
                default:
                    Logger.logError(LOG_TAG, "Invalid action: \"" + action + "\"");
                    break;
            }
        } else if (!mVisibleWindow) {
            // Show window if hidden when launched through launcher icon.
            setVisible(true);
        }

        return Service.START_NOT_STICKY;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logVerbose(LOG_TAG, "onDestroy");
        if (sInstance == this) sInstance = null; // shiroikuma-termux

        if (mFloatingWindow != null)
            mFloatingWindow.closeFloatingWindow();

        runStopForeground();
    }
    /** Request to stop service. */
    public void requestStopService() {
        Logger.logDebug(LOG_TAG, "Requesting to stop service");
        runStopForeground();
        stopSelf();
    }

    /** Process action to stop service. */
    private void actionStopService() {
        if (mSession != null)
            mSession.killIfExecuting(this, false);
        requestStopService();
    }

    /** Make service run in foreground mode. */
    private void runStartForeground() {
        setupNotificationChannel();
        startForeground(TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_ID, buildNotification());
    }

    /** Make service leave foreground mode. */
    private void runStopForeground() {
        stopForeground(true);
    }



    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationUtils.setupNotificationChannel(this, TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_ID,
                getString(R.string.float_app_name), NotificationManager.IMPORTANCE_LOW); // shiroikuma-termux: our name
    }

    private Notification buildNotification() {
        final Resources res = getResources();

        // shiroikuma-termux: the UI page's "Notification text" row wins over upstream's hide/show hint
        String notificationText = ShiroikumaTheme.getString(this, ShiroikumaTheme.FLOAT_NOTIFICATION_TEXT);
        if (notificationText == null || notificationText.trim().isEmpty())
            notificationText = res.getString(mVisibleWindow ? R.string.notification_message_visible : R.string.notification_message_hidden);

        final String intentAction = mVisibleWindow ? TERMUX_FLOAT_SERVICE.ACTION_HIDE : TERMUX_FLOAT_SERVICE.ACTION_SHOW;
        Intent notificationIntent = new Intent(this, TermuxFloatService.class).setAction(intentAction);
        PendingIntent contentIntent = PendingIntent.getService(this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        // Build the notification
        Notification.Builder builder =  NotificationUtils.geNotificationBuilder(this,
                TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_ID, Notification.PRIORITY_LOW,
                getString(R.string.float_app_name), notificationText, null, // shiroikuma-termux: our name
                contentIntent, null, NotificationUtils.NOTIFICATION_MODE_SILENT);
        if (builder == null)  return null;

        // No need to show a timestamp:
        builder.setShowWhen(false);

        // Set notification icon
        builder.setSmallIcon(R.drawable.ic_service_notification); // shiroikuma-termux: the app's icon

        // Set background color for small notification icon
        builder.setColor(0xFF000000);

        // TermuxSessions are always ongoing
        builder.setOngoing(true);

        // Set Exit button action
        Intent exitIntent = new Intent(this, TermuxFloatService.class).setAction(TERMUX_FLOAT_SERVICE.ACTION_STOP_SERVICE);
        builder.addAction(android.R.drawable.ic_delete, res.getString(R.string.notification_action_exit),
                PendingIntent.getService(this, 0, exitIntent,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        return builder.build();
    }



    @SuppressLint("InflateParams")
    private boolean initializeFloatView() {
        boolean floatWindowWasNull = false;
        if (mFloatingWindow == null) {
            mFloatingWindow = (TermuxFloatView) ((LayoutInflater)
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.float_window, null); // shiroikuma-termux: renamed
            floatWindowWasNull = true;
        }

        mFloatingWindow.initFloatView(this);

        mSession = createTermuxSession(
                new ExecutionCommand(0, null, null, null, mFloatingWindow.getProperties().getDefaultWorkingDirectory(), ExecutionCommand.Runner.TERMINAL_SESSION.getName(), false), null);
        if (mSession == null)
            return false;
        mFloatingWindow.getTerminalView().attachSession(mSession.getTerminalSession());

        try {
            mFloatingWindow.launchFloatingWindow();
        } catch (Exception e) {
            Logger.logStackTrace(LOG_TAG, e);
            // Settings.canDrawOverlays() does not work (always returns false, perhaps due to sharedUserId?).
            // So instead we catch the exception and prompt here.
            startActivity(new Intent(this, TermuxFloatPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            requestStopService();
            return false;
        }

        if (floatWindowWasNull)
            Logger.showToast(this, getString(R.string.initial_instruction_toast), true);

        return true;
    }

    private void setVisible(boolean newVisibility) {
        mVisibleWindow = newVisibility;
        mFloatingWindow.setVisibility(newVisibility ? View.VISIBLE : View.GONE);
        updateNotification();
    }

    private void updateNotification() {
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_ID, buildNotification());
    }

    /**
     * shiroikuma-termux: re-applies the Floating terminal section of the 白い熊 Termux UI page (frame
     * colours, border, corners, notification text) to a running window; a no-op when none is up.
     */
    public static void reloadStyle() {
        TermuxFloatService service = sInstance;
        if (service == null || service.mFloatingWindow == null) return;
        service.mFloatingWindow.applyWindowStyle(false);
        service.updateNotification();
    }

    /** shiroikuma-termux: whether the floating terminal is currently running. */
    public static boolean isRunning() {
        return sInstance != null && sInstance.mFloatingWindow != null;
    }



    /** Create a {@link TermuxSession}. */
    @Nullable
    public synchronized TermuxSession createTermuxSession(ExecutionCommand executionCommand, String sessionName) {
        if (executionCommand == null) return null;

        Logger.logDebug(LOG_TAG, "Creating \"" + executionCommand.getCommandIdAndLabelLogString() + "\" TermuxSession");

        if (ExecutionCommand.Runner.APP_SHELL.getName().equals(executionCommand.runner)) {
            Logger.logDebug(LOG_TAG, "Ignoring a background execution command passed to createTermuxSession()");
            return null;
        }

        if (Logger.getLogLevel() >= Logger.LOG_LEVEL_VERBOSE)
            Logger.logVerboseExtended(LOG_TAG, executionCommand.toString());

        executionCommand.shellName = sessionName;
        executionCommand.terminalTranscriptRows = mFloatingWindow.getProperties().getTerminalTranscriptRows();
        TermuxSession newTermuxSession = TermuxSession.execute(this, executionCommand,
                mFloatingWindow.getTermuxFloatSessionClient(), null, new TermuxShellEnvironment(),
                null, executionCommand.isPluginExecutionCommand);
        if (newTermuxSession == null) {
            Logger.logError(LOG_TAG, "Failed to execute new TermuxSession command for:\n" + executionCommand.getCommandIdAndLabelLogString());
            return null;
        }

        // Emulator won't be set at this point so colors won't be set by TermuxFloatSessionClient.checkForFontAndColors()
        mFloatingWindow.reloadViewStyling();

        return newTermuxSession;
    }

    public TermuxSession getTermuxSession() {
        return mSession;
    }

    public TerminalSession getCurrentSession() {
        return mSession != null ? mSession.getTerminalSession() : null;
    }

}
