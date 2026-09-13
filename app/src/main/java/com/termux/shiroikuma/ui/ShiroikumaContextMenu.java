package com.termux.shiroikuma.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.termux.app.TermuxActivity;

import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux (Phase 4): the terminal's long-press context menu in the house look.
 *
 * <p>A {@link ContextMenu} backed by a {@link PopupMenu}'s {@link Menu} (the framework's
 * {@code MenuBuilder}, so items keep their ids, checkable / checked / enabled state), handed to
 * upstream's untouched {@code TermuxActivity.onCreateContextMenu(menu, view, null)}, then shown as
 * a bordered black list (the Menus section's colours, border and corners): checkable items get a
 * ✓ when checked, disabled ones are dimmed and inert. A tap runs
 * {@code activity.onContextItemSelected(item)} and closes; closing (either way) runs
 * {@code activity.onContextMenuClosed(menu)}, which {@code TerminalView} needs to drop the stored
 * selection. Zero menu logic duplicated — {@link ShiroikumaRootView} funnels all four upstream
 * trigger sites here.
 */
public final class ShiroikumaContextMenu implements ContextMenu {

    @NonNull private final Menu mMenu;

    private ShiroikumaContextMenu(@NonNull Menu menu) {
        mMenu = menu;
    }

    /** Builds, fills and shows the menu; false when upstream added nothing (no session yet). */
    public static boolean show(@NonNull final TermuxActivity activity, @NonNull View anchor) {
        final ShiroikumaContextMenu menu = new ShiroikumaContextMenu(new PopupMenu(activity, anchor).getMenu());
        activity.onCreateContextMenu(menu, anchor, null);
        if (!menu.hasVisibleItems()) return false;

        final Context ctx = activity;
        int ink = color(ctx, MENU_TEXT);
        int padH = ShiroikumaDialogs.dp(ctx, 20);
        int padV = ShiroikumaDialogs.dp(ctx, 12);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, ShiroikumaDialogs.dp(ctx, 6), 0, ShiroikumaDialogs.dp(ctx, 6));
        box.setBackground(ShiroikumaDialogs.menuBackground(ctx));

        final AlertDialog dialog = ShiroikumaDialogs.panel(activity, box, true);
        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            if (!item.isVisible()) continue;
            TextView row = new TextView(ctx);
            String prefix = item.isCheckable() ? (item.isChecked() ? "✓  " : "   ") : "";
            row.setText(prefix + item.getTitle());
            row.setTextColor(ink);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.setPadding(padH, padV, padH, padV);
            if (item.isEnabled()) {
                row.setBackgroundResource(ShiroikumaFontPicker.selectableBackground(ctx));
                row.setOnClickListener(v -> {
                    // selection first, then close — shareSelectedText() needs the stored selection
                    // that onContextMenuClosed() clears.
                    activity.onContextItemSelected(item);
                    dialog.dismiss();
                });
            } else {
                row.setAlpha(0.4f);
            }
            box.addView(row);
        }
        dialog.setOnDismissListener(d -> activity.onContextMenuClosed(menu));
        dialog.show();
        return true;
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
