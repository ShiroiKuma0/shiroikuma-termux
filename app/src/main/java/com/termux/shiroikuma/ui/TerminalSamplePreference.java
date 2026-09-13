package com.termux.shiroikuma.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * shiroikuma-termux (Phase 4): the live sample at the top of the Terminal section —
 * {@code $ ls -la ~ ▮} in the current foreground on the current background, the cursor block in
 * the cursor colour, in the current font at the current size (Termux font sizes are pixels).
 * Re-read from the files on every bind; the fragment calls {@link #refresh()} after a change.
 */
public class TerminalSamplePreference extends Preference {

    public TerminalSamplePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setSelectable(false);
    }

    public void refresh() {
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView sample = (TextView) holder.findViewById(R.id.terminal_sample);
        if (sample == null) return;
        Context ctx = getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        int bg = TerminalStyleFiles.currentColor(TerminalStyleFiles.KEY_BACKGROUND);
        int fg = TerminalStyleFiles.currentColor(TerminalStyleFiles.KEY_FOREGROUND);
        int cursor = TerminalStyleFiles.currentColor(TerminalStyleFiles.KEY_CURSOR);

        SpannableStringBuilder sb = new SpannableStringBuilder(ctx.getString(R.string.shiroikuma_ui_terminal_sample_text));
        sb.append(' ');
        int start = sb.length();
        sb.append('▮');
        sb.setSpan(new ForegroundColorSpan(cursor), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sample.setText(sb);
        sample.setTextColor(fg);
        sample.setTypeface(TerminalStyleFiles.currentTypeface());

        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(ctx);
        int px = prefs != null ? prefs.getFontSize() : Math.round(12 * density);
        sample.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);

        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setStroke(Math.max(1, Math.round(density)), fg);
        d.setCornerRadius(4 * density);
        sample.setBackground(d);
    }
}
