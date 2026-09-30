package com.mitas.ppnam.launcheraa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

private val GraphiteBackground = Color(0xFF07101A)
private val GraphiteSurface = Color(0xFF0E1B29)
private val GraphiteBorder = Color(0xFF1D2F42)
private val TextPrimary = Color(0xFFEDF4FB)
private val TextMuted = Color(0xFF9BAEC0)
private val DangerRed = Color(0xFFE5484D)
private val WarnAmber = Color(0xFFF0A13A)

class MainActivity : ComponentActivity() {

    private data class Tile(val entry: KioskApps.Entry, val icon: ImageBitmap?, val installed: Boolean)

    private val tiles = mutableStateOf<List<Tile>>(emptyList())

    /**
     * What the supervisor panel renders from. Observable and re-read on every resume and
     * after every supervisor action: exitKiosk() releases the portrait lock, which
     * recreates this activity, so a value read once at composition is stale by the time
     * the panel redraws.
     */
    private val deviceOwner = mutableStateOf(false)
    private val kioskEnabled = mutableStateOf(true)

    /**
     * True once the supervisor PIN is entered: reveals the supervisor-only tiles. Survives
     * the recreation exitKiosk() triggers, but is dropped the moment the launcher leaves
     * the screen (an app opened, screen off) so the tiles never stay exposed.
     */
    private val supervisorUnlocked = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supervisorUnlocked.value = savedInstanceState?.getBoolean(KEY_SUPERVISOR_UNLOCKED) ?: false
        refreshKioskState()
        setContent {
            val visible = KioskApps.visibleEntries(supervisorUnlocked.value)
            LauncherScreen(
                tiles = tiles.value.filter { it.entry in visible },
                onLaunch = { KioskApps.launch(this, it) },
                onSupervisorUnlocked = { supervisorUnlocked.value = true },
                isDeviceOwner = deviceOwner.value,
                isKioskEnabled = kioskEnabled.value,
                onEnterKiosk = {
                    KioskManager.enterKiosk(this)
                    refreshKioskState()
                },
                onExitKiosk = {
                    KioskManager.exitKiosk(this)
                    refreshKioskState()
                },
                onRemoveOwner = {
                    KioskManager.removeDeviceOwner(this)
                    refreshKioskState()
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        KioskManager.ensurePinned(this)
        refreshKioskState()
        // Rebuilt on every resume so a tile appears the moment its app gets installed.
        tiles.value = KioskApps.entries.map { entry ->
            val installed = KioskApps.isInstalled(this, entry)
            val icon = try {
                packageManager.getApplicationIcon(entry.packageName).toBitmap().asImageBitmap()
            } catch (e: Exception) {
                null
            }
            Tile(entry, icon, installed)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) supervisorUnlocked.value = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SUPERVISOR_UNLOCKED, supervisorUnlocked.value)
    }

    private fun refreshKioskState() {
        deviceOwner.value = KioskManager.isDeviceOwner(this)
        kioskEnabled.value = KioskManager.isKioskEnabled(this)
    }

    @Composable
    private fun LauncherScreen(
        tiles: List<Tile>,
        onLaunch: (KioskApps.Entry) -> Unit,
        onSupervisorUnlocked: () -> Unit,
        isDeviceOwner: Boolean,
        isKioskEnabled: Boolean,
        onEnterKiosk: () -> Unit,
        onExitKiosk: () -> Unit,
        onRemoveOwner: () -> Unit,
    ) {
        // rememberSaveable, not remember: exiting kiosk releases the orientation lock and
        // recreates this activity. With plain remember the panel vanished mid-action and
        // looked to the supervisor like the exit had silently failed.
        var showPinDialog by rememberSaveable { mutableStateOf(false) }
        var showLockdownDialog by rememberSaveable { mutableStateOf(false) }
        var showRemoveConfirm by rememberSaveable { mutableStateOf(false) }

        Surface(modifier = Modifier.fillMaxSize(), color = GraphiteBackground) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "PPNAM",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp
                            ),
                            color = TextPrimary
                        )
                        Text(
                            "Station Launcher",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = { showPinDialog = true }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Device lockdown",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(tiles) { tile ->
                        AppTile(tile = tile, onLaunch = onLaunch)
                    }
                }
            }
        }

        if (showPinDialog) {
            SupervisorPinDialog(
                onDismiss = { showPinDialog = false },
                onUnlocked = {
                    showPinDialog = false
                    onSupervisorUnlocked()
                    showLockdownDialog = true
                }
            )
        }

        if (showLockdownDialog) {
            LockdownDialog(
                isDeviceOwner = isDeviceOwner,
                isKioskEnabled = isKioskEnabled,
                onEnterKiosk = onEnterKiosk,
                onExitKiosk = onExitKiosk,
                onRemoveOwner = {
                    showLockdownDialog = false
                    showRemoveConfirm = true
                },
                onDismiss = { showLockdownDialog = false },
            )
        }

        if (showRemoveConfirm) {
            AlertDialog(
                onDismissRequest = { showRemoveConfirm = false },
                containerColor = GraphiteSurface,
                title = { Text("Remove Device Owner?", color = TextPrimary) },
                text = {
                    Text(
                        "Kiosk mode is switched off permanently and the PPNAM Launcher gives up " +
                            "control of this scanner, so the apps can be uninstalled. Putting it " +
                            "back into service requires provisioning it again.",
                        color = TextMuted
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRemoveConfirm = false
                        onRemoveOwner()
                    }) { Text("Remove", color = DangerRed) }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
                },
            )
        }
    }

    @Composable
    private fun AppTile(tile: Tile, onLaunch: (KioskApps.Entry) -> Unit) {
        val alpha = if (tile.installed) 1f else 0.35f
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(GraphiteSurface)
                .clickable(enabled = tile.installed) { onLaunch(tile.entry) }
                .padding(vertical = 18.dp, horizontal = 8.dp)
        ) {
            if (tile.icon != null) {
                Image(
                    bitmap = tile.icon,
                    contentDescription = tile.entry.label,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    alpha = alpha
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GraphiteBorder)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tile.entry.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (tile.installed) TextPrimary else TextMuted,
                textAlign = TextAlign.Center
            )
            if (!tile.installed) {
                Text(
                    "Not installed",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    /** Same supervisor PIN and lockout behaviour as the station apps' settings screens. */
    @Composable
    private fun SupervisorPinDialog(onDismiss: () -> Unit, onUnlocked: () -> Unit) {
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var attempts by remember { mutableStateOf(0) }
        var lockedOutUntil by remember { mutableStateOf(0L) }

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = GraphiteSurface,
            title = { Text("Supervisor PIN", color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6) pin = it },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = DangerRed, style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    when {
                        now < lockedOutUntil -> {
                            val left = (lockedOutUntil - now + 999) / 1_000
                            error = "Too many attempts. Try again in ${left}s."
                            pin = ""
                        }
                        pin == CORRECT_PIN -> onUnlocked()
                        else -> {
                            pin = ""
                            attempts++
                            if (attempts >= MAX_PIN_ATTEMPTS) {
                                lockedOutUntil = now + PIN_LOCKOUT_MS
                                attempts = 0
                                error = "Too many attempts. Try again in ${PIN_LOCKOUT_MS / 1_000}s."
                            } else {
                                val left = MAX_PIN_ATTEMPTS - attempts
                                error = "Incorrect PIN. $left attempt${if (left == 1) "" else "s"} left."
                            }
                        }
                    }
                }) { Text("Unlock") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }

    @Composable
    private fun LockdownDialog(
        isDeviceOwner: Boolean,
        isKioskEnabled: Boolean,
        onEnterKiosk: () -> Unit,
        onExitKiosk: () -> Unit,
        onRemoveOwner: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = GraphiteSurface,
            title = { Text("Device Lockdown", color = TextPrimary) },
            text = {
                Column {
                    when {
                        !isDeviceOwner -> Text(
                            "This launcher is not the device owner. Provision it with:\n\n" +
                                "dpm set-device-owner com.mitas.ppnam.launcheraa/.KioskDeviceAdminReceiver",
                            color = TextMuted
                        )
                        isKioskEnabled -> Text(
                            "Kiosk is active: the device is confined to the apps on this " +
                                "launcher. Exiting frees Home, Recents and all other apps " +
                                "until kiosk is re-entered.",
                            color = TextMuted
                        )
                        else -> Text(
                            "Kiosk is off: the device is unlocked. Entering kiosk confines " +
                                "the device to the apps on this launcher.",
                            color = TextMuted
                        )
                    }
                    if (LockdownPanel.offersRemoval(isDeviceOwner)) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onRemoveOwner) {
                            Text("Remove Device Owner", color = DangerRed)
                        }
                    }
                }
            },
            confirmButton = {
                when (LockdownPanel.actionFor(isDeviceOwner, isKioskEnabled)) {
                    LockdownPanel.Action.EXIT_KIOSK ->
                        TextButton(onClick = onExitKiosk) {
                            Text("Exit Kiosk Mode", color = WarnAmber)
                        }
                    LockdownPanel.Action.ENTER_KIOSK ->
                        TextButton(onClick = onEnterKiosk) {
                            Text("Enter Kiosk Mode", color = DangerRed)
                        }
                    LockdownPanel.Action.NONE -> Unit
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        )
    }

    private companion object {
        // Ported from the station apps' settings lock so every supervisor PIN matches.
        const val CORRECT_PIN = "079545"
        const val MAX_PIN_ATTEMPTS = 5
        const val PIN_LOCKOUT_MS = 30_000L
        const val KEY_SUPERVISOR_UNLOCKED = "supervisor_unlocked"
    }
}
