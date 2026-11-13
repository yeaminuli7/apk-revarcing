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

# 4. drop the archives — the extracted tree replaces them
rm -f "$ASSETS"/tts/hi/*/*.tar.bz2 "$ASSETS"/tts/en/*.tar.bz2
if find "$ASSETS" -name '*.tar.bz2' -print -quit | grep -q .; then
  echo "ERROR: archives still present in assets" >&2
  exit 1
fi

echo "──────────────────────────────────────────────"
echo "assets/tts: $(du -sh "$ASSETS/tts" | cut -f1), $(find "$ASSETS/tts" -type f | wc -l) files"
echo "largest files:"
find "$ASSETS/tts" -type f -printf '%10s  %P\n' | sort -rn | head -8
echo "extracted tree:"
find "$EXP" -maxdepth 3 -type d | sed "s|$ASSETS/||" | sort
