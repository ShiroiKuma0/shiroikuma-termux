package com.termux.shiroikuma.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * shiroikuma-termux (Phase 4b): the automation-token row of the Export / Import section (a port
 * of arcanechat's): tapping the row copies the token, and a "Regenerate" pill sits on the right
 * inside the row's widget frame ({@code @layout/preference_widget_regenerate}).
 */
public class AutomationTokenPreference extends ShiroikumaPreference {

    @Nullable private Runnable mOnRegenerate;

    public AutomationTokenPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AutomationTokenPreference(@NonNull Context context) {
        super(context);
    }

    public void setOnRegenerateListener(@Nullable Runnable onRegenerate) {
        mOnRegenerate = onRegenerate;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View button = holder.findViewById(R.id.automation_regenerate);
        if (button == null) return;
        // the pill consumes the touch, so tapping it does not also fire the row's copy action
        button.setOnClickListener(v -> {
            if (mOnRegenerate != null) mOnRegenerate.run();
        });
    }
}
