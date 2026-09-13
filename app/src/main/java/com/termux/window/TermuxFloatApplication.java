package com.termux.window;

import android.content.Context;

/**
 * shiroikuma-termux (Phase 4c): upstream's {@code Application} subclass of Termux:Float, reduced
 * to a plain holder. This process already has {@code com.termux.app.TermuxApplication}, which
 * installs the crash handler and the log tag / level once for everything in it — so
 * {@link #setLogConfig} is a no-op kept only so the upstream call sites (TermuxFloatActivity,
 * TermuxFloatService.onCreate) stay byte-identical for hand-porting.
 * See TermuxFloatService for the upstream commit.
 */
public final class TermuxFloatApplication {

    public static final String LOG_TAG = "TermuxFloatApplication";

    private TermuxFloatApplication() {
    }

    /** No-op inside the app: TermuxApplication.setLogConfig() already configured this process. */
    @SuppressWarnings("unused")
    public static void setLogConfig(Context context, boolean commitToFile) {
    }

}
