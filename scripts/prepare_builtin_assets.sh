#!/usr/bin/env bash
# Prepares the fully-offline assets tree for the built-in-voices APK.
#
# The phone should never have to run bzip2 or juggle 300+ MB of temporary files,
# so the archives are unpacked HERE, on the build machine, and land in assets:
#
#   app/src/main/assets/tts/bn/model.onnx                     (plain file already)
#   app/src/main/assets/tts/extracted/hi/{pratham,rohan,priyamvada}/...
#   app/src/main/assets/tts/extracted/en/...
#
# NeuralVoiceManager.ensurePacksInstalled() sees assets/tts/extracted/<lang> and
# just copies that tree into filesDir — no download, no extraction, no failure
# mode. The .tar.bz2 files are deleted afterwards so the APK does not carry the
# models twice.
#
# Usage (project root):  bash scripts/prepare_builtin_assets.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets"
EXP="$ASSETS/tts/extracted"

# 1. download the archives (skips files already present)
bash "$ROOT/scripts/fetch_builtin_voices.sh"

# 2. unpack them into assets/tts/extracted/<lang>/...
rm -rf "$EXP"
mkdir -p "$EXP/en" "$EXP/hi"

# extract <archive.tar.bz2> <dest-dir> — unpacks and hoists a lone top-level dir
extract() {
  local archive="$1" dest="$2" tmp src
  tmp="$(mktemp -d)"
  echo "… extracting $(basename "$archive") ($(numfmt --to=iec "$(stat -c %s "$archive")"))"
  tar -xjf "$archive" -C "$tmp"

  src="$tmp"
  mapfile -d '' tops < <(find "$tmp" -mindepth 1 -maxdepth 1 -print0)
  if [ "${#tops[@]}" -eq 1 ] && [ -d "${tops[0]}" ]; then
    src="${tops[0]}"            # archive wrapped everything in one folder
  fi

  mkdir -p "$dest"
  ( shopt -s dotglob nullglob; mv "$src"/* "$dest"/ )
  rm -rf "$tmp"
  echo "  → ${dest#"$ASSETS"/}  ($(find "$dest" -type f | wc -l) files, $(du -sh "$dest" | cut -f1))"
}

for voice in pratham rohan priyamvada; do
  archive="$ASSETS/tts/hi/$voice/vits-piper-hi_IN-$voice-medium-int8.tar.bz2"
  [ -f "$archive" ] || { echo "ERROR: $archive missing" >&2; exit 1; }
  extract "$archive" "$EXP/hi/$voice"
done

en_archive="$ASSETS/tts/en/kokoro-int8-multi-lang-v1_0.tar.bz2"
[ -f "$en_archive" ] || { echo "ERROR: $en_archive missing" >&2; exit 1; }
extract "$en_archive" "$EXP/en"

# 3. assert the files the app looks for (same names as isInstalledUncached())
need() {
  local dir="$1"; shift
  for name in "$@"; do
    if ! find "$dir" -name "$name" -print -quit | grep -q .; then
      echo "ERROR: '$name' not found under ${dir#"$ASSETS"/}" >&2
      exit 1
    fi
  done
}

need "$EXP/en" model.int8.onnx voices.bin tokens.txt lexicon-us-en.txt espeak-ng-data
for voice in pratham rohan priyamvada; do
  need "$EXP/hi/$voice" '*.onnx' tokens.txt espeak-ng-data
done
need "$ASSETS/tts/bn" model.onnx

# 4. hand the unpacked models to the *installer*: they go into jniLibs, so the
#    package manager extracts them into /data/app/…/lib/arm64 at install time and
#    the app never has to copy 414 MB on first launch.
JNI="$ROOT/app/src/main/jniLibs/arm64-v8a"
MANIFEST="$ASSETS/tts/libmap.tsv"
rm -rf "$JNI"
mkdir -p "$JNI"
: > "$MANIFEST"
n=0

# pack <lang> <src-root>  — every file under src-root becomes libvNNNN.so
pack() {
  local lang="$1" srcroot="$2" f rel lib
  while IFS= read -r -d '' f; do
    rel="${f#"$srcroot"/}"
    lib=$(printf 'libv%04d.so' "$n")
    cp "$f" "$JNI/$lib"
    printf '%s\t%s\t%s\n' "$lang" "$rel" "$lib" >> "$MANIFEST"
    n=$((n + 1))
  done < <(find "$srcroot" -type f -print0 | sort -z)
}

pack en "$EXP/en"
pack hi "$EXP/hi"                       # includes pratham/ rohan/ priyamvada/
lib=$(printf 'libv%04d.so' "$n")
cp "$ASSETS/tts/bn/model.onnx" "$JNI/$lib"
printf 'bn\tmodel.onnx\t%s\n' "$lib" >> "$MANIFEST"
n=$((n + 1))

# 5. assets now only carry the (tiny) manifest + bn vocab
rm -rf "$EXP" "$ASSETS/tts/bn/model.onnx"
rm -f "$ASSETS"/tts/hi/*/*.tar.bz2 "$ASSETS"/tts/en/*.tar.bz2
rmdir "$ASSETS/tts/bn" 2>/dev/null || true
if find "$ASSETS" -name '*.tar.bz2' -print -quit | grep -q .; then
  echo "ERROR: archives still present in assets" >&2
  exit 1
fi

# 6. sanity: one lib per manifest line, unique names, sane relative paths
lines=$(grep -c . "$MANIFEST")
libs=$(find "$JNI" -name 'libv*.so' | wc -l)
[ "$lines" -eq "$libs" ] || { echo "ERROR: manifest $lines lines vs $libs libs" >&2; exit 1; }
[ "$(cut -f3 "$MANIFEST" | sort -u | wc -l)" -eq "$lines" ] || { echo "ERROR: duplicate lib names" >&2; exit 1; }
if awk -F'\t' '{print $2}' "$MANIFEST" | grep -qE '^\.\./|^/'; then
  echo "ERROR: unsafe relative path in manifest" >&2
  exit 1
fi
for lang in en hi bn; do
  grep -q "^$lang	" "$MANIFEST" || { echo "ERROR: no entries for $lang" >&2; exit 1; }
done

echo "──────────────────────────────────────────────"
echo "jniLibs:  $(du -sh "$JNI" | cut -f1), $libs model libs"
echo "manifest: $(du -sh "$MANIFEST" | cut -f1), $lines entries"
echo "largest model libs:"
find "$JNI" -name 'libv*.so' -printf '%10s  %f\n' | sort -rn | head -6
echo "per language:"
for lang in bn en hi; do
  echo "  $lang: $(grep -c "^$lang	" "$MANIFEST") files"
done
