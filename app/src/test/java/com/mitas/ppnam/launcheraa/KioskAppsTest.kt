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

    // Visual critique 2026-11-06 P2-5: a missing station's tile fell back to a generic
    // grey square and lost its identity. The fallback is drawn from these values, which
    // must match the icon pack (UI_Design/Android app logo directions/README.md).

    @Test
    fun `each station carries its icon-pack field colour and badge`() {
        val expected = mapOf(
            "com.mitas.ppnam.station1aa" to (0xFF1B4DA0L to "1"),
            "com.mitas.ppnam.station2aa" to (0xFF1D6B45L to "2"),
            "com.mitas.ppnam.station3aa" to (0xFF7B1418L to "3"),
            "com.mitas.ppnam.station4aa" to (0xFF55368CL to "4"),
            "com.mitas.ppnam.station5aa" to (0xFFA85F14L to "5"),
        )
        expected.forEach { (pkg, identity) ->
            val entry = KioskApps.entries.single { it.packageName == pkg }
            assertEquals(pkg, identity.first, entry.fieldColor)
            assertEquals(pkg, identity.second, entry.badge)
        }
    }

    @Test
    fun `supervisor tools have no suite identity to fall back to`() {
        KioskApps.entries.filter { it.supervisorOnly }.forEach {
            assertEquals(it.packageName, null, it.fieldColor)
            assertEquals(it.packageName, null, it.badge)
        }
    }

    // Skill critique 2026-11-06 P3-5: a station app that is on the device but disabled
    // was labelled "Not installed", which sends the supervisor to reinstall it instead of
    // re-enabling it in Settings.

    private val station = KioskApps.entries.first { !it.supervisorOnly }
    private val tool = KioskApps.entries.first { it.supervisorOnly }

    @Test
    fun `a launchable station has no status line`() {
        assertEquals(KioskApps.TileStatus.READY, KioskApps.tileStatus(station, launchable = true, present = true))
    }

    @Test
    fun `a station that is present but not launchable is disabled, not missing`() {
        assertEquals(KioskApps.TileStatus.DISABLED, KioskApps.tileStatus(station, launchable = false, present = true))
    }

    @Test
    fun `a station that is absent is not installed`() {
        assertEquals(KioskApps.TileStatus.NOT_INSTALLED, KioskApps.tileStatus(station, launchable = false, present = false))
    }

    @Test
    fun `a launchable supervisor tool is tagged supervisor`() {
        assertEquals(KioskApps.TileStatus.SUPERVISOR, KioskApps.tileStatus(tool, launchable = true, present = true))
    }

    @Test
    fun `each status has the wording the tile shows`() {
        assertEquals(" ", KioskApps.TileStatus.READY.label)
        assertEquals("Supervisor", KioskApps.TileStatus.SUPERVISOR.label)
        assertEquals("Disabled", KioskApps.TileStatus.DISABLED.label)
        assertEquals("Not installed", KioskApps.TileStatus.NOT_INSTALLED.label)
    }
}
