---
app_name: Launcher
file_stem: Launcher
station: All stations (home screen)
mark: "L"
accent: "#3A4655"
accent_tint: "#AEB8C4"
icon: images/icon.png
version: 1.5.0
date: 2026-11-06
device: Chainway C72
standfirst: >-
  How to open a station app from the PPNAM Station Launcher, what the home
  screen tells you about the handheld, and how a supervisor or IT locks it
  down, unlocks it and provisions it.
description: Standard operating procedure for the PPNAM Station Launcher 1.5.0 (kiosk home screen) on the Chainway C72.
crop: {top: 72, bottom: 126}
scan_sounds: false
revisions:
  - version: 1.5.0
    date: 2026-11-06
    change: "Suite release: visual refresh, consistency pass, scan sounds. First Launcher SOP."
---

<!-- Screenshots: v1.5.0 (8) clean debug build of master on the c72_clone2 emulator in airplane
     mode, with the five station apps installed from their own debug builds. The device-owner
     states were set with `dpm set-device-owner` on the emulator only and undone with the app's
     Remove Device Owner button; `dpm list-owners` printed "no owners" afterwards. -->

# Purpose and scope {#purpose}

The PPNAM Station Launcher is the **home screen** of every PPNAM Chainway C72 handheld. Operators use it to open the station app for their work. On a provisioned handheld it also **locks the device down** (kiosk mode), so only the PPNAM apps can be used.

## What it does

- Shows a tile for each station app (Station 1 to 5) and opens it with one tap.
- Shows when a station app is missing or disabled, so it can be reinstalled or enabled.
- Keeps the handheld in kiosk mode: it starts on the launcher, the Home button and closing an app always come back to it, the screen stays upright, and other apps and Recents are blocked.
- Lets a supervisor, with the PIN, turn kiosk off and on, open Android Settings, and remove the launcher's control before a handheld is decommissioned.

## What it does not do

- It does not sign you in. Each station app has its own sign-in.
- It does not connect to any station or broker, and it makes no scan sounds.
- It does not install or update the station apps. IT does that when provisioning.

> [!NOTE] Who this is for
> Every handheld user, for sections 1 to 5, 11 and 12. Sections marked **Supervisor** are for the shift supervisor (who holds the PIN) and IT, who provision the handhelds.

# Before you start {#before}

- [ ] The handheld is charged. Put it back on its charger at the end of the shift.
- [ ] You know which station app your work uses.
- [ ] For supervisors: you know the supervisor PIN. It is the same PIN as in the station apps' Settings.
- [ ] For IT: the PPNAM provisioning kit and a PC with the handheld connected by USB (section 8).

::: step The home screen
shot: 01_home_kiosk.png
caption: A locked-down handheld: five station tiles and nothing else.
alt: PPNAM Station Launcher home with the gear, and tiles for Station 1 to Station 5 in their pack colours
When the handheld starts, or when you press **Home**, you see the launcher: **PPNAM**, *Station Launcher · v1.5.0*, and a grid of station tiles. Each tile has the station's colour and number.
:::

# The header and lockdown notice {#status}

The launcher has no connection pill: it talks to no station. The top of the screen tells you which version is installed and whether the handheld is locked down.

::: step The header
shot: 01_home_kiosk.png
caption: Locked down: no notice under the header.
alt: Home header "PPNAM / Station Launcher · v1.5.0" with a gear, and no notice
- **PPNAM** and *Station Launcher · v1.5.0*: the launcher's version.
- The **gear** (Device lockdown) opens the supervisor PIN (section 4). Operators do not need it.
- **No notice** under the header means the handheld is locked down in kiosk mode. This is the normal state.
:::

When the handheld is **not** locked down, an amber strip with a warning sign says why:

::: shots
02_home_kiosk_off.png | Kiosk is off: a supervisor unlocked it. | Home with an amber notice "Kiosk is off: this scanner is unlocked"
03_home_not_provisioned.png | Not provisioned: kiosk cannot run. | Home with an amber notice "Not provisioned: kiosk unavailable"
:::

Section 9 lists what each notice means and who acts on it.

# The supervisor PIN {#signin}

Operators do not sign in to the launcher: you sign in inside each station app. The launcher has one gate, the **supervisor PIN**, behind the gear. It is the same PIN and the same rule as the station apps' Settings.

> [!SUPERVISOR] Keep the PIN to yourself
> Never tell an operator the PIN or type it where they can see. If an operator needs something behind it, do it for them.

::: step Enter the PIN
shot: 10_pin_empty.png
caption: The PIN box opens with the number keyboard.
alt: Dialog "Supervisor PIN" with a PIN field, Cancel and Unlock
1. Tap the **gear**. The *Supervisor PIN* box opens with the number keyboard.
2. Type the 6-digit PIN. The digits are hidden.
3. Tap **Unlock** (or Enter). The *Device Lockdown* panel opens (sections 7 and 8), and the supervisor tiles appear on the grid (section 10).

**Cancel** closes the box without using an attempt. Unlock with an empty box says *Enter the 6-digit PIN* and does not count.
:::

A wrong PIN says *Incorrect PIN. 4 attempts left before lockout.* After **five** wrong attempts the box locks for **30 seconds** and counts down (*Too many attempts. Try again in 29s.*). Closing the box or restarting the handheld does not reset the count.

::: shots
11_pin_wrong.png | A wrong PIN: four attempts left. | Supervisor PIN dialog with "Incorrect PIN. 4 attempts left before lockout."
12_pin_locked_out.png | Five wrong PINs: locked for 30 seconds. | Supervisor PIN dialog with "Too many attempts. Try again in 29s." and Unlock greyed out
:::

# Opening a station app {#workflow-stations}

::: step Tap your station
label: Step 1
Tap the tile of the station you work at, for example **Station 2**. The station app opens on its own Log In screen. Follow that station's SOP from there.
:::

::: step Back to the launcher
label: Step 2
When you are done, close the station app: from its Log In or home screen press **Back** and tap **Close** on *Close the app?*. Or press the **Home** button.

Either way you land back on the launcher. In kiosk mode nothing else can be opened, and Back on the launcher's grid does nothing.
:::

## A station tile is greyed out

A station app that is not ready keeps its tile, greyed out, with an amber warning and a status line. Tapping it does nothing.

::: shots
04_home_station_disabled.png | Disabled: the app is on the handheld but turned off. | Home grid with the Station 4 tile greyed out and "Disabled" under it
05_home_station_missing.png | Not installed: the app is missing. | Home grid with a greyed Station 4 placeholder, a warning badge and "Not installed"
:::

Table: Tile status
| Under the tile | What it means | What to do |
|---|---|---|
| *(nothing)* | The app is installed and ready. | Tap it. |
| Disabled | The app is on the handheld but turned off in Android. | Tell your supervisor. It is re-enabled in Android Settings; do not reinstall it. |
| Not installed | The app is not on the handheld. | Tell your supervisor. IT installs it. The tile works as soon as it is installed. |

# Kiosk mode {#workflow-kiosk}

On a provisioned handheld, kiosk mode is **on by default**. While it is on:

- The handheld starts on the launcher, and **Home** or closing any PPNAM app always comes back to it.
- Only the station apps (and, for supervisors, Android Settings and the Chainway Keyboard Emulator) can be opened. Recents and every other app are blocked.
- The status bar shows only the clock, Wi-Fi and battery, and cannot be pulled down.
- The whole handheld stays upright: auto-rotate is off and the screen is held in portrait.
- Safe boot, factory reset, adding users and installing apps from unknown sources are blocked, and there is no lock screen.

# Turning kiosk off and on {#workflow-lockdown}

> [!SUPERVISOR] Supervisors only
> Turn kiosk off only for a task that needs the rest of the handheld, and turn it back on before you hand the handheld back. An unlocked handheld shows *Kiosk is off: this scanner is unlocked* on the home screen until you do.

::: step Exit kiosk
label: Step 1
shot: 20_lockdown_kiosk_active.png
caption: The Device Lockdown panel while kiosk is on.
alt: Device Lockdown dialog "Kiosk is active: the device is confined to the apps on this launcher. Exiting frees Home, Recents and all other apps until kiosk is re-entered." with Remove Device Owner, Close and Exit Kiosk Mode
1. Tap the **gear** and enter the PIN. The *Device Lockdown* panel opens: *Kiosk is active: the device is confined to the apps on this launcher. Exiting frees Home, Recents and all other apps until kiosk is re-entered.*
2. Tap **Exit Kiosk Mode**.

The handheld is released at once: Android's own home screen may appear. Home, Recents and every app are free, and auto-rotate is back on.
:::

::: step Enter kiosk again
label: Step 2
shot: 21_lockdown_kiosk_off.png
caption: The panel while kiosk is off.
alt: Device Lockdown dialog "Kiosk is off: the device is unlocked. Entering kiosk confines the device to the apps on this launcher." with Remove Device Owner, Close and a green Enter Kiosk Mode button
1. Open the PPNAM Launcher (it shows *Kiosk is off: this scanner is unlocked*).
2. Tap the **gear** and enter the PIN. The panel says *Kiosk is off: the device is unlocked. Entering kiosk confines the device to the apps on this launcher.*
3. Tap **Enter Kiosk Mode**.

The handheld is locked down again straight away and the amber notice disappears. **Close** leaves the panel without changing anything.
:::

# Provisioning and decommissioning {#workflow-provisioning}

> [!SUPERVISOR] For IT
> These steps make the launcher the handheld's **device owner**, which kiosk mode needs, or take that away again. Do them only when commissioning or decommissioning a handheld.

::: step Provisioning a handheld
shot: 22_lockdown_not_owner.png
caption: On an unprovisioned handheld the panel shows the command to run.
alt: Device Lockdown dialog "This scanner has not been provisioned, so kiosk mode is not available. Run the PPNAM provisioning kit (PPNAM_Provisioning\provision.ps1) from a PC with the scanner connected. It runs:" and the command dpm set-device-owner com.mitas.ppnam.launcheraa/.KioskDeviceAdminReceiver
A handheld that is not provisioned shows *Not provisioned: kiosk unavailable* on the home screen. The *Device Lockdown* panel says:

*This scanner has not been provisioned, so kiosk mode is not available. Run the PPNAM provisioning kit (PPNAM_Provisioning\provision.ps1) from a PC with the scanner connected. It runs:*

`dpm set-device-owner com.mitas.ppnam.launcheraa/.KioskDeviceAdminReceiver`

1. Connect the handheld to the provisioning PC by USB, with USB debugging on.
2. Run the provisioning kit. It installs the apps and makes the launcher the device owner. Android only accepts a device owner on a handheld with no accounts added, so start from a freshly reset handheld.
3. The kit also allows the launcher to change system settings, which it needs to hold the screen upright. Without that, kiosk still works but rotation is left alone.
4. Open the launcher. The amber notice disappears and kiosk is on.

The command line in the panel scrolls sideways and can be selected and copied.
:::

::: step Removing the device owner
shot: 23_remove_owner_confirm.png
caption: Removing the device owner asks first.
alt: Dialog "Remove Device Owner?" with "Kiosk mode is switched off permanently and the PPNAM Launcher gives up control of this scanner, so the apps can be uninstalled. Putting it back into service requires provisioning it again." and Cancel and Remove
To decommission or re-provision a handheld (a device owner cannot be uninstalled, and kiosk blocks factory reset):

1. Tap the **gear**, enter the PIN, and tap **Remove Device Owner** in the panel.
2. Read *Remove Device Owner?* and tap **Remove**.

Kiosk is turned off, the factory-reset and other blocks are lifted, and the launcher gives up control. The home screen shows *Not provisioned: kiosk unavailable*. To use the handheld again, provision it again.
:::

# Messages and what to do {#messages}

Every message the launcher shows, word for word. The two kiosk-state texts of the Device Lockdown panel are quoted in section 7.

Table: Home screen
| Message | What it means | What to do |
|---|---|---|
| Kiosk is off: this scanner is unlocked | A supervisor turned kiosk off. | Supervisor: enter kiosk again (section 7). |
| Not provisioned: kiosk unavailable | The launcher is not the device owner. | IT: provision the handheld (section 8). |
| Disabled (under a tile) | The app is installed but turned off. | Supervisor: enable it in Android Settings. |
| Not installed (under a tile) | The app is missing. | IT: install it. |

Table: Supervisor PIN
| Message | What it means | What to do |
|---|---|---|
| Enter the 6-digit PIN | Unlock was tapped with an empty box. | Type the PIN. |
| Incorrect PIN. *N* attempts left before lockout. | Wrong PIN. | Supervisor only. Check the PIN. |
| Too many attempts. Try again in *N*s. | Five wrong PINs lock the box for 30 seconds. | Wait for the countdown. |

Table: Device Lockdown
| Message | What it means | What to do |
|---|---|---|
| This scanner has not been provisioned, so kiosk mode is not available. Run the PPNAM provisioning kit (PPNAM_Provisioning\provision.ps1) from a PC with the scanner connected. It runs: | Not provisioned. | IT: run the provisioning kit. |
| Remove Device Owner? Kiosk mode is switched off permanently and the PPNAM Launcher gives up control of this scanner, so the apps can be uninstalled. Putting it back into service requires provisioning it again. | Confirms decommissioning. | **Remove** only when decommissioning. |

# Settings {#settings}

The launcher has no settings of its own. Behind the supervisor PIN are the *Device Lockdown* panel (sections 7 and 8) and two supervisor tiles.

::: step Supervisor tiles
shot: 13_home_supervisor.png
caption: After the PIN, the Settings tile appears.
alt: Home grid with the five station tiles and a Settings tile marked "Supervisor"
After you unlock with the PIN, the grid also shows, marked **Supervisor**:

- **Settings**: Android Settings, for Wi-Fi, date and time, and enabling a disabled app.
- **Keyboard Emulator**: the Chainway scan-service app, to check the scanner set-up. It shows only where that app is installed.

The supervisor tiles disappear again as soon as the launcher leaves the screen, for example when you open an app or the screen turns off. Both can be opened in kiosk mode.
:::

> [!SUPERVISOR] Station settings are in the station apps
> Broker settings, auto sign-out and the station diagnostics live in each station app's own Settings screen, behind the same PIN. See that station's SOP.

# Finishing up {#finishing}

1. Close the station app (Back, then **Close**), or press **Home**. You are back on the launcher.
2. Check that no amber notice shows under the header. If *Kiosk is off* shows, tell your supervisor.
3. Put the handheld on its charger at the end of the shift.

> [!SUPERVISOR] Before handing a handheld back
> If you turned kiosk off, turn it back on (section 7). Leave the PIN box and the supervisor tiles closed: they close by themselves when the screen turns off.

# Troubleshooting {#troubleshooting}

Table: Common problems
| Problem | Likely cause | What to do |
|---|---|---|
| A station tile is greyed out with *Not installed*. | The app was never installed or was removed. | IT installs the app. The tile works as soon as it is installed. |
| A station tile is greyed out with *Disabled*. | The app was turned off in Android. | Supervisor: enable it in Android Settings. Do not reinstall it. |
| *Kiosk is off: this scanner is unlocked* on the home screen. | A supervisor exited kiosk. | Supervisor: **Enter Kiosk Mode**. |
| *Not provisioned: kiosk unavailable*. | The launcher is not the device owner (a new or reset handheld). | IT: run the provisioning kit. |
| Android's own home screen appears. | Kiosk was turned off, or the handheld is not provisioned. | Open the PPNAM Launcher; a supervisor turns kiosk back on. |
| The screen turns sideways in a station app. | Kiosk is off, or provisioning did not allow the launcher to change system settings. | Supervisor: enter kiosk. If it persists, IT re-runs provisioning. |
| The PIN box will not take a PIN. | Five wrong attempts locked it. | Wait for the 30-second countdown. |
| Back does nothing on the launcher. | That is by design: the launcher is the home screen. | Tap a station tile. |
| A factory reset is refused. | Kiosk blocks it. | IT: **Remove Device Owner** first (section 8). |

> [!NOTE] Reporting a fault
> Give your supervisor the launcher version from the header (*Station Launcher · v1.5.0*), the station tile involved, and the exact notice or status on screen.
