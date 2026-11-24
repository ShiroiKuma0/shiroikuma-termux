package com.termux.shiroikuma.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.view.menu.ListMenuItemView;
import androidx.appcompat.view.menu.MenuItemImpl;

import com.termux.shared.logger.Logger;

import static com.termux.shiroikuma.ui.ShiroikumaMenus.*;

/**
 * shiroikuma-termux (pill menus): the row of every AppCompat popup menu — here the toolbar ⋮
 * overflow of termux-shared's Report and Text IO activities. Swapped in through the app's own
 * copies of {@code abc_popup_menu_item_layout.xml} and {@code abc_cascading_menu_item_layout.xml}
 * (an app layout overrides the library's of the same name), so no activity is touched.
 *
 * <p>An action row becomes an outlined pill across the menu with its icon inside at the left
 * (coloured by what the row does) and a › when it opens something; a destructive row takes the
 * delete colour, a disabled one 38 %; a checkable row stays plain, 36 dp tall, with the outlined
 * switch in place of the checkbox. Icons, roles and › come from {@link ShiroikumaMenus#popupSpec};
 * a missing icon is set on this view only — the menu item itself is left as upstream built it.
 * The list behind the rows takes the Menus section's background and border.
 */
@SuppressLint("RestrictedApi")
public class ShiroikumaMenuItemView extends ListMenuItemView {

    private static final String LOG_TAG = "ShiroikumaMenuItemView";

    private boolean mReady;
    private LinearLayout mContent;
    private TextView mTitle;
    private ImageView mArrow;
    private int mPadStart, mPadTop, mPadEnd, mPadBottom;
    private int mContentHeight;

    public ShiroikumaMenuItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ShiroikumaMenuItemView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        try {
            mContent = findViewById(androidx.appcompat.R.id.content);
            mTitle = findViewById(androidx.appcompat.R.id.title);
            mArrow = findViewById(androidx.appcompat.R.id.submenuarrow);
            mReady = mContent != null && mTitle != null;
            if (!mReady) return;
            mPadStart = mContent.getPaddingStart();
            mPadTop = mContent.getPaddingTop();
            mPadEnd = mContent.getPaddingEnd();
            mPadBottom = mContent.getPaddingBottom();
            mContentHeight = mContent.getLayoutParams().height;

            // The stock group divider is all but invisible on a black menu: a clear line, as wide
            // as the pills.
            View divider = findViewById(androidx.appcompat.R.id.group_divider);
            if (divider != null) {
                divider.setBackgroundColor(divider(getContext()));
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) divider.getLayoutParams();
                lp.leftMargin = dp(PILL_MARGIN_H_DP);
                lp.rightMargin = dp(PILL_MARGIN_H_DP);
                divider.setLayoutParams(lp);
            }
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot prepare the menu row", t);
            mReady = false;
        }
    }

    @Override
    public void initialize(MenuItemImpl itemData, int menuType) {
        // The overflow hides item icons unless forced; a pill row always shows its icon column.
        if (mReady) setForceShowIcon(true);
        super.initialize(itemData, menuType);
        if (!mReady) return;
        try {
            style(itemData);
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot style the menu row", t);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // The pill carries the ripple, so the list's rectangular press highlight would only paint
        // over its round ends; the list itself takes the menu's background and border.
        ViewParent parent = getParent();
        if (mReady && parent instanceof AbsListView) {
            AbsListView list = (AbsListView) parent;
            if (!(list.getSelector() instanceof ColorDrawable)) {
                list.setSelector(new ColorDrawable(Color.TRANSPARENT));
                list.setBackground(ShiroikumaDialogs.menuBackground(getContext()));
            }
        }
    }

    private void style(MenuItemImpl item) {
        Context context = getContext();
        int id = item.getItemId();
        Spec spec = popupSpec(id);
        boolean checkable = item.isCheckable();
        boolean pill = !checkable;
        boolean destructive = pill && spec != null && spec.destructive;

        int color = destructive ? roleColor(context, ROLE_DELETE) : text(context);
        int iconColor = destructive ? color : roleColor(context, spec != null ? spec.role : ROLE_VIEW);
        if (!item.isEnabled()) {
            color = disabled(color);
            iconColor = disabled(iconColor);
        }

        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mContent.getLayoutParams();
        int h = dp(PILL_MARGIN_H_DP);
        int v = pill ? dp(PILL_MARGIN_V_DP) : 0;
        // A checkable row has nothing to frame, so it is only as tall as its switch needs.
        int height = checkable ? dp(TOGGLE_HEIGHT_DP) : mContentHeight;
        if (lp.leftMargin != h || lp.topMargin != v || lp.rightMargin != h || lp.bottomMargin != v || lp.height != height) {
            lp.height = height;
            lp.setMargins(h, v, h, v);
            mContent.setLayoutParams(lp);
        }

        mContent.setBackground(pill ? pill(context, color) : null);
        // The switch of a checkable row ends where the pills end.
        mContent.setPaddingRelative(mPadStart, mPadTop, checkable ? 0 : mPadEnd, mPadBottom);
        mTitle.setTextColor(color);

        if (item.getIcon() == null && spec != null) setIcon(new ShiroikumaIcons(context, spec.icon, iconColor));

        if (mArrow != null) {
            CharSequence text = item.getTitle();
            boolean ellipsis = text != null && text.toString().trim().endsWith("…");
            boolean opens = pill && (item.hasSubMenu() || ellipsis || (spec != null && spec.opens));
            if (opens) mArrow.setImageDrawable(new ShiroikumaIcons(context, ShiroikumaIcons.ICON_CHEVRON, color));
            mArrow.setVisibility(opens ? View.VISIBLE : View.GONE);
        }

        for (int i = 0; i < mContent.getChildCount(); i++) {
            View child = mContent.getChildAt(i);
            if (child instanceof CheckBox) {
                if (child.getTag() == null) {
                    CheckBox box = (CheckBox) child;
                    box.setButtonDrawable(switchDrawable(context));
                    box.setBackground(null);
                    // A long label stops short of the switch.
                    ViewGroup.MarginLayoutParams blp = (ViewGroup.MarginLayoutParams) box.getLayoutParams();
                    blp.setMarginStart(dp(8));
                    box.setLayoutParams(blp);
                    box.setTag(Boolean.TRUE);
                }
            } else if (child.getId() == androidx.appcompat.R.id.icon && child instanceof ImageView) {
                // Clear of the round end of the pill, and not squeezed in a low row.
                ViewGroup.MarginLayoutParams ilp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                if (ilp.topMargin != dp(2)) {
                    ilp.setMarginStart(dp(ICON_INSET_DP));
                    ilp.setMarginEnd(-dp(6));
                    ilp.topMargin = dp(2);
                    ilp.bottomMargin = dp(2);
                    child.setLayoutParams(ilp);
                }
                // The icon is coloured by what its row does, also when the item brought it itself.
                ((ImageView) child).setImageTintList(ColorStateList.valueOf(iconColor));
            }
        }
    }

    private int dp(int value) {
        return ShiroikumaDialogs.dp(getContext(), value);
    }
}
