package com.termux.shiroikuma.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

import static com.termux.shiroikuma.ui.ShiroikumaIcons.*;
import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux: draws every row of the 白い熊 Termux UI page by the black-yellow theme's
 * rules, in the one place all of them pass through — the list's adapter — from the page's colour
 * roles; the row layouts carry no colour.
 *
 * <ul>
 * <li>an icon at the start of every label, coloured by what the row does and sized by the label
 *     (1.25 × its text size, never under 18 dp);</li>
 * <li>a row that opens or runs something ({@link ShiroikumaActionPreference}) is an outlined pill
 *     that hugs its label, its icon inside, a › when it opens something, its explanation beneath;
 *     the main action of a section is 3 dp and bold, every other 1.25 dp; a removing one is red;</li>
 * <li>control rows (a switch, a swatch, a slider, a value that opens a picker) stay plain;</li>
 * <li>a row with an explanation: bold label, dim-gold text, a 2 dp dim-gold rule at its left;</li>
 * <li>every switch is the outlined one: a 2 dp round outline with a solid dot, never filled;</li>
 * <li>no minimum height, 2 dp above and below (3 dp around a pill).</li>
 * </ul>
 * Section headings ({@link ShiroikumaSectionCategory}) and the chip index
 * ({@link ShiroikumaIndexPreference}) draw themselves with the helpers below.
 */
@SuppressLint("RestrictedApi")
public final class ShiroikumaRows {

    private ShiroikumaRows() {
    }

    /** The adapter {@code ShiroikumaUiFragment.onCreateAdapter} hands to its list. */
    public static final class Adapter extends PreferenceGroupAdapter {
        public Adapter(@NonNull PreferenceGroup group) {
            super(group);
        }

        @Override
        public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
            Preference pref = getItem(position);
            if (pref != null) bind(pref, holder);
        }
    }

    // ---- which icon, in which colour: told from the row's key -----------------------------------

    private static final int VIEW = 0, CONNECT = 1, SHARE = 2, CONTACT = 3, BLOCK = 4, DELETE = 5, OWN = 6;

    /** Exact keys first; the colour and size rows are told from their key's words below. */
    private static final Object[][] KEYS = {
        {"app_settings", ICON_GEAR, VIEW},
        {"app_language", ICON_GLOBE, VIEW},
        {"eximport", ICON_EXPORT, SHARE},
        {"export_dir", ICON_DIR, SHARE},
        {"last_export", ICON_CLOCK, SHARE},
        {"automation_enabled", ICON_BRANCH, CONNECT},
        {"automation_require_token", ICON_LOCK, BLOCK},
        {"automation_token", ICON_KEY, BLOCK},
        {"allow_external_apps", ICON_PLUG, CONNECT},
        {"external_apps_warn", ICON_BELL, VIEW},
        {"external_apps_callers", ICON_PERSON, CONTACT},
        {"external_apps_battery", ICON_BATTERY, CONNECT},
        {"external_apps_overlay", ICON_LAYERS, VIEW},
        {"terminal_scheme", ICON_GRID, VIEW},
        {"terminal_font_size", ICON_SIZE, VIEW},
        {"terminal_font", ICON_TEXT, VIEW},
        {"terminal_cursor", ICON_TERMINAL, VIEW},
        {"widget_refresh", ICON_RESET, CONNECT},
        {"widget_dynamic_create", ICON_ADD, CONNECT},
        {"widget_dynamic_remove", ICON_KILL, DELETE},
        {"float_open", ICON_WINDOW, VIEW},
        {"float_notification_text", ICON_BELL, VIEW},
        {"reset_order", ICON_SORT, CONNECT},
        {"reset_ui", ICON_RESET, CONNECT},
    };

    /** {icon, role} of a row. A colour role that carries a meaning shows its own colour. */
    @NonNull
    private static int[] spec(@Nullable String key) {
        if (key == null) return new int[]{ICON_DOT, VIEW};
        for (Object[] k : KEYS) if (key.equals(k[0])) return new int[]{(Integer) k[1], (Integer) k[2]};
        int role = key.startsWith("menu_icon_") || key.equals(MENU_DELETE) || key.startsWith("section_")
            || key.startsWith("switch_") || key.equals(PAGE_WARNING) || key.equals(SESSION_DEAD_TEXT) ? OWN : VIEW;
        int icon;
        if (key.startsWith("switch_")) icon = ICON_TOGGLE;
        else if (key.endsWith("_border_dp") || key.endsWith("_border_color") || key.endsWith("_frame") || key.equals(PAGE_DIVIDER)) icon = ICON_FRAME;
        else if (key.endsWith("_corner_dp")) icon = ICON_CORNER;
        else if (key.endsWith("_size_sp")) icon = ICON_SIZE;
        else if (key.endsWith("_bg") || key.endsWith("background")) icon = ICON_SQUARE;
        else if (key.equals(SLIDER) || key.equals(SLIDER_TRACK)) icon = ICON_SLIDERS;
        else if (key.equals(PAGE_WARNING)) icon = ICON_WARNING;
        else if (key.contains("icon") || key.startsWith("section_")) icon = ICON_DROP;
        else icon = ICON_TEXT; // a text / label / title / heading / value colour
        return new int[]{icon, role};
    }

    private static int roleColor(@NonNull Context c, int role, @Nullable String key) {
        switch (role) {
            case CONNECT:
                return color(c, MENU_ICON_CONNECT);
            case SHARE:
                return color(c, MENU_ICON_SHARE);
            case CONTACT:
                return color(c, MENU_ICON_CONTACT);
            case BLOCK:
                return color(c, MENU_ICON_BLOCK);
            case DELETE:
                return color(c, MENU_DELETE);
            case OWN:
                return key != null ? color(c, key) : color(c, PAGE_ICON);
            default:
                return color(c, PAGE_ICON);
        }
    }

    // ---- the row --------------------------------------------------------------------------------

    public static void bind(@NonNull Preference pref, @NonNull PreferenceViewHolder holder) {
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        if (pref instanceof ShiroikumaSectionCategory || pref instanceof ShiroikumaIndexPreference) return; // they draw themselves

        final Context c = pref.getContext();
        final View root = holder.itemView;
        final TextView title = textView(holder, android.R.id.title);
        final TextView summary = textView(holder, android.R.id.summary);
        final int layout = pref.getLayoutResource();
        final boolean enabled = pref.isEnabled();

        if (layout == R.layout.preference_shiroikuma_subheader) {
            if (title != null) {
                title.setTextColor(color(c, PAGE_HEADING));
                title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            }
            root.setPaddingRelative(dp(c, 32), dp(c, 8), dp(c, 16), dp(c, 2));
            return;
        }

        final ShiroikumaActionPreference action = pref instanceof ShiroikumaActionPreference ? (ShiroikumaActionPreference) pref : null;
        final boolean hasTitle = title != null && !TextUtils.isEmpty(pref.getTitle());
        final boolean hasSummary = summary != null && !TextUtils.isEmpty(pref.getSummary());
        final boolean value = pref instanceof ShiroikumaPreference && ((ShiroikumaPreference) pref).isSummaryValue();
        final boolean explained = hasTitle && hasSummary && !value;
        // 16 dp at the top of the page (the App settings pill), 32 dp in a section, 48 dp under a sub-heading
        final int level = !(pref.getParent() instanceof ShiroikumaSectionCategory) ? 0
            : layout == R.layout.preference_shiroikuma_indent2 || layout == R.layout.preference_shiroikuma_seekbar_l2 ? 2 : 1;

        // ---- label, icon, pill
        int gutter = 0;
        if (hasTitle) {
            final boolean destructive = action != null && action.isDestructive();
            int label = action == null ? color(c, PAGE_LABEL) : destructive ? color(c, MENU_DELETE) : color(c, PILL_TEXT);
            if (!enabled) label = disabled(label);
            title.setTextColor(label);
            title.setTypeface(Typeface.DEFAULT, (action != null ? action.isMain() : explained) ? Typeface.BOLD : Typeface.NORMAL);
            int[] spec = spec(pref.getKey());
            int iconColor = destructive ? color(c, MENU_DELETE) : roleColor(c, spec[1], pref.getKey());
            if (!enabled) iconColor = disabled(iconColor);
            gutter = labelIcon(title, spec[0], iconColor, action != null && action.opens(), label, 18);
            if (action != null) {
                int frame = destructive ? color(c, MENU_DELETE) : color(c, PILL_FRAME);
                title.setBackground(pill(c, enabled ? frame : disabled(frame), action.isMain()));
                title.setPaddingRelative(dp(c, PILL_PAD_DP), dp(c, 8), dp(c, PILL_PAD_DP), dp(c, 8));
                title.setGravity(Gravity.CENTER_VERTICAL);
                gutter += dp(c, PILL_PAD_DP); // the explanation lines up with the pill's label, not its edge
            }
        }

        // ---- explanation or value, and the rule of an explained row
        if (summary != null) {
            int ink = color(c, value ? PAGE_VALUE : PAGE_NOTE);
            summary.setTextColor(enabled ? ink : disabled(ink));
            summary.setAlpha(1f);
            summary.setPaddingRelative(hasTitle ? gutter : 0, action != null ? dp(c, 2) : 0, 0, 0);
        }
        View column = holder.findViewById(R.id.sk_text_column);
        if (column != null) {
            if (explained) {
                column.setBackground(new LeftRule(color(c, PAGE_NOTE), dp(c, 2)));
                column.setPaddingRelative(dp(c, 10), dp(c, 1), 0, dp(c, 1));
            } else {
                column.setBackground(null);
                column.setPaddingRelative(0, 0, 0, 0);
            }
        }

        // ---- only as tall as its content; the plain row presses as a whole, a pill by itself
        root.setMinimumHeight(0);
        int pad = dp(c, action != null ? 3 : 2);
        root.setPaddingRelative(dp(c, 16 + 16 * level), pad, dp(c, 16), pad);
        root.setBackground(action == null && pref.isSelectable() ? rowRipple(color(c, PAGE_LABEL)) : null);

        View sw = holder.findViewById(androidx.preference.R.id.switchWidget);
        if (sw instanceof SwitchCompat) outlineSwitch((SwitchCompat) sw);
    }

    @Nullable
    private static TextView textView(@NonNull PreferenceViewHolder holder, int id) {
        View v = holder.findViewById(id);
        return v instanceof TextView ? (TextView) v : null;
    }

    static int dp(@NonNull Context c, float v) {
        return ShiroikumaDialogs.dp(c, v);
    }

    // ---- shared drawing: icon in a label, pill, ripple, switch, rule ----------------------------

    private static final int PILL_PAD_DP = 14;

    /**
     * Puts {@code icon} (an {@code ICON_*}, or -1 for none) at the start of {@code label}, sized by
     * the label — 1.25 × its text size, never under {@code floorDp} — and, for a row that opens
     * something, a › at its end in {@code chevronColor}. Returns the gutter: how far the label's
     * text now starts after the label's own start, for whatever sits beneath it.
     */
    public static int labelIcon(@NonNull TextView label, int icon, int color, boolean chevron, int chevronColor, int floorDp) {
        Context c = label.getContext();
        int size = Math.max(Math.round(label.getTextSize() * 1.25f), dp(c, floorDp));
        int gap = size / 2;
        Drawable start = icon < 0 ? null : ShiroikumaIcons.ofPx(icon, color, size);
        Drawable end = chevron ? ShiroikumaIcons.ofPx(ICON_CHEVRON, chevronColor, Math.round(label.getTextSize() * 1.1f)) : null;
        label.setCompoundDrawablesRelativeWithIntrinsicBounds(start, null, end, null);
        label.setCompoundDrawablePadding(gap);
        return start == null ? 0 : size + gap;
    }

    /** An outlined pill, never filled: 3 dp when {@code main}, else 1.25 dp; the ripple inside it. */
    @NonNull
    public static Drawable pill(@NonNull Context c, int color, boolean main) {
        float radius = dp(c, 100);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.TRANSPARENT);
        frame.setCornerRadius(radius);
        frame.setStroke(Math.max(1, Math.round((main ? 3f : 1.25f) * c.getResources().getDisplayMetrics().density)), color);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radius);
        return new RippleDrawable(ColorStateList.valueOf(withAlpha(color, 0x4D)), frame, mask);
    }

    /** The press feedback of a plain row or a heading: the given colour at 30 %. */
    @NonNull
    public static Drawable rowRipple(int color) {
        return new RippleDrawable(ColorStateList.valueOf(withAlpha(color, 0x4D)), null, new ColorDrawable(Color.WHITE));
    }

    /**
     * The outlined switch on a {@link SwitchCompat}: a 52 × 28 dp round 2 dp outline with the
     * ground showing through and a solid dot (46 % of the height) — right and in the 「Switch, on」
     * colour when on, left and in the opaque 「Switch, off」 colour when off. The dot sits in a
     * {@link LayerDrawable} with layer insets, NOT an InsetDrawable: SwitchCompat takes an
     * InsetDrawable's insets for optical insets and shrinks the track by them.
     */
    public static void outlineSwitch(@NonNull SwitchCompat v) {
        Context c = v.getContext();
        int on = color(c, SWITCH_ON), off = color(c, SWITCH_OFF);
        ColorStateList colors = new ColorStateList(
            new int[][]{{-android.R.attr.state_enabled, android.R.attr.state_checked}, {-android.R.attr.state_enabled}, {android.R.attr.state_checked}, {}},
            new int[]{disabled(on), disabled(off), on, off});
        // The drawables once per view: setting them again on every rebind would cut the dot's travel.
        if (v.getTag(R.id.shiroikuma_switch_look) == null) {
            float d = c.getResources().getDisplayMetrics().density;
            int h = Math.round(28 * d);
            GradientDrawable track = new GradientDrawable();
            track.setShape(GradientDrawable.RECTANGLE);
            track.setCornerRadius(h / 2f);
            track.setColor(Color.TRANSPARENT);
            track.setStroke(Math.max(1, Math.round(2 * d)), Color.WHITE);
            track.setSize(Math.round(52 * d), h);
            int dotSize = Math.round(h * 0.46f);
            int insetV = (h - dotSize) / 2;
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Color.WHITE);
            dot.setSize(dotSize, dotSize);
            int boxW = Math.round(26 * d);
            int insetH = Math.max(0, (boxW - dotSize) / 2);
            LayerDrawable thumb = new LayerDrawable(new Drawable[]{dot});
            thumb.setLayerInset(0, insetH, insetV, insetH, insetV);
            v.setThumbDrawable(thumb);
            v.setTrackDrawable(track);
            v.setSwitchMinWidth(Math.round(52 * d));
            v.setShowText(false);
            v.setSplitTrack(false);
            v.setMinHeight(0);
            v.setMinimumHeight(0);
            v.setBackground(null);
            v.setTag(R.id.shiroikuma_switch_look, Boolean.TRUE);
        }
        v.setThumbTintList(colors);
        v.setTrackTintList(colors);
    }

    /** A thin bar down the whole left edge of an explained row. */
    private static final class LeftRule extends Drawable {
        private final Paint mPaint = new Paint();
        private final int mWidth;

        LeftRule(int color, int width) {
            mPaint.setColor(color);
            mWidth = width;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            canvas.drawRect(b.left, b.top, b.left + mWidth, b.bottom, mPaint);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
