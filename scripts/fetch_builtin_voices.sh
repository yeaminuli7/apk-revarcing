#!/usr/bin/env bash
# Downloads every neural voice model into app/src/main/assets/tts/ so that the
# built-in-voices APK ships them inside the APK (no first-run download).
#
# Usage (from project root):  bash scripts/fetch_builtin_voices.sh
#
# ~294 MiB of models. NeuralVoiceManager picks them up automatically: each
# PackDownload already carries an `assetPath`, and ensurePacksInstalled() copies
# from assets instead of hitting the network when that file is present.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets"

# path relative to assets | minimum size in bytes | url
FILES=(
  "tts/bn/model.onnx|110000000|https://huggingface.co/sriram09764/itantra-tts-onnx/resolve/main/bn/model.onnx"
  "tts/hi/pratham/vits-piper-hi_IN-pratham-medium-int8.tar.bz2|19000000|https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-pratham-medium-int8.tar.bz2"
  "tts/hi/rohan/vits-piper-hi_IN-rohan-medium-int8.tar.bz2|19000000|https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium-int8.tar.bz2"
  "tts/hi/priyamvada/vits-piper-hi_IN-priyamvada-medium-int8.tar.bz2|19000000|https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-priyamvada-medium-int8.tar.bz2"
  "tts/en/kokoro-int8-multi-lang-v1_0.tar.bz2|125000000|https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2"
)

total=0
for entry in "${FILES[@]}"; do
  IFS='|' read -r rel min_size url <<< "$entry"
  dest="$ASSETS/$rel"
  mkdir -p "$(dirname "$dest")"

  ok=0
  if [ -f "$dest" ]; then
    have=$(stat -c %s "$dest")
    if [ "$have" -ge "$min_size" ]; then
      echo "✓ already present: $rel ($(numfmt --to=iec "$have"))"
      ok=1
    else
      echo "… incomplete ($have B), re-downloading $rel"
      rm -f "$dest"
    fi
  fi

  if [ "$ok" -eq 0 ]; then
    echo "↓ $rel  ($(numfmt --to=iec "$min_size")+)"
    curl -fL --retry 4 --retry-delay 3 --retry-all-errors \
         --connect-timeout 30 -C - -o "$dest.part" "$url" \
      || { echo "ERROR: download failed for $rel"; exit 1; }
    got=$(stat -c %s "$dest.part")
    if [ "$got" -lt "$min_size" ]; then
      echo "ERROR: $rel is $got bytes, expected at least $min_size"
      exit 1
    fi
    mv "$dest.part" "$dest"
    echo "  saved $(numfmt --to=iec "$got")"
  fi
  total=$((total + $(stat -c %s "$dest")))
done

echo "──────────────────────────────────────────────"
echo "Bundled voice models: $(numfmt --to=iec "$total") in $ASSETS/tts"
find "$ASSETS/tts" -type f -printf '%10s  %P\n' | sort -k2
