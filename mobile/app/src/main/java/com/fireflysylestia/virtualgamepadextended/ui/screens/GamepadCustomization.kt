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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.fireflysylestia.virtualgamepadextended.data.MacroAction
import com.fireflysylestia.virtualgamepadextended.data.MacroDefinition
import com.fireflysylestia.virtualgamepadextended.data.GamepadGeometry
import com.fireflysylestia.virtualgamepadextended.data.HorizontalAnchor
import com.fireflysylestia.virtualgamepadextended.data.MacroStep
import com.fireflysylestia.virtualgamepadextended.data.OFFSET_VALUE_RANGE
import com.fireflysylestia.virtualgamepadextended.data.horizontal
import com.fireflysylestia.virtualgamepadextended.data.PreviewBase
import com.fireflysylestia.virtualgamepadextended.data.SettingsRepository
import com.fireflysylestia.virtualgamepadextended.data.buttonConfigsForLayout
import com.fireflysylestia.virtualgamepadextended.data.defaultButtonConfigs
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings
import com.fireflysylestia.virtualgamepadextended.ui.composables.BoundedNumericInput
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
        configs[component] ?: ButtonConfig.default(component)
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
    macros: List<MacroDefinition>,
    bindings: Map<String, ControllerAction>,
    skin: GamepadSkin = GamepadSkin.DEFAULT,
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
            macros = macros,
            bindings = bindings
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
    macros: List<MacroDefinition>,
    bindings: Map<String, ControllerAction>,
    showHandles: Boolean,
    selected: ButtonComponent?,
    onSelect: (ButtonComponent) -> Unit,
    onConfigChange: (ButtonComponent, ButtonConfig) -> Unit,
    modifier: Modifier = Modifier,
    skin: GamepadSkin = GamepadSkin.DEFAULT,
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
                macros = macros,
                bindings = bindings,
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



@Composable
fun GamepadCustomizationScreen(
    onNavigateBack: () -> Unit,
    settingsRepository: SettingsRepository
) {
    val buttonConfigs by settingsRepository.buttonConfigs.collectAsState(initial = defaultButtonConfigs)
    val controllerLayout by settingsRepository.controllerLayout.collectAsState(initial = ControllerLayout.SWITCH_2)
    val macros by settingsRepository.macros.collectAsState(initial = emptyList())
    val bindings by settingsRepository.controllerBindings.collectAsState(initial = defaultControllerBindings)

    var showBindingEditor by rememberSaveable { mutableStateOf(false) }
    var showMacroEditor by rememberSaveable { mutableStateOf(false) }
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
            val config = base[component] ?: ButtonConfig.default(component)
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
            ButtonComponent.MACRO_BUTTONS,
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
            macros = macros,
            bindings = bindings,
            skin = gamepadSkin,
        )
        return
    }

    if (showExportDialog) {
        ExportConfigDialog(buttonConfigs = currentConfigs, onDismiss = { showExportDialog = false })
    }

    if (showBindingEditor) {
        ControllerBindingsDialog(
            bindings = bindings,
            onSave = { updated ->
                runBlocking { settingsRepository.setControllerBindings(updated) }
                showBindingEditor = false
            },
            onDismiss = { showBindingEditor = false }
        )
    }

    if (showMacroEditor) {
        MacroEditorDialog(
            macros = macros,
            onSave = { updated ->
                runBlocking { settingsRepository.setMacros(updated) }
                showMacroEditor = false
            },
            onDismiss = { showMacroEditor = false }
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
                    macros = macros,
                    bindings = bindings,
                    showHandles = showHandles,
                    selected = editingComponent,
                    onSelect = { editingComponent = it },
                    onConfigChange = { component, config -> updateConfig(component, config) }
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
                        onDismiss = { editingComponent = null }
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
                ToolButton(label = "Macros", onClick = { showMacroEditor = true })
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
fun MacroEditorDialog(
    macros: List<MacroDefinition>,
    onSave: (List<MacroDefinition>) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember(macros) {
        (0..3).map { slot ->
            macros.getOrNull(slot) ?: MacroDefinition(name = "Macro ${slot + 1}")
        }
    }
    var definitions by remember(macros) { mutableStateOf(initial) }
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(initial.first().name) }
    var steps by remember { mutableStateOf(initial.first().steps) }
    var action by rememberSaveable { mutableStateOf(MacroAction.A) }
    var value by rememberSaveable { mutableStateOf(1f) }
    var holdMillis by rememberSaveable { mutableIntStateOf(120) }
    var gapMillis by rememberSaveable { mutableIntStateOf(40) }

    fun commitCurrent(): List<MacroDefinition> {
        val updated = definitions.toMutableList()
        updated[slot] = MacroDefinition(
            name = name.trim().ifEmpty { "Macro ${slot + 1}" },
            steps = steps.take(32),
        )
        definitions = updated
        return updated
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Macro editor") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Create up to four macros. Button and D-pad steps use press/release; trigger and axis steps use the value below.")
                ListItemPicker(
                    modifier = Modifier.fillMaxWidth(),
                    list = (0..3).asIterable(),
                    selectedItem = slot,
                    label = "Macro slot",
                    formattedDisplay = { Text("M${it + 1}") },
                    onItemSelected = { nextSlot ->
                        commitCurrent()
                        slot = nextSlot
                        name = definitions[nextSlot].name
                        steps = definitions[nextSlot].steps
                    }
                )
                TextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    label = { Text("Macro name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ListItemPicker(
                    modifier = Modifier.fillMaxWidth(),
                    list = MacroAction.entries.asIterable(),
                    selectedItem = action,
                    label = "Action",
                    formattedDisplay = { Text(it.name.replace('_', ' ')) },
                    onItemSelected = { action = it }
                )
                BoundedNumericInput(
                    value = value,
                    onValueChange = { value = it },
                    label = "Axis / trigger value",
                    minValue = -1f,
                    maxValue = 1f,
                    parseValue = { it.toFloatOrNull() },
                    formatValue = { "%.2f".format(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoundedNumericInput(
                        value = holdMillis,
                        onValueChange = { holdMillis = it },
                        label = "Hold (ms)",
                        minValue = 10,
                        maxValue = 5000,
                        parseValue = { it.toIntOrNull() },
                        modifier = Modifier.weight(1f)
                    )
                    BoundedNumericInput(
                        value = gapMillis,
                        onValueChange = { gapMillis = it },
                        label = "Gap (ms)",
                        minValue = 0,
                        maxValue = 5000,
                        parseValue = { it.toIntOrNull() },
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = {
                        if (steps.size < 32) {
                            steps = steps + MacroStep(
                                action = action,
                                value = value,
                                holdMillis = holdMillis,
                                gapMillis = gapMillis,
                            )
                        }
                    },
                    enabled = steps.size < 32,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add step (${steps.size}/32)")
                }
                steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}. ${step.action.name.replace('_', ' ')} · ${step.holdMillis} ms",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { steps = steps.filterIndexed { i, _ -> i != index } }) {
                            Text("Remove")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(commitCurrent()) }) {
                Text("Save macros")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}


@Composable
fun ControllerBindingsDialog(
    bindings: Map<String, ControllerAction>,
    onSave: (Map<String, ControllerAction>) -> Unit,
    onDismiss: () -> Unit,
) {
    var edited by remember(bindings) {
        mutableStateOf(defaultControllerBindings + bindings)
    }
    val bindingKeys = defaultControllerBindings.keys.sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remap controller buttons") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Choose which gamepad action each visible digital control sends. Analog sticks and triggers keep their axis/pressure behavior.")
                bindingKeys.forEach { key ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            key.replace('_', ' ').uppercase(),
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
                    }
                }
                Button(
                    onClick = { edited = defaultControllerBindings },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset mappings")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(edited) }) {
                Text("Save mappings")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
