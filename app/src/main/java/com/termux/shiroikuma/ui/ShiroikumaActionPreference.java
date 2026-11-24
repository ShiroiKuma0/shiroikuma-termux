package com.termux.shiroikuma.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * shiroikuma-termux: a row of the 白い熊 Termux UI page that DOES something when tapped — opens a
 * screen, a panel or a window, or runs an action — drawn as an outlined pill that hugs its label,
 * so it can never be taken for a heading or a note (the family's settings-page rule).
 *
 * <p>Layout {@code preference_shiroikuma_action.xml}: the title is the pill, the summary sits
 * beneath it in dim gold, aligned with the label. Only the pill is the touch target (the ripple
 * stays inside it); the row itself is inert. {@code app:skMain} = the section's one main action
 * (3 dp frame, bold; otherwise 1.25 dp, normal), {@code app:skOpens} = it opens something, so a ›
 * stands at the pill's right end, {@code app:skDestructive} = it removes something, so frame, icon
 * and label are red. Never filled. The pill itself is drawn by {@link ShiroikumaRows#bind} from
 * the page's colour roles; this class carries the three flags and the click.
 */
public class ShiroikumaActionPreference extends ShiroikumaPreference {

    private boolean mMain;
    private boolean mOpens;
    private boolean mDestructive;

    public ShiroikumaActionPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ShiroikumaActionPreference);
        mMain = a.getBoolean(R.styleable.ShiroikumaActionPreference_skMain, false);
        mOpens = a.getBoolean(R.styleable.ShiroikumaActionPreference_skOpens, false);
        mDestructive = a.getBoolean(R.styleable.ShiroikumaActionPreference_skDestructive, false);
        a.recycle();
        setLayoutResource(R.layout.preference_shiroikuma_action);
    }

    public boolean isMain() {
        return mMain;
    }

    public boolean opens() {
        return mOpens;
    }

    public boolean isDestructive() {
        return mDestructive;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        // The row is not the button: the pill is.
        holder.itemView.setOnClickListener(null);
        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        View pill = holder.findViewById(android.R.id.title);
        if (pill == null) return;
        pill.setEnabled(isEnabled());
        pill.setClickable(true);
        pill.setFocusable(true);
        pill.setOnClickListener(v -> {
            OnPreferenceClickListener listener = getOnPreferenceClickListener();
            if (listener != null) listener.onPreferenceClick(this);
        });
    }
}
