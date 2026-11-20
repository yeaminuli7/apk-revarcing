# Building BalaSpeak on Windows

Everything below is typed into **cmd.exe**, **PowerShell** or **Git Bash**. Only copy the
lines that start with a command - the explanations are not commands.

## Why the first attempt failed

| Symptom | Cause |
|---|---|
| `error: pathspec 'আরও...' did not match` | the Bengali comment after the command was pasted into the shell |
| `'#' is not recognized as an internal or external command` | `#` is a comment in bash, **not** in cmd.exe |
| `'bash' is not recognized` | bash is not on PATH; Git for Windows ships it at `C:\Program Files\Git\bin\bash.exe` |
| `did not match any file(s) known to git` | `main` of this repo is only a README stub - the sources live on the work branch and must be fetched |

## One-shot setup (recommended)

From the repository folder:

```
scripts\windows-setup.bat
```

and if you want every neural voice inside the APK:

```
scripts\windows-setup.bat -WithVoices
```

The script fetches the work branch when the sources are missing, downloads the
sherpa-onnx AAR (~48 MB) that is not on Maven, and optionally runs the voice packing
script through Git Bash (~400 MB).

## Manual steps

```
git fetch origin arena/05c3abe6-apk-revarcing
git checkout -B v34 FETCH_HEAD
dir settings.gradle.kts
```

```
if not exist app\libs mkdir app\libs
curl -L -o app\libs\sherpa-onnx-1.13.8.aar https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
```

Voices inside the APK (needs Git Bash, or WSL):

```
"C:\Program Files\Git\bin\bash.exe" scripts/prepare_builtin_assets.sh
```

## Open and run

1. Android Studio **Otter 2025.2 or newer** (AGP 9.1.1 / Gradle 9.3.1 / compileSdk 36 / JDK 21).
2. **File > Open** the folder that contains `settings.gradle.kts`.
3. Install SDK Platform 36 when Studio asks, then press **Run**.
4. Use a real arm64 phone (developer options + USB debugging). The APK ships
   `arm64-v8a` only, so an x86_64 emulator cannot load the sherpa engine.

## Build from the command line

```
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease -PnoMinify
```

Output: `app\build\outputs\apk\release\app-release.apk`

Release signing (key is already in the repo):

| Field   | Value |
|---------|-------|
| keystore | `keystore\balaspeak-release.p12` |
| alias    | `upload` |
| password | contents of `keystore\password.txt` |
| type     | PKCS12 |

## Common errors

| Error | Fix |
|---|---|
| `app/libs/sherpa-onnx-1.13.8.aar is missing` | run the curl line above (or `scripts\windows-setup.bat`) |
| `Minimum supported Gradle version` / AGP wants a newer IDE | update Android Studio |
| `Failed to find target with hash string 'android-36'` | SDK Manager: install Platform 36 + Build-Tools |
| App starts but the voice list stays empty | slim build; use the `-WithVoices` build or the released `builtin` APK |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | the phone has the app signed with another key - uninstall it first |
| Studio shows `jniLibs/` as untracked | expected, it is gitignored (~400 MB of models); do not commit it |
