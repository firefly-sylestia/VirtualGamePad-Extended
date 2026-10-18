package com.fireflysylestia.virtualgamepadextended.ui.screens

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fireflysylestia.virtualgamepadextended.R
import com.fireflysylestia.virtualgamepadextended.data.ButtonAnchor
import com.fireflysylestia.virtualgamepadextended.data.ButtonComponent
import com.fireflysylestia.virtualgamepadextended.data.ButtonConfig
import com.fireflysylestia.virtualgamepadextended.data.ControllerAction
import com.fireflysylestia.virtualgamepadextended.data.ControllerLayout
import com.fireflysylestia.virtualgamepadextended.data.GamepadSkin
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import com.fireflysylestia.virtualgamepadextended.data.GamepadGeometry
import com.fireflysylestia.virtualgamepadextended.data.HorizontalAnchor
import com.fireflysylestia.virtualgamepadextended.data.OFFSET_VALUE_RANGE
import com.fireflysylestia.virtualgamepadextended.data.horizontal
import com.fireflysylestia.virtualgamepadextended.data.PreviewBase
import com.fireflysylestia.virtualgamepadextended.data.SettingsRepository
import com.fireflysylestia.virtualgamepadextended.data.buttonConfigsForLayout
import com.fireflysylestia.virtualgamepadextended.data.CustomKeyBinding
import com.fireflysylestia.virtualgamepadextended.data.CustomKeyOption
import com.fireflysylestia.virtualgamepadextended.data.controlLabel
import com.fireflysylestia.virtualgamepadextended.data.defaultControlKeyBindings
import com.fireflysylestia.virtualgamepadextended.data.normaliseControlKeyBindings
import com.fireflysylestia.virtualgamepadextended.data.canonicalKeyChord
import com.fireflysylestia.virtualgamepadextended.data.canonicalKeyName
import com.fireflysylestia.virtualgamepadextended.data.controlsForComponent
import com.fireflysylestia.virtualgamepadextended.data.customKeyOptionGroups
import com.fireflysylestia.virtualgamepadextended.data.keyChordParts
import com.fireflysylestia.virtualgamepadextended.data.keyChordValue
import com.fireflysylestia.virtualgamepadextended.data.knownCustomKeyNames
import com.fireflysylestia.virtualgamepadextended.data.defaultButtonConfigs
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings
import com.fireflysylestia.virtualgamepadextended.data.defaultCustomKeyBindings
import com.fireflysylestia.virtualgamepadextended.data.normaliseCustomKeyBindings
import com.fireflysylestia.virtualgamepadextended.ui.composables.ButtonConfigEditor
import com.fireflysylestia.virtualgamepadextended.ui.composables.DrawGamepad
import com.fireflysylestia.virtualgamepadextended.ui.composables.ListItemPicker
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

private const val logTag = "GamepadCustomization"

/**
 * Sanitizes button configurations to ensure all components have a valid config.
 */
private fun sanitizeButtonConfigs(configs: Map<ButtonComponent, ButtonConfig>): Map<ButtonComponent, ButtonConfig> {
    return ButtonComponent.entries.associateWith { component ->
        // A control added after these configs were saved is missing from them:
        // take its designed default so it lands where the layout expects it.
        configs[component] ?: defaultButtonConfigs[component] ?: ButtonConfig.default(component)
    }
}

/**
 * Full-screen live gamepad preview (read-only, drives the shared state).
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun GamepadPreview(
    buttonConfigs: Map<ButtonComponent, ButtonConfig>,
    controllerLayout: ControllerLayout,
    bindings: Map<String, ControllerAction>,
    skin: GamepadSkin = GamepadSkin.DEFAULT,
    customKeyBindings: List<CustomKeyBinding> = defaultCustomKeyBindings,
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp
    val screenWidth = configuration.screenWidthDp

    val gamepadState = remember { GamepadReading() }

    // The preview has to wear the same skin as the live gamepad, or the player is
    // editing a controller that does not exist.
    CompositionLocalProvider(LocalGamepadSkin provides skin) {
        DrawGamepad(
            widthDp = screenWidth,
            heightDp = screenHeight,
            gamepadState = gamepadState,
            buttonConfigs = buttonConfigs,
            controllerLayout = controllerLayout,
            bindings = bindings,
            customKeyBindings = customKeyBindings,
        )
    }
}

/**
 * An editable view of the real gamepad.
 *
 * The controls are the same composables the game uses, drawn at the same places
 * and sizes, so this is exactly what the player will see. Over the top sits one
 * invisible-to-the-eye tap target per control, in the rectangle
 * [GamepadGeometry] computes for it: tap a control to open its small floating
 * editor, or drag it to move it. Handles draw a faint outline around each control
 * so the tap targets are visible, and a hidden control keeps its name on show so
 * it can still be found and switched on.
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun GamepadEditorPreview(
    buttonConfigs: Map<ButtonComponent, ButtonConfig>,
    controllerLayout: ControllerLayout,
    bindings: Map<String, ControllerAction>,
    showHandles: Boolean,
    selected: ButtonComponent?,
    onSelect: (ButtonComponent) -> Unit,
    onConfigChange: (ButtonComponent, ButtonConfig) -> Unit,
    modifier: Modifier = Modifier,
    skin: GamepadSkin = GamepadSkin.DEFAULT,
    customKeyBindings: List<CustomKeyBinding> = defaultCustomKeyBindings,
    controlKeyBindings: Map<String, String> = emptyMap(),
    onActionChange: (String, ControllerAction) -> Unit = { _, _ -> },
    onControlKeyChange: (String, String) -> Unit = { _, _ -> },
    onCustomKeyChange: (Int, CustomKeyBinding) -> Unit = { _, _ -> },
) {
    val gamepadState = remember { GamepadReading() }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        val widthDp = maxWidth.value.toInt()
        val heightDp = maxHeight.value.toInt()
        val baseDp = GamepadGeometry.baseDp(widthDp.coerceAtLeast(1), heightDp.coerceAtLeast(1))
        val basePx = with(density) { baseDp.dp.toPx() }

        // Same skin as the live gamepad, so the handles sit on the controls the
        // player actually gets.
        CompositionLocalProvider(LocalGamepadSkin provides skin) {
            DrawGamepad(
                widthDp = widthDp,
                heightDp = heightDp,
                gamepadState = gamepadState,
                buttonConfigs = buttonConfigs,
                controllerLayout = controllerLayout,
                    bindings = bindings,
                customKeyBindings = customKeyBindings,
            )
        }

        ButtonComponent.entries.forEach { component ->
            val config = buttonConfigs[component] ?: ButtonConfig.default(component)
            // The drag writes the offsets it is reading, so the gesture must not be
            // keyed on the config: every drag event changes it, and restarting the
            // pointer input mid-drag cancels the gesture that is running — the
            // control moved one step and then stopped following the finger. The
            // gesture reads the newest config from here instead.
            val draggedConfig by rememberUpdatedState(config)
            val rect = GamepadGeometry.bounds(component, config, widthDp, heightDp)
            val isSelected = selected == component

            Box(
                modifier = Modifier
                    .offset(x = rect.left.dp, y = rect.top.dp)
                    .size(rect.width.dp, rect.height.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (showHandles || isSelected) {
                            Modifier.border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    config.visible -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .alpha(if (config.visible) 1f else 0.6f)
                    .clickable { onSelect(component) }
                    .pointerInput(component, basePx) {
                        // No onSelect here: selecting on drag start popped the editor
                        // the instant the finger moved, so a control could never be
                        // dragged. A tap (handled by the clickable above) opens the
                        // editor; a drag only moves.
                        //
                        // Travel is measured from the config the drag started on, and
                        // each event adds to a running total, rather than adding to
                        // whatever config is newest. Adding to the newest one meant
                        // every event depended on the previous move having made it
                        // back through the state round trip: the events that arrived
                        // before it did all started from the same old value, so the
                        // control crept along and then stalled, which is what made it
                        // feel stuck part-way across the screen.
                        var startConfig: ButtonConfig? = null
                        var travelledX = 0f
                        var travelledY = 0f
                        detectDragGestures(
                            onDragStart = {
                                startConfig = draggedConfig
                                travelledX = 0f
                                travelledY = 0f
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                travelledX += drag.x
                                travelledY += drag.y
                                val base = startConfig ?: draggedConfig
                                // Both axes follow the finger, so a control can be
                                // put anywhere on screen by dragging it there
                                // instead of pushing two sliders until it looks right.
                                val nx = (base.offsetX + travelledX / basePx)
                                    .coerceIn(OFFSET_VALUE_RANGE)
                                val ny = (base.offsetY + travelledY / basePx)
                                    .coerceIn(OFFSET_VALUE_RANGE)
                                onConfigChange(
                                    component,
                                    base.copy(offsetX = nx, offsetY = ny)
                                )
                            },
                            onDragEnd = { startConfig = null },
                            onDragCancel = { startConfig = null },
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // A hidden control is labelled, so it can be found at a glance.
                if (!config.visible) {
                    Text(
                        text = stringResource(component.nameRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.alpha(0.7f)
                    )
                }
            }
        }
    }
}

/**
 * The small floating editor for one control, shown over the preview.
 *
 * It opens opposite the control that was tapped, so it never covers the very
 * thing being edited.
 */
@Composable
private fun FloatingControlEditor(
    component: ButtonComponent,
    config: ButtonConfig,
    controlIsOnRightHalf: Boolean,
    onConfigChange: (ButtonConfig) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    bindings: Map<String, ControllerAction> = emptyMap(),
    controlKeyBindings: Map<String, String> = emptyMap(),
    customKeyBindings: List<CustomKeyBinding> = defaultCustomKeyBindings,
    onActionChange: (String, ControllerAction) -> Unit = { _, _ -> },
    onControlKeyChange: (String, String) -> Unit = { _, _ -> },
    onCustomKeyChange: (Int, CustomKeyBinding) -> Unit = { _, _ -> },
) {
    BoxWithConstraints(
        modifier = modifier
            // A tap anywhere but on the card closes the editor, which is what every
            // other floating panel in the app does. The card is drawn over this, so
            // a tap that lands on the card never reaches it.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        // Wide enough for the sliders, and never wider than the preview.
        val cardWidth = (maxWidth.value * 0.42f).coerceIn(220f, 330f).dp
        Card(
            modifier = Modifier
                // Opposite side to the control being edited, so it stays visible.
                .align(if (controlIsOnRightHalf) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(10.dp)
                .width(cardWidth)
                // Swallows taps on the card's own background, so reading a label or
                // touching the space between two rows does not throw the editor
                // away. A control inside the card takes its own taps first.
                .pointerInput(Unit) { detectTapGestures { } },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(component.nameRes),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.customization_done)
                        )
                    }
                }
                // Remap first: the reason most taps open this editor at all is
                // "what does this button send?", and the answer is per key — each
                // button the component stands for gets its own row below.
                ControlRemapSection(
                    component = component,
                    bindings = bindings,
                    controlKeyBindings = controlKeyBindings,
                    customKeyBindings = customKeyBindings,
                    onActionChange = onActionChange,
                    onControlKeyChange = onControlKeyChange,
                    onCustomKeyChange = onCustomKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                ButtonConfigEditor(
                    component = component,
                    config = config,
                    onConfigChange = onConfigChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}



/**
 * The "Sends" block of the per-control editor: one remap row per button the
 * tapped component stands for, so remapping happens per key on the screen the
 * player is already looking at, instead of only through the whole-pad table.
 *
 * A component with no digital output of its own (the analog sticks, the
 * phone-only controls) renders nothing and leaves its editor as it was.
 */
@Composable
private fun ControlRemapSection(
    component: ButtonComponent,
    bindings: Map<String, ControllerAction>,
    controlKeyBindings: Map<String, String>,
    customKeyBindings: List<CustomKeyBinding>,
    onActionChange: (String, ControllerAction) -> Unit,
    onControlKeyChange: (String, String) -> Unit,
    onCustomKeyChange: (Int, CustomKeyBinding) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCustomKeys = component == ButtonComponent.CUSTOM_KEYS
    val controlIds = controlsForComponent(component)
    if (!isCustomKeys && controlIds.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Sends", style = MaterialTheme.typography.titleSmall)
        Text(
            "What each button sends. A bound key replaces the gamepad action. Two " +
                "keys make a chord: pick a key, then a 2nd key — on a tap both go down " +
                "in that order and release together.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isCustomKeys) {
            customKeyBindings.forEachIndexed { index, binding ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        binding.label.ifBlank { "Key ${index + 1}" },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    KeyChordEditor(
                        value = binding.key,
                        onCommit = { value ->
                            onCustomKeyChange(index, binding.copy(key = value))
                        },
                        blankHint = "No key: this button sends nothing.",
                    )
                }
            }
        } else {
            controlIds.forEach { id ->
                val hasAction = id in defaultControllerBindings
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(controlLabel(id), style = MaterialTheme.typography.labelMedium)
                    if (hasAction) {
                        ListItemPicker(
                            modifier = Modifier.fillMaxWidth(),
                            list = ControllerAction.entries.asIterable(),
                            selectedItem = bindings[id]
                                ?: defaultControllerBindings.getValue(id),
                            label = "Gamepad action",
                            formattedDisplay = { action ->
                                Text(action.name.replace('_', ' '))
                            },
                            onItemSelected = { action -> onActionChange(id, action) },
                        )
                    }
                    KeyChordEditor(
                        value = controlKeyBindings[id].orEmpty(),
                        onCommit = { value -> onControlKeyChange(id, value) },
                        blankHint = if (hasAction) {
                            "Sends its gamepad action."
                        } else {
                            "Sends its trigger pressure; a key replaces it and goes " +
                                "down on a pull past half."
                        },
                    )
                }
            }
        }
    }
}

/**
 * The key fields of one remap row: the key, and an optional second key that
 * turns the pair into a chord.
 *
 * Edits commit as they are typed, because the editor is not a dialog with a
 * Save button — the value is stored raw and canonicalised only for the hint, so
 * typing "1" stays "1" under the finger even though the PC resolves it as
 * Digit1. The second field only exists once there is a first key: a chord with
 * an empty first half would be reordered by the normaliser when it is read back.
 */
@Composable
private fun KeyChordEditor(
    value: String,
    onCommit: (String) -> Unit,
    blankHint: String,
    modifier: Modifier = Modifier,
) {
    val parts = keyChordParts(value)
    // The stored value is the source of truth; the fields are local views of it,
    // rebuilt whenever it changes from elsewhere (a pick above, an import).
    var first by remember(value) { mutableStateOf(parts.getOrElse(0) { "" }) }
    var second by remember(value) { mutableStateOf(parts.getOrElse(1) { "" }) }
    // No picker open (null), the first key (false), or the second one (true).
    var pickingSecond by remember { mutableStateOf<Boolean?>(null) }

    fun commit() {
        val picked = listOf(first.trim(), second.trim()).filter { it.isNotEmpty() }
        onCommit(keyChordValue(picked))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = first,
                onValueChange = { text ->
                    first = text
                    commit()
                },
                label = { Text("PC key") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pickingSecond = false }) { Text("Pick") }
        }

        if (first.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = second,
                    onValueChange = { text ->
                        second = text
                        commit()
                    },
                    label = { Text("2nd key (chord)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { pickingSecond = true }) { Text("Pick") }
            }
        }

        val unknownParts = parts.map(::canonicalKeyName)
            .filter { part -> part.lowercase() !in knownCustomKeyNames }
        Text(
            when {
                parts.isEmpty() -> blankHint
                unknownParts.isNotEmpty() ->
                    "\"${unknownParts.first()}\" is not a key the PC knows; it is ignored there."
                parts.size == 2 ->
                    "Sends ${canonicalKeyName(parts[0])} + ${canonicalKeyName(parts[1])}: " +
                        "both go down in that order, then release together."
                else -> "Sends ${canonicalKeyName(parts[0])}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (unknownParts.isNotEmpty()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }

    pickingSecond?.let { secondSlot ->
        KeyPickerDialog(
            title = if (secondSlot) "Second key of the chord" else "Key",
            onPick = { option ->
                if (secondSlot) {
                    second = option.key
                } else {
                    first = option.key
                }
                pickingSecond = null
                commit()
            },
            onDismiss = { pickingSecond = null },
        )
    }
}

@Composable
fun GamepadCustomizationScreen(
    onNavigateBack: () -> Unit,
    settingsRepository: SettingsRepository
) {
    val buttonConfigs by settingsRepository.buttonConfigs.collectAsState(initial = defaultButtonConfigs)
    val controllerLayout by settingsRepository.controllerLayout.collectAsState(initial = ControllerLayout.SWITCH_2)
    val bindings by settingsRepository.controllerBindings.collectAsState(initial = defaultControllerBindings)
    val controlKeyBindings by settingsRepository.controlKeyBindings
        .collectAsState(initial = defaultControlKeyBindings)
    val customKeyBindings by settingsRepository.customKeyBindings
        .collectAsState(initial = defaultCustomKeyBindings)

    var showCustomKeysEditor by rememberSaveable { mutableStateOf(false) }
    var showBindingEditor by rememberSaveable { mutableStateOf(false) }
    var modifiedLayout by rememberSaveable { mutableStateOf<ControllerLayout?>(null) }
    var modifiedConfigs by rememberSaveable {
        mutableStateOf<Map<ButtonComponent, ButtonConfig>?>(null)
    }
    var showPlayPreview by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var importJsonText by rememberSaveable { mutableStateOf("") }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showHandles by rememberSaveable { mutableStateOf(true) }
    var editingComponent by rememberSaveable { mutableStateOf<ButtonComponent?>(null) }

    val currentConfigs = modifiedConfigs ?: buttonConfigs
    val currentLayout = modifiedLayout ?: controllerLayout

    fun updateConfig(component: ButtonComponent, config: ButtonConfig) {
        val base = modifiedConfigs ?: buttonConfigs
        modifiedConfigs = base + (component to config)
    }

    // Each remap edit persists straight away, like the remap dialogs do: the pad
    // picks the new binding up from the settings flow on its next press. Both the
    // preview and the floating editor write through these, so a tap anywhere on
    // the screen saves the same way.
    fun saveAction(id: String, action: ControllerAction) {
        runBlocking {
            settingsRepository.setControllerBindings(bindings + (id to action))
        }
    }

    fun saveControlKey(id: String, key: String) {
        runBlocking {
            settingsRepository.setControlKeyBindings(controlKeyBindings + (id to key))
        }
    }

    fun saveCustomKey(index: Int, binding: CustomKeyBinding) {
        runBlocking {
            settingsRepository.setCustomKeyBindings(
                customKeyBindings.mapIndexed { i, existing ->
                    if (i == index) binding else existing
                }
            )
        }
    }

    fun applyPreset(layout: ControllerLayout) {
        modifiedLayout = layout
        if (layout != ControllerLayout.CUSTOM) {
            modifiedConfigs = buttonConfigsForLayout(layout)
        }
    }

    /** Show every control. The layout is laid out so even this does not overlap. */
    fun enableAllControls() {
        val base = currentConfigs.toMutableMap()
        ButtonComponent.entries.forEach { component ->
            val config = base[component]
                ?: defaultButtonConfigs[component]
                ?: ButtonConfig.default(component)
            base[component] = config.copy(visible = true)
        }
        modifiedConfigs = base
    }

    /** Keep the controls every controller has, and drop the extras. */
    fun hideExtraControls() {
        val extras = setOf(
            ButtonComponent.SELECT_BUTTON,
            ButtonComponent.START_BUTTON,
            ButtonComponent.PLUS_MINUS,
            ButtonComponent.HOME_CAPTURE,
            ButtonComponent.SIDE_BUTTONS,
            ButtonComponent.REAR_PADDLES,
            ButtonComponent.TOUCH_SURFACE,
            ButtonComponent.GYRO_TOGGLE,
            ButtonComponent.CUSTOM_KEYS,
            ButtonComponent.RECORDER,
        )
        val base = currentConfigs.toMutableMap()
        extras.forEach { component ->
            val config = base[component] ?: ButtonConfig.default(component)
            base[component] = config.copy(visible = false)
        }
        modifiedConfigs = base
        modifiedLayout = ControllerLayout.CUSTOM
    }

    /** Store whatever is on screen as the user's own layout, and select it. */
    fun saveAsCustom() {
        runBlocking {
            modifiedConfigs?.let { settingsRepository.setAllButtonConfigs(it) }
            settingsRepository.setControllerLayout(ControllerLayout.CUSTOM)
        }
        modifiedLayout = ControllerLayout.CUSTOM
    }

    val gamepadSkin =
        settingsRepository.gamepadSkin.collectAsState(initial = GamepadSkin.DEFAULT).value

    BackHandler(enabled = showPlayPreview) { showPlayPreview = false }

    if (showPlayPreview) {
        GamepadPreview(
            buttonConfigs = currentConfigs,
            controllerLayout = currentLayout,
            bindings = bindings,
            skin = gamepadSkin,
            customKeyBindings = customKeyBindings,
        )
        return
    }

    if (showExportDialog) {
        ExportConfigDialog(buttonConfigs = currentConfigs, onDismiss = { showExportDialog = false })
    }

    if (showBindingEditor) {
        ControllerBindingsDialog(
            bindings = bindings,
            keyBindings = controlKeyBindings,
            onSave = { updated, updatedKeys ->
                runBlocking {
                    settingsRepository.setControllerBindings(updated)
                    settingsRepository.setControlKeyBindings(updatedKeys)
                }
                showBindingEditor = false
            },
            onDismiss = { showBindingEditor = false }
        )
    }

    if (showCustomKeysEditor) {
        CustomKeysDialog(
            bindings = customKeyBindings,
            onSave = { updated ->
                runBlocking { settingsRepository.setCustomKeyBindings(updated) }
                showCustomKeysEditor = false
            },
            onDismiss = { showCustomKeysEditor = false }
        )
    }

    if (showImportDialog) {
        ImportConfigDialog(
            importedJsonText = importJsonText,
            onJsonTextChange = { importJsonText = it },
            onImport = { configs ->
                modifiedConfigs = configs
                showImportDialog = false
                importJsonText = ""
                Log.i(logTag, "Button configs imported")
            },
            onDismiss = {
                showImportDialog = false
                importJsonText = ""
            }
        )
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.customization_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Box(modifier = Modifier.weight(1f)) {
                    ListItemPicker(
                        modifier = Modifier.fillMaxWidth(),
                        list = ControllerLayout.entries.asIterable(),
                        selectedItem = currentLayout,
                        label = "Controller preset",
                        isHorizontal = true,
                        formattedDisplay = { layout -> Text(layout.displayName) },
                        onItemSelected = { layout -> applyPreset(layout) }
                    )
                }
            }

            // The preview *is* the gamepad: the real controls, in the real places.
            // Tap one to open its small floating editor, or drag it to move it.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                GamepadEditorPreview(
                    modifier = Modifier.fillMaxSize(),
                    skin = gamepadSkin,
                    buttonConfigs = currentConfigs,
                    controllerLayout = currentLayout,
                    bindings = bindings,
                    customKeyBindings = customKeyBindings,
                    controlKeyBindings = controlKeyBindings,
                    showHandles = showHandles,
                    selected = editingComponent,
                    onSelect = { editingComponent = it },
                    onConfigChange = { component, config -> updateConfig(component, config) },
                    // Each edit persists straight away, like the remap dialogs do:
                    // the pad picks the new binding up from the settings flow on
                    // its next press.
                    onActionChange = { id, action -> saveAction(id, action) },
                    onControlKeyChange = { id, key -> saveControlKey(id, key) },
                    onCustomKeyChange = { index, binding ->
                        saveCustomKey(index, binding)
                    },
                )

                val component = editingComponent
                val config = component?.let { currentConfigs[it] ?: ButtonConfig.default(it) }
                if (component != null && config != null) {
                    FloatingControlEditor(
                        modifier = Modifier.fillMaxSize(),
                        component = component,
                        config = config,
                        controlIsOnRightHalf = config.anchor.horizontal == HorizontalAnchor.END ||
                            config.offsetX > 0.1f,
                        onConfigChange = { updateConfig(component, it) },
                        onDismiss = { editingComponent = null },
                        bindings = bindings,
                        controlKeyBindings = controlKeyBindings,
                        customKeyBindings = customKeyBindings,
                        onActionChange = { id, action -> saveAction(id, action) },
                        onControlKeyChange = { id, key -> saveControlKey(id, key) },
                        onCustomKeyChange = { index, binding ->
                            saveCustomKey(index, binding)
                        },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    stringResource(R.string.customization_desc),
                    style = MaterialTheme.typography.bodySmall
                )
                ToolButton(
                    label = if (showHandles) "Hide handles" else "Show handles",
                    onClick = { showHandles = !showHandles }
                )
            }

            // Tools. A single row of these buttons is wider than a phone, so they
            // wrap onto as many lines as the screen needs instead of running off
            // the edge where they could not be tapped at all.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToolButton(
                    label = stringResource(R.string.reset),
                    onClick = { applyPreset(ControllerLayout.SWITCH_2) }
                )
                ToolButton(
                    label = stringResource(R.string.customization_preview),
                    onClick = { showPlayPreview = true }
                )
                ToolButton(label = "Keys", onClick = { showCustomKeysEditor = true })
                ToolButton(label = "Remap", onClick = { showBindingEditor = true })

                Box {
                    ToolButton(label = "Menu", onClick = { showMenu = true })
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Enable every control") },
                            onClick = {
                                enableAllControls()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Hide the extra controls") },
                            onClick = {
                                hideExtraControls()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.customization_import)) },
                            onClick = {
                                showImportDialog = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.customization_export)) },
                            onClick = {
                                showExportDialog = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save as custom layout") },
                            onClick = {
                                showMenu = false
                                saveAsCustom()
                                onNavigateBack()
                            }
                        )
                    }
                }

                ToolButton(label = stringResource(R.string.cancel), onClick = onNavigateBack)
                ToolButton(
                    label = stringResource(R.string.save),
                    onClick = {
                        runBlocking {
                            try {
                                modifiedConfigs?.let { configs ->
                                    settingsRepository.setAllButtonConfigs(configs)
                                }
                                settingsRepository.setControllerLayout(currentLayout)
                                Log.i(logTag, "Button configs and controller preset saved")
                            } catch (e: Exception) {
                                Log.e(logTag, "Error saving button configs", e)
                            }
                        }
                        onNavigateBack()
                    }
                )
            }
        }
    }
}

/**
 * One button in the customisation screen's tool bar.
 *
 * The bar holds more buttons than fit across a phone, so each one keeps a small
 * footprint and the bar wraps. [FlowRow] is what does the wrapping; this only
 * makes sure a button never grows wider than it needs to be.
 */
@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, maxLines = 1)
    }
}

/**
 * Dialog for exporting button configurations as JSON
 */
@Composable
fun ExportConfigDialog(
    buttonConfigs: Map<ButtonComponent, ButtonConfig>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val jsonString = remember(buttonConfigs) {
        Json.encodeToString(buttonConfigs)
    }
    val exportLabel = stringResource(R.string.customization_export_label)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.customization_export_title)) },
        text = {
            Log.i(logTag, "Exporting button configs: $jsonString")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.customization_export_desc),
                    style = MaterialTheme.typography.bodySmall
                )
                TextField(
                    value = jsonString,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    label = { Text(stringResource(R.string.customization_export_label)) },
                    readOnly = true,
                    singleLine = false,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodySmall,
                    trailingIcon = {
                        Button(onClick = {
                            val clip = android.content.ClipData.newPlainText(
                                exportLabel, jsonString
                            )
                            clipboard.setPrimaryClip(clip)
                            Log.i(logTag, "Exported JSON copied to clipboard")
                        }) {
                            Text(stringResource(R.string.customization_copy))
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.customization_done))
            }
        }
    )
}

/**
 * Dialog for importing button configurations from JSON
 */
@Composable
fun ImportConfigDialog(
    importedJsonText: String,
    onJsonTextChange: (String) -> Unit,
    onImport: (Map<ButtonComponent, ButtonConfig>) -> Unit,
    onDismiss: () -> Unit
) {
    var hasError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.customization_import_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.customization_import_desc),
                    style = MaterialTheme.typography.bodySmall
                )
                val errorMessage = if (hasError) {
                    stringResource(R.string.customization_import_error)
                } else {
                    ""
                }
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                TextField(
                    value = importedJsonText,
                    onValueChange = onJsonTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    placeholder = { Text(stringResource(R.string.customization_import_placeholder)) },
                    singleLine = false,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val configs: Map<ButtonComponent, ButtonConfig> =
                            Json.decodeFromString(importedJsonText)

                        val sanitizedConfigs = sanitizeButtonConfigs(configs)

                        hasError = false
                        Log.i(logTag, "Imported and sanitized button configs: $sanitizedConfigs")
                        onImport(sanitizedConfigs)
                    } catch (e: Exception) {
                        hasError = true
                        Log.e(logTag, "Error parsing JSON", e)
                    }
                }
            ) {
                Text(stringResource(R.string.customization_import))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Preview(
    showBackground = true,
    widthDp = 800,
    heightDp = 400
)
@Composable
fun GamepadCustomizationScreenPreview() {
    PreviewBase {
        GamepadCustomizationScreen(
            onNavigateBack = {},
            settingsRepository = SettingsRepository(LocalContext.current)
        )
    }
}




@Composable
fun CustomKeysDialog(
    bindings: List<CustomKeyBinding>,
    onSave: (List<CustomKeyBinding>) -> Unit,
    onDismiss: () -> Unit,
) {
    var edited by remember(bindings) {
        mutableStateOf(normaliseCustomKeyBindings(bindings))
    }
    var pickingSlot by remember { mutableStateOf<Int?>(null) }

    fun update(index: Int, binding: CustomKeyBinding) {
        edited = edited.toMutableList().also { it[index] = binding }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom key buttons") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Each of the four buttons sends one PC key — or two as a chord — while it " +
                        "is held. Type any key or pick one: a letter (W), a digit (1, sent as " +
                        "Digit1), punctuation for chat commands (/), F1-F12, Space, Tab, Shift, " +
                        "the mouse buttons. The name has to be one the PC's key table knows; " +
                        "anything else is ignored there instead of pressing a different key. " +
                        "Join two keys with + — for example LeftShift+C — and both go down " +
                        "together while the button is held."
                )
                edited.forEachIndexed { index, binding ->
                    val canonical = canonicalKeyChord(binding.key)
                    val unknownParts = keyChordParts(binding.key)
                        .map(::canonicalKeyName)
                        .filter { part -> part.lowercase() !in knownCustomKeyNames }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = binding.label,
                                onValueChange = { text -> update(index, binding.copy(label = text)) },
                                label = { Text("Label") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = binding.key,
                                onValueChange = { text -> update(index, binding.copy(key = text)) },
                                label = { Text("Key") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { pickingSlot = index }) {
                                Text("Pick")
                            }
                        }
                        Text(
                            when {
                                binding.key.isBlank() -> "No key: this button sends nothing."
                                unknownParts.isNotEmpty() ->
                                    "\"${unknownParts.first()}\" is not a key the PC knows; it is ignored there."
                                keyChordParts(binding.key).size == 2 ->
                                    "Sends $canonical as a chord: both keys go down while " +
                                        "the button is held, then release together."
                                canonical != binding.key -> "Sends $canonical"
                                else -> "Sends $binding.key"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (binding.key.isNotBlank() && unknownParts.isNotEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Button(
                    onClick = { edited = defaultCustomKeyBindings },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Minecraft defaults")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(edited.map { it.copy(key = canonicalKeyChord(it.key)) })
            }) {
                Text("Save keys")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    pickingSlot?.let { slot ->
        KeyPickerDialog(
            title = "Pick a key",
            onPick = { option ->
                val current = edited[slot]
                // A label that is still the key it came with follows the new key;
                // one the player typed is left alone.
                val label = if (current.label.isBlank() || current.label == current.key) {
                    option.label
                } else {
                    current.label
                }
                // Repicks the first key; a chord's second key stays put.
                val rest = keyChordParts(current.key).drop(1)
                update(
                    slot,
                    current.copy(key = keyChordValue(listOf(option.key) + rest), label = label),
                )
                pickingSlot = null
            },
            onDismiss = { pickingSlot = null }
        )
    }
}

/**
 * One row per control: what gamepad action it sends, and the PC key it sends
 * instead when one is given.
 *
 * The two choices sit together because they answer the same question — what does
 * this button do? — and a control bound to a key sends only that key, which is
 * what makes a pad button work in a game that never looks at a controller. Any
 * key the PC's table knows can be typed or picked, and an unknown name is
 * flagged here and ignored there rather than sending some other key.
 */
@Composable
fun ControllerBindingsDialog(
    bindings: Map<String, ControllerAction>,
    keyBindings: Map<String, String>,
    onSave: (Map<String, ControllerAction>, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var edited by remember(bindings) {
        mutableStateOf(defaultControllerBindings + bindings)
    }
    var editedKeys by remember(keyBindings) {
        mutableStateOf(normaliseControlKeyBindings(keyBindings))
    }
    var pickingKeyFor by remember { mutableStateOf<String?>(null) }
    val bindingKeys = defaultControllerBindings.keys.sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remap controller buttons") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Choose which gamepad action each digital control sends, and — for a game " +
                        "that only reads a keyboard — the PC key it should send instead. A " +
                        "control with a key sends that key and not its gamepad action. Two keys " +
                        "joined with + (LeftShift+C) make a chord: on a tap both go down in that " +
                        "order, then release together. Analog sticks keep their axis; a trigger " +
                        "with a key bound sends it on a pull past half instead of its pressure."
                )
                bindingKeys.forEach { key ->
                    val boundKey = editedKeys[key].orEmpty()
                    val parts = keyChordParts(boundKey)
                    val canonical = canonicalKeyChord(boundKey)
                    val unknownParts = parts
                        .map(::canonicalKeyName)
                        .filter { part -> part.lowercase() !in knownCustomKeyNames }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            controlLabel(key),
                            style = MaterialTheme.typography.labelMedium
                        )
                        ListItemPicker(
                            modifier = Modifier.fillMaxWidth(),
                            list = ControllerAction.entries.asIterable(),
                            selectedItem = edited[key] ?: defaultControllerBindings.getValue(key),
                            label = "Output action",
                            formattedDisplay = { Text(it.name.replace('_', ' ')) },
                            onItemSelected = { action ->
                                edited = edited + (key to action)
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = boundKey,
                                onValueChange = { text ->
                                    editedKeys = editedKeys + (key to text.trim())
                                },
                                label = { Text("PC key (optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { pickingKeyFor = key }) {
                                Text("Pick")
                            }
                        }
                        Text(
                            when {
                                boundKey.isBlank() -> "Sends its gamepad action"
                                unknownParts.isNotEmpty() ->
                                    "\"${unknownParts.first()}\" is not a key the PC knows; " +
                                        "this control keeps sending its gamepad action there."
                                parts.size == 2 ->
                                    "Sends $canonical as a chord: both keys go down in " +
                                        "that order, then release together."
                                canonical != boundKey -> "Sends $canonical"
                                else -> "Sends $boundKey"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (boundKey.isNotBlank() && unknownParts.isNotEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Button(
                    onClick = {
                        edited = defaultControllerBindings
                        editedKeys = emptyMap()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset mappings")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    edited,
                    normaliseControlKeyBindings(
                        editedKeys.mapValues { (_, key) -> canonicalKeyChord(key) }
                    ),
                )
            }) {
                Text("Save mappings")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    pickingKeyFor?.let { control ->
        KeyPickerDialog(
            title = "Key for ${controlLabel(control)}",
            onPick = { option ->
                // Repicks the first key; a second key picked for a chord stays.
                val rest = keyChordParts(editedKeys[control].orEmpty()).drop(1)
                editedKeys = editedKeys +
                    (control to keyChordValue(listOf(option.key) + rest))
                pickingKeyFor = null
            },
            onDismiss = { pickingKeyFor = null }
        )
    }
}

/**
 * Every key the PC's table can resolve, grouped the way a keyboard is.
 *
 * Shared by the custom-key buttons and the per-control key bindings, so a key
 * that can be picked for one can be picked for the other and neither list can
 * drift from the other.
 */
@Composable
fun KeyPickerDialog(
    title: String,
    onPick: (CustomKeyOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                customKeyOptionGroups.forEach { (group, options) ->
                    Text(group, style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        options.forEach { option ->
                            OutlinedButton(onClick = { onPick(option) }) {
                                Text(option.label)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
