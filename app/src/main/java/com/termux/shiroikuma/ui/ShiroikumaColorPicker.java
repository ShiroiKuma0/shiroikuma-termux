package com.termux.shiroikuma.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.termux.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux (Phase 4): the house ARGB colour picker — a Java port of kxkb's
 * {@code ColorPicker.kt}. A row of up to {@value #MAX_RECENT} one-click swatches (recently chosen
 * colours, persisted in {@code shiroikuma_ui/recent_colors}, seeded with black, yellow, white and
 * dim yellow), a preview box showing the colour and its hex, then the A/R/G/B sliders (0–255)
 * which apply LIVE on every change; Cancel / outside reverts to the initial colour, OK remembers
 * the colour in the recent list.
 *
 * <p>{@code alphaEnabled=false} hides the A slider and pins alpha to 255 — the terminal's
 * {@code colors.properties} readers accept {@code #RRGGBB} only.
 */
public final class ShiroikumaColorPicker {

    public interface Callback {
        void onColor(int argb);
    }

    private static final int MAX_RECENT = 8;

    private ShiroikumaColorPicker() {
    }

    public static void show(@NonNull Activity activity, @NonNull CharSequence title, int initial,
                            boolean alphaEnabled, @NonNull Callback onColor) {
        final Context ctx = activity;
        final float density = ctx.getResources().getDisplayMetrics().density;
        final int ink = color(ctx, DIALOG_TEXT);
        final int accent = color(ctx, DIALOG_BUTTON);

        final int[] argb = {
            alphaEnabled ? Color.alpha(initial) : 255, Color.red(initial), Color.green(initial), Color.blue(initial)
        };
        final List<SeekBar> sliders = new ArrayList<>();

        final TextView preview = new TextView(ctx);
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        preview.setMinHeight(Math.round(52 * density));
        preview.setTypeface(android.graphics.Typeface.MONOSPACE);

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            int c = Color.argb(argb[0], argb[1], argb[2], argb[3]);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(c);
            bg.setStroke(Math.max(1, Math.round(1.5f * density)), ink);
            bg.setCornerRadius(4 * density);
            preview.setBackground(bg);
            double luminance = 0.299 * argb[1] + 0.587 * argb[2] + 0.114 * argb[3];
            preview.setTextColor(luminance < 128 || argb[0] < 128 ? Color.WHITE : Color.BLACK);
            preview.setText(alphaEnabled
                ? String.format(Locale.ROOT, "#%02X%02X%02X%02X", argb[0], argb[1], argb[2], argb[3])
                : String.format(Locale.ROOT, "#%02X%02X%02X", argb[1], argb[2], argb[3]));
        };

        // one-click swatches
        LinearLayout swatchRow = new LinearLayout(ctx);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(Gravity.CENTER_VERTICAL);
        int sizePx = Math.round(32 * density);
        int gap = Math.round(6 * density);
        for (final int sw : recent(ctx)) {
            View v = new View(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
            lp.setMarginEnd(gap);
            v.setLayoutParams(lp);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(alphaEnabled ? sw : (sw | 0xFF000000));
            bg.setStroke(Math.max(1, Math.round(1.5f * density)), ink);
            bg.setCornerRadius(4 * density);
            v.setBackground(bg);
            v.setOnClickListener(x -> {
                argb[0] = alphaEnabled ? Color.alpha(sw) : 255;
                argb[1] = Color.red(sw);
                argb[2] = Color.green(sw);
                argb[3] = Color.blue(sw);
                int i = 0;
                if (alphaEnabled) sliders.get(i++).setProgress(argb[0]);
                sliders.get(i++).setProgress(argb[1]);
                sliders.get(i++).setProgress(argb[2]);
                sliders.get(i).setProgress(argb[3]);
                refresh[0].run();
                onColor.onColor(Color.argb(argb[0], argb[1], argb[2], argb[3]));
            });
            swatchRow.addView(v);
        }

        int pad = Math.round(20 * density);
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, 0);
        if (swatchRow.getChildCount() > 0) layout.addView(spaced(swatchRow, 18, density));
        layout.addView(spaced(preview, 18, density));
        String[] labels = {"A", "R", "G", "B"};
        for (int ch = alphaEnabled ? 0 : 1; ch < 4; ch++) {
            final int channel = ch;
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(ctx);
            label.setText(labels[ch]);
            label.setTextColor(ink);
            label.setWidth(Math.round(22 * density));
            row.addView(label);
            SeekBar seek = new SeekBar(ctx);
            seek.setMax(255);
            seek.setProgress(argb[ch]);
            seek.setProgressTintList(ColorStateList.valueOf(accent));
            seek.setThumbTintList(ColorStateList.valueOf(accent));
            seek.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (!fromUser) return;
                    argb[channel] = p;
                    refresh[0].run();
                    onColor.onColor(Color.argb(argb[0], argb[1], argb[2], argb[3]));
                }

                @Override
                public void onStartTrackingTouch(SeekBar sb) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar sb) {
                }
            });
            sliders.add(seek);
            row.addView(seek);
            layout.addView(spaced(row, ch < 3 ? 8 : 0, density));
        }
        refresh[0].run();

        LinearLayout box = ShiroikumaDialogs.box(ctx);
        box.addView(ShiroikumaDialogs.text(ctx, title, 19, color(ctx, DIALOG_TITLE), true));
        box.addView(layout);
        final AlertDialog dialog = ShiroikumaDialogs.panel(activity, box, true);
        final boolean[] kept = {false};
        LinearLayout buttons = ShiroikumaDialogs.buttonRow(ctx);
        ShiroikumaDialogs.addPill(buttons, ShiroikumaDialogs.pill(ctx, ctx.getString(android.R.string.cancel), v -> dialog.cancel()));
        ShiroikumaDialogs.addPill(buttons, ShiroikumaDialogs.pill(ctx, ctx.getString(android.R.string.ok), v -> {
            kept[0] = true;
            int c = Color.argb(argb[0], argb[1], argb[2], argb[3]);
            onColor.onColor(c);
            remember(ctx, c);
            dialog.dismiss();
        }));
        box.addView(buttons);
        dialog.setOnDismissListener(d -> {
            if (!kept[0]) onColor.onColor(initial);
        });
        dialog.show();
    }

    private static View spaced(View view, int bottomDp, float density) {
        LinearLayout.LayoutParams lp = view.getLayoutParams() instanceof LinearLayout.LayoutParams
            ? (LinearLayout.LayoutParams) view.getLayoutParams()
            : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(bottomDp * density);
        view.setLayoutParams(lp);
        return view;
    }

    /** Stored recents first, then the seeds, de-duplicated, at most MAX_RECENT. */
    @NonNull
    private static List<Integer> recent(@NonNull Context ctx) {
        Set<Integer> out = new LinkedHashSet<>(stored(ctx));
        out.add(BLACK);
        out.add(YELLOW);
        out.add(WHITE);
        out.add(YELLOW_DIM);
        List<Integer> list = new ArrayList<>(out);
        return list.size() > MAX_RECENT ? list.subList(0, MAX_RECENT) : list;
    }

    @NonNull
    private static List<Integer> stored(@NonNull Context ctx) {
        List<Integer> out = new ArrayList<>();
        String csv = getString(ctx, RECENT_COLORS);
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            try {
                out.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static void remember(@NonNull Context ctx, int color) {
        Set<Integer> out = new LinkedHashSet<>();
        out.add(color);
        out.addAll(stored(ctx));
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int c : out) {
            if (n++ >= MAX_RECENT) break;
            if (sb.length() > 0) sb.append(',');
            sb.append(c);
        }
        setString(ctx, RECENT_COLORS, sb.toString());
    }

    /** The dialog title resource shared by every colour row. */
    public static CharSequence defaultTitle(@NonNull Context ctx) {
        return ctx.getString(R.string.shiroikuma_ui_pick_colour);
    }
}
