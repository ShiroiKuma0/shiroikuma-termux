package com.termux.shiroikuma.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.termux.R;
import com.termux.app.TermuxActivity;

/**
 * shiroikuma-termux (Phase 4): the terminal's long-press context menu in the house look.
 *
 * <p>A {@link ContextMenu} backed by a {@link PopupMenu}'s {@link Menu} (the framework's
 * {@code MenuBuilder}, so items keep their ids, checkable / checked / enabled state), handed to
 * upstream's untouched {@code TermuxActivity.onCreateContextMenu(menu, view, null)}, then shown as
 * a bordered black list (the Menus section's colours, border and corners) in the family's pill-menu
 * look ({@link ShiroikumaMenus}): every action an outlined pill across the menu with its icon, a ›
 * when it opens something, the checkable item a plain row with the outlined switch, disabled ones
 * dimmed and inert, a line between the groups. A tap runs
 * {@code activity.onContextItemSelected(item)} and closes; closing (either way) runs
 * {@code activity.onContextMenuClosed(menu)}, which {@code TerminalView} needs to drop the stored
 * selection. Zero menu logic duplicated — {@link ShiroikumaRootView} funnels all four upstream
 * trigger sites here.
 *
 * <p>One row is ours, at the top: 「Show side menu」 opens the sessions drawer — the left pull-out
 * that an edge swipe has to fight gesture navigation for. It is added before upstream fills the
 * menu (same order {@code NONE}, so it sorts first) and handled here, not by
 * {@code onContextItemSelected}.
 *
 * <p>A second row is ours too: 「Extra keys row」, a switch under 「Keep screen on」 that hides and
 * shows the rows of extra keys above the keyboard (白い熊, 2026-11-28). It is upstream's own toggle
 * ({@code TermuxActivity.toggleTerminalToolbar()}, until now reachable only by Ctrl+Alt+K /
 * Volume Up+K and a long press on the drawer's KEYBOARD button) and upstream's own preference
 * ({@code show_terminal_toolbar}), so the state is remembered across restarts.
 */
public final class ShiroikumaContextMenu implements ContextMenu {

    @NonNull private final Menu mMenu;

    private ShiroikumaContextMenu(@NonNull Menu menu) {
        mMenu = menu;
    }

    /** Builds, fills and shows the menu; false when upstream added nothing (no session yet). */
    public static boolean show(@NonNull final TermuxActivity activity, @NonNull View anchor) {
        final ShiroikumaContextMenu menu = new ShiroikumaContextMenu(new PopupMenu(activity, anchor).getMenu());
        final MenuItem showDrawer = menu.add(Menu.NONE, R.id.shiroikuma_menu_show_drawer, Menu.NONE, R.string.shiroikuma_menu_show_drawer);
        final MenuItem extraKeys = menu.add(Menu.NONE, R.id.shiroikuma_menu_extra_keys, Menu.NONE, R.string.shiroikuma_ui_section_extrakeys)
            .setCheckable(true).setChecked(activity.getPreferences().shouldShowTerminalToolbar());
        activity.onCreateContextMenu(menu, anchor, null);
        if (menu.size() <= 2) return false; // upstream added nothing (no session yet): stay silent, as before

        final Context ctx = activity;

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, ShiroikumaDialogs.dp(ctx, 6), 0, ShiroikumaDialogs.dp(ctx, 6));
        box.setBackground(ShiroikumaDialogs.menuBackground(ctx));

        final AlertDialog dialog = ShiroikumaDialogs.panel(activity, box, true);
        // 「白い熊 Termux UI」 is the menu's first row (白い熊, 2026-11-28); the rest keep upstream's order.
        final java.util.List<MenuItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item == extraKeys) continue;
            if (item.getItemId() == ShiroikumaMenus.CONTEXT_UI_PAGE) items.add(0, item);
            else items.add(item);
        }
        // 「Extra keys row」 goes under the other switch, 「Keep screen on」 (at the end should upstream drop it).
        int extraKeysAt = items.size();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItemId() == ShiroikumaMenus.CONTEXT_KEEP_SCREEN_ON) extraKeysAt = i + 1;
        }
        items.add(extraKeysAt, extraKeys);
        for (final MenuItem item : items) {
            if (!item.isVisible()) continue;
            if (box.getChildCount() > 0 && ShiroikumaMenus.contextGroupStart(item.getItemId())) box.addView(divider(ctx));
            View row = row(ctx, item);
            if (item.isEnabled()) {
                row.setOnClickListener(v -> {
                    // selection first, then close — shareSelectedText() needs the stored selection
                    // that onContextMenuClosed() clears.
                    if (item == showDrawer) activity.getDrawer().openDrawer(Gravity.START);
                    else if (item == extraKeys) activity.toggleTerminalToolbar();
                    else activity.onContextItemSelected(item);
                    dialog.dismiss();
                });
            }
            box.addView(row);
        }
        dialog.setOnDismissListener(d -> activity.onContextMenuClosed(menu));
        dialog.show();
        return true;
    }

    /**
     * One row. An action is an outlined pill across the menu — icon inside at the left, coloured
     * by what the row does, a › at the right when it opens something; the destructive one takes
     * the delete colour throughout, a disabled one 38 %. A checkable item stays plain and lower,
     * its icon and label lined up with the pills' and the outlined switch ending on their edge.
     */
    @NonNull
    private static View row(@NonNull Context ctx, @NonNull MenuItem item) {
        ShiroikumaMenus.Spec spec = ShiroikumaMenus.contextSpec(item.getItemId());
        boolean checkable = item.isCheckable();
        boolean destructive = !checkable && spec.destructive;

        int ink = destructive ? ShiroikumaMenus.roleColor(ctx, ShiroikumaMenus.ROLE_DELETE) : ShiroikumaMenus.text(ctx);
        int iconInk = destructive ? ink : ShiroikumaMenus.roleColor(ctx, spec.role);
        if (!item.isEnabled()) {
            ink = ShiroikumaMenus.disabled(ink);
            iconInk = ShiroikumaMenus.disabled(iconInk);
        }

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ShiroikumaDialogs.dp(ctx, checkable ? ShiroikumaMenus.TOGGLE_HEIGHT_DP : ShiroikumaMenus.ROW_HEIGHT_DP));
        int h = ShiroikumaDialogs.dp(ctx, ShiroikumaMenus.PILL_MARGIN_H_DP);
        int v = checkable ? 0 : ShiroikumaDialogs.dp(ctx, ShiroikumaMenus.PILL_MARGIN_V_DP);
        lp.setMargins(h, v, h, v);
        row.setLayoutParams(lp);
        row.setBackground(checkable ? ShiroikumaMenus.plainRipple(ctx, ink) : ShiroikumaMenus.pill(ctx, ink));

        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(new ShiroikumaIcons(ctx, spec.icon, iconInk));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(ShiroikumaDialogs.dp(ctx, 24), ShiroikumaDialogs.dp(ctx, 24));
        iconLp.setMarginStart(ShiroikumaDialogs.dp(ctx, ShiroikumaMenus.ICON_INSET_DP));
        row.addView(icon, iconLp);

        TextView label = new TextView(ctx);
        label.setText(item.getTitle());
        label.setTextColor(ink);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.setMarginStart(ShiroikumaDialogs.dp(ctx, 12));
        // A long label stops 8 dp short of what follows it.
        labelLp.setMarginEnd(ShiroikumaDialogs.dp(ctx, 8));
        row.addView(label, labelLp);

        if (checkable) {
            ShiroikumaMenus.SwitchDrawable knob = ShiroikumaMenus.switchDrawable(ctx);
            knob.setChecked(item.isChecked());
            ImageView toggle = new ImageView(ctx);
            toggle.setImageDrawable(knob);
            if (!item.isEnabled()) toggle.setAlpha(0.38f);
            row.addView(toggle, new LinearLayout.LayoutParams(
                ShiroikumaDialogs.dp(ctx, ShiroikumaMenus.SWITCH_WIDTH_DP), ShiroikumaDialogs.dp(ctx, ShiroikumaMenus.SWITCH_HEIGHT_DP)));
        } else if (spec.opens) {
            ImageView chevron = new ImageView(ctx);
            chevron.setImageDrawable(new ShiroikumaIcons(ctx, ShiroikumaIcons.ICON_CHEVRON, ink));
            LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(ShiroikumaDialogs.dp(ctx, 24), ShiroikumaDialogs.dp(ctx, 24));
            chevronLp.setMarginEnd(ShiroikumaDialogs.dp(ctx, 8));
            row.addView(chevron, chevronLp);
        }
        return row;
    }

    /** The line between two groups: 1 dp, the derived darker text colour, as wide as the pills. */
    @NonNull
    private static View divider(@NonNull Context ctx) {
        View line = new View(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, ShiroikumaDialogs.dp(ctx, 1)));
        int h = ShiroikumaDialogs.dp(ctx, ShiroikumaMenus.PILL_MARGIN_H_DP);
        int v = ShiroikumaDialogs.dp(ctx, 4);
        lp.setMargins(h, v, h, v);
        line.setLayoutParams(lp);
        line.setBackgroundColor(ShiroikumaMenus.divider(ctx));
        return line;
    }

    // ---- ContextMenu header: no header in the house look -----------------------------------------

    @NonNull
    @Override
    public ContextMenu setHeaderTitle(int titleRes) {
        return this;
    }

    @NonNull
    @Override
    public ContextMenu setHeaderTitle(CharSequence title) {
        return this;
    }

    @NonNull
    @Override
    public ContextMenu setHeaderIcon(int iconRes) {
        return this;
    }

    @NonNull
    @Override
    public ContextMenu setHeaderIcon(Drawable icon) {
        return this;
    }

    @NonNull
    @Override
    public ContextMenu setHeaderView(View view) {
        return this;
    }

    @Override
    public void clearHeader() {
    }

    // ---- Menu: delegated -----------------------------------------------------------------------

    @Override
    public MenuItem add(CharSequence title) {
        return mMenu.add(title);
    }

    @Override
    public MenuItem add(int titleRes) {
        return mMenu.add(titleRes);
    }

    @Override
    public MenuItem add(int groupId, int itemId, int order, CharSequence title) {
        return mMenu.add(groupId, itemId, order, title);
    }

    @Override
    public MenuItem add(int groupId, int itemId, int order, int titleRes) {
        return mMenu.add(groupId, itemId, order, titleRes);
    }

    @Override
    public SubMenu addSubMenu(CharSequence title) {
        return mMenu.addSubMenu(title);
    }

    @Override
    public SubMenu addSubMenu(int titleRes) {
        return mMenu.addSubMenu(titleRes);
    }

    @Override
    public SubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title) {
        return mMenu.addSubMenu(groupId, itemId, order, title);
    }

    @Override
    public SubMenu addSubMenu(int groupId, int itemId, int order, int titleRes) {
        return mMenu.addSubMenu(groupId, itemId, order, titleRes);
    }

    @Override
    public int addIntentOptions(int groupId, int itemId, int order, ComponentName caller, Intent[] specifics,
                                Intent intent, int flags, MenuItem[] outSpecificItems) {
        return mMenu.addIntentOptions(groupId, itemId, order, caller, specifics, intent, flags, outSpecificItems);
    }

    @Override
    public void removeItem(int id) {
        mMenu.removeItem(id);
    }

    @Override
    public void removeGroup(int groupId) {
        mMenu.removeGroup(groupId);
    }

    @Override
    public void clear() {
        mMenu.clear();
    }

    @Override
    public void setGroupCheckable(int group, boolean checkable, boolean exclusive) {
        mMenu.setGroupCheckable(group, checkable, exclusive);
    }

    @Override
    public void setGroupVisible(int group, boolean visible) {
        mMenu.setGroupVisible(group, visible);
    }

    @Override
    public void setGroupEnabled(int group, boolean enabled) {
        mMenu.setGroupEnabled(group, enabled);
    }

    @Override
    public boolean hasVisibleItems() {
        return mMenu.hasVisibleItems();
    }

    @Override
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    @Override
    public int size() {
        return mMenu.size();
    }

    @Override
    public MenuItem getItem(int index) {
        return mMenu.getItem(index);
    }

    @Override
    public void close() {
        mMenu.close();
    }

    @Override
    public boolean performShortcut(int keyCode, KeyEvent event, int flags) {
        return mMenu.performShortcut(keyCode, event, flags);
    }

    @Override
    public boolean isShortcutKey(int keyCode, KeyEvent event) {
        return mMenu.isShortcutKey(keyCode, event);
    }

    @Override
    public boolean performIdentifierAction(int id, int flags) {
        return mMenu.performIdentifierAction(id, flags);
    }

    @Override
    public void setQwertyMode(boolean isQwerty) {
        mMenu.setQwertyMode(isQwerty);
    }
}
