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

    /** The panel is re-derived, never toggled: a stale flag is what caused the bug. */
    @Test
    fun `action follows the live kiosk flag both ways`() {
        val owner = true
        assertEquals(LockdownPanel.Action.EXIT_KIOSK, LockdownPanel.actionFor(owner, true))
        assertEquals(LockdownPanel.Action.ENTER_KIOSK, LockdownPanel.actionFor(owner, false))
    }
}
