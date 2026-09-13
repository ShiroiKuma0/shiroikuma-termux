package com.termux.shiroikuma.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * shiroikuma-termux (Phase 4): the seekbar row of the 白い熊 Termux UI page (kxkb's layout:
 * title, dim summary, the slider with its live value on the right). Stores nothing itself — the
 * fragment reads {@link #getValue}, listens with {@link #setOnValueChanged} (called on every user
 * tick, i.e. live) and pushes {@link #setValue} back. Range {@code [skMin, skMax]} in {@code skUnit};
 * with {@code skZeroLabel} set, one extra step at the left stores 0 and reads as that label
 * ("default"). Long-press on the title restores the default via {@link #setOnLongClick}.
 */
public class ShiroikumaSeekBarPreference extends Preference {

    public interface OnValueChanged {
        void onValueChanged(int value);
    }

    private int mMin;
    private int mMax = 100;
    @Nullable private String mUnit;
    @Nullable private String mZeroLabel;
    private int mValue;
    @Nullable private OnValueChanged mListener;
    @Nullable private Runnable mOnLongClick;

    public ShiroikumaSeekBarPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ShiroikumaSeekBarPreference);
            mMin = a.getInt(R.styleable.ShiroikumaSeekBarPreference_skMin, 0);
            mMax = a.getInt(R.styleable.ShiroikumaSeekBarPreference_skMax, 100);
            mUnit = a.getString(R.styleable.ShiroikumaSeekBarPreference_skUnit);
            mZeroLabel = a.getString(R.styleable.ShiroikumaSeekBarPreference_skZeroLabel);
            a.recycle();
        }
        mValue = mMin;
    }

    public void setRange(int min, int max) {
        mMin = min;
        mMax = max;
        notifyChanged();
    }

    public int getMin() {
        return mMin;
    }

    public int getMax() {
        return mMax;
    }

    public int getValue() {
        return mValue;
    }

    /** Sets the value (clamped; 0 allowed when a zero label exists) and rebinds. */
    public void setValue(int value) {
        mValue = clamp(value);
        notifyChanged();
    }

    public void setOnValueChanged(@Nullable OnValueChanged listener) {
        mListener = listener;
    }

    public void setOnLongClick(@Nullable Runnable onLongClick) {
        mOnLongClick = onLongClick;
        notifyChanged();
    }

    private int clamp(int value) {
        if (mZeroLabel != null && value <= 0) return 0;
        return Math.max(mMin, Math.min(mMax, value));
    }

    private int toProgress(int value) {
        if (mZeroLabel != null) return value <= 0 ? 0 : value - mMin + 1;
        return value - mMin;
    }

    private int toValue(int progress) {
        if (mZeroLabel != null) return progress == 0 ? 0 : mMin + progress - 1;
        return mMin + progress;
    }

    @NonNull
    private String label(int value) {
        if (mZeroLabel != null && value <= 0) return mZeroLabel;
        return mUnit == null ? String.valueOf(value) : value + " " + mUnit;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false);
        final SeekBar seekBar = (SeekBar) holder.findViewById(R.id.seekbar);
        final TextView valueView = (TextView) holder.findViewById(R.id.seekbar_value);
        if (seekBar == null) return;
        seekBar.setOnSeekBarChangeListener(null);
        seekBar.setMax(mMax - mMin + (mZeroLabel != null ? 1 : 0));
        seekBar.setProgress(toProgress(mValue));
        seekBar.setProgressTintList(ColorStateList.valueOf(ShiroikumaTheme.YELLOW));
        seekBar.setThumbTintList(ColorStateList.valueOf(ShiroikumaTheme.YELLOW));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(ShiroikumaTheme.YELLOW_DIM));
        seekBar.setEnabled(isEnabled());
        if (valueView != null) valueView.setText(label(mValue));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                mValue = toValue(progress);
                if (valueView != null) valueView.setText(label(mValue));
                if (mListener != null) mListener.onValueChanged(mValue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        final Runnable longClick = mOnLongClick;
        holder.itemView.setLongClickable(longClick != null);
        holder.itemView.setOnLongClickListener(longClick == null ? null : v -> {
            longClick.run();
            return true;
        });
    }
}
