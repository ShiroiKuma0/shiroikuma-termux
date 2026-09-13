package com.termux.shiroikuma.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.view.TerminalView;
import com.termux.view.textselection.TextSelectionCursorController;

import static com.termux.shiroikuma.ui.ShiroikumaTheme.*;

/**
 * shiroikuma-termux (Phase 4): the terminal's text-selection toolbar — COPY · PASTE · MORE… — in
 * the house look, replacing the platform floating toolbar.
 *
 * <p>The platform one cannot be themed: {@code FloatingToolbar} re-wraps the window context in
 * {@code Theme.DeviceDefault(.Light)} and takes only light/dark from the app, so it stays a grey
 * pill with white text whatever the activity theme says. This is an {@link ActionMode} of our own,
 * handed to upstream's {@code TextSelectionCursorController} through its
 * {@link TextSelectionCursorController.FloatingActionModeFactory} seam (installed by
 * {@code ShiroikumaLifecycle}): upstream's {@code Callback2} fills a real {@link Menu} (a
 * {@link PopupMenu}'s, so ids and the enabled state of PASTE survive), each visible item becomes a
 * button on a bordered black bar (the Menus section's colours, border and corners), and a tap goes
 * back through {@code onActionItemClicked}. {@code TerminalView} keeps driving it exactly as it
 * drives the platform mode: {@code hide(-1)} while a handle is dragged, {@code hide(0)} to show
 * again, {@code invalidate()} on every render (re-asks {@code onGetContentRect} and follows the
 * selection), {@code finish()} when selection ends.
 *
 * <p>Placement mirrors the platform's: centred over the selection, above it when the visible frame
 * has room, else below it, never off the visible frame (the keyboard). The popup is a
 * non-focusable {@link PopupWindow} — the terminal keeps focus and the handles keep working.
 */
@RequiresApi(Build.VERSION_CODES.M)
public final class ShiroikumaSelectionToolbar extends ActionMode {

    /** The platform's cap on one hide(duration). */
    private static final long MAX_HIDE_MS = 3000;

    @NonNull private final TerminalView mView;
    @NonNull private final ActionMode.Callback2 mCallback;
    @NonNull private final Menu mMenu;
    @NonNull private final View mContent;
    @NonNull private final PopupWindow mPopup;
    private final Rect mRect = new Rect();
    private final Rect mFrame = new Rect();
    private final int[] mInWindow = new int[2];
    private final int[] mOnScreen = new int[2];
    private final Runnable mShow = this::showNow;
    private boolean mHidden, mFinished;

    /** The factory {@code ShiroikumaLifecycle} installs. */
    @Nullable
    public static ActionMode start(@NonNull TerminalView terminalView, @NonNull ActionMode.Callback2 callback) {
        if (terminalView.getWindowToken() == null) return null;
        ShiroikumaSelectionToolbar mode = new ShiroikumaSelectionToolbar(terminalView, callback);
        if (!callback.onCreateActionMode(mode, mode.mMenu)) return null;
        mode.buildButtons();
        mode.showNow();
        return mode;
    }

    private ShiroikumaSelectionToolbar(@NonNull TerminalView terminalView, @NonNull ActionMode.Callback2 callback) {
        mView = terminalView;
        mCallback = callback;
        Context ctx = terminalView.getContext();
        mMenu = new PopupMenu(ctx, terminalView).getMenu();

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(ShiroikumaDialogs.menuBackground(ctx));
        mContent = bar;

        mPopup = new PopupWindow(bar, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        mPopup.setTouchable(true);
        mPopup.setOutsideTouchable(false); // taps elsewhere reach the terminal, which ends the selection itself
        mPopup.setClippingEnabled(true);
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        // The selection handles are TYPE_APPLICATION_SUB_PANEL popups; the platform toolbar sits one
        // layer above them (TYPE_APPLICATION_ABOVE_SUB_PANEL = FIRST_SUB_WINDOW + 5, hidden from the
        // SDK but accepted for any sub-window), and so must ours or a handle would draw over the bar.
        mPopup.setWindowLayoutType(WindowManager.LayoutParams.FIRST_SUB_WINDOW + 5);
        setType(TYPE_FLOATING);
    }

    /** One button per visible item; disabled items (PASTE with an empty clipboard) dimmed and inert. */
    private void buildButtons() {
        Context ctx = mView.getContext();
        LinearLayout bar = (LinearLayout) mContent;
        int ink = color(ctx, MENU_TEXT);
        int padH = ShiroikumaDialogs.dp(ctx, 16);
        int padV = ShiroikumaDialogs.dp(ctx, 11);
        for (int i = 0; i < mMenu.size(); i++) {
            final MenuItem item = mMenu.getItem(i);
            if (!item.isVisible()) continue;
            TextView button = new TextView(ctx);
            button.setText(item.getTitle());
            button.setAllCaps(true);
            button.setSingleLine(true);
            button.setGravity(Gravity.CENTER);
            button.setTextColor(ink);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            button.setPadding(padH, padV, padH, padV);
            if (item.isEnabled()) {
                button.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(ink, 0x33)), null, new ColorDrawable(Color.WHITE)));
                button.setOnClickListener(v -> {
                    if (!mFinished) mCallback.onActionItemClicked(this, item);
                });
            } else {
                button.setAlpha(0.4f);
            }
            bar.addView(button);
        }
    }

    private void showNow() {
        mHidden = false;
        if (mFinished || !mView.isAttachedToWindow()) return;
        place(!mPopup.isShowing());
    }

    /**
     * Centred over the selection's rect (view coordinates from {@code onGetContentRect}), above it
     * when the visible frame has room, else below; clamped to the terminal's width and to the
     * visible frame's bottom. Popup coordinates are window-relative — the floating terminal's
     * window is not at the screen origin — so the room checks use screen coordinates separately.
     */
    private void place(boolean first) {
        mCallback.onGetContentRect(this, mView, mRect);
        mView.getLocationInWindow(mInWindow);
        mView.getLocationOnScreen(mOnScreen);
        mView.getWindowVisibleDisplayFrame(mFrame);
        mContent.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int w = mContent.getMeasuredWidth();
        int h = mContent.getMeasuredHeight();
        int margin = ShiroikumaDialogs.dp(mView.getContext(), 8);

        int x = mInWindow[0] + (mRect.left + mRect.right) / 2 - w / 2;
        int maxX = mInWindow[0] + mView.getWidth() - w;
        x = Math.max(mInWindow[0], Math.min(x, Math.max(mInWindow[0], maxX)));

        int screenToWindow = mInWindow[1] - mOnScreen[1];
        int aboveOnScreen = mOnScreen[1] + mRect.top - h - margin;
        int y = aboveOnScreen >= mFrame.top ? aboveOnScreen : mOnScreen[1] + mRect.bottom + margin;
        y = Math.min(y, mFrame.bottom - h);
        y = Math.max(y, mFrame.top);
        y += screenToWindow;

        if (first) mPopup.showAtLocation(mView, Gravity.NO_GRAVITY, x, y);
        else mPopup.update(x, y, -1, -1);
    }

    // ---- what TerminalView calls -----------------------------------------------------------------

    /** Platform semantics: {@code <= 0} shows now, {@code DEFAULT_HIDE_DURATION} = the platform default, capped at 3 s. */
    @Override
    public void hide(long duration) {
        if (mFinished) return;
        mView.removeCallbacks(mShow);
        if (duration == DEFAULT_HIDE_DURATION) duration = ViewConfiguration.getDefaultActionModeHideDuration();
        duration = Math.min(MAX_HIDE_MS, duration);
        if (duration <= 0) {
            showNow();
        } else {
            mHidden = true;
            mPopup.dismiss();
            mView.postDelayed(mShow, duration);
        }
    }

    @Override
    public void invalidate() {
        if (mFinished || mHidden || !mPopup.isShowing()) return;
        place(false);
    }

    @Override
    public void finish() {
        if (mFinished) return;
        mFinished = true;
        mView.removeCallbacks(mShow);
        mPopup.dismiss();
        mCallback.onDestroyActionMode(this);
    }

    @Override
    public Menu getMenu() {
        return mMenu;
    }

    @Override
    public MenuInflater getMenuInflater() {
        return new MenuInflater(mView.getContext());
    }

    // ---- title / custom view: the bar has neither ------------------------------------------------

    @Override
    public void setTitle(CharSequence title) {
    }

    @Override
    public void setTitle(int resId) {
    }

    @Override
    public void setSubtitle(CharSequence subtitle) {
    }

    @Override
    public void setSubtitle(int resId) {
    }

    @Override
    public void setCustomView(View view) {
    }

    @Override
    public CharSequence getTitle() {
        return null;
    }

    @Override
    public CharSequence getSubtitle() {
        return null;
    }

    @Override
    public View getCustomView() {
        return null;
    }
}
