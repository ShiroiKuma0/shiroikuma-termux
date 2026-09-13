package com.termux.shiroikuma.ui;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.termux.R;
import com.termux.shiroikuma.ui.TerminalStyleFiles.FontOption;

import java.util.List;

import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux (Phase 4): the terminal font picker — the kxkb / raikidoban list where every
 * entry is drawn in its own typeface (18 sp, dialog text colour), a ✓ prefix on the current one,
 * "Monospace (default)" first, then the bundled fonts, then ~/.termux/fonts/*, and an "Add font…"
 * entry that opens the document picker.
 */
public final class ShiroikumaFontPicker {

    public interface Callback {
        void onPick(@NonNull FontOption option);

        void onAddFont();
    }

    private ShiroikumaFontPicker() {
    }

    public static void show(@NonNull Activity activity, @NonNull Callback callback) {
        final Context ctx = activity;
        int padH = ShiroikumaDialogs.dp(ctx, 20);
        int padV = ShiroikumaDialogs.dp(ctx, 12);
        int ink = color(ctx, DIALOG_TEXT);
        String current = TerminalStyleFiles.currentFontId(ctx);

        LinearLayout box = ShiroikumaDialogs.box(ctx);
        box.setPadding(ShiroikumaDialogs.dp(ctx, 2), ShiroikumaDialogs.dp(ctx, 12), ShiroikumaDialogs.dp(ctx, 2), ShiroikumaDialogs.dp(ctx, 8));
        TextView title = ShiroikumaDialogs.text(ctx, ctx.getString(R.string.shiroikuma_ui_terminal_font), 19, color(ctx, DIALOG_TITLE), true);
        title.setPadding(padH, 0, padH, padV / 2);
        box.addView(title);

        final AlertDialog dialog = ShiroikumaDialogs.panel(activity, box, true);

        List<FontOption> options = TerminalStyleFiles.fontOptions(ctx);
        for (final FontOption option : options) {
            boolean selected = option.id().equals(current);
            TextView row = new TextView(ctx);
            row.setText((selected ? "✓  " : "") + option.label);
            row.setTextColor(ink);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            row.setTypeface(TerminalStyleFiles.typeface(ctx, option));
            row.setPadding(padH, padV, padH, padV);
            row.setBackgroundResource(selectableBackground(ctx));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onPick(option);
            });
            box.addView(row);
        }

        TextView add = new TextView(ctx);
        add.setText(R.string.shiroikuma_ui_terminal_font_add);
        add.setTextColor(color(ctx, DIALOG_BUTTON));
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        add.setPadding(padH, padV, padH, padV);
        add.setBackgroundResource(selectableBackground(ctx));
        add.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onAddFont();
        });
        box.addView(add);

        dialog.show();
    }

    static int selectableBackground(@NonNull Context ctx) {
        TypedValue tv = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId;
    }
}
