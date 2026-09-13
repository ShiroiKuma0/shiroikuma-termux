package com.termux.shiroikuma.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.termux.app.terminal.TermuxActivityRootView;

/**
 * shiroikuma-termux (Phase 4): the root of activity_termux.xml (one class-name edit there).
 * Every {@code view.showContextMenu()} of a descendant — TerminalView's long-press, the text
 * selection "More" action, the hardware menu key, {@code onCreateOptionsMenu} — climbs the parent
 * chain through {@code showContextMenuForChild}; intercepting it here replaces the platform
 * context menu with {@link ShiroikumaContextMenu} for all four sites at once. When our menu has
 * nothing to show (no session yet) the call falls through to the platform, as before.
 *
 * <p>Only the plain {@code showContextMenuForChild(View)} is overridden: on API 24+ the
 * coordinate variant of ViewGroup calls the plain one first (its compatibility path), so both
 * routes end here.
 */
public class ShiroikumaRootView extends TermuxActivityRootView {

    public ShiroikumaRootView(Context context) {
        super(context);
    }

    public ShiroikumaRootView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ShiroikumaRootView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean showContextMenuForChild(View originalView) {
        if (mActivity != null && ShiroikumaContextMenu.show(mActivity, originalView)) return true;
        return super.showContextMenuForChild(originalView);
    }
}
