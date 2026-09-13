package com.termux.shiroikuma.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux (Phase 4): the house dialog vocabulary, ported from raikidoban's
 * {@code ExportImportPanel} helpers so the Export / Import panel of Phase 4b can reuse it as is.
 *
 * <p>A dialog we build is a transparent-window {@link AlertDialog} whose content is ONE bordered
 * rounded box ({@link #panelBackground}: the Dialogs section's background, border colour/width and
 * corner radius), yellow text, and pill buttons ({@link #pill}: corner 50 dp, dialog fill, 1.5 dp
 * stroke in the button colour, translucent ripple, no all-caps, {@code minWidth 0}).
 * {@link #styleAlertDialog} retrofits the same look onto an AppCompat AlertDialog built with
 * {@code setTitle/setMessage/set*Button} — after {@code show()}, because the buttons exist only then.
 */
public final class ShiroikumaDialogs {

    private ShiroikumaDialogs() {
    }

    public static int dp(@NonNull Context context, float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    // ---- building blocks ------------------------------------------------------------------------

    /** The bordered rounded panel every surface of our dialogs is drawn on. */
    @NonNull
    public static GradientDrawable panelBackground(@NonNull Context context) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(context, DIALOG_BG));
        int border = value(context, DIALOG_BORDER_DP);
        if (border > 0) bg.setStroke(Math.max(1, dp(context, border)), color(context, DIALOG_BORDER_COLOR));
        bg.setCornerRadius(dp(context, value(context, DIALOG_CORNER_DP)));
        return bg;
    }

    /** The context menu's box: the Menus section's colours, border and corners. */
    @NonNull
    public static GradientDrawable menuBackground(@NonNull Context context) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(context, MENU_BG));
        int border = value(context, MENU_BORDER_DP);
        if (border > 0) bg.setStroke(Math.max(1, dp(context, border)), color(context, MENU_BORDER_COLOR));
        bg.setCornerRadius(dp(context, value(context, MENU_CORNER_DP)));
        return bg;
    }

    /** A vertical box on the panel background with the usual padding. */
    @NonNull
    public static LinearLayout box(@NonNull Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(context, 22), dp(context, 20), dp(context, 22), dp(context, 16));
        box.setBackground(panelBackground(context));
        return box;
    }

    @NonNull
    public static TextView text(@NonNull Context context, @Nullable CharSequence s, int sizeSp, int color, boolean bold) {
        TextView tv = new TextView(context);
        tv.setText(s);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        return tv;
    }

    /** Title (19 sp bold, dialog title colour) + body (14 sp, dialog text colour) in a box. */
    @NonNull
    public static LinearLayout infoBox(@NonNull Context context, @Nullable CharSequence title, @Nullable CharSequence body) {
        LinearLayout box = box(context);
        if (title != null) box.addView(text(context, title, 19, color(context, DIALOG_TITLE), true));
        if (body != null) {
            TextView bodyView = text(context, body, 14, color(context, DIALOG_TEXT), false);
            bodyView.setPadding(0, title != null ? dp(context, 10) : 0, 0, 0);
            box.addView(bodyView);
        }
        return box;
    }

    @NonNull
    public static View divider(@NonNull Context context, int topGapDp) {
        View v = new View(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 1)));
        lp.topMargin = dp(context, topGapDp);
        v.setLayoutParams(lp);
        v.setBackgroundColor(color(context, DIALOG_BORDER_COLOR));
        v.setAlpha(0.4f);
        return v;
    }

    /** An ArcaneChat-style round pill: dialog fill, thin stroke and text in the button colour, ripple. */
    @NonNull
    public static Button pill(@NonNull Context context, @Nullable CharSequence label, @Nullable View.OnClickListener onClick) {
        Button b = new Button(context);
        b.setText(label);
        b.setAllCaps(false);
        int accent = color(context, DIALOG_BUTTON);
        b.setTextColor(accent);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(context, DIALOG_BG));
        bg.setStroke(Math.max(1, dp(context, 1.5f)), accent);
        bg.setCornerRadius(dp(context, 50));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(accent, 0x33)), bg, null));
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 8));
        b.setOnClickListener(onClick);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    /** A right-aligned button row (16 dp above) — add pills to it in order. */
    @NonNull
    public static LinearLayout buttonRow(@NonNull Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 16), 0, 0);
        return row;
    }

    /** 8 dp gap between pills. */
    public static void addPill(@NonNull LinearLayout row, @NonNull Button pill) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (row.getChildCount() > 0) lp.setMarginStart(dp(row.getContext(), 8));
        row.addView(pill, lp);
    }

    // ---- dialogs -------------------------------------------------------------------------------

    /**
     * A transparent-window dialog around {@code content} (which carries its own panel background),
     * scrollable, 10 dp off the window edge. Not yet shown.
     */
    @NonNull
    public static AlertDialog panel(@NonNull Activity activity, @NonNull View content, boolean cancelable) {
        ScrollView scroll = new ScrollView(activity);
        int m = dp(activity, 10);
        scroll.setPadding(m, m, m, m);
        scroll.setClipToPadding(false);
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll).create();
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        transparentWindow(dialog);
        return dialog;
    }

    public static void transparentWindow(@NonNull AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    /** Title, body, one OK pill right-aligned; {@code onOk} runs after dismissal (may be null). */
    @NonNull
    public static AlertDialog infoDialog(@NonNull Activity activity, @Nullable CharSequence title, @Nullable CharSequence body,
                                         boolean cancelable, @Nullable Runnable onOk) {
        LinearLayout box = infoBox(activity, title, body);
        final AlertDialog dialog = panel(activity, box, cancelable);
        LinearLayout buttons = buttonRow(activity);
        addPill(buttons, pill(activity, activity.getString(android.R.string.ok), v -> {
            dialog.dismiss();
            if (onOk != null) onOk.run();
        }));
        box.addView(buttons);
        dialog.show();
        return dialog;
    }

    /** Title, body, Cancel + one action pill; {@code onConfirm} runs after dismissal. */
    @NonNull
    public static AlertDialog confirmDialog(@NonNull Activity activity, @Nullable CharSequence title, @Nullable CharSequence body,
                                            @NonNull CharSequence action, @NonNull Runnable onConfirm) {
        LinearLayout box = infoBox(activity, title, body);
        final AlertDialog dialog = panel(activity, box, true);
        LinearLayout buttons = buttonRow(activity);
        addPill(buttons, pill(activity, activity.getString(android.R.string.cancel), v -> dialog.dismiss()));
        addPill(buttons, pill(activity, action, v -> {
            dialog.dismiss();
            onConfirm.run();
        }));
        box.addView(buttons);
        dialog.show();
        return dialog;
    }

    /**
     * Title, one yellow-on-black text field (Phase 4c: the floating terminal's notification text),
     * Cancel + OK pills; {@code onOk} gets the trimmed text (empty allowed — "back to default").
     */
    @NonNull
    public static AlertDialog inputDialog(@NonNull Activity activity, @Nullable CharSequence title, @Nullable CharSequence hint,
                                          @Nullable CharSequence initial, @NonNull Callback<String> onOk) {
        LinearLayout box = infoBox(activity, title, null);
        int ink = color(activity, DIALOG_TEXT);
        final EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setTextColor(ink);
        field.setHintTextColor(withAlpha(ink, 0x99));
        field.setHint(hint);
        field.setText(initial);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        field.setBackgroundTintList(ColorStateList.valueOf(color(activity, DIALOG_BUTTON)));
        if (initial != null) field.setSelection(field.getText().length());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(activity, 12);
        box.addView(field, lp);
        final AlertDialog dialog = panel(activity, box, true);
        LinearLayout buttons = buttonRow(activity);
        addPill(buttons, pill(activity, activity.getString(android.R.string.cancel), v -> dialog.dismiss()));
        addPill(buttons, pill(activity, activity.getString(android.R.string.ok), v -> {
            dialog.dismiss();
            onOk.call(field.getText().toString().trim());
        }));
        box.addView(buttons);
        Window window = dialog.getWindow();
        if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        field.requestFocus();
        return dialog;
    }

    public interface Callback<T> {
        void call(T value);
    }

    /**
     * Retrofits the house look onto an AppCompat AlertDialog built the classic way: the window
     * becomes the panel (inset 16 dp), title / message take the dialog colours, the buttons become
     * pills. Call after {@code show()}.
     */
    public static void styleAlertDialog(@NonNull AlertDialog dialog) {
        Context ctx = dialog.getContext();
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new InsetDrawable(panelBackground(ctx), dp(ctx, 16)));

        TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (title != null) title.setTextColor(color(ctx, DIALOG_TITLE));
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(color(ctx, DIALOG_TEXT));

        int accent = color(ctx, DIALOG_BUTTON);
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button b = dialog.getButton(which);
            if (b == null) continue;
            GradientDrawable pillBg = new GradientDrawable();
            pillBg.setColor(color(ctx, DIALOG_BG));
            pillBg.setCornerRadius(dp(ctx, 50));
            pillBg.setStroke(Math.max(1, dp(ctx, 1.5f)), accent);
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(accent, 0x33)), pillBg, null));
            b.setTextColor(accent);
            b.setAllCaps(false);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(dp(ctx, 20), dp(ctx, 6), dp(ctx, 20), dp(ctx, 6));
            ViewGroup.LayoutParams lp = b.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).setMarginStart(dp(ctx, 8));
                b.setLayoutParams(lp);
            }
        }
    }
}
