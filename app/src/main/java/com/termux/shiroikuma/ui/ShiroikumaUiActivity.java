package com.termux.shiroikuma.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.Preference;

import com.termux.R;

/**
 * shiroikuma-termux (Phase 4): host of the 白い熊 Termux UI page. Opened from a long-press on the
 * drawer gear, from the context menu's "白い熊 Termux UI" item (upstream's Style slot) and from the
 * first row of the Settings root screen.
 *
 * <p>Theme {@code Theme.ShiroikumaUi} is translucent, so while this page is in front the
 * TermuxActivity underneath is paused but never stopped — its ACTION_RELOAD_STYLE receiver stays
 * registered and every change previews live (see {@link ShiroikumaStyle}). {@link #onResume}
 * fires one more reload for the case where an opaque picker (the font document picker) did stop
 * it in between.
 */
public class ShiroikumaUiActivity extends AppCompatActivity {

    public static void open(@NonNull Context context) {
        Intent intent = new Intent(context, ShiroikumaUiActivity.class);
        context.startActivity(intent);
    }

    /** The Settings root row: one line in SettingsActivity.RootPreferencesFragment. */
    public static void bindSettingsRow(@Nullable Preference row) {
        if (row == null) return;
        row.setOnPreferenceClickListener(p -> {
            open(p.getContext());
            return true;
        });
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shiroikuma_ui);
        Toolbar toolbar = findViewById(com.termux.shared.R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.shiroikuma_ui_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.shiroikuma_ui_container, new ShiroikumaUiFragment())
                .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Posted, not sent: when an opaque picker closes, the terminal behind us is restarted in the
        // same batch of transactions — give its onStart() (receiver registration) a moment first.
        final Context app = getApplicationContext();
        getWindow().getDecorView().postDelayed(() -> ShiroikumaStyle.changed(app), 300);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
