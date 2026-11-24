package com.termux.shiroikuma.ui;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * shiroikuma-termux: the arrangement of the 白い熊 Termux UI page — the order of its sections and
 * which of them are open. Two small comma-joined lists in the page's own {@code shiroikuma_ui}
 * file, so they travel in the export with the colours; a reset of the colours keeps them
 * ({@link ShiroikumaTheme#reset}). Every section is folded on a fresh install.
 */
public final class ShiroikumaPageState {

    private ShiroikumaPageState() {
    }

    /** The stored order; sections it does not know yet are appended in their default order. */
    @NonNull
    public static List<String> order(@NonNull Context context, @NonNull List<String> defaults) {
        List<String> out = new ArrayList<>();
        String stored = ShiroikumaTheme.getString(context, ShiroikumaTheme.SECTION_ORDER);
        if (!TextUtils.isEmpty(stored)) {
            for (String id : stored.split(",")) {
                if (defaults.contains(id) && !out.contains(id)) out.add(id);
            }
        }
        for (String id : defaults) if (!out.contains(id)) out.add(id);
        return out;
    }

    public static void setOrder(@NonNull Context context, @NonNull List<String> order) {
        ShiroikumaTheme.setString(context, ShiroikumaTheme.SECTION_ORDER, TextUtils.join(",", order));
    }

    public static void resetOrder(@NonNull Context context) {
        ShiroikumaTheme.setString(context, ShiroikumaTheme.SECTION_ORDER, null);
    }

    @NonNull
    private static Set<String> open(@NonNull Context context) {
        String stored = ShiroikumaTheme.getString(context, ShiroikumaTheme.SECTIONS_OPEN);
        return TextUtils.isEmpty(stored) ? new LinkedHashSet<String>() : new LinkedHashSet<>(Arrays.asList(stored.split(",")));
    }

    public static boolean isOpen(@NonNull Context context, @NonNull String id) {
        return open(context).contains(id);
    }

    public static void setOpen(@NonNull Context context, @NonNull String id, boolean value) {
        Set<String> set = open(context);
        if (value) set.add(id); else set.remove(id);
        ShiroikumaTheme.setString(context, ShiroikumaTheme.SECTIONS_OPEN, set.isEmpty() ? null : TextUtils.join(",", set));
    }
}
