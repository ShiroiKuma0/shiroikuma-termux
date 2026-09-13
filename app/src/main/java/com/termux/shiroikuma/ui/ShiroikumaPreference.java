package com.termux.shiroikuma.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * shiroikuma-termux (Phase 4): the plain row of the 白い熊 Termux UI page — a {@link Preference}
 * that can carry a long-press action (restore the default) and, when its widget layout is the
 * colour swatch, paints the swatch in {@link #setColor}'s colour: 38 dp, 1.5 dp stroke in the
 * house yellow, 4 dp radius.
 */
public class ShiroikumaPreference extends Preference {

    @Nullable private Integer mColor;
    @Nullable private Runnable mOnLongClick;

    public ShiroikumaPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ShiroikumaPreference(@NonNull Context context) {
        super(context);
    }

    public void setColor(@Nullable Integer argb) {
        mColor = argb;
        notifyChanged();
    }

    public void setOnLongClick(@Nullable Runnable onLongClick) {
        mOnLongClick = onLongClick;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View swatch = holder.findViewById(com.termux.R.id.color_swatch);
        if (swatch != null) {
            if (mColor == null) {
                swatch.setVisibility(View.GONE);
            } else {
                float density = getContext().getResources().getDisplayMetrics().density;
                GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.RECTANGLE);
                d.setColor(mColor);
                d.setStroke(Math.max(1, Math.round(1.5f * density)), ShiroikumaTheme.YELLOW);
                d.setCornerRadius(4 * density);
                swatch.setBackground(d);
                swatch.setVisibility(View.VISIBLE);
            }
        }
        final Runnable longClick = mOnLongClick;
        holder.itemView.setLongClickable(longClick != null);
        holder.itemView.setOnLongClickListener(longClick == null ? null : v -> {
            longClick.run();
            return true;
        });
    }
}
