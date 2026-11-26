# Session state — agy "cannot open shared object file" fix

## Status: DONE & SHIPPED (build-24)

**Root cause (proven, not guessed):** `bootstrap-aarch64.zip` (85,070,266 B, core-v1) contains
**only the Termux prefix** — 4,862 files, all under `bin/ share/ include/ lib/ ...`.
There is **no `engine/` directory, no `agy`, no `language_server`**. The fork's
`installCoreEngine()` was a no-op ("Core engine already bundled with bootstrap"), so
`engine/bin/agy` was **never created**. The glibc loader then reported the program path itself
as the unresolvable object:
`.../engine/bin/agy: error while loading shared libraries: ... cannot open shared object file`.
The `ld-linux-aarch64.so.1` was present and fine — it was never a missing `lib*.so`.

**Why the fork lost it:** upstream `KuatoDev/Antigravity` has
`install/CoreUpdateManager.kt` (326 lines) and a real `installCoreEngine()` that downloads the
binary. The zip we imported (gofile archive) was missing that file, and
`installCoreEngine()` had been replaced with a stub. `git log` shows exactly one import commit
`a15a36b`, so this was an omission in the source archive, not a later regression.

**Fix committed:**
1. Added `app/src/main/kotlin/id/vern/antigravity/install/CoreUpdateManager.kt` (restored from upstream).
2. `installCoreEngine()` now calls `CoreUpdateManager.checkForUpdate()` + `downloadAndApply()`.
3. `engineProblem(context)`: returns a reason string when `engine/bin/agy` is missing, < 1 MB,
   non-ELF, or non-executable (also checks the loader is ≥ 100 kB). Public — used by boot.
4. `verifyEngine()` logs the verified size after a fresh install.
5. `healEngineIfMissing()` runs on the fast path (already-installed) and re-downloads if broken.
6. **Engine-only repair path**: if prefix is intact (`usr/bin/bash` + `usr/.installed`) but the
   engine is broken, repair *only* the engine (glibc if absent → download core → overlay →
   permissions → wrapper) and skip the 85 MB bootstrap re-download. Falls back to full install.
7. `isInstalled()` hardened: requires `agy.length() > 1_000_000` and `ld.length() > 100_000`.
8. `AntigravityViewModel.bootEngine()` now checks `engineProblem()` before starting the core
   service and surfaces "Core engine is not usable: …" instead of the raw loader message.

**Verification done in CI (real artifacts, not assumptions):**
- `docs/` probe runs (removed afterwards) downloaded the manifest:
  `https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_arm64.json`
  → HTTP 200, `version 1.5.17`, tarball `cli_linux_arm64.tar.gz`, 93,662,528 B,
  sha512 `e4f086d0…716f7` **matched**.
- Tarball has exactly one member: `antigravity`, 260,657,840 B aarch64 PIE, stripped.
  `NEEDED`: libresolv.so.2, libpthread.so.0, libm.so.6, libdl.so.2, librt.so.1, libc.so.6.
- All six are present in `assets/antigravity-glibc.zip`. Missing symbol versions: **none**
  (binary needs up to GLIBC_2.26; vendored libc is
  "GNU C Library (GNU libc for Android/com.termux) stable release version 2.44-0").
- **Actually executed on `ubuntu-24.04-arm`**:
  `/…/ld-linux-aarch64.so.1 --library-path <glibc/lib> <agy> --version` → printed `1.5.17`, exit 0.

**Shipped:** Release **build-24** — run 45735933100 success.
`https://github.com/yeaminuli7/apk-revarcing/releases/download/build-24/Antigravity-1.0.1-release.apk`
(6,306,805 B). Previous: build-21, build-19 (same engine fix, before the engine-only-repair
optimisation), build-17.

## Keys / facts to reuse
- Branch `arena/6d2e1f67-apk-revarcing`, HEAD `22c9a3d`. Workflow `.github/workflows/android-release.yml`
  publishes `build-N` automatically. Probe workflows were deleted — recreate if needed.
- `NetworkDownloader.httpGetString(url, headers, userAgent, connectTimeout, readTimeout): Pair<Int, String?>`
  and `NetworkDownloader.downloadWithResume(url: String, dest: File, maxAttempts, userAgent, onProgress)`.
- `AppLogger` has only `d/w/i/e(tag, message)` — **no throwable argument**.
- Sandbox cannot reach `release-assets.githubusercontent.com`, `storage.googleapis.com`, or the
  manifest host; **use `ubuntu-latest` Actions runners** for anything touching those.
  `ubuntu-24.04-arm` is available and can run aarch64 binaries natively (no qemu; the
  `apt install qemu-user-static` step fails there).
- Bootstrap zip has no engine → any future "engine missing" report means the core download failed
  (network/93 MB) rather than a packaging problem.

## Next step if the user reports it again
Ask for the boot error text (it is now descriptive), or the app log; the likely causes are a
93 MB download failing on mobile data or the sha512/tar extraction failing on a low-storage
device. The Retry button on the splash error card re-runs `triggerEngineBoot()`.
