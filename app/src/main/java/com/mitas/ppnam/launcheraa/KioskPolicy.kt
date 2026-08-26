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
}
