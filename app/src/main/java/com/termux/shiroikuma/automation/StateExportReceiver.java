package com.termux.shiroikuma.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;
import com.termux.shared.logger.Logger;
import com.termux.shiroikuma.backup.ExportDir;
import com.termux.shiroikuma.backup.TermuxBackup;
import com.termux.shiroikuma.backup.TermuxBackup.Cat;

import java.io.File;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * shiroikuma-termux (Phase 4b): the sister-app <b>state-export automation contract</b> (§1) —
 * the wire shape every 白い熊 app exposes so one 自由作業盤 task can back them all up headlessly.
 *
 * <ul>
 * <li>{@code com.termux.action.EXPORT_STATE}: the category-ZIP export with no UI. Extras (all
 * String): {@code token} (optional — checked only while 「Use authorization token?」 is on),
 * {@code path} (optional absolute directory; wins over the configured one), {@code items}
 * (optional comma list of category ids; absent = the default set), {@code progress_action}
 * (optional), plus {@code reply_action} / {@code reply_package} / {@code reply_id}.</li>
 * <li>{@code com.termux.action.LIST_CATEGORIES}: instant enumeration, one
 * {@code id<TAB>label<TAB>parent<TAB>on|off} line per category.</li>
 * <li>{@code com.termux.action.CANCEL_EXPORT}: stop a running export ({@code reply_id} names it;
 * absent = every export in flight). Fire-and-forget, never answered; the export unwinds at the
 * next entry, deletes its {@code .part}, and answers its ORIGINAL request {@code ERROR:cancelled}.</li>
 * </ul>
 *
 * <p>The receiver does nothing but gate, validate and hand off to {@link AutomationDataService}
 * (a tar of {@code usr} takes minutes; a receiver must return inside the broadcast window). The
 * reply is a FRESH broadcast to {@code reply_package} with {@code FLAG_INCLUDE_STOPPED_PACKAGES}
 * — no binders, no ordered-broadcast result (EMUI severs both). Exactly one terminal reply.
 *
 * <p>Storage: this app declares {@code MANAGE_EXTERNAL_STORAGE}, so an absolute {@code path} is
 * written with {@code java.io.File} — and when the grant is missing the answer is exactly
 * {@code ERROR:no-storage-access} (no SAF fallback: the contract scopes that to apps without the
 * permission). Without a path: the configured directory, else {@code ERROR:no-directory}.
 *
 * <p>Exported with no permission, deliberately (v2): this is the unauthenticated half of the
 * surface — it only writes where told and reports what it did. Everything that moves data through
 * a caller-supplied descriptor lives behind {@link AutomationProvider}, which knows who is calling.
 */
public class StateExportReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "StateExportReceiver";

    public static final String ACTION_EXPORT_STATE = BuildConfig.APPLICATION_ID + ".action.EXPORT_STATE";
    public static final String ACTION_LIST_CATEGORIES = BuildConfig.APPLICATION_ID + ".action.LIST_CATEGORIES";
    public static final String ACTION_CANCEL_EXPORT = BuildConfig.APPLICATION_ID + ".action.CANCEL_EXPORT";

    private static final String EXTRA_TOKEN = "token";
    private static final String EXTRA_PATH = "path";
    private static final String EXTRA_ITEMS = "items";
    private static final String EXTRA_PROGRESS_ACTION = "progress_action";
    private static final String EXTRA_REPLY_ACTION = "reply_action";
    private static final String EXTRA_REPLY_PACKAGE = "reply_package";
    private static final String EXTRA_REPLY_ID = "reply_id";
    private static final String EXTRA_RESULT = "result";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final String action = intent.getAction();
        if (action == null) return;
        final String token = intent.getStringExtra(EXTRA_TOKEN);
        final String replyAction = trimmed(intent.getStringExtra(EXTRA_REPLY_ACTION));
        final String replyPackage = trimmed(intent.getStringExtra(EXTRA_REPLY_PACKAGE));
        final String replyId = trimmed(intent.getStringExtra(EXTRA_REPLY_ID));
        final String progressAction = trimmed(intent.getStringExtra(EXTRA_PROGRESS_ACTION));
        final String pathOverride = trimmed(intent.getStringExtra(EXTRA_PATH));
        final String items = trimmed(intent.getStringExtra(EXTRA_ITEMS));

        // Cancel is handled ahead of the replying gate: it must never answer anything, and a
        // rejected token is silence too. Safe at any time — when nothing matches, nothing happens.
        if (ACTION_CANCEL_EXPORT.equals(action)) {
            if (AutomationAuth.refuse(app, token) == null) AutomationJobs.cancelByReplyId(replyId);
            return;
        }

        final AtomicBoolean replied = new AtomicBoolean(false);
        final Replier reply = result -> {
            if (!replied.compareAndSet(false, true)) return;
            if (replyAction.isEmpty() || replyPackage.isEmpty()) return;
            Intent out = new Intent(replyAction);
            out.setPackage(replyPackage);
            out.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            out.putExtra(EXTRA_REPLY_ID, replyId);
            out.putExtra(EXTRA_RESULT, result);
            app.sendBroadcast(out);
        };

        // The gate, in ONE place (§2): the switch is on by default, the token opt-in and IGNORED
        // when not asked for.
        String refusal = AutomationAuth.refuse(app, token);
        if (refusal != null) {
            reply.send(refusal);
            return;
        }

        if (ACTION_LIST_CATEGORIES.equals(action)) {
            reply.send(listCategories(app));
            return;
        }

        if (!ACTION_EXPORT_STATE.equals(action)) {
            reply.send("ERROR:unknown action: " + action);
            return;
        }

        Set<Cat> cats = Cat.resolve(items);
        if (cats == null) {
            reply.send("ERROR:unknown category in items: " + items);
            return;
        }

        // Directory precedence: `path` → the configured directory → ERROR:no-directory. Either is
        // written with java.io.File, which needs the storage grant — checked here, up front.
        File dir;
        if (!pathOverride.isEmpty()) {
            dir = new File(pathOverride);
        } else {
            dir = ExportDir.dir(app);
            if (dir == null) {
                reply.send("ERROR:no-directory");
                return;
            }
        }
        if (!ExportDir.hasStorageAccess(app)) {
            reply.send("ERROR:no-storage-access");
            return;
        }
        if (TermuxBackup.RUNNING.get()) {
            reply.send("ERROR:export already running");
            return;
        }

        // Hand off and get out: the service does the work, the progress and the one reply.
        String jobId = AutomationJobs.begin(replyId);
        try {
            AutomationDataService.startFileExport(app, jobId, dir, items, replyAction, replyPackage, replyId, progressAction);
        } catch (Throwable t) {
            // An unguarded startForegroundService in onReceive would take the process down.
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not start the export service", t);
            AutomationJobs.finish(jobId);
            reply.send(AutomationDataService.startFailure(app, t));
        }
    }

    /** {@code OK:} + one {@code id<TAB>label<TAB>parent<TAB>on|off} line per category. */
    @NonNull
    static String listCategories(@NonNull Context app) {
        StringBuilder sb = new StringBuilder("OK:");
        boolean first = true;
        for (Cat cat : Cat.values()) {
            if (!first) sb.append('\n');
            first = false;
            // The parent field stays present but empty for a top-level category: the fourth
            // field is positional.
            sb.append(cat.id).append('\t').append(app.getString(cat.labelRes))
                .append('\t').append(cat.parentId == null ? "" : cat.parentId)
                .append('\t').append(cat.defaultSelected ? "on" : "off");
        }
        return sb.toString();
    }

    @NonNull
    private static String trimmed(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    private interface Replier {
        void send(@NonNull String result);
    }
}
