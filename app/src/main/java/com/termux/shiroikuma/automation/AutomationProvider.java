package com.termux.shiroikuma.automation;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shiroikuma.backup.TermuxBackup;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * shiroikuma-termux (Phase 4b): the data door (contract §2a) — export this app's own state, and
 * put it back, for a caller we can identify. It sits alongside {@link StateExportReceiver}.
 *
 * <p>A broadcast cannot tell you who sent it; a provider gets the caller's identity from the
 * framework ({@link AutomationCallers}: exact package name → uid cross-check → pinned signing
 * certificate). And a list needs a synchronous answer: 応用管理 draws a row per installed app
 * before any export exists.
 *
 * <p>The payload never passes through here: {@link #call} validates, hands a duplicated
 * descriptor to the foreground {@link AutomationDataService} and returns {@code OK:<job_id>} —
 * the callee mints the id, and it is the correlation key of the terminal reply and every progress
 * line. {@code describe} answers from the manifest and a plain enum only: a provider call can be
 * what starts the process on a clean phone, before {@code Application.onCreate} has run.
 *
 * <p>{@code import} exists ONLY here — the §1 receiver is exported without a permission, and an
 * import there would let any app on the phone wipe the prefix.
 */
public class AutomationProvider extends ContentProvider {

    public static final String METHOD_DESCRIBE = "describe";
    public static final String METHOD_EXPORT = "export";
    public static final String METHOD_IMPORT = "import";
    public static final String METHOD_CANCEL = "cancel";

    public static final String KEY_RESULT = "result";
    public static final String KEY_FD = "fd";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_JOB_ID = "job_id";
    /** The broadcast door's correlation extra; the provider door mirrors its job_id into it too. */
    public static final String KEY_REPLY_ID = "reply_id";
    public static final String KEY_ITEMS = "items";
    public static final String KEY_REPLY_ACTION = "reply_action";
    public static final String KEY_REPLY_PACKAGE = "reply_package";
    public static final String KEY_PROGRESS_ACTION = "progress_action";

    /** This app's archive format; bumped when an older build could no longer read what we write. */
    public static final int FORMAT = TermuxBackup.VERSION;
    /** The oldest archive this build still reads. */
    public static final int MIN_FORMAT_READABLE = 1;

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * Every method answers a Bundle with {@link #KEY_RESULT} — {@code OK…} / {@code ERROR:…}, the
     * broadcast contract's grammar. A refusal is returned, never thrown: an exception across a
     * binder reaches the caller as a stack trace.
     */
    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        Context ctx = getContext();
        if (ctx == null) return fail("ERROR:not ready");
        ctx = ctx.getApplicationContext();

        // WHO, before WHAT.
        String refusedCaller = AutomationCallers.verify(ctx, getCallingPackage());
        if (refusedCaller != null) return fail(refusedCaller);
        String refused = AutomationAuth.refuse(ctx, extras == null ? null : extras.getString(KEY_TOKEN));
        if (refused != null) return fail(refused);

        if (METHOD_DESCRIBE.equals(method)) return ok(describe(ctx));
        if (METHOD_EXPORT.equals(method)) return start(ctx, extras, false);
        if (METHOD_IMPORT.equals(method)) return start(ctx, extras, true);
        if (METHOD_CANCEL.equals(method)) {
            AutomationJobs.cancel(extras == null ? null : extras.getString(KEY_JOB_ID));
            return ok("OK:cancelled");
        }
        return fail("ERROR:unknown method: " + method);
    }

    /** The header, returned from the call and never put inside the archive. */
    @NonNull
    private String describe(@NonNull Context ctx) {
        JSONObject header = new JSONObject();
        try {
            header.put("app_id", ctx.getPackageName());
            long code = 0;
            String name = "";
            try {
                PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                @SuppressWarnings("deprecation")
                int legacy = info.versionCode;
                code = legacy;
                name = info.versionName == null ? "" : info.versionName;
            } catch (Exception e) {
                // a header without a version is still a usable header
            }
            header.put("version_code", code);
            header.put("version_name", name);
            header.put("format", FORMAT);
            header.put("min_format_readable", MIN_FORMAT_READABLE);
            // A restore into a never-launched install is the intended case: the tars land in
            // files/{home,usr} and TermuxInstaller skips the bootstrap when $PREFIX is non-empty.
            header.put("requires_launch_first", false);
            // Everything the import writes is this app's own data under files/ — no runtime
            // permission is involved (and MANAGE_EXTERNAL_STORAGE must never be listed here).
            header.put("requires_permissions", new JSONArray());
            JSONArray contains = new JSONArray();
            contains.put(ctx.getString(R.string.shiroikuma_eim_contains_settings));
            contains.put(ctx.getString(R.string.shiroikuma_eim_contains_home));
            contains.put(ctx.getString(R.string.shiroikuma_eim_contains_usr));
            header.put("contains", contains);
        } catch (Exception e) {
            return "ERROR:" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return "OK:" + header;
    }

    /**
     * Hand the descriptor to the foreground service and get out of the way. The descriptor is
     * DUPLICATED first: the one in {@code extras} belongs to the binder transaction and is closed
     * when {@code call()} returns. If the service cannot be started, the dup is closed and the job
     * dropped BEFORE the refusal is returned — no {@code OK:<job_id>} is ever handed out for a job
     * that will not run, so nothing here double-answers.
     */
    @NonNull
    private Bundle start(@NonNull Context ctx, @Nullable Bundle extras, boolean importing) {
        ParcelFileDescriptor fd = extras == null ? null : extras.<ParcelFileDescriptor>getParcelable(KEY_FD);
        if (fd == null) return fail("ERROR:no descriptor");
        ParcelFileDescriptor dup;
        try {
            dup = fd.dup();
        } catch (Exception e) {
            return fail("ERROR:descriptor unusable");
        }
        if (TermuxBackup.RUNNING.get()) {
            closeQuietly(dup);
            return fail(importing ? "ERROR:import already running" : "ERROR:export already running");
        }
        String jobId = AutomationJobs.begin(null);
        try {
            AutomationDataService.startDescriptor(ctx, jobId, dup, importing, extras.getString(KEY_ITEMS),
                extras.getString(KEY_REPLY_ACTION), extras.getString(KEY_REPLY_PACKAGE), extras.getString(KEY_PROGRESS_ACTION));
        } catch (Throwable t) {
            AutomationJobs.finish(jobId);
            closeQuietly(dup);
            return fail(AutomationDataService.startFailure(ctx, t));
        }
        return ok("OK:" + jobId);
    }

    private static void closeQuietly(@NonNull ParcelFileDescriptor fd) {
        try {
            fd.close();
        } catch (Exception ignored) {
        }
    }

    private static Bundle ok(@NonNull String result) {
        Bundle b = new Bundle();
        b.putString(KEY_RESULT, result);
        return b;
    }

    private static Bundle fail(@NonNull String why) {
        return ok(why);
    }

    // A provider that is only ever call()ed still has to answer these; refusing loudly beats an
    // empty cursor that reads downstream as "there is no data".

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] args, String order) {
        throw new UnsupportedOperationException("automation is call() only");
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("automation is call() only");
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException("automation is call() only");
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException("automation is call() only");
    }
}
