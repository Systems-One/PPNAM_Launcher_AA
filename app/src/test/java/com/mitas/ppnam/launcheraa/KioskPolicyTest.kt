package com.mitas.ppnam.launcheraa

import org.junit.Assert.assertEquals
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

class KioskRotationPolicyTest {

    @Test
    fun `kiosk on locks the device to portrait and disables auto-rotate`() {
        val settings = KioskPolicy.rotationSettings(lockPortrait = true)
        assertEquals(0, settings[KioskPolicy.SETTING_ACCELEROMETER_ROTATION])
        assertEquals(0, settings[KioskPolicy.SETTING_USER_ROTATION])
    }

    @Test
    fun `kiosk off restores auto-rotate and leaves the user rotation alone`() {
        val settings = KioskPolicy.rotationSettings(lockPortrait = false)
        assertEquals(1, settings[KioskPolicy.SETTING_ACCELEROMETER_ROTATION])
        assertFalse(settings.containsKey(KioskPolicy.SETTING_USER_ROTATION))
    }

    @Test
    fun `setting keys match the Android system settings names`() {
        assertEquals("accelerometer_rotation", KioskPolicy.SETTING_ACCELEROMETER_ROTATION)
        assertEquals("user_rotation", KioskPolicy.SETTING_USER_ROTATION)
    }
}
