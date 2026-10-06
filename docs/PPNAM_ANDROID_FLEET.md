# PPNAM Android — Scanner Apps & Deployment

Six Android apps for Chainway C72 scanners, deployed as one bundle:

| Project | Package | Role |
|---|---|---|
| `PPNAM_Launcher_AA` | `com.mitas.ppnam.launcheraa` | Kiosk launcher, **device owner**, home screen |
| `PPNAM_Station_1_AA` | `com.mitas.ppnam.station1aa` | Station 1 (offload / tag assignment) |
| `PPNAM_Station_2_AA` | `com.mitas.ppnam.station2aa` | Station 2 |
| `PPNAM_Station_3_AA` | `com.mitas.ppnam.station3aa` | Station 3 (placeholder UI) |
| `PPNAM_Station_4_AA` | `com.mitas.ppnam.station4aa` | Station 4 |
| `PPNAM_Station_5_AA` | `com.mitas.ppnam.station5aa` | Station 5 (placeholder UI) |

**MQTT:** all station apps follow the base standard in
[`MQTT_BASE_README.md`](https://github.com/Systems-One/PPNAM_Server/blob/main/docs/mqtt/MQTT_BASE_README.md) (PPNAM_Server, `docs/mqtt/`) (topic structure, derived device
ids, presence/LWT, message organisation, schema 4.1 SCRAM login), modeled on
Station 1.

The launcher is the Android **device owner** on every scanner. It enforces
kiosk lock-task mode, whitelists the five station apps (plus the Chainway
keyboard wedge/scanner services and Settings), and provides a supervisor
PIN panel to enter/exit kiosk.

---

## Current state (as of 2026-08-31)

- All six apps build as **debug APKs** (`gradlew assembleDebug` per project).
- Three scanners provisioned and verified in kiosk mode.
- Each project is its own git repo under the **`Systems-One`** GitHub org
  (⚠ currently **public** — consider making them private; they are client
  apps). Station 2's deployable branch is `feat/jc-driven-mixing`
  (`master` still has the old `com.ppnam.station2aa` package id).
- Every repo has `.github/workflows/release.yml`: pushing a `v*` tag
  builds the APK and publishes it as a GitHub Release. CI signs with the
  shared debug keystore (secret `DEBUG_KEYSTORE_B64`) so releases stay
  signature-compatible with the apps already on the scanners.
- `PPNAM_Provisioning\` holds the on-site tool: `provision.ps1` (fresh
  scanner → kiosk device) and `update.ps1` (pull latest releases,
  `adb install -r`), plus an IT-person runbook. Scripts auto-download
  adb and need no dev tools on the central computer.

## Provisioning a NEW scanner today (manual recipe)

Device owner can only be set on a factory-fresh device with **no accounts
added** — do this before anyone signs into Google.

1. Unbox; skip through the setup wizard **without adding any account**.
2. Enable Developer Options (tap Build Number 7×) and USB debugging; connect USB; accept the RSA prompt.
3. From this directory (adb lives at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`):

   ```powershell
   $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
   foreach ($a in "PPNAM_Launcher_AA","PPNAM_Station_1_AA","PPNAM_Station_2_AA","PPNAM_Station_3_AA","PPNAM_Station_4_AA","PPNAM_Station_5_AA") {
     & $adb install -r "$a\app\build\outputs\apk\debug\app-debug.apk"
   }
   & $adb shell dpm set-device-owner com.mitas.ppnam.launcheraa/.KioskDeviceAdminReceiver
   & $adb shell am start -n com.mitas.ppnam.launcheraa/.MainActivity
   ```

4. Verify: `adb shell dumpsys activity activities | findstr mLockTaskModeState` should show `LOCKED`.

**Updating an already-provisioned scanner:** just `adb install -r` the new
APKs — package names and signatures match, so data and device ownership
are preserved. Never uninstall the launcher on a provisioned device
(that orphans device ownership; recovery is a factory reset).

---

## DEPLOYMENT PIPELINE (implemented 2026-08-31; three steps outstanding)

The 2026-08-28 plan was implemented with one change: instead of a
monorepo, the six existing per-project repos under the `Systems-One` org
are used. Each repo releases independently; the on-site tool always
pulls the **latest release of each repo**, so tag all six together when
shipping a coordinated change.

**How it works:**

- **Release**: push a `v*` tag in a repo → GitHub Actions builds
  `assembleDebug` signed with the shared debug keystore (secret
  `DEBUG_KEYSTORE_B64` = base64 of `%USERPROFILE%\.android\debug.keystore`
  on Jonathan's PC) → APK is attached to a GitHub Release named after
  the tag. Debug signing is deliberate for now: it matches the signature
  on every provisioned scanner, so updates install seamlessly.
- **On-site**: the customer's IT person clones/copies `PPNAM_Provisioning`
  onto the central computer and runs `provision.ps1` (new scanner) or
  `update.ps1` (existing scanners). See its README.md runbook.

**Verified 2026-08-31:** `v1.0.0` released on all six repos; every APK
downloaded from GitHub carries the shared debug cert
(SHA-256 `a704ae09…`), matching the provisioned scanners. CI refuses to
release an APK signed with anything else (the first builds silently
re-signed with a runner-generated keystore — caught and fixed by
pinning `ANDROID_USER_HOME` and adding the signature gate).

**Still outstanding:**

1. Publish `PPNAM_Provisioning` to GitHub. `Jwagener1` cannot create
   repos in the Systems-One org — create the empty private repo in the
   org web UI (or grant Jwagener1 rights), then from `PPNAM_Provisioning`:
   `git push -u origin master` (remote already configured).
2. One live `update.ps1` run against a scanner before handing the tool
   to the IT person (download path already tested; no device was
   connected during setup).

**Release-signing migration (later, recommended):** generate a proper
release keystore (backed up in ≥2 places off this PC — losing it means
factory-resetting every scanner), add `signingConfigs.release` to all six
apps, switch CI to `assembleRelease`, and plan one migration visit: the
signature change requires uninstall/reinstall of the station apps and
launcher ownership migration via `transferOwnership`. Also consider
making the six repos **private** (they are currently public); the
provisioning tool already supports a token via `github_token.txt`.

**Later / optional:**

- QR-code provisioning (no-cable enrollment of new scanners; launcher
  gains a first-run step that silently installs the station apps as
  device owner).
- Launcher self-update: as device owner it can silently install updates
  pulled from the Release feed, removing the USB step for app updates.
- Fill in Station 3 and Station 5 real UIs (currently placeholders).

## Device notes

- Provisioned scanners so far: two Chainway C72 (2026-08-26) + one more
  (`HC720DE260100322`, 2026-08-28; legacy packages `com.mitas.ppnam.station1`,
  `com.ppnam.station2aa`, `com.ppnam.station4aa` were removed from it).
- Supervisor PIN is shared across launcher and stations; 5-attempt/30 s lockout.
- Kiosk whitelist lives in the launcher (`KioskApps`): launcher + 5 stations
  + `com.rscja.infowedge` + `com.rscja.scanner` + `com.android.settings`.
