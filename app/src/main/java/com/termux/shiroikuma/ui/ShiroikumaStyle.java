package com.termux.shiroikuma.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;

/**
 * shiroikuma-termux (Phase 4): the live-preview trigger of the 白い熊 Termux UI page.
 *
 * <p>Every change on the page (a slider tick, a colour-picker slider, a scheme pick) ends in
 * {@link #changed(Context)}, which — at most once per {@value #THROTTLE_MS} ms, trailing, so the
 * last change always lands — sends upstream's own reload broadcast,
 * {@link TermuxActivity#updateTermuxActivityStyling(Context, boolean)} with {@code recreate=false}.
 * TermuxActivity's receiver for {@code ACTION_RELOAD_STYLE} is registered in {@code onStart()} and
 * unregistered in {@code onStop()}; the page's theme is {@code windowIsTranslucent}, so while it is
 * in front the activity underneath is paused but never stopped, the receiver stays registered and
 * {@code mIsVisible} stays true — {@code reloadActivityStyling(false)} then reloads the properties,
 * rebuilds the extra keys, re-reads {@code ~/.termux/colors.properties} + {@code font.ttf}
 * ({@code checkForFontAndColors}) and runs our {@link ShiroikumaChrome#apply} hook.
 *
 * <p>Main thread only (the page's widgets call it from there).
 */
public final class ShiroikumaStyle {

    private static final long THROTTLE_MS = 80;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Context sAppContext;
    private static long sLastSentUptime;
    private static boolean sPending;

    private ShiroikumaStyle() {
    }

    public static void changed(@NonNull Context context) {
        sAppContext = context.getApplicationContext();
        if (sPending) return; // a trailing send is already scheduled; it will read the newest state
        long now = SystemClock.uptimeMillis();
        long due = sLastSentUptime + THROTTLE_MS;
        if (now >= due) {
            send();
        } else {
            sPending = true;
            HANDLER.postAtTime(ShiroikumaStyle::flush, due);
        }
    }

    private static void flush() {
        sPending = false;
        send();
    }

    private static void send() {
        sLastSentUptime = SystemClock.uptimeMillis();
        if (sAppContext != null) TermuxActivity.updateTermuxActivityStyling(sAppContext, false);
    }
}
