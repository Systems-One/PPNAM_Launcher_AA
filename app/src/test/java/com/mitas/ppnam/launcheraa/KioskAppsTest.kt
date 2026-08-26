package com.mitas.ppnam.launcheraa

import org.junit.Assert.assertEquals
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
    fun `allowlist covers the launcher, stations, settings, wedge and keyboard emulator`() {
        val packages = KioskApps.lockTaskPackages("com.mitas.ppnam.launcheraa").toList()
        listOf(
            "com.mitas.ppnam.launcheraa",
            "com.mitas.ppnam.station1aa",
            "com.mitas.ppnam.station2aa",
            "com.mitas.ppnam.station3aa",
            "com.mitas.ppnam.station4aa",
            "com.mitas.ppnam.station5aa",
            "com.android.settings",
            "com.rscja.infowedge",
            "com.rscja.scanner",
        ).forEach { pkg -> assertTrue("missing $pkg", pkg in packages) }
    }

    @Test
    fun `no duplicate packages in the allowlist`() {
        val packages = KioskApps.lockTaskPackages("com.mitas.ppnam.launcheraa").toList()
        assertEquals(packages.size, packages.distinct().size)
    }
}
