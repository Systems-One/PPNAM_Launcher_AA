# PPNAM Launcher — UI Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every Launcher finding from the 2026-10-01 handheld UI audit (launcher-01..12 plus the static rows static-15/21/26 that name the Launcher) without touching kiosk / device-owner policy.

**Architecture:** The app is one Compose activity (`MainActivity.kt`) plus pure-Kotlin helpers (`KioskApps`, `KioskPolicy`, `LockdownPanel`) that already have JUnit 4 tests. The PIN gate logic (attempts, lockout, blank guard, digit filter) moves out of the dialog into a new pure class `PinGate` backed by a `Store` interface, so it is unit-tested on the JVM and persisted through `SharedPreferences` in the activity. Portrait lock, soft-input mode, Back-swallow, operator-facing strings, a `MaterialTheme` with the admin teal primary over the station graphite palette, and tile layout fixes are applied in `MainActivity.kt`, `AndroidManifest.xml`, `KioskManager.kt` (orientation only) and `res/values/strings.xml`.

**Tech Stack:** Kotlin 2.0.0, AGP 8.4.2, Compose BOM 2024.12.01 (material3 1.3.x, ui 1.7.x), activity-compose 1.9.0, JUnit 4.13.2 JVM unit tests (`app/src/test`). No Robolectric and no `androidTest` sources exist; UI behaviour is verified manually on `emulator-5554` plus a compile check.

**Spec:** `C:\Users\Jonathan\AppData\Local\Temp\claude\C--Dev-Clients-PPNAM\ba7a1680-4205-4b04-bcb6-1b1f23c94914\scratchpad\audit\CONSOLIDATED_REPORT.md` (sections 3, 4 "Launcher" + "Static consistency audit", 5, 6, 7), with the dynamic audit `...\audit\launcher.md` and `...\audit\static_consistency.md` (§1, §3 "L PIN", §10) as evidence.

## Global Constraints

- Repo: `C:\Dev\Clients\PPNAM\PPNAM_Launcher_AA`, start branch `feature/remove-device-owner`, work branch `fix/ui-audit-2026-10-02`. Working tree was clean at planning time (`git status` showed no modified files) — there are **no pre-existing dirty files**; if Task 1 finds any, list them in the commit body as "pre-existing, untouched" and never stage them.
- Build: `.\gradlew.bat :app:assembleDebug --offline` from the repo root (drop `--offline` only if dependency resolution fails). APK: `app\build\outputs\apk\debug\app-debug.apk`. The Gradle daemon prints a harmless "SDK processing… SDK XML versions" warning — ignore it.
- Unit tests: `.\gradlew.bat :app:testDebugUnitTest --offline` (verified to pass before this plan).
- Emulator: `emulator-5554`; adb = `C:\Users\Jonathan\AppData\Local\Android\Sdk\platform-tools\adb.exe` (referred to as `adb` below — run it with the full path). Install: `adb -s emulator-5554 install -r -g app\build\outputs\apk\debug\app-debug.apk`; if it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` run `adb -s emulator-5554 uninstall com.mitas.ppnam.launcheraa` first (the provisioning-signed build is installed). Launch: `adb -s emulator-5554 shell am start -n com.mitas.ppnam.launcheraa/.MainActivity`.
- Supervisor PIN `079545`, 5 attempts, 30 s lockout — values unchanged (spec §3(c)). Wrong-PIN wording copied from S1 `SettingsActivity.kt:217`: `Incorrect PIN. N attempts left before lockout.` / `:198,214`: `Too many attempts. Try again in Ns.`; singular `attempt` via `<plurals>`.
- Palette (spec §5 "Error colour", static §1 row L): surface `#102233`, border `#25384C`, danger red `#E25C5C`, text `#EDF4FB` / `#9BAEC0`, warning `#F0A13A`, background `#07101A`. Launcher `MaterialTheme` primary = admin teal `#0F6E75`, light tint `#9BCBCE` (README "A — Teal" row).
- Dialogs (spec §5 "Dialog style"): M3 rounded, neutral (muted) dismiss, red destructive confirm.
- Every activity portrait-locked; every activity with a text field `android:windowSoftInputMode="stateHidden|adjustResize"` (spec §7 Tier 1 items 1–2).
- Enter / IME Done submits the PIN; blank submit never spends an attempt; lockout persists across dismiss and process restart with a 1 s ticker and Unlock + field disabled while locked (spec §3(b), §3(c)).
- OUT OF SCOPE — do not change: `KioskManager` device-owner / lock-task / policy code (only the runtime orientation toggle is removed, because it overrides the manifest lock), `KioskApps` package list, `KioskDeviceAdminReceiver`, `KioskPolicy`, `LockdownPanel`, broker or credential anything (the Launcher has none), the Launcher app icon.
- Git: commit after every task; add only the task's files (never `git add -A`). Every commit message ends with the two trailer lines:
  ```
  Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q
  ```

## Review Focus

1. Device clock moved backwards while locked out (persisted absolute `lockedOutUntilMs` now lies more than 30 s in the future) — a supervisor must never be locked out longer than 30 s; `PinGate` treats a lockout further away than its own duration as expired. Test added in Task 3.
2. Non-digit input reaching the PIN field (voice input, paste, a scanner wedge suffix) — it is dropped, never masked as dots; `PinGate.sanitize` is the single filter and is unit-tested in Task 3.
3. Reopening the PIN dialog (or restarting the app) during an active lockout — the countdown shows immediately, Unlock and the field are disabled, and the message clears on its own when the lockout ends. Manual check in Task 5 step 7.
4. Hardware Enter (`KEYCODE_ENTER`, what the C72 keypad sends) with the PIN typed — submits exactly like the IME tick; a second Enter after a wrong PIN does not open anything else. Manual check in Task 5 step 7.
5. Back pressed while a dialog is open on an unprovisioned device — closes only the dialog; Back on the bare grid does nothing (the launcher never backs out into whatever app was behind it). Manual check in Task 6 step 3.

---

### Task 1: Record repo state and create the work branch

**Files:** none modified.

**Interfaces:**
- Produces: branch `fix/ui-audit-2026-10-02` on top of `feature/remove-device-owner`.

- [x] **Step 1: Record the working tree**

Run from `C:\Dev\Clients\PPNAM\PPNAM_Launcher_AA`:
```powershell
git status --short
git branch --show-current
```
Expected: no output from `git status --short` (clean tree, as at planning time) and `feature/remove-device-owner`. If any file is listed, write its path down: it is "pre-existing, untouched" and must never be staged by a later task.

- [x] **Step 2: Create the branch**

```powershell
git switch -c fix/ui-audit-2026-10-02
git branch --show-current
```
Expected: `fix/ui-audit-2026-10-02`.

- [x] **Step 3: Confirm the baseline builds and tests pass**

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
```
Expected: both exit 0 (only the SDK XML warning printed). No commit for this task.

---

### Task 2: Portrait lock and soft-input mode on the only activity (Tier 1 — launcher-02, group (e))

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:33-39`
- Modify: `app/src/main/java/com/mitas/ppnam/launcheraa/KioskManager.kt:60-63, 79-83, 101-111`
- Modify: `app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt:67-72, 153-155` (comments only)

**Interfaces:**
- Consumes: nothing.
- Produces: `MainActivity` is always portrait; `KioskManager.applyOrientationLock` no longer exists.

Why `KioskManager` is touched: `applyOrientationLock(activity, locked = false)` sets `requestedOrientation = SCREEN_ORIENTATION_UNSPECIFIED` on every resume of an unprovisioned device, and a runtime `requestedOrientation` overrides the manifest attribute. Leaving it in place would silently undo the manifest lock. This removes only the orientation toggle; every policy / lock-task line stays as is.

- [x] **Step 1: Manifest — lock portrait and declare the soft-input mode**

In `app/src/main/AndroidManifest.xml` replace lines 33–39:
```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTask"
            android:lockTaskMode="if_whitelisted"
            android:theme="@style/Theme.PPNAMLauncher">
```
with:
```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTask"
            android:lockTaskMode="if_whitelisted"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.PPNAMLauncher"
            android:windowSoftInputMode="stateHidden|adjustResize">
```

- [x] **Step 2: KioskManager — remove the runtime orientation toggle**

In `KioskManager.kt` replace lines 60–63:
```kotlin
    fun ensurePinned(activity: Activity) {
        // Applied on every resume, not just the one that pins: the activity can be
        // recreated inside an already-pinned task and would otherwise come back rotatable.
        applyOrientationLock(activity, locked = isDeviceOwner(activity) && isKioskEnabled(activity))
        val shouldPin = KioskPolicy.shouldPin(
```
with:
```kotlin
    fun ensurePinned(activity: Activity) {
        // Orientation is fixed to portrait in the manifest for every state of the device
        // (UI audit launcher-02): a runtime requestedOrientation would override it.
        val shouldPin = KioskPolicy.shouldPin(
```
Replace lines 79–83:
```kotlin
    /** Supervisor action: release the device until kiosk is re-enabled. */
    fun exitKiosk(activity: Activity) {
        setEnabled(activity, false)
        applyOrientationLock(activity, locked = false)
        releasePolicies(activity)
```
with:
```kotlin
    /** Supervisor action: release the device until kiosk is re-enabled. */
    fun exitKiosk(activity: Activity) {
        setEnabled(activity, false)
        releasePolicies(activity)
```
Delete lines 101–111 entirely:
```kotlin
    /**
     * Kiosk holds the launcher in portrait ("vertical"); unlocked, the device rotates
     * normally so supervisors can use Settings and station apps however they like.
     */
    private fun applyOrientationLock(activity: Activity, locked: Boolean) {
        activity.requestedOrientation = if (locked) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

```
Delete the now-unused import at line 10: `import android.content.pm.ActivityInfo`.

- [x] **Step 3: MainActivity — correct the two comments that describe the recreation**

Replace lines 67–72:
```kotlin
    /**
     * What the supervisor panel renders from. Observable and re-read on every resume and
     * after every supervisor action: exitKiosk() releases the portrait lock, which
     * recreates this activity, so a value read once at composition is stale by the time
     * the panel redraws.
     */
```
with:
```kotlin
    /**
     * What the supervisor panel renders from. Observable and re-read on every resume and
     * after every supervisor action, so the panel always describes the device as it is
     * now rather than as it was when the dialog opened.
     */
```
Replace lines 153–155:
```kotlin
        // rememberSaveable, not remember: exiting kiosk releases the orientation lock and
        // recreates this activity. With plain remember the panel vanished mid-action and
        // looked to the supervisor like the exit had silently failed.
```
with:
```kotlin
        // rememberSaveable, not remember: the dialog flags must survive any recreation of
        // this activity (process death, a future configuration change) — with plain
        // remember the panel once vanished mid-action and looked like a silent failure.
```

- [x] **Step 4: Compile and run the existing unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
```
Expected: exit 0 for both.

- [x] **Step 5: Manual verification on the emulator**

```powershell
adb -s emulator-5554 install -r -g app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 shell settings put system accelerometer_rotation 0
adb -s emulator-5554 shell settings put system user_rotation 1
adb -s emulator-5554 shell am start -n com.mitas.ppnam.launcheraa/.MainActivity
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml
adb -s emulator-5554 shell head -c 120 /sdcard/ui.xml
```
Expected: the dump root reads `<hierarchy rotation="0">` (still portrait although the device is forced to landscape). Then tap the gear (`adb -s emulator-5554 shell input tap 948 204`), tap the PIN field, and run `adb -s emulator-5554 shell dumpsys window | findstr ITYPE_IME` — the IME frame is reported in portrait (top ≈ 1023 px for the text keyboard until Task 5 switches it to a numeric pad ≈ 1155 px) and the Cancel/Unlock buttons are above it (`adb shell uiautomator dump` → the `text="Unlock"` node's bottom bound < IME top). Restore rotation afterwards:
```powershell
adb -s emulator-5554 shell settings put system user_rotation 0
adb -s emulator-5554 shell settings put system accelerometer_rotation 1
```

- [x] **Step 6: Commit**

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/mitas/ppnam/launcheraa/KioskManager.kt app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt
git commit -m @'
fix(launcher): lock portrait and declare adjustResize on MainActivity

Closes UI audit launcher-02 (PIN dialog buttons under the landscape keyboard).
The manifest now holds the portrait lock for every device state; the runtime
orientation toggle in KioskManager is removed because requestedOrientation
would override the manifest attribute on every resume. Kiosk policy unchanged.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q
'@
```

---

### Task 3: `PinGate` — pure, persisted supervisor PIN logic with JVM tests (Tier 2 item 10 — launcher-01, launcher-05, launcher-06 logic; Review Focus 1, 2)

**Files:**
- Create: `app/src/main/java/com/mitas/ppnam/launcheraa/PinGate.kt`
- Test: `app/src/test/java/com/mitas/ppnam/launcheraa/PinGateTest.kt`

**Interfaces:**
- Produces (used by Task 5):
  ```kotlin
  class PinGate(store: PinGate.Store, clock: () -> Long = System::currentTimeMillis, correctPin: String = PinGate.CORRECT_PIN)
  interface PinGate.Store { var failedAttempts: Int; var lockedOutUntilMs: Long }
  class PinGate.MemoryStore : PinGate.Store
  sealed class PinGate.Result { object Unlocked; object Blank; data class Wrong(val attemptsLeft: Int); data class LockedOut(val secondsLeft: Long) }
  fun PinGate.submit(pin: String): PinGate.Result
  fun PinGate.lockoutSecondsLeft(): Long          // 0 when not locked out
  fun PinGate.isLockedOut(): Boolean
  val PinGate.lockedOutUntilMs: Long               // mirror of store value, for LaunchedEffect keys
  fun PinGate.Companion.sanitize(input: String): String   // digits only, max PIN_LENGTH
  const val PinGate.CORRECT_PIN = "079545"; MAX_ATTEMPTS = 5; LOCKOUT_MS = 30_000L; PIN_LENGTH = 6
  ```

- [x] **Step 1: Write the failing tests**

Create `app/src/test/java/com/mitas/ppnam/launcheraa/PinGateTest.kt`:
```kotlin
package com.mitas.ppnam.launcheraa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The supervisor PIN gate is the only thing between an operator and kiosk exit, so its
 * counter and lockout must live outside the dialog (UI audit launcher-01: Cancel + reopen
 * reset them) and never be spent by a mis-tap on an empty field (launcher-05).
 */
class PinGateTest {

    private var now = 1_000_000L
    private val store = PinGate.MemoryStore()
    private val gate = PinGate(store, clock = { now })

    @Test
    fun `correct PIN unlocks and clears the failure count`() {
        store.failedAttempts = 3
        assertEquals(PinGate.Result.Unlocked, gate.submit("079545"))
        assertEquals(0, store.failedAttempts)
        assertEquals(0L, store.lockedOutUntilMs)
    }

    @Test
    fun `wrong PIN counts down from five attempts`() {
        assertEquals(PinGate.Result.Wrong(4), gate.submit("111111"))
        assertEquals(PinGate.Result.Wrong(3), gate.submit("111111"))
        assertEquals(PinGate.Result.Wrong(2), gate.submit("111111"))
        assertEquals(PinGate.Result.Wrong(1), gate.submit("111111"))
    }

    @Test
    fun `fifth wrong PIN locks out for thirty seconds and resets the count`() {
        repeat(4) { gate.submit("000000") }
        assertEquals(PinGate.Result.LockedOut(30), gate.submit("000000"))
        assertTrue(gate.isLockedOut())
        assertEquals(30L, gate.lockoutSecondsLeft())
        assertEquals(0, store.failedAttempts)
        assertEquals(now + PinGate.LOCKOUT_MS, store.lockedOutUntilMs)
    }

    /** launcher-05: an empty Unlock must not eat lockout budget. */
    @Test
    fun `blank PIN is refused without spending an attempt`() {
        assertEquals(PinGate.Result.Blank, gate.submit(""))
        assertEquals(PinGate.Result.Blank, gate.submit("   "))
        assertEquals(0, store.failedAttempts)
        assertEquals(PinGate.Result.Wrong(4), gate.submit("000000"))
    }

    @Test
    fun `correct PIN during lockout is refused and the countdown keeps running`() {
        repeat(5) { gate.submit("000000") }
        now += 3_000
        assertEquals(PinGate.Result.LockedOut(27), gate.submit("079545"))
        assertEquals(27L, gate.lockoutSecondsLeft())
    }

    /** launcher-01: dismissing the dialog creates a new gate over the same store. */
    @Test
    fun `lockout survives a new gate on the same store`() {
        repeat(5) { gate.submit("000000") }
        val reopened = PinGate(store, clock = { now })
        assertTrue(reopened.isLockedOut())
        assertEquals(PinGate.Result.LockedOut(30), reopened.submit("079545"))
    }

    @Test
    fun `failure count survives a new gate on the same store`() {
        gate.submit("000000")
        gate.submit("000000")
        val reopened = PinGate(store, clock = { now })
        assertEquals(PinGate.Result.Wrong(2), reopened.submit("000000"))
    }

    @Test
    fun `lockout expires after thirty seconds`() {
        repeat(5) { gate.submit("000000") }
        now += 30_000
        assertFalse(gate.isLockedOut())
        assertEquals(0L, gate.lockoutSecondsLeft())
        assertEquals(PinGate.Result.Wrong(4), gate.submit("000000"))
    }

    @Test
    fun `seconds left rounds up so the ticker never shows zero while locked`() {
        repeat(5) { gate.submit("000000") }
        now += 100
        assertEquals(30L, gate.lockoutSecondsLeft())
        now += 900
        assertEquals(29L, gate.lockoutSecondsLeft())
        now += 28_999
        assertEquals(1L, gate.lockoutSecondsLeft())
    }

    /** Review Focus 1: a clock set backwards must not extend the lockout past 30 s. */
    @Test
    fun `lockout further away than its own duration is treated as expired`() {
        store.lockedOutUntilMs = now + PinGate.LOCKOUT_MS + 1
        assertFalse(gate.isLockedOut())
        assertEquals(0L, gate.lockoutSecondsLeft())
        assertEquals(PinGate.Result.Wrong(4), gate.submit("000000"))
    }

    /** Review Focus 2 / launcher-03: the field only ever holds up to six digits. */
    @Test
    fun `sanitize keeps digits only and caps at six`() {
        assertEquals("", PinGate.sanitize("abc"))
        assertEquals("079545", PinGate.sanitize("0795456789"))
        assertEquals("123", PinGate.sanitize("1 2-3\n"))
        assertEquals("", PinGate.sanitize(""))
    }
}
```

- [x] **Step 2: Run the tests to verify they fail**

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.mitas.ppnam.launcheraa.PinGateTest"
```
Expected: compilation FAILS with `Unresolved reference: PinGate`.

- [x] **Step 3: Write the implementation**

Create `app/src/main/java/com/mitas/ppnam/launcheraa/PinGate.kt`:
```kotlin
package com.mitas.ppnam.launcheraa

/**
 * Supervisor PIN gate: five attempts, then a 30 s lockout — the same rule as every station
 * app's Settings screen (Station 1 `SettingsActivity.submitPin`).
 *
 * The counter and the lockout deadline live in [Store], not in the dialog that shows them,
 * so dismissing the dialog or restarting the process cannot reset them (UI audit
 * launcher-01). Pure Kotlin with an injectable clock so the rule is tested on the JVM.
 */
class PinGate(
    private val store: Store,
    private val clock: () -> Long = System::currentTimeMillis,
    private val correctPin: String = CORRECT_PIN,
) {

    interface Store {
        var failedAttempts: Int
        var lockedOutUntilMs: Long
    }

    /** In-memory store for tests and previews. */
    class MemoryStore : Store {
        override var failedAttempts: Int = 0
        override var lockedOutUntilMs: Long = 0L
    }

    sealed class Result {
        object Unlocked : Result()
        /** Nothing typed: refused without spending an attempt (launcher-05). */
        object Blank : Result()
        data class Wrong(val attemptsLeft: Int) : Result()
        data class LockedOut(val secondsLeft: Long) : Result()
    }

    val lockedOutUntilMs: Long get() = store.lockedOutUntilMs

    /** Whole seconds until the lockout ends, rounded up; 0 when not locked out. */
    fun lockoutSecondsLeft(): Long {
        val now = clock()
        val until = store.lockedOutUntilMs
        // A deadline further away than one lockout means the clock moved backwards:
        // treat it as expired rather than locking the supervisor out indefinitely.
        if (now >= until || until - now > LOCKOUT_MS) return 0
        return (until - now + 999) / 1_000
    }

    fun isLockedOut(): Boolean = lockoutSecondsLeft() > 0

    fun submit(pin: String): Result {
        val secondsLeft = lockoutSecondsLeft()
        if (secondsLeft > 0) return Result.LockedOut(secondsLeft)
        if (pin.isBlank()) return Result.Blank
        if (pin == correctPin) {
            store.failedAttempts = 0
            store.lockedOutUntilMs = 0L
            return Result.Unlocked
        }
        val failed = store.failedAttempts + 1
        return if (failed >= MAX_ATTEMPTS) {
            store.failedAttempts = 0
            store.lockedOutUntilMs = clock() + LOCKOUT_MS
            Result.LockedOut(LOCKOUT_MS / 1_000)
        } else {
            store.failedAttempts = failed
            Result.Wrong(MAX_ATTEMPTS - failed)
        }
    }

    companion object {
        // Ported from the station apps' settings lock so every supervisor PIN matches.
        const val CORRECT_PIN = "079545"
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
        const val PIN_LENGTH = 6

        /** The only filter the PIN field applies: digits only, at most [PIN_LENGTH]. */
        fun sanitize(input: String): String = input.filter(Char::isDigit).take(PIN_LENGTH)
    }
}
```

- [x] **Step 4: Run the tests to verify they pass**

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.mitas.ppnam.launcheraa.PinGateTest"
```
Expected: BUILD SUCCESSFUL, 11 tests passed.

- [x] **Step 5: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/launcheraa/PinGate.kt app/src/test/java/com/mitas/ppnam/launcheraa/PinGateTest.kt
git commit -m @'
feat(launcher): extract supervisor PIN gate into a tested, store-backed class

Attempts and lockout deadline live in a Store instead of dialog state, blank
submits never spend an attempt, seconds-left rounds up for a ticker, and a
clock jump cannot extend the lockout. Prepares UI audit launcher-01/05/06.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q
'@
```

---

### Task 4: Operator-facing PIN strings and plurals (Tier 2 item 13 — static-21 wording, spec §3(i))

**Files:**
- Modify: `app/src/main/res/values/strings.xml:1-4`

**Interfaces:**
- Produces string ids used by Task 5: `R.string.pin_dialog_title`, `R.string.pin_label`, `R.string.pin_blank`, `R.string.pin_locked_out`, `R.plurals.pin_attempts_left`, `R.string.pin_unlock`, `R.string.pin_cancel`.

- [x] **Step 1: Add the resources**

Replace the whole of `app/src/main/res/values/strings.xml` with:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">PPNAM Launcher</string>

    <!-- Supervisor PIN dialog. Wording copied from Station 1 SettingsActivity so every
         PIN gate on the handheld reads the same (UI audit static-21). -->
    <string name="pin_dialog_title">Supervisor PIN</string>
    <string name="pin_label">PIN</string>
    <string name="pin_blank">Enter the PIN</string>
    <string name="pin_locked_out">Too many attempts. Try again in %1$ds.</string>
    <plurals name="pin_attempts_left">
        <item quantity="one">Incorrect PIN. %1$d attempt left before lockout.</item>
        <item quantity="other">Incorrect PIN. %1$d attempts left before lockout.</item>
    </plurals>
    <string name="pin_unlock">Unlock</string>
    <string name="pin_cancel">Cancel</string>
</resources>
```

- [x] **Step 2: Compile check**

```powershell
.\gradlew.bat :app:assembleDebug --offline -q
```
Expected: exit 0 (resources compile; nothing references them yet).

- [x] **Step 3: Commit**

```powershell
git add app/src/main/res/values/strings.xml
git commit -m @'
chore(launcher): add supervisor PIN strings with S1 wording and plurals

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q
'@
```

---

### Task 5: Rewrite `SupervisorPinDialog` on `PinGate` — persisted lockout, blank guard, ticker, numeric keyboard, Enter submits, auto-focus (Tier 2 items 9 + 10 — launcher-01, -03, -04, -05, -06, launcher-11 partial; Review Focus 3, 4)

**Files:**
- Modify: `app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt` — imports (lines 3–51), `MainActivity` fields (after line 81), `setContent` call (line 92), `LauncherScreen` signature (lines 143–152) and the `SupervisorPinDialog` call (lines 211–220), the whole `SupervisorPinDialog` (lines 308–362), companion (lines 422–428).

**Interfaces:**
- Consumes: `PinGate` (Task 3), string ids (Task 4).
- Produces: `SupervisorPinDialog(gate: PinGate, onDismiss: () -> Unit, onUnlocked: () -> Unit)`; `LauncherScreen(..., pinGate: PinGate, ...)`; private top-level `class PrefsPinStore(context: Context) : PinGate.Store`.

- [x] **Step 1: Add imports**

In `MainActivity.kt` add these imports (keep alphabetical order with the existing block):
```kotlin
import android.content.Context
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
```

- [x] **Step 2: Persisted store and gate in the activity**

After line 81 (`private val supervisorUnlocked = mutableStateOf(false)`) add:
```kotlin

    /** Attempt counter + lockout outlive the dialog and the process (UI audit launcher-01). */
    private val pinGate by lazy { PinGate(PrefsPinStore(this)) }
```
At the very end of the file (after the closing brace of `MainActivity`) add:
```kotlin

/** SharedPreferences-backed [PinGate.Store]. */
private class PrefsPinStore(context: Context) : PinGate.Store {
    private val prefs = context.getSharedPreferences("supervisor_pin", Context.MODE_PRIVATE)

    override var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(value) { prefs.edit().putInt(KEY_FAILED, value).apply() }

    override var lockedOutUntilMs: Long
        get() = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        set(value) { prefs.edit().putLong(KEY_LOCKED_UNTIL, value).apply() }

    private companion object {
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_out_until_ms"
    }
}
```

- [x] **Step 3: Pass the gate through `LauncherScreen`**

Line 92 — inside `setContent { LauncherScreen( ... ) }` add a parameter after `onSupervisorUnlocked = { supervisorUnlocked.value = true },`:
```kotlin
                pinGate = pinGate,
```
Lines 143–152 — add the parameter to the signature after `onSupervisorUnlocked: () -> Unit,`:
```kotlin
        pinGate: PinGate,
```
Lines 211–220 — replace:
```kotlin
        if (showPinDialog) {
            SupervisorPinDialog(
                onDismiss = { showPinDialog = false },
```
with:
```kotlin
        if (showPinDialog) {
            SupervisorPinDialog(
                gate = pinGate,
                onDismiss = { showPinDialog = false },
```

- [x] **Step 4: Replace the dialog (lines 308–362) with the gate-driven version**

```kotlin
    /**
     * Same supervisor PIN and lockout rule as the station apps' settings screens; the
     * rule itself is [PinGate], this composable only renders it. Numeric keyboard, Enter
     * submits, field auto-focused, field and Unlock disabled while locked out, and the
     * countdown ticks every second (UI audit launcher-03/04/05/06).
     */
    @Composable
    private fun SupervisorPinDialog(gate: PinGate, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
        val resources = LocalContext.current.resources
        var pin by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<PinGate.Result?>(null) }
        var lockedOutUntil by remember { mutableStateOf(gate.lockedOutUntilMs) }
        var secondsLeft by remember { mutableStateOf(gate.lockoutSecondsLeft()) }
        val lockedOut = secondsLeft > 0
        val focusRequester = remember { FocusRequester() }

        // 1 s ticker while locked out; restarts whenever a new lockout begins.
        LaunchedEffect(lockedOutUntil) {
            while (true) {
                val left = gate.lockoutSecondsLeft()
                secondsLeft = left
                if (left == 0L) break
                delay(1_000)
            }
            if (result is PinGate.Result.LockedOut) result = null
        }

        // Focus (and so the keyboard) once the dialog window has had its first frame.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (!gate.isLockedOut()) focusRequester.requestFocus()
        }

        fun submit() {
            val outcome = gate.submit(pin)
            result = outcome
            lockedOutUntil = gate.lockedOutUntilMs
            when (outcome) {
                PinGate.Result.Unlocked -> onUnlocked()
                is PinGate.Result.Wrong, is PinGate.Result.LockedOut -> pin = ""
                PinGate.Result.Blank -> Unit
            }
        }

        val message: String? = when {
            lockedOut -> resources.getString(R.string.pin_locked_out, secondsLeft)
            result is PinGate.Result.Wrong -> {
                val left = (result as PinGate.Result.Wrong).attemptsLeft
                resources.getQuantityString(R.plurals.pin_attempts_left, left, left)
            }
            result == PinGate.Result.Blank -> resources.getString(R.string.pin_blank)
            else -> null
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = GraphiteSurface,
            title = { Text(stringResource(R.string.pin_dialog_title), color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = PinGate.sanitize(it) },
                    label = { Text(stringResource(R.string.pin_label)) },
                    singleLine = true,
                    enabled = !lockedOut,
                    isError = message != null,
                    supportingText = message?.let { { Text(it, color = DangerRed) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 6.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledTextColor = TextMuted,
                        errorTextColor = TextPrimary,
                        cursorColor = TextPrimary,
                        errorCursorColor = DangerRed,
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = GraphiteBorder,
                        disabledBorderColor = GraphiteBorder,
                        errorBorderColor = DangerRed,
                        focusedLabelColor = TextPrimary,
                        unfocusedLabelColor = TextMuted,
                        disabledLabelColor = TextMuted,
                        errorLabelColor = DangerRed,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            },
            confirmButton = {
                Button(onClick = { submit() }, enabled = !lockedOut) {
                    Text(stringResource(R.string.pin_unlock))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_cancel)) }
            },
        )
    }
```

- [x] **Step 5: Remove the now-duplicated constants from the companion (lines 422–428)**

Replace:
```kotlin
    private companion object {
        // Ported from the station apps' settings lock so every supervisor PIN matches.
        const val CORRECT_PIN = "079545"
        const val MAX_PIN_ATTEMPTS = 5
        const val PIN_LOCKOUT_MS = 30_000L
        const val KEY_SUPERVISOR_UNLOCKED = "supervisor_unlocked"
    }
```
with:
```kotlin
    private companion object {
        const val KEY_SUPERVISOR_UNLOCKED = "supervisor_unlocked"
    }
```

- [x] **Step 6: Compile and run all unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
```
Expected: exit 0. If the compiler reports `pin` smart-cast problems inside `submit()`, it is because `pin` is a delegated `var`; the code above only reads it once per call, which compiles.

- [x] **Step 7: Manual verification on the emulator**

Install and launch, then:
```powershell
adb -s emulator-5554 install -r -g app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 shell am start -n com.mitas.ppnam.launcheraa/.MainActivity
adb -s emulator-5554 shell input tap 948 204          # gear → PIN dialog
adb -s emulator-5554 shell dumpsys window | findstr ITYPE_IME
```
1. Auto-focus + numeric pad (launcher-03/04): the IME frame is present without tapping the field and its top is ≈ 1155 px (numeric pad), not ≈ 1023 (QWERTY). `adb -s emulator-5554 shell input text abc` leaves the field empty (dump shows no dots).
2. Enter submits (launcher-04, Review Focus 4): `adb -s emulator-5554 shell input text 111111` then `adb -s emulator-5554 shell input keyevent 66`. `adb shell uiautomator dump /sdcard/ui.xml; adb shell cat /sdcard/ui.xml | findstr "attempts left"` shows `Incorrect PIN. 4 attempts left before lockout.` and the field is empty. A second `keyevent 66` on the empty field shows `Enter the PIN` and the count stays at 4 (launcher-05).
3. Lockout persists across Cancel (launcher-01): repeat `input text 111111` + `keyevent 66` four more times → `Too many attempts. Try again in 30s.`; the dump shows the `Unlock` node `enabled="false"`. Tap Cancel, tap the gear again: the message is back with a lower number and Unlock still disabled. `adb -s emulator-5554 shell am force-stop com.mitas.ppnam.launcheraa` then start again and open the gear within 30 s: still locked (Review Focus 3).
4. Ticker (launcher-06): take two dumps 3 s apart without touching anything — the number in `Try again in Ns.` decreases. After it reaches 0 the message disappears, the field and Unlock re-enable, and the field takes focus on the next tap.
5. Correct PIN: `input text 079545` + `keyevent 66` → PIN dialog closes, the "Device Lockdown" dialog opens, the grid shows the Settings tile. Final `input text 1` + `keyevent 66` plural check: after one wrong PIN later the message must read `… 1 attempt left before lockout.` when only one remains.

- [x] **Step 8: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt
git commit -m @'
fix(launcher): supervisor PIN dialog — persisted lockout, numeric pad, Enter submits

Closes UI audit launcher-01 (lockout bypass on dismiss), launcher-03 (QWERTY,
letters accepted), launcher-04 (Done ignored, no auto-focus), launcher-05
(empty Unlock spends an attempt), launcher-06 (static countdown) and the S1
wording/plural rows of static-21. Field and Unlock are disabled while locked.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q
'@
```

---

### Task 6: Swallow Back on the grid (Tier 2 item 12 — launcher-12; Review Focus 5)

**Files:**
- Modify: `app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt:83-86` (`onCreate`)

**Interfaces:**
- Consumes: nothing.
- Produces: nothing; behaviour only.

- [x] **Step 1: Register a swallowing back callback**

Add the import `import androidx.activity.addCallback` (alphabetical, after `import androidx.activity.ComponentActivity`). In `onCreate`, after `refreshKioskState()` (line 86) and before `setContent {`, insert:
```kotlin
        // The launcher is the floor of the device: Back must never drop a supervisor into
        // whatever app sat behind the task on an unprovisioned handheld (UI audit
        // launcher-12). Under lock task the OS already blocks Back; this covers the rest.
        // Compose dialogs run in their own window, so Back still closes an open dialog.
        onBackPressedDispatcher.addCallback(this) { /* swallow */ }
```

- [x] **Step 2: Compile**

```powershell
.\gradlew.bat :app:assembleDebug --offline -q
```
Expected: exit 0.

- [x] **Step 3: Manual verification**

```powershell
adb -s emulator-5554 install -r -g app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 shell am start -n com.mitas.ppnam.station1aa/.LoginActivity
adb -s emulator-5554 shell am start -n com.mitas.ppnam.launcheraa/.MainActivity
adb -s emulator-5554 shell input keyevent 4
adb -s emulator-5554 shell dumpsys activity activities | findstr topResumedActivity
```
Expected: `topResumedActivity` is still `com.mitas.ppnam.launcheraa/.MainActivity` (previously Back showed Station 1's Login, `15_after_back_on_grid.png`). Then tap the gear (`input tap 948 204`), press `keyevent 4` once — the keyboard closes; again — the PIN dialog closes and the grid is still on top (Review Focus 5).

- [x] **Step 4: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt
git commit -m @'
fix(launcher): swallow Back on the grid so the launcher never backs out

Closes UI audit launcher-12. Dialogs keep their own Back handling.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q
'@
```

---

### Task 7: Operator-facing "not provisioned" message with a copyable, non-wrapping command (Tier 2 item 13 — launcher-07, group (f))

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (append)
- Modify: `app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt` — `LockdownDialog` lines 376–384 (title + `!isDeviceOwner` branch) and imports.

**Interfaces:**
- Consumes: nothing new.
- Produces: string ids `lockdown_title`, `lockdown_not_owner`, `lockdown_provision_command`.

- [x] **Step 1: Strings**

Inside `<resources>` in `strings.xml`, before `</resources>`, add:
```xml

    <!-- Device Lockdown dialog, unprovisioned branch (UI audit launcher-07). The shell
         command is for the provisioning PC, so it is shown on one monospace line the
         supervisor can scroll and copy rather than wrapped into the prose. -->
    <string name="lockdown_title">Device Lockdown</string>
    <string name="lockdown_not_owner">This scanner has not been provisioned, so kiosk mode is not available. Run the PPNAM provisioning kit (PPNAM_Provisioning\\provision.ps1) from a PC with the scanner connected. It runs:</string>
    <string name="lockdown_provision_command">dpm set-device-owner com.mitas.ppnam.launcheraa/.KioskDeviceAdminReceiver</string>
```

- [x] **Step 2: Dialog code**

Add imports:
```kotlin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
```
In `LockdownDialog` replace line 376:
```kotlin
            title = { Text("Device Lockdown", color = TextPrimary) },
```
with:
```kotlin
            title = { Text(stringResource(R.string.lockdown_title), color = TextPrimary) },
```
and replace lines 380–384:
```kotlin
                        !isDeviceOwner -> Text(
                            "This launcher is not the device owner. Provision it with:\n\n" +
                                "dpm set-device-owner com.mitas.ppnam.launcheraa/.KioskDeviceAdminReceiver",
                            color = TextMuted
                        )
```
with:
```kotlin
                        !isDeviceOwner -> {
                            Text(stringResource(R.string.lockdown_not_owner), color = TextMuted)
                            Spacer(Modifier.height(12.dp))
                            SelectionContainer {
                                Text(
                                    stringResource(R.string.lockdown_provision_command),
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    softWrap = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                )
                            }
                        }
```

- [x] **Step 3: Compile**

```powershell
.\gradlew.bat :app:assembleDebug --offline -q
```
Expected: exit 0.

- [x] **Step 4: Manual verification**

Install, launch, gear, `input text 079545`, `keyevent 66` (if locked out from Task 5, wait for the countdown first). Take `adb -s emulator-5554 exec-out screencap -p > lockdown.png` and open it: the prose ends with "It runs:" and the command sits on a single monospace line that is clipped at the right edge (no `KioskDevic` / `eAdminReceiver` break). `adb shell input swipe 900 900 300 900` inside the command line scrolls it; a long-press on it selects text (selection handles appear).

- [x] **Step 5: Commit**

```powershell
git add app/src/main/res/values/strings.xml app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt
git commit -m @'
fix(launcher): plain-language provisioning message with a copyable command line

Closes UI audit launcher-07: the dpm command no longer wraps mid-word and can
be selected; the prose tells the supervisor what to do instead.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q
'@
```

---

### Task 8: `MaterialTheme` with admin teal over the station graphite palette; neutral dismiss / red destructive dialog buttons (Tier 3 item 22 — launcher-10, launcher-11, static-15, static-21 palette)

**Files:**
- Modify: `app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt` — palette constants (lines 53–59), `setContent` (lines 87–108), the three dialogs' dismiss buttons, the PIN field focused colours from Task 5, imports.

**Interfaces:**
- Consumes: Task 5's `SupervisorPinDialog`.
- Produces: private `LauncherTheme(content)` composable; private `NeutralTextButton(onClick, text)`; constants `AdminTeal`, `AdminTealTint`.

- [x] **Step 1: Palette — align the three drifted tokens and add the teal**

Replace lines 53–59:
```kotlin
private val GraphiteBackground = Color(0xFF07101A)
private val GraphiteSurface = Color(0xFF0E1B29)
private val GraphiteBorder = Color(0xFF1D2F42)
private val TextPrimary = Color(0xFFEDF4FB)
private val TextMuted = Color(0xFF9BAEC0)
private val DangerRed = Color(0xFFE5484D)
private val WarnAmber = Color(0xFFF0A13A)
```
with:
```kotlin
// Station graphite palette (Station 2 ui/theme/Color.kt, Station 1 colors.xml) so the
// launcher and the apps it opens read as one family (UI audit static-21).
private val GraphiteBackground = Color(0xFF07101A)
private val GraphiteSurface = Color(0xFF102233)
private val GraphiteBorder = Color(0xFF25384C)
private val TextPrimary = Color(0xFFEDF4FB)
private val TextMuted = Color(0xFF9BAEC0)
private val DangerRed = Color(0xFFE25C5C)
private val WarnAmber = Color(0xFFF0A13A)

// Admin identity from the icon-pack README ("A — Teal"): field colour for filled
// controls, light tint where an accent has to be legible as text or a 1 dp stroke on
// graphite (#0F6E75 on #102233 is only 2.7:1).
private val AdminTeal = Color(0xFF0F6E75)
private val AdminTealTint = Color(0xFF9BCBCE)

private val LauncherColorScheme = darkColorScheme(
    primary = AdminTeal,
    onPrimary = Color.White,
    primaryContainer = AdminTeal,
    onPrimaryContainer = Color.White,
    secondary = AdminTealTint,
    onSecondary = GraphiteBackground,
    background = GraphiteBackground,
    onBackground = TextPrimary,
    surface = GraphiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = GraphiteSurface,
    onSurfaceVariant = TextMuted,
    surfaceContainerHigh = GraphiteSurface,
    error = DangerRed,
    onError = TextPrimary,
    outline = GraphiteBorder,
)

/** The only theme wrapper in the app; every dialog, button and field inherits it. */
@Composable
private fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LauncherColorScheme, content = content)
}

/** Dismiss action: muted text, so the red destructive confirm is the only loud button. */
@Composable
private fun NeutralTextButton(onClick: () -> Unit, text: String) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = TextMuted),
    ) { Text(text) }
}
```
Add imports:
```kotlin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.darkColorScheme
```

- [x] **Step 2: Wrap the content**

In `onCreate` change the `setContent` block (lines 87–108) so the screen is inside the theme:
```kotlin
        setContent {
            LauncherTheme {
                val visible = KioskApps.visibleEntries(supervisorUnlocked.value)
                LauncherScreen(
                    tiles = tiles.value.filter { it.entry in visible },
                    onLaunch = { KioskApps.launch(this, it) },
                    onSupervisorUnlocked = { supervisorUnlocked.value = true },
                    pinGate = pinGate,
                    isDeviceOwner = deviceOwner.value,
                    isKioskEnabled = kioskEnabled.value,
                    onEnterKiosk = {
                        KioskManager.enterKiosk(this)
                        refreshKioskState()
                    },
                    onExitKiosk = {
                        KioskManager.exitKiosk(this)
                        refreshKioskState()
                    },
                    onRemoveOwner = {
                        KioskManager.removeDeviceOwner(this)
                        refreshKioskState()
                    },
                )
            }
        }
```

- [x] **Step 3: Neutral dismiss buttons on all three dialogs**

Remove-owner confirm (original line 256):
```kotlin
                    TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
```
→
```kotlin
                    NeutralTextButton(onClick = { showRemoveConfirm = false }, text = "Cancel")
```
PIN dialog (Task 5 code):
```kotlin
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_cancel)) }
            },
```
→
```kotlin
            dismissButton = {
                NeutralTextButton(onClick = onDismiss, text = stringResource(R.string.pin_cancel))
            },
```
Lockdown dialog (original line 418):
```kotlin
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
```
→
```kotlin
            dismissButton = { NeutralTextButton(onClick = onDismiss, text = "Close") },
```
The destructive confirms (`Remove` DangerRed, `Enter Kiosk Mode` DangerRed, `Exit Kiosk Mode` WarnAmber) stay as they are.

- [x] **Step 4: PIN field focus accent = teal tint (launcher-11 legibility finished)**

In the `OutlinedTextFieldDefaults.colors(...)` call from Task 5 change three values:
```kotlin
                        cursorColor = TextPrimary,
                        ...
                        focusedBorderColor = TextPrimary,
                        ...
                        focusedLabelColor = TextPrimary,
```
→
```kotlin
                        cursorColor = AdminTealTint,
                        ...
                        focusedBorderColor = AdminTealTint,
                        ...
                        focusedLabelColor = AdminTealTint,
```

- [x] **Step 5: Compile and test**

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
```
Expected: exit 0.

- [x] **Step 6: Manual verification**

Install, launch, open the gear. `adb -s emulator-5554 exec-out screencap -p > pin_themed.png`:
- No purple anywhere (`03_pin_dialog.png` had #6750A4 buttons and outline). Unlock is a filled teal pill with white text; Cancel is muted grey; the focused field outline/label are the light teal tint; dialog corners rounded (M3 28 dp).
- Type `input text 12` — two large bullets are clearly visible in white at 22 sp with 6 sp spacing (launcher-11; compare `05_pin_letters.png`).
- Type a wrong PIN + Enter: the supporting text and border are `#E25C5C` red.
- Tile surfaces are now `#102233` (sample a tile pixel in the screenshot) — slightly bluer than before, matching the station cards.
- Enter `079545`: the Lockdown dialog's Close is muted grey.

- [x] **Step 7: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt
git commit -m @'
feat(launcher): MaterialTheme with admin teal over the station graphite palette

Closes UI audit launcher-10 (stock purple dialogs), launcher-11 (invisible PIN
dots), static-15/static-21 (palette drift: surface, border and danger red now
match the station apps; neutral dismiss, red destructive confirm).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q
'@
```

---

### Task 9: Tiles — equal heights, hide absent supervisor tiles, placeholder icon and amber "Not installed" (Tier 3 item 24 — launcher-08, launcher-09, static-26)

**Files:**
- Modify: `app/src/main/java/com/mitas/ppnam/launcheraa/KioskApps.kt:38-41` (add `showsTile`)
- Test: `app/src/test/java/com/mitas/ppnam/launcheraa/KioskAppsTest.kt` (append)
- Modify: `app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt` — tile filter in `setContent`, `AppTile` (original lines 262–306), imports.

**Interfaces:**
- Produces: `fun KioskApps.showsTile(entry: Entry, installed: Boolean): Boolean`.

- [x] **Step 1: Write the failing test**

Append to `KioskAppsTest.kt` before the final `}`:
```kotlin

    /** launcher-09: a dead "Not installed" supervisor tile tells a supervisor nothing. */
    @Test
    fun `absent supervisor-only apps are hidden, absent station apps stay visible`() {
        val keyboardEmulator = KioskApps.entries.first { it.packageName == "com.rscja.scanner" }
        val station3 = KioskApps.entries.first { it.packageName == "com.mitas.ppnam.station3aa" }
        assertFalse(KioskApps.showsTile(keyboardEmulator, installed = false))
        assertTrue(KioskApps.showsTile(keyboardEmulator, installed = true))
        assertTrue(KioskApps.showsTile(station3, installed = false))
        assertTrue(KioskApps.showsTile(station3, installed = true))
    }
```

- [x] **Step 2: Run it to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.mitas.ppnam.launcheraa.KioskAppsTest"
```
Expected: compilation FAILS with `Unresolved reference: showsTile`.

- [x] **Step 3: Implement `showsTile`**

In `KioskApps.kt` after `visibleEntries` (line 41) add:
```kotlin

    /**
     * Whether a tile is drawn at all. A station app that is missing is a provisioning
     * fault the operator should see ("Not installed"); a missing supervisor-only app is
     * just a device without that service, and an inert tile would only confuse.
     */
    fun showsTile(entry: Entry, installed: Boolean): Boolean =
        installed || !entry.supervisorOnly
```

- [x] **Step 4: Run the tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.mitas.ppnam.launcheraa.KioskAppsTest"
```
Expected: PASS (8 tests).

- [x] **Step 5: Apply the filter and fix the tile layout**

In `setContent` (Task 8 version) change:
```kotlin
                    tiles = tiles.value.filter { it.entry in visible },
```
to:
```kotlin
                    tiles = tiles.value.filter {
                        it.entry in visible && KioskApps.showsTile(it.entry, it.installed)
                    },
```
Add imports:
```kotlin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.style.TextOverflow
```
Replace the whole `AppTile` composable (original lines 262–306) with:
```kotlin
    /**
     * Fixed-height tile so a two-line label or a "Not installed" line never makes one row
     * taller than its neighbours (UI audit launcher-08); a missing icon gets a warning
     * glyph instead of a blank square (launcher-09).
     */
    @Composable
    private fun AppTile(tile: Tile, onLaunch: (KioskApps.Entry) -> Unit) {
        val alpha = if (tile.installed) 1f else 0.35f
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .height(148.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GraphiteSurface)
                .clickable(enabled = tile.installed) { onLaunch(tile.entry) }
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            if (tile.icon != null) {
                Image(
                    bitmap = tile.icon,
                    contentDescription = tile.entry.label,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    alpha = alpha
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GraphiteBorder)
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = WarnAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tile.entry.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (tile.installed) TextPrimary else TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!tile.installed) {
                Text(
                    "Not installed",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarnAmber,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
```

- [x] **Step 6: Compile and run all tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
```
Expected: exit 0.

- [x] **Step 7: Manual verification**

Install, launch, gear, `input text 079545`, `keyevent 66`, Close the lockdown dialog, then `adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml`:
- No node with text `Keyboard Emulator` (the emulator has no `com.rscja.scanner`, so the tile is hidden); the `Settings` tile is in row 2 next to Station 4/5 and every tile's bounds have the same height (`148 dp × 3 = 444 px` at 480 dpi; previously 474 vs 366 px).
- To see the station "Not installed" state: `adb -s emulator-5554 shell pm uninstall --user 0 com.mitas.ppnam.station5aa` (or `pm disable-user --user 0 com.mitas.ppnam.station5aa`), restart the launcher: the Station 5 tile shows a grey square with an amber warning glyph, "Station 5" muted, "Not installed" in amber at 12 sp, same height as its neighbours, tap does nothing. Re-install Station 5 afterwards from its repo APK (or `pm enable com.mitas.ppnam.station5aa` if disabled).

- [x] **Step 8: Commit**

```powershell
git add app/src/main/java/com/mitas/ppnam/launcheraa/KioskApps.kt app/src/test/java/com/mitas/ppnam/launcheraa/KioskAppsTest.kt app/src/main/java/com/mitas/ppnam/launcheraa/MainActivity.kt
git commit -m @'
fix(launcher): equal-height tiles, hide absent supervisor tiles, warning glyph

Closes UI audit launcher-08 (uneven rows), launcher-09 (dead blank tile) and
the Launcher row of static-26 (label wrap).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01ExhLEukYAu1CqjJUWPR64Q
'@
```

---

### Task 10: Full regression pass (no code changes)

**Files:** none.

- [x] **Step 1: Clean build and all tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --offline
.\gradlew.bat :app:assembleDebug --offline
git status --short
```
Expected: all tests pass (KioskAppsTest 8, KioskPolicyTest 4, LockdownPanelTest 6, PinGateTest 11); build succeeds; `git status --short` is empty (every change committed; any file noted as pre-existing in Task 1 is still unstaged).

- [x] **Step 2: Walk the audit's screen inventory once on the emulator**

Install the final APK and repeat, in order: grid portrait-only (Task 2 step 5), Back on grid stays (Task 6 step 3), gear → numeric pad with auto-focus → wrong PIN plural/singular wording → empty Enter → lockout, Cancel, reopen, force-stop, reopen, countdown, re-enable (Task 5 step 7), correct PIN → themed Lockdown dialog with one-line command (Tasks 7/8), Settings tile opens Android Settings and the supervisor tiles are hidden again after returning (unchanged behaviour), tiles equal height (Task 9). Capture `adb exec-out screencap -p > final_grid.png` and `final_pin.png` for the audit's `shots\launcher\` folder.

- [x] **Step 3: Report**

List the commits (`git log --oneline feature/remove-device-owner..HEAD`) — expected 8 commits (Tasks 2–9) — and hand the branch over for review; do not merge.

---

## Self-review

**Spec coverage (Launcher rows):**
- launcher-01 → Task 3 + Task 5 (persisted `PinGate`, `PrefsPinStore`).
- launcher-02 → Task 2 (manifest portrait + `adjustResize`; Task 5 also makes Enter submit so buttons are never required).
- launcher-03 → Task 5 (`KeyboardType.NumberPassword`, `PinGate.sanitize`).
- launcher-04 → Task 5 (`KeyboardActions(onDone)`, `FocusRequester` after first frame).
- launcher-05 → Task 3 (`Result.Blank`) + Task 5 message.
- launcher-06 → Task 5 (`LaunchedEffect` ticker, disabled field/Unlock, message clears).
- launcher-07 → Task 7.
- launcher-08 / launcher-09 / static-26 (Launcher row) → Task 9.
- launcher-10 / static-15 (Launcher) / static-21 (theme, palette, PIN keyboard, wording) → Tasks 4, 5, 8.
- launcher-11 → Task 5 (`textStyle`, text colours) + Task 8 (teal tint focus accent).
- launcher-12 → Task 6.
- §5 rows applied to the Launcher: gear icon (already `Icons.Filled.Settings`, unchanged), accent = admin teal (Task 8), dialog style (Task 8), error colour `#E25C5C` (Task 8), portrait lock (Task 2), `stateHidden|adjustResize` (Task 2), persisted PIN lockout with ticker and blank guard (Tasks 3, 5), Enter submits (Task 5), operator-facing strings (Task 7), plurals (Tasks 4, 5), Back policy = swallow on the grid (Task 6). Rows with no Launcher counterpart (login layout, pill vocabulary, Test & Apply, Diagnostics order, session/auto sign-out, workflow timeout) are not applicable — the Launcher has no login, broker or session.

**Not covered, and why:** static-23 "Launcher icon background white" — the Launcher's icon is a bespoke red/grey monogram on a white field with no README direction; recolouring the field to teal under that foreground is a design decision, not a bug fix, and the brief excludes icon changes from the Launcher. Nothing else in §3/§4/§6/§7 names the Launcher.

**Placeholder scan:** no TBD/TODO; every code step carries the full code; every test step carries the test code; manual steps name the adb commands and the expected dump/screenshot evidence.

**Type consistency:** `PinGate.Result.{Unlocked, Blank, Wrong(attemptsLeft: Int), LockedOut(secondsLeft: Long)}`, `lockoutSecondsLeft(): Long`, `lockedOutUntilMs: Long`, `sanitize(String): String` are used with those exact names in Tasks 3 and 5; `showsTile(entry, installed)` in Task 9 test and code; `NeutralTextButton(onClick, text)` and `LauncherTheme` defined and used only in Task 8 (Task 9's `setContent` snippet is the Task 8 version with one line changed); `pinGate` parameter threads `MainActivity` → `LauncherScreen` → `SupervisorPinDialog(gate = …)`.

**Review Focus:** 1 and 2 pinned by `PinGateTest` (Task 3); 3 and 4 by the Task 5 manual steps (reopen / force-stop during lockout; `keyevent 66`); 5 by the Task 6 manual step.
