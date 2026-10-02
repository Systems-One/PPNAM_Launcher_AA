package com.mitas.ppnam.launcheraa

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay

// Station graphite palette (Station 2 ui/theme/Color.kt, Station 1 colors.xml) so the
// launcher and the apps it opens read as one family (UI audit static-21).
private val GraphiteBackground = Color(0xFF07101A)
private val GraphiteSurface = Color(0xFF102233)
private val GraphiteBorder = Color(0xFF25384C)
private val TextPrimary = Color(0xFFEDF4FB)
private val TextMuted = Color(0xFF9BAEC0)
private val DangerRed = Color(0xFFE25C5C)
private val WarnAmber = Color(0xFFF0A13A)

// Admin identity from the icon-pack README ("A — Teal"): field colour for filled
// controls, light tint where an accent has to be legible as text or a 1 dp stroke on
// graphite (#0F6E75 on #102233 is only 2.7:1).
private val AdminTeal = Color(0xFF0F6E75)
private val AdminTealTint = Color(0xFF9BCBCE)

private val LauncherColorScheme = darkColorScheme(
    primary = AdminTeal,
    onPrimary = Color.White,
    primaryContainer = AdminTeal,
    onPrimaryContainer = Color.White,
    secondary = AdminTealTint,
    onSecondary = GraphiteBackground,
    background = GraphiteBackground,
    onBackground = TextPrimary,
    surface = GraphiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = GraphiteSurface,
    onSurfaceVariant = TextMuted,
    surfaceContainerHigh = GraphiteSurface,
    error = DangerRed,
    onError = TextPrimary,
    outline = GraphiteBorder,
)

/** The only theme wrapper in the app; every dialog, button and field inherits it. */
@Composable
private fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LauncherColorScheme, content = content)
}

/** Dismiss action: muted text, so the red destructive confirm is the only loud button. */
@Composable
private fun NeutralTextButton(onClick: () -> Unit, text: String) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = TextMuted),
    ) { Text(text) }
}

class MainActivity : ComponentActivity() {

    private data class Tile(val entry: KioskApps.Entry, val icon: ImageBitmap?, val installed: Boolean)

    private val tiles = mutableStateOf<List<Tile>>(emptyList())

    /**
     * What the supervisor panel renders from. Observable and re-read on every resume and
     * after every supervisor action, so the panel always describes the device as it is
     * now rather than as it was when the dialog opened.
     */
    private val deviceOwner = mutableStateOf(false)
    private val kioskEnabled = mutableStateOf(true)

    /**
     * True once the supervisor PIN is entered: reveals the supervisor-only tiles. Survives
     * the recreation exitKiosk() triggers, but is dropped the moment the launcher leaves
     * the screen (an app opened, screen off) so the tiles never stay exposed.
     */
    private val supervisorUnlocked = mutableStateOf(false)

    /** Attempt counter + lockout outlive the dialog and the process (UI audit launcher-01). */
    private val pinGate by lazy { PinGate(PrefsPinStore(this), SUPERVISOR_PIN) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supervisorUnlocked.value = savedInstanceState?.getBoolean(KEY_SUPERVISOR_UNLOCKED) ?: false
        refreshKioskState()
        // The launcher is the floor of the device: Back must never drop a supervisor into
        // whatever app sat behind the task on an unprovisioned handheld (UI audit
        // launcher-12). Under lock task the OS already blocks Back; this covers the rest.
        // Compose dialogs run in their own window, so Back still closes an open dialog.
        onBackPressedDispatcher.addCallback(this) { /* swallow */ }
        setContent {
            LauncherTheme {
                val visible = KioskApps.visibleEntries(supervisorUnlocked.value)
                LauncherScreen(
                    tiles = tiles.value.filter { it.entry in visible },
                    onLaunch = { KioskApps.launch(this, it) },
                    onSupervisorUnlocked = { supervisorUnlocked.value = true },
                    pinGate = pinGate,
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
        pinGate: PinGate,
        isDeviceOwner: Boolean,
        isKioskEnabled: Boolean,
        onEnterKiosk: () -> Unit,
        onExitKiosk: () -> Unit,
        onRemoveOwner: () -> Unit,
    ) {
        // rememberSaveable, not remember: the dialog flags must survive any recreation of
        // this activity (process death, a future configuration change) — with plain
        // remember the panel once vanished mid-action and looked like a silent failure.
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
                gate = pinGate,
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
                    NeutralTextButton(onClick = { showRemoveConfirm = false }, text = "Cancel")
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

    /**
     * Same supervisor PIN and lockout rule as the station apps' settings screens; the
     * rule itself is [PinGate], this composable only renders it. Numeric keyboard, Enter
     * submits, field auto-focused, field and Unlock disabled while locked out, and the
     * countdown ticks every second (UI audit launcher-03/04/05/06).
     */
    @Composable
    private fun SupervisorPinDialog(gate: PinGate, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
        val resources = LocalContext.current.resources
        var pin by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<PinGate.Result?>(null) }
        var lockedOutUntil by remember { mutableStateOf(gate.lockedOutUntilMs) }
        var secondsLeft by remember { mutableStateOf(gate.lockoutSecondsLeft()) }
        val lockedOut = secondsLeft > 0
        val focusRequester = remember { FocusRequester() }

        // 1 s ticker while locked out; restarts whenever a new lockout begins.
        LaunchedEffect(lockedOutUntil) {
            while (true) {
                val left = gate.lockoutSecondsLeft()
                secondsLeft = left
                if (left == 0L) break
                delay(1_000)
            }
            if (result is PinGate.Result.LockedOut) result = null
        }

        // Focus (and so the keyboard) once the dialog window has had its first frame.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (!gate.isLockedOut()) focusRequester.requestFocus()
        }

        fun submit() {
            val outcome = gate.submit(pin)
            result = outcome
            lockedOutUntil = gate.lockedOutUntilMs
            when (outcome) {
                PinGate.Result.Unlocked -> onUnlocked()
                is PinGate.Result.Wrong, is PinGate.Result.LockedOut -> pin = ""
                PinGate.Result.Blank -> Unit
            }
        }

        val message: String? = when {
            lockedOut -> resources.getString(R.string.pin_locked_out, secondsLeft)
            result is PinGate.Result.Wrong -> {
                val left = (result as PinGate.Result.Wrong).attemptsLeft
                resources.getQuantityString(R.plurals.pin_attempts_left, left, left)
            }
            result == PinGate.Result.Blank -> resources.getString(R.string.pin_blank)
            else -> null
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = GraphiteSurface,
            title = { Text(stringResource(R.string.pin_dialog_title), color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = PinGate.sanitize(it) },
                    label = { Text(stringResource(R.string.pin_label)) },
                    singleLine = true,
                    enabled = !lockedOut,
                    isError = message != null,
                    supportingText = message?.let { { Text(it, color = DangerRed) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 6.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        disabledTextColor = TextMuted,
                        errorTextColor = TextPrimary,
                        cursorColor = AdminTealTint,
                        errorCursorColor = DangerRed,
                        focusedBorderColor = AdminTealTint,
                        unfocusedBorderColor = GraphiteBorder,
                        disabledBorderColor = GraphiteBorder,
                        errorBorderColor = DangerRed,
                        focusedLabelColor = AdminTealTint,
                        unfocusedLabelColor = TextMuted,
                        disabledLabelColor = TextMuted,
                        errorLabelColor = DangerRed,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            },
            confirmButton = {
                Button(onClick = { submit() }, enabled = !lockedOut) {
                    Text(stringResource(R.string.pin_unlock))
                }
            },
            dismissButton = {
                NeutralTextButton(onClick = onDismiss, text = stringResource(R.string.pin_cancel))
            },
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
            title = { Text(stringResource(R.string.lockdown_title), color = TextPrimary) },
            text = {
                Column {
                    when {
                        !isDeviceOwner -> {
                            Text(stringResource(R.string.lockdown_not_owner), color = TextMuted)
                            Spacer(Modifier.height(12.dp))
                            SelectionContainer {
                                Text(
                                    stringResource(R.string.lockdown_provision_command),
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    softWrap = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                )
                            }
                        }
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
            dismissButton = { NeutralTextButton(onClick = onDismiss, text = "Close") },
        )
    }

    private companion object {
        // Ported from the station apps' settings lock so every supervisor PIN matches.
        // TODO: move supervisor PIN to provisioning config
        const val SUPERVISOR_PIN = "079545"
        const val KEY_SUPERVISOR_UNLOCKED = "supervisor_unlocked"
    }
}

/** SharedPreferences-backed [PinGate.Store]. */
private class PrefsPinStore(context: Context) : PinGate.Store {
    private val prefs = context.getSharedPreferences("supervisor_pin", Context.MODE_PRIVATE)

    override var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(value) { prefs.edit().putInt(KEY_FAILED, value).apply() }

    override var lockedOutUntilMs: Long
        get() = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        set(value) { prefs.edit().putLong(KEY_LOCKED_UNTIL, value).apply() }

    private companion object {
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_out_until_ms"
    }
}
