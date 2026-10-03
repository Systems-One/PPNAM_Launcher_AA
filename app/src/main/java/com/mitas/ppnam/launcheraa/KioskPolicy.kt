package com.mitas.ppnam.launcheraa

/**
 * Decides when the launcher should pin itself into lock task (kiosk) mode.
 *
 * Device ownership is a hard precondition: without it, startLockTask() falls back to
 * escapable screen-pinning behind a system confirmation dialog — worse than not pinning.
 */
object KioskPolicy {

    fun shouldPin(isDeviceOwner: Boolean, kioskEnabled: Boolean, alreadyPinned: Boolean): Boolean {
        return isDeviceOwner && kioskEnabled && !alreadyPinned
    }

    /** `Settings.System.ACCELEROMETER_ROTATION`: 1 = auto-rotate follows the sensor, 0 = fixed. */
    const val SETTING_ACCELEROMETER_ROTATION = "accelerometer_rotation"

    /** `Settings.System.USER_ROTATION`: the fixed rotation while auto-rotate is off; 0 = portrait. */
    const val SETTING_USER_ROTATION = "user_rotation"

    /**
     * Device-wide rotation settings the launcher writes while kiosk is on or off.
     *
     * The launcher's own activity is already portrait in the manifest, but that does nothing
     * for the station apps or the system UI once an operator rotates the handheld. Locking
     * the *device* means turning auto-rotate off and parking the fixed rotation at portrait,
     * so every allowed app stays upright. Leaving kiosk restores auto-rotate; the fixed
     * rotation is left as-is because it only matters while auto-rotate is off.
     */
    fun rotationSettings(lockPortrait: Boolean): Map<String, Int> =
        if (lockPortrait) {
            mapOf(SETTING_ACCELEROMETER_ROTATION to 0, SETTING_USER_ROTATION to 0)
        } else {
            mapOf(SETTING_ACCELEROMETER_ROTATION to 1)
        }
}
