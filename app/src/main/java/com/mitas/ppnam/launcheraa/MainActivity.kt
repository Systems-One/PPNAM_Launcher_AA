package com.mitas.ppnam.launcheraa

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay

// Station graphite palette (Station 2 ui/theme/Color.kt, Station 1 colors.xml) so the
// launcher and the apps it opens read as one family (UI audit static-21).
private val GraphiteBackground = Color(0xFF07101A)
private val GraphiteSurface = Color(0xFF102233)
private val GraphiteBorder = Color(0xFF25384C)
// Visual critique 2026-11-06 P2-2: GraphiteBorder is 1.35:1 on GraphiteSurface, so the
// unfocused PIN field had no visible edge. This mid tone is 3.3:1 (WCAG non-text 3:1).
private val FieldOutline = Color(0xFF5E7389)
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
                    tiles = tiles.value.filter {
                        it.entry in visible && KioskApps.showsTile(it.entry, it.installed)
                    },
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
        val notice = LockdownPanel.homeNotice(isDeviceOwner, isKioskEnabled)
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
                            "Station Launcher · v${BuildConfig.VERSION_NAME}",
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

                if (notice != LockdownPanel.HomeNotice.NONE) {
                    Spacer(Modifier.height(16.dp))
                    LockdownNotice(notice)
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

    /**
     * Amber-outlined strip under the header whenever the scanner is not locked down (visual
     * critique 2026-11-06 P1-1). Icon and words, not colour alone.
     */
    @Composable
    private fun LockdownNotice(notice: LockdownPanel.HomeNotice) {
        val text = when (notice) {
            LockdownPanel.HomeNotice.KIOSK_OFF -> "Kiosk is off: this scanner is unlocked"
            LockdownPanel.HomeNotice.NOT_PROVISIONED -> "Not provisioned: kiosk unavailable"
            LockdownPanel.HomeNotice.NONE -> return
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GraphiteSurface)
                .border(1.dp, WarnAmber, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = WarnAmber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
    }

    /**
     * Fixed-height tile so a two-line label or a "Not installed" line never makes one row
     * taller than its neighbours (UI audit launcher-08); a missing icon gets a warning
     * glyph instead of a blank square (launcher-09).
     */
    @Composable
    private fun AppTile(tile: Tile, onLaunch: (KioskApps.Entry) -> Unit) {
        val alpha = if (tile.installed) 1f else 0.35f
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .height(148.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GraphiteSurface)
                .clickable(enabled = tile.installed) { onLaunch(tile.entry) }
                .padding(vertical = 12.dp, horizontal = 8.dp)
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
            } else if (tile.entry.fieldColor != null && tile.entry.badge != null) {
                MissingStationIcon(Color(tile.entry.fieldColor), tile.entry.badge)
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GraphiteBorder)
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = WarnAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tile.entry.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (tile.installed) TextPrimary else TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // The status line is always laid out (blank when installed), so every tile centres
            // the same block and icons line up across a row. Adding it only when missing lifted
            // that tile's icon above its neighbours' (visual critique 2026-11-06 P3-10).
            // The same line tags the PIN-revealed tools, which otherwise mixed in with the
            // stations with nothing to say supervisor mode was on (P3-9).
            Text(
                when {
                    !tile.installed -> "Not installed"
                    tile.entry.supervisorOnly -> "Supervisor"
                    else -> " "
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (tile.installed) AdminTealTint else WarnAmber,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }

    /**
     * Placeholder for a station app that is not on the device: its own icon-pack field
     * colour and numeral, dimmed, with an amber warning disc where the real icon has its
     * badge. Replaces a generic grey square that dropped the station's identity (visual
     * critique 2026-11-06 P2-5).
     */
    @Composable
    private fun MissingStationIcon(field: Color, badge: String) {
        Box(modifier = Modifier.size(56.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .alpha(0.45f)
                    .clip(CircleShape)
                    .background(field)
            ) {
                Text(badge, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(GraphiteSurface)
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = WarnAmber,
                    modifier = Modifier.size(16.dp)
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
        // Seeded from the persisted counter so a dialog reopened at "1 attempt left" says so
        // instead of presenting a clean field right before a lockout.
        var result by remember {
            val left = gate.attemptsLeft()
            mutableStateOf<PinGate.Result?>(
                if (left < PinGate.MAX_ATTEMPTS) PinGate.Result.Wrong(left) else null
            )
        }
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
            if (result is PinGate.Result.LockedOut) {
                result = null
                // While disabled the field lost focus to Cancel, which then sat highlighted
                // like a pressed button with no keyboard up (visual critique 2026-11-06 P2-3).
                withFrameNanos { }
                focusRequester.requestFocus()
            }
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
                        unfocusedBorderColor = FieldOutline,
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
                            // Code well with a fade at the right edge: the bare line used to
                            // stop mid-glyph with nothing to say it scrolls (visual critique
                            // 2026-11-06 P2-6).
                            val commandScroll = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GraphiteBackground)
                                    .border(1.dp, GraphiteBorder, RoundedCornerShape(8.dp))
                            ) {
                                SelectionContainer {
                                    Text(
                                        stringResource(R.string.lockdown_provision_command),
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        softWrap = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(commandScroll)
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                                if (commandScroll.value < commandScroll.maxValue) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .size(width = 32.dp, height = 36.dp)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color.Transparent, GraphiteBackground)
                                                )
                                            )
                                    )
                                }
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
                        // No side padding, so the label lines up with the text above it
                        // (visual critique 2026-11-06 P3-8).
                        TextButton(
                            onClick = onRemoveOwner,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                        ) {
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
                    // Filled teal, like Unlock: re-locking is the safe, routine action. In
                    // red it read exactly like the irreversible Remove Device Owner beside it
                    // (visual critique 2026-11-06 P2-4).
                    LockdownPanel.Action.ENTER_KIOSK ->
                        Button(onClick = onEnterKiosk) {
                            Text("Enter Kiosk Mode")
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
