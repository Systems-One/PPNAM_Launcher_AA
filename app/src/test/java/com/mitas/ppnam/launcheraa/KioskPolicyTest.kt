package com.mitas.ppnam.launcheraa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When to (re)pin the launcher into lock task mode. Pinning without device-owner rights
 * would fall back to escapable screen-pinning with a system confirmation dialog — worse
 * than not pinning, so device ownership is a hard precondition, not a nice-to-have.
 */
class KioskPolicyTest {

    @Test
    fun `pins when device owner with kiosk enabled and not already pinned`() {
        assertTrue(KioskPolicy.shouldPin(isDeviceOwner = true, kioskEnabled = true, alreadyPinned = false))
    }

    @Test
    fun `never pins without device-owner rights`() {
        assertFalse(KioskPolicy.shouldPin(isDeviceOwner = false, kioskEnabled = true, alreadyPinned = false))
    }

    @Test
    fun `does not pin when a supervisor has disabled kiosk`() {
        assertFalse(KioskPolicy.shouldPin(isDeviceOwner = true, kioskEnabled = false, alreadyPinned = false))
    }

    @Test
    fun `does not re-pin when already in lock task mode`() {
        assertFalse(KioskPolicy.shouldPin(isDeviceOwner = true, kioskEnabled = true, alreadyPinned = true))
    }
}
