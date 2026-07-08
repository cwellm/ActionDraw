#!/usr/bin/env bash
# Generate all icon assets from the single master SVG.
# Needs: librsvg (rsvg-convert) + icoutils (icotool). Run in the Docker container
# (see packaging/README.md) or on any Linux box with those tools.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
svg="$repo_root/art/actiondraw.svg"
out="$repo_root/art/icons"
mkdir -p "$out"

sizes=(16 24 32 48 64 128 256 512)
for sz in "${sizes[@]}"; do
    rsvg-convert -w "$sz" -h "$sz" "$svg" -o "$out/actiondraw-$sz.png"
done

# Windows multi-resolution .ico (16..256; 512 is not used inside .ico).
icotool -c -o "$out/actiondraw.ico" \
    "$out/actiondraw-16.png" "$out/actiondraw-24.png" "$out/actiondraw-32.png" \
    "$out/actiondraw-48.png" "$out/actiondraw-64.png" "$out/actiondraw-128.png" \
    "$out/actiondraw-256.png"

echo "Wrote icons to $out:"
ls -1 "$out"
