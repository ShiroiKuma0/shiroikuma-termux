package com.termux.shiroikuma.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import static com.termux.shiroikuma.ui.ShiroikumaIcons.*;
import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux (pill menus): the family's settled menu look, shared by the terminal's context
 * menu ({@link ShiroikumaContextMenu}, rows built by hand) and every AppCompat popup row
 * ({@link ShiroikumaMenuItemView}: the toolbar ⋮ overflow of Report / Text IO).
 *
 * <ul>
 * <li>An action row is an outlined pill across the menu (1.25 dp frame, never filled), its icon
 *     inside at the left, a › at the right when it opens something.</li>
 * <li>The icon is coloured by what the row does (settable roles of the Menus section); frame and
 *     label keep the menu's text colour. A destructive row lends the delete colour to all three, a
 *     disabled one is drawn at 38 %.</li>
 * <li>A checkable row stays plain, 36 dp tall, with the compact outlined switch.</li>
 * <li>Group dividers and the switch's off colour are derived from the menu's background and text
 *     colours, never constants.</li>
 * </ul>
 *
 * <p>Menu items carry no "opens something" / "destructive" flag and mostly no icon, so the two
 * small tables here supply them by item id.
 */
public final class ShiroikumaMenus {

    // What a row does — picks the icon colour.
    public static final int ROLE_VIEW = 0;
    public static final int ROLE_CONNECT = 1;
    public static final int ROLE_SHARE = 2;
    public static final int ROLE_CONTACT = 3;
    public static final int ROLE_BLOCK = 4;
    public static final int ROLE_DELETE = 5;

    /** Measures settled on the device (black-yellow-theme, "Menus"). */
    public static final int ROW_HEIGHT_DP = 48;
    public static final int TOGGLE_HEIGHT_DP = 36;
    public static final int PILL_MARGIN_H_DP = 8;
    public static final int PILL_MARGIN_V_DP = 3;
    public static final int ICON_INSET_DP = 14;
    public static final int SWITCH_WIDTH_DP = 44;
    public static final int SWITCH_HEIGHT_DP = 24;
    private static final float FRAME_DP = 1.25f;
    private static final float DISABLED = 0.38f;
    private static final float RIPPLE = 0.30f;

    // TermuxActivity's context-menu item ids. They are private constants there, so the values are
    // repeated here (upstream names in the comments); the UI-page row sits in upstream's "Style"
    // slot. Our own rows have resource ids (R.id.shiroikuma_menu_show_drawer, _extra_keys).
    static final int CONTEXT_SELECT_URL = 0;          // CONTEXT_MENU_SELECT_URL_ID
    static final int CONTEXT_SHARE_TRANSCRIPT = 1;    // CONTEXT_MENU_SHARE_TRANSCRIPT_ID
    static final int CONTEXT_AUTOFILL_PASSWORD = 2;   // CONTEXT_MENU_AUTOFILL_PASSWORD
    static final int CONTEXT_RESET_TERMINAL = 3;      // CONTEXT_MENU_RESET_TERMINAL_ID
    static final int CONTEXT_KILL_PROCESS = 4;        // CONTEXT_MENU_KILL_PROCESS_ID
    static final int CONTEXT_UI_PAGE = 5;             // CONTEXT_MENU_STYLING_ID
    static final int CONTEXT_KEEP_SCREEN_ON = 6;      // CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON
    static final int CONTEXT_HELP = 7;                // CONTEXT_MENU_HELP_ID
    static final int CONTEXT_SETTINGS = 8;            // CONTEXT_MENU_SETTINGS_ID
    static final int CONTEXT_REPORT = 9;              // CONTEXT_MENU_REPORT_ID
    static final int CONTEXT_SHARE_SELECTED_TEXT = 10; // CONTEXT_MENU_SHARE_SELECTED_TEXT
    static final int CONTEXT_AUTOFILL_USERNAME = 11;  // CONTEXT_MENU_AUTOFILL_USERNAME

    /** What the tables know about one item. */
    public static final class Spec {
        public final int icon;
        public final int role;
        public final boolean opens;
        public final boolean destructive;

        Spec(int icon, int role, boolean opens, boolean destructive) {
            this.icon = icon;
            this.role = role;
            this.opens = opens;
            this.destructive = destructive;
        }
    }

    private ShiroikumaMenus() {
    }

    // ---- the tables -----------------------------------------------------------------------------

    /** The terminal's context menu. An id upstream adds later gets a neutral dot until it is listed. */
    @NonNull
    static Spec contextSpec(int id) {
        if (id == R.id.shiroikuma_menu_show_drawer) return new Spec(ICON_MENU, ROLE_VIEW, false, false);
        if (id == R.id.shiroikuma_menu_extra_keys) return new Spec(ICON_KEYBOARD, ROLE_VIEW, false, false);
        switch (id) {
            case CONTEXT_SELECT_URL:
                return new Spec(ICON_LINK, ROLE_VIEW, true, false);
            case CONTEXT_SHARE_TRANSCRIPT:
            case CONTEXT_SHARE_SELECTED_TEXT:
                return new Spec(ICON_SHARE, ROLE_SHARE, true, false);
            case CONTEXT_AUTOFILL_USERNAME:
                return new Spec(ICON_PERSON, ROLE_CONTACT, false, false);
            case CONTEXT_AUTOFILL_PASSWORD:
                return new Spec(ICON_KEY, ROLE_BLOCK, false, false);
            case CONTEXT_RESET_TERMINAL:
                return new Spec(ICON_RESET, ROLE_CONNECT, false, false);
            case CONTEXT_KILL_PROCESS:
                return new Spec(ICON_KILL, ROLE_DELETE, false, true);
            case CONTEXT_UI_PAGE:
                return new Spec(ICON_SLIDERS, ROLE_VIEW, true, false);
            case CONTEXT_KEEP_SCREEN_ON:
                return new Spec(ICON_SUN, ROLE_VIEW, false, false);
            case CONTEXT_HELP:
                return new Spec(ICON_HELP, ROLE_VIEW, true, false);
            case CONTEXT_SETTINGS:
                return new Spec(ICON_GEAR, ROLE_VIEW, true, false);
            case CONTEXT_REPORT:
                return new Spec(ICON_WARNING, ROLE_VIEW, true, false);
            default:
                return new Spec(ICON_DOT, ROLE_VIEW, false, false);
        }
    }

    /** True for the rows of the context menu that start a new group (a divider goes above them). */
    static boolean contextGroupStart(int id) {
        return id == CONTEXT_RESET_TERMINAL || id == CONTEXT_KEEP_SCREEN_ON || id == CONTEXT_HELP;
    }

    /**
     * The AppCompat popup rows: termux-shared's {@code menu_report.xml} and {@code menu_text_io.xml}.
     * Null for an item nobody listed — it keeps its own icon (if any) in the view role.
     */
    @Nullable
    static Spec popupSpec(int id) {
        if (id == com.termux.shared.R.id.menu_item_share_report || id == com.termux.shared.R.id.menu_item_share_text)
            return new Spec(ICON_SHARE, ROLE_SHARE, true, false);
        if (id == com.termux.shared.R.id.menu_item_copy_report || id == com.termux.shared.R.id.menu_item_copy_text)
            return new Spec(ICON_COPY, ROLE_SHARE, false, false);
        if (id == com.termux.shared.R.id.menu_item_save_report_to_file)
            return new Spec(ICON_SAVE, ROLE_SHARE, true, false);
        if (id == com.termux.shared.R.id.menu_item_cancel)
            return new Spec(ICON_CANCEL, ROLE_VIEW, false, false);
        return null;
    }

    // ---- colours --------------------------------------------------------------------------------

    /** Frame and label of an ordinary row: the menu's text colour. */
    public static int text(@NonNull Context context) {
        return color(context, MENU_TEXT);
    }

    /** The icon colour of a role; "view" is the menu's text colour. */
    public static int roleColor(@NonNull Context context, int role) {
        switch (role) {
            case ROLE_CONNECT:
                return color(context, MENU_ICON_CONNECT);
            case ROLE_SHARE:
                return color(context, MENU_ICON_SHARE);
            case ROLE_CONTACT:
                return color(context, MENU_ICON_CONTACT);
            case ROLE_BLOCK:
                return color(context, MENU_ICON_BLOCK);
            case ROLE_DELETE:
                return color(context, MENU_DELETE);
            default:
                return text(context);
        }
    }

    /** The group divider: opaque, 70 % of the way from the menu's background to its text colour. */
    public static int divider(@NonNull Context context) {
        return blend(color(context, MENU_BG), text(context), 0.70f);
    }

    /** A switch that is off: its own role ({@code #8C8C00} by default), shared with the UI page. */
    public static int switchOff(@NonNull Context context) {
        return color(context, SWITCH_OFF);
    }

    /** A disabled row's colour: the same at 38 %. */
    public static int disabled(int color) {
        return scaleAlpha(color, DISABLED);
    }

    private static int scaleAlpha(int color, float factor) {
        return ShiroikumaTheme.withAlpha(color, Math.round(Color.alpha(color) * factor));
    }

    private static int blend(int from, int to, float t) {
        return Color.rgb(
            Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
            Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
            Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    // ---- drawables ------------------------------------------------------------------------------

    /** The outlined pill of an action row, carrying the ripple (the row colour at 30 %). */
    @NonNull
    public static Drawable pill(@NonNull Context context, int color) {
        float radius = ShiroikumaDialogs.dp(context, 100);
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(radius);
        frame.setColor(Color.TRANSPARENT);
        frame.setStroke(Math.max(1, Math.round(FRAME_DP * context.getResources().getDisplayMetrics().density)), color);
        return new RippleDrawable(ColorStateList.valueOf(scaleAlpha(color, RIPPLE)), frame, roundMask(radius));
    }

    /** The press feedback of a plain (toggle) row: the same round ripple, no frame. */
    @NonNull
    public static Drawable plainRipple(@NonNull Context context, int color) {
        return new RippleDrawable(ColorStateList.valueOf(scaleAlpha(color, RIPPLE)), null,
            roundMask(ShiroikumaDialogs.dp(context, 100)));
    }

    @NonNull
    private static Drawable roundMask(float radius) {
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(radius);
        mask.setColor(Color.WHITE);
        return mask;
    }

    @NonNull
    public static SwitchDrawable switchDrawable(@NonNull Context context) {
        return new SwitchDrawable(color(context, SWITCH_ON), switchOff(context),
            ShiroikumaDialogs.dp(context, SWITCH_WIDTH_DP), ShiroikumaDialogs.dp(context, SWITCH_HEIGHT_DP),
            ShiroikumaDialogs.dp(context, 2));
    }

    /**
     * The outlined switch, drawn small for a menu row: a round outline with a solid dot — right
     * and in the text colour when on, left and in the derived dim colour when off. Never filled.
     * Follows {@code state_checked} (as a CheckBox's button) or {@link #setChecked}.
     */
    public static final class SwitchDrawable extends Drawable {
        private final int mOn, mOff, mWidth, mHeight;
        private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private boolean mChecked;
        /** Set once {@link #setChecked} was used: the host view's drawable state is then ignored. */
        private boolean mManual;

        SwitchDrawable(int on, int off, int width, int height, int strokeWidth) {
            mOn = on;
            mOff = off;
            mWidth = width;
            mHeight = height;
            mStroke.setStyle(Paint.Style.STROKE);
            mStroke.setStrokeWidth(strokeWidth);
            mFill.setStyle(Paint.Style.FILL);
        }

        public void setChecked(boolean checked) {
            mManual = true;
            if (checked == mChecked) return;
            mChecked = checked;
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            int color = mChecked ? mOn : mOff;
            float inset = mStroke.getStrokeWidth() / 2f;
            mRect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
            mStroke.setColor(color);
            canvas.drawRoundRect(mRect, mRect.height() / 2f, mRect.height() / 2f, mStroke);
            mFill.setColor(color);
            float cx = mChecked ? b.right - b.height() / 2f : b.left + b.height() / 2f;
            canvas.drawCircle(cx, b.exactCenterY(), b.height() * 0.23f, mFill);
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(@NonNull int[] state) {
            if (mManual) return false;
            boolean checked = false;
            for (int s : state) {
                if (s == android.R.attr.state_checked) {
                    checked = true;
                    break;
                }
            }
            if (checked == mChecked) return false;
            mChecked = checked;
            invalidateSelf();
            return true;
        }

        @Override
        public int getIntrinsicWidth() {
            return mWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return mHeight;
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
