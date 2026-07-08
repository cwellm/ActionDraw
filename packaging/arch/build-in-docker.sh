#!/usr/bin/env bash
# Build the ArchLinux package inside an archlinux container (works on Windows/Docker Desktop).
# Usage (from the repo root):
#   docker run --rm -v "${PWD}:/src" archlinux bash /src/packaging/arch/build-in-docker.sh
# Output: packaging/arch/dist/actiondraw-*.pkg.tar.zst
set -euo pipefail

echo "==> Installing build tools"
pacman -Syu --noconfirm --needed base-devel jdk17-openjdk git librsvg icoutils

# makepkg refuses to run as root; build as an unprivileged user with a copy of the repo.
id builder &>/dev/null || useradd -m builder
cp -a /src /home/builder/ActionDraw
chown -R builder:builder /home/builder/ActionDraw

echo "==> Generating icons from the SVG"
su builder -s /bin/bash -c 'bash ~/ActionDraw/packaging/gen-icons.sh'

echo "==> makepkg"
su builder -s /bin/bash -c 'cd ~/ActionDraw/packaging/arch && makepkg -f --noconfirm'

echo "==> Collecting artifacts"
mkdir -p /src/packaging/arch/dist
cp /home/builder/ActionDraw/packaging/arch/*.pkg.tar.zst /src/packaging/arch/dist/
# Also surface the generated icons back to the host for the Windows/MSI build.
mkdir -p /src/art/icons
cp /home/builder/ActionDraw/art/icons/* /src/art/icons/ 2>/dev/null || true

echo "==> Done:"
ls -1 /src/packaging/arch/dist/
