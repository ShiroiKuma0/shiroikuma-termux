package com.termux.shiroikuma.automation;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * shiroikuma-termux (Phase 4b): the gate in front of the external-automation surface — the
 * {@link StateExportReceiver} broadcasts and the {@link AutomationProvider} data door — as the
 * sister-app contract v2 defines it (a port of raikidoban's {@code AutomationAuth}).
 *
 * <p>Device-local by design: these values live in their OWN SharedPreferences file, which is not a
 * backup category, so the token never travels inside a ZIP.
 *
 * <p>v2: {@code automation_enabled} defaults to <b>true</b> and the token is opt-in through
 * {@code automation_require_token} (default <b>false</b>) — a pasted secret cannot survive the
 * wipe this feature exists to recover from. A token handed to an app that does not require one is
 * IGNORED, never refused. All three flags are written with {@code commit()}: the gate fails OPEN,
 * so a lost {@code setEnabled(false)} would silently reopen the door.
 *
 * <p>The whole decision lives in {@link #refuse} and nowhere else.
 */
public final class AutomationAuth {

    public static final String PREFS = "shiroikuma_automation";
    private static final String KEY_ENABLED = "automation_enabled";
    private static final String KEY_REQUIRE_TOKEN = "automation_require_token";
    private static final String KEY_TOKEN = "automation_token";

    private AutomationAuth() {
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The master switch. <b>Default ON</b> (v2) — a clean phone has nothing to turn on. */
    public static boolean isEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    /** Whether a caller must also present the token. <b>Default OFF</b> (v2). */
    public static boolean isTokenRequired(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_REQUIRE_TOKEN, false);
    }

    public static void setTokenRequired(@NonNull Context context, boolean required) {
        prefs(context).edit().putBoolean(KEY_REQUIRE_TOKEN, required).commit();
    }

    /**
     * The one gate. {@code null} to proceed, otherwise the exact {@code ERROR:} line to answer
     * with — "automation disabled" and "bad token" stay distinct because they debug differently.
     * When the token is not required, {@code candidate} is not even looked at.
     */
    @Nullable
    public static String refuse(@NonNull Context context, @Nullable String candidate) {
        if (!isEnabled(context)) return "ERROR:automation disabled";
        if (isTokenRequired(context) && !isTokenValid(context, candidate)) return "ERROR:bad token";
        return null;
    }

    /** The shared secret — 24 random bytes, hex; generated on first read so the row always shows one. */
    @NonNull
    public static String token(@NonNull Context context) {
        String stored = prefs(context).getString(KEY_TOKEN, null);
        if (stored != null && !stored.isEmpty()) return stored;
        return regenerateToken(context);
    }

    @NonNull
    public static String regenerateToken(@NonNull Context context) {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        String token = sb.toString();
        prefs(context).edit().putString(KEY_TOKEN, token).commit();
        return token;
    }

    /** Abbreviated form for the settings row — {@code 80922d8c…4c49a87c}. */
    @NonNull
    public static String abbreviate(@Nullable String token) {
        if (token == null) return "";
        if (token.length() <= 20) return token;
        return token.substring(0, 8) + "…" + token.substring(token.length() - 8);
    }

    /** Constant-time ({@link MessageDigest#isEqual}) so a wrong token leaks nothing through timing. */
    public static boolean isTokenValid(@NonNull Context context, @Nullable String candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), token(context).getBytes(StandardCharsets.UTF_8));
    }
}
