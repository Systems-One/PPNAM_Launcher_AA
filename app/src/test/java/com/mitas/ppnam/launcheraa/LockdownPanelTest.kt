package com.mitas.ppnam.launcheraa

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The supervisor panel must describe the device as it is *now*, not as it was when the
 * dialog opened: exiting kiosk recreates MainActivity, and the panel is rebuilt mid-action.
 */
class LockdownPanelTest {

    @Test
    fun `offers the exit when kiosk is active`() {
        assertEquals(
            LockdownPanel.Action.EXIT_KIOSK,
            LockdownPanel.actionFor(isDeviceOwner = true, kioskEnabled = true)
        )
    }

    @Test
    fun `offers the way back in once a supervisor has exited`() {
        assertEquals(
            LockdownPanel.Action.ENTER_KIOSK,
            LockdownPanel.actionFor(isDeviceOwner = true, kioskEnabled = false)
        )
    }

    @Test
    fun `offers nothing without device-owner rights`() {
        assertEquals(
            LockdownPanel.Action.NONE,
            LockdownPanel.actionFor(isDeviceOwner = false, kioskEnabled = true)
        )
        assertEquals(
            LockdownPanel.Action.NONE,
            LockdownPanel.actionFor(isDeviceOwner = false, kioskEnabled = false)
        )
    }

    /** Removal is the only way off a provisioned device short of a recovery wipe. */
    @Test
    fun `offers device-owner removal whenever the launcher owns the device`() {
        assertEquals(true, LockdownPanel.offersRemoval(isDeviceOwner = true))
    }

    @Test
    fun `offers no removal without device-owner rights`() {
        assertEquals(false, LockdownPanel.offersRemoval(isDeviceOwner = false))
    }

    /** The panel is re-derived, never toggled: a stale flag is what caused the bug. */
    @Test
    fun `action follows the live kiosk flag both ways`() {
        val owner = true
        assertEquals(LockdownPanel.Action.EXIT_KIOSK, LockdownPanel.actionFor(owner, true))
        assertEquals(LockdownPanel.Action.ENTER_KIOSK, LockdownPanel.actionFor(owner, false))
    }

    // Visual critique 2026-11-06 P1-1: with kiosk off the home grid looked exactly like a
    // locked scanner, so a supervisor could walk away from an unlocked device unaware.

    @Test
    fun `home shows no lockdown notice while kiosk is active`() {
        assertEquals(
            LockdownPanel.HomeNotice.NONE,
            LockdownPanel.homeNotice(isDeviceOwner = true, kioskEnabled = true)
        )
    }

    @Test
    fun `home warns when a supervisor has switched kiosk off`() {
        assertEquals(
            LockdownPanel.HomeNotice.KIOSK_OFF,
            LockdownPanel.homeNotice(isDeviceOwner = true, kioskEnabled = false)
        )
    }

    @Test
    fun `home warns that an unprovisioned scanner is not locked down`() {
        assertEquals(
            LockdownPanel.HomeNotice.NOT_PROVISIONED,
            LockdownPanel.homeNotice(isDeviceOwner = false, kioskEnabled = true)
        )
        assertEquals(
            LockdownPanel.HomeNotice.NOT_PROVISIONED,
            LockdownPanel.homeNotice(isDeviceOwner = false, kioskEnabled = false)
        )
    }
}
