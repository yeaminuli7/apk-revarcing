# Session state — API-key only + on-device screen agent + bubble + voice

## Status: SHIPPED as build-36 (184 MB APK)

Release: https://github.com/yeaminuli7/apk-revarcing/releases/download/build-36/Antigravity-1.0.1-release.apk
Branch `arena/6d2e1f67-apk-revarcing`, HEAD `3ae32ac`, run 45747798841 success.

### 1) Google login removed (API key only) — done
- Deleted `oauth/OAuthManager.kt`, `oauth/AccountManager.kt`, `ui/components/GoogleSignInDialog.kt`
  (2,600+ lines removed). No `OAuthManager`/`AccountManager` references remain.
- `AuthDelegate` rewritten: only the API-key and accessibility dialogs; `refreshAuthState` is
  a no-op that just refreshes quota.
- `MainActivity`: start destination = SPLASH when `GeminiClient.hasApiKey`, else API_KEY.
  The OAuth check is gone.
- `ChatScreen`: drawer account card → API-key card (`Icons.Default.Key`, not `VpnKey` — that
  icon does not exist in the bundled icon set).
- `SettingsScreen`: "Sign In Required" gate removed from Models & Usage; `AccountSettingsPage`
  replaced with an API-key manager (show / save / remove); footer row opens the key dialog via
  `viewModel.showApiKeyDialogExternal()`.
- `CloudCodeQuotaService`: token is hard-null, so quota/tier calls report unavailable instead of
  failing on a missing session.
- Chat still runs the `agy` CLI via ACP (`executeAgyAcpTurn`); only the account UI is gone.

### 2) Bundled engine (offline first launch) — done
- `.github/workflows/android-release.yml` gained "Bundle bootstrap + core engine assets": it
  downloads `bootstrap-aarch64.zip` (85,070,266 B) and the manifest tarball
  (`cli_linux_arm64.tar.gz`, 93,662,528 B, sha512 verified) into `app/src/main/assets/`,
  writing `core-engine.txt` (`version=`, `sha512=`).
- **Asset naming trap:** a `.tar.gz` asset name made AAPT store a decompressed 260 MB `.tar`
  entry, so the APK was 336 MB and the app could never find its payload. The engine asset is
  now called **`core_engine_payload`** (no dots). Verify with
  `unzip -lv … | grep assets/core_` → `Stored`/`Defl` 93,662,528 B.
- `CoreUpdateManager.hasBundledEngine()` + `installBundledEngine()`: extracts the `antigravity`
  member from the asset, patches syscalls via `CoreBinaryPatcher`, installs to
  `engine/bin/agy`, smoke-tests `ld-linux --library-path … agy --version`, writes
  `.engine_version`. Falls back to download on failure.
- `BootstrapInstaller.extractBootstrap()` prefers the bundled bootstrap, then Downloads, then
  network.
- Verified inside the shipped APK: payload sha512 = `e4f086d0…716f7` (matches manifest),
  `gzip OK`, single member `antigravity`.

### 3) Screen understanding + human-like tapping — done (first version)
- `vision/ScreenCaptureManager.kt`: MediaProjection screenshot, downscaled to 720 px wide,
  JPEG q70, base64. Must be granted once from an Activity.
- `accessibility/ScreenControlService.kt` rewritten: `describeScreen()` flattens every window
  into numbered `[id]` elements (role, text, desc, hint, bounds); actions `clickNode(id)`,
  `tap`, `longPress`, `swipe`, `scroll`, `typeInto`, `back/home/recents/openApp`. Every action
  blocks until the gesture completes.
- **Config fix:** `res/xml/accessibility_service_config.xml` had
  `android:packageNames="id.vern.antigravity"`, which restricted the service to our own app.
  Removed; now `flagRetrieveInteractiveWindows`, `flagIncludeNotImportantViews`,
  `canRequestEnhancedWebAccessibility`, `settingsActivity`.
- `api/GeminiClient.kt`: `screenAgentTurn()` (multimodal + `functionDeclarations`),
  `buildObservationParts()`, `buildFunctionResponseTurn()`. Default model `gemini-2.5-flash`
  (user's own choice confirmed).
- `vision/ScreenAgent.kt`: look → decide → act → look loop, `MAX_STEPS = 25`, tools
  tap/tap_xy/type_text/scroll/swipe/back/home/open_app/wait/task_finished, StateFlow state
  (running, step, lastAction, summary, error, lastScreenshotBase64), `stop()`, `reset()`.
- `ChatScreen`: a "Screen" toggle pill next to the model pill; when on, `onSendMessage` drives
  the agent instead of plain chat. Requests accessibility + MediaProjection as needed. Status
  panel shows step / last action / Stop / Dismiss. `ChatInputBar` gained
  `screenAgentEnabled` + `onToggleScreenAgent`.

## Gotchas learned this session
- `Icons.Default.VpnKey` does not exist; use `Icons.Default.Key`.
- `AppLogger` has only `d/w/i/e(tag, message)`.
- A composable inserted after `Scaffold(...)`'s closing brace but before its `bottomBar` lambda
  is composed inside the slot → "@Composable invocations can only happen from …" errors.
  Place top-level dialogs/panels before the `if (showModelSelector)` block.
- CI failure diagnostics land in `errors.txt` on the branch; `git rm --cached` it before the
  next push or the rebase conflicts.
- Sandbox cannot reach `release-assets.githubusercontent.com` / `storage.googleapis.com` /
  the manifest host. Use Actions runners; `ubuntu-24.04-arm` runs aarch64 natively.

## Next steps / open items
1. **Termux user-package pre-bundling** (user's third choice was "both" = bootstrap + core; a
   separate "termux packages" option was offered but not chosen). If requested later: `pkg install`
   payloads can be pre-seeded into `assets/` and copied into `$PREFIX`, but DEB dependency order
   and ~40 MB/package sizes make this a bigger job.
2. **Never sync this branch's `assets/` to a plain Git remote** — the 178 MB payload is fetched
   in CI only and is NOT committed (`.gitignore` untouched; assets are produced per build).
3. Screen agent is a first pass: no OCR fallback, no screenshot preview in chat
   (`lastScreenshotBase64` is captured but not yet rendered), typing falls back to failure on
   custom canvases. Real-device testing has not happened yet — ask the user for a report of what
   the agent does on their phone.


## Round 2 (build-36) — recurring permission, bubble, voice

### Why the "Share your screen with Antigravity?" dialog kept coming back
The old `ScreenCaptureManager.captureBitmap()` created a **new VirtualDisplay + ImageReader
for every frame and released them in `finally`**. Releasing the display ends the capture
session, so Android asked again on the next screenshot — and the sheet defaults to
"Share one app". Fixed by `startSession()`: one MediaProjection, one VirtualDisplay, one
ImageReader, frames converted on the listener thread with a 250 ms throttle, newest frame
cached. `captureBitmap()`/`captureBase64Jpeg()` now take no Context.

User must pick **"Entire screen"** once; "Share one app" limits capture to that app.

### New components
- `vision/ScreenCaptureManager.kt` — rewritten as a persistent session (`startSession`,
  `stopSession`, `isReady`, `captureBitmap`, `captureBase64Jpeg`).
- `service/AssistantBubbleService.kt` — `TYPE_APPLICATION_OVERLAY` bubble: drag to move
  (position in prefs `antigravity_bubble_prefs`), tap = command bar (text + mic), long
  press = voice toggle, colour = state (green idle-listening / amber wake / blue speaking).
  Local intents handled inline: stop|cancel, hide, home, back. Anything else → ScreenAgent.
  Speaks the agent's final summary via `collectLatest` on `ScreenAgent.state`.
  `canDrawOverlays()` + `openOverlayPermission()` gate `show()`.
  `autoStartVoice()` starts voice when the bubble appears (mic permission checked).
- `voice/VoiceController.kt` — continuous `SpeechRecognizer` with wake words
  ("hey/hi/ok antigravity", Bangla variants), partial-result fast path, auto-restart with
  backoff, dedupe window 4 s, `TextToSpeech` for replies. `onCommand` hook is set by the
  bubble service.
- Manifest: `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`, bubble service
  (`foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`).
- `MainActivity`: requests `RECORD_AUDIO`; requests battery-optimisation exemption
  (the auto-revoke "pinning" idea was dropped: `setAutoRevokePermissionsMode` is not public
  API and reflection on it would fail silently).
- Chat drawer: "Screen agent setup" card with three rows —
  accessibility (Open), screen sharing (Allow/Granted), floating bubble (On/Off).

### Known gaps in this round
- Voice uses `SpeechRecognizer`, which on most phones stops at ~1 min per utterance and
  restarts; it is not a true always-on wake word engine (that needs e.g. Porcupine/ONNX).
  Battery cost of continuous recognition is real; the bubble can turn voice off.
- The wake word cannot be heard while the phone is fully locked (Android blocks mic for
  background apps); it works while the screen is on or the app is in the foreground.
- No screenshot preview in chat yet; `lastScreenshotBase64` is captured but not rendered.
