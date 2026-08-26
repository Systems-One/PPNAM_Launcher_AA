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
import android.util.Log

/**
 * Dedicated-device (kiosk) lockdown, active only when this launcher has been provisioned
 * as device owner (`dpm set-device-owner com.mitas.ppnam.launcheraa/.KioskDeviceAdminReceiver`).
 *
 * While kiosk is enabled the device is confined to the [KioskApps] allowlist with this
 * launcher as the forced HOME activity: the device boots straight into the launcher grid,
 * and the Home button — or backing out of any allowed app — always returns to it. The
 * status bar shows only system info (wifi, battery, clock) and cannot be expanded;
 * Recents and every other app stay unavailable. Supervisors toggle kiosk from the
 * PIN-locked panel inside the launcher. Without device ownership every call here is a
 * no-op, so development installs behave normally.
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
        val shouldPin = KioskPolicy.shouldPin(
            isDeviceOwner = isDeviceOwner(activity),
            kioskEnabled = isKioskEnabled(activity),
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
        } catch (e: Exception) {
            Log.e(TAG, "Applying kiosk policies failed", e)
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
            // The user restrictions stay: they only block escapes (safe boot, factory
            // reset, side-loading) that a supervisor has no legitimate use for either.
        } catch (e: Exception) {
            Log.e(TAG, "Releasing kiosk policies failed", e)
        }
    }
}
