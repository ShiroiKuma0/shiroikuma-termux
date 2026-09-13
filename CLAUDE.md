# CLAUDE.md — guide for Claude Code in this repo

**shiroikuma-termux** — 白い熊's fork of [Termux](https://github.com/termux/termux-app), the Android
terminal emulator and Linux environment (Java, ndk-build for the bootstrap loader; GPLv3). It **keeps
the `com.termux` app id** and installs *in place of* upstream (same id, our key), as **白い熊 Termux** —
the head of the `com.termux` shared-UID family (this app, `termux-api`, `termux-x11`, `termux-gui`,
白い熊 GNU Emacs) that is all signed with one keystore.

This repo (`ShiroiKuma0/shiroikuma-termux`) is a fork. We track upstream's **`master` branch tip** on
`master` and layer our customizations on `custom`.

## Read this first

Before any work, read **`.claude/skills/build-apk/SKILL.md`** (canonical build + delivery) and
**`.claude/skills/upstream-new-version/SKILL.md`** (upstream sync + rebase, with the mandatory
proceed-gated upstream-changes table — two of them here, see below). Publishing a release uses the
**global** `/publish-version` skill — this repo has no local copy.

## Fork workflow — READ THIS FIRST

### Git remotes & branches

- `origin` → `git@github.com:ShiroiKuma0/shiroikuma-termux.git` (push here).
- `upstream` → `https://github.com/termux/termux-app.git` (fetch only; its push URL is `DISABLED`).
- `master` — mirrors `upstream/master`, **fast-forward only**. No fork work here.
- `custom` — all our work, rebased onto `master` on each sync, and the GitHub default branch so the
  repo page lands on the fork.

Four more **fetch-only** remotes (push URL `DISABLED`, `tagOpt --no-tags`) carry the plugins that
are **absorbed into this app** (Phase 4 of the plan — all four done) — their sources are copied
into `app/`, their own Java packages kept, so porting an upstream change is a manual diff
(`git diff <recorded sha>..upstream-<x>/master -- app/src/main/java`), never a rebase:

| Plugin | Upstream remote · absorbed commit | Where its sources live here · what was changed |
| --- | --- | --- |
| Termux:Boot | `upstream-boot` → `https://github.com/termux/termux-boot.git` · **`a8493bd6ba016bc370af34aa65fcbe065cc00ced`** (2026-01-20) | `app/src/main/java/com/termux/boot/{BootReceiver,BootJobService}.java` (header comment in `BootReceiver`). Changes: the hard-coded `/data/data/com.termux/files/home/.termux/boot` → `TermuxConstants.TERMUX_BOOT_SCRIPTS_DIR`; the copied `TermuxService` literals → `TermuxConstants.TERMUX_APP.TERMUX_SERVICE{,_NAME}` (+ `EXTRA_RUNNER=app-shell` beside `EXTRA_BACKGROUND`); `Log` → `Logger`. **Dropped**: `BootActivity` + `overview.html` (the "open the app once" launcher screen) and its icons — no second launcher icon. Manifest: `com.termux.boot.BootReceiver` (`BOOT_COMPLETED`, unexported) + `BootJobService` (`BIND_JOB_SERVICE`); `RECEIVE_BOOT_COMPLETED` was already declared. **The receiver fires only once 白い熊 Termux has been opened once after install** (a never-launched package is in the stopped state and gets no `BOOT_COMPLETED`) |
| Termux:Widget | `upstream-widget` → `https://github.com/termux/termux-widget.git` · **`b3aacb8175fb2d7167ce8d1ad6817087498aa044`** (2026-01-21) | `app/src/main/java/com/termux/widget/` (`NaturalOrderComparator`, `ShortcutFile`, `TermuxCreateShortcutActivity` = the launcher's CREATE_SHORTCUT picker, `TermuxLaunchShortcutActivity` = the token-guarded shortcut target, `TermuxWidgetControlExecutorReceiver` + `TermuxWidgetControlsProviderService` = Android 11+ Device Controls, `TermuxWidgetProvider`, `TermuxWidgetService`, `receivers/SystemEventReceiver`, `utils/ShortcutUtils`; header comment in `TermuxWidgetProvider`), `res/layout/{widget_layout,widget_item,activity_termux_create_shortcut}.xml`, `res/drawable/{ripple_mask.xml,widget_preview.png}`, `res/xml/termux_appwidget_info.xml` (+ `android:description`), `res/values/termux_widget_strings.xml` (entities `白い熊 Termux` / `白い熊 Termux Widget`, help URL = ours). Changes: `TermuxWidgetApplication` → a stub (`setLogConfig` no-op; `TermuxApplication` owns the process), `activities/TermuxWidgetMainActivity` + launcher alias **dropped** (its dynamic-shortcut logic → `ShortcutUtils.create/removeDynamicShortcuts`, driven from the UI page; the limit now subtracts our static shortcuts and `addDynamicShortcuts` is guarded), `R.drawable.ic_launcher` → `R.mipmap.ic_launcher`, the list's colours / text size from `ShiroikumaTheme` (`widget_*`) applied to the `RemoteViews` on every refresh, `widget_layout.xml` black/yellow defaults + `@id/widget_title`. Deps `reactive-streams 1.0.3` + `rxjava 2.2.10` in `app/shiroikuma.gradle`. Files: `~/.shortcuts/` (+ `tasks/`, `icons/`), `~/.termux/widget/dynamic_shortcuts/` |
| Termux:Float | `upstream-float` → `https://github.com/termux/termux-float.git` · **`75352bde928e9888a2aa2ad7e130f16118391b75`** (2025-10-29) | `app/src/main/java/com/termux/window/` (`FloatingBubbleManager`, `TermuxFloatActivity`, `TermuxFloatPermissionActivity`, `TermuxFloatService`, `TermuxFloatSessionClient`, `TermuxFloatView`, `TermuxFloatViewClient`, `settings/properties/TermuxFloatAppSharedProperties`; header comment in `TermuxFloatService`), `res/layout/float_window.xml` (was `activity_main.xml`), `res/layout/float_permission.xml` (was `activity_permission.xml`), `res/drawable/{floating_window_background,ic_exit_icon,ic_minimize_icon,round_button_with_outline}.xml`, `res/values/termux_float_{strings,dimens}.xml` (`float_app_name` = `白い熊 Termux Float` for the notification title + channel). Changes: `TermuxFloatApplication` → a stub; `TermuxFloatActivity` is **not a launcher activity** (reached from the static shortcut "Floating terminal" and the UI page); notification icon = the app's `R.drawable.ic_service_notification`; the window frame (`float_bg`, `float_border_color`, `float_border_dp`, `float_corner_dp`) is a `GradientDrawable` built by `TermuxFloatView.applyWindowStyle()` (border = padding, corners clip; red stroke while resizing replaces `floating_window_background_resize.xml`), the notification text (`float_notification_text`) overrides upstream's hide/show hint, `TermuxFloatService.reloadStyle()` (static instance) restyles a running window live. **Dropped**: `styles.xml`, `mipmap/ic_service_notification`, launcher icons. The overlay-permission flow is upstream's (`addView` fails → `TermuxFloatPermissionActivity`); `SYSTEM_ALERT_WINDOW` was already declared. Its properties file stays `~/.termux/termux.float.properties`, its prefs `com.termux.window_preferences` (window x/y/size, font size) |
| Termux:Styling | `upstream-styling` → `https://github.com/termux/termux-styling.git` | no package of its own — absorbed into the **白い熊 Termux UI** page (Phase 4a: its 114 colour schemes (+ the house `shiroikuma.properties`) + 4 fonts under `app/src/main/assets/shiroikuma/`; termux-app already had the `ACTION_RELOAD_STYLE` plumbing) |

Shared plumbing of the absorption: `termux-shared`'s `TermuxWidgetAppSharedPreferences.build()` /
`TermuxFloatAppSharedPreferences.build()` (both overloads) resolve to **this app's own** preferences
file when the caller's package is `com.termux` (`absorbedPluginContext`), instead of looking up a
plugin package that is no longer installed (the `exitAppOnError` overload would otherwise kill the
app with a dialog). `TermuxConstants.TERMUX_{BOOT,WIDGET,FLOAT}_APP_NAME` **stay upstream's**
(`Termux:Boot` …): they label the upstream plugin projects in the About page's link list, like
`TERMUX_SITE`; our names live in the ported resources only. `app/proguard-rules.pro` keeps the three
packages' constructors.

**Uninstall the standalone Termux:Boot / Termux:Widget / Termux:Float APKs** once this build is on the
phone: they share the UID and the same `~/.termux/boot`, `~/.shortcuts` — with both installed every
boot script runs twice and the launcher offers two widgets and two "Floating terminal"s (the
standalone Termux:Float still works, but its window is not ours to style). The rebase guard for
these packages is in "Hard rules" below.

Nothing in the prefix addresses these by package name (they only call `TermuxService` and read
`~/.termux/*`), and their dependencies (`termux-shared`, `terminal-view`) already live in this repo.
`termux-api`, `termux-x11` and `termux-gui` stay **separate forks** (their CLI / loader / clients
target their own package names); `termux-tasker` is not forked.

**Upstream tracking: `git`** — `custom` is rebased onto every upstream commit, so the fork
versionName pins the upstream base: `<upstream>+<base date>.<HH-MM>.g<sha>+<BUILD_NUMBER, 3 digits>`.
See the global **`git-versioning`** skill. Why git and not tags: termux-app's `master` is far ahead of
its last release (`0.118.x` on the store, months of fixes on the branch — Sixel/iTerm images, the
OSC 52 buffer, multi-window flicker), and upstream's `versionName "0.118.0"` literal has stood still
through all of it, so the literal alone says nothing about how current we are.

### Our customizations (install identity + build)

| What | Value | Where |
| --- | --- | --- |
| applicationId | `com.termux` (**UNCHANGED** — the bootstrap / package prefix) | `app/build.gradle` → `namespace` (no `applicationId` line; AGP takes the namespace) |
| namespace (R/BuildConfig pkg) | `com.termux` (**never rename**) | `app/build.gradle` |
| sharedUserId | `com.termux` (**never rename**) | `app/src/main/AndroidManifest.xml` → `${TERMUX_PACKAGE_NAME}` placeholder |
| App label | `白い熊 Termux` — **done (Phase 3)** | `TermuxConstants.TERMUX_APP_NAME` in `termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java` (feeds notification channels / titles / crash reports / the default logcat tag) + the `<!ENTITY TERMUX_APP_NAME "白い熊 Termux">` in **both** `app/src/main/res/values/strings.xml` and `termux-shared/src/main/res/values/strings.xml` (launcher label = `application_name` = the entity); `TERMUX_API_APP_NAME` / its entity → `白い熊 Termux API`. `TERMUX_SITE` / `TERMUX_WIKI` are pinned to the literal `Termux Site` / `Termux Wiki` (they label upstream's manual, not us). `TERMUX_{BOOT,FLOAT,STYLING,TASKER,WIDGET}_APP_NAME` and their entities stay upstream's **on purpose** (Phase 4c): they label the upstream plugin projects in About's link list; the absorbed code shows `白い熊 Termux Widget` / `白い熊 Termux Float` from its own resources |
| UI page | `白い熊 Termux UI` — **done (Phase 4a appearance, Phase 4b Export/Import)**; opened by a long-press on the drawer gear, from the context-menu item (upstream's "Style" slot, `action_style_terminal` → `白い熊 Termux UI`) and from the first row of the Settings root screen — see "UI page" below | `app/src/main/java/com/termux/shiroikuma/{ShiroikumaLifecycle,ShiroikumaDefaults}.java` + `ui/` (activity, fragment, theme/chrome/style, pickers, dialogs, context menu, root view, `ExportImportPanel`, `AutomationTokenPreference`), `res/values/shiroikuma_{strings,theme}.xml`, `res/xml/preferences_shiroikuma_ui.xml`, `res/layout/preference_*shiroikuma*.xml` + `activity_shiroikuma_ui.xml` + `preference_widget_{color_swatch,regenerate}.xml`, `res/drawable/shiroikuma_{dialog_bg,pill_bg}.xml`, `assets/shiroikuma/{colors,fonts}/`; upstream hooks (one-liners): `TermuxActivity` (`onCreate`, `setSettingsButtonView`, `onContextItemSelected`, `setExtraKeysView`, `reloadActivityStyling`), `TermuxSessionsListViewController.getView`, `TermuxApplication.onCreate`, `SettingsActivity.RootPreferencesFragment`, `activity_termux.xml` (root class), `root_preferences.xml`, `AndroidManifest.xml` (the activity), `proguard-rules.pro` |
| Export / Import + backup engine | **done (Phase 4b)** — one ZIP (`shiroikuma-termux_<yyyy-MM-dd_HH-mm-ss>.zip`, `.part` then rename) of `settings` + tars of `home` and `usr` (白い熊's `baktermux` in the app); import streams the ZIP, restores settings and swaps the trees in from staging — see "UI page → Export / Import" below | `app/src/main/java/com/termux/shiroikuma/backup/{TermuxBackup,TarPack,TarUnpack,PrefsJson,ExportDir,ProcessReaper}.java` (Apache Commons Compress `1.27.1`, declared in `app/shiroikuma.gradle`; `-dontwarn org.tukaani.**` / `com.github.luben.**` / `org.brotli.**` in `app/proguard-rules.pro`), `ui/ExportImportPanel.java` |
| 保存復元 automation contract v2 | **done (Phase 4b)** — §1 exported receiver, §2 gate, §2a provider data door, §3 progress, §4 manifest; every run in one foreground `dataSync` service — see "UI page → Automation" below | `app/src/main/java/com/termux/shiroikuma/automation/{AutomationAuth,AutomationCallers,AutomationJobs,AutomationProgress,StateExportReceiver,AutomationProvider,AutomationDataService}.java`, `AndroidManifest.xml` (`<queries>`, `FOREGROUND_SERVICE_DATA_SYNC`, receiver / provider / service / 3 `<meta-data>`), `ui/AutomationTokenPreference.java` + `res/layout/preference_widget_regenerate.xml` |
| App icon | black-yellow traced `>_` prompt in the rounded square (yellow `#FFFF00` line-art on black) — **done (Phase 2)** | `design/shiroikuma-termux-icon.svg` (the source) → `tools/icon/emit_launcher.py` → `app/src/main/res/drawable/ic_foreground.xml` (adaptive foreground), `app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.png` (legacy), `fastlane/metadata/android/en-US/images/icon.png`; upstream's `art/` generator is superseded by `tools/icon/` |
| Version tail | `versionName = "<upstream>+<pin>+NNN"`, `versionCode = <upstream code>*10000+N` | `app/shiroikuma.gradle` (applied by the last line of `app/build.gradle`) |
| Signing | gitignored `keystore.properties` → `~/.android-keystores/shiroikuma-emacs-termux.jks` (alias `Emacs keystore`) | `app/shiroikuma.gradle` → `signingConfigs.release` |
| Fork links | `https://github.com/ShiroiKuma0/shiroikuma-termux` (+ `/issues`) — **done (Phase 3)** | `TermuxConstants.TERMUX_GITHUB_REPO_NAME = "shiroikuma-termux"`, `TERMUX_GITHUB_REPO_URL` set **literally** (the issues URL derives from it); `TERMUX_API_GITHUB_REPO_NAME` / `_URL` → `ShiroiKuma0/shiroikuma-termux-api` likewise. `TERMUX_GITHUB_ORGANIZATION_*` stay `termux` (the packages / plugin links hang off them) and `TERMUX_GITHUB_WIKI_REPO_URL` is re-anchored to `TERMUX_GITHUB_ORGANIZATION_URL + "/termux-app/wiki"` so the GitHub wiki and `RUN_COMMAND_API_HELP_URL` keep pointing at the manual; `TERMUX_WIKI_URL`, packages site, e-mail and reddit stay upstream's |
| De-branding | our name + our GitHub links everywhere user-visible — **done (Phase 3)** | About page and `TermuxUtils.getImportantLinksMarkdownString` follow the constants (no code change); the donate row is dropped by removing the `configureDonatePreference(context)` call in `app/src/main/java/com/termux/app/activities/SettingsActivity.java` (upstream's `root_preferences.xml` ships it `isPreferenceVisible="false"`, and upstream's signature check would have shown it for our key); `fastlane/metadata/android/en-US/{short,full}_description.txt`; `README.md` fork header above upstream's body. Untouched on purpose: `manifestPlaceholders.*_APP_NAME` in `app/build.gradle` (no manifest uses them), `msg_report_issue` / the "GitHub Issues for Termux apps" headings (upstream's community text) |
| Absorbed plugins | Boot / Widget / Float — **done (Phase 4c)**, one manifest block (`app/src/main/AndroidManifest.xml`, after the automation `<meta-data>`), the static shortcuts "Floating terminal" + "白い熊 Termux UI" in `res/xml/shortcuts.xml` (`ShiroikumaUiActivity` exported for it), the UI page's Widget + Floating terminal sections; Styling — **absorbed (Phase 4a)** into the UI page (its 114 colour schemes (+ the house `shiroikuma.properties`) + 4 OFL/Apache fonts bundled under `app/src/main/assets/shiroikuma/`, the "Style" context-menu item and `TERMUX_STYLING_*` constants now unused by the app) | see the table above |

### UI page (白い熊 Termux UI, Phase 4a + 4b)

`ShiroikumaUiActivity` (theme `Theme.ShiroikumaUi`, **translucent**: the `TermuxActivity` underneath is
paused but never stopped, so its `ACTION_RELOAD_STYLE` receiver — registered in `onStart`, unregistered
in `onStop` — stays alive) hosts `ShiroikumaUiFragment` over `res/xml/preferences_shiroikuma_ui.xml`.
Every change ends in `ShiroikumaStyle.changed(ctx)` → throttled (≤ 1 per 80 ms, trailing)
`TermuxActivity.updateTermuxActivityStyling(ctx, false)` → upstream's `reloadActivityStyling(false)`
(properties, extra keys `reload()`, `checkForFontAndColors()`) plus our `ShiroikumaChrome.apply(this)`
hook at its top — so everything previews live behind the page. The page's `onResume` fires one more
reload (an opaque SAF picker may have stopped the terminal in between).

Sections and prefs keys (file `shiroikuma_ui`; an absent key = house default; long-press a colour /
size row = default):

| Section | Rows → keys |
| --- | --- |
| Export / Import | first section (no hairline): 「Export / Import…」 (the panel) · 「Export directory」 (absolute path in yellow, red "not set"; a red second line "All-files access not granted — tap to grant" while `Environment.isExternalStorageManager()` is false on API 30+ — tap opens the grant page, otherwise the SAF tree picker; long-press = the picker regardless) · 「Last export」 (newest `shiroikuma-termux_*.zip`, queried on every resume on a background thread; red "none" / "no directory") · 「Automation export」 (switch, default ON) · 「Use authorization token?」 (switch, default OFF) · token row (`AutomationTokenPreference`, visible only while the token is required; tap copies, Regenerate pill → confirm). Prefs: `shiroikuma_eximport` (`export_dir` absolute path, `export_dir_uri`), `shiroikuma_automation` (`automation_enabled`, `automation_require_token`, `automation_token`) — both device-local, all writes `commit()`, never exported |
| Terminal | live sample row; Colours: Background · Foreground · Cursor (picker with alpha **disabled**, written as `key=#RRGGBB` lines to `~/.termux/colors.properties` by `TerminalStyleFiles`), Colour scheme… (`terminal_scheme`, display only; the 114 termux-styling schemes + house `shiroikuma.properties` from `assets/shiroikuma/colors/`); Font: Font (`terminal_font`, display only — the truth is `~/.termux/font.ttf`; bundled `assets/shiroikuma/fonts/` + `~/.termux/fonts/*.ttf\|otf` + "Add font…" SAF import), Font size (`TermuxAppSharedPreferences.setFontSize`, px) |
| Extra keys row | `extrakeys_bg` · `extrakeys_text` · `extrakeys_active_text` · `extrakeys_active_bg` · `extrakeys_text_size_sp` (0 = default) · `extrakeys_border_dp` · `extrakeys_border_color` · `extrakeys_corner_dp` — applied via `ExtraKeysView.setButtonColors` + an `OnHierarchyChangeListener` styling each `MaterialButton` as `reload()` adds it |
| Drawer / sessions | `drawer_bg` · `drawer_button_text` · `drawer_icon_tint` · `session_text` · `session_selected_bg` · `session_dead_text` · `session_text_size_sp` |
| Toolbar / status bar | `toolbar_bg` · `toolbar_text` · `toolbar_icon` · `statusbar_bg` · `navbar_bg` — Settings / Help / Report / our page via `ShiroikumaLifecycle`; TermuxActivity keeps upstream's translucent bars |
| Dialogs / menus | `dialog_bg` · `dialog_text` · `dialog_title` · `dialog_button` · `dialog_border_color` · `dialog_border_dp` · `dialog_corner_dp`; `menu_bg` · `menu_text` · `menu_border_color` · `menu_border_dp` · `menu_corner_dp` — our own dialogs (`ShiroikumaDialogs`) and the context menu (`ShiroikumaRootView` → `ShiroikumaContextMenu`); upstream's platform `AlertDialog`s get the static black/yellow `ThemeOverlay.Shiroikuma.Dialog.Platform` applied by `ShiroikumaLifecycle.onActivityCreated` |
| Widget (Phase 4c) | hint row; `widget_bg` · `widget_text` · `widget_text_size_sp` (10–24, default 16) — pushed to every placed widget's `RemoteViews` via `TermuxWidgetProvider.refreshAppWidgets(ctx, ids, true)`, debounced 150 ms; actions 「Refresh widgets」 (toast with the ids / "none"), 「Create dynamic shortcuts」 / 「Remove dynamic shortcuts」 (`ShortcutUtils`, API 25+; they hang off the app's launcher icon next to the 5 static ones) |
| Floating terminal (Phase 4c) | 「Open floating terminal」 (`startService(TermuxFloatService)`; the overlay window comes up over the page); `float_bg` · `float_border_color` · `float_border_dp` (0–4, default 1) · `float_corner_dp` (0–24, default 8) · `float_notification_text` (string; tap → `ShiroikumaDialogs.inputDialog`, empty / long-press = upstream's "Touch to hide/show window.") — each change → `TermuxFloatService.reloadStyle()` on a running window |
| Reset | confirm → clear `shiroikuma_ui`, house `colors.properties`, delete `font.ttf`, default font size, reload + `recreate()`; also refreshes the widgets and the floating window |

Bookkeeping keys: `recent_colors` (the picker's swatches, CSV), `defaults_written` (first-run marker of
`ShiroikumaDefaults`, which writes the house `colors.properties` once when none exists).

#### Export / Import (Phase 4b)

`ExportImportPanel` (port of raikidoban's: one bordered box, red/yellow directory box, last-backup
line, 全選択 + category checkboxes with `data.home` / `data.usr` indented under `data` and following
it, pills Cancel ‖ Import Export, a progress line while a run is in flight, Cancel then stops the
job). Success → info dialog whose OK closes the chain dialog → panel → page (`Host.onChainFinished()`
→ `ShiroikumaUiActivity.finish()`); failures leave the panel open; an import ends with 「Later」 /
「Restart now」 (`Intent.makeRestartActivityTask(TermuxActivity)` + `Runtime.exit(0)`). Before an
export of the data categories the panel prompts when the app is not battery-exempt; before a
`data.*` restore it confirms "Restoring packages closes all terminal sessions". **Every run — the
panel's too — executes in the foreground `AutomationDataService`** (partial wakelock, notification
channel `shiroikuma_backup`, progress in the notification text); the panel follows its own job
through `AutomationJobs.Listener`, so one engine has one runner.

Categories (`LIST_CATEGORIES` lines `id\tlabel\tparent\ton|off`, all `on`; `TermuxBackup.Cat`):

| id | label | parent |
| --- | --- | --- |
| `settings` | Settings (app preferences · termux.properties · colours · font) | |
| `data` | Data (home + packages) | |
| `data.home` | Home (/data/data/com.termux/files/home) | `data` |
| `data.usr` | Packages ($PREFIX = …/files/usr) | `data` |

`data` alone (no child named) means both parts; a named part means that part only.

ZIP layout (`TermuxBackup`, Apache Commons Compress; `manifest.json` FIRST):

```
manifest.json                      format "shiroikuma-termux", version 1, app, appVersion, createdTs,
                                   categories[], bytes{"data.home":N,"data.usr":N}  (the restore's free-space check)
settings.json                      {"<file>":{"<key>":{"t":"int|long|float|bool|string|set","v":…}}} for
                                   com.termux_preferences (minus current_session) and shiroikuma_ui
settings/termux.properties         ~/.termux/termux.properties
settings/config/termux.properties  ~/.config/termux/termux.properties
settings/colors.properties         ~/.termux/colors.properties
settings/font.ttf                  ~/.termux/font.ttf
settings/fonts/<name>              ~/.termux/fonts/*
data/home.tar                      files/home as home/…   — DEFLATED level 0 (streamable; `unzip -p x.zip data/home.tar | tar t` works)
data/usr.tar                       files/usr  as usr/…
```

Tars (`TarPack`): `TarArchiveOutputStream` with `LONGFILE_POSIX` + `BIGNUMBER_POSIX`, entries from
`Os.lstat` (mode & 07777, uid/gid, mtime), directories, regular files (a file that shrinks under us
is zero-padded, one that grows is cut, one that cannot be opened is skipped), symlinks
(`Os.readlink`), hard links via an inode map (`LF_LINK`), sockets / FIFOs / devices skipped and
counted (baktermux's `sed '/socket ignored/d'`), a pre-walk for honest totals. Zip: `Zip64Mode.Always`.
Why Java tars: a wiped phone has no `usr`, and restoring `usr` with a tar living inside `usr` is
circular. The data half needs API 26+ (`FileTime` inside Commons Compress; `desugar_jdk_libs 1.1.5`
does not cover `java.nio.file`) — `TermuxBackup.supported()` refuses below that with a clear error.

Restore (`TermuxBackup.importZip`, streaming `ZipArchiveInputStream`, no spool): `settings` merges
prefs with `commit()` and copies the files; **tighten-only rule** — `allow-external-apps=true` from
an archive is written as `false` unless the device already had it on (post-pass over the restored
home's two properties files too), and the summary says so. `data.*` → `TarUnpack` into
`files/home-staging` / `files/usr-staging` (path-traversal guarded; modes via `chmod`, dirs last;
mtimes; `symlink`; `link`), then — only after the LAST byte of the ZIP was read — `ProcessReaper`
(`TermuxService` `ACTION_STOP_SERVICE`, then SIGKILL every other pid of our uid from `/proc`,
sparing `Process.myPid()`; **note the shared UID: termux-api / termux-x11 / termux-gui / 白い熊 GNU
Emacs processes die too**, anything with the old prefix mapped must) and the swap `live → live.old`,
`staging → live`, delete `.old`. Free-space check up front from `manifest.bytes` (+5 % + 64 MB):
`ERROR:not enough space (need X, have Y)`. A truncated archive never touches live data; cancel
deletes staging. `TermuxInstaller.setupBootstrapIfNeeded` skips the bootstrap when `$PREFIX` exists
and is non-empty (`TermuxFileUtils.isTermuxPrefixDirectoryEmpty`), so a restore before first launch
boots into the restored prefix (`describe`: `requires_launch_first:false`).

Destination: an ABSOLUTE path with `java.io.File` (termux declares `MANAGE_EXTERNAL_STORAGE`); the SAF
tree picker only chooses the folder, `ExportDir.resolve` turns `primary:…` into
`/storage/emulated/0/…` and a removable `XXXX-XXXX:…` into `/storage/XXXX-XXXX/…`. When the grant is
missing (`ExportDir.hasStorageAccess`: `isExternalStorageManager()` on API 30+, the legacy pair
below) the automation reply is exactly `ERROR:no-storage-access` — no SAF fallback.

#### Automation (contract v2, `~/git/shiroikuma-jiyusagyoban/sister-app-contract-backup-automation-hand-off.md`)

- §1 `StateExportReceiver` (exported, no permission): `com.termux.action.EXPORT_STATE` /
  `LIST_CATEGORIES` / `CANCEL_EXPORT`. Extras `token` (ignored unless required), `path` (absolute dir,
  wins over the configured one; `ERROR:no-storage-access` without the grant; `ERROR:no-directory`
  when neither), `items`, `progress_action`, `reply_action` / `reply_package` / `reply_id`. The
  receiver only gates, validates and starts the service (guarded `startForegroundService`; a
  `ForegroundServiceStartNotAllowedException` matched by NAME → `ERROR:no-foreground-start` only when
  not battery-exempt, else `ERROR:cannot start export service: <Class>`). Reply = a fresh broadcast
  with `FLAG_INCLUDE_STOPPED_PACKAGES`, `reply_id` echoed, `result` = `OK:<abs path>|<bytes>|<human>|<n>
  categories` / `OK:` + category lines / `ERROR:<reason>` (`automation disabled`, `bad token`,
  `export already running`, `cancelled`, …); exactly one per request. `CANCEL_EXPORT` is silent
  (`reply_id` names the run, absent = every run), the export unwinds at the next entry, deletes its
  `.part`, and answers its original request `ERROR:cancelled`.
- §2 `AutomationAuth` (`shiroikuma_automation`: `automation_enabled` default true,
  `automation_require_token` default false, `automation_token` 24 SecureRandom bytes hex, lazily;
  `refuse()` is the one gate; `MessageDigest.isEqual`; `commit()` everywhere).
- §2a `AutomationProvider`, authority `com.termux.automation`, exported: `describe` (header from the
  manifest + the enum only — `requires_launch_first:false`, `requires_permissions:[]`,
  `contains:["Settings","Home (~)","Packages ($PREFIX)"]`), `export` / `import` (extra `fd`
  `dup()`ed, the callee mints the `job_id`, returns `OK:<job_id>`; the terminal reply and every
  progress line carry it as both `job_id` and `reply_id`), `cancel`. Caller check =
  `AutomationCallers` verbatim (exact name → uid → pinned cert; `shiroikuma.oyokanri`,
  `shiroikuma.jiyusagyoban`). `import` exists only here.
- §3 `AutomationProgress`: ≥ 500 ms apart, final line always, 20 s heartbeat; `item` = category id,
  `text` "Home 1234/8942 · 512 MB / 4.2 GB", `current`/`total` files, `unit` "files", `bytes`/`bytes_total`.
- `AutomationDataService` (`foregroundServiceType="dataSync"`, unexported): read extras → guarded
  `startForeground` → drain the descriptor handover in the same try/finally → early returns (a stale
  job id stops silently); `TermuxBackup.RUNNING` claimed only after the promotion; one terminal
  reply; the panel's listener is told before the job is forgotten. Modes: file export (§1 + panel),
  descriptor export (§2a), descriptor import (§2a + panel).
- Manifest: `<queries>` for both callers, `FOREGROUND_SERVICE_DATA_SYNC`, the three integer
  `<meta-data>` (`shiroikuma.automation.contract`=2, `.format`=1, `.min_format`=1).

### Versioning & APK naming

- The upstream base lives in `app/build.gradle` `defaultConfig` as upstream's own `versionCode 118`
  / `versionName "0.118.0"` literals. **We never edit them.** `app/shiroikuma.gradle` — applied by
  the **last** line of `app/build.gradle` — reads them back and overwrites the two fields with our
  derived pair, so a rebase brings a new base in automatically. It must stay the last line: upstream
  runs `validateVersionName(versionName)` inline in `defaultConfig` with a strict SemVer regex that
  allows one `+`, and our form carries two.
- The pin is `git merge-base HEAD master` (the upstream commit our patches sit on — not our HEAD, not
  `master`'s tip) shortened to 8 chars, plus that commit's own committer date **and time, in UTC**
  (`%ct` epoch → `yyyy-MM-dd.HH-mm`). It moves only on a sync.
- `BUILD_NUMBER` (in `gradle.properties`) is our per-build `N`:
  `versionName = "<upstream>+<YYYY-MM-DD>.<HH-MM>.g<sha8>+<NNN>"` (e.g.
  `0.118.0+2026-09-11.22-27.g45844885+001`), `versionCode = <upstream code>*10000 + N` (plain
  integer, e.g. `1180001`). Zero-padded to 3 digits **in the name only**. The `buildFork` task bumps
  it after every successful build.
- **`BUILD_NUMBER` runs MONOTONICALLY. Reset it to `1` ONLY when upstream's `versionCode` itself
  moves** — never merely because a sync moved the `.g<sha>` pin. An installer compares `versionCode`
  and nothing else; upstream leaves `118` standing for months on `master`, so resetting `N` on a
  sync would send `versionCode` *backwards* and make every sync a downgrade by construction.
- `buildFork` enforces this: it records `LAST_BUILT_VERSION_CODE` in `gradle.properties` and
  **refuses to build** a `versionCode` that does not exceed it (checked from the task graph, before
  the long native build). Raise `BUILD_NUMBER` past the last built tail; never lower it.
- APK: `shiroikuma-termux_<versionName>_universal.apk`, copied to `~/tmp/`. Release builds are
  **universal** (all four ABIs in one APK) — upstream splits per ABI only when
  `TERMUX_SPLIT_APKS_FOR_RELEASE_BUILDS=1`, which we never set. The versionName contains no `_`
  (Debian's rule, inherited across the family) and no `~` (`git check-ref-format` rejects it, so
  `/publish-version` could not tag).

### Build commands

```bash
# Our build: signed release → ~/tmp + bump BUILD_NUMBER (use this)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew buildFork --console=plain < /dev/null
# Release APK only (no copy / no bump) — the toolchain test
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew :app:assembleRelease --console=plain < /dev/null
# What the next build will be called
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:versionName -q < /dev/null
# Publish OUR termux-shared (+ terminal-view, terminal-emulator) to mavenLocal for the sister forks
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew publishReleasePublicationToMavenLocal --console=plain < /dev/null
```

**`termux-shared` for the sister forks.** `shiroikuma-termux-api` builds against *this* repo's
`termux-shared` (白い熊 names and links in `TermuxConstants`, the absorbed-plugin preference lookups)
instead of upstream's JitPack artifact: the publish task above lands
`com.termux:termux-shared:<SHIROIKUMA_TERMUX_SHARED_VERSION>` (+ `terminal-view`, `terminal-emulator`
at the same version, which its POM needs) in `~/.m2`. The version lives in `gradle.properties`
(`0.118.0-sk1`; upstream base in front, `-skN` bumped whenever the library changes in a way a sister
fork must pick up) and is applied by the one-line `apply from: 'shiroikuma.gradle'` at the end of
each of the three library modules' `build.gradle` (the script re-labels upstream's `release`
publication in `afterEvaluate`; upstream's literal `0.118.0` stays untouched). After a sync that
touches `termux-shared`, re-publish and bump the suffix in both repos' `gradle.properties`.

The first build runs upstream's `downloadBootstraps` (four `bootstrap-<arch>.zip` from
`termux/termux-packages` releases into `app/src/main/cpp/`, gitignored via `*.zip`, SHA-256
checked) and fetches `com.termux:termux-am-library` from JitPack — network once, then cached. A cold
build takes 10+ minutes (ndk-build + R8 over four ABIs); run it in the background and poll.
`TERMUX_PACKAGE_VARIANT` stays at upstream's default `apt-android-7`.

### Toolchain

- JDK **21** at `/usr/lib/jvm/java-21-openjdk-amd64` (the host default `java` is JDK 11; Gradle 9.x
  aborts on it — always set `JAVA_HOME`).
- Android SDK at `~/android-sdk` (`ANDROID_HOME` or the gitignored `local.properties` with
  `sdk.dir=/home/shiroikuma/android-sdk`); `compileSdk 36`, `targetSdk 28` (upstream's choice —
  Android 10+ W^X rules; **never raise it**), `minSdk 21`. NDK **`29.0.14206865`** exactly (from
  `gradle.properties` `ndkVersion`). Gradle wrapper 9.2.1, AGP 8.13.2, Java 8 source level with
  core-library desugaring.

## Architecture (upstream termux-app)

Multi-module Gradle build: `:app` (activity, service, installer, settings), `:termux-shared`
(constants, settings, shell/file/packages utilities — shared with every plugin), `:terminal-view`
(the `TerminalView` widget + extra keys), `:terminal-emulator` (the emulator proper).

| Area | Where |
| --- | --- |
| Application class, crash handler | `app/src/main/java/com/termux/app/TermuxApplication.java` |
| Main activity (drawer, sessions, extra keys) | `app/src/main/java/com/termux/app/TermuxActivity.java` |
| Foreground service, session/task management | `app/src/main/java/com/termux/app/TermuxService.java` |
| Bootstrap extraction into `$PREFIX` | `app/src/main/java/com/termux/app/TermuxInstaller.java` + `app/src/main/cpp/` (ndk-build, embeds the zips) |
| Settings / About / Help activities | `app/src/main/java/com/termux/app/activities/`, `app/src/main/res/xml/` |
| Style reload (`ACTION_RELOAD_STYLE`), styling properties | `TermuxActivity` receiver + `termux-shared/.../termux/settings/properties/` |
| 白い熊 Termux UI page (fork) | `app/src/main/java/com/termux/shiroikuma/`, `app/src/main/res/xml/preferences_shiroikuma_ui.xml`, `app/src/main/assets/shiroikuma/` |
| Absorbed plugins (fork, Phase 4c) | `app/src/main/java/com/termux/boot/` (Termux:Boot), `app/src/main/java/com/termux/widget/` (Termux:Widget), `app/src/main/java/com/termux/window/` (Termux:Float) — see the absorbed-plugins table at the top |
| Backup engine + automation contract (fork) | `app/src/main/java/com/termux/shiroikuma/{backup,automation}/` — see "UI page → Export / Import" and "→ Automation" above |
| Constants (names, paths, URLs, package ids) | `termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java` |
| String entities (`TERMUX_APP_NAME` &c.) | `app/src/main/res/values/strings.xml`, `termux-shared/src/main/res/values/strings.xml` (both carry the `<!DOCTYPE>` entity block) |
| Launcher icon sources | `art/ic_launcher.svg`, `art/generate-launcher-images.sh`, `app/src/main/res/drawable/ic_foreground.xml` |
| Store metadata | `fastlane/metadata/android/en-US/` |

## Hard rules

- **Never change `applicationId` / `namespace` / `sharedUserId` from `com.termux`** — every binary in
  the bootstrap and on packages.termux.dev hardcodes `/data/data/com.termux/files/usr`; `termux-api`,
  the `termux-x11` loader and every termux-gui client hardcode `com.termux.*`. Renaming would mean a
  self-hosted package repository forever.
- **Every APK of the `com.termux` shared-UID family (this app, termux-api, termux-x11, termux-gui,
  白い熊 GNU Emacs) must be signed with the same keystore** —
  `~/.android-keystores/shiroikuma-emacs-termux.jks`, alias `Emacs keystore`. Android refuses to
  install a `sharedUserId` app whose signature differs from the others already installed. Changing
  the key later means uninstall + restore for everything.
- **Never `targetSdk` above 28** — upstream's deliberate choice; Android 10+ enforces W^X on
  higher targets and the whole package ecosystem breaks.
- **Never commit/push unprompted.** Build, deliver, and stop; 白い熊 tests. Commit + push only on
  their explicit **"Push"** — which means commit, then `git push --force-with-lease origin custom`
  (after a rebase `custom` is rewritten; `master` fast-forwards with a plain push).
- `keystore.properties`, `local.properties` and `*.jks` are gitignored — never commit them.
  (Upstream's `app/testkey_untrusted.jks` is theirs and already tracked; leave it.)
- **Always run `adb`, `scp` and `git status`/`git diff` with `dangerouslyDisableSandbox: true`**
  (the sandbox blocks adb's server socket and invents phantom untracked dotfiles at the repo root).
- **After ANY functional change, build and deliver automatically** — the global `/after-build`
  standing authorization; never wait for "build it". Every build bumps `BUILD_NUMBER`; never
  overwrite or delete an older APK, in `~/tmp/` or on the phone.
- On new upstream commits, run the **`upstream-new-version`** skill — it presents the proceed-gated
  upstream-changes table (and a second one for the absorbed plugins' upstreams) **before** any
  rebasing, then fast-forwards `master`, rebases `custom`, keeps `BUILD_NUMBER` counting, and builds
  the next `+NNN`.
- **Rebase grep guard.** After every rebase, before building, grep for upstream branding that
  re-entered user-visible strings and re-de-brand:
  `p='= "Termux"\|= "termux-app"\|ENTITY TERMUX_APP_NAME "Termux"\|termux/termux-app'; grep -rn "$p" termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java app/src/main/res/values/strings.xml termux-shared/src/main/res/values/strings.xml fastlane/ | sed 's#// Default:.*##' | grep "$p"`
  (code namespaces, internal ids, log tags and upstream's copyright / attribution stay as upstream
  wrote them — only the user-visible name, links and icon are ours).
- **Absorbed-plugin rebase guard (Phase 4c).** termux-app's `master` never touches
  `app/src/main/java/com/termux/{boot,widget,window}/` (those packages exist only here), so a rebase
  cannot conflict inside them — but it CAN touch what they lean on. After every rebase, before
  building: (1) `git diff master --stat -- termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/Termux{Widget,Float}AppSharedPreferences.java`
  must still show our `absorbedPluginContext` fallback in both `build()` overloads; (2) the manifest
  block "shiroikuma-termux (Phase 4c)" and the two shortcuts in `res/xml/shortcuts.xml` are intact;
  (3) `grep -rn "R.drawable.ic_launcher\|R.mipmap.ic_service_notification\|activity_main\|activity_permission" app/src/main/java/com/termux/{boot,widget,window}` is empty;
  (4) if upstream renamed anything in `TermuxConstants.TERMUX_{BOOT,WIDGET,FLOAT}_APP` /
  `TERMUX_SERVICE` / `TermuxSession.execute`, the three packages are the callers to fix. Porting a
  plugin's own upstream change: `git fetch upstream-<boot|widget|float>`, `git diff <sha in the
  table>..upstream-<x>/master -- app/src/main/java app/src/main/res app/src/main/AndroidManifest.xml`,
  apply by hand (the file headers list every local deviation), update the sha in the table.

### CHANGELOG.md is unified — never fork it, never overwrite upstream's

Upstream ships **no** `CHANGELOG.md` (its history is GitHub release notes), so ours is the root
`CHANGELOG.md`, our sections newest first, in the same shape the sister forks use:

- Fork entries are `## 白い熊 Termux <tag> — <YYYY-MM-DD>`, newest first, each naming the upstream
  base it is built on (`0.118.0` + the pin), and are **per-release deltas** — only the first release
  lists everything.
- Every release section carries **(a)** our changes and **(b)** an **"Upstream since `<previous base
  sha>`"** subsection distilled from the proceed-gate table of the sync(s) that moved the base —
  that is the "merged changelog" published with each release. The absorbed plugins' upstream changes
  go in the same subsection, one line each, named by plugin.
- Should upstream ever add a `CHANGELOG.md`, ours moves **above** theirs in the same file, byte for
  byte as they wrote it below; a rebase conflict there is resolved "keep both blocks, ours on top".
- The same text goes in the GitHub release notes. The **global `/publish-version` skill** does both.

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer, nor a "🤖 Generated with Claude Code" /
Anthropic-attribution line, to commit messages or PR bodies in this repo. End the message at the last
line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
