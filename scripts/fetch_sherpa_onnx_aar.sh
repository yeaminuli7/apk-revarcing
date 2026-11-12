#!/usr/bin/env bash
# Downloads the sherpa-onnx Android AAR (offline neural TTS engine) into app/libs/.
# Run once from the project root:  bash scripts/fetch_sherpa_onnx_aar.sh
set -euo pipefail

VERSION="1.13.8"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/app/libs"
OUT_FILE="$OUT_DIR/sherpa-onnx-$VERSION.aar"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VERSION/sherpa-onnx-$VERSION.aar"

mkdir -p "$OUT_DIR"
if [ -f "$OUT_FILE" ]; then
  echo "✓ $OUT_FILE already present ($(du -h "$OUT_FILE" | cut -f1))"
  exit 0
fi

echo "Downloading sherpa-onnx $VERSION AAR (~48 MB)…"
curl -fL --progress-bar "$URL" -o "$OUT_FILE"
echo "✓ Saved to $OUT_FILE"
