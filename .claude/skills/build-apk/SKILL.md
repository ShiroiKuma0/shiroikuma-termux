---
name: build-apk
description: Build the signed release APK of shiroikuma-termux (白い熊 Termux — our fork of termux/termux-app, app id com.termux) with the buildFork Gradle task, then deliver it automatically via the global /after-build skill (adb push if the phone is reachable, else scp to skhw — no prompt). Always build first without asking permission to build. Use whenever 白い熊 asks to build the app, build the APK, make a release build, or build and send to the phone, and after any functional code change.
---

# Build the 白い熊 Termux release APK and deliver it

> **ALWAYS build, then ALWAYS deliver — no asking (白い熊's standing authorization, 2026-07-09).**
> After ANY functional change, build **immediately** and deliver. Do not stop at a compile-check, do
> not offer to build, do not ask how to transfer it. Build-and-deliver does **not** commit or push —
> a commit/push still waits for 白い熊's explicit "Push". (Skip the build only for non-functional
> edits — docs, comments.)

## Build environment (this machine)

The default `java` is **JDK 11**, which cannot run Gradle 9.x. Always export JDK 21, and the SDK
path for background shells (they do not inherit `ANDROID_HOME`):

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/shiroikuma/android-sdk
```

The gitignored `local.properties` (`sdk.dir=/home/shiroikuma/android-sdk`) covers the SDK path too —
recreate it if a build fails with **`SDK location not found`**. The NDK must be exactly
`29.0.14206865` (upstream's `ndkVersion` in `gradle.properties`); it is installed.

## Steps

1. **Note the output filename / version.**
   - `grep -nE 'versionCode |versionName "' app/build.gradle | head -2` — upstream's base
     (`118` / `0.118.0`); these track upstream and are **never hand-edited**.
   - `grep -E '^BUILD_NUMBER|^LAST_BUILT_VERSION_CODE' gradle.properties` — the `N` used for THIS
     build (the task bumps it afterwards) and the floor it must exceed.
   - Or simply ask the build:
     `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:versionName -q < /dev/null`
     prints the exact versionName, e.g. `0.118.0+2026-09-11.22-27.g45844885+001`.
   - APK will be `shiroikuma-termux_<versionName>_universal.apk` (`N` zero-padded to three digits in
     the name), e.g. `shiroikuma-termux_0.118.0+2026-09-11.22-27.g45844885+001_universal.apk`.
   - versionCode for this build = `118 * 10000 + N` (plain, unpadded), e.g. `1180001`.

2. **Build** (signed universal release) — from the repo root:
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew buildFork --console=plain < /dev/null
   ```
   - `buildFork` (in `app/shiroikuma.gradle`) runs `assembleRelease` (R8 minify, signed from
     `keystore.properties`), copies the universal APK from `app/build/outputs/apk/release/` to
     `~/tmp/<apk name>`, increments `BUILD_NUMBER` and records `LAST_BUILT_VERSION_CODE` in
     `gradle.properties`.
   - It prints `>>> <path>` and `>>> versionCode <n>` in cyan — use those to confirm the exact
     filename/code; confirm `BUILD SUCCESSFUL`.
   - **It refuses to run** (from the task graph, before the native build) when `keystore.properties`
     is missing — an unsigned APK cannot install over the signed family — or when the versionCode
     would not exceed `LAST_BUILT_VERSION_CODE`. Fix the cause; never work around either guard.
   - A cold build (fresh checkout / after `./gradlew clean`) downloads the four bootstrap zips
     (`app/src/main/cpp/bootstrap-*.zip`, ~100 MB, gitignored) and `termux-am-library` from JitPack,
     then runs ndk-build for four ABIs plus R8 — **10+ minutes**. Run it with `run_in_background`
     and poll; never abandon a running build. Warm builds are a minute or two.
   - **Fast iteration:** `./gradlew :app:assembleDebug` gives a debug-signed **split** set under
     `app/build/outputs/apk/debug/` (upstream's `testkey_untrusted.jks`). It installs as the same
     `com.termux` id but with a **different signature**, so it will **not** install over the signed
     family on the phone — it is for compile checks and the emulator only. The shippable build is
     always `buildFork`.

3. **Deliver via the global `/after-build` skill** — no exceptions, no asking. It runs `/adb-check`
   UNSANDBOXED, `adb push`es **this repo's** newest `~/tmp/shiroikuma-termux_*.apk` to `/sdcard/tmp/`
   if the phone is reachable, otherwise `scp`s it to `skhw:~/tmp/`, then announces what landed.
   `~/tmp/` is shared with parallel chats building sister apps — always pick the
   `shiroikuma-termux_*` APK, never merely the newest file there (`shiroikuma-termux-api_*`,
   `shiroikuma-termux-x11_*` and `shiroikuma-termux-gui_*` share the prefix — the glob must be
   `shiroikuma-termux_*`, underscore included).

4. **Never delete or prune older APKs** — not in `~/tmp/`, not in `/sdcard/tmp/`. Every build carries
   a unique `+NNN`; older builds stay where they are so 白い熊 can roll back.

5. **On-phone notes for 白い熊** (say them in the handover when relevant): the fork installs **in
   place** of upstream Termux (same `com.termux` id, higher `versionCode`) only if the installed one
   already carries our key — a stock Play/F-Droid/GitHub Termux must be uninstalled first (its
   `$PREFIX` and `$HOME` go with it; back them up from inside Termux beforehand). Never
   `adb install` — 白い熊 installs from `/sdcard/tmp/` by hand.

## Signing

Release signing is non-interactive: `app/shiroikuma.gradle` reads `keystore.properties` (gitignored,
at the repo root) which points at `~/.android-keystores/shiroikuma-emacs-termux.jks`, alias
`Emacs keystore` (**contains a space — quote it** on any command line), PKCS12, store password =
key password (recorded in `~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org`, backed up to that
directory's `android-keystores/`). It is upstream GNU Emacs's **public** `java/emacs.keystore`
(RSA-2048 / SHA1withRSA, created 2022-12-25, valid to 2296), chosen so that 白い熊 GNU Emacs —
built with `--with-shared-user-id=com.termux` — and this app share one UID. Certificate SHA-256:
`50:B4:7E:8F:09:B8:78:1F:CC:C9:98:DF:3F:C5:C0:2D:E0:DD:96:70:A3:D3:7E:6C:AC:BA:9F:4E:76:31:96:04`.

**The whole `com.termux` shared-UID family — this app, termux-api, termux-x11, termux-gui, Emacs —
must be signed with this one key.** Android refuses a `sharedUserId` app whose signature differs
from the ones already installed. Never point `keystore.properties` at another key.

Verify a build when in doubt:
```bash
apksigner verify --print-certs ~/tmp/shiroikuma-termux_<ver>_universal.apk | grep SHA-256
aapt dump badging ~/tmp/shiroikuma-termux_<ver>_universal.apk | head -3
```

If `keystore.properties` is missing, `buildFork` stops before building (a bare `assembleRelease`
still produces an **unsigned** APK, for toolchain tests only) — restore the file rather than working
around it.

## Versioning (how the numbers are formed)

- Upstream's own `versionCode 118` / `versionName "0.118.0"` in `app/build.gradle` `defaultConfig`
  are the base; a rebase brings the new values in automatically. **Never hand-edit them.**
- `app/shiroikuma.gradle` — applied by the **last** line of `app/build.gradle`, after upstream's
  inline SemVer `validateVersionName` has already accepted upstream's literal — reads them and
  overwrites `versionName`/`versionCode` with ours.
- `versionName = "<upstream>+<base date>.<HH-MM>.g<sha8>+<NNN>"` (global `git-versioning` skill):
  the pin is `git merge-base HEAD master` (the upstream commit our patches sit on) with its committer
  time in UTC; it moves only on a sync. `versionCode = <upstream code> * 10000 + N`.
- `BUILD_NUMBER` in `gradle.properties` is **our** increment, bumped on every `buildFork`. It runs
  **monotonically** and is reset to `1` **only** when upstream's own `versionCode` moves — never on a
  sync that merely repinned the sha (that would send `versionCode` backwards; the
  `LAST_BUILT_VERSION_CODE` floor makes such a build fail instead of installing as a downgrade).

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of
the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
