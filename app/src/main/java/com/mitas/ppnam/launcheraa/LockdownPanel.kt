package com.mitas.ppnam.launcheraa

/**
 * What the supervisor lockdown panel offers, given who the launcher is and whether kiosk
 * is currently on.
 *
 * Split out from the dialog for the same reason as [KioskPolicy]: the panel used to hold
 * its own copy of the kiosk flag, taken once when it opened. Exiting kiosk releases the
 * portrait lock and recreates MainActivity, so that copy went stale the moment it
 * mattered. The action is now derived from live state on every recomposition, and the
 * rule that decides it is a pure function that can be tested without a device.
 */
object LockdownPanel {

    enum class Action { ENTER_KIOSK, EXIT_KIOSK, NONE }

    /**
     * Without device-owner rights there is no lockdown to offer at all — [KioskPolicy]
     * refuses to pin in that case, so a button here would promise something it cannot do.
     */
    fun actionFor(isDeviceOwner: Boolean, kioskEnabled: Boolean): Action = when {
        !isDeviceOwner -> Action.NONE
        kioskEnabled -> Action.EXIT_KIOSK
        else -> Action.ENTER_KIOSK
    }
}
