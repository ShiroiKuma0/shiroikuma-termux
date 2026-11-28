package com.termux.shiroikuma;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Typeface;
import android.os.Build;
import android.os.LocaleList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shiroikuma.ui.ShiroikumaDialogs;
import com.termux.shiroikuma.ui.ShiroikumaIcons;
import com.termux.shiroikuma.ui.ShiroikumaRows;
import com.termux.shiroikuma.ui.ShiroikumaTheme;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * shiroikuma-termux: the app's own language — the 「App language」 section of the UI page.
 *
 * <ul>
 * <li>The chosen BCP-47 tag is stored in the page's {@code shiroikuma_ui} file
 *     ({@link ShiroikumaTheme#APP_LANGUAGE}; empty / absent = the system's), so it travels in the
 *     export, and is RE-ASSERTED at every process start ({@link #reassert}, from
 *     {@code ShiroikumaLifecycle.install}): EMUI does not keep the system's per-app locale across
 *     a sideload update.</li>
 * <li>AppCompat activities follow {@link AppCompatDelegate#setApplicationLocales}; the manifest
 *     declares {@code res/xml/locales_config.xml} (Android 13+), and the picker's list is READ
 *     from that file ({@link #shippedTags}), so the two cannot drift apart.</li>
 * <li>Below Android 13 that choice does not reach the application context — nor a plain
 *     {@link Activity}. {@link #localize} lays the locale over a context's own resources: the
 *     application's (shared by its services, receivers and providers — the notifications, the
 *     widget, the automation replies) at start and after every configuration change, and every
 *     non-AppCompat activity as it is created.</li>
 * </ul>
 */
public final class AppLanguage {

    private static final String LOG_TAG = "AppLanguage";

    private AppLanguage() {
    }

    /** The stored tag; empty = follow the system. */
    @NonNull
    public static String tag(@NonNull Context context) {
        String tag = ShiroikumaTheme.getString(context, ShiroikumaTheme.APP_LANGUAGE);
        return tag == null ? "" : tag;
    }

    /** Stores the tag and applies it at once: every started AppCompat activity is recreated. */
    public static void set(@NonNull Context context, @NonNull String tag) {
        Context app = context.getApplicationContext();
        ShiroikumaTheme.prefs(app).edit().putString(ShiroikumaTheme.APP_LANGUAGE, tag).commit();
        localize(app, true);
        applyToActivities(app, tag);
    }

    /**
     * Android 13+: the system's own per-app locale, asked for directly — AppCompat's call finds
     * no LocaleManager while no activity exists yet (process start). Below: AppCompat's requested
     * locales, which every AppCompat activity applies as it attaches.
     */
    private static void applyToActivities(@NonNull Context app, @NonNull String tag) {
        if (Build.VERSION.SDK_INT >= 33) Api33.setApplicationLocales(app, tag);
        else AppCompatDelegate.setApplicationLocales(tag.isEmpty() ? LocaleListCompat.getEmptyLocaleList() : LocaleListCompat.forLanguageTags(tag));
    }

    /** Kept apart so that older runtimes never have to resolve {@code LocaleManager}. */
    @androidx.annotation.RequiresApi(33)
    private static final class Api33 {
        static void setApplicationLocales(@NonNull Context app, @NonNull String tag) {
            android.app.LocaleManager manager = app.getSystemService(android.app.LocaleManager.class);
            if (manager == null) return;
            LocaleList wanted = tag.isEmpty() ? LocaleList.getEmptyLocaleList() : LocaleList.forLanguageTags(tag);
            if (!manager.getApplicationLocales().equals(wanted)) manager.setApplicationLocales(wanted);
        }
    }

    /** Once per process, before the first activity: the stored choice becomes the app's locale. */
    public static void reassert(@NonNull final Application application) {
        try {
            String tag = tag(application);
            if (!tag.isEmpty()) {
                localize(application, false);
                applyToActivities(application, tag);
            }
            // A configuration change (rotation, fold, font scale) hands the application's
            // resources the system's configuration again — lay ours back over it.
            application.registerComponentCallbacks(new ComponentCallbacks() {
                @Override
                public void onConfigurationChanged(@NonNull Configuration newConfig) {
                    localize(application, false);
                }

                @Override
                public void onLowMemory() {
                }
            });
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot apply the app language", e);
        }
    }

    /** A non-AppCompat activity, inside its {@code super.onCreate()}: AppCompat does not reach it. */
    public static void onActivityCreated(@NonNull Activity activity) {
        if (!(activity instanceof AppCompatActivity)) localize(activity, false);
    }

    /**
     * Lays the chosen locale over {@code context}'s own resources. With no language chosen this
     * does nothing, unless {@code restoreSystem} — then the system's locales are put back (the
     * choice was just cleared in this process).
     */
    @SuppressWarnings("deprecation") // updateConfiguration: the one call that reaches the shared application resources
    private static void localize(@NonNull Context context, boolean restoreSystem) {
        String tag = tag(context);
        if (tag.isEmpty() && !restoreSystem) return;
        Resources res = context.getResources();
        Configuration cfg = new Configuration(res.getConfiguration());
        Configuration system = Resources.getSystem().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cfg.setLocales(tag.isEmpty() ? system.getLocales() : new LocaleList(Locale.forLanguageTag(tag)));
        } else {
            cfg.setLocale(tag.isEmpty() ? system.locale : Locale.forLanguageTag(tag));
        }
        res.updateConfiguration(cfg, res.getDisplayMetrics());
    }

    /** The languages the app ships: the tags of {@code res/xml/locales_config.xml}. */
    @NonNull
    public static List<String> shippedTags(@NonNull Context context) {
        List<String> tags = new ArrayList<>();
        try (XmlResourceParser parser = context.getResources().getXml(R.xml.locales_config)) {
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event != XmlPullParser.START_TAG || !"locale".equals(parser.getName())) continue;
                String name = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name");
                if (name != null && !name.isEmpty() && !tags.contains(name)) tags.add(name);
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot read locales_config.xml", e);
        }
        return tags;
    }

    /** A language's name in its own language, first letter upper-cased ("English", "日本語"). */
    @NonNull
    public static String nameOf(@NonNull String tag) {
        Locale locale = Locale.forLanguageTag(tag);
        String name = locale.getDisplayLanguage(locale);
        if (name.isEmpty()) return tag;
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    /** What the 「Language: …」 row shows: the chosen language's own name, or 「System default」. */
    @NonNull
    public static String currentName(@NonNull Context context) {
        String tag = tag(context);
        return tag.isEmpty() ? context.getString(R.string.shiroikuma_lang_system) : nameOf(tag);
    }

    // ---- the picker -----------------------------------------------------------------------------

    /**
     * The single-choice dialog in the family's settled form (2026-11-27). Top to bottom: the title
     * with the globe; 日本語, then English, as two full-width outlined pills that never scroll
     * (the current one 3 dp and bold with a filled mark, the other 1.25 dp; both thin when the
     * current language is neither); one framed, rounded box that scrolls by itself, with 「System
     * default」 as its first row and every other listed language after it, sorted by its own name
     * — plain rows with 1 px hairlines, the current one in a thick pill; one regular Cancel pill.
     * Each pill and row: selection ring, flag (a bundled image; a globe for 「System default」 and
     * for a language with no country), the language's own name with its country beneath in dim
     * gold, and the tag in dim-gold monospace in a right-aligned column. A language declared
     * without a country shows the flag, country and tag of its likely country ({@code ja} →
     * {@code ja-JP}); what is stored and applied stays the declared tag.
     */
    public static void showPicker(@NonNull final Activity activity) {
        final String current = tag(activity);
        List<String> rest = shippedTags(activity);
        String japanese = takePinned(rest, "ja", "JP");
        String english = takePinned(rest, "en", "US");
        if (!current.isEmpty() && !current.equals(japanese) && !current.equals(english) && !rest.contains(current)) rest.add(current);
        Collections.sort(rest, (a, b) -> nameOf(a).compareToIgnoreCase(nameOf(b)));
        rest.add(0, ""); // 「System default」: the only way back from a chosen language

        int frame = ShiroikumaTheme.color(activity, ShiroikumaTheme.DIALOG_BUTTON);
        int divider = ShiroikumaTheme.color(activity, ShiroikumaTheme.PAGE_DIVIDER);

        LinearLayout box = ShiroikumaDialogs.infoBox(activity, activity.getString(R.string.shiroikuma_ui_section_language), null);
        if (box.getChildCount() > 0 && box.getChildAt(0) instanceof TextView) {
            ShiroikumaRows.labelIcon((TextView) box.getChildAt(0), ShiroikumaIcons.ICON_GLOBE,
                ShiroikumaTheme.color(activity, ShiroikumaTheme.DIALOG_TITLE), false, 0, 18);
        }

        // Our own window instead of ShiroikumaDialogs.panel: that one scrolls as a whole, and here
        // only the box in the middle may scroll.
        android.widget.FrameLayout holder = new android.widget.FrameLayout(activity);
        int m = ShiroikumaDialogs.dp(activity, 10);
        holder.setPadding(m, m, m, m);
        holder.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(holder).create();
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        ShiroikumaDialogs.transparentWindow(dialog);

        // ---- the pinned pair
        boolean first = true;
        for (String tag : new String[]{japanese, english}) {
            if (tag == null) continue;
            boolean isCurrent = tag.equals(current);
            View row = row(activity, dialog, tag, current);
            row.setBackground(ShiroikumaRows.pill(activity, frame, isCurrent));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = ShiroikumaDialogs.dp(activity, first ? 12 : 6);
            box.addView(row, lp);
            first = false;
        }

        // ---- the box that scrolls by itself
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < rest.size(); i++) {
            String tag = rest.get(i);
            boolean isCurrent = tag.equals(current);
            boolean previousIsCurrent = i > 0 && rest.get(i - 1).equals(current);
            if (i > 0 && !isCurrent && !previousIsCurrent) {
                View line = new View(activity);
                line.setBackgroundColor(divider);
                LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
                lineLp.setMarginStart(ShiroikumaDialogs.dp(activity, 14));
                lineLp.setMarginEnd(ShiroikumaDialogs.dp(activity, 14));
                list.addView(line, lineLp);
            }
            View row = row(activity, dialog, tag, current);
            row.setBackground(isCurrent ? ShiroikumaRows.pill(activity, frame, true)
                : ShiroikumaRows.rowRipple(ShiroikumaTheme.color(activity, ShiroikumaTheme.DIALOG_TEXT)));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (isCurrent) {
                rowLp.topMargin = ShiroikumaDialogs.dp(activity, 3);
                rowLp.bottomMargin = ShiroikumaDialogs.dp(activity, 3);
            }
            list.addView(row, rowLp);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        android.graphics.drawable.GradientDrawable boxFrame = new android.graphics.drawable.GradientDrawable();
        boxFrame.setColor(0);
        boxFrame.setCornerRadius(ShiroikumaDialogs.dp(activity, 12));
        boxFrame.setStroke(Math.max(1, Math.round(1.25f * activity.getResources().getDisplayMetrics().density)),
            ShiroikumaTheme.color(activity, ShiroikumaTheme.DIALOG_BORDER_COLOR));
        scroll.setBackground(boxFrame);
        scroll.setClipToOutline(true);
        int pad = ShiroikumaDialogs.dp(activity, 4);
        scroll.setPadding(pad, pad, pad, pad);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // height 0 + weight: as tall as its rows, and the one part that gives way on a short screen
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = ShiroikumaDialogs.dp(activity, 10);
        box.addView(scroll, scrollLp);

        LinearLayout buttons = ShiroikumaDialogs.buttonRow(activity);
        ShiroikumaDialogs.addPill(buttons, ShiroikumaDialogs.pill(activity, activity.getString(android.R.string.cancel), v -> dialog.dismiss()));
        box.addView(buttons);
        dialog.show();
    }

    /**
     * Takes the one listed tag of {@code language} out of {@code tags} for the pinned pair: the
     * variant of {@code country} when there are several, else the first; {@code null} when the app
     * does not list the language.
     */
    @Nullable
    private static String takePinned(@NonNull List<String> tags, @NonNull String language, @NonNull String country) {
        String found = null;
        for (String tag : tags) {
            Locale locale = Locale.forLanguageTag(tag);
            if (!language.equals(locale.getLanguage())) continue;
            if (found == null || country.equals(locale.getCountry())) found = tag;
        }
        if (found != null) tags.remove(found);
        return found;
    }

    /** {@code tag} with its likely country filled in ({@code ja} → {@code ja-JP}) — for display only. */
    @NonNull
    private static Locale shownLocale(@NonNull String tag) {
        Locale locale = Locale.forLanguageTag(tag);
        if (!locale.getCountry().isEmpty()) return locale;
        String country = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                country = android.icu.util.ULocale.addLikelySubtags(android.icu.util.ULocale.forLocale(locale)).getCountry();
            } catch (RuntimeException e) {
                // the short table below
            }
        }
        if (country.isEmpty()) {
            if ("ja".equals(locale.getLanguage())) country = "JP";
            else if ("en".equals(locale.getLanguage())) country = "US";
        }
        // "001" and the like: a language without a sensible country (Esperanto) keeps the globe
        return country.length() == 2 ? new Locale(locale.getLanguage(), country) : locale;
    }

    /** The bundled flag (Noto Color Emoji) of a listed language's country; 0 = the globe. */
    private static int flagOf(@NonNull String country) {
        switch (country) {
            case "JP":
                return R.drawable.shiroikuma_flag_jp;
            case "US":
                return R.drawable.shiroikuma_flag_us;
            default:
                return 0;
        }
    }

    /** One pill or row, without its background: ring · flag · name over country · tag. */
    @NonNull
    private static View row(@NonNull final Activity activity, @NonNull final AlertDialog dialog, @NonNull final String tag, @NonNull final String current) {
        boolean isCurrent = tag.equals(current);
        int text = ShiroikumaTheme.color(activity, ShiroikumaTheme.DIALOG_TEXT);
        int note = ShiroikumaTheme.color(activity, ShiroikumaTheme.PAGE_NOTE);
        int off = ShiroikumaTheme.color(activity, ShiroikumaTheme.SWITCH_OFF);
        int frame = ShiroikumaTheme.color(activity, ShiroikumaTheme.DIALOG_BUTTON);
        Locale shown = tag.isEmpty() ? null : shownLocale(tag);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(ShiroikumaDialogs.dp(activity, 14), ShiroikumaDialogs.dp(activity, 9), ShiroikumaDialogs.dp(activity, 14), ShiroikumaDialogs.dp(activity, 9));

        ImageView ring = new ImageView(activity);
        ring.setImageDrawable(ring(activity, isCurrent ? frame : off, isCurrent));
        row.addView(ring, new LinearLayout.LayoutParams(ShiroikumaDialogs.dp(activity, 20), ShiroikumaDialogs.dp(activity, 20)));

        ImageView flag = new ImageView(activity);
        int flagRes = shown == null ? 0 : flagOf(shown.getCountry());
        if (flagRes != 0) flag.setImageResource(flagRes);
        else flag.setImageDrawable(ShiroikumaIcons.ofPx(ShiroikumaIcons.ICON_GLOBE, text, ShiroikumaDialogs.dp(activity, 22)));
        flag.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams flagLp = new LinearLayout.LayoutParams(ShiroikumaDialogs.dp(activity, 30), ShiroikumaDialogs.dp(activity, 22));
        flagLp.setMarginStart(ShiroikumaDialogs.dp(activity, 12));
        row.addView(flag, flagLp);

        LinearLayout names = new LinearLayout(activity);
        names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams namesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        namesLp.setMarginStart(ShiroikumaDialogs.dp(activity, 12));
        names.addView(ShiroikumaDialogs.text(activity, tag.isEmpty() ? activity.getString(R.string.shiroikuma_lang_system) : nameOf(tag), 16, text, isCurrent));
        String country = shown == null ? "" : shown.getDisplayCountry(shown);
        if (!country.isEmpty()) names.addView(ShiroikumaDialogs.text(activity, country, 13, note, false));
        row.addView(names, namesLp);

        if (shown != null) {
            TextView tagView = ShiroikumaDialogs.text(activity, shown.toLanguageTag(), 13, note, false);
            tagView.setTypeface(Typeface.MONOSPACE);
            tagView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tagView.setGravity(Gravity.END);
            LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tagLp.setMarginStart(ShiroikumaDialogs.dp(activity, 10));
            row.addView(tagView, tagLp);
        }

        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (!tag.equals(current)) set(activity, tag);
        });
        return row;
    }

    /** The selection mark of a row: a ring, with a dot inside on the current row. */
    @NonNull
    private static android.graphics.drawable.Drawable ring(@NonNull Context context, int color, boolean dot) {
        int size = ShiroikumaDialogs.dp(context, 20);
        android.graphics.drawable.GradientDrawable outer = new android.graphics.drawable.GradientDrawable();
        outer.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        outer.setColor(0);
        outer.setStroke(Math.max(1, ShiroikumaDialogs.dp(context, 2)), color);
        outer.setSize(size, size);
        if (!dot) return outer;
        android.graphics.drawable.GradientDrawable inner = new android.graphics.drawable.GradientDrawable();
        inner.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        inner.setColor(color);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{outer, inner});
        int inset = ShiroikumaDialogs.dp(context, 6);
        layers.setLayerInset(1, inset, inset, inset, inset);
        return layers;
    }
}
