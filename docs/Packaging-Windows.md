# ActionDraw — Windows packaging & installation

How to build the Windows installer (`.msi`) and install/run/uninstall it. Every step lists
what you should see, so you can verify as you go.

---

## What you get
- A single file: **`ActionDraw-1.0.0.msi`** (~52 MB).
- Self-contained: it bundles its own Java runtime, so end users need **no JDK/JRE**.
- **Per-user install** (no administrator rights required), a Start-menu shortcut under
  **ActionDraw**, and in-place upgrades for future versions (fixed upgrade GUID).

---

## Prerequisites (build machine only)
- Windows 10/11, 64-bit.
- **JDK 17** (e.g. [Temurin 17](https://adoptium.net/)) — building runs Gradle, which uses it
  and its bundled `jpackage`.
- Internet on first build: the Compose Gradle plugin **downloads its own WiX 3 automatically**
  (task `:downloadWix`). **You do not need to install WiX yourself.**

> End users who only *install* the `.msi` need none of the above.

---

## Build

From the repository root:

```powershell
.\gradlew.bat genIcons      # 1. render art/icons/* from art/actiondraw.svg (Skiko; no extra tools)
.\gradlew.bat packageMsi    # 2. build the installer
```

Expected tail of step 2:

```
> Task :downloadWix
> Task :packageMsi
The distribution is written to ...\build\compose\binaries\main\msi\ActionDraw-1.0.0.msi
BUILD SUCCESSFUL
```

**Output:** `build\compose\binaries\main\msi\ActionDraw-1.0.0.msi`

### Verify the artifact
```powershell
Get-Item .\build\compose\binaries\main\msi\ActionDraw-1.0.0.msi |
    Select-Object Name, @{n='MB';e={[math]::Round($_.Length/1MB,2)}}
```
You should see `ActionDraw-1.0.0.msi` at roughly **52 MB**.

Optional — read the product metadata straight from the MSI:
```powershell
$w = New-Object -ComObject WindowsInstaller.Installer
$db = $w.GetType().InvokeMember('OpenDatabase','InvokeMethod',$null,$w,@((Resolve-Path .\build\compose\binaries\main\msi\ActionDraw-1.0.0.msi).Path,0))
$v = $db.GetType().InvokeMember('OpenView','InvokeMethod',$null,$db,@("SELECT Property,Value FROM Property"))
$v.GetType().InvokeMember('Execute','InvokeMethod',$null,$v,$null)
while ($r = $v.GetType().InvokeMember('Fetch','InvokeMethod',$null,$v,$null)) {
    '{0} = {1}' -f $r.GetType().InvokeMember('StringData','GetProperty',$null,$r,@(1)),
                   $r.GetType().InvokeMember('StringData','GetProperty',$null,$r,@(2))
}
```
Expect `ProductName = ActionDraw`, `ProductVersion = 1.0.0`, `Manufacturer = creaflect`.

---

## Install
- **GUI:** double-click `ActionDraw-1.0.0.msi`. You can pick the install folder (the installer
  offers a directory chooser). No UAC/admin prompt (per-user).
- **Command line:**
  ```powershell
  msiexec /i ActionDraw-1.0.0.msi          # interactive
  msiexec /i ActionDraw-1.0.0.msi /qn      # silent
  ```

After install you'll have an **ActionDraw** entry in the Start menu (and in
Settings → Apps → Installed apps).

## Run
- Start menu → **ActionDraw**, or launch `ActionDraw.exe` from the folder you installed into.
- First run: pick a reference-image folder and start a session (see the app's own controls).

## Uninstall
- **GUI:** Settings → Apps → *ActionDraw* → Uninstall.
- **Command line:**
  ```powershell
  msiexec /x ActionDraw-1.0.0.msi          # or /qn for silent
  ```

---

## Troubleshooting
- **"Windows protected your PC" / SmartScreen** on first run: the installer is **not code-signed**,
  so SmartScreen and some antivirus may warn. Choose *More info → Run anyway*. (Code-signing with an
  Authenticode certificate is the fix for a public release — a future step.)
- **`:downloadWix` fails:** no internet or a proxy blocks GitHub. Retry online, or pre-place WiX 3
  on `PATH`.
- **`packageMsi` can't find a JDK / toolchain 17:** install JDK 17 and set `JAVA_HOME`, then re-run.
- **Icon didn't change** after editing `art/actiondraw.svg`: re-run `.\gradlew.bat genIcons` before
  `packageMsi` (icons are generated, then committed under `art/icons/`).

## Notes
- Config lives in `build.gradle.kts` under `compose.desktop { … windows { … } }`
  (icon, `menuGroup`, `shortcut`, `perUserInstall`, `dirChooser`, `upgradeUuid`).
- The `.msi` is a build artifact (under `build/`, git-ignored); it is not committed. For a public
  release, attach it to a GitHub/GitLab Release (a CI workflow can automate this).
