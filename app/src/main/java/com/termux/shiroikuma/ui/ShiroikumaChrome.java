package com.termux.shiroikuma.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.view.TerminalView;

import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux (Phase 4): paints the house look onto upstream's views at render time —
 * the drawer, the session rows, the extra keys row, the toolbars and system bars of the
 * secondary activities. Called from a handful of one-line hooks in upstream code
 * ({@code TermuxActivity.onCreate} / {@code reloadActivityStyling} / {@code setExtraKeysView},
 * {@code TermuxSessionsListViewController.getView}, {@code ShiroikumaLifecycle}); every value comes
 * from {@link ShiroikumaTheme} so a change on the 白い熊 Termux UI page shows on the next reload.
 */
public final class ShiroikumaChrome {

    private ShiroikumaChrome() {
    }

    // ---- TermuxActivity ------------------------------------------------------------------------

    /**
     * Restyles the drawer (background, gear tint, KEYBOARD / NEW SESSION text), repaints the
     * session list, re-applies the extra keys colours and pushes the terminal font size.
     * Runs after {@code setContentView} in {@code onCreate} and at the top of
     * {@code reloadActivityStyling} — before upstream's {@code mExtraKeysView.reload(...)}, so the
     * buttons that reload creates are born with the new colours.
     */
    public static void apply(@NonNull TermuxActivity activity) {
        Context c = activity;

        View drawer = activity.findViewById(R.id.left_drawer);
        if (drawer != null) drawer.setBackgroundColor(color(c, DRAWER_BG));

        ImageButton gear = activity.findViewById(R.id.settings_button);
        if (gear != null) ImageViewCompat.setImageTintList(gear, ColorStateList.valueOf(color(c, DRAWER_ICON_TINT)));

        int buttonText = color(c, DRAWER_BUTTON_TEXT);
        for (int id : new int[]{R.id.toggle_keyboard_button, R.id.new_session_button}) {
            View b = activity.findViewById(id);
            if (b instanceof TextView) {
                ((TextView) b).setTextColor(buttonText);
                if (b instanceof MaterialButton)
                    ((MaterialButton) b).setRippleColor(ColorStateList.valueOf(withAlpha(buttonText, 0x33)));
            }
        }

        // The session rows are styled as the adapter binds them (styleSessionRow); ask for a rebind.
        ListView sessions = activity.findViewById(R.id.terminal_sessions_list);
        if (sessions != null) {
            ListAdapter adapter = sessions.getAdapter();
            if (adapter instanceof BaseAdapter) ((BaseAdapter) adapter).notifyDataSetChanged();
        }

        // The toolbar pager is the black band the extra keys / text input sit on.
        ViewPager pager = activity.getTerminalToolbarViewPager();
        if (pager != null) pager.setBackgroundColor(color(c, EXTRAKEYS_BG));
        ExtraKeysView extraKeys = activity.getExtraKeysView();
        if (extraKeys != null) applyExtraKeys(activity, extraKeys);

        // Upstream's reload path never re-reads the font size (only pinch-zoom sets it); the page's
        // Font size slider writes the pref and relies on this. A no-op when nothing changed:
        // TerminalView.updateSize() bails out when columns/rows are unchanged.
        TerminalView terminalView = activity.getTerminalView();
        TermuxAppSharedPreferences prefs = activity.getPreferences();
        if (terminalView != null && prefs != null) terminalView.setTextSize(prefs.getFontSize());
    }

    /**
     * Styles the extra keys row: colours through upstream's own {@link ExtraKeysView#setButtonColors},
     * and — because {@code ExtraKeysView} is final and {@code reload()} recreates every
     * {@link MaterialButton} — a hierarchy listener that gives each button its text size, stroke and
     * corner radius as {@code reload()} adds it. Hooked in {@code TermuxActivity.setExtraKeysView()},
     * which the toolbar pager calls right before the first {@code reload()}.
     */
    public static void applyExtraKeys(@NonNull Context context, @NonNull ExtraKeysView view) {
        final Context c = context.getApplicationContext();
        int bg = color(c, EXTRAKEYS_BG);
        view.setButtonColors(color(c, EXTRAKEYS_TEXT), color(c, EXTRAKEYS_ACTIVE_TEXT), bg, color(c, EXTRAKEYS_ACTIVE_BG));
        view.setBackgroundColor(bg);
        view.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                styleExtraKeyButton(c, child);
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
            }
        });
        for (int i = 0; i < view.getChildCount(); i++) styleExtraKeyButton(c, view.getChildAt(i));
    }

    /**
     * Text size, fill, stroke and corners of one extra key. MaterialButton's own API keeps its
     * MaterialShapeDrawable background, which matters: ExtraKeysView paints the pressed / released
     * state with {@code view.setBackgroundColor(...)}, and MaterialButton routes that into the same
     * shape's fill as long as the original background is in place — so the border survives a press.
     */
    private static void styleExtraKeyButton(@NonNull Context c, @Nullable View child) {
        if (!(child instanceof MaterialButton)) return;
        MaterialButton b = (MaterialButton) child;
        float density = c.getResources().getDisplayMetrics().density;
        int sp = value(c, EXTRAKEYS_TEXT_SIZE_SP);
        if (sp > 0) b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setBackgroundColor(color(c, EXTRAKEYS_BG));
        int strokeDp = value(c, EXTRAKEYS_BORDER_DP);
        b.setStrokeColor(ColorStateList.valueOf(color(c, EXTRAKEYS_BORDER_COLOR)));
        b.setStrokeWidth(Math.round(strokeDp * density));
        b.setCornerRadius(Math.round(value(c, EXTRAKEYS_CORNER_DP) * density));
        b.setRippleColor(ColorStateList.valueOf(withAlpha(color(c, EXTRAKEYS_TEXT), 0x33)));
    }

    /**
     * One session row of the drawer list (hooked at the end of
     * {@code TermuxSessionsListViewController.getView()}): text colour (dead sessions in the
     * dead-session colour), text size, and an activated-state background in the selected colour
     * under a ripple in the text colour.
     */
    public static void styleSessionRow(@NonNull TextView title, boolean dead) {
        Context c = title.getContext();
        int text = color(c, dead ? SESSION_DEAD_TEXT : SESSION_TEXT);
        title.setTextColor(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, value(c, SESSION_TEXT_SIZE_SP));

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_activated}, new ColorDrawable(color(c, SESSION_SELECTED_BG)));
        states.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        title.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(text, 0x33)), states, null));
    }

    // ---- the secondary activities (Settings, Help, Report, our page) ---------------------------

    /** Status and navigation bar colours, with icon contrast to match. */
    public static void applyStatusBars(@NonNull Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;
        int status = color(activity, STATUSBAR_BG);
        int nav = color(activity, NAVBAR_BG);
        window.setStatusBarColor(status);
        window.setNavigationBarColor(nav);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(!isDark(status));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) controller.setAppearanceLightNavigationBars(!isDark(nav));
    }

    /**
     * The {@code @id/toolbar} of an activity (termux-shared's partial_primary_toolbar, or our page's):
     * background, title/subtitle colour, navigation + overflow icons and any menu-item icons.
     */
    public static void applyToolbar(@NonNull Activity activity) {
        View v = activity.findViewById(com.termux.shared.R.id.toolbar);
        if (!(v instanceof Toolbar)) return;
        Toolbar toolbar = (Toolbar) v;
        int icon = color(activity, TOOLBAR_ICON);
        int text = color(activity, TOOLBAR_TEXT);
        toolbar.setBackgroundColor(color(activity, TOOLBAR_BG));
        toolbar.setTitleTextColor(text);
        toolbar.setSubtitleTextColor(withAlpha(text, 0xC8));
        Drawable nav = toolbar.getNavigationIcon();
        if (nav != null) toolbar.setNavigationIcon(tint(nav, icon));
        Drawable overflow = toolbar.getOverflowIcon();
        if (overflow != null) toolbar.setOverflowIcon(tint(overflow, icon));
        Menu menu = toolbar.getMenu();
        if (menu != null) {
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item.getIcon() != null) item.setIcon(tint(item.getIcon(), icon));
            }
        }
    }

    @Nullable
    private static Drawable tint(@Nullable Drawable d, int color) {
        if (d == null) return null;
        Drawable wrapped = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(wrapped, color);
        return wrapped;
    }
}
