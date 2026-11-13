# Learning APK Reverse Engineering — Complete Beginner-to-Advanced Roadmap

A self-paced curriculum for someone starting from **basic/zero experience**, aiming to
**understand how Android apps work under the hood** (hobbyist / learning goal), with
about **1–2 hours per week**. Expect roughly **6 months** for the core path; mastery
comes with practice over a year or more.

> ## ⚠️ Legal & ethical ground rules (read first)
>
> Reverse engineering is a skill, not a license to do anything with it. Stay inside
> these lines and you will never have a problem:
>
> - **Only analyze apps you own, apps that are open source, or deliberately vulnerable
>   practice apps.** This covers Open Source apps from F-Droid/GitHub, your own
>   compiled apps, and training targets like DIVA and InsecureBank.
> - **Cracking paid apps, bypassing licensing, removing ads, or "modding" someone
>   else's app for redistribution is against most Terms of Service and is illegal in
>   many jurisdictions (e.g., DMCA anti-circumvention in the US).** This guide does not
>   teach that, and the skills below don't require it.
> - **Do not analyze or attack apps/servers you don't have permission to test.**
>   Security research on third-party apps means getting permission or using bug-bounty
>   programs / coordinated disclosure.
> - The exact legality of reverse engineering varies by country. Personal learning on
>   your own device and own apps is effectively universally safe; distribution and
>   circumvention are where people get into trouble.
> - When in doubt: **build the app yourself first, then reverse your own build.**
>   That is the single fastest, fully-legal way to learn.
>
> Practice targets that are 100% safe and legal:
> - **DIVA** (Damn Insecure and Vulnerable App) — Android security training app
> - **InsecureBankv2** — intentionally vulnerable banking app used in courses
> - **crackmes.one** — filter for "Android"; purpose-made reverse-engineering puzzles
> - **Any APK you compile yourself** with Android Studio
> - **Open-source apps** (e.g., from F-Droid or GitHub releases)

---

## How to use this roadmap

- Time budget: **~1.5–2 hours per week**, split into e.g. 3× 40-minute sessions.
- Each phase lists **Goal → Learn → Do → Checkpoint**. Don't move on until you pass
  the checkpoint honestly — the pacing is built for retention, not speed.
- Keep a **lab notebook** (a markdown file per session) with commands, screenshots,
  and questions. Re-reading it is how the material sticks.
- All tools below run on Linux, macOS, or Windows (WSL2 works great).

---

## Phase 0 — Orientation & prerequisites (Weeks 1–3)

**Goal:** Comfortable with the command line and the basics of how Android works.

**Learn**
- Terminal basics: navigation (`cd`, `ls`, `pwd`), files (`cp`, `mv`, `rm`, `mkdir`),
  pipes (`|`), redirection (`>`), `grep`, `find`, `unzip`.
- How Android apps are built: Java/Kotlin source → compiled to **DEX** bytecode →
  packaged with resources into a signed **APK**.
- Basic Java/Kotlin reading ability (you don't need to be a developer; you need to
  *read* code): classes, methods, fields, strings, loops, conditionals.
- Android app anatomy from a developer's view: `AndroidManifest.xml`, Activities,
  Services, BroadcastReceivers, ContentProviders, permissions.

**Do**
- Install a Linux-ish environment or WSL2. Learn `unzip -l app.apk` — yes, an APK is
  just a ZIP archive.
- Install **Android Studio** and create a "Hello World" app. Build it. Run it on the
  emulator. You have just made your first analysis target.
- Skim the Android developers docs: "App fundamentals", "App manifest overview".

**Checkpoint:** You can explain, in plain words, what an APK is, what DEX is, and what
the manifest declares. You can build and run a Hello World app.

---

## Phase 1 — APK file structure (Weeks 3–5)

**Goal:** Open any APK and know what every file inside is for.

**Learn**
- APK = ZIP. Contents:
  - `AndroidManifest.xml` — binary (compiled) XML: package name, version, permissions,
    exported components, SDK levels, intent filters.
  - `classes.dex` (sometimes `classes2.dex`, `classes3.dex`…) — compiled Dalvik/ART
    bytecode. **This is where the program logic lives.**
  - `resources.arsc` — compiled resource table (strings, IDs).
  - `res/` — images and other raw resources.
  - `assets/` — raw files bundled unprocessed (databases, HTML/JS in hybrid apps).
  - `lib/<abi>/` — native libraries (`.so` files) for arms/arm64/x86 — compiled C/C++.
  - `META-INF/` — signature and certificate files (`CERT.RSA`, `MANIFEST.MF`).
  - `kotlin/`, `META-INF/*.version`, `build.gradle` hints — fingerprints of tooling.

**Do**
- Take your own Hello World APK and open it with `unzip -l` and a GUI archive tool.
- Decode the binary manifest with:
  - `aapt dump badging app.apk` and `aapt dump permissions app.apk`
  - or `apktool d app.apk` (see Phase 2)
- Compare an APK built in **debug** vs **release** mode: size, signature
  (`META-INF/`), and readable strings (`strings app.apk | less` or on `classes.dex`).

**Checkpoint:** Given an APK, you can list its DEX files, native libs, permissions,
min/target SDK, and signing certificate — without decompiling any code yet.

---

## Phase 2 — The toolbelt (Weeks 5–8)

**Goal:** Install and feel comfortable with the standard static-analysis toolchain.

**Learn & install** (all free, open source)

| Tool | Purpose |
|------|---------|
| **jadx** / jadx-gui | Decompile DEX → readable Java. Your #1 daily tool. |
| **Apktool** | Decode APK → Smali + resources (`apktool d`), rebuild (`apktool b`). |
| **smali/baksmali** | Assemble/disassemble DEX ↔ Smali (the readable bytecode format). |
| **aapt / aapt2** | Inspect manifest, resources, badging (comes with Android SDK build-tools). |
| **keytool / apksigner** | Inspect signing certificates (`keytool -printcert -file CERT.RSA`). |
| **Android Studio** | Build practice apps, logcat viewer, AVD emulators. |
| **adb** | Talk to emulator/device: install, logs, shell. |
| **Ghidra** (later) | Disassemble native `.so` libraries (C/C++ parts). |
| **MobSF** (later) | Automated static+dynamic security report — great for learning what "findings" look like. |

**Do**
- Decompile your own Hello World APK in jadx-gui. Find the `MainActivity`, read the
  decompiled `onCreate`. Change a string in your Kotlin source, rebuild, decompile
  again, and watch the diff — this builds deep intuition.
- Run `apktool d` on the same APK and browse `smali/`. Open `AndroidManifest.xml`
  (now decoded to readable XML).
- Run `apktool b` to rebuild and see that it round-trips.

**Checkpoint:** You can decode, read, rebuild, and re-decode your own APK, and you
know which tool to reach for to answer: "what does this permission do?", "what does
this class do?", "who signed this?".

---

## Phase 3 — Static analysis workflow (Weeks 8–12)

**Goal:** A repeatable process for understanding what an app *does* without running it.

**Learn the workflow**
1. **Triage the APK**: hash it (`sha256sum`), record package, version, signing cert,
   SDK versions, permissions, target SDKs.
2. **Read the manifest**: exported activities/services/receivers/providers, intent
   filters, permissions (normal vs dangerous), `android:debuggable`, `usesCleartextTraffic`.
3. **Decompile with jadx**, set up a project view, and rename things as you go.
4. **Map the entry points**: what the main launcher activity does; what deep links
   exist; what each exported component accepts.
5. **Find the interesting bits**: hardcoded strings/URLs/API keys (`jadx` search,
   `strings`, grep over resources), crypto usage, file/database access, logging.
6. **Understand the data flow**: user input → validation → storage → network egress.
7. **Note weaknesses** (for your own apps / lab apps): cleartext HTTP, debuggable
   builds, exported components without permissions, secrets in code, verbose logs.

**Do**
- Full static write-up on a training app: DIVA or InsecureBankv2. For each challenge in
  DIVA, locate the relevant code in jadx before looking at any walkthrough.
- Practice with `jadx` search: find all usages of `SharedPreferences`, `SQLite`,
  `HttpURLConnection`, `WebView`, `Log.d`.
- Unzip an app and grep `resources.arsc` strings (`strings resources.arsc`) for URLs
  and keys — hybrid (Cordova/React Native) apps leak a lot there.

**Checkpoint:** Given a training APK you've never seen, you can produce a one-page
report: package/cert info, components, permissions, main flows, and notable strings —
in under an hour.

---

## Phase 4 — Dynamic analysis (Weeks 12–16)

**Goal:** Watch the app run: logs, network, storage, and runtime behavior.

**Learn**
- Emulator/AVD setup (use a **test** emulator or a dedicated test device — not your
  daily phone). Note: Google Play images vs AOSP images; rooting a lab emulator is fine.
- `adb logcat` — read runtime logs; filter by package; spot leaked data in logs.
- Network inspection: point your **lab** emulator at a proxy you control (e.g.,
  Burp Suite Community or mitmproxy) to see your *own* training app's traffic.
  Understand TLS: certificate pinning, and why inspecting someone else's traffic needs
  explicit authorization (in a lab, on apps you own or are given, it's fine).
- File system inspection via `adb shell` (lab device): check what the app writes to
  `/data/data/<package>/` (databases, shared_prefs, files).
- **Frida** (gentle intro): hook Java methods at runtime to observe arguments/returns.
  Learn the concept on DIVA/InsecureBank. Frida's docs are excellent; start with a
  single `Java.perform` hook that logs a method call.

**Do**
- Run DIVA/InsecureBankv2 on the emulator; capture `logcat` while triggering each
  challenge; observe insecure logging and storage firsthand.
- Sniff your own test app's HTTP traffic through mitmproxy; watch what it sends.
- Write one Frida hook that prints the arguments of a specific method in your own app.

**Checkpoint:** You can combine static + dynamic analysis to explain exactly what a
training app exposes to logs, the file system, and the network — and demonstrate each.

---

## Phase 5 — Bytecode & native deep dives (Weeks 16–20)

**Goal:** Read what decompilers hide: Smali/DEX and native code.

**Learn**
- DEX format basics: string pool, classes, methods, the Dalvik instruction set
  (`invoke-virtual`, `const-string`, `iget`…). Read Smali well enough to follow logic
  when jadx fails or produces garbage (obfuscated code).
- Smali patching loop: `apktool d` → edit `.smali` → `apktool b` → sign with `apksigner`
  → reinstall. Use only on **your own or lab apps**. The classic lesson: toggle a
  boolean, and understand what signature verification means for integrity.
- Code shrinking & obfuscation: ProGuard/R8 output, name mangling, and how to still
  navigate it (xrefs, strings, constants).
- Native libraries: if `lib/` has `.so` files doing the interesting work, use **Ghidra**
  to disassemble them — function discovery, string references, calling convention.
  Start with tiny C functions compiled into your own app.

**Do**
- Take your own obfuscated (R8 release) build and decompile it; compare with the debug
  build. Note what was renamed.
- Smali-patch one boolean in your own app, rebuild, sign, and verify the behavior change.
- Compile a small C function into a test APK via the NDK and locate it in Ghidra.

**Checkpoint:** When jadx output is unreadable, you can still answer a concrete question
by dropping to Smali (or Ghidra for native), on your own or lab apps.

---

## Phase 6 — Practice, synthesis & next steps (Weeks 20–24+)

**Goal:** Consolidate into durable skill.

**Projects (pick 3–4)**
1. **APK anatomy atlas**: take 5 open-source APKs (e.g., from F-Droid) and document
   their structure — decompile status, native libs, permissions.
2. **DIVA/InsecureBank full pass**: write a report on every challenge, with code
   references and fixes you would apply.
3. **crackmes.one Android**: solve 5 beginner/medium Android crackmes for fun.
4. **Build-and-break**: deliberately write a vulnerable version of an app you build
   (hardcoded secret, exported receiver, cleartext HTTP), then reverse your own build,
   find the bugs, fix them, and verify. This is the best exercise that exists for this.
5. **Certificate/signature comparison**: verify why re-signing a release build breaks
   updates, and explain what that means for integrity.

**Where to go deeper (only with proper authorization)**
- OWASP **MASTG** (Mobile Application Security Testing Guide) & **MASVS** — the
  industry-standard knowledge base; use it as a checklist on lab apps.
- **MobSF** for automated scanning and understanding report terminology.
- **Android malware analysis** (a big step): start with public reports and
  sandbox services on *inert* samples only — never on your personal device.
- **Bug bounties**: only for apps/programs that explicitly authorize mobile testing.
- **Google Play Protect / MASA** — awareness of how apps are vetted officially.

---

## Weekly template (1.5–2 h/week)

| Session | Length | Activity |
|---------|--------|----------|
| 1 | 30 min | Reading: docs/MASTG/article for the week's topic |
| 2 | 45 min | Hands-on: install, decompile, trace, log |
| 3 | 30 min | Lab notebook: write what you learned + one question to revisit |

Every 4–5 weeks, spend the whole week re-doing one earlier checkpoint from memory.

## A minimal toolchain checklist (Phase 2)

- [ ] Java JDK + Android Studio (SDK, emulator/AVD, build-tools incl. `aapt`,
      `apksigner`) + `adb` on PATH
- [ ] jadx-gui
- [ ] apktool (wrapper script + jar)
- [ ] smali/baksmali (optional; apktool bundles compatible versions)
- [ ] Ghidra (Phase 5)
- [ ] mitmproxy or Burp Suite Community (Phase 4)
- [ ] A disposable lab emulator — **never your daily driver**

## Glossary (starter)

- **APK** — Android Package; signed ZIP containing code, resources, manifest.
- **DEX / Dalvik** — Android's bytecode format and the VM/runtime that executes it.
- **Smali** — human-readable assembly for DEX ("anti-Jamali" jokes aside, it's the
  disassembly format used by baksmali).
- **AXML** — Android's binary XML format used for the compiled manifest/resources.
- **resources.arsc** — compiled resource table mapping IDs → values.
- **R8/ProGuard** — shrinkers/obfuscators in release builds.
- **Certificate pinning** — client-side check that the TLS cert matches an expected
  cert; bypassing on apps you don't own/aren't authorized to test is off-limits.
- **Exported component** — an Activity/Service/Receiver/Provider reachable from other
  apps; a classic source of Android vulnerabilities.

## Common beginner mistakes

- Trying to decompile before understanding APK structure — you'll be lost in files.
- Practicing on real third-party apps instead of lab apps — wrong habit, wrong ethics.
- Skipping the "build your own app first" step — you lose the ground truth that makes
  decompiler output make sense.
- Expecting jadx to always produce working code — release builds with R8 are for reading,
  not recompiling.
- Running unknown APKs on a personal phone. Use the lab emulator.

## FAQ

**Is reverse engineering legal?** For learning on apps you own and lab apps: yes,
effectively everywhere. Circumventing protection of apps you don't own, and
redistributing modified third-party APKs, is where legal exposure lives. Check your
jurisdiction and ToS.

**Do I need to know Java/Kotlin first?** Not fluently — reading comprehension is
enough. You'll pick up a working subset en route because every tutorial in this space
is written in Java.

**Should I learn Flutter/Unity/React Native reverse specifics?** Yes, later. Hybrid
and cross-compiled apps (React Native, Flutter, Xamarin) bundle JS/Dart/.NET assemblies
and need different handling. Add this in Phase 6 if interested.

**How long until I'm "good"?** At 2 h/week: comfortable with structure + tools in ~2
months, static workflow in ~3 months, and dynamic-analysis-level competence in ~4–6
months. Real depth (native code, obfuscation) takes a year+ of steady reps.

## Suggested resource list

- OWASP MASTG & MASVS — https://mas.owasp.org/
- jadx — https://github.com/skylot/jadx
- Apktool — https://apktool.org/
- Frida — https://frida.re/
- Android Developers — App fundamentals & manifest: https://developer.android.com/
- DIVA — https://github.com/payatu/diva-android
- InsecureBankv2 — https://github.com/dineshshetty/AndroidInsecureBankv2
- crackmes.one (Android filter) — https://crackmes.one/
- F-Droid (open-source target apps) — https://f-droid.org/
- MobSF — https://github.com/MobSF/Mobile-Security-Framework-MobSF
- The Android Hacker's Handbook (Wiley, 2014) — dated but excellent on fundamentals

---

*Path summary: Phase 0 orientation → Phase 1 APK anatomy → Phase 2 tools →
Phase 3 static workflow → Phase 4 dynamic workflow → Phase 5 Smali/native depth →
Phase 6 practice & specialization. Revisit checkpoints; keep a lab notebook; practice
only on what you're allowed to touch.*
