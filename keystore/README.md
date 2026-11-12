# Release signing key

`balaspeak-release.p12` — PKCS#12 keystore (alias `upload`, RSA 2048, valid to 2056),
password in `password.txt`.

## Why this is committed

Every earlier build generated a **throwaway** key inside the CI job, so each new APK had a
different signature. Android refuses to install an APK whose signature differs from the
installed app ("App not installed" / "signature mismatch") and some installers reject
v2-only APKs as "no signature". A single stable key fixes both.

## What this means

* This is a **public sideload key** — anyone with this repository can sign an APK that
  Android will accept as an update of BalaSpeak. That is acceptable for a sideloaded,
  open-source app distributed from this repo's Releases page, but it is **not** suitable
  for Google Play.
* **Before publishing on Play:** generate your own keystore, keep it private (never commit
  it), and build with `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_ALIAS` environment variables
  or repository secrets instead.

```bash
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload \
        -keyalg RSA -keysize 2048 -validity 10950 \
        -dname "CN=BalaSpeak, O=BalaSpeak, L=Dhaka, C=BD"
```
