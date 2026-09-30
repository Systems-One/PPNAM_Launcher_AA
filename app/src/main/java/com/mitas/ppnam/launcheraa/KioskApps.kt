package com.mitas.ppnam.launcheraa

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * The sanctioned surface of a locked-down handheld: the only apps reachable while the
 * device is in kiosk mode. Every entry is whitelisted for lock task, so launching one
 * keeps the device pinned; everything else on the device stays unreachable.
 *
 * The launcher itself is the forced HOME activity, so boot, the Home button and closing
 * any of these apps all land back on the launcher grid.
 */
object KioskApps {

    private const val TAG = "KioskApps"

    /** [supervisorOnly] tiles stay off the grid until the supervisor PIN is entered. */
    data class Entry(val label: String, val packageName: String, val supervisorOnly: Boolean = false)

    const val ANDROID_SETTINGS_PACKAGE = "com.android.settings"

    /** Chainway's scan-service app, labelled "keyboardemulator" on the device (v12.x). */
    private const val KEYBOARD_EMULATOR_PACKAGE = "com.rscja.scanner"

    val entries = listOf(
        Entry("Station 1", "com.mitas.ppnam.station1aa"),
        Entry("Station 2", "com.mitas.ppnam.station2aa"),
        Entry("Station 3", "com.mitas.ppnam.station3aa"),
        Entry("Station 4", "com.mitas.ppnam.station4aa"),
        Entry("Station 5", "com.mitas.ppnam.station5aa"),
        Entry("Keyboard Emulator", KEYBOARD_EMULATOR_PACKAGE, supervisorOnly = true),
        Entry("Settings", ANDROID_SETTINGS_PACKAGE, supervisorOnly = true),
    )

    /** The tiles to show. Hidden entries stay in [lockTaskPackages] so a supervisor can
     *  still launch them while the device is pinned. */
    fun visibleEntries(supervisorUnlocked: Boolean): List<Entry> =
        entries.filter { supervisorUnlocked || !it.supervisorOnly }

    /** Everything allowed to run while pinned. Not every package has to be installed —
     *  the DPM accepts absent packages, which keeps one build valid for every handheld. */
    fun lockTaskPackages(ownPackage: String): Array<String> =
        (listOf(ownPackage) + entries.map { it.packageName })
            .distinct()
            .toTypedArray()

    fun isInstalled(context: Context, entry: Entry): Boolean =
        entry.packageName == ANDROID_SETTINGS_PACKAGE ||
            context.packageManager.getLaunchIntentForPackage(entry.packageName) != null

    fun launch(context: Context, entry: Entry): Boolean {
        val intent = if (entry.packageName == ANDROID_SETTINGS_PACKAGE) {
            // The Settings app has no stable launcher alias across OEM builds; the
            // ACTION_SETTINGS intent is the guaranteed front door.
            Intent(Settings.ACTION_SETTINGS)
        } else {
            context.packageManager.getLaunchIntentForPackage(entry.packageName)
                ?: return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Launching ${entry.packageName} failed", e)
            false
        }
    }
}
