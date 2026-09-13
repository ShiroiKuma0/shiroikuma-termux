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

Four more **fetch-only** remotes (push URL `DISABLED`, `tagOpt --no-tags`) carry the plugins that are
being **absorbed into this app** (Phase 4 of the plan) — their sources are copied into `app/`, their
own Java packages kept, so porting an upstream change is a manual diff, never a rebase:

| Plugin | Upstream remote | Where its sources live here |
| --- | --- | --- |
| Termux:Boot | `upstream-boot` → `https://github.com/termux/termux-boot.git` | `app/src/main/java/com/termux/boot/` (`BootReceiver`, `BootJobService`, `RECEIVE_BOOT_COMPLETED`) — pending |
| Termux:Widget | `upstream-widget` → `https://github.com/termux/termux-widget.git` | `app/src/main/java/com/termux/widget/` (providers, shortcuts, Device Controls) — pending |
| Termux:Float | `upstream-float` → `https://github.com/termux/termux-float.git` | `app/src/main/java/com/termux/window/` (`TermuxFloatService`, the window, `SYSTEM_ALERT_WINDOW`) — pending |
| Termux:Styling | `upstream-styling` → `https://github.com/termux/termux-styling.git` | no package of its own — absorbed into the **白い熊 Termux UI** page (colours / fonts / schemes; termux-app already has the `ACTION_RELOAD_STYLE` plumbing) — pending |

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
| App label | `白い熊 Termux` — **pending (Phase 3)** | `TermuxConstants.TERMUX_APP_NAME` in `termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java` (feeds notification channels / titles / crash reports) + the `<!ENTITY TERMUX_APP_NAME …>` in **both** `app/src/main/res/values/strings.xml` and `termux-shared/src/main/res/values/strings.xml` |
| UI page | `白い熊 Termux UI` — **pending (Phase 4)**; opened by a long-press on the drawer gear and from the "Style" context-menu item | `app/src/main/java/com/termux/shiroikuma/` (new), `action_style_terminal` in `app/src/main/res/values/strings.xml` |
| App icon | black-yellow traced `>_` prompt in the rounded square (yellow `#FFFF00` line-art on black) — **pending (Phase 2)** | `app/src/main/res/drawable/ic_foreground.xml`, `art/ic_launcher.svg` → `app/src/main/res/mipmap-*/` via `art/generate-launcher-images.sh`, `fastlane/metadata/android/en-US/images/icon.png` |
| Version tail | `versionName = "<upstream>+<pin>+NNN"`, `versionCode = <upstream code>*10000+N` | `app/shiroikuma.gradle` (applied by the last line of `app/build.gradle`) |
| Signing | gitignored `keystore.properties` → `~/.android-keystores/shiroikuma-emacs-termux.jks` (alias `Emacs keystore`) | `app/shiroikuma.gradle` → `signingConfigs.release` |
| Fork links | `https://github.com/ShiroiKuma0/shiroikuma-termux` (+ `/issues`) — **pending (Phase 3)** | `TermuxConstants.TERMUX_GITHUB_REPO_NAME` / `TERMUX_GITHUB_REPO_URL` / `TERMUX_GITHUB_ISSUES_REPO_URL`; `TERMUX_WIKI_URL`, packages site and e-mail stay upstream's |
| De-branding | our name + our GitHub links everywhere user-visible — **pending (Phase 3)** | About page (follows the constants), `TermuxUtils.getImportantLinksMarkdownString`, fastlane descriptions, README fork header |
| Absorbed plugins | Boot / Widget / Float / Styling — **pending (Phase 4)** | see the table above |

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
```

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
