package com.termux.shiroikuma.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * shiroikuma-termux: a top-level section of the 白い熊 Termux UI page. Its heading — bold,
 * underlined as wide as its text, the section's icon at the left in the section's colour, a › when
 * folded and a ⌄ when open at the right — folds and unfolds its rows on a tap. The 1 px line above
 * the heading stays, folded or open. Folded by default; the state is remembered
 * ({@link ShiroikumaPageState}).
 *
 * <p>The section's id is its key without the {@code section_} prefix; icon and colour role come
 * from the page's section registry through {@link #look}, so heading and chip cannot drift apart.
 */
public class ShiroikumaSectionCategory extends PreferenceCategory {

    public static final String KEY_PREFIX = "section_";
    private static final String HIDDEN = "shiroikuma_hidden";

    private int mIcon = ShiroikumaIcons.ICON_DOT;
    @NonNull private String mColorRole = ShiroikumaTheme.SECTION_BASE;
    private boolean mOpen;

    public ShiroikumaSectionCategory(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_category_shiroikuma);
        // A PreferenceCategory reports itself as not enabled, and the library would grey out and
        // disable its heading's views for that — a disabled view takes no tap, and the heading folds.
        setShouldDisableView(false);
    }

    /**
     * A row that is only shown under a condition of its own (the token row) says so here, so that
     * unfolding its section does not bring it back.
     */
    public static void setWanted(@NonNull Preference row, boolean wanted) {
        row.getExtras().putBoolean(HIDDEN, !wanted);
        PreferenceGroup parent = row.getParent();
        boolean open = !(parent instanceof ShiroikumaSectionCategory) || ((ShiroikumaSectionCategory) parent).mOpen;
        row.setVisible(wanted && open);
    }

    @NonNull
    public String id() {
        String key = getKey();
        return key != null && key.startsWith(KEY_PREFIX) ? key.substring(KEY_PREFIX.length()) : String.valueOf(key);
    }

    public void look(int icon, @NonNull String colorRole) {
        mIcon = icon;
        mColorRole = colorRole;
    }

    public int icon() {
        return mIcon;
    }

    @NonNull
    public String colorRole() {
        return mColorRole;
    }

    public boolean isOpen() {
        return mOpen;
    }

    /** Shows or hides the section's rows; the heading and the line above it always stay. */
    public void setOpen(boolean open, boolean remember) {
        mOpen = open;
        for (int i = 0; i < getPreferenceCount(); i++) {
            Preference p = getPreference(i);
            p.setVisible(open && !p.getExtras().getBoolean(HIDDEN, false));
        }
        if (remember) ShiroikumaPageState.setOpen(getContext(), id(), open);
        notifyChanged();
    }

    @Override
    public boolean addPreference(@NonNull Preference preference) {
        boolean added = super.addPreference(preference);
        if (added && !mOpen) preference.setVisible(false);
        return added;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        Context c = getContext();
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);
        View divider = holder.findViewById(R.id.sk_divider);
        if (divider != null) divider.setBackgroundColor(ShiroikumaTheme.color(c, ShiroikumaTheme.PAGE_DIVIDER));
        int heading = ShiroikumaTheme.color(c, ShiroikumaTheme.PAGE_HEADING);
        View titleView = holder.findViewById(android.R.id.title);
        if (titleView instanceof TextView) {
            TextView title = (TextView) titleView;
            title.setTextColor(heading);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            title.setAllCaps(false);
            ShiroikumaRows.labelIcon(title, mIcon, ShiroikumaTheme.color(c, mColorRole), false, 0, 18);
        }
        View chevron = holder.findViewById(R.id.sk_chevron);
        if (chevron instanceof ImageView) {
            ((ImageView) chevron).setImageDrawable(new ShiroikumaIcons(c,
                mOpen ? ShiroikumaIcons.ICON_CHEVRON_DOWN : ShiroikumaIcons.ICON_CHEVRON, heading, 22));
        }
        holder.itemView.setBackground(ShiroikumaRows.rowRipple(heading));
        holder.itemView.setEnabled(true);
        holder.itemView.setFocusable(true);
        holder.itemView.setClickable(true);
        holder.itemView.setOnClickListener(v -> setOpen(!mOpen, true));
    }
}
