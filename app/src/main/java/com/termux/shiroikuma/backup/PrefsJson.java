package com.termux.shiroikuma.backup;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * shiroikuma-termux (Phase 4b): the family's type-tagged SharedPreferences dump —
 * {@code {"<file>":{"<key>":{"t":"int|long|float|bool|string|set","v":…}}}} — and its merge.
 *
 * <p>The import merges per key with {@code commit()}: 応用管理 force-stops the process the instant
 * an import reports success, and an {@code apply()} still in flight would be lost with it.
 */
public final class PrefsJson {

    private PrefsJson() {
    }

    /** One file's typed dump; {@code skip} names keys left out (device-local bookkeeping). */
    @NonNull
    public static JSONObject dumpFile(@NonNull Context context, @NonNull String file, @Nullable Set<String> skip) throws JSONException {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(file, Context.MODE_PRIVATE);
        JSONObject out = new JSONObject();
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            String key = e.getKey();
            Object v = e.getValue();
            if (key == null || v == null) continue;
            if (skip != null && skip.contains(key)) continue;
            JSONObject entry = new JSONObject();
            if (v instanceof Integer) {
                entry.put("t", "int").put("v", (Integer) v);
            } else if (v instanceof Long) {
                entry.put("t", "long").put("v", (Long) v);
            } else if (v instanceof Float) {
                entry.put("t", "float").put("v", ((Float) v).doubleValue());
            } else if (v instanceof Boolean) {
                entry.put("t", "bool").put("v", (Boolean) v);
            } else if (v instanceof String) {
                entry.put("t", "string").put("v", (String) v);
            } else if (v instanceof Set) {
                JSONArray arr = new JSONArray();
                for (Object o : (Set<?>) v) if (o != null) arr.put(o.toString());
                entry.put("t", "set").put("v", arr);
            } else {
                continue;
            }
            out.put(key, entry);
        }
        return out;
    }

    /**
     * Merges one file's dump into the live file, key by key, {@code commit()}ed. Returns the number
     * of keys written; unknown types and keys in {@code skip} are left alone.
     */
    public static int mergeFile(@NonNull Context context, @NonNull String file, @NonNull JSONObject dump, @Nullable Set<String> skip) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(file, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        int n = 0;
        Iterator<String> keys = dump.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (skip != null && skip.contains(key)) continue;
            JSONObject entry = dump.optJSONObject(key);
            if (entry == null) continue;
            String t = entry.optString("t", "");
            try {
                switch (t) {
                    case "int":
                        ed.putInt(key, entry.getInt("v"));
                        break;
                    case "long":
                        ed.putLong(key, entry.getLong("v"));
                        break;
                    case "float":
                        ed.putFloat(key, (float) entry.getDouble("v"));
                        break;
                    case "bool":
                        ed.putBoolean(key, entry.getBoolean("v"));
                        break;
                    case "string":
                        ed.putString(key, entry.getString("v"));
                        break;
                    case "set": {
                        JSONArray arr = entry.getJSONArray("v");
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
                        ed.putStringSet(key, set);
                        break;
                    }
                    default:
                        continue;
                }
            } catch (JSONException e) {
                continue;
            }
            n++;
        }
        ed.commit();
        return n;
    }
}
