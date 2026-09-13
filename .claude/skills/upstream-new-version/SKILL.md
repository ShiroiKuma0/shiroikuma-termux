---
name: upstream-new-version
description: Sync the shiroikuma-termux fork onto new upstream commits of termux/termux-app master — fetch upstream (and the four absorbed-plugin remotes), fast-forward the master mirror, rebase custom, keep BUILD_NUMBER counting, build the next +NNN. Use when 白い熊 says a new upstream version is out, asks to check/update/sync to upstream, or to rebase custom onto the latest termux-app. ALWAYS present the proceed-gated upstream-changes tables (termux-app, then the absorbed plugins) BEFORE rebasing.
---

# Sync shiroikuma-termux onto new upstream termux-app commits

This fork tracks [termux/termux-app](https://github.com/termux/termux-app) — the Android terminal
emulator and Linux environment. `master` mirrors **`upstream/master`** (fast-forward only); `custom`
carries our patches and is rebased onto it.

**We follow the branch tip, not release tags** (`Upstream tracking: git`, per the plan of
2026-09-13). termux-app's `master` runs months ahead of its last tagged release and upstream's
`versionName "0.118.0"` literal stands still through all of it — so a sync happens whenever
**`upstream/master` has moved**, and the version pin (`+<date>.<HH-MM>.g<sha8>`, from
`git merge-base HEAD master`) is what records which upstream commit a build sits on.

Four **fetch-only** plugin remotes ride along — `upstream-boot`, `upstream-widget`, `upstream-float`,
`upstream-styling` (termux-boot / -widget / -float / -styling). Their sources are (Phase 4) copied into
`app/src/main/java/com/termux/{boot,widget,window}` and the styling page, so their changes are never
rebased in — they are **ported by hand**. This skill fetches them and reports what moved.

> **Never `git push` or `git commit` unprompted.** After the rebase + build you stop and let 白い熊
> test; you push only on their explicit **"Push"** (`custom` needs `--force-with-lease` after a
> rebase; `master` fast-forwards with a plain push).

## Branch / remote model

| Branch | Role | Update mode |
| --- | --- | --- |
| `master` | Mirrors `upstream/master`. No fork work here. | `git merge --ff-only upstream/master` |
| `custom` | Our patches; the working/dev branch and the GitHub default branch. | rebased onto `master` each sync |

`origin` = `git@github.com:ShiroiKuma0/shiroikuma-termux.git` (push). `upstream` =
`https://github.com/termux/termux-app.git` (fetch only; push URL `DISABLED`). `upstream-boot` /
`upstream-widget` / `upstream-float` / `upstream-styling` = `https://github.com/termux/termux-<x>.git`
(fetch only, `--no-tags`, push URL `DISABLED`).

## Steps

1. **Check for newer upstream commits** — the app first, then the plugins:
   ```bash
   cd ~/git/shiroikuma-termux
   git fetch upstream
   git fetch upstream-boot; git fetch upstream-widget; git fetch upstream-float; git fetch upstream-styling

   old=$(git rev-parse master)                      # capture BEFORE any fast-forward
   if git merge-base --is-ancestor upstream/master master; then
     echo ">>> No new upstream commits — master already contains upstream/master."
   else
     echo ">>> upstream/master is ahead by $(git rev-list --count master..upstream/master) commit(s)"
     git show master:app/build.gradle          | grep -E 'versionCode |versionName "' | head -2
     git show upstream/master:app/build.gradle | grep -E 'versionCode |versionName "' | head -2
   fi
   # The absorbed plugins: compare each against the commit we last ported from (recorded below).
   for p in boot widget float styling; do
     echo "== termux-$p: $(git log -1 --format='%h %cs %s' upstream-$p/master)"
   done
   ```
   "New version" = `upstream/master` is **not** an ancestor of our `master`. Report old → new
   `versionName` / `versionCode` (they usually do **not** change — that is the point of the pin) and
   the commit count. If nothing is new on the app **and** nothing on the plugins, stop and report
   "already current".

   The plugin ports are tracked by the sha each was last ported from, kept in the table at the end
   of this file (**"Absorbed-plugin port state"**) — update it when a port lands.

2. **⛔ PROCEED GATE — present the upstream changes as TWO tables, then STOP.** 白い熊's standing
   requirement: **before** anything is rebased, show what the new upstream commits actually bring.

   Gather the material for the app from all of these — they complement each other:
   ```bash
   git log --oneline --no-merges "$old"..upstream/master        # what really landed
   git log --merges --format='%s' "$old"..upstream/master        # which PRs were merged
   git log --stat --format='%n### %h  %s%n%b' "$old"..upstream/master   # bodies + files, to judge relevance
   git diff --stat "$old"..upstream/master                       # where the weight is
   gh release list -R termux/termux-app --limit 3                # did a tag land in the span?
   gh release view <tag> -R termux/termux-app                    # …if so, its notes
   ```
   Dependabot / GitHub-Actions bumps get **one** row, never one each; translation-only commits
   likewise.

   **Table 1 — termux-app** — a **descriptive markdown table**, one row per feature/change, in
   plain language, not raw commit subjects:

   | Area | Change | What it means for us |
   | --- | --- | --- |
   | Terminal emulator | … | … |
   | App / service / installer | … | … |
   | termux-shared (constants, settings) | … | … |
   | Build / deps / NDK / bootstrap | … | … |

   Cover features, fixes, bootstrap version bumps (`downloadBootstraps` checksums — a bump means
   the first build re-downloads ~120 MB), NDK/AGP/Gradle moves (check the machine has the exact
   `ndkVersion`), and anything touching files our patches own — **flag those rows**, they are the
   likely conflict sites: `app/build.gradle` (our `apply from` last line — and watch for upstream
   moving `validateVersionName` or adding its own signing block), `gradle.properties`, `.gitignore`,
   `app/src/main/AndroidManifest.xml`, `TermuxConstants.java`, both `strings.xml` entity blocks,
   `TermuxActivity.java` / `TermuxService.java` / `TermuxApplication.java` (our UI-page hooks),
   `app/src/main/res/drawable/ic_foreground.xml` + mipmaps, `fastlane/`, the root docs.

   **Table 2 — absorbed plugins** — a **second, small table**, one row per plugin that moved since
   its last-ported sha (skip plugins with nothing new):

   | Plugin | Commits since port | Change | Port needed? |
   | --- | --- | --- | --- |
   | Termux:Boot | `<sha>..upstream-boot/master` (n) | … | yes — `app/src/main/java/com/termux/boot/…` / no (CI / docs only) |
   | Termux:Widget | … | … | … |
   | Termux:Float | … | … | … |
   | Termux:Styling | … | … | (page feature, colour schemes) |

   ```bash
   git log --oneline --no-merges <last-ported-sha>..upstream-<p>/master
   git diff --stat <last-ported-sha>..upstream-<p>/master -- app/src/main/java
   ```
   Porting is **manual** (copy the relevant hunks into our packages; the plugins' `termux-shared`
   is ours already). Rows here never block the app rebase — they are a to-do list, done in the same
   session only if 白い熊 says so.

   Also state the stack size (`git rev-list --count master..custom`) and the plan.

   **Then stop and wait for 白い熊's explicit go-ahead.** Do not move `master`, do not rebase, do not
   build until they say proceed. If they decline, nothing has been touched.

3. **Fast-forward `master`** (mirror; no fork work lives here) and take a safety branch:
   ```bash
   git status --short                              # tree must be clean (unsandboxed — see CLAUDE.md)
   git branch custom-pre-$(date +%Y-%m-%d) custom  # rollback point; delete it once "Push" has landed
   git checkout master
   git merge --ff-only upstream/master
   ```
   (`master` is pushed in step 9, not here.)

4. **Rebase `custom`:**
   ```bash
   git checkout custom
   git rebase master
   ```
   Resolve conflicts so **all** our customizations survive (table below). Reconcile, don't drop: if
   upstream restructured a file we patch, port our change to the new structure rather than forcing
   the old diff. Keep **upstream's** `versionCode` / `versionName` literals — `app/shiroikuma.gradle`
   reads them, they are never edited by hand. **If conflicts are significant, stop and plan with
   白い熊** before continuing. If it goes irrecoverable: `git rebase --abort` (`custom` is untouched
   by an aborted rebase; `master` is safely fast-forwarded).

5. **Do NOT reset the build tail.** `BUILD_NUMBER` in `gradle.properties` keeps counting. Reset it
   to `1` **only** if upstream's own `versionCode` literal moved (step 1 shows it) — and even then
   only when the new `<code>*10000+1` exceeds `LAST_BUILT_VERSION_CODE` (a bump from `118` to `119`
   always does). An installer compares `versionCode` alone; resetting `N` under a standing upstream
   code would make the sync a downgrade, and `buildFork` would refuse it anyway.

6. **Verify our customizations are intact after the rebase:**

   | What | Expected | Where |
   | --- | --- | --- |
   | applicationId / namespace / sharedUserId | `com.termux`, all three, **unchanged** | `app/build.gradle` → `namespace`; `AndroidManifest.xml` → `${TERMUX_PACKAGE_NAME}` |
   | Fork script hook | `apply from: 'shiroikuma.gradle'` is the **last line** of `app/build.gradle` | `app/build.gradle` |
   | Fork script | pin + signing + `buildFork` (+ the `buildNdkBuild* → downloadBootstraps` dependency) | `app/shiroikuma.gradle` |
   | Build tail | `BUILD_NUMBER` unchanged (not reset), `LAST_BUILT_VERSION_CODE` present | `gradle.properties` |
   | Signing | `keystore.properties` present (gitignored), alias `Emacs keystore` | repo root |
   | App label | `TERMUX_APP_NAME = "白い熊 Termux"` + the `<!ENTITY TERMUX_APP_NAME …>` in **both** `strings.xml` (Phase 3) | `termux-shared/.../TermuxConstants.java`, `app/src/main/res/values/strings.xml`, `termux-shared/src/main/res/values/strings.xml` |
   | Fork links | `TERMUX_GITHUB_REPO_NAME` / `_URL` / issues URL → `ShiroiKuma0/shiroikuma-termux` (Phase 3) | `TermuxConstants.java` |
   | Black-yellow icon | yellow line-art `>_` on black, all densities (Phase 2) | `app/src/main/res/drawable/ic_foreground.xml`, `mipmap-*/`, `art/ic_launcher.svg`, `fastlane/…/icon.png` |
   | 白い熊 Termux UI page + hooks | `com.termux.shiroikuma.*`, the gear long-press, the Style item, `ShiroikumaRootView` (Phase 4) | `app/src/main/java/com/termux/shiroikuma/`, `TermuxActivity.java`, `activity_termux.xml`, `AndroidManifest.xml` |
   | Absorbed plugins | `com.termux.{boot,widget,window}` packages + merged manifest components (Phase 4) | `app/src/main/java/com/termux/{boot,widget,window}/` |
   | Committed agent files | `CLAUDE.md`, `.claude/skills/` tracked; only `.claude/settings.local.json` ignored | `.gitignore` |

   Watch for **new upstream strings that reintroduce the plain "Termux" name or the
   `termux/termux-app` links** in user-visible places — the rebase grep guard from CLAUDE.md:
   ```bash
   p='= "Termux"\|= "termux-app"\|ENTITY TERMUX_APP_NAME "Termux"\|termux/termux-app'; grep -rn "$p" termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java app/src/main/res/values/strings.xml termux-shared/src/main/res/values/strings.xml fastlane/ | sed 's#// Default:.*##' | grep "$p"
   ```
   (Code namespaces, internal ids, log tags and upstream's copyright stay as upstream wrote them.)
   Sanity check the script still evaluates and prints the new pin:
   `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:versionName -q < /dev/null`
   — the `.g<sha8>` must now be the new `git merge-base HEAD master`.

7. **Port the absorbed plugins' changes** flagged "yes" in Table 2, if 白い熊 asked for it in the
   go-ahead; otherwise leave them listed in the handover. Update the port-state table at the end of
   this file either way (only the ported shas move).

8. **Build the next `+NNN`** via the **build-apk** skill
   (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew buildFork --console=plain < /dev/null`,
   in the background — a bootstrap or NDK bump makes it a cold build), then deliver it via the
   global **`/after-build`** skill (no transfer prompt). This is the first build on the new base:
   same upstream literal, new pin, next counter — e.g.
   `0.118.0+2026-09-11.22-27.g45844885+003` → `0.118.0+2026-10-02.09-14.g1a2b3c4d+004`.

9. **Stop.** Let 白い熊 test. Commit/push only on their explicit **"Push"** — and then, in that turn:
   ```bash
   git checkout master && git push origin master                 # ff, safe
   git checkout custom && git push --force-with-lease origin custom   # rebased history
   git branch -D custom-pre-<date>                               # the safety branch has served
   ```

## Notes

- Keep our changes a **small, legible layer** on top of upstream — prefer rebasing (linear history)
  over merging, so the customization set stays easy to audit and replay.
- **`app/build.gradle`: the `apply from` must stay the last line.** Upstream's inline
  `validateVersionName(versionName)` (strict SemVer, one `+`) runs inside `defaultConfig`; our
  two-`+` name is assigned afterwards by the applied script. If upstream ever moves validation to
  `afterEvaluate` or into a task, the script needs the same move — that is a stop-and-plan item.
- **`targetSdk` stays 28** whatever upstream does around it — the W^X rule (see CLAUDE.md).
- A bootstrap bump (new `downloadBootstraps` checksums) means `app/src/main/cpp/bootstrap-*.zip`
  are re-downloaded on the next build (the task deletes a zip whose hash no longer matches); an
  NDK bump means checking `~/android-sdk/ndk/<exact version>` exists before building.
- `termux-shared` is consumed by the separate `shiroikuma-termux-api` / `-x11` / `-gui` forks only
  as a JitPack artefact pinned by their own build files — a sync here never obliges a sync there.

## Absorbed-plugin port state

Last upstream commit of each plugin that has been ported into this app (Phase 4 has not landed
yet — the first port sets every row). Move a row only when its port is in `custom`.

| Plugin | Remote | Ported up to | Target package |
| --- | --- | --- | --- |
| Termux:Boot | `upstream-boot/master` | *(not yet ported)* | `com.termux.boot` |
| Termux:Widget | `upstream-widget/master` | *(not yet ported)* | `com.termux.widget` |
| Termux:Float | `upstream-float/master` | *(not yet ported)* | `com.termux.window` |
| Termux:Styling | `upstream-styling/master` | *(not yet ported)* | the 白い熊 Termux UI page (colour schemes / fonts) |

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` /
"Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of
the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
