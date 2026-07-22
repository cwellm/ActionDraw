# Packaging ActionDraw

Detailed, step-by-step guides (with verification checkpoints):
- **[docs/Packaging-Windows.md](../docs/Packaging-Windows.md)** — build & install the `.msi`
- **[docs/Packaging-ArchLinux.md](../docs/Packaging-ArchLinux.md)** — build & install the `.pkg.tar.zst`

This file is the short overview.

One master logo — `art/actiondraw.svg` — drives every icon.

## Icons

`./gradlew genIcons` renders the SVG into `art/icons/` using Skiko (the Skia engine
already bundled with Compose) — **no external tools, any OS**:

- `actiondraw.ico` — Windows, multi-resolution 16–256 px
- `actiondraw-{16..512}.png` — Linux hicolor sizes

(These are committed, so a package build doesn't strictly need to regenerate them.
`packaging/gen-icons.sh` is a librsvg-based equivalent used inside the Arch container.)

## Windows — `.msi`

Prerequisites: Windows + JDK 17 (Gradle toolchain). The Compose plugin downloads
its own WiX 3 automatically — **no manual WiX install needed**.

```powershell
.\gradlew.bat genIcons      # once, or whenever the logo changes
.\gradlew.bat packageMsi
```

Output: `build/compose/binaries/main/msi/ActionDraw-1.0.0.msi` (~52 MB, bundled JRE).

## ArchLinux — `.pkg.tar.zst` (built via Docker, works on Windows)

jpackage cannot emit pacman packages, so a `PKGBUILD` packages a self-contained
app image (bundled JRE) into `/opt/actiondraw` with a launcher, `.desktop` entry
and hicolor icons. It builds inside an `archlinux` container:

```powershell
docker run --rm -v "${PWD}:/src" archlinux bash /src/packaging/arch/build-in-docker.sh
```

Output: `packaging/arch/dist/actiondraw-1.0.0-1-x86_64.pkg.tar.zst`

Install on Arch:

```bash
sudo pacman -U actiondraw-1.0.0-1-x86_64.pkg.tar.zst
```

> Requires a working Docker/WSL2 (or a native Arch box). If Docker's WSL2 backend
> isn't set up, run `wsl --install` and enable Docker Desktop's WSL integration first.

## Build-environment notes
- The `.msi` builds only on Windows; the Arch package only on Arch (makepkg, here
  via Docker). No native cross-build — but both use the same SVG logo.
- The app image bundles its own Java runtime, so end users need no JDK/JRE.
- `PKGBUILD` builds from the in-repo checkout (`$startdir/../..`); for AUR/release,
  switch `source=()` to a git/tarball source and add checksums.
