package com.mitas.ppnam.launcheraa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lock task allowlist is the whole security boundary of the kiosk: anything missing
 * from it is unreachable on a locked device, anything extra is an escape hatch.
 */
class KioskAppsTest {

    @Test
    fun `allowlist covers every tile entry`() {
        val packages = KioskApps.lockTaskPackages("com.mitas.ppnam.launcheraa").toList()
        KioskApps.entries.forEach { entry ->
            assertTrue("missing ${entry.packageName}", entry.packageName in packages)
        }
    }

    @Test
    fun `allowlist covers the launcher, stations, settings and keyboard emulator`() {
        val packages = KioskApps.lockTaskPackages("com.mitas.ppnam.launcheraa").toList()
        listOf(
            "com.mitas.ppnam.launcheraa",
            "com.mitas.ppnam.station1aa",
            "com.mitas.ppnam.station2aa",
            "com.mitas.ppnam.station3aa",
            "com.mitas.ppnam.station4aa",
            "com.mitas.ppnam.station5aa",
            "com.android.settings",
            "com.rscja.scanner",
        ).forEach { pkg -> assertTrue("missing $pkg", pkg in packages) }
    }

    @Test
    fun `operators see only the station tiles`() {
        assertEquals(
            listOf(
                "com.mitas.ppnam.station1aa",
                "com.mitas.ppnam.station2aa",
                "com.mitas.ppnam.station3aa",
                "com.mitas.ppnam.station4aa",
                "com.mitas.ppnam.station5aa",
            ),
            KioskApps.visibleEntries(supervisorUnlocked = false).map { it.packageName }
        )
    }

    @Test
    fun `supervisor PIN reveals keyboard emulator and settings`() {
        val visible = KioskApps.visibleEntries(supervisorUnlocked = true).map { it.packageName }
        assertEquals(KioskApps.entries.map { it.packageName }, visible)
        assertTrue("com.rscja.scanner" in visible)
        assertTrue("com.android.settings" in visible)
    }

    /** Hidden is not blocked: supervisor tiles must still launch while the device is pinned. */
    @Test
    fun `supervisor-only apps stay in the lock task allowlist`() {
        val packages = KioskApps.lockTaskPackages("com.mitas.ppnam.launcheraa").toList()
        KioskApps.entries.filter { it.supervisorOnly }.forEach {
            assertTrue("missing ${it.packageName}", it.packageName in packages)
        }
    }

    /** Keyboard Wedge was dropped from the kiosk: not needed, so not reachable. */
    @Test
    fun `allowlist excludes the keyboard wedge`() {
        val packages = KioskApps.lockTaskPackages("com.mitas.ppnam.launcheraa").toList()
        assertFalse("com.rscja.infowedge" in packages)
        assertFalse(KioskApps.entries.any { it.packageName == "com.rscja.infowedge" })
    }

    @Test
    fun `no duplicate packages in the allowlist`() {
        val packages = KioskApps.lockTaskPackages("com.mitas.ppnam.launcheraa").toList()
        assertEquals(packages.size, packages.distinct().size)
    }

    /** launcher-09: a dead "Not installed" supervisor tile tells a supervisor nothing. */
    @Test
    fun `absent supervisor-only apps are hidden, absent station apps stay visible`() {
        val keyboardEmulator = KioskApps.entries.first { it.packageName == "com.rscja.scanner" }
        val station3 = KioskApps.entries.first { it.packageName == "com.mitas.ppnam.station3aa" }
        assertFalse(KioskApps.showsTile(keyboardEmulator, installed = false))
        assertTrue(KioskApps.showsTile(keyboardEmulator, installed = true))
        assertTrue(KioskApps.showsTile(station3, installed = false))
        assertTrue(KioskApps.showsTile(station3, installed = true))
    }
}
