package com.mitas.ppnam.launcheraa

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log

/**
 * Dedicated-device (kiosk) lockdown, active only when this launcher has been provisioned
 * as device owner (`dpm set-device-owner com.mitas.ppnam.launcheraa/.KioskDeviceAdminReceiver`).
 *
 * While kiosk is enabled the device is confined to the [KioskApps] allowlist with this
 * launcher as the forced HOME activity: the device boots straight into the launcher grid,
 * and the Home button — or backing out of any allowed app — always returns to it. The
 * status bar shows only system info (wifi, battery, clock) and cannot be expanded;
 * Recents and every other app stay unavailable, and the whole device is held in portrait
 * (auto-rotate off, fixed rotation 0) so the station apps cannot be turned sideways either.
 * Supervisors toggle kiosk from the PIN-locked panel inside the launcher. Without device
 * ownership every call here is a no-op, so development installs behave normally.
 */
object KioskManager {

    private const val TAG = "KioskManager"
    private const val PREFS = "kiosk"
    private const val KEY_ENABLED = "kiosk_enabled"

    private val LOCKDOWN_RESTRICTIONS = listOf(
        // Safe mode boots with all third-party apps disabled — the classic kiosk escape.
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
    )

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(context: Context): ComponentName =
        ComponentName(context, KioskDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean =
        dpm(context).isDeviceOwnerApp(context.packageName)

    /** Kiosk defaults ON: a provisioned device is locked down until a supervisor opts out. */
    fun isKioskEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    /**
     * Pin this activity's task into lock task mode when policy allows. Called from
     * MainActivity's onResume so the launcher re-pins wherever its task surfaces (fresh
     * boot, Home button, a crash-relaunch). Boot itself is already covered by
     * lockTaskMode="if_whitelisted" in the manifest; this is the belt to that braces.
     */
    fun ensurePinned(activity: Activity) {
        val isDeviceOwner = isDeviceOwner(activity)
        val kioskEnabled = isKioskEnabled(activity)
        // The launcher's own orientation is fixed to portrait in the manifest (UI audit
        // launcher-02). The device-wide lock is re-asserted on every resume, not only when
        // (re)pinning: a provisioned device boots already pinned via lockTaskMode=
        // "if_whitelisted", and an app update relaunches into a still-locked task, so the
        // applyPolicies path below would otherwise never run on an upgraded device.
        if (isDeviceOwner && kioskEnabled) applyRotationLock(activity, lockPortrait = true)
        val shouldPin = KioskPolicy.shouldPin(
            isDeviceOwner = isDeviceOwner,
            kioskEnabled = kioskEnabled,
            alreadyPinned = isPinned(activity),
        )
        if (!shouldPin) return
        applyPolicies(activity)
        try {
            activity.startLockTask()
            Log.i(TAG, "Lock task started by ${activity.localClassName}")
        } catch (e: Exception) {
            Log.e(TAG, "startLockTask failed", e)
        }
    }

    /** Supervisor action: release the device until kiosk is re-enabled. */
    fun exitKiosk(activity: Activity) {
        setEnabled(activity, false)
        releasePolicies(activity)
        if (isPinned(activity)) {
            try {
                activity.stopLockTask()
            } catch (e: Exception) {
                Log.e(TAG, "stopLockTask failed", e)
            }
        }
        Log.w(TAG, "Kiosk disabled by supervisor")
    }

    /** Supervisor action: lock the device back down immediately. */
    fun enterKiosk(activity: Activity) {
        setEnabled(activity, true)
        ensurePinned(activity)
        Log.w(TAG, "Kiosk enabled by supervisor")
    }

    private fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isPinned(activity: Activity): Boolean {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun applyPolicies(context: Context) {
        val dpm = dpm(context)
        val admin = admin(context)
        try {
            dpm.setLockTaskPackages(admin, KioskApps.lockTaskPackages(context.packageName))
            LOCKDOWN_RESTRICTIONS.forEach { dpm.addUserRestriction(admin, it) }
            // No lock screen between boot and the launcher.
            dpm.setKeyguardDisabled(admin, true)
            // Explicitly false (not just "don't disable"): an earlier lockdown iteration
            // disabled the status bar outright, and that policy persists on a provisioned
            // device until undone. Lock task already keeps the bar unexpandable while
            // pinned; SYSTEM_INFO below brings back the wifi/battery/clock icons.
            dpm.setStatusBarDisabled(admin, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    admin,
                    // HOME: the Home button works, but only ever lands on this launcher.
                    // GLOBAL_ACTIONS: long-press power still offers power off/restart.
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                        DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                        DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
                )
            }

            // Forced HOME: boot, Home and closing an allowed app all land on the grid.
            // Clear leftovers first — an earlier iteration forced Station 1 as HOME.
            KioskApps.lockTaskPackages(context.packageName).forEach {
                dpm.clearPackagePersistentPreferredActivities(admin, it)
            }
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin, homeFilter, ComponentName(context, MainActivity::class.java),
            )
            applyRotationLock(context, lockPortrait = true)
        } catch (e: Exception) {
            Log.e(TAG, "Applying kiosk policies failed", e)
        }
    }

    /**
     * Device-wide portrait lock: auto-rotate off and the fixed rotation parked at portrait, so
     * the station apps and the system UI cannot be turned sideways either.
     *
     * Written through Settings.System under WRITE_SETTINGS. Device ownership is no help here:
     * DevicePolicyManager.setSystemSetting only allows the screen-brightness/timeout settings
     * and refuses rotation ("device owners cannot update accelerometer_rotation"), and
     * WRITE_SETTINGS is an app-op a device owner cannot grant itself. Provisioning grants it
     * (`appops set <pkg> WRITE_SETTINGS allow`); without it this logs and leaves rotation alone.
     */
    private fun applyRotationLock(context: Context, lockPortrait: Boolean) {
        if (!Settings.System.canWrite(context)) {
            Log.w(TAG, "WRITE_SETTINGS not granted; device rotation left as-is (re-run provisioning)")
            return
        }
        val resolver = context.contentResolver
        var changed = false
        var ok = true
        KioskPolicy.rotationSettings(lockPortrait).forEach { (setting, value) ->
            try {
                // Idempotent: called on every resume, so only touch a setting that differs.
                if (Settings.System.getInt(resolver, setting, -1) == value) return@forEach
                changed = true
                if (!Settings.System.putInt(resolver, setting, value)) ok = false
            } catch (e: Exception) {
                ok = false
                Log.e(TAG, "Writing $setting=$value failed", e)
            }
        }
        if (changed && ok) {
            Log.i(TAG, if (lockPortrait) "Device rotation locked to portrait" else "Device auto-rotate restored")
        }
    }

    private fun releasePolicies(context: Context) {
        val dpm = dpm(context)
        val admin = admin(context)
        try {
            KioskApps.lockTaskPackages(context.packageName).forEach {
                dpm.clearPackagePersistentPreferredActivities(admin, it)
            }
            // Empty allowlist, not just unpin: MainActivity is lockTaskMode="if_whitelisted",
            // so leaving the allowlist in place would re-pin the launcher on its next start
            // even though a supervisor turned kiosk off.
            dpm.setLockTaskPackages(admin, emptyArray())
            dpm.setKeyguardDisabled(admin, false)
            applyRotationLock(context, lockPortrait = false)
            // The user restrictions stay: they only block escapes (safe boot, factory
            // reset, side-loading) that a supervisor has no legitimate use for either.
        } catch (e: Exception) {
            Log.e(TAG, "Releasing kiosk policies failed", e)
        }
    }

    /**
     * Supervisor action: give up device ownership so the launcher (and every station app)
     * can be uninstalled and the device re-provisioned or handed back as a normal handheld.
     * Undoes everything [applyPolicies] set first — including the user restrictions, which
     * would otherwise keep blocking the factory reset — then clears ownership itself.
     */
    fun removeDeviceOwner(activity: Activity): Boolean {
        exitKiosk(activity)
        val dpm = dpm(activity)
        val admin = admin(activity)
        return try {
            LOCKDOWN_RESTRICTIONS.forEach { dpm.clearUserRestriction(admin, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(activity.packageName)
            // Next provisioning should start locked, not inherit this supervisor's opt-out.
            setEnabled(activity, true)
            Log.w(TAG, "Device owner removed by supervisor")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Removing device owner failed", e)
            false
        }
    }
}
