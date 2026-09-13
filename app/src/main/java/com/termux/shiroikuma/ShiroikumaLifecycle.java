package com.termux.shiroikuma;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shiroikuma.ui.ShiroikumaChrome;

/**
 * shiroikuma-termux (Phase 4): the one process-wide hook — installed by a single line in
 * {@code TermuxApplication.onCreate()} — that gives every activity the house look without
 * touching its class.
 *
 * <ul>
 * <li>{@link #onActivityCreated}: runs inside {@code super.onCreate()}, i.e. before the activity's
 *     own {@code setContentView}, and applies {@code ThemeOverlay.Shiroikuma.Activity} to the
 *     activity theme with force — so the platform / AppCompat {@code AlertDialog}s upstream builds
 *     come out black/yellow with the bordered {@code shiroikuma_dialog_bg}. For TermuxActivity it
 *     also writes the house {@code colors.properties} on first run ({@link ShiroikumaDefaults}),
 *     ahead of {@code checkForFontAndColors()} reading it.</li>
 * <li>{@link #onActivityStarted} / {@link #onActivityResumed}: the status/navigation bars and the
 *     {@code @id/toolbar} of every activity but TermuxActivity (whose translucent bars and inset
 *     logic upstream relies on) take the Toolbar / status bar section's colours; resumed again
 *     because options menus (Report's share/copy) are inflated after the first start.</li>
 * </ul>
 */
public final class ShiroikumaLifecycle implements Application.ActivityLifecycleCallbacks {

    private ShiroikumaLifecycle() {
    }

    public static void install(@NonNull Application application) {
        application.registerActivityLifecycleCallbacks(new ShiroikumaLifecycle());
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        activity.getTheme().applyStyle(R.style.ThemeOverlay_Shiroikuma_Activity, true);
        if (activity instanceof TermuxActivity) ShiroikumaDefaults.ensure(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (activity instanceof TermuxActivity) return;
        ShiroikumaChrome.applyStatusBars(activity);
        ShiroikumaChrome.applyToolbar(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof TermuxActivity) return;
        activity.getWindow().getDecorView().post(() -> {
            if (!activity.isFinishing()) ShiroikumaChrome.applyToolbar(activity);
        });
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
