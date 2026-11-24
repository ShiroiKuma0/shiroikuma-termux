package com.termux.shiroikuma;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shiroikuma.ui.ShiroikumaChrome;
import com.termux.shiroikuma.ui.ShiroikumaSelectionToolbar;
import com.termux.shiroikuma.ui.ShiroikumaUiActivity;
import com.termux.view.textselection.TextSelectionCursorController;

/**
 * shiroikuma-termux (Phase 4): the one process-wide hook — installed by a single line in
 * {@code TermuxApplication.onCreate()} — that gives every activity the house look without
 * touching its class.
 *
 * <ul>
 * <li>{@link #install}: re-asserts the stored app language ({@link AppLanguage}) and hands {@code TextSelectionCursorController} the house text-selection
 *     toolbar ({@link ShiroikumaSelectionToolbar}) in place of the platform floating one.</li>
 * <li>{@link #onActivityCreated}: runs inside {@code super.onCreate()}, i.e. before the activity's
 *     own {@code setContentView}, and applies {@code ThemeOverlay.Shiroikuma.Activity} to the
 *     activity theme with force — so the platform / AppCompat {@code AlertDialog}s upstream builds
 *     come out black/yellow with the bordered {@code shiroikuma_dialog_bg}. Every activity but
 *     TermuxActivity and the UI page (which draws itself from its colour roles) — Settings, Help, About / Report, the widget's shortcut picker, the float
 *     permission page) gets {@code ThemeOverlay.Shiroikuma.Secondary} on top: black ground, yellow
 *     text, yellow controls — what their theme paints and no view-by-view hook reaches. For
 *     TermuxActivity it also writes the house {@code colors.properties} on first run
 *     ({@link ShiroikumaDefaults}), ahead of {@code checkForFontAndColors()} reading it.</li>
 * <li>{@link #onActivityStarted} / {@link #onActivityResumed}: the status/navigation bars and the
 *     {@code @id/toolbar} of every activity but TermuxActivity (whose translucent bars and inset
 *     logic upstream relies on) take the Toolbar / status bar section's colours; resumed again
 *     because options menus (Report's share/copy) are inflated after the first start. For
 *     TermuxActivity the first resume of a process runs {@link ExternalAppsAccess#maybeWarn}.</li>
 * </ul>
 */
public final class ShiroikumaLifecycle implements Application.ActivityLifecycleCallbacks {

    private ShiroikumaLifecycle() {
    }

    public static void install(@NonNull Application application) {
        AppLanguage.reassert(application); // the stored app language, before the first activity
        application.registerActivityLifecycleCallbacks(new ShiroikumaLifecycle());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            TextSelectionCursorController.setFloatingActionModeFactory(ShiroikumaSelectionToolbar::start);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        AppLanguage.onActivityCreated(activity);
        activity.getTheme().applyStyle(R.style.ThemeOverlay_Shiroikuma_Activity, true);
        if (activity instanceof TermuxActivity) ShiroikumaDefaults.ensure(activity);
        // The UI page takes every colour from its roles in code — no static palette over it.
        else if (!(activity instanceof ShiroikumaUiActivity)) activity.getTheme().applyStyle(R.style.ThemeOverlay_Shiroikuma_Secondary, true);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (activity instanceof TermuxActivity) return;
        ShiroikumaChrome.applyStatusBars(activity);
        ShiroikumaChrome.applyToolbar(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof TermuxActivity) {
            // Once per process: the dialog that says allow-external-apps is off.
            activity.getWindow().getDecorView().post(() -> ExternalAppsAccess.maybeWarn(activity));
            return;
        }
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
