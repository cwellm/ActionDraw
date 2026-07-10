# ActionDraw — ArchLinux packaging & installation

How to build the pacman package (`.pkg.tar.zst`) and install/run/uninstall it. Every step lists
what you should see, so you can verify as you go.

---

## What you get
- A single file: **`actiondraw-1.0.0-1-x86_64.pkg.tar.zst`**.
- Self-contained: it bundles its own Java runtime, so there is **no `java`/JRE dependency** at
  runtime (only `hicolor-icon-theme`).
- Installs a self-contained app image under `/opt/actiondraw`, a launcher on `PATH`, a desktop
  entry, and hicolor icons — so it appears in your application menu (Graphics).

Installed layout:

| Path | Contents |
|---|---|
| `/opt/actiondraw/` | app image + bundled JRE (launcher at `/opt/actiondraw/bin/ActionDraw`) |
| `/usr/bin/actiondraw` | small wrapper that execs the launcher |
| `/usr/share/applications/actiondraw.desktop` | menu entry |
| `/usr/share/icons/hicolor/<size>/apps/actiondraw.png` | icons (same logo as Windows) |

---

## Build

jpackage cannot emit pacman packages, so a `PKGBUILD` (in `packaging/arch/`) packages the
Compose `createDistributable` app image. Pick **A** (Docker — works on Windows/macOS/Linux) or
**B** (native Arch).

### A. Via Docker  *(recommended on Windows)*
Prerequisite: a working Docker with a Linux engine. On Windows that means Docker Desktop with the
**WSL2** backend running (see *WSL2 / Docker setup* below if `docker ps` fails).

From the repository root:
```bash
docker run --rm -v "${PWD}:/src" archlinux bash /src/packaging/arch/build-in-docker.sh
```
(PowerShell: use `"${PWD}:/src"` as-is.)

The script installs `base-devel jdk17-openjdk librsvg icoutils`, builds as an unprivileged user,
regenerates the icons, and runs `makepkg`.

**Output:** `packaging/arch/dist/actiondraw-1.0.0-1-x86_64.pkg.tar.zst`

### B. Native Arch (or WSL-Arch)
Prerequisites: `base-devel` and `jdk17-openjdk`.
```bash
cd packaging/arch
makepkg -f                 # build; add -si to build AND install
```
**Output:** `packaging/arch/actiondraw-1.0.0-1-x86_64.pkg.tar.zst`

### Verify the artifact
```bash
ls -lh packaging/arch/dist/*.pkg.tar.zst        # (Docker) or packaging/arch/*.pkg.tar.zst (native)
pacman -Qip packaging/arch/dist/actiondraw-1.0.0-1-x86_64.pkg.tar.zst   # metadata
tar tf packaging/arch/dist/actiondraw-1.0.0-1-x86_64.pkg.tar.zst | grep -E 'opt/|usr/bin|desktop|hicolor' | head
```
`pacman -Qip` should report Name `actiondraw`, Version `1.0.0-1`, and the `.tar tf` listing should
show `opt/actiondraw/bin/ActionDraw`, `usr/bin/actiondraw`, the `.desktop`, and icon paths.

---

## Install
```bash
sudo pacman -U actiondraw-1.0.0-1-x86_64.pkg.tar.zst
```

## Run
```bash
actiondraw            # from a terminal
```
…or launch **ActionDraw** from your desktop application menu (Graphics category).

## Uninstall
```bash
sudo pacman -R actiondraw
```

---

## WSL2 / Docker setup (Windows)
If `docker ps` fails with *"daemon is not running"* and `wsl --status` shows **no installed
distribution**, set up WSL2 first (one-time, needs admin + a reboot):
```powershell
wsl --install            # installs the WSL2 kernel + a default distro; reboot when asked
```
Then start **Docker Desktop** → *Settings → Resources → WSL integration* → enable it. Confirm with:
```powershell
docker run --rm hello-world
```
Once that works, re-run the build command in section A.

## Troubleshooting
- **`makepkg` refuses to run as root:** by design. The Docker script builds as an unprivileged
  `builder` user; natively, run `makepkg` as your normal user (it calls `sudo` only for deps).
- **Gradle download errors:** the build fetches Gradle + Compose dependencies on first run; ensure
  the container/host has internet.
- **No JDK 17 found by Gradle:** the package sets `JAVA_HOME=/usr/lib/jvm/java-17-openjdk`
  (from `jdk17-openjdk`); install that package if building natively.
- **Icon/menu not refreshed after install:** handled by `actiondraw.install`
  (`gtk-update-icon-cache` / `update-desktop-database`); log out/in if your DE caches aggressively.

## Publishing to the AUR (later)
The current `PKGBUILD` builds from the in-repo checkout (`$startdir/../..`), which is ideal for
first-party/CI builds. For the AUR, switch `source=()` to a versioned git tag or release tarball,
fill in `sha256sums`, and bump `pkgrel` on packaging-only changes.
