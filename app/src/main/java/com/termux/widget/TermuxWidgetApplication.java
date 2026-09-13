package com.termux.widget;

import android.content.Context;

/**
 * shiroikuma-termux (Phase 4c): upstream's {@code Application} subclass of Termux:Widget, reduced
 * to a plain holder. This process already has {@code com.termux.app.TermuxApplication}, which
 * installs the crash handler, the log tag / level and the night mode once for everything in it —
 * so {@link #setLogConfig} is a no-op kept only so the upstream call sites (the shortcut launcher
 * activity, the controls executor receiver) stay byte-identical for hand-porting.
 * See TermuxWidgetProvider for the upstream commit.
 */
public final class TermuxWidgetApplication {

    public static final String LOG_TAG = "TermuxWidgetApplication";

    private TermuxWidgetApplication() {
    }

    /** No-op inside the app: TermuxApplication.setLogConfig() already configured this process. */
    @SuppressWarnings("unused")
    public static void setLogConfig(Context context, boolean commitToFile) {
    }

}
