package com.termux.shiroikuma.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.termux.R;
import com.termux.shiroikuma.ui.TerminalStyleFiles.Scheme;

import java.util.List;

import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux (Phase 4): the colour-scheme picker — the 122 termux-styling schemes plus the
 * house one, each row drawn in that scheme's own foreground on its own background (with a cursor
 * block in its cursor colour), a ✓ on the current one. Picking writes every key of the scheme to
 * ~/.termux/colors.properties.
 */
public final class ShiroikumaSchemePicker {

    public interface Callback {
        void onPick(@NonNull Scheme scheme);
    }

    private ShiroikumaSchemePicker() {
    }

    public static void show(@NonNull Activity activity, @NonNull Callback callback) {
        final Context ctx = activity;
        final float density = ctx.getResources().getDisplayMetrics().density;
        final List<Scheme> schemes = TerminalStyleFiles.bundledSchemes(ctx);
        final String current = ShiroikumaTheme.getString(ctx, TERMINAL_SCHEME);
        final String houseLabel = ctx.getString(R.string.shiroikuma_ui_terminal_scheme_house);

        LinearLayout box = ShiroikumaDialogs.box(ctx);
        box.setPadding(ShiroikumaDialogs.dp(ctx, 12), ShiroikumaDialogs.dp(ctx, 12), ShiroikumaDialogs.dp(ctx, 12), ShiroikumaDialogs.dp(ctx, 8));
        TextView title = ShiroikumaDialogs.text(ctx, ctx.getString(R.string.shiroikuma_ui_scheme_title), 19, color(ctx, DIALOG_TITLE), true);
        title.setPadding(ShiroikumaDialogs.dp(ctx, 8), 0, ShiroikumaDialogs.dp(ctx, 8), ShiroikumaDialogs.dp(ctx, 8));
        box.addView(title);

        // The panel's ScrollView cannot host a ListView; the dialog gets the list directly and the
        // box a fixed, screen-relative height instead.
        final ListView list = new ListView(ctx);
        list.setDivider(null);
        list.setDividerHeight(Math.round(4 * density));
        list.setSelector(android.R.color.transparent);
        int maxHeight = Math.round(ctx.getResources().getDisplayMetrics().heightPixels * 0.6f);
        box.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight));

        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(wrap(ctx, box)).create();
        ShiroikumaDialogs.transparentWindow(dialog);

        list.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return schemes.size();
            }

            @Override
            public Object getItem(int position) {
                return schemes.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                Scheme s = schemes.get(position);
                TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(ctx);
                boolean selected = s.name.equals(current);
                String label = TerminalStyleFiles.HOUSE_SCHEME.equals(s.name) ? houseLabel : s.name;
                CharSequence text = (selected ? "✓  " : "") + label + "  ";
                android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(text);
                int start = sb.length();
                sb.append("▮");
                sb.setSpan(new android.text.style.ForegroundColorSpan(s.cursor), start, sb.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(sb);
                tv.setTextColor(s.foreground);
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                int p = Math.round(10 * density);
                tv.setPadding(p + p / 2, p, p + p / 2, p);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(s.background);
                bg.setStroke(Math.max(1, Math.round(density)), s.foreground);
                bg.setCornerRadius(4 * density);
                tv.setBackground(bg);
                return tv;
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            callback.onPick(schemes.get(position));
        });
        int sel = -1;
        for (int i = 0; i < schemes.size(); i++) {
            if (schemes.get(i).name.equals(current)) {
                sel = i;
                break;
            }
        }
        if (sel > 0) list.setSelection(Math.max(0, sel - 2));

        LinearLayout buttons = ShiroikumaDialogs.buttonRow(ctx);
        buttons.setPadding(0, ShiroikumaDialogs.dp(ctx, 10), ShiroikumaDialogs.dp(ctx, 8), 0);
        ShiroikumaDialogs.addPill(buttons, ShiroikumaDialogs.pill(ctx, ctx.getString(android.R.string.cancel), v -> dialog.dismiss()));
        box.addView(buttons);

        dialog.show();
    }

    /** 10 dp around the box, like ShiroikumaDialogs.panel, but without the ScrollView. */
    private static View wrap(@NonNull Context ctx, @NonNull View box) {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(ctx);
        int m = ShiroikumaDialogs.dp(ctx, 10);
        frame.setPadding(m, m, m, m);
        frame.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return frame;
    }
}
