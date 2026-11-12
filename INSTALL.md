# Install / signature troubleshooting

Every APK published from this repository since **v2.8** is signed with the same stable
sideload key, with **v1 + v2 + v3** signature schemes:

```
subject : C=BD, L=Dhaka, OU=Mobile, O=BalaSpeak, CN=BalaSpeak
valid   : 2026-01-01 → 2056-01-01
SHA-256 : C8:E5:66:7F:E6:23:DB:D6:CF:40:63:B3:A6:62:0B:9F:2C:A1:10:2C:96:62:07:F9:DD:82:B0:26:33:4F:81:3B
```

Check any APK you downloaded with:

```bash
keytool -printcert -jarfile BalaSpeak-v2.8-builtin-allvoices-arm64.apk
sha256sum BalaSpeak-v2.8-builtin-allvoices-arm64.apk     # compare with the release notes
```

## "App not installed" / "signature mismatch"

The old app was signed with a **different key** (v2.7 and older generated a throwaway key on
every build). Android refuses to replace an app with a differently signed one.

**Fix:** uninstall BalaSpeak first, then install the new APK.

```bash
adb uninstall com.aistudio.balaspeak.ytwqzn
adb install -r BalaSpeak-v2.8-builtin-allvoices-arm64.apk
```

## "There is no signature" / "Invalid package" (third-party APK installers, Android 7)

Older file managers and installers only read the **v1 (JAR)** signature. v2.7 was v2-only, so
those tools called it signatureless. v2.8 signs with v1 + v2 + v3 — installers that only
understand v1 now see a valid signature.

If an installer still refuses, install with the system package installer instead (Files app →
tap the APK), or use `adb install`.

## "App not installed as package appears to be invalid" / parse error

Almost always a **truncated download** — the built-in-voices APK is 349 MB. Verify:

```bash
sha256sum BalaSpeak-v2.8-builtin-allvoices-arm64.apk
# must equal the SHA-256 in the release notes / dist/BUILTIN-INFO.txt
```

Download it over a stable connection (browser download, not a flaky mobile session).

## Play Protect blocks the install

Sideloaded apps from an unknown developer can be flagged. Choose **Install anyway**, or
temporarily disable Play Protect → Play Store → profile → Play Protect → settings.

## App installs but crashes on launch

* Use `BalaSpeak-v2.8-debug-arm64.apk` from the **v2.8-slim** release. It is unminified, so if
  the minified build was broken by R8 the debug one will start.
* The built-in-voices APK is already built **without R8** (`-PnoMinify`), so shrinking cannot
  be the cause there.
* Collect the crash log — this is the fastest path to a real fix:

```bash
adb logcat -d | grep -iE "AndroidRuntime|balaspeak|FATAL" | tail -50
```

## Storage / first launch

The built-in-voices APK needs roughly **700 MB free** (349 MB APK + ~294 MB unpacked once).
The first launch unpacks the models (30–60 s); if storage is tight the unpack fails and no
voice plays. The slim APK needs only ~50 MB plus whatever packs you download in the app.

## Which file should I install?

| Release | Asset | Size | When |
|---|---|---|---|
| `v2.8-builtin` | `BalaSpeak-v2.8-builtin-allvoices-arm64.apk` | 349 MB | Want all voices offline, no downloads |
| `v2.8-slim` | `BalaSpeak-v2.8-release-arm64.apk` | ~50 MB | Want a small app, download packs on demand |
| `v2.8-slim` | `BalaSpeak-v2.8-debug-arm64.apk` | ~70 MB | Troubleshooting a crash |
