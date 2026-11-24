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
 * colour swatch, paints the swatch in {@link #setColor}'s colour: 28 dp, 1.5 dp stroke in the
 * 「Swatch frame」 colour, 4 dp radius. {@link #setValueText} shows a short value (a colour's hex)
 * at the right of the row, so the summary stays free for the row's explanation;
 * {@link #setSummaryValue} says the summary itself is a value (a path, a name), not an explanation
 * — {@link ShiroikumaRows} then draws it in the value colour, without the left rule.
 */
public class ShiroikumaPreference extends Preference {

    @Nullable private Integer mColor;
    @Nullable private Runnable mOnLongClick;
    @Nullable private CharSequence mValueText;
    private boolean mSummaryValue;

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

    public void setValueText(@Nullable CharSequence value) {
        mValueText = value;
        notifyChanged();
    }

    public void setSummaryValue(boolean summaryIsValue) {
        mSummaryValue = summaryIsValue;
    }

    public boolean isSummaryValue() {
        return mSummaryValue;
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
                d.setStroke(Math.max(1, Math.round(1.5f * density)), ShiroikumaTheme.color(getContext(), ShiroikumaTheme.SWATCH_FRAME));
                d.setCornerRadius(4 * density);
                swatch.setBackground(d);
                swatch.setVisibility(View.VISIBLE);
            }
        }
        View valueView = holder.findViewById(com.termux.R.id.sk_value);
        if (valueView instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) valueView;
            tv.setText(mValueText);
            tv.setTextColor(ShiroikumaTheme.color(getContext(), ShiroikumaTheme.PAGE_VALUE));
            tv.setVisibility(mValueText == null ? View.GONE : View.VISIBLE);
        }
        final Runnable longClick = mOnLongClick;
        holder.itemView.setLongClickable(longClick != null);
        holder.itemView.setOnLongClickListener(longClick == null ? null : v -> {
            longClick.run();
            return true;
        });
    }
}
