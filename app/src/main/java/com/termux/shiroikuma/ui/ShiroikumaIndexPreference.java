package com.termux.shiroikuma.ui;

import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * shiroikuma-termux: the chip index of the 白い熊 Termux UI page — a wrapping grid of small
 * outlined chips, one for every section, each exactly as wide as its icon, its label and its
 * padding. A tap unfolds the section and scrolls to it. A long-press lifts the chip into a drag:
 * the chips reorder live under the finger, and the sections below follow on drop.
 */
public class ShiroikumaIndexPreference extends Preference {

    /** What the page does when a chip is tapped or the order changes. */
    public interface Host {
        @NonNull
        List<ShiroikumaSectionCategory> sections();

        void jump(@NonNull ShiroikumaSectionCategory section);

        void reorder(@NonNull List<String> order);
    }

    public static final String KEY = "shiroikuma_index";

    private final Host mHost;
    private ShiroikumaFlowLayout mFlow;
    private View mDragged;
    private int mLastTarget = -1;

    public ShiroikumaIndexPreference(@NonNull Context context, @NonNull Host host) {
        super(context);
        mHost = host;
        setLayoutResource(R.layout.preference_shiroikuma_index);
        setKey(KEY);
        setPersistent(false);
        setSelectable(false);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        // The row's root itself, never looked up by id: AndroidX replaces the id of every row's
        // root view when it binds the row.
        if (!(holder.itemView instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) holder.itemView;
        frame.removeAllViews();
        Context c = getContext();
        mFlow = new ShiroikumaFlowLayout(c, ShiroikumaDialogs.dp(c, 6));
        int frameColor = ShiroikumaTheme.color(c, ShiroikumaTheme.CHIP_FRAME);
        int labelColor = ShiroikumaTheme.color(c, ShiroikumaTheme.CHIP_TEXT);
        for (final ShiroikumaSectionCategory section : mHost.sections()) {
            final TextView chip = new TextView(c);
            chip.setText(section.getTitle());
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            chip.setTextColor(labelColor);
            chip.setSingleLine(true);
            chip.setBackground(ShiroikumaRows.pill(c, frameColor, false));
            chip.setPaddingRelative(ShiroikumaDialogs.dp(c, 9), ShiroikumaDialogs.dp(c, 5), ShiroikumaDialogs.dp(c, 11), ShiroikumaDialogs.dp(c, 5));
            // The same icon in the same colour as the section's heading, sized by the chip's label.
            ShiroikumaRows.labelIcon(chip, section.icon(), ShiroikumaTheme.color(c, section.colorRole()), false, 0, 0);
            chip.setCompoundDrawablePadding(ShiroikumaDialogs.dp(c, 5));
            chip.setTag(section.id());
            chip.setOnClickListener(v -> mHost.jump(section));
            chip.setOnLongClickListener(v -> {
                mDragged = v;
                mLastTarget = -1;
                v.setAlpha(0.3f);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) v.startDragAndDrop(null, new View.DragShadowBuilder(v), v, 0);
                else v.startDrag(null, new View.DragShadowBuilder(v), v, 0);
                return true;
            });
            mFlow.addView(chip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        mFlow.setOnDragListener((v, event) -> onDrag(event));
        frame.addView(mFlow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Draws the chips again, in the order (and the language, the colours) the page now has. */
    public void redraw() {
        notifyChanged();
    }

    private boolean onDrag(@NonNull DragEvent event) {
        if (mDragged == null || mFlow == null) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_LOCATION: {
                // Live: the chip moves to the slot under the finger. The drag itself is carried by
                // the shadow, so moving the view in the tree does not end it.
                int target = mFlow.childAt(event.getX(), event.getY());
                int from = mFlow.indexOfChild(mDragged);
                if (target < 0 || target == from) {
                    mLastTarget = -1;
                    return true;
                }
                // Chips differ in width, so a swap can put the neighbour back under the finger:
                // without this guard the two would flap on every event.
                if (target == mLastTarget) return true;
                mFlow.removeView(mDragged);
                mFlow.addView(mDragged, target);
                mLastTarget = from;
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED: {
                mDragged.setAlpha(1f);
                mDragged = null;
                List<String> order = new ArrayList<>();
                for (int i = 0; i < mFlow.getChildCount(); i++) order.add((String) mFlow.getChildAt(i).getTag());
                // The sections follow; their fold state is untouched.
                mHost.reorder(order);
                return true;
            }
            default:
                return true;
        }
    }
}
